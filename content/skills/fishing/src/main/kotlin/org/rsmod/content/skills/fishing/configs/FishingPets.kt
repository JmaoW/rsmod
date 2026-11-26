package org.rsmod.content.skills.fishing.configs

/**
 * The heron's base chances (wiki `Heron#Drop rate`): one roll per catch, at 1 in (base - level *
 * 25). Keyed by the raw fish's name. Fish the wiki does not list use the tuna's chance (a guess).
 */
object FishingPets {
    private const val DEFAULT = 257_770

    private val BASE =
        mapOf(
            "raw shrimps" to 870_330,
            "raw anchovies" to 870_330,
            "raw karambwanji" to 443_697,
            "raw sardine" to 1_056_000,
            "raw herring" to 1_056_000,
            "raw mackerel" to 1_147_827,
            "raw cod" to 1_147_827,
            "raw bass" to 1_147_827,
            "raw trout" to 923_616,
            "raw salmon" to 923_616,
            "raw pike" to 305_792,
            "raw rainbow fish" to 137_739,
            "raw tuna" to 257_770,
            "raw swordfish" to 257_770,
            "raw lobster" to 116_129,
            "raw cave eel" to 257_770,
            "raw monkfish" to 138_583,
            "raw karambwan" to 170_874,
            "leaping trout" to 1_280_862,
            "leaping salmon" to 1_280_862,
            "leaping sturgeon" to 1_280_862,
            "raw shark" to 82_243,
            "infernal eel" to 165_000,
            "raw anglerfish" to 78_649,
            "minnow" to 977_778,
            "raw dark crab" to 149_434,
            "sacred eel" to 99_000,
            "raw guppy" to 820_330,
            "raw cavefish" to 300_792,
            "raw tetra" to 257_770,
            "raw catfish" to 152_120,
        )

    fun baseChance(rawName: String): Int = BASE[rawName.lowercase()] ?: DEFAULT
}
