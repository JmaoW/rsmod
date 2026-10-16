package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList

/**
 * Server-only switches, one per god, that are `1` while the player wears at least one item devoted
 * to that god ([GodItems]) and `0` otherwise. Not saved: [GodItems.sync] sets them at login and
 * whenever the worn items change. The followers' hunt modes read them to decide who to leave alone.
 */
object GodAlignmentVars : VarpReferences() {
    val armadyl = find("isle_godwars_aligned_armadyl")
    val bandos = find("isle_godwars_aligned_bandos")
    val saradomin = find("isle_godwars_aligned_saradomin")
    val zamorak = find("isle_godwars_aligned_zamorak")
}

internal object GodAlignmentVarpBuilder : VarpBuilder() {
    init {
        build("isle_godwars_aligned_armadyl") { temporary = true }
        build("isle_godwars_aligned_bandos") { temporary = true }
        build("isle_godwars_aligned_saradomin") { temporary = true }
        build("isle_godwars_aligned_zamorak") { temporary = true }
    }
}

/**
 * Which items are devoted to which god. In the God Wars Dungeon a follower leaves a player alone
 * while they wear any one item of its god (the Wiki's "God equipment" table), and other gods' items
 * do not help. The lists are that table's items by cache symbol name (the Wiki's item ids, checked
 * against the cache); Zaros is left out because the Ancient Prison is not built. Items the cache
 * does not have (the Saradomin and Bandos cloaks, croziers, stoles, and so on) are not listed.
 * `Staff of Light` is on both the Saradomin and Zamorak lists, as in the Wiki.
 */
class GodItems @Inject constructor(objTypes: ObjTypeList) {
    private val byGod: Map<God, Set<Int>> =
        God.entries.associateWith { god ->
            val names = GodItemNames.of(god).toSet()
            objTypes.values.filter { it.internalName in names }.mapTo(hashSetOf()) { it.id }
        }

    /** The gods whose items [player] is wearing. */
    fun alignedWith(player: Player): Set<God> {
        val aligned = mutableSetOf<God>()
        for (obj in player.worn.objs) {
            if (obj == null) {
                continue
            }
            for ((god, ids) in byGod) {
                if (obj.id in ids) {
                    aligned += god
                }
            }
        }
        return aligned
    }

    /** How many of the items [player] wears are devoted to [god]. */
    fun wornCount(player: Player, god: God): Int {
        val ids = byGod.getValue(god)
        return player.worn.objs.count { it != null && it.id in ids }
    }

    /** Whether [id] is devoted to [god]. */
    fun isDevoted(god: God, id: Int): Boolean = id in byGod.getValue(god)

    /** Brings the four [GodAlignmentVars] in line with what [player] wears. */
    fun sync(player: Player) {
        val aligned = alignedWith(player)
        for (god in God.entries) {
            val value = if (god in aligned) 1 else 0
            if (player.vars[god.aligned] != value) {
                VarPlayerIntMapSetter.set(player, god.aligned, value)
            }
        }
    }
}

/** The names behind [GodItems]; a name that is not in the cache would simply never match. */
object GodItemNames {
    fun of(god: God): List<String> =
        when (god) {
            God.ARMADYL -> armadyl
            God.BANDOS -> bandos
            God.SARADOMIN -> saradomin
            God.ZAMORAK -> zamorak
        }

    val armadyl =
        listOf(
            "acb",
            "ags",
            "agsg",
            "armadyl_chestplate",
            "armadyl_halo",
            "armadyl_halo_broken",
            "armadyl_halo_trouver",
            "armadyl_helmet",
            "armadyl_skirt",
            "armadylbook_complete",
            "blessed_boots_armadyl",
            "blessed_dhide_shield_armadyl",
            "blessing_armadyl",
            "deadman_ags",
            "ikov_pendantofarmardyl",
            "rune_full_helm_armadyl",
            "rune_kiteshield_armadyl",
            "rune_platebody_armadyl",
            "rune_platelegs_armadyl",
            "rune_plateskirt_armadyl",
            "trail_armadyl_chaps",
            "trail_armadyl_chest",
            "trail_armadyl_cloak",
            "trail_armadyl_coif",
            "trail_armadyl_mitre",
            "trail_armadyl_robe_l",
            "trail_armadyl_robe_t",
            "trail_armadyl_scarf",
            "trail_armadyl_staff",
            "trail_armadyl_vambraces",
            "wild_cave_bow_charged",
            "wild_cave_bow_uncharged",
            "wild_cave_webweaver_charged",
            "wild_cave_webweaver_uncharged",
        )

    val bandos =
        listOf(
            "ancient_goblin_mace",
            "bandos_boots",
            "bandos_chestplate",
            "bandos_halo",
            "bandos_halo_broken",
            "bandos_halo_trouver",
            "bandos_skirt",
            "bandosbook_complete",
            "bgs",
            "bgsg",
            "blessed_boots_bandos",
            "blessed_dhide_shield_bandos",
            "blessing_bandos",
            "echo_boots",
            "guardian_boots",
            "rune_full_helm_bandos",
            "rune_kiteshield_bandos",
            "rune_platebody_bandos",
            "rune_platelegs_bandos",
            "rune_plateskirt_bandos",
            "trail_bandos_chaps",
            "trail_bandos_chest",
            "trail_bandos_cloak",
            "trail_bandos_coif",
            "trail_bandos_mitre",
            "trail_bandos_robe_l",
            "trail_bandos_robe_t",
            "trail_bandos_scarf",
            "trail_bandos_staff",
            "trail_bandos_vambraces",
        )

