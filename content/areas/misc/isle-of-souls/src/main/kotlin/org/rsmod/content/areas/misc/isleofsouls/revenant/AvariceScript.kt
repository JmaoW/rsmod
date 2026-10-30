package org.rsmod.content.areas.misc.isleofsouls.revenant

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.drops.DropResult
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.content.areas.misc.isleofsouls.dungeons.DungeonRevenantScript
import org.rsmod.content.areas.misc.isleofsouls.pvp.PvpSkull
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

typealias avarice_timers = AvariceTimers

object AvariceTimers : TimerReferences() {
    /** Ends the skull that stays for 20 minutes after the amulet comes off. */
    val skull = find("avarice_skull")
}

object AvariceVarps : VarpReferences() {
    /**
     * Where the amulet's skull is: [AvariceScript.NONE], [AvariceScript.WORN] or
     * [AvariceScript.FADING].
     */
    val skull = find("isle_avarice_skull")
}

internal object AvariceVarpBuilder : VarpBuilder() {
    init {
        build("isle_avarice_skull") { permanent = true }
    }
}

/**
 * The amulet of avarice (OSRS Wiki). Its +20% accuracy and damage against revenants is part of the
 * combat formulas (a salve-style bonus that does not stack with a Slayer helmet). The rest is here:
 * - Wearing it skulls the player, and the skull stays while it is worn. Taken off, it stays for 20
 *   minutes more. The skull is what makes revenants drop their skulled tables.
 * - The drops a revenant leaves for a player who wears it are noted, if they have a noted form.
 *   Uniques (the drops the tables mark as boosted) and the bracelet of ethereum are not noted, nor
 *   are the secondary supplies (the blighted sacks and food, and the revenant cave teleport).
 *   Stackable drops (the ether and the coins) have no noted form anyway.
 *
 * Not built: the warning the game shows before the amulet skulls you (an equip cannot be refused
 * here), the 30-minute Forinthry Surge skull from the Revenant maledictus (not in the isle), and
 * the Wilderness Slayer Cave and the Chaos Temple (the other places where drops are noted), which
 * the isle does not have. The fading skull does not survive a log out for its remaining time: it is
 * given its full 20 minutes again at the next login.
 */
class AvariceScript
@Inject
constructor(private val objTypes: ObjTypeList, private val pvpSkull: PvpSkull) : PluginScript() {
    private val noted: Map<Int, UnpackedObjType> by lazy {
        objTypes.values.filter { it.isCert }.associateBy { it.certlink }
    }

    override fun ScriptContext.startup() {
        onWearposChange { if (wearpos == Wearpos.Front) refresh(player, login = false) }
        onPlayerLogin { refresh(player, login = true) }
        onPlayerSoftTimer(avarice_timers.skull) { expire(player) }
        onEvent<NpcDropEvents.Claim> { note(hero, npc, drops) }
    }

    /** Whether [player] wears the amulet of avarice. */
    fun isWorn(player: Player): Boolean =
        player.worn[Wearpos.Front.slot].isType(objs.amulet_of_avarice)

    /** Skulls the player while the amulet is worn, and lets the skull fade after it comes off. */
    fun refresh(player: Player, login: Boolean) {
        val state = player.vars[AvariceVarps.skull]
        when {
            isWorn(player) -> {
                if (state != WORN) {
                    player.mes("The amulet of avarice marks you with a skull.")
                }
                player.clearSoftTimer(avarice_timers.skull)
                player.skullIcon = SKULL_ICON
                VarPlayerIntMapSetter.set(player, AvariceVarps.skull, WORN)
            }
            state == WORN || (state == FADING && login) -> {
                player.skullIcon = SKULL_ICON
                player.softTimer(avarice_timers.skull, FADE_TICKS)
                VarPlayerIntMapSetter.set(player, AvariceVarps.skull, FADING)
            }
        }
    }

    /** The fading skull is over (unless the amulet is on again). */
    fun expire(player: Player) {
        player.clearSoftTimer(avarice_timers.skull)
        if (isWorn(player)) {
            return
        }
        // A skull from attacking a player keeps the icon on.
        player.skullIcon = if (pvpSkull.active(player)) SKULL_ICON else null
        VarPlayerIntMapSetter.set(player, AvariceVarps.skull, NONE)
    }

    /** Swaps the drops in [drops] that can be noted for their noted form. Returns how many. */
    fun note(hero: Player, npc: Npc, drops: MutableList<DropResult>): Int {
        if (!isWorn(hero) || !isRevenant(npc)) {
            return 0
        }
        var changed = 0
        for (index in drops.indices) {
            val drop = drops[index]
            val note = if (isNotedByAmulet(drop)) noted[objId(drop.obj)] else null
            if (note != null) {
                drops[index] = drop.copy(obj = note)
                changed++
            }
        }
        return changed
    }

    /** Uniques, the bracelet of ethereum and secondary supplies stay as they are. */
    private fun isNotedByAmulet(drop: DropResult): Boolean =
        !drop.boosted &&
            drop.obj.id != ether_objs.bracelet_uncharged.id &&
            drop.obj.internalName?.startsWith("blighted_") != true &&
            drop.obj.id != ether_objs.revenant_cave_teleport.id

    private fun objId(obj: ObjType): Int = obj.id

    companion object {
        const val NONE = 0
        const val WORN = 1
        const val FADING = 2

        /** 20 minutes. */
        const val FADE_TICKS = 2_000

        /** The white skull, the same one a player gets in the wilderness. */
        const val SKULL_ICON = constants.skullicon_default
    }
}

/**
 * Marks the revenants with the `revenant` param, which the combat formulas read for the amulet of
 * avarice's bonus. Nothing set it before, so the bonus never applied. Needs `packCache`.
 */
internal object RevenantNpcEdits : NpcEditor() {
    init {
        for (kind in DungeonRevenantScript.KINDS) {
            edit(kind.type) { param[params.revenant] = 1 }
        }
    }
}
