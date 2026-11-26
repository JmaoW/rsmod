package org.rsmod.content.skills.mining.configs

/**
 * The rock golem's base chances (wiki `Rock golem#Drop rates`): one roll per ore mined, at 1 in
 * (base - level * 25). Keyed by the mined item's name. The golem comes in the colour of the ore.
 */
object MiningPets {
    private const val DEFAULT = 741_600

    private val BASE =
        mapOf(
            "coal" to 290_640,
            "gold ore" to 296_640,
            "mithril ore" to 148_320,
            "lovakite ore" to 245_562,
            "adamantite ore" to 59_328,
            "runite ore" to 42_377,
            "amethyst" to 46_350,
        )

    /** Items that never roll the pet (wiki: essence and salt mines). */
    private val NO_PET = setOf("rune essence", "pure essence", "daeyalt essence")

    private val GOLEM_LOOKS =
        mapOf(
            "tin ore" to "tin",
            "copper ore" to "copper",
            "iron ore" to "iron",
            "blurite ore" to "blurite",
            "silver ore" to "silver",
            "coal" to "coal",
            "gold ore" to "gold",
            "mithril ore" to "mithril",
            "adamantite ore" to "adamantite",
            "runite ore" to "runite",
            "amethyst" to "amethyst",
            "lovakite ore" to "lovakite",
            "elemental ore" to "elemental",
            "daeyalt ore" to "daeyalt",
        )

    /** The base chance for mining [itemName], or `null` when it gives no pet roll. */
    fun baseChance(itemName: String): Int? {
        val name = itemName.lowercase()
        if (name in NO_PET) {
            return null
        }
        if (name.startsWith("uncut ")) {
            return GEM_ROCKS
        }
        return BASE[name] ?: DEFAULT
    }

    /** The golem's look for [itemName]: the ore's colour, or plain rock. */
    fun golemLook(itemName: String): String {
        val name = itemName.lowercase()
        if (name.startsWith("granite")) {
            return "granite"
        }
        return GOLEM_LOOKS[name] ?: "rock"
    }

    private const val GEM_ROCKS = 211_886
}
