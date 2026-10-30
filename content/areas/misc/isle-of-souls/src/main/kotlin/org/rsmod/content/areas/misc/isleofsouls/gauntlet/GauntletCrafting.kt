package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.events.EventBus
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.stat.StatType

/**
 * What a player makes with the Gauntlet's items: the singing bowl's products and the steps of an
 * Egniol potion. Kept apart from the script that wires the stations so that it can be driven
 * directly.
 */
class GauntletCrafting
@Inject
constructor(
    private val gauntlet: Gauntlet,
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
    private val eventBus: EventBus,
) {
    fun ProtectedAccess.advance(stat: StatType, xp: Double) {
        statAdvance(stat, xp * xpMods.get(player, stat))
    }

    /** Where a worn obj is worn, or `null`. */
    fun ProtectedAccess.wornSlot(obj: ObjType): Wearpos? =
        Wearpos.entries.firstOrNull { worn[it.slot].isType(obj) }

    /** How many of [obj] the player has to use: the pack's, plus the one they may be wearing. */
    fun ProtectedAccess.available(obj: ObjType): Int =
        inv.count(objTypes[obj]) + if (wornSlot(obj) != null) 1 else 0

    /**
     * Makes [recipe] up to [times] times (a piece of gear only once): each takes its materials and
     * shards. A needed piece that is worn is upgraded where it is worn, so a player can improve
     * their armour without taking it off.
     */
    fun ProtectedAccess.make(run: GauntletRun, recipe: GauntletRecipe, times: Int) {
        val shard = run.mode.items.shard
        val wanted = if (recipe.gear != null) 1 else times.coerceAtLeast(1)
        var made = 0
        while (made < wanted) {
            val affordable =
                inv.count(objTypes[shard]) >= recipe.shards &&
                    recipe.needs.all { (obj, need) -> available(obj) >= need }
            if (!affordable) {
                break
            }
            val upgradeSlot =
                recipe.needs
                    .firstOrNull { (obj, need) -> inv.count(objTypes[obj]) < need }
                    ?.let { wornSlot(it.first) }
            val freed =
                recipe.needs.sumOf { (obj, need) -> if (objTypes[obj].stackable) 0 else need }
            if (upgradeSlot == null && inv.freeSpace() + freed < 1) {
                mes("You don't have enough inventory space.")
                break
            }
            for ((obj, need) in recipe.needs) {
                if (inv.count(objTypes[obj]) >= need) {
                    invDel(inv, obj, need)
                } else if (upgradeSlot != null && worn[upgradeSlot.slot].isType(obj)) {
                    worn[upgradeSlot.slot] = null
                }
            }
            if (recipe.shards > 0) {
                invDel(inv, shard, recipe.shards)
            }
            if (upgradeSlot != null) {
                worn[upgradeSlot.slot] = InvObj(recipe.product)
                eventBus.publish(
                    HeldEquipEvents.WearposChange(player, upgradeSlot, objTypes[recipe.product])
                )
                rebuildAppearance()
            } else {
                invAdd(inv, recipe.product)
            }
            if (recipe.xp > 0) {
                advance(stats.crafting, recipe.xp)
                advance(stats.smithing, recipe.xp)
            }
            when {
                recipe.gear != null ->
                    run.addPoints(player, GauntletRecipes.GEAR_POINTS[recipe.tier])
                recipe.product.id == run.mode.items.comboFood.id ->
                    run.addPoints(player, GauntletRecipes.COMBO_FOOD_POINTS)
            }
            made++
        }
        if (made == 0) {
            mes("You do not have the materials required to make that.")
            return
        }
        mes("With the help of the crystal bowl, you sing a beautiful song and shape the crystals.")
    }

    fun ProtectedAccess.addLeaf(items: GauntletItemSet) {
        if (gauntlet.runOf(player) == null) return
        if (!invDel(inv, items.grymLeaf, 1).success) return
        if (!invDel(inv, GauntletShared.waterVial, 1).success) return
        invAdd(inv, GauntletShared.grymPotion)
        anim(gauntlet_seqs.mix)
        mes("You put the grym leaf into the vial.")
    }

    fun ProtectedAccess.grind(items: GauntletItemSet) {
        if (gauntlet.runOf(player) == null) return
        if (inv.count(objTypes[items.shard]) < DUST_PER_POTION) {
            mes("You need at least $DUST_PER_POTION shards to make anything useful.")
            return
        }
        invDel(inv, items.shard, DUST_PER_POTION)
        invAdd(inv, items.dust, DUST_PER_POTION)
        anim(gauntlet_seqs.grind)
        mes("You grind the shards into dust.")
    }

    fun ProtectedAccess.finishPotion(items: GauntletItemSet) {
        if (gauntlet.runOf(player) == null) return
        if (inv.count(objTypes[items.dust]) < DUST_PER_POTION) {
            mes("You need $DUST_PER_POTION crystal dust to finish the potion.")
            return
        }
        invDel(inv, items.dust, DUST_PER_POTION)
        invDel(inv, GauntletShared.grymPotion, 1)
        invAdd(inv, GauntletShared.egniol)
        anim(gauntlet_seqs.mix)
        advance(stats.herblore, EGNIOL_XP)
        mes("You add the crystal dust to the vial and make an Egniol potion.")
    }

    companion object {
        const val DUST_PER_POTION = 10
        const val EGNIOL_XP = 10.0
    }
}
