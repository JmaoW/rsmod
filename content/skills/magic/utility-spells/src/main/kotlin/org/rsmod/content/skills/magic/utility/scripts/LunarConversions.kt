package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.magic.utility.configs.LunarRecipes
import org.rsmod.content.skills.magic.utility.configs.lunar_objs
import org.rsmod.content.skills.magic.utility.configs.lunar_seqs
import org.rsmod.content.skills.magic.utility.configs.lunar_spotanims
import org.rsmod.content.skills.magic.utility.configs.utility_objs
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// The Lunar spells that turn what is in the inventory into something else:
// - Bake Pie bakes every uncooked pie, one set of runes each, without burning them
// - Plank Make saws one log into a plank for coins
// - String Jewellery strings every unstrung amulet, symbol and emblem, one set of runes each
// - Superglass Make turns buckets of sand and seaweed or soda ash into molten glass
// - Tan Leather tans up to five hides
// - Spin Flax spins up to five flax into bowstrings
// - Humidify fills every empty container it knows
// - Recharge Dragonstone tops up one piece of dragonstone jewellery to four charges
//
// Left out: snake hides (nothing on the isle gives them) and the cowhide hard leather option, and
// the pies that need a botanical, mushroom or dragonfruit (not in this cache). The wiki's quest and
// diary requirements do not apply on the isle.
class LunarConversions
@Inject
constructor(
    private val caster: SpellCaster,
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
    private val objRepo: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        with(caster) {
            onSpell(objs.spell_bake_pie) { bakePies() }
            onItemSpell(objs.spell_plank_make) { makePlank(it) }
            onSpell(objs.spell_string_jewelry) { stringJewellery() }
            onSpell(objs.spell_superglass) { makeGlass() }
            onSpell(objs.spell_tan_leather) { tanLeather() }
            onSpell(objs.spell_spin_flax) { spinFlax() }
            onSpell(objs.spell_humidify) { humidify() }
            onItemSpell(objs.spell_recharge_dragonstone) { recharge(it) }
        }
    }

    private fun ProtectedAccess.cast(spell: ObjType, cooldown: Int): Boolean =
        with(caster) { cast(spell, cooldown) } != null

    private fun ProtectedAccess.effects(seq: SeqType, spot: SpotanimType?) {
        anim(seq)
        if (spot != null) {
            spotanim(spot, height = SPOTANIM_HEIGHT)
        }
    }

    /** Swaps one [from] in the inventory for [to]. */
    private fun ProtectedAccess.swapOne(from: ObjType, to: ObjType): Boolean {
        val slot = inv.indexOfFirst { it?.id == from.id }
        if (slot == -1) {
            return false
        }
        return invReplaceSlot(inv, slot, 1, to).success
    }

    private fun ProtectedAccess.bakePies() {
        var baked = 0
        var tooHigh: String? = null
        var anyPies = false
        outer@ for (pie in LunarRecipes.pies) {
            var count = invTotal(inv, pie.raw)
            if (count == 0) {
                continue
            }
            anyPies = true
            if (player.cookingLvl < pie.level) {
                tooHigh = tooHigh ?: "You need a Cooking level of ${pie.level} to bake that."
                continue
            }
            while (count > 0) {
                if (!cast(objs.spell_bake_pie, COOLDOWN_PIE)) {
                    break@outer
                }
                if (!swapOne(pie.raw, pie.cooked)) {
                    break
                }
                statAdvance(stats.cooking, pie.xp * xpMods.get(player, stats.cooking))
                baked++
                count--
            }
        }
        when {
            baked > 0 -> {
                effects(lunar_seqs.cast, lunar_spotanims.bake_pie)
                publish(Converted(player, objs.spell_bake_pie, baked))
            }
            !anyPies -> mes("You have no uncooked pies to bake.")
            tooHigh != null -> mes(tooHigh)
        }
    }

    private fun ProtectedAccess.makePlank(target: SpellTarget) {
        val log = objTypes.uncert(target.obj)
        val recipe = LunarRecipes.planks.firstOrNull { it.log.id == log.id }
        if (recipe == null) {
            mes("You can only cast this spell on logs.")
            return
        }
        if (invTotal(inv, utility_objs.coins) < recipe.cost) {
            mes("You need ${recipe.cost} coins to do that.")
            return
        }
        if (!cast(objs.spell_plank_make, COOLDOWN)) {
            return
        }
        invDel(inv, target.obj, 1, slot = target.slot)
        invDel(inv, utility_objs.coins, recipe.cost)
        invAdd(inv, recipe.plank, 1)
        effects(lunar_seqs.plank_make, lunar_spotanims.plank_make)
        publish(Converted(player, objs.spell_plank_make, 1))
    }

    private fun ProtectedAccess.stringJewellery() {
        var strung = 0
        var any = false
        outer@ for ((unstrung, done) in LunarRecipes.strings) {
            var count = invTotal(inv, unstrung)
            if (count > 0) {
                any = true
            }
            while (count > 0) {
                if (!cast(objs.spell_string_jewelry, COOLDOWN)) {
                    break@outer
                }
                if (!swapOne(unstrung, done)) {
                    break
                }
                statAdvance(stats.crafting, STRING_CRAFTING_XP * xpMods.get(player, stats.crafting))
                strung++
                count--
            }
        }
        when {
            strung > 0 -> {
                effects(lunar_seqs.cast, lunar_spotanims.string_jewellery)
                publish(Converted(player, objs.spell_string_jewelry, strung))
            }
            !any -> mes("You have nothing to string.")
        }
    }

    private fun ProtectedAccess.makeGlass() {
        val sand = invTotal(inv, lunar_objs.bucket_sand)
        val seaweed = invTotal(inv, lunar_objs.seaweed)
        val ash = invTotal(inv, lunar_objs.soda_ash)
        val giant = invTotal(inv, lunar_objs.giant_seaweed)
        val regular = seaweed + ash
        val units = regular + giant * GIANT_UNITS
        if (sand == 0 || units == 0) {
            mes("You need buckets of sand and seaweed or soda ash to make glass.")
            return
        }
        if (!cast(objs.spell_superglass, COOLDOWN)) {
            return
        }

        // Each sand takes one unit of seaweed or ash, and a giant seaweed is six units.
        val used = minOf(sand, units)
        val usedRegular = minOf(used, regular)
        val usedGiantUnits = used - usedRegular
        val giantUsed = (usedGiantUnits + GIANT_UNITS - 1) / GIANT_UNITS

        var glass = used
        repeat(usedRegular) { if (random.of(PERCENT) < EXTRA_GLASS_CHANCE) glass++ }
        repeat(usedGiantUnits) { if (random.of(PERCENT) < EXTRA_GLASS_CHANCE_GIANT) glass++ }

        invDel(inv, lunar_objs.bucket_sand, used)
        val fromSeaweed = minOf(usedRegular, seaweed)
        if (fromSeaweed > 0) {
            invDel(inv, lunar_objs.seaweed, fromSeaweed)
        }
        if (usedRegular > fromSeaweed) {
            invDel(inv, lunar_objs.soda_ash, usedRegular - fromSeaweed)
        }
        if (giantUsed > 0) {
            invDel(inv, lunar_objs.giant_seaweed, giantUsed)
        }
        invAddOrDrop(objRepo, lunar_objs.molten_glass, glass)

        val xp = used * SAND_CRAFTING_XP + giantUsed * GIANT_CRAFTING_XP
        statAdvance(stats.crafting, xp * xpMods.get(player, stats.crafting))
        effects(lunar_seqs.cast, lunar_spotanims.superglass)
        publish(Converted(player, objs.spell_superglass, glass))
    }

    private fun ProtectedAccess.tanLeather() {
        val hides = ArrayList<Pair<ObjType, ObjType>>()
        for (recipe in LunarRecipes.hides) {
            repeat(invTotal(inv, recipe.hide)) { hides += recipe.hide to recipe.leather }
        }
        if (hides.isEmpty()) {
            mes("You have no hides to tan.")
            return
        }
        if (!cast(objs.spell_tan_leather, COOLDOWN)) {
            return
        }
        var tanned = 0
        for ((hide, leather) in hides.take(MAX_HIDES)) {
            if (swapOne(hide, leather)) {
                tanned++
            }
        }
        effects(lunar_seqs.cast, lunar_spotanims.tan_leather)
        publish(Converted(player, objs.spell_tan_leather, tanned))
    }

    private fun ProtectedAccess.spinFlax() {
        val flax = minOf(invTotal(inv, lunar_objs.flax), MAX_FLAX)
        if (flax == 0) {
            mes("You have no flax to spin.")
            return
        }
        if (!cast(objs.spell_spin_flax, COOLDOWN_FLAX)) {
            return
        }
        var spun = 0
        repeat(flax) { if (swapOne(lunar_objs.flax, lunar_objs.bow_string)) spun++ }
        statAdvance(stats.crafting, spun * FLAX_CRAFTING_XP * xpMods.get(player, stats.crafting))
        effects(lunar_seqs.cast, lunar_spotanims.flax)
        publish(Converted(player, objs.spell_spin_flax, spun))
    }

    private fun ProtectedAccess.humidify() {
        val empties = LunarRecipes.fills.sumOf { invTotal(inv, it.empty) }
        if (empties == 0) {
            mes("You have nothing to fill.")
            return
        }
        if (!cast(objs.spell_humidify, COOLDOWN_HUMIDIFY)) {
            return
        }
        var filled = 0
        for (fill in LunarRecipes.fills) {
            repeat(invTotal(inv, fill.empty)) { if (swapOne(fill.empty, fill.full)) filled++ }
        }
        effects(lunar_seqs.humidify, lunar_spotanims.humidify)
        publish(Converted(player, objs.spell_humidify, filled))
    }

    private fun ProtectedAccess.recharge(target: SpellTarget) {
        val recipe =
            LunarRecipes.recharges.firstOrNull { recipe ->
                recipe.items.any { it.id == target.obj.id }
            }
        if (recipe == null) {
            if (LunarRecipes.recharges.any { it.full.id == target.obj.id }) {
                mes("That is already fully charged.")
            } else {
                mes(
                    "This spell can only be cast on an amulet of glory, combat bracelet or skills necklace."
                )
            }
            return
        }
        if (!cast(objs.spell_recharge_dragonstone, COOLDOWN)) {
            return
        }
        invReplaceSlot(inv, target.slot, 1, recipe.full)
        effects(lunar_seqs.cast, lunar_spotanims.recharge_dragonstone)
        publish(Converted(player, objs.spell_recharge_dragonstone, 1))
    }

    /** Published once a Lunar [spell] has turned [count] items into something else. */
    class Converted(val player: Player, val spell: ObjType, val count: Int) : UnboundEvent

    private companion object {
        const val COOLDOWN = 3
        const val COOLDOWN_PIE = 3
        const val COOLDOWN_FLAX = 5
        const val COOLDOWN_HUMIDIFY = 4
        const val PERCENT = 100
        const val SPOTANIM_HEIGHT = 92

        const val MAX_HIDES = 5
        const val MAX_FLAX = 5

        const val STRING_CRAFTING_XP = 4.0
        const val FLAX_CRAFTING_XP = 15.0
        const val SAND_CRAFTING_XP = 10.0
        const val GIANT_CRAFTING_XP = 60.0

        /** Seaweed units in a giant seaweed. */
        const val GIANT_UNITS = 6

        /** The chance of an extra molten glass, for each sand used with seaweed or soda ash. */
        const val EXTRA_GLASS_CHANCE = 30

        /** The same, for the sand that goes with giant seaweed. */
        const val EXTRA_GLASS_CHANCE_GIANT = 60
    }
}
