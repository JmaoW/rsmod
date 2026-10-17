package org.rsmod.content.skills.slayer.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.content.skills.slayer.configs.SlayerUnlocks
import org.rsmod.content.skills.slayer.configs.SlayerVarBits
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Superior Slayer monsters. A kill of a monster on the player's task has a small chance to bring up
 * its superior on the same tile, which attacks the player. It is a kill for the task like any
 * other, gives its own Slayer XP and drops, and goes away after a while if it is left alone.
 *
 * Superiors only appear once the player has the "Bigger and Badder" unlock (bit
 * [SlayerUnlocks.BIGGER_AND_BADDER]); the chance is 1 in [BASE_ODDS], less when a hook (the
 * Passives relic) asks for it.
 */
class SlayerSuperiors
@Inject
constructor(
    private val engine: SlayerEngine,
    private val random: GameRandom,
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val objRepo: ObjRepository,
    private val objTypes: ObjTypeList,
) : PluginScript() {
    private val superiorByBase: Map<String, UnpackedNpcType> by lazy {
        val ids = engine.superiorIds
        BASE_TO_SUPERIOR.mapNotNull { (base, superior) ->
                val type =
                    npcTypes.values.firstOrNull {
                        it.id in ids && it.name.equals(superior, ignoreCase = true)
                    }
                type?.let { base to it }
            }
            .toMap()
    }

    override fun ScriptContext.startup() {
        onEvent<SlayerEvents.Kill> { roll(player, npc, onTask) }
        onEvent<SlayerEvents.SuperiorKilled> { tertiary(player, npc) }
    }

    /**
     * The drops every superior shares: an eternal gem, an imbued heart and the mist and dust
     * battlestaves. The Wiki's chance is `1 in 8 * (200 - floor((level + 55)^2 / 125))` for the gem
     * and the heart, and 3.5 times as likely for each staff, for the superior's combat level.
     */
    fun tertiary(player: Player, superior: Npc) {
        val level = superior.visType.vislevel
        val gem = 8 * (200 - (level + 55) * (level + 55) / 125)
        if (gem <= 0) {
            return
        }
        val staff = maxOf(1, (gem / 3.5).toInt())
        val rolls =
            listOf(
                SlayerObjs.eternal_gem to gem,
                SlayerObjs.imbued_heart to gem,
                SlayerObjs.mist_battlestaff to staff,
                SlayerObjs.dust_battlestaff to staff,
            )
        for ((obj, odds) in rolls) {
            if (random.of(odds) == 0) {
                val drop = Obj.fromOwner(player, superior.coords, objTypes[obj], 1)
                objRepo.add(drop, constants.lootdrop_duration)
            }
        }
    }

    /** The chance, as `1 in N`, that a task kill brings up a superior for [player]. */
    fun odds(player: Player): Int =
        engine.hooks.superiorOdds.mapNotNull { it(player) }.minOrNull() ?: BASE_ODDS

    /**
     * Whether [player] can meet superiors at all: they have the unlock and have not switched it
     * off.
     */
    fun enabled(player: Player): Boolean =
        engine.isUnlocked(player, SlayerUnlocks.BIGGER_AND_BADDER) &&
            player.vars[SlayerVarBits.toggle_off_superiors] == 0

    /** The superior that comes from [base], if it has one. */
    fun superiorFor(base: Npc): UnpackedNpcType? = superiorByBase[base.visType.name.lowercase()]

    private fun roll(player: Player, base: Npc, onTask: Boolean) {
        if (!enabled(player) || base.visType.id in engine.superiorIds) {
            return
        }
        val superior = superiorFor(base) ?: return
        // A task kill, or any kill of a task monster for a player who is always on task.
        if (!onTask && engine.hooks.alwaysOnTask.none { it(player) }) {
            return
        }
        if (random.of(odds(player)) != 0) {
            return
        }
        spawn(player, base, superior)
    }

    fun spawn(player: Player, base: Npc, superior: UnpackedNpcType): Npc {
        val npc = Npc(superior, base.coords)
        npcRepo.add(npc, LIFETIME)
        if (superior.attackRange > 1) {
            npc.apPlayer2(player, interactions)
        } else {
            npc.opPlayer2(player, interactions)
        }
        player.mes("<col=ef1020>A superior foe has appeared...</col>")
        engine.publish(SlayerEvents.SuperiorSpawned(player, npc, base))
        return npc
    }

    companion object {
        const val BASE_ODDS = 200

        /** Ticks a superior stays if nobody kills it. */
        const val LIFETIME = 500

        /** Base monster (lower case) to its superior's name. */
        val BASE_TO_SUPERIOR =
            mapOf(
                "crawling hand" to "Crushing hand",
                "cave crawler" to "Chasm Crawler",
                "banshee" to "Screaming banshee",
                "twisted banshee" to "Screaming twisted banshee",
                "rockslug" to "Giant rockslug",
                "cockatrice" to "Cockathrice",
                "pyrefiend" to "Flaming pyrelord",
                "basilisk" to "Monstrous basilisk",
                "basilisk knight" to "Basilisk Sentinel",
                "infernal mage" to "Malevolent Mage",
                "bloodveld" to "Insatiable Bloodveld",
                "mutated bloodveld" to "Insatiable mutated Bloodveld",
                "jelly" to "Vitreous Jelly",
                "warped jelly" to "Vitreous warped Jelly",
                "cave horror" to "Cave abomination",
                "aberrant spectre" to "Abhorrent spectre",
                "deviant spectre" to "Repugnant spectre",
                "dust devil" to "Choke devil",
                "kurask" to "King kurask",
                "smoke devil" to "Nuclear smoke devil",
                "gargoyle" to "Marble gargoyle",
                "dark beast" to "Night beast",
                "abyssal demon" to "Greater abyssal demon",
            )
    }
}
