package org.rsmod.content.interfaces.collectionlog

import org.rsmod.game.type.varp.VarpType

/**
 * Which monsters count towards which kill-count line of the collection log. Matching is by the
 * monster's name (any letter case), as the kill tasks do, so every form of a boss counts.
 *
 * Counters that are not a kill of one monster (Barrows chests, clue completions, Fight Caves and
 * the like) are not here: the code that gives the reward calls [CollectionKillCounts.add] itself.
 */
internal object CollectionKillSources {
    private val kills = CollectionKillCountVarps

    private val byName: Map<String, List<VarpType>> =
        HashMap<String, MutableList<VarpType>>().apply {
            fun source(counter: VarpType, vararg names: String) {
                for (name in names) {
                    getOrPut(name.lowercase()) { ArrayList() }.add(counter)
                }
            }

            source(kills.abyssal_sire_kills, "Abyssal Sire")
            source(kills.alchemical_hydra_kills, "Alchemical Hydra")
            source(kills.amoxliatl_kills, "Amoxliatl")
            source(kills.araxxor_kills, "Araxxor")
            source(kills.artio_kills, "Artio")
            source(kills.bryophyta_kills, "Bryophyta")
            source(kills.callisto_kills, "Callisto")
            source(kills.calvarion_kills, "Calvar'ion")
            source(kills.cerberus_kills, "Cerberus")
            source(kills.chaos_elemental_kills, "Chaos Elemental")
            source(kills.chaos_fanatic_kills, "Chaos Fanatic")
            source(kills.commander_zilyana_kills, "Commander Zilyana")
            source(kills.corporeal_beast_kills, "Corporeal Beast")
            source(kills.crazy_archaeologist_kills, "Crazy archaeologist")
            source(kills.dagannoth_prime_kills, "Dagannoth Prime")
            source(kills.dagannoth_rex_kills, "Dagannoth Rex")
            source(kills.dagannoth_supreme_kills, "Dagannoth Supreme")
            source(kills.demonic_gorilla_kills, "Demonic gorilla")
            source(kills.deranged_archaeologist_kills, "Deranged archaeologist")
            source(kills.duke_sucellus_kills, "Duke Sucellus")
            source(kills.general_graardor_kills, "General Graardor")
            source(kills.giant_mole_kills, "Giant Mole")
            source(kills.grotesque_guardian_kills, "Dusk")
            source(kills.hespori_kills, "Hespori")
            source(kills.hueycoatl_kills, "The Hueycoatl")
            source(kills.kalphite_queen_kills, "Kalphite Queen")
            source(kills.king_black_dragon_kills, "King Black Dragon")
            source(kills.kraken_kills, "Kraken")
            source(kills.kreearra_kills, "Kree'arra")
            source(kills.kril_tsutsaroth_kills, "K'ril Tsutsaroth")
            source(kills.leviathan_kills, "The Leviathan")
            source(kills.nex_kills, "Nex")
            source(kills.nightmare_kills, "The Nightmare")
            source(kills.obor_kills, "Obor")
            source(kills.phantom_muspah_kills, "Phantom Muspah")
            source(kills.phosanis_nightmare_kills, "Phosani's Nightmare")
            source(kills.sarachnis_kills, "Sarachnis")
            source(kills.scorpia_kills, "Scorpia")
            source(kills.scurrius_kills, "Scurrius")
            source(kills.skotizo_kills, "Skotizo")
            source(kills.sol_heredit_kills, "Sol Heredit")
            source(kills.spindel_kills, "Spindel")
            source(kills.tempoross_kills, "Tempoross")
            source(kills.thermonuclear_smoke_devil_kills, "Thermonuclear smoke devil")
            source(kills.tormented_demon_kills, "Tormented Demon")
            source(kills.tortured_gorilla_kills, "Tortured gorilla")
            source(kills.tzkal_zuk_kills, "TzKal-Zuk")
            source(kills.tztok_jad_kills, "TzTok-Jad")
            source(kills.vardorvis_kills, "Vardorvis")
            source(kills.venenatis_kills, "Venenatis")
            source(kills.vetion_kills, "Vet'ion")
            source(kills.vorkath_kills, "Vorkath")
            source(kills.whisperer_kills, "The Whisperer")
            source(kills.wintertodt_kills, "Wintertodt")
            source(kills.yama_kills, "Yama")
            source(kills.zalcano_kills, "Zalcano")
            source(kills.zulrah_kills, "Zulrah")
        }

    /** Every revenant, whatever it looks like ("Revenant imp", "Revenant dragon"). */
    private const val REVENANT_PREFIX = "revenant "

    fun countersFor(npcName: String): List<VarpType> {
        val name = npcName.trim().lowercase()
        val exact = byName[name]
        if (exact != null) {
            return exact
        }
        if (name.startsWith(REVENANT_PREFIX)) {
            return listOf(kills.revenant_kills)
        }
        return emptyList()
    }

    /** The monster names that have a counter, for tests. */
    val names: Set<String>
        get() = byName.keys
}
