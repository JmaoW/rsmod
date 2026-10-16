package org.rsmod.content.other.food

import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.UnpackedObjType

/**
 * The objs of the cache by name, without the noted copies and placeholders. The wiki, and so
 * [FoodDef], names items rather than numbering them, and a name can have several objs: a minigame
 * keeps its own shark, for instance.
 */
internal class FoodIndex(types: Collection<UnpackedObjType>) {
    private val all: Map<String, List<UnpackedObjType>> =
        types
            .filter { it.certtemplate <= 0 && it.placeholdertemplate <= 0 }
            .sortedBy { it.id }
            .groupBy { key(it.name) }

    /** The objs called [name] that have an `Eat` or `Drink` first option, lowest id first. */
    fun edible(name: String): List<ObjType> =
        all[key(name)].orEmpty().filter {
            it.iop.getOrNull(0).let { op -> op == EAT || op == DRINK }
        }

    /** The lowest id obj called [name], or `null` if there is none. */
    fun item(name: String): ObjType? = all[key(name)]?.firstOrNull()

    private companion object {
        /** Names are compared without accents and other characters the cache stores oddly. */
        fun key(name: String): String = name.filter { it.code < 128 }

        const val EAT = "Eat"
        const val DRINK = "Drink"
    }
}
