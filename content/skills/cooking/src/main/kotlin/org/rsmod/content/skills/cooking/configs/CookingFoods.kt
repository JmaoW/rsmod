package org.rsmod.content.skills.cooking.configs

import kotlin.math.floor
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.type.obj.ObjType

internal object CookingObjs : ObjReferences() {
    val raw_beef = find("raw_beef")
    val raw_chicken = find("raw_chicken")
    val raw_rabbit = find("raw_rabbit")
    val cooked_meat = find("cooked_meat")
    val cooked_chicken = find("cooked_chicken")
    val cooked_rabbit = find("cooked_rabbit")
    val burnt_meat = find("burnt_meat")
    val burnt_chicken = find("burnt_chicken")

    val raw_shrimp = find("raw_shrimp")
    val shrimp = find("shrimp")
    val burnt_shrimp = find("burnt_shrimp")
    val raw_anchovies = find("raw_anchovies")
    val anchovies = find("anchovies")
    val raw_sardine = find("raw_sardine")
    val sardine = find("sardine")
    val raw_herring = find("raw_herring")
    val herring = find("herring")
    val raw_mackerel = find("raw_mackerel")
    val mackerel = find("mackerel")
    val raw_trout = find("raw_trout")
    val trout = find("trout")
    val raw_cod = find("raw_cod")
    val cod = find("cod")
    val raw_pike = find("raw_pike")
    val pike = find("pike")
    val raw_salmon = find("raw_salmon")
    val salmon = find("salmon")
    val raw_tuna = find("raw_tuna")
    val tuna = find("tuna")
    val raw_karambwan = find("tbwt_raw_karambwan")
    val cooked_karambwan = find("tbwt_cooked_karambwan")
    val burnt_karambwan = find("tbwt_burnt_karambwan")
    val raw_lobster = find("raw_lobster")
    val lobster = find("lobster")
    val burnt_lobster = find("burnt_lobster")
    val raw_bass = find("raw_bass")
    val bass = find("bass")
    val raw_swordfish = find("raw_swordfish")
    val swordfish = find("swordfish")
    val burnt_swordfish = find("burnt_swordfish")
    val raw_monkfish = find("raw_monkfish")
    val monkfish = find("monkfish")
    val burnt_monkfish = find("burnt_monkfish")
    val raw_shark = find("raw_shark")
    val shark = find("shark")
    val burnt_shark = find("burnt_shark")
    val raw_anglerfish = find("raw_anglerfish")
    val anglerfish = find("anglerfish")
    val burnt_anglerfish = find("burnt_anglerfish")

    /** Generic "Burnt fish" items. Each one is the same item with a different id. */
    val burnt_fish_1 = find("burntfish1")
    val burnt_fish_2 = find("burntfish2")
    val burnt_fish_3 = find("burntfish3")
    val burnt_fish_4 = find("burntfish4")
    val burnt_fish_5 = find("burntfish5")

    val bread_dough = find("bread_dough")
    val bread = find("bread")
    val burnt_bread = find("burnt_bread")
    val uncooked_redberry_pie = find("uncooked_redberry_pie")
    val redberry_pie = find("redberry_pie")
    val uncooked_meat_pie = find("uncooked_meat_pie")
    val meat_pie = find("meat_pie")
    val uncooked_apple_pie = find("uncooked_apple_pie")
    val apple_pie = find("apple_pie")
    val burnt_pie = find("burnt_pie")
    val uncooked_pizza = find("uncooked_pizza")
    val plain_pizza = find("plain_pizza")
    val burnt_pizza = find("burnt_pizza")
}

internal object CookingSeqs : SeqReferences() {
    val range = find("human_cooking")
    val fire = find("human_firecooking")
}

/** What a food is being cooked on. Only meat, fish, and stew can be cooked on an open fire. */
internal enum class HeatSource {
    Fire,
    Range,
}

/**
 * The wiki's success rate values for a food: the chance to cook it without burning at level 1 and
 * level 99, out of 256. The chance at any level in between is interpolated linearly.
 */
