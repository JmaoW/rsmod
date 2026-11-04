package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.ranged.RangedAmmunition
import org.rsmod.api.combat.manager.AmmoPouch
import org.rsmod.api.config.refs.categories
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.Wearpos

/**
 * Dizana's quiver's extra ammunition slot (wiki): while a quiver is worn or carried, a second stack
 * of arrows or bolts sits above the ammo slot. The client draws it from two varps (the obj, `-1`
 * for none, and the count; client scripts 5023-5026 and the quiver's own screen, 592, read them),
 * so that is where it is kept.
 *
 * The real game fires from whichever slot holds what the weapon needs, the ammo slot first. Here,
 * when the ammo slot does not suit the weapon and the pouch does, the two swap before the shot (the
 * "Swap" option done for the player; see [AmmoPouch]). The ammo slot then shows what is being
 * fired, and every bonus, effect and ammo use works unchanged.
 */
class DizanasQuiverPouch @Inject constructor(private val objTypes: ObjTypeList) : AmmoPouch {
    fun obj(player: Player): Int? = player.vars[QuiverPouchVars.obj].takeIf { it > 0 }

    fun count(player: Player): Int =
        if (obj(player) == null) 0 else player.vars[QuiverPouchVars.count]

    fun isEmpty(player: Player): Boolean = count(player) <= 0

    /** Puts [count] of [obj] in the pouch (or empties it with `null`). */
    fun set(player: Player, obj: Int?, count: Int) {
        if (obj == null || count <= 0) {
            VarPlayerIntMapSetter.set(player, QuiverPouchVars.obj, -1)
            VarPlayerIntMapSetter.set(player, QuiverPouchVars.count, 0)
            return
        }
        VarPlayerIntMapSetter.set(player, QuiverPouchVars.obj, obj)
        VarPlayerIntMapSetter.set(player, QuiverPouchVars.count, count)
    }

    /** An empty pouch is `-1`, not the varp's default 0 (which the client draws as an item). */
    fun syncOnLogin(player: Player) {
        if (isEmpty(player)) {
            set(player, null, 0)
        }
    }

    /** Whether [player] wears a quiver or carries one: the pouch only works then. */
    fun hasQuiver(player: Player): Boolean {
        val back = player.worn[Wearpos.Back.slot]
        if (back != null && isQuiver(back.id)) {
            return true
        }
        return player.inv.any { it != null && isQuiver(it.id) }
    }

    fun isQuiver(obj: Int): Boolean = QuiverPouchObjs.quivers.any { it.id == obj }

    /** Only arrows and bolts go in the pouch (wiki). */
    fun fitsPouch(obj: InvObj): Boolean {
        val type = objTypes[obj]
        return type.isCategoryType(categories.arrows) ||
            type.isCategoryType(categories.dragon_arrow) ||
            type.isCategoryType(categories.crossbow_bolt)
    }

    /** The "Swap" option: the ammo slot and the pouch change places. */
    fun swap(player: Player) {
        val worn = player.worn[Wearpos.Quiver.slot]
        val pouchObj = obj(player)
        val pouchCount = count(player)
        player.worn[Wearpos.Quiver.slot] =
            pouchObj?.let { objTypes[it] }?.let { InvObj(it, pouchCount) }
        set(player, worn?.id, worn?.count ?: 0)
    }

    override fun ready(player: Player, weapon: UnpackedObjType) {
        val pouchObj = obj(player) ?: return
        if (!hasQuiver(player)) {
            return
        }
        val worn = objTypes.getOrNull(player.worn[Wearpos.Quiver.slot])
        if (worn != null && fires(weapon, worn)) {
            return
        }
        val pouchType = objTypes[pouchObj] ?: return
        if (fires(weapon, pouchType)) {
            swap(player)
        }
    }

    private fun fires(weapon: UnpackedObjType, ammo: UnpackedObjType): Boolean {
        val valid = RangedAmmunition.Validation.Valid
        return when {
            weapon.isCategoryType(categories.crossbow) ->
                RangedAmmunition.validateBolts(weapon, ammo) == valid
            weapon.isCategoryType(categories.bow) ->
                RangedAmmunition.validateArrows(weapon, ammo) == valid
            else -> false
        }
    }
}

object QuiverPouchVars : VarpReferences() {
    val obj = find("dizanas_quiver_temp_ammo")
    val count = find("dizanas_quiver_temp_ammo_amount")
}

/** Every quiver with the extra slot: Dizana's quivers, locked or not, and the max cape. */
object QuiverPouchObjs : ObjReferences() {
    val uncharged = find("dizanas_quiver_uncharged")
    val uncharged_trouver = find("dizanas_quiver_uncharged_trouver")
    val charged = find("dizanas_quiver_charged")
    val charged_trouver = find("dizanas_quiver_charged_trouver")
    val blessed = find("dizanas_quiver_infinite")
    val blessed_trouver = find("dizanas_quiver_infinite_trouver")
    val max_cape = find("skillcape_max_dizanas")
    val max_cape_trouver = find("skillcape_max_dizanas_trouver")

    val quivers
        get() =
            listOf(
                uncharged,
                uncharged_trouver,
                charged,
                charged_trouver,
                blessed,
                blessed_trouver,
                max_cape,
                max_cape_trouver,
            )
}
