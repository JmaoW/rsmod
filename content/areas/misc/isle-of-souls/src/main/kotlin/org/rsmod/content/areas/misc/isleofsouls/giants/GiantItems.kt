package org.rsmod.content.areas.misc.isleofsouls.giants

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.prayer.scripts.Burying
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.game.type.loc.LocTypeList

/**
 * Giant bones, which both bosses drop: they cannot be picked up (their ground option "Take" only
 * says so), only buried where they lie, for 150 Prayer experience (OSRS Wiki "Giant bones", tsps).
 */
class GiantBones
@Inject
constructor(private val objRepo: ObjRepository, private val xpMods: XpModifiers) {
    /** "Take" on giant bones. */
    fun ProtectedAccess.take() {
        mes(NO_ROOM)
    }

    /**
     * Buries [bones] where they lie: asks first ("Yes, and don't ask again" stops it asking), then
     * the animation, the sound and the experience, with no message.
     */
    suspend fun ProtectedAccess.bury(bones: Obj) {
        val flags = player.vars[giant_varps.flags]
        if ((flags and GiantFlags.BURY_WITHOUT_ASKING) == 0) {
            val choice =
                choice3(
                    "Yes.",
                    1,
                    "Yes, and don't ask again.",
                    2,
                    "No.",
                    3,
                    title = "Bury the bones? (This grants Prayer XP)",
                )
            when (choice) {
                3 -> return
                2 ->
                    VarPlayerIntMapSetter.set(
                        player,
                        giant_varps.flags,
                        flags or GiantFlags.BURY_WITHOUT_ASKING,
                    )
            }
        }
        if (!objRepo.del(bones)) {
            return
        }
        anim(giant_seqs.player_bury)
        soundSynth(giant_synths.bury)
        statAdvance(stats.prayer, XP * xpMods.get(player, stats.prayer))
        publish(Burying.BoneBuried(player, giant_objs.giant_bones))
    }

    companion object {
        const val NO_ROOM = "I don't think those will fit in my backpack..."
        const val XP = 150.0
    }
}

/**
 * Bryophyta's staff: her essence on a battlestaff makes an uncharged staff (62 Crafting, both are
 * used up), nature runes charge it (up to 1,000) and "Uncharge" takes the charges out (the runes
 * are lost). The engine lets a charged staff stand in for any number of nature runes, so the
 * charges themselves are not counted down.
 */
class BryophytasStaff {
    fun ProtectedAccess.make() {
        if (player.craftingLvl < CRAFTING_LEVEL) {
            mes("You need a Crafting level of $CRAFTING_LEVEL to make Bryophyta's staff.")
            return
        }
        val taken =
            invDel(
                inv = inv,
                type1 = giant_objs.bryophytas_essence,
                count1 = 1,
                type2 = giant_objs.battlestaff,
                count2 = 1,
            )
        if (taken.failure) {
            return
        }
        invAdd(inv, giant_objs.bryophytas_staff_uncharged)
        mes("You infuse the battlestaff with the power of nature.")
    }

    fun ProtectedAccess.charge() {
        val runes = minOf(invTotal(inv, objs.nature_rune), MAX_CHARGES)
        if (runes <= 0) {
            return
        }
        if (invDel(inv, giant_objs.bryophytas_staff_uncharged, 1).failure) {
            return
        }
        invDel(inv, objs.nature_rune, runes)
        invAdd(inv, giant_objs.bryophytas_staff)
        mes("You charge the staff with $runes nature runes.")
    }

    fun ProtectedAccess.uncharge() {
        if (invDel(inv, giant_objs.bryophytas_staff, 1).failure) {
            return
        }
        invAdd(inv, giant_objs.bryophytas_staff_uncharged)
        mes("You remove the charges from the staff. The nature runes are lost.")
    }

    companion object {
        const val CRAFTING_LEVEL = 62
        const val MAX_CHARGES = 1000
    }
}

/**
 * A lair's chest. It opens once per kill with a key, which it uses up: the loot spills onto the
 * floor, the chest count goes up and the chest closes again [CHEST_OPEN_TICKS] ticks later. The
 * first click after a kill (with a key or without) brings the boss back.
 */
class GiantChests
@Inject
constructor(
    private val rewards: GiantRewards,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
) {
    suspend fun ProtectedAccess.open(director: GiantDirector<*>, chest: BoundLocInfo) {
        val run = director.runOf(player) ?: return
        val lair = run.lair
        if (!run.dead) {
            mes(lair.attackingMessage)
            return
        }
        director.chestClicked(run, player)
        if (run.chestOpened) {
            return
        }
        if (invTotal(inv, lair.key) == 0) {
            mes(lair.needKeyMessage)
            return
        }
        run.chestOpened = true
        invDel(inv, lair.key, 1)
        anim(giant_seqs.player_open_chest)
        soundSynth(giant_synths.chest_open)
        delay(1)
        val opened = player.vars[lair.countVarp] + 1
        VarPlayerIntMapSetter.set(player, lair.countVarp, opened)
        mes(SPILL)
        mes(lair.countMessage.format(opened))
        rewards.openChest(run, player)
        locRepo.change(chest, locTypes[lair.chestOpen], CHEST_OPEN_TICKS)
    }

    companion object {
        const val SPILL = "The loot spills out as you open the chest."
        const val CHEST_OPEN_TICKS = 19
    }
}
