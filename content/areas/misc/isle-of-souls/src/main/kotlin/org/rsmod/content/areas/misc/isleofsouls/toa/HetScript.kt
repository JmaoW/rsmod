package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.mining.scripts.Mining.Companion.miningAnim
import org.rsmod.content.skills.mining.scripts.Mining.Companion.pickaxeLevelReq
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The pickaxes the statue cavities in the Path of Het and the lobby can keep, in the order the
 * cavity loc shows them (the varbit holds the place in this list plus one).
 */
object HetPickaxes : ObjReferences() {
    val bronze = find("bronze_pickaxe")

    val storable: List<ObjType> =
        listOf(
            find("iron_pickaxe"),
            find("steel_pickaxe"),
            find("black_pickaxe"),
            find("mithril_pickaxe"),
            find("adamant_pickaxe"),
            find("rune_pickaxe"),
            find("trail_gilded_pickaxe"),
            find("dragon_pickaxe"),
            find("dragon_pickaxe_pretty"),
            find("zalcano_pickaxe"),
            find("infernal_pickaxe"),
            find("infernal_pickaxe_empty"),
            find("3a_pickaxe"),
            find("crystal_pickaxe"),
            find("crystal_pickaxe_inactive"),
            find("league_trailblazer_pickaxe"),
            find("trailblazer_pickaxe"),
            find("trailblazer_pickaxe_empty"),
            find("trailblazer_pickaxe_no_infernal"),
            find("trailblazer_reloaded_pickaxe"),
            find("trailblazer_reloaded_pickaxe_empty"),
            find("trailblazer_reloaded_pickaxe_no_infernal"),
        )
}

/**
 * Path of Het's interactions: mirrors and walls, the seal, the carried mirror, and the statue
 * cavities that keep a pickaxe between raids.
 */
