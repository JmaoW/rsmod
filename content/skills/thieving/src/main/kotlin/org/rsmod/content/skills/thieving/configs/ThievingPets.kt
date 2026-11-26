package org.rsmod.content.skills.thieving.configs

/**
 * Rocky's base chances (wiki `Rocky#Drop rates`): one roll per successful pickpocket or theft from
 * a stall, at 1 in (base - level * 25). Keyed by the target's or stall's key. Targets the wiki does
 * not list use the common pickpocket chance; stalls use the common stall chance (monkey food and
 * general stalls are guesses).
 */
object ThievingPets {
    private const val PICKPOCKET = 257_211
    private const val STALL = 36_490

    private val PICKPOCKETS =
        mapOf(
            "watchman" to 134_625,
            "paladin" to 127_056,
            "gnome" to 108_718,
            "hero" to 99_175,
            "elf_lletya" to 99_175,
            "elf_prifddinas" to 99_175,
            "vyre" to 99_175,
            "tzhaar_hur" to 176_743,
        )

    private val STALLS =
        mapOf(
            "vegetable" to 206_777,
            "bakery" to 124_066,
            "crafting" to 47_718,
            "monkey_food" to 47_718,
            "monkey_general" to 47_718,
            "silk" to 68_926,
            "tea" to 68_926,
            "fruit" to 124_066,
        )

    fun pickpocketChance(targetKey: String): Int = PICKPOCKETS[targetKey] ?: PICKPOCKET

    fun stallChance(stallKey: String): Int = STALLS[stallKey] ?: STALL
}