    val saradomin =
        listOf(
            "blessed_boots_saradomin",
            "blessed_dhide_shield_saradomin",
            "blessed_saradomin_sword",
            "blessed_saradomin_sword_degraded",
            "blessedstar",
            "blessing_saradomin",
            "castlewars_saradomin_halo",
            "castlewars_saradomin_halo_broken",
            "castlewars_saradomin_halo_trouver",
            "deadman_ma2_saradomin_cape",
            "devout_boots",
            "ghrazi_rapier_or",
            "hallowed_focus",
            "hallowed_grapple",
            "hallowed_hammer",
            "hallowed_ring",
            "hallowed_symbol",
            "holy_sandals",
            "holy_wraps",
            "justiciar_chestguard",
            "justiciar_faceguard",
            "justiciar_leg_guards",
            "ma2_saradomin_cape",
            "ma2_saradomin_cape_broken",
            "ma2_saradomin_cape_trouver",
            "monkrobebottom",
            "monkrobetop",
            "poh_helmet_saradomin_rune",
            "poh_shield_saradomin_rune",
            "ring_of_endurance",
            "ring_of_endurance_nocharges",
            "rune_full_helm_saradomin",
            "rune_kiteshield_saradomin",
            "rune_platebody_saradomin",
            "rune_platelegs_saradomin",
            "rune_plateskirt_saradomin",
            "rune_scimitar_saradomin",
            "sanguinesti_staff_or",
            "sanguinesti_staff_uncharged_or",
            "saradomin_cape",
            "saradomin_staff",
            "saradomin_sword",
            "saradominbook_complete",
            "scythe_of_vitur_or",
            "scythe_of_vitur_uncharged_or",
            "sgs",
            "sgsg",
            "skillcape_max_hood_saradomin",
            "skillcape_max_hood_saradomin2",
            "skillcape_max_saradomin",
            "skillcape_max_saradomin2",
            "skillcape_max_saradomin2_broken",
            "skillcape_max_saradomin2_trouver",
            "staff_of_light",
            "trail_saradomin_chaps",
            "trail_saradomin_chest",
            "trail_saradomin_cloak",
            "trail_saradomin_coif",
            "trail_saradomin_mitre",
            "trail_saradomin_robe_l",
            "trail_saradomin_robe_t",
            "trail_saradomin_scarf",
            "trail_saradomin_staff",
            "trail_saradomin_vambraces",
        )

    val zamorak =
        listOf(
            "blessed_boots_zamorak",
            "blessed_dhide_shield_zamorak",
            "blessedsnake",
            "blessing_zamorak",
            "castlewars_zamorak_halo",
            "castlewars_zamorak_halo_broken",
            "castlewars_zamorak_halo_trouver",
            "dagonhai_hat",
            "dagonhai_robe_bottom",
            "dagonhai_robe_top",
            "deadman_ma2_zamorak_cape",
            "dragonhunter_lance",
            "elderchaos_bottom",
            "elderchaos_hood",
            "elderchaos_top",
            "elite_black_full_helm",
            "elite_black_platebody",
            "elite_black_platelegs",
            "inquisitors_body",
            "inquisitors_helm",
            "inquisitors_mace",
            "inquisitors_skirt",
            "ma2_zamorak_cape",
            "ma2_zamorak_cape_broken",
            "ma2_zamorak_cape_trouver",
            "oathplate_chest",
            "oathplate_helm",
            "oathplate_legs",
            "poh_helmet_zamorak_rune",
            "poh_shield_zamorak_rune",
            "rune_full_helm_zamorak",
            "rune_kiteshield_zamorak",
            "rune_platebody_zamorak",
            "rune_platelegs_zamorak",
            "rune_plateskirt_zamorak",
            "rune_scimitar_zamorak",
            "skillcape_max_hood_zamorak",
            "skillcape_max_hood_zamorak2",
            "skillcape_max_zamorak",
            "skillcape_max_zamorak2",
            "skillcape_max_zamorak2_broken",
            "skillcape_max_zamorak2_trouver",
            "sotd",
            "staff_of_balance",
            "staff_of_light",
            "toxic_sotd",
            "toxic_sotd_charged",
            "trail_zamorak_chaps",
            "trail_zamorak_chest",
            "trail_zamorak_cloak",
            "trail_zamorak_coif",
            "trail_zamorak_mitre",
            "trail_zamorak_robe_l",
            "trail_zamorak_robe_t",
            "trail_zamorak_scarf",
            "trail_zamorak_staff",
            "trail_zamorak_vambraces",
            "wgs_dark_squall_hood",
            "wgs_dark_squall_robe_bottom",
            "wgs_dark_squall_robe_top",
            "wild_cave_accursed_charged",
            "wild_cave_accursed_charged_recol",
            "wild_cave_accursed_uncharged",
            "wild_cave_accursed_uncharged_recol",
            "wild_cave_chainmace_charged",
            "wild_cave_chainmace_uncharged",
            "wild_cave_sceptre_charged",
            "wild_cave_sceptre_charged_recol",
            "wild_cave_sceptre_uncharged",
            "wild_cave_sceptre_uncharged_recol",
            "wild_cave_ursine_charged",
            "wild_cave_ursine_uncharged",
            "zamorak_cape",
            "zamorak_hasta",
            "zamorak_spear",
            "zamorak_staff",
            "zamorakbook_complete",
            "zamrobebottom",
            "zamrobetop",
            "zgs",
            "zgsg",
        )
}
