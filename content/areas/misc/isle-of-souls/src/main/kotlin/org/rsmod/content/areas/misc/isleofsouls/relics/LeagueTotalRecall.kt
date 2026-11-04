package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.content.skills.magic.utility.configs.utility_seqs
import org.rsmod.content.skills.magic.utility.configs.utility_spotanims
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object TotalRecallVarps : VarpReferences() {
    /** The saved tile, as a packed coordinate. `0` means nothing is saved. */
    val coord = find("league_last_recall_source_coord")
}

object TotalRecallVarBits : VarBitReferences() {
    val hitpoints = find("league_last_recall_stored_stat_hitpoints")
    val prayer = find("league_last_recall_stored_stat_prayer")

    /** Special attack energy in whole percent (`0`-`100`). */
    val special = find("league_last_recall_stored_stat_special_attack")
}

/**
 * The Total Recall relic (tier 5 in the code, "tier 6" in the plan and on the wiki). Its crystal of
 * echoes saves one tile with the player's Hitpoints, Prayer and special attack energy
 * ("Save-memory"), and teleports back there whenever the player likes, setting those three back to
 * what was saved ("Teleport-back"). The memory stays after use, like the wiki's, so it can be used
 * again and again, and it survives death. It cannot be saved inside an instance (a boss lair, a
 * raid).
 */
class LeagueTotalRecall @Inject constructor(private val tools: LeagueRelicTools) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(isle_objs.crystal_of_echoes) { teleportBack() }
        onOpHeld2(isle_objs.crystal_of_echoes) { checkMemory() }
        onOpHeld3(isle_objs.crystal_of_echoes) { saveMemory() }
    }

    private fun ProtectedAccess.saveMemory() {
        if (!tools.isChosen(player, RelicItem.TotalRecall)) {
            mes("Only someone with the Total Recall relic can use the crystal.")
            return
        }
        if (RegionRegistry.inWorkingArea(player.coords)) {
            mes("The crystal cannot hold a memory of this place.")
            return
        }
        VarPlayerIntMapSetter.set(player, TotalRecallVarps.coord, player.coords.packed)
        player.store(TotalRecallVarBits.hitpoints, player.stat(stats.hitpoints))
        player.store(TotalRecallVarBits.prayer, player.stat(stats.prayer))
        player.store(TotalRecallVarBits.special, player.vars[varps.sa_energy] / 10)
        mes("The crystal of echoes remembers this moment.")
    }

    private fun ProtectedAccess.checkMemory() {
        val coord = savedCoord(player)
        if (coord == null) {
            mes("The crystal of echoes holds no memory.")
            return
        }
        val hitpoints = player.vars[TotalRecallVarBits.hitpoints]
        val prayer = player.vars[TotalRecallVarBits.prayer]
        val special = player.vars[TotalRecallVarBits.special]
        mes(
            "The crystal remembers ${LeagueChat.blue("$hitpoints")} Hitpoints, " +
                "${LeagueChat.blue("$prayer")} Prayer and " +
                "${LeagueChat.blue("$special%")} special attack energy."
        )
    }

    private suspend fun ProtectedAccess.teleportBack() {
        if (!tools.isChosen(player, RelicItem.TotalRecall)) {
            mes("Only someone with the Total Recall relic can use the crystal.")
            return
        }
        val coord = savedCoord(player)
        if (coord == null) {
            mes("The crystal of echoes holds no memory. Save one first.")
            return
        }
        anim(utility_seqs.cast_teleport)
        spotanim(utility_spotanims.teleport, height = SPOTANIM_HEIGHT)
        soundSynth(synths.teleport_all)
        beginTeleport(TELEPORT_TICKS)
        delay(TELEPORT_TICKS)
        telejump(coord)
        soundSynth(synths.teleport_reverse)
        anim(utility_seqs.cast_teleport_reverse)
        resetSpotanim()
        restore(stats.hitpoints, player.vars[TotalRecallVarBits.hitpoints])
        restore(stats.prayer, player.vars[TotalRecallVarBits.prayer])
        val special = player.vars[TotalRecallVarBits.special] * 10
        VarPlayerIntMapSetter.set(player, varps.sa_energy, special)
    }

    private fun ProtectedAccess.restore(stat: StatType, level: Int) {
        val current = player.stat(stat)
        when {
            level > current -> player.statAdd(stat, constant = level - current, percent = 0)
            level < current -> player.statSub(stat, constant = current - level, percent = 0)
        }
    }

    /** Stores [value] in [varbit], clamped to what its bits can hold. */
    private fun Player.store(varbit: VarBitType, value: Int) {
        val max = (1 shl (varbit.msb - varbit.lsb + 1)) - 1
        VarPlayerIntMapSetter.set(this, varbit, value.coerceIn(0, max))
    }

    companion object {
        private const val TELEPORT_TICKS = 3
        private const val SPOTANIM_HEIGHT = 92

        /** The tile [player]'s crystal remembers, or `null` if it holds no memory. */
        fun savedCoord(player: Player): CoordGrid? {
            val packed = player.vars[TotalRecallVarps.coord]
            return if (packed == 0) null else CoordGrid(packed)
        }
    }
}
