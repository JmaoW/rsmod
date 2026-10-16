package org.rsmod.content.skills.runecraft.configs

import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.varbit.VarBitType

/**
 * An essence pouch. What it holds is kept in two varbits the cache already has: how many essence
 * ([count]) and which kind ([kind], see [PouchKind]). Pouches never wear out here, so none of the
 * degraded forms are used.
 *
 * @property level the Runecraft level needed to use it.
 */
class Pouch(
    val obj: ObjType,
    val name: String,
    val level: Int,
    private val fixedCapacity: Int,
    val count: VarBitType,
    val kind: VarBitType,
) {
    /** How much essence it holds for a player of [runecraftLevel]. */
    fun capacity(runecraftLevel: Int): Int =
        if (this === Pouches.colossal) colossalCapacity(runecraftLevel) else fixedCapacity

    private companion object {
        /** 8 at level 25 rising to 40 at level 85, which the wiki gives as its range. */
        fun colossalCapacity(level: Int): Int = (8 + (level - 25) * 32 / 60).coerceIn(8, 40)
    }
}

/** The kind of essence a pouch holds. [None] is an empty pouch. */
enum class PouchKind(val value: Int) {
    None(0),
    Rune(1),
    Pure(2);

    companion object {
        fun of(value: Int): PouchKind = entries.firstOrNull { it.value == value } ?: None
    }
}

object Pouches {
    val small =
        Pouch(
            RunecraftObjs.small_pouch,
            "small",
            level = 1,
            fixedCapacity = 3,
            count = RunecraftVarBits.small_pouch,
            kind = RunecraftVarBits.small_pouch_type,
        )
    val medium =
        Pouch(
            RunecraftObjs.medium_pouch,
            "medium",
            level = 25,
            fixedCapacity = 6,
            count = RunecraftVarBits.medium_pouch,
            kind = RunecraftVarBits.medium_pouch_type,
        )
    val large =
        Pouch(
            RunecraftObjs.large_pouch,
            "large",
            level = 50,
            fixedCapacity = 9,
            count = RunecraftVarBits.large_pouch,
            kind = RunecraftVarBits.large_pouch_type,
        )
    val giant =
        Pouch(
            RunecraftObjs.giant_pouch,
            "giant",
            level = 75,
            fixedCapacity = 12,
            count = RunecraftVarBits.giant_pouch,
            kind = RunecraftVarBits.giant_pouch_type,
        )
    val colossal =
        Pouch(
            RunecraftObjs.colossal_pouch,
            "colossal",
            level = 25,
            fixedCapacity = 40,
            count = RunecraftVarBits.colossal_pouch,
            kind = RunecraftVarBits.colossal_pouch_type,
        )

    /** The four pouches the colossal one is made from, smallest first. */
    val standard: List<Pouch> = listOf(small, medium, large, giant)

    val all: List<Pouch> = standard + colossal
}
