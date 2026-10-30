package org.rsmod.content.areas.misc.isleofsouls.revenant

import org.rsmod.api.config.refs.stats
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.stat.StatType

typealias ether_objs = EtherObjs

/** The revenant weapons, the bracelet of ethereum and what the weapons are made from. */
object EtherObjs : ObjReferences() {
    val ether = find("wild_cave_shard")

    val bracelet = find("wild_cave_bracelet_charged")
    val bracelet_uncharged = find("wild_cave_bracelet_uncharged")

    /** A secondary supply a revenant drops, which the amulet of avarice does not note. */
    val revenant_cave_teleport = find("teleportscroll_revenants")

    val viggora_uncharged = find("wild_cave_chainmace_uncharged")
    val viggora = find("wild_cave_chainmace_charged")
    val craw_uncharged = find("wild_cave_bow_uncharged")
    val craw = find("wild_cave_bow_charged")
    val thammaron_uncharged = find("wild_cave_sceptre_uncharged")
    val thammaron = find("wild_cave_sceptre_charged")

    /** The sceptre's autocast form, "(a)", and its uncharged one, "(au)". */
    val thammaron_a_uncharged = find("wild_cave_sceptre_uncharged_recol")
    val thammaron_a = find("wild_cave_sceptre_charged_recol")

    val ursine_uncharged = find("wild_cave_ursine_uncharged")
    val ursine = find("wild_cave_ursine_charged")
    val webweaver_uncharged = find("wild_cave_webweaver_uncharged")
    val webweaver = find("wild_cave_webweaver_charged")
    val accursed_uncharged = find("wild_cave_accursed_uncharged")
    val accursed = find("wild_cave_accursed_charged")
    val accursed_a_uncharged = find("wild_cave_accursed_uncharged_recol")
    val accursed_a = find("wild_cave_accursed_charged_recol")

    /** What the Wilderness bosses drop for the upgrades. */
    val claws_of_callisto = find("wbr_callisto_claws")
    val fangs_of_venenatis = find("wbr_venenatis_fang")
    val skull_of_vetion = find("wbr_vetion_skull")
}

/** What a revenant weapon does when it is wielded. */
enum class EtherStyle {
    Melee,
    Ranged,

    /** A powered staff: its built-in spell uses the ether. */
    Powered,

    /** An autocast staff: the spells it casts use the ether. */
    Autocast,
}

/**
 * One revenant weapon: its [uncharged] and [charged] forms. A charged one holds the activation
 * ether and what the passive can spend (see `RevenantEther`).
 *
 * @property maxHitOffset for a powered staff, what is taken off a third of the Magic level to get
 *   the built-in spell's max hit (Wiki: Thammaron's sceptre 8, Accursed sceptre 6).
 */
class EtherWeapon(
    val name: String,
    val style: EtherStyle,
    val uncharged: ObjType,
    val charged: ObjType,
    val maxHitOffset: Int = 0,
) {
    fun isForm(obj: InvObj?): Boolean = obj.isType(uncharged) || obj.isType(charged)
}

object EtherItems {
    val viggora =
        EtherWeapon(
            "Viggora's chainmace",
            EtherStyle.Melee,
            ether_objs.viggora_uncharged,
            ether_objs.viggora,
        )
    val ursine =
        EtherWeapon(
            "Ursine chainmace",
            EtherStyle.Melee,
            ether_objs.ursine_uncharged,
            ether_objs.ursine,
        )
    val craw =
        EtherWeapon("Craw's bow", EtherStyle.Ranged, ether_objs.craw_uncharged, ether_objs.craw)
    val webweaver =
        EtherWeapon(
            "Webweaver bow",
            EtherStyle.Ranged,
            ether_objs.webweaver_uncharged,
            ether_objs.webweaver,
        )
    val thammaron =
        EtherWeapon(
            "Thammaron's sceptre",
            EtherStyle.Powered,
            ether_objs.thammaron_uncharged,
            ether_objs.thammaron,
            maxHitOffset = 8,
        )
    val thammaronA =
        EtherWeapon(
            "Thammaron's sceptre (a)",
            EtherStyle.Autocast,
            ether_objs.thammaron_a_uncharged,
            ether_objs.thammaron_a,
        )
    val accursed =
        EtherWeapon(
            "Accursed sceptre",
            EtherStyle.Powered,
            ether_objs.accursed_uncharged,
            ether_objs.accursed,
            maxHitOffset = 6,
        )
    val accursedA =
        EtherWeapon(
            "Accursed sceptre (a)",
            EtherStyle.Autocast,
            ether_objs.accursed_a_uncharged,
            ether_objs.accursed_a,
        )

    val weapons =
        listOf(viggora, ursine, craw, webweaver, thammaron, thammaronA, accursed, accursedA)

    /** The weapon [obj] is a form of, if any. */
    fun weaponOf(obj: InvObj?): EtherWeapon? = weapons.firstOrNull { it.isForm(obj) }

    /** The weapon [obj] is the charged form of, if any. */
    fun chargedOf(obj: InvObj?): EtherWeapon? = weapons.firstOrNull { obj.isType(it.charged) }

    /** Ether in the weapons that were made by one weapon taking a part off another. */
    const val DISMANTLE_WEAPON_ETHER = 7_500
    const val DISMANTLE_BRACELET_ETHER = 250

    /** The Swap option of the sceptres: the same sceptre in its other form. */
    val swaps: Map<EtherWeapon, EtherWeapon> =
        mapOf(
            thammaron to thammaronA,
            thammaronA to thammaron,
            accursed to accursedA,
            accursedA to accursed,
        )

    /**
     * A weapon made from another by adding a part, in the uncharged form (Wiki: Ursine chainmace,
     * Webweaver bow, Accursed sceptre). Each takes 85 of a skill and gives no experience, and comes
     * apart again when dismantled (the Wiki says only that the skull crafting is reversible; the
     * claws and fangs are assumed to be too).
     *
     * @property plain the weapon before the part goes on, uncharged.
     */
    class Upgrade(
        val plain: ObjType,
        val part: ObjType,
        val made: ObjType,
        val skill: StatType,
        val skillName: String,
        val label: String,
    )

    /** Every upgrade. The sceptres' autocast forms are made from and come apart into their own. */
    val upgrades: List<Upgrade> by lazy {
        listOf(
            Upgrade(
                ether_objs.viggora_uncharged,
                ether_objs.claws_of_callisto,
                ether_objs.ursine_uncharged,
                stats.smithing,
                "Smithing",
                "Ursine chainmace",
            ),
            Upgrade(
                ether_objs.craw_uncharged,
                ether_objs.fangs_of_venenatis,
                ether_objs.webweaver_uncharged,
                stats.fletching,
                "Fletching",
                "Webweaver bow",
            ),
            Upgrade(
                ether_objs.thammaron_uncharged,
                ether_objs.skull_of_vetion,
                ether_objs.accursed_uncharged,
                stats.crafting,
                "Crafting",
                "Accursed sceptre",
            ),
            Upgrade(
                ether_objs.thammaron_a_uncharged,
                ether_objs.skull_of_vetion,
                ether_objs.accursed_a_uncharged,
                stats.crafting,
                "Crafting",
                "Accursed sceptre (a)",
            ),
        )
    }

    const val UPGRADE_LEVEL = 85
}
