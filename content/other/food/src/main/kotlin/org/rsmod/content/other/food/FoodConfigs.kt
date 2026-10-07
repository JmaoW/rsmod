package org.rsmod.content.other.food

import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.type.obj.ObjType

internal object FoodObjs : ObjReferences() {
    val shrimp = find("shrimp")
    val anchovies = find("anchovies")
    val sardine = find("sardine")
    val herring = find("herring")
    val mackerel = find("mackerel")
    val trout = find("trout")
    val cod = find("cod")
    val pike = find("pike")
    val salmon = find("salmon")
    val tuna = find("tuna")
    val lobster = find("lobster")
    val bass = find("bass")
    val swordfish = find("swordfish")
    val monkfish = find("monkfish")
    val cooked_karambwan = find("tbwt_cooked_karambwan")
    val shark = find("shark")
    val anglerfish = find("anglerfish")
}

internal object FoodSeqs : SeqReferences() {
    val eat = find("human_eat")
}

/** How a food delays the player. Each kind has its own cooldown, so one of each can be eaten. */
internal enum class FoodKind(
    /** Ticks before another food of this kind can be eaten. */
    val eatDelay: Int,
    /** Ticks added to the player's next attack. */
    val attackDelay: Int,
) {
    Standard(eatDelay = 3, attackDelay = 3),

    /** Combo food can be eaten on the same tick as standard food. */
    Combo(eatDelay = 2, attackDelay = 2),
}

internal class Food(
    val obj: ObjType,
    val kind: FoodKind,
    /** The hitpoints restored, given the player's base Hitpoints level. */
    val heal: (baseHitpoints: Int) -> Int,
    /** `true` if the food can raise hitpoints above the base level, by the amount it heals. */
    val overheals: Boolean = false,
)

internal object Foods {
    val all: List<Food> =
        listOf(
            fixed(FoodObjs.shrimp, 3),
            fixed(FoodObjs.anchovies, 1),
            fixed(FoodObjs.sardine, 4),
            fixed(FoodObjs.herring, 5),
            fixed(FoodObjs.mackerel, 6),
            fixed(FoodObjs.trout, 7),
            fixed(FoodObjs.cod, 7),
            fixed(FoodObjs.pike, 8),
            fixed(FoodObjs.salmon, 9),
            fixed(FoodObjs.tuna, 10),
            fixed(FoodObjs.lobster, 12),
            fixed(FoodObjs.bass, 13),
            fixed(FoodObjs.swordfish, 14),
            fixed(FoodObjs.monkfish, 16),
            fixed(FoodObjs.cooked_karambwan, 18, FoodKind.Combo),
            fixed(FoodObjs.shark, 20),
            Food(FoodObjs.anglerfish, FoodKind.Standard, ::anglerfishHeal, overheals = true),
        )

    /** The wiki's formula: `floor(x / 10) + 2 * floor(x / 25) + 5 * floor(x / 93) + 2`. */
    fun anglerfishHeal(baseHitpoints: Int): Int =
        baseHitpoints / 10 + 2 * (baseHitpoints / 25) + 5 * (baseHitpoints / 93) + 2

    private fun fixed(obj: ObjType, heal: Int, kind: FoodKind = FoodKind.Standard): Food =
        Food(obj, kind, { heal })
}
