package org.rsmod.content.other.special.attacks.support

import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos

internal object MoonObjs : ObjReferences() {
    val blue_helm = find("frost_moon_helm")
    val blue_helm_degraded = find("frost_moon_helm_degraded")
    val blue_body = find("frost_moon_chestplate")
    val blue_body_degraded = find("frost_moon_chestplate_degraded")
    val blue_legs = find("frost_moon_tassets")
    val blue_legs_degraded = find("frost_moon_tassets_degraded")

    val blood_helm = find("blood_moon_helm")
    val blood_helm_degraded = find("blood_moon_helm_degraded")
    val blood_body = find("blood_moon_chestplate")
    val blood_body_degraded = find("blood_moon_chestplate_degraded")
    val blood_legs = find("blood_moon_tassets")
    val blood_legs_degraded = find("blood_moon_tassets_degraded")

    val eclipse_helm = find("eclipse_moon_helm")
    val eclipse_helm_degraded = find("eclipse_moon_helm_degraded")
    val eclipse_body = find("eclipse_moon_chestplate")
    val eclipse_body_degraded = find("eclipse_moon_chestplate_degraded")
    val eclipse_legs = find("eclipse_moon_tassets")
    val eclipse_legs_degraded = find("eclipse_moon_tassets_degraded")
}

/**
 * The three Moons of Peril armour sets whose weapons only use their special attack when the whole
 * set is worn: the blue moon (frostmoon spear), blood moon (dual macuahuitl, "Bloodrager") and
 * eclipse moon (eclipse atlatl).
 */
enum class MoonSet(
    private val helm: List<ObjType>,
    private val body: List<ObjType>,
    private val legs: List<ObjType>,
    val title: String,
) {
    Blue(
        listOf(MoonObjs.blue_helm, MoonObjs.blue_helm_degraded),
        listOf(MoonObjs.blue_body, MoonObjs.blue_body_degraded),
        listOf(MoonObjs.blue_legs, MoonObjs.blue_legs_degraded),
        "Frostmoon",
    ),
    Blood(
        listOf(MoonObjs.blood_helm, MoonObjs.blood_helm_degraded),
        listOf(MoonObjs.blood_body, MoonObjs.blood_body_degraded),
        listOf(MoonObjs.blood_legs, MoonObjs.blood_legs_degraded),
        "Bloodrager",
    ),
    Eclipse(
        listOf(MoonObjs.eclipse_helm, MoonObjs.eclipse_helm_degraded),
        listOf(MoonObjs.eclipse_body, MoonObjs.eclipse_body_degraded),
        listOf(MoonObjs.eclipse_legs, MoonObjs.eclipse_legs_degraded),
        "Eclipse",
    );

    /** Whether [player] wears a helm, body and legs of the set. */
    fun isWornBy(player: Player): Boolean =
        player.worn[Wearpos.Hat.slot].isOneOf(helm) &&
            player.worn[Wearpos.Torso.slot].isOneOf(body) &&
            player.worn[Wearpos.Legs.slot].isOneOf(legs)

    private fun InvObj?.isOneOf(types: List<ObjType>): Boolean =
        this != null && types.any { it.id == id }
}
