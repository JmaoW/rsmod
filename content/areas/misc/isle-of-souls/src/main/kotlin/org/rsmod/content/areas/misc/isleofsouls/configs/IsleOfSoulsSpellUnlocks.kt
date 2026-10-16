package org.rsmod.content.areas.misc.isleofsouls.configs

import org.rsmod.api.config.aliases.ParamBool
import org.rsmod.api.config.aliases.ParamInt
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.param.ParamReferences
import org.rsmod.game.type.dbrow.DbRowType
import org.rsmod.game.type.dbrow.HashedDbRowType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.param.ParamType

/** The spell object params the client's spellbook reads to decide whether a spell is unlocked. */
object IsleOfSoulsSpellParams : ParamReferences() {
    /** The quest (a row of the quest table) that must be complete. `-1` means none. */
    val quest_requirement: ParamType<DbRowType> = find("spell_questreq_struct")

    /** The achievement diary reward that must be unlocked. `0` means none. */
    val diary_requirement: ParamInt = find("spell_achievement_diary_requirement")

    /** `1` for the spells that wait for a Magic Training Arena reward or a bounty target. */
    val extra_unlock: ParamBool = find("spell_extra_unlock")
}

/** The spell objects that need a quest, a diary or an unlock to show as unlocked in the book. */
object IsleOfSoulsSpellObjs : ObjReferences() {
    fun spell(internalName: String): ObjType = find(internalName)

    /**
     * Every spell the cache gives a requirement: Ancient Magicks, Lunar, Arceuus, the god spells,
     * Charge, Iban Blast and Bones to Peaches. Found with the cache's spell tables.
     */
    val locked: List<String> =
        listOf(
            "leather",
            "caveorb4dot",
            "50_iban_blast",
            "51_ardougne_teleport",
            "58_watchtower_teleport",
            "60_claws_of_guthix",
            "60_flames_of_zamorak",
            "60_saradomin_strike",
            "61_trollheim_teleport",
            "80_charge",
            "50_smoke_rush",
            "52_shadow_rush",
            "54_paddewwa_teleport",
            "56_blood_rush",
            "58_ice_rush",
            "60_senntisten_teleport",
            "62_smoke_burst",
            "64_shadow_burst",
            "66_kharyllyl_teleport",
            "68_blood_burst",
            "70_ice_burst",
            "72_lassar_teleport",
            "74_smoke_blitz",
            "76_shadow_blitz",
            "78_dareeyak_teleport",
            "80_blood_blitz",
            "82_ice_blitz",
            "84_carrallagar_teleport",
            "86_smoke_barrage",
            "88_shadow_barrage",
            "90_annakarl_teleport",
            "92_blood_barrage",
            "94_ice_barrage",
            "96_ghorrock_teleport",
            "watering_can_dummy",
            "60_bones_peaches",
            "poh_temp_limestonebrick",
            "65_bake_pie",
            "66_cure_plant",
            "67_npc_contact",
            "68_cure_other",
            "69_tele_moonclan",
            "70_tele_moonclan_group",
            "71_cure_me",
            "72_tele_waterbirth",
            "73_tele_waterbirth_group",
            "74_cure_group",
            "75_tele_barb_outpost",
            "76_tele_barb_outpost_group",
            "77_superglass",
            "78_tele_port_khazard",
            "79_tele_port_khazard_group",
            "80_string_jewelry",
            "81_res_pot_share",
            "82_magic_imbue",
            "83_treat_soil",
            "84_statboost_pot_share",
            "85_tele_fish_guild",
            "86_tele_fish_guild_group",
            "87_tele_catherby",
            "88_tele_catherby_group",
            "89_tele_ghorrock",
            "90_tele_ghorrock_group",
            "91_energy_transfer",
            "92_heal_other",
            "93_vengeance_other",
            "94_vengeance",
            "95_heal_group",
            "zanarischoir_dummy",
            "66_monster_examine",
            "68_humidify",
            "71_hunter_kit",
            "75_stat_spy",
            "79_dream",
            "86_plank_make",
            "96_spellbook_swap",
            "placeholder_snakepet_orange",
            "placeholder_snakepet_blue",
            "placeholder_vetion_pet2",
            "placeholder_skillpethunter_grey",
            "placeholder_skillpethunter_black",
            "placeholder_skillpethunter_gold",
            "br_bass",
            "br_willow_bow",
            "br_adamant_scimitar",
            "br_moonclan_legs",
            "br_xeric_body",
            "76_spin_flax",
            "71_tele_zmialtar",
            "skillcape_ardy_hood_firecape",
            "placeholder_skillcape_ardy_hood_firecape",
            "dmm_ref_torso",
            "dmm_ref_legs",
            "cert_deadman_level99_lamp",
            "placeholder_blighted_sack_snare",
            "osb8_bamboo",
            "osb8_monkey_trap",
            "osb8_monkey_donie",
            "osb8_monkey_bob",
            "osb8_monkey_aereck",
            "osb8_monkey_cow",
            "osb8_monkey_unicorn",
            "osb8_monkey_sheep",
            "osb8_unicorn_horn",
        )
}

/**
 * The isle has no quests or achievement diaries, and the client's spellbook
 * (`magic_spellbook_unlocked`) shows a padlock on any spell whose quest or diary is not done unless
 * the Grimoire relic is active. This clears those requirements on every spell, so a spell is
 * unlocked by its Magic level and its runes alone.
 *
 * The client reads these from the cache, so this needs `./gradlew packCache` to reach it.
 */
internal object IsleOfSoulsSpellUnlocks : ObjEditor() {
    /** The row id the client's spellbook script compares against to see that there is no quest. */
    private val noQuest: DbRowType =
        HashedDbRowType(startHash = null, internalName = null, internalId = -1)
    private const val NO_DIARY = 0

    init {
        for (name in IsleOfSoulsSpellObjs.locked) {
            edit(IsleOfSoulsSpellObjs.spell(name)) {
                param[IsleOfSoulsSpellParams.quest_requirement] = noQuest
                param[IsleOfSoulsSpellParams.diary_requirement] = NO_DIARY
                param[IsleOfSoulsSpellParams.extra_unlock] = false
            }
        }
    }
}
