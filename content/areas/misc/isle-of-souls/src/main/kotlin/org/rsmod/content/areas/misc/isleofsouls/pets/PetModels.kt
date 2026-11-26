package org.rsmod.content.areas.misc.isleofsouls.pets

import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.obj.ObjType

/**
 * One look of a pet: the item it is carried as and the follower npc it walks around as. [ops] are
 * the follower's right-click options from the cache, by op number (1 to 5).
 */
class PetForm(val obj: ObjType, val npc: NpcType, val label: String, val ops: Map<Int, String>) {
    /** The op number of the follower option called [text] (`Pick-up`, `Talk-to`, ...), if any. */
    fun op(vararg text: String): Int? =
        ops.entries.firstOrNull { (_, op) -> text.any { it.equals(op, ignoreCase = true) } }?.key
}

/**
 * A pet of the collection log. [bit] is its place in the pet insurance enum (985): the bit of the
 * `pet_insurance_*` bitmasks Probita's screen reads, and of the isle's own insured bitmasks.
 * [forms] are its looks; the first is the one it is received as.
 */
class Pet(val key: String, val bit: Int, val name: String, val forms: List<PetForm>) {
    val base: PetForm
        get() = forms.first()

    fun form(label: String): PetForm =
        forms.firstOrNull { it.label.equals(label, ignoreCase = true) }
            ?: error("Pet `$key` has no form `$label`: ${forms.map { it.label }}")

    fun formOf(objId: Int): PetForm? = forms.firstOrNull { it.obj.id == objId }

    fun formOfNpc(npcId: Int): PetForm? = forms.firstOrNull { it.npc.id == npcId }

    override fun toString(): String = "Pet($key)"
}

/** How [Pets.award] handed a pet over. */
enum class PetAward {
    /** It appeared as the player's follower. */
    Followed,

    /** The player already had a follower, so it went into the inventory. */
    Backpack,

    /** A follower out and no inventory space: it went to Probita, to be reclaimed. */
    Lost,

    /** The player already owns it, so nothing was given. */
    Duplicate,
}
