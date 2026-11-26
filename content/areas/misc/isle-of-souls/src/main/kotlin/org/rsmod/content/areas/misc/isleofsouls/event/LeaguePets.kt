package org.rsmod.content.areas.misc.isleofsouls.event

import org.rsmod.content.areas.misc.isleofsouls.pets.PetCatalog

/**
 * Which items are pets, by the name the player sees. The cache has no flag for it, and its internal
 * names are not consistent (`snakepet`, `jad_pet`, `hell_pet`), so this is a list. The drop tables
 * name their pets either `Pet <boss>` or after the pet itself, and both are covered.
 */
object LeaguePets {
    private val names: Set<String> =
        setOf(
                "Abyssal orphan",
                "Baby mole",
                "Baron",
                "Butch",
                "Callisto cub",
                "Hellpuppy",
                "Ikkle Hydra",
                "Jal-nib-rek",
                "Kalphite Princess",
                "Lil'viathan",
                "Muphin",
                "Noon",
                "Prince Black Dragon",
                "Scorpia's offspring",
                "Smol heredit",
                "Smolcano",
                "Sraracha",
                "Tangleroot",
                "TzRek-Jad",
                "Venenatis spiderling",
                "Vet'ion Jr.",
                "Wisp",
            )
            .map { it.lowercase() }
            .toSet()

    fun isPet(displayName: String): Boolean {
        val name = displayName.lowercase()
        return name in names || name.startsWith("pet ")
    }

    private val petObjs: Set<Int> by lazy {
        PetCatalog.pets.flatMap { pet -> pet.forms.map { it.obj.id } }.toSet()
    }

    /** Whether [objId] is any look of a pet of the collection log. */
    fun isPetObj(objId: Int): Boolean = objId in petObjs
}
