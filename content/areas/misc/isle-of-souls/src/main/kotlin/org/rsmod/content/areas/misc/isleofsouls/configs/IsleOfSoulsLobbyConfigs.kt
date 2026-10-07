package org.rsmod.content.areas.misc.isleofsouls.configs

import org.rsmod.api.config.aliases.ParamStr
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.param.ParamReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences

typealias lobby_components = IsleOfSoulsLobbyComponents

typealias lobby_objs = IsleOfSoulsLobbyObjs

typealias lobby_params = IsleOfSoulsLobbyParams

typealias lobby_seqs = IsleOfSoulsLobbySeqs

typealias lobby_spotanims = IsleOfSoulsLobbySpotanims

/** The spellbook buttons for every Home Teleport the client can show. */
object IsleOfSoulsLobbyComponents : ComponentReferences() {
    val home_standard = find("magic_spellbook:teleport_home_standard")
    val home_edgeville = find("magic_spellbook:teleport_home_zaros")
    val home_lunar = find("magic_spellbook:teleport_home_lunar")
    val home_arceuus = find("magic_spellbook:teleport_home_arceuus")
    val home_kourend = find("magic_spellbook:league_twisted_home_teleport")
    val home_catherby = find("magic_spellbook:league_3_home_teleport")

    val home_teleports =
        listOf(home_standard, home_edgeville, home_lunar, home_arceuus, home_kourend, home_catherby)
}

object IsleOfSoulsLobbyObjs : ObjReferences() {
    val home_kourend = find("league_twisted_home_teleport")
    val home_catherby = find("league_3_home_teleport")
    val home_catherby_2 = find("league_3_home_teleport_2")
    val home_catherby_3 = find("league_3_home_teleport_3")
    val home_catherby_4 = find("league_3_home_teleport_4")
}

object IsleOfSoulsLobbyParams : ParamReferences() {
    /** The description the spellbook tooltip shows under a spell's name. */
    val spell_desc: ParamStr = find("spell_desc")
}

/** The normal teleport spell animations. */
object IsleOfSoulsLobbySeqs : SeqReferences() {
    val cast_teleport = find("human_castteleport")
    val cast_teleport_reverse = find("human_castteleport_reverse")
}

object IsleOfSoulsLobbySpotanims : SpotanimReferences() {
    val teleport_casting = find("teleport_casting")
}

/**
 * Renames every Home Teleport spell and rewrites its tooltip. The spellbook reads the name and
 * description from the spell object's params, so this needs `./gradlew packCache` to reach the
 * client.
 */
internal object IsleOfSoulsHomeTeleportEditor : ObjEditor() {
    const val NAME = "Home Teleport"
    const val DESCRIPTION = "Teleports you to the Soul Wars lobby. Requires no runes."

    init {
        val spells =
            listOf(
                objs.spell_hometeleport_lumbridge,
                objs.spell_hometeleport_edgeville,
                objs.spell_hometeleport_lunar,
                objs.spell_hometeleport_arceuus,
                lobby_objs.home_kourend,
                lobby_objs.home_catherby,
                lobby_objs.home_catherby_2,
                lobby_objs.home_catherby_3,
                lobby_objs.home_catherby_4,
            )
        for (spell in spells) {
            edit(spell) {
                param[params.spell_name] = NAME
                param[lobby_params.spell_desc] = DESCRIPTION
            }
        }
    }
}
