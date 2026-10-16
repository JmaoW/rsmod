package org.rsmod.content.areas.misc.isleofsouls.starter

import jakarta.inject.Inject
import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.obj.Obj
import org.rsmod.game.obj.ObjEntity
import org.rsmod.game.obj.ObjScope
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The small things the isle's starting areas need: a pickaxe to take from the rocks by the mine,
 * Gerrant's fishing shop, a general store, and a hammer by the anvil and a furnace on the hill. The
 * axe on the stump by the starting trees is Lumbridge's "Take-axe" logs, which this only places.
 *
 * Everything is placed when the server starts. Each rock face and stump comes back with its tool
 * [TOOL_RESPAWN_TICKS] ticks after the tool is taken.
 */
class StarterScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
    private val mapClock: MapClock,
    private val shops: Shops,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { place() }

        onOpLoc1(StarterLocs.rocks_with_pickaxe) { takePickaxe(it.loc) }
        onOpNpc1(StarterNpcs.gerrant) { talkToGerrant(it.npc) }
        onOpNpc3(StarterNpcs.gerrant) { player.openFishingShop(it.npc) }
    }

    /** Puts everything on the isle. Runs when the server starts. */
    fun place() {
        placeLoc(StarterLocs.rocks_with_pickaxe, PICKAXE_ROCKS, TOOL_ANGLE)
        placeLoc(StarterLocs.stump_with_axe, AXE_STUMP, TOOL_ANGLE)
        placeLoc(StarterLocs.furnace, FURNACE, FURNACE_ANGLE)
        placeNpc(StarterNpcs.gerrant, GERRANT)
        placeNpc(StarterNpcs.shop_keeper, SHOP_KEEPER)
        placeHammer()
    }

    private fun placeLoc(type: LocType, coords: CoordGrid, angle: LocAngle) {
        locRepo.add(coords, type, Int.MAX_VALUE, angle, LocShape.CentrepieceStraight)
    }

    private fun placeNpc(type: NpcType, coords: CoordGrid) {
        npcRepo.add(Npc(npcTypes[type], coords), Int.MAX_VALUE)
    }

    /**
     * The hammer is a permanent ground spawn, the same kind the map has: when a player takes it,
     * the object repository puts it back after the hammer's respawn time (see [StarterObjEdits]).
     */
    private fun placeHammer() {
        val entity = ObjEntity(objs.hammer.id, 1, ObjScope.Perm.id)
        val hammer = Obj(HAMMER, entity, mapClock.cycle, Obj.NULL_OBSERVER_ID)
        objRepo.add(hammer, Int.MAX_VALUE)
    }

    private suspend fun ProtectedAccess.takePickaxe(loc: BoundLocInfo) {
        if (content.mining_pickaxe in inv) {
            mesbox("You already have a pickaxe.")
            return
        }

        if (inv.isFull()) {
            mesbox("You don't have enough room for the pickaxe.")
            return
        }

        locRepo.change(loc, StarterLocs.rocks, TOOL_RESPAWN_TICKS)
        invAddOrDrop(objRepo, objs.bronze_pickaxe)
        soundSynth(synths.take_axe)
        objbox(objs.bronze_pickaxe, 400, "You take a bronze pickaxe from the rocks.")
    }

    private fun Player.openFishingShop(npc: Npc) {
        shops.open(this, npc, "Gerrant's Fishy Business", StarterInvs.fishing_shop)
    }

    private suspend fun ProtectedAccess.talkToGerrant(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Welcome to Gerrant's Fishy Business! Would you like to buy anything?")
            val choice = choice2("Let's see what you've got.", 1, "No, thanks.", 2)
            when (choice) {
                1 -> {
                    chatPlayer(happy, "Let's see what you've got.")
                    player.openFishingShop(npc)
                }
                else -> chatPlayer(neutral, "No, thanks.")
            }
        }
    }

    companion object {
        /** Ticks until a rock face or stump has its tool again. */
        const val TOOL_RESPAWN_TICKS = 10

        /** The rocks by the mine, with a bronze pickaxe stuck in them. Two tiles wide. */
        val PICKAXE_ROCKS = CoordGrid(2195, 2785, 0)

        /** The stump near the starting trees, with a bronze axe in it. */
        val AXE_STUMP = CoordGrid(2168, 2808, 0)

        /** Gerrant stands in the fishing area. */
        val GERRANT = CoordGrid(2167, 2786, 0)

        /** The general store's shop keeper, in the crafting area. */
        val SHOP_KEEPER = CoordGrid(2162, 2805, 0)

        /** A furnace on the hill by the anvil. */
        val FURNACE = CoordGrid(2218, 2787, 0)

        /** The hammer lying by the anvil. */
        val HAMMER = CoordGrid(2217, 2789, 0)

        /** The rocks and stump are placed the way their models were made. */
        val TOOL_ANGLE = LocAngle.West

        /** Faces the way Edgeville's furnace does. */
        val FURNACE_ANGLE = LocAngle.East
    }
}
