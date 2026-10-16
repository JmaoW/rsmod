package org.rsmod.content.skills.runecraft.scripts

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.content.skills.runecraft.configs.RunecraftLocs
import org.rsmod.content.skills.runecraft.configs.RunecraftNpcs
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Puts the runecraft people and doors in the world when the server starts: the Mage of Zamorak and
 * Aubury on the isle, the Dark Mage in the middle of the Abyss, the portal that leads out of the
 * essence mine, the one beside the Astral Altar, and the Abyss rifts for blood, astral and wrath.
 * Set [ENABLED] to `false` to stop all of it.
 */
class RunecraftSpawns
@Inject
constructor(
    private val sites: RunecraftSites,
    private val locTypes: LocTypeList,
    private val npcTypes: NpcTypeList,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        if (!ENABLED) {
            return
        }
        onGameStartup { spawn() }
    }

    fun spawn() {
        npc(RunecraftNpcs.mage_of_zamorak, sites.home)
        npc(RunecraftNpcs.aubury, sites.aubury)
        npc(RunecraftNpcs.dark_mage, sites.darkMage)

        loc(RunecraftLocs.mine_exit, sites.mineExit)
        loc(RunecraftLocs.mine_exit, sites.astralExit)
        loc(RunecraftLocs.blood_rift, sites.bloodRift)
        loc(RunecraftLocs.astral_rift, sites.astralRift)
        loc(RunecraftLocs.wrath_rift, sites.wrathRift)
    }

    private fun npc(type: NpcType, coords: CoordGrid) {
        val npc = Npc(npcTypes[type], coords)
        npc.noneMode()
        npcRepo.add(npc, Int.MAX_VALUE)
        logger.info { "Runecraft npc: ${npc.type.internalName} at ${coords.x}, ${coords.z}" }
    }

    private fun loc(type: LocType, coords: CoordGrid) {
        locRepo.add(
            coords,
            locTypes[type],
            Int.MAX_VALUE,
            LocAngle.South,
            LocShape.CentrepieceStraight,
        )
        logger.info { "Runecraft loc: ${locTypes[type].internalName} at ${coords.x}, ${coords.z}" }
    }

    companion object {
        /** Set to `false` to stop placing the mages, portals and rifts. */
        const val ENABLED = true
    }
}
