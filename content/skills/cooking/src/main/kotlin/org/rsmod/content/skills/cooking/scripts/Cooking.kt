package org.rsmod.content.skills.cooking.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.cooking.configs.CookableFood
import org.rsmod.content.skills.cooking.configs.CookingFoods
import org.rsmod.content.skills.cooking.configs.CookingSeqs
import org.rsmod.content.skills.cooking.configs.HeatSource
import org.rsmod.events.UnboundEvent
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the "what would you like to cook?" interface (everything is cooked, like selecting "All")
// - Lumbridge Castle and Hosidius ranges, cooking gauntlets, and the cooking skillcape
// - stews, cakes, pitta bread, wine, and the other recipes
// - cooking on a fire that the player has lit (firemaking is not implemented yet)
//
// Cooking continues through the otherwise unused op4 of the fire or range once the first item has
// been started, mirroring how the gathering skills keep their loop alive.
class Cooking
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
    private val mapClock: MapClock,
) : PluginScript() {
    /** The food each player is currently cooking, so the loop knows what to keep cooking. */
    private val cooking = WeakHashMap<Player, CookableFood>()

    override fun ScriptContext.startup() {
        for (type in locTypes.values) {
            val source = heatSource(type) ?: continue
            onOpLocU(type) { useOn(it.loc, source, it.objType) }
            if (type.op.getOrNull(0) == "Cook") {
                onOpLoc1(type) { cookAnything(it.loc, source) }
            }
            onOpLoc4(type) { cookNext(it.loc, source) }
        }
    }

    private fun ProtectedAccess.useOn(loc: BoundLocInfo, source: HeatSource, obj: ObjType) {
        val food = CookingFoods.forRaw(obj)
        if (food == null) {
            mes("Nothing interesting happens.")
            return
        }
        start(loc, source, food)
    }

    private fun ProtectedAccess.cookAnything(loc: BoundLocInfo, source: HeatSource) {
        val food =
            inv.filterNotNull { CookingFoods.forRaw(objTypes[it]) != null }
                .firstNotNullOfOrNull { CookingFoods.forRaw(objTypes[it]) }
        if (food == null) {
            mes("You have nothing to cook.")
            return
        }
        start(loc, source, food)
    }

    private fun ProtectedAccess.start(loc: BoundLocInfo, source: HeatSource, food: CookableFood) {
        if (!canCook(food, source)) {
            return
        }
        cooking[player] = food
        actionDelay = mapClock + COOK_DELAY
        skillAnimDelay = mapClock + ANIM_INTERVAL
        anim(animFor(source))
        opLoc4(loc)
    }

    private fun ProtectedAccess.cookNext(loc: BoundLocInfo, source: HeatSource) {
        val food = cooking[player] ?: return
        if (food.raw !in inv) {
            stop()
            return
        }
        if (!canCook(food, source)) {
            stop()
            return
        }

        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + ANIM_INTERVAL
            anim(animFor(source))
        }

        if (actionDelay < mapClock) {
            actionDelay = mapClock + COOK_DELAY
        } else if (actionDelay == mapClock) {
            cookOne(food, source)
            if (food.raw !in inv) {
                stop()
                return
            }
        }

        opLoc4(loc)
    }

    private fun ProtectedAccess.cookOne(food: CookableFood, source: HeatSource) {
        val rate = food.rate(source) ?: return
        val roll =
            CookRoll(player, food.raw, random.randomDouble() < rate.chance(player.cookingLvl))
        publish(roll)
        val success = roll.success
        val result = if (success) food.cooked else food.burnt
        if (!invReplace(inv, food.raw, 1, result).success) {
            return
        }

        food.leaves?.let { invAdd(inv, it, 1) }

        val name = objTypes[result].name.lowercase()
        if (success) {
            val xp = food.xp * xpMods.get(player, stats.cooking)
            spam("You successfully ${food.verb} ${withArticle(name)}.")
            statAdvance(stats.cooking, xp)
            publish(Cooked(player, food.raw, food.cooked, source == HeatSource.Range))
        } else {
            spam("You accidentally burn the ${objTypes[food.cooked].name.lowercase()}.")
            publish(Burnt(player, food.raw, food.burnt))
        }
    }

    /** Checks level and heat source. Sends a message for the first problem. */
    private fun ProtectedAccess.canCook(food: CookableFood, source: HeatSource): Boolean {
        if (food.rate(source) == null) {
            mes("You need to cook this on a range.")
            return false
        }
        if (player.cookingLvl < food.level) {
            mes("You need a Cooking level of ${food.level} to cook this.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.stop() {
        cooking.remove(player)
        resetAnim()
    }

    private fun animFor(source: HeatSource) =
        when (source) {
            HeatSource.Fire -> CookingSeqs.fire
            HeatSource.Range -> CookingSeqs.range
        }

    /** What cooking a raw item gives, for content that cooks food without a fire or range. */
    class Recipe(val cooked: ObjType, val burnt: ObjType, val xp: Double)

    /**
     * Published on every cook attempt, after the normal roll. Listeners can change whether the
     * attempt is a [success], for example to stop food from burning.
     */
    class CookRoll(val player: Player, val raw: ObjType, var success: Boolean) : UnboundEvent

    /** Published once a food has been cooked successfully. [onRange] is false for a fire. */
    class Cooked(
        val player: Player,
        val raw: ObjType,
        val cooked: ObjType,
        val onRange: Boolean = false,
    ) : UnboundEvent

    /** Published once a food has burnt while being cooked. */
    class Burnt(val player: Player, val raw: ObjType, val burnt: ObjType) : UnboundEvent

    companion object {
        /**
         * Ticks to wait before each item is cooked, minus one. The loop spends one extra tick
         * between items, so this gives a four tick interval between cooked items.
         */
        private const val COOK_DELAY = 3
        private const val ANIM_INTERVAL = 4

        private val UNCOUNTABLE =
            setOf("bread", "cooked meat", "cooked chicken", "cooked rabbit", "stew", "curry")

        /** The recipe for cooking [raw], or `null` if it cannot be cooked. */
        fun recipe(raw: ObjType): Recipe? =
            CookingFoods.forRaw(raw)?.let { Recipe(it.cooked, it.burnt, it.xp) }

        internal fun heatSource(type: UnpackedLocType): HeatSource? {
            val name = type.name
            return when {
                name.equals("Fire", ignoreCase = true) && type.op.getOrNull(0).isNullOrBlank() ->
                    HeatSource.Fire
                name.equals("Range", ignoreCase = true) ||
                    name.equals("Cooking range", ignoreCase = true) -> HeatSource.Range
                else -> null
            }
        }

        internal fun withArticle(name: String): String =
            when {
                name in UNCOUNTABLE || name.endsWith("s") -> "some $name"
                name.first() in "aeiou" -> "an $name"
                else -> "a $name"
            }
    }
}