class HetScript
@Inject
constructor(
    private val raids: ToaRaids,
    private val rooms: ToaRoomRegistry,
    private val objTypes: ObjTypeList,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(ToaRoom.HetPuzzle) { env, raid -> HetPuzzleRuntime(env, raid) }

        onOpLoc1(het_locs.mirror) { pickUp(it.loc) }
        onOpLoc2(het_locs.mirror) { rotate(it.loc, clockwise = true) }
        onOpLoc3(het_locs.mirror) { rotate(it.loc, clockwise = false) }
        onOpLoc4(het_locs.mirror) { push(it.loc) }
        onOpLoc1(het_locs.mirror_dirty) { clean(it.loc) }
        onOpHeld1(het_objs.mirror) { place() }

        onOpLoc1(het_locs.breakable_inner) { breakWall(it.loc) }
        onOpLoc1(het_locs.breakable_end) { breakWall(it.loc) }

        for (type in listOf(het_npcs.seal, het_npcs.seal_weak)) {
            // The seal never dies of damage: the last hit wins the room instead.
            onNpcQueue(type, queues.death) { rooms.runtimeOf(npc)?.hitpointsGone(npc) }
        }
        onOpNpc1(het_npcs.seal) { destroySeal(it.npc) }
        onOpNpc1(het_npcs.seal_weak) { destroySeal(it.npc) }

        for (cavity in listOf(het_locs.statue, het_locs.lobby_cavity)) {
            onOpLoc1(cavity) { takePickaxe() }
            onOpLoc2(cavity) { depositPickaxe() }
        }
    }

    private fun ProtectedAccess.room(): HetPuzzleRuntime? {
        if (raids.isGhost(player)) {
            mes("A mysterious force prevents you from doing that.")
            return null
        }
        val raid = raids.raidOf(player) ?: return null
        val member = raid.member(player) ?: return null
        if (member.room != ToaRoom.HetPuzzle) {
            return null
        }
        return rooms.runtime(raid, ToaRoom.HetPuzzle) as? HetPuzzleRuntime
    }

    private fun HetPuzzleRuntime.real(coords: CoordGrid): CoordGrid? = raid.realOf(coords)

    // Mirrors and walls -------------------------------------------------------------------------

    private fun ProtectedAccess.pickUp(loc: BoundLocInfo) {
        val room = room() ?: return
        val real = room.real(loc.coords) ?: return
        if (!room.pickUp(player, real)) {
            mes("You don't have enough space in your inventory.")
            return
        }
        anim(het_seqs.pick_up)
        soundSynth(toa_synths.het_pick)
    }

    private fun ProtectedAccess.rotate(loc: BoundLocInfo, clockwise: Boolean) {
        val room = room() ?: return
        room.real(loc.coords)?.let { room.rotate(it, clockwise) }
        soundSynth(toa_synths.het_mirror_rotate)
    }

    private fun ProtectedAccess.push(loc: BoundLocInfo) {
        val room = room() ?: return
        val real = room.real(loc.coords) ?: return
        val from = room.real(player.coords) ?: return
        anim(het_seqs.push)
        soundSynth(toa_synths.het_push)
        room.push(real, from)
    }

    private fun ProtectedAccess.clean(loc: BoundLocInfo) {
        val room = room() ?: return
        val real = room.real(loc.coords) ?: return
        if (room.clean(real)) {
            anim(het_seqs.pick_up)
            soundSynth(toa_synths.het_pick)
            mes("You clean the mirror.")
        }
    }

    private fun ProtectedAccess.place() {
        val room = room() ?: return
        val real = room.real(player.coords) ?: return
        if (!room.placeMirror(player, real)) {
            mes("You can't place that here.")
            return
        }
        anim(het_seqs.pick_up)
        soundSynth(toa_synths.het_place)
    }

    /** Swinging a pickaxe takes two game ticks; the wall gives way on the second swing. */
    private fun ProtectedAccess.breakWall(loc: BoundLocInfo) {
        val room = room() ?: return
        val real = room.real(loc.coords) ?: return
        if (!room.isBreakable(real)) {
            return
        }
        val pickaxe = Mining.findPickaxe(player, objTypes)
        if (pickaxe == null) {
            noPickaxe()
            return
        }
        if (actionDelay > mapClock) {
            opLoc1(loc)
            return
        }
        if (actionDelay < mapClock) {
            actionDelay = mapClock + SWING_TICKS
            anim(objTypes[pickaxe].miningAnim)
            mes("You swing your pick at the barrier.")
            opLoc1(loc)
            return
        }
        if (room.breakWall(real)) {
            resetAnim()
            mes("The barrier crumbles apart.")
            soundSynth(toa_synths.het_wall_mined)
            statAdvance(stats.mining, WALL_XP)
        }
    }

    // The seal ----------------------------------------------------------------------------------

    private fun ProtectedAccess.noPickaxe() {
        mes("You need a pickaxe to do that.")
        mes("You do not have a pickaxe which you have the Mining level to use.")
    }

    private fun ProtectedAccess.destroySeal(seal: Npc) {
        val room = room() ?: return
        if (!room.isStarted) {
            mes("You don't have to do that right now.")
            return
        }
        if (!room.canDestroySeal) {
            mes("A strange force is protecting the seal from damage. It must be weakened somehow.")
            return
        }
        val pickaxe = Mining.findPickaxe(player, objTypes)
        if (pickaxe == null) {
            noPickaxe()
            return
        }
        if (actionDelay > mapClock) {
            opNpc1(seal)
            return
        }
        if (actionDelay < mapClock) {
            actionDelay = mapClock + SWING_TICKS
            anim(objTypes[pickaxe].miningAnim)
            mes("You swing your pick at the seal.")
            opNpc1(seal)
            return
        }
        val type = objTypes[pickaxe]
        val range = HetMining.damageRange(player.miningLvl, type.pickaxeLevelReq)
        statAdvance(stats.mining, SEAL_XP)
        room.damageSeal(player, random.of(range))
        soundSynth(toa_synths.het_seal_hit)
        actionDelay = mapClock + SWING_TICKS
        anim(type.miningAnim)
        if (room.canDestroySeal) {
            opNpc1(seal)
        }
    }

    // The pickaxe cavities ----------------------------------------------------------------------

    private fun ProtectedAccess.takePickaxe() {
        val stored = player.vars[toa_varbits.pickaxe_stored]
        if (heldPickaxe() != null) {
            mes("You already have a pickaxe.")
            return
        }
        val take = if (stored == 0) HetPickaxes.bronze else HetPickaxes.storable[stored - 1]
        if (!player.invAdd(player.inv, take).success) {
            mes("You don't have any space in your inventory.")
            return
        }
        vars[toa_varbits.pickaxe_stored] = 0
        anim(het_seqs.take)
        mes("You take the ${objTypes[take].name.lowercase()}.")
    }

    private fun ProtectedAccess.depositPickaxe() {
        val held = heldPickaxe()
        if (held == null) {
            mes("You don't have anything to deposit.")
            return
        }
        if (player.vars[toa_varbits.pickaxe_stored] != 0) {
            mes("There's already a pickaxe stored here.")
            return
        }
        val index = HetPickaxes.storable.indexOfFirst { it.id == held.id }
        if (index < 0) {
            mes("There's no point storing a bronze pickaxe here. Het will provide.")
            return
        }
        val removed =
            if (player.righthand?.id == held.id) {
                player.invDel(player.worn, held, 1).success
            } else {
                player.invDel(player.inv, held, 1).success
            }
        if (!removed) {
            return
        }
        vars[toa_varbits.pickaxe_stored] = index + 1
        anim(het_seqs.take)
        mes("You place down the ${objTypes[held].name.lowercase()}.")
    }

    /** The best pickaxe carried or wielded that a cavity would deal with, bronze included. */
    private fun ProtectedAccess.heldPickaxe(): ObjType? {
        val candidates = HetPickaxes.storable + HetPickaxes.bronze
        val worn = player.righthand?.id
        val carried =
            candidates.filter { type -> type.id == worn || player.inv.any { it?.id == type.id } }
        return carried.maxByOrNull { HetPickaxes.storable.indexOf(it) }
    }

    companion object {
        const val SWING_TICKS = 2
        const val WALL_XP = 25.0
        const val SEAL_XP = 35.0
    }
}
