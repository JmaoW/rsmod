package org.rsmod.content.skills.woodcutting.configs

/**
 * The beaver's base chances (wiki `Beaver#Drop rates`): one roll per log cut, at 1 in (base -
 * level * 25). Keyed by the log's name; other trees use the normal tree's chance.
 */
object WoodcuttingPets {
    private const val DEFAULT = 317_647

    private val BASE =
        mapOf(
            "logs" to 317_647,
            "achey tree logs" to 317_647,
            "oak logs" to 361_146,
            "willow logs" to 289_286,
            "teak logs" to 264_336,
            "juniper logs" to 360_000,
            "maple logs" to 221_918,
            "bark" to 214_367,
            "mahogany logs" to 220_623,
            "arctic pine logs" to 145_758,
            "yew logs" to 145_013,
            "blisterwood logs" to 289_286,
            "camphor logs" to 145_013,
            "magic logs" to 72_321,
            "ironwood logs" to 72_321,
            "redwood logs" to 72_321,
            "rosewood logs" to 72_321,
        )

    fun baseChance(logName: String): Int = BASE[logName.lowercase()] ?: DEFAULT
}