internal data class CookRate(val low: Int, val high: Int) {
    /** The chance (from `0.0` to `1.0`) of successfully cooking the food at Cooking [level]. */
    fun chance(level: Int): Double {
        val clamped = level.coerceIn(1, 99)
        val rate = (low * (99 - clamped) + high * (clamped - 1)) / 98.0
        return (floor(rate + 0.5) / 256.0).coerceIn(0.0, 1.0)
    }
}

internal class CookableFood(
    val raw: ObjType,
    val cooked: ObjType,
    val burnt: ObjType,
    val level: Int,
    val xp: Double,
    /** Rate when cooked on an open fire, or `null` if the food cannot be cooked on one. */
    val fire: CookRate?,
    val range: CookRate,
    /** Verb shown in chat when the food is cooked. */
    val verb: String = "cook",
) {
    fun rate(source: HeatSource): CookRate? =
        when (source) {
            HeatSource.Fire -> fire
            HeatSource.Range -> range
        }

    /** The lowest Cooking level where the food never burns, or `null` if it always can. */
    fun stopBurnLevel(source: HeatSource): Int? {
        val rate = rate(source) ?: return null
        return (level..99).firstOrNull { rate.chance(it) >= 1.0 }
    }
}

internal object CookingFoods {
    val all: List<CookableFood> =
        listOf(
            // Meat.
            meat(CookingObjs.raw_beef, CookingObjs.cooked_meat, CookingObjs.burnt_meat),
            meat(CookingObjs.raw_chicken, CookingObjs.cooked_chicken, CookingObjs.burnt_chicken),
            meat(CookingObjs.raw_rabbit, CookingObjs.cooked_rabbit, CookingObjs.burnt_meat),

            // Fish.
            fish(
                CookingObjs.raw_shrimp,
                CookingObjs.shrimp,
                CookingObjs.burnt_shrimp,
                1,
                30.0,
                128,
                512,
            ),
            fish(
                CookingObjs.raw_anchovies,
                CookingObjs.anchovies,
                CookingObjs.burnt_fish_1,
                1,
                30.0,
                128,
                512,
            ),
            fish(
                CookingObjs.raw_sardine,
                CookingObjs.sardine,
                CookingObjs.burnt_fish_1,
                1,
                40.0,
                118,
                492,
            ),
            fish(
                CookingObjs.raw_herring,
                CookingObjs.herring,
                CookingObjs.burnt_fish_2,
                5,
                50.0,
                108,
                472,
            ),
            fish(
                CookingObjs.raw_mackerel,
                CookingObjs.mackerel,
                CookingObjs.burnt_fish_3,
                10,
                60.0,
                98,
                452,
            ),
            fish(
                CookingObjs.raw_trout,
                CookingObjs.trout,
                CookingObjs.burnt_fish_2,
                15,
                70.0,
                88,
                432,
            ),
            fish(
                CookingObjs.raw_cod,
                CookingObjs.cod,
                CookingObjs.burnt_fish_3,
                18,
                75.0,
                fireLow = 83,
                fireHigh = 422,
                rangeLow = 88,
                rangeHigh = 432,
            ),
            fish(
                CookingObjs.raw_pike,
                CookingObjs.pike,
                CookingObjs.burnt_fish_2,
                20,
                80.0,
                78,
                412,
            ),
            fish(
                CookingObjs.raw_salmon,
                CookingObjs.salmon,
                CookingObjs.burnt_fish_2,
                25,
                90.0,
                68,
                392,
            ),
            fish(
                CookingObjs.raw_tuna,
                CookingObjs.tuna,
                CookingObjs.burnt_fish_4,
                30,
                100.0,
                58,
                372,
            ),
            fish(
                CookingObjs.raw_karambwan,
                CookingObjs.cooked_karambwan,
                CookingObjs.burnt_karambwan,
                30,
                190.0,
                70,
                255,
            ),
            fish(
                CookingObjs.raw_lobster,
                CookingObjs.lobster,
                CookingObjs.burnt_lobster,
                40,
                120.0,
                38,
                332,
            ),
            fish(
                CookingObjs.raw_bass,
                CookingObjs.bass,
                CookingObjs.burnt_fish_5,
                43,
                130.0,
                33,
                312,
            ),
            fish(
                CookingObjs.raw_swordfish,
                CookingObjs.swordfish,
                CookingObjs.burnt_swordfish,
                45,
                140.0,
                fireLow = 18,
                fireHigh = 292,
                rangeLow = 30,
                rangeHigh = 310,
            ),
            fish(
                CookingObjs.raw_monkfish,
                CookingObjs.monkfish,
                CookingObjs.burnt_monkfish,
                62,
                150.0,
                fireLow = 11,
                fireHigh = 275,
                rangeLow = 13,
                rangeHigh = 280,
            ),
            fish(
                CookingObjs.raw_shark,
                CookingObjs.shark,
                CookingObjs.burnt_shark,
                80,
                210.0,
                fireLow = 1,
                fireHigh = 202,
                rangeLow = 1,
                rangeHigh = 232,
            ),
            fish(
                CookingObjs.raw_anglerfish,
                CookingObjs.anglerfish,
                CookingObjs.burnt_anglerfish,
                84,
                230.0,
                fireLow = 1,
                fireHigh = 200,
                rangeLow = 1,
                rangeHigh = 220,
            ),

            // Baked goods. These can only be cooked on a range.
            baked(
                CookingObjs.bread_dough,
                CookingObjs.bread,
                CookingObjs.burnt_bread,
                1,
                40.0,
                118,
                492,
            ),
            baked(
                CookingObjs.uncooked_redberry_pie,
                CookingObjs.redberry_pie,
                CookingObjs.burnt_pie,
                10,
                78.0,
                98,
                452,
            ),
            baked(
                CookingObjs.uncooked_meat_pie,
                CookingObjs.meat_pie,
                CookingObjs.burnt_pie,
                20,
                110.0,
                78,
                412,
            ),
            baked(
                CookingObjs.uncooked_apple_pie,
                CookingObjs.apple_pie,
                CookingObjs.burnt_pie,
                30,
                130.0,
                58,
                372,
            ),
            baked(
                CookingObjs.uncooked_pizza,
                CookingObjs.plain_pizza,
                CookingObjs.burnt_pizza,
                35,
                143.0,
                48,
                352,
            ),
        )

