package org.rsmod.content.areas.misc.isleofsouls.magic

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.content.skills.magic.utility.scripts.TeleportDestinations
import org.rsmod.content.skills.runecraft.configs.RuneAltar
import org.rsmod.content.skills.runecraft.configs.RuneAltars
import org.rsmod.content.skills.runecraft.scripts.RunecraftSites
import org.rsmod.game.type.obj.ObjType
import org.rsmod.map.CoordGrid

/**
 * Where each teleport spell takes a player on the Isle of Souls. Every teleport keeps its real
 * spell, level, runes and experience; only the destination is the isle's.
 *
 * **These are placeholders.** The isle has few places to go yet, so teleports share them. To change
 * one, change the [Place] next to its spell in [places]; nothing else needs to be touched. A
 * teleport spell with no entry here cannot be cast on the isle at all, because the cache's
 * destinations are in the real world.
 *
 * Group teleports and Teleother use the destination of the spell they are a version of.
 */
class IsleTeleportDestinations @Inject constructor(private val sites: RunecraftSites) {
    /** A place a player can be sent. */
    private fun interface Place {
        fun resolve(): CoordGrid
    }

    private fun fixed(x: Int, z: Int, level: Int = 0) = Place { CoordGrid(x, z, level) }

    /** Inside a runic altar, on the free tile nearest its exit portal. */
    private fun altar(altar: RuneAltar) = Place { sites.landing(altar.inside) }

    private val blueGraveyard = fixed(2206, 2850)
    private val redGraveyard = fixed(2214, 2850)
    private val fightCaves = fixed(2208, 2858)
    private val miningGuild = fixed(3019, 9739)
    private val mageOfZamorak = Place { sites.home }
    private val aubury = Place { sites.aubury }
    private val essenceMine = Place { sites.mineArrival }
    private val abyss = Place { sites.abyssArrival }

    private val places: Map<ObjType, Place> =
        mapOf(
            // Standard spellbook
            objs.spell_varrock_teleport to altar(RuneAltars.air),
            objs.spell_lumbridge_teleport to blueGraveyard,
            objs.spell_falador_teleport to altar(RuneAltars.earth),
            objs.spell_camelot_teleport to fightCaves,
            objs.spell_ardougne_teleport to redGraveyard,
            objs.spell_watchtower_teleport to altar(RuneAltars.mind),
            objs.spell_trollheim_teleport to altar(RuneAltars.fire),
            objs.spell_apeatoll_teleport to altar(RuneAltars.water),
            objs.spell_kourendcastle_teleport to mageOfZamorak,
            objs.spell_civitas_fortis_teleport to aubury,
            objs.spell_teleother_lumbridge to blueGraveyard,
            objs.spell_teleother_falador to altar(RuneAltars.earth),
            objs.spell_teleother_camelot to fightCaves,
            // Ancient Magicks
            objs.spell_paddewwa_teleport to altar(RuneAltars.chaos),
            objs.spell_senntisten_teleport to altar(RuneAltars.death),
            objs.spell_kharyllyl_teleport to altar(RuneAltars.blood),
            objs.spell_lassar_teleport to altar(RuneAltars.cosmic),
            objs.spell_dareeyak_teleport to altar(RuneAltars.nature),
            objs.spell_carrallagar_teleport to altar(RuneAltars.law),
            objs.spell_annakarl_teleport to altar(RuneAltars.wrath),
            objs.spell_ghorrock_teleport to abyss,
            // Lunar spellbook, and the group versions of its teleports
            objs.spell_moonclan_teleport to mageOfZamorak,
            objs.spell_moonclan_telegroup to mageOfZamorak,
            objs.spell_zmi_teleport to essenceMine,
            objs.spell_waterbirth_teleport to miningGuild,
            objs.spell_waterbirth_telegroup to miningGuild,
            objs.spell_barboutpost_teleport to altar(RuneAltars.body),
            objs.spell_barboutpost_telegroup to altar(RuneAltars.body),
            objs.spell_portkhazard_teleport to fightCaves,
            objs.spell_portkhazard_telegroup to fightCaves,
            objs.spell_fishguild_teleport to altar(RuneAltars.astral),
            objs.spell_fishguard_telegroup to altar(RuneAltars.astral),
            objs.spell_catherby_teleport to aubury,
            objs.spell_catherby_telegroup to aubury,
            objs.spell_lunarghorrock_teleport to abyss,
            objs.spell_lunarghorrock_telegroup to abyss,
            // Arceuus spellbook
            objs.spell_arceuuslibrary_teleport to aubury,
            objs.spell_draynormanor_teleport to mageOfZamorak,
            objs.spell_battlefront_teleport to fightCaves,
            objs.spell_mindaltar_teleport to altar(RuneAltars.mind),
            objs.spell_respawn_teleport to blueGraveyard,
            objs.spell_salvegrave_teleport to redGraveyard,
            objs.spell_fenkenstrainscastle_teleport to miningGuild,
            objs.spell_westardougne_teleport to altar(RuneAltars.cosmic),
            objs.spell_harmonyisland_teleport to altar(RuneAltars.water),
            objs.spell_cemetery_teleport to altar(RuneAltars.death),
            objs.spell_barrows_teleport to altar(RuneAltars.law),
            objs.spell_arceuusapeatoll_teleport to altar(RuneAltars.air),
        )

    /** Hands every destination to the teleport spells and stops them using the real world's. */
    fun apply(destinations: TeleportDestinations) {
        destinations.overridesOnly = true
        for ((spell, place) in places) {
            destinations.override(spell) { place.resolve() }
        }
    }

    /** The spells that have a destination here. */
    val spells: Set<ObjType>
        get() = places.keys
}