    private val byRawId: Map<Int, CookableFood> by lazy { all.associateBy { it.raw.id } }

    fun forRaw(raw: ObjType): CookableFood? = byRawId[raw.id]

    private fun meat(raw: ObjType, cooked: ObjType, burnt: ObjType): CookableFood =
        fish(raw, cooked, burnt, 1, 30.0, 128, 512)

    private fun fish(
        raw: ObjType,
        cooked: ObjType,
        burnt: ObjType,
        level: Int,
        xp: Double,
        low: Int,
        high: Int,
    ): CookableFood = fish(raw, cooked, burnt, level, xp, low, high, low, high)

    private fun fish(
        raw: ObjType,
        cooked: ObjType,
        burnt: ObjType,
        level: Int,
        xp: Double,
        fireLow: Int,
        fireHigh: Int,
        rangeLow: Int,
        rangeHigh: Int,
    ): CookableFood =
        CookableFood(
            raw = raw,
            cooked = cooked,
            burnt = burnt,
            level = level,
            xp = xp,
            fire = CookRate(fireLow, fireHigh),
            range = CookRate(rangeLow, rangeHigh),
        )

    private fun baked(
        raw: ObjType,
        cooked: ObjType,
        burnt: ObjType,
        level: Int,
        xp: Double,
        low: Int,
        high: Int,
    ): CookableFood =
        CookableFood(
            raw = raw,
            cooked = cooked,
            burnt = burnt,
            level = level,
            xp = xp,
            fire = null,
            range = CookRate(low, high),
            verb = "bake",
        )
}
