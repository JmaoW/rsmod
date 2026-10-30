package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.config.refs.components
import org.rsmod.api.drops.DropResult
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.tasks.league_progress
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.inv.InvObj
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.region.util.RegionRotations
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

/** What came of a player asking to go into the Gauntlet. */
enum class GauntletEntry {
    Entered,
    AlreadyInside,

    /** Bryn has not yet explained the place to them. */
    NotBriefed,

    /** There is something in the reward chest. */
    RewardWaiting,

    /** They hold or wear something; nothing can be taken in. */
    HandsFull,

    /** The Corrupted version opens after one completion. */
    CorruptedLocked,

    /** Their team's run has reached the Hunllef; the maze is closed. */
    TooLate,

    /** Their team is in the other version. */
    OtherVersion,

    /** They died or left this maze and cannot go back into it. */
    Barred,
    NoSpace,

    /** The maze could not be built. */
    InvalidEntry,
}

/**
 * Runs the Gauntlet: one [GauntletRun] per team (or lone player) at a time. A run's maze is a small
 * region that starts empty (every zone blank and unwalkable, which is the fog) and gains rooms as
 * they are lit; players inside are sent the region again when it changes.
 *
 * Players go into a maze with nothing and come out with nothing: the run hands out the starting kit
 * and takes back everything on every way out.
 */
class Gauntlet
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val registry: RegionRegistry,
    private val collision: CollisionFlagMap,
    private val zoneActivity: ZonePlayerActivityBitSet,
    private val players: PlayerList,
    private val teams: LeagueTeams,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val eventBus: EventBus,
    private val objTypes: ObjTypeList,
    private val nodes: GauntletNodes,
    private val resources: GauntletResources,
    private val monsters: GauntletMonsters,
    private val hunllef: GauntletHunllef,
    private val rewards: GauntletRewards,
    private val scoreboard: GauntletScoreboard,
    private val tracker: LeagueTaskTracker,
) {
    private val runs = LinkedHashMap<String, GauntletRun>()

    /** Players who died in a maze and have yet to be placed in the lobby by the respawn. */
    private val respawning = HashSet<Int>()

    /** The random numbers a maze is planned and a start tile picked with. Tests replace it. */
    var pick: (Int) -> Int = { random.of(it) }

    /** Every run that is going on. */
    fun liveRuns(): List<GauntletRun> = runs.values.toList()

    /** The run [player] is inside, if any. */
    fun runOf(player: Player): GauntletRun? =
        runs.values.firstOrNull { run -> run.members.any { it === player } }

    // Entering ----------------------------------------------------------------------------------

    /** Whether [player] has completed either version at least once. */
    fun hasCompleted(player: Player): Boolean =
        player.vars[gauntlet_varps.done] + player.vars[gauntlet_varps.done_hm] > 0

    /**
     * Takes [player] into their team's run of [mode], starting one if the team has none. Every
     * condition Bryn and the entrance enforce is checked here.
     */
    fun enter(player: Player, mode: GauntletMode): GauntletEntry {
        sweep()
        if (runOf(player) != null) {
            return GauntletEntry.AlreadyInside
        }
        if (player.vars[gauntlet_varps.bryn] == 0) {
            return GauntletEntry.NotBriefed
        }
        if (player.vars[gauntlet_varbits.reward_available] != 0) {
            return GauntletEntry.RewardWaiting
        }
        if (!isEmptyHanded(player)) {
            return GauntletEntry.HandsFull
        }
        if (mode.corrupted && !hasCompleted(player)) {
            return GauntletEntry.CorruptedLocked
        }

        val owner = ownerOf(player)
        var run = runs[owner]
        if (run != null) {
            when {
                run.mode != mode -> return GauntletEntry.OtherVersion
                run.stage != GauntletStage.Prep -> return GauntletEntry.TooLate
                player.accountId in run.barred -> return GauntletEntry.Barred
            }
        } else {
            run = createRun(owner, mode) ?: return GauntletEntry.NoSpace
            runs[owner] = run
        }

        val dest = run.startTile(pick)
        if (!collision.isZoneValid(dest)) {
            return GauntletEntry.InvalidEntry
        }
        join(run, player, dest)
        return GauntletEntry.Entered
    }

    private fun join(run: GauntletRun, player: Player, dest: CoordGrid) {
        run.members += player
        VarPlayerIntMapSetter.set(player, gauntlet_varps.in_run, 1)
        PathingEntityCommon.telejump(player, collision, dest)
        // The engine only marks a zone as occupied when it next processes the player, and a region
        // that looks empty is reclaimed as soon as any other region is created.
        zoneActivity.flag(ZoneKey.from(dest))
        giveKit(player, run.mode)
        syncMaze(player, run)
        player.ifOpenOverlay(
            gauntlet_interfaces.overlay,
            components.toplevel_target_overlay_hud,
            eventBus,
        )
        player.runClientScript(TIMER_UPDATE_SCRIPT, run.prepLeft)
    }

    private fun createRun(owner: String, mode: GauntletMode): GauntletRun? {
        val region = regionRepo.add(RegionTemplate.create {}) ?: return null
        val layout = GauntletLayout.plan(pick)
        val run = GauntletRun(owner, mode, region, layout, mapClock.cycle)
        // The Hunllef's room and the start room are there from the beginning.
        lightRoom(run, layout.boss)
        lightRoom(run, layout.start)
        hunllef.spawn(run, pick)
        return run
    }

    private fun giveKit(player: Player, mode: GauntletMode) {
        player.worn[Wearpos.RightHand.slot] = InvObj(mode.sceptre)
        // As equipping from the pack does: tell the weapon scripts, then redraw the player.
        eventBus.publish(
            HeldEquipEvents.WearposChange(player, Wearpos.RightHand, objTypes[mode.sceptre])
        )
        player.rebuildAppearance()
        for (tool in mode.tools) {
            player.invAdd(player.inv, tool, 1)
        }
    }

    private fun isEmptyHanded(player: Player): Boolean =
        player.inv.isEmpty() && player.worn.isEmpty()

    private fun ownerOf(player: Player): String {
        val team = teams.teamOf(player)
        return if (team != null) "team:${team.id}" else "solo:${player.accountId}"
    }

    // The maze ----------------------------------------------------------------------------------

    /**
     * Draws [room] into the run's maze: its four zones, on all four levels, turned the way the plan
     * says. Everyone inside is sent the region again so the client builds the new room. Returns
     * `false` if the room was already lit.
     */
    fun lightRoom(run: GauntletRun, room: GauntletRoom): Boolean {
        if (room.lit) {
            return false
        }
        room.lit = true
        val sw = run.region.southWestZone
        val rotation = room.rotation
        for (level in 0 until CoordGrid.LEVEL_COUNT) {
            for (dx in 0 until GauntletGrid.ROOM_CHUNKS) {
                for (dz in 0 until GauntletGrid.ROOM_CHUNKS) {
                    val turned =
                        RegionRotations.translateZone(
                            rotation,
                            dx,
                            dz,
                            GauntletGrid.ROOM_CHUNKS,
                            GauntletGrid.ROOM_CHUNKS,
                        )
                    val regionZone =
                        ZoneKey(
                            sw.x + room.x * GauntletGrid.ROOM_CHUNKS + turned.x,
                            sw.z + room.z * GauntletGrid.ROOM_CHUNKS + turned.z,
                            level,
                        )
                    val normal =
                        ZoneKey(
                            room.templateChunkX + run.mode.chunkOffset + dx,
                            room.templateChunkZ + dz,
                            level,
                        )
                    registry.registerZone(
                        run.region,
                        regionZone,
                        RegionZoneCopy(normal, rotation, flag = null),
                    )
                }
            }
        }
        for (member in run.members) {
            member.requestRegionRebuild()
            VarPlayerIntMapSetter.set(member, gauntlet_varbits.room_found[room.index], 1)
        }
        nodes.lightPassages(run, room)
        resources.stock(run, room, pick)
        monsters.populate(run, room, pick)
        return true
    }

    /** What lighting a node did. */
    enum class LightResult {
        Lit,
        AlreadyLit,

        /** The node is on the maze's edge: there is no room past it. */
        NothingBeyond,

        /** The sceptre lights nodes; without one in the pack or in hand nothing happens. */
        NoSceptre,
        NotInRun,
    }

    /**
     * [player] lights the node that stands at [at] (a loc of [type] at angle [angle]): the room on
     * the other side of its side of the room is drawn, and the passage's nodes turn lit.
     */
    fun lightNode(player: Player, at: CoordGrid, type: UnpackedLocType, angle: Int): LightResult {
        val run = runOf(player) ?: return LightResult.NotInRun
        if (run.stage != GauntletStage.Prep) {
            return LightResult.NotInRun
        }
        if (run.mode.sceptre !in player.inv && run.mode.sceptre !in player.worn) {
            return LightResult.NoSceptre
        }
        val room = run.roomAt(at) ?: return LightResult.NotInRun
        val side = nodes.sideOf(run, room, at, type, angle)
        val next =
            run.layout.room(room.x + side.dx, room.z + side.dz) ?: return LightResult.NothingBeyond
        if (!lightRoom(run, next)) {
            return LightResult.AlreadyLit
        }
        return LightResult.Lit
    }

    /** What using the teleport crystal did. */
    enum class CrystalResult {
        Teleported,

        /** Wiki: it does not work in the start room. */
        InStartRoom,

        /** Wiki: once the fight has begun, "you win or you die". */
        BossPhase,
        NotInRun,
    }

    /** The teleport crystal takes [player] back to the start room, once. */
    fun useTeleportCrystal(player: Player): CrystalResult {
        val run = runOf(player) ?: return CrystalResult.NotInRun
        if (run.stage != GauntletStage.Prep) {
            return CrystalResult.BossPhase
        }
        if (run.roomAt(player.coords) == run.layout.start) {
            return CrystalResult.InStartRoom
        }
        if (!player.invDel(player.inv, run.mode.teleportCrystal, 1).success) {
            return CrystalResult.NotInRun
        }
        PathingEntityCommon.telejump(player, collision, run.startTile(pick))
        return CrystalResult.Teleported
    }

    /** Sets the maze map's varbits for [player]: the version, the start room, the lit rooms. */
    private fun syncMaze(player: Player, run: GauntletRun) {
        VarPlayerIntMapSetter.set(
            player,
            gauntlet_varbits.corrupted,
            if (run.mode.corrupted) 1 else 0,
        )
        VarPlayerIntMapSetter.set(player, gauntlet_varbits.in_gauntlet, 1)
        VarPlayerIntMapSetter.set(
            player,
            gauntlet_varbits.boss_started,
            if (run.stage == GauntletStage.Boss) 1 else 0,
        )
        VarPlayerIntMapSetter.set(
            player,
            gauntlet_varbits.start,
            run.layout.start.z * GauntletGrid.SIZE + run.layout.start.x,
        )
        for (room in run.layout.all()) {
            VarPlayerIntMapSetter.set(
                player,
                gauntlet_varbits.room_found[room.index],
                if (room.lit) 1 else 0,
            )
        }
        updateRoomVarbits(player, run)
    }

    /** The maze map marks the room the player stands in. */
    private fun updateRoomVarbits(player: Player, run: GauntletRun) {
        val room = run.roomAt(player.coords) ?: return
        if (player.vars[gauntlet_varbits.current_room_x] != room.x) {
            VarPlayerIntMapSetter.set(player, gauntlet_varbits.current_room_x, room.x)
        }
        if (player.vars[gauntlet_varbits.current_room_z] != room.z) {
            VarPlayerIntMapSetter.set(player, gauntlet_varbits.current_room_z, room.z)
        }
    }

    // The clock ---------------------------------------------------------------------------------

    /** Advances every run one tick. Called once per game cycle. */
    fun tick() {
        if (runs.isEmpty()) {
            return
        }
        for (run in runs.values.toList()) {
            advance(run)
        }
        sweep()
    }

    private fun advance(run: GauntletRun) {
        // Anyone moved out of the maze by something else has left it.
        for (member in run.members.toList()) {
            if (!run.contains(member.coords)) {
                leave(member, GauntletExit.Left)
            }
        }
        if (run.stage == GauntletStage.Ended) {
            return
        }
        for (member in run.members) {
            updateRoomVarbits(member, run)
        }
        monsters.tick(run)
        if (run.stage == GauntletStage.Boss) {
            hunllef.tick(run, pick)
            if (run.won && mapClock.cycle >= run.endAt) {
                for (member in run.members.toList()) {
                    leave(member, GauntletExit.Completed)
                }
            }
        }
        if (run.stage != GauntletStage.Prep) {
            return
        }
        run.prepLeft--
        if (run.prepLeft <= 0) {
            startBossPhase(run, forced = true)
        } else if (run.prepLeft % TIMER_RESYNC == 0 || run.prepLeft < TIMER_FINAL_SYNC) {
            for (member in run.members) {
                member.runClientScript(TIMER_UPDATE_SCRIPT, run.prepLeft)
            }
        }
    }

    /**
     * Ends the preparation: through the barrier, or when the time runs out. Everyone still inside
     * is taken to the Hunllef's room together. Returns `false` if the phase had already begun.
     */
    fun startBossPhase(run: GauntletRun, forced: Boolean): Boolean {
        if (run.stage != GauntletStage.Prep) {
            return false
        }
        run.stage = GauntletStage.Boss
        run.bossCycle = mapClock.cycle
        hunllef.start(run)
        for ((index, member) in run.members.withIndex()) {
            VarPlayerIntMapSetter.set(member, gauntlet_varbits.boss_started, 1)
            member.runClientScript(TIMER_END_SCRIPT)
            PathingEntityCommon.telejump(member, collision, run.arenaTile(index))
            if (forced) {
                member.mes("You have run out of time, and are taken to face the Hunllef.")
            }
        }
        return true
    }

    // Leaving -----------------------------------------------------------------------------------

    /**
     * Takes [player] out of their run for good: the kit goes, the maze map closes, and (unless the
     * reason already moved them) they are put in the lobby. Returns `false` if they were in none.
     */
    fun leave(player: Player, reason: GauntletExit): Boolean {
        val run = runOf(player) ?: return false
        run.members.removeAll { it === player }
        run.barred += player.accountId
        clearKit(player)

        // What the run earned, by how it ended (see [GauntletRewards]).
        val kind = rewards.kindFor(reason, run.points[player.accountId] ?: 0)
        if (kind != null) {
            rewards.award(player, run.mode, kind)
            player.mes("Your reward awaits you in the nearby chest.")
        }

        VarPlayerIntMapSetter.set(player, gauntlet_varps.in_run, 0)
        VarPlayerIntMapSetter.set(player, gauntlet_varbits.in_gauntlet, 0)
        VarPlayerIntMapSetter.set(player, gauntlet_varbits.boss_started, 0)
        player.ifCloseOverlay(gauntlet_interfaces.overlay, eventBus)

        when (reason) {
            GauntletExit.Exit,
            GauntletExit.Escape,
            GauntletExit.Completed,
            GauntletExit.Logout -> moveTo(player, LOBBY)
            GauntletExit.Died -> respawning += player.accountId
            GauntletExit.Left -> {}
        }
        if (run.members.isEmpty()) {
            run.stage = GauntletStage.Ended
        }
        sweep()
        return true
    }

    /** Removes the runs that have nobody in them. */
    fun sweep() {
        val iterator = runs.values.iterator()
        while (iterator.hasNext()) {
            val run = iterator.next()
            if (run.stage == GauntletStage.Ended || run.members.isEmpty()) {
                run.stage = GauntletStage.Ended
                monsters.clear(run)
                hunllef.clear(run)
                iterator.remove()
            }
        }
    }

    /** The run whose Hunllef (in any form) or tornado is [npc]. */
    fun runOfHunllef(npc: Npc): GauntletRun? =
        runs.values.firstOrNull { run -> run.hunllef?.npc === npc }

    /**
     * The Hunllef died: the run is won. Every player still in it has it counted, and the team is
     * taken to the lobby a few ticks later.
     */
    fun hunllefDied(run: GauntletRun) {
        if (run.stage != GauntletStage.Boss || run.won) {
            return
        }
        run.won = true
        run.endAt = mapClock.cycle + COMPLETE_DELAY
        hunllef.clearExtras(run)
        val now = mapClock.cycle
        val total = (now - run.startCycle).coerceAtLeast(1)
        val prep = ((run.bossCycle.takeIf { it > 0 } ?: now) - run.startCycle).coerceAtLeast(0)
        val kill = (now - (run.bossCycle.takeIf { it > 0 } ?: now)).coerceAtLeast(0)
        scoreboard.completion(run.mode, total)
        val counted = HashSet<Any>()
        for (member in run.members) {
            markCompleted(member, run.mode)
            // The "complete it N times" tasks are the team's: count the run once for each team.
            val countRun = counted.add(teams.teamOf(member) ?: member.username)
            finished(run, member, total, prep, kill, countRun)
        }
    }

    /** The statistics, messages and tasks of a player who was in the maze when the Hunllef died. */
    private fun finished(
        run: GauntletRun,
        player: Player,
        total: Int,
        prep: Int,
        kill: Int,
        countRun: Boolean,
    ) {
        val mode = run.mode
        val kc = if (mode.corrupted) gauntlet_varps.kc_hm else gauntlet_varps.kc
        VarPlayerIntMapSetter.set(player, kc, player.vars[kc] + 1)

        val bestVarp = if (mode.corrupted) gauntlet_varps.best_hm else gauntlet_varps.best
        val previous = player.vars[bestVarp]
        val record = previous == 0 || total < previous
        if (record) {
            VarPlayerIntMapSetter.set(player, bestVarp, total)
        }
        val done = player.vars[if (mode.corrupted) gauntlet_varps.done_hm else gauntlet_varps.done]
        val name = if (mode.corrupted) "Corrupted Gauntlet" else "Gauntlet"
        player.mes(
            "Challenge duration: ${red(GauntletScoreboard.time(total))}." +
                if (record) " (new personal best)"
                else " Personal best: ${GauntletScoreboard.time(previous)}"
        )
        player.mes(
            "Preparation time: ${red(GauntletScoreboard.time(prep))}. " +
                "Hunllef kill time: ${red(GauntletScoreboard.time(kill))}."
        )
        player.mes("Your $name completion count is: ${red(done.toString())}.")

        tracker.complete(player, if (mode.corrupted) TASK_COMPLETE_CORRUPTED else TASK_COMPLETE)
        if (mode.corrupted && countRun) {
            tracker.addProgress(player, TASK_CORRUPTED_50, league_progress.gauntlet_hm_50, 50)
            tracker.addProgress(player, TASK_CORRUPTED_150, league_progress.gauntlet_hm_150, 150)
        }
    }

    private fun red(text: String) = "<col=ef1020>$text</col>"

    /** The run a monster belongs to. */
    fun runOf(npc: Npc): GauntletRun? = monsters.runOf(npc, runs.values)

    /**
     * A monster of a run was killed by [hero]: counts the points and returns what it drops for
     * them. Called from the drop hook.
     */
    fun monsterKilled(run: GauntletRun, npc: Npc, hero: Player): List<DropResult> =
        monsters.killed(run, npc, hero, owns = { it in hero.inv || it in hero.worn }, pick = pick)

    /**
     * Takes everything the player carries. What was worn is taken off as unequipping would: the
     * weapon scripts hear of it (the combat tab, special attack, bonuses) and the player is
     * redrawn. At login the client's tabs are not up yet, so only the model is redrawn.
     */
    private fun clearKit(player: Player, notify: Boolean = true) {
        val taken = Wearpos.entries.mapNotNull { pos -> player.worn[pos.slot]?.let { pos to it } }
        player.inv.fillNulls()
        player.worn.fillNulls()
        if (notify) {
            for ((pos, obj) in taken) {
                WornUnequipOp.notifyWornUnequip(
                    player,
                    pos,
                    objTypes[obj],
                    eventBus,
                    rebuildAppearance = false,
                )
            }
        }
        player.rebuildAppearance()
    }

    private fun moveTo(player: Player, dest: CoordGrid) {
        PathingEntityCommon.telejump(player, collision, dest)
        zoneActivity.flag(ZoneKey.from(dest))
    }

    /** A player died in a maze: they are out of it, and the respawn puts them in the lobby. */
    fun died(player: Player) {
        val run = runOf(player)
        if (run != null) {
            val deaths = if (run.mode.corrupted) gauntlet_varps.deaths_hm else gauntlet_varps.deaths
            VarPlayerIntMapSetter.set(player, deaths, player.vars[deaths] + 1)
            scoreboard.death(run.mode)
        }
        leave(player, GauntletExit.Died)
    }

    /** Where a player who just died in a maze wakes up, once; `null` for everyone else. */
    fun respawnFor(player: Player): CoordGrid? =
        if (respawning.remove(player.accountId)) LOBBY else null

    /**
     * The escape crystal takes [player] out of the maze at once, in the preparation or the fight.
     * How long it channels in the real game is not known. Returns `false` if they are in no run.
     */
    fun useEscapeCrystal(player: Player): Boolean {
        if (runOf(player) == null) {
            return false
        }
        player.mes("You activate the escape crystal and are taken out of the Gauntlet.")
        return leave(player, GauntletExit.Escape)
    }

    /** Logging out inside takes the kit and leaves the player in the lobby. */
    fun loggedOut(player: Player) {
        leave(player, GauntletExit.Logout)
    }

    /**
     * Logging in after a run was cut short (the server stopped, or the logout did not get to save
     * the cleanup): whatever the player carries is the kit, so it goes, and they wake up in the
     * lobby.
     */
    fun loggedIn(player: Player) {
        syncCompletion(player)
        rewards.syncChest(player)
        if (player.vars[gauntlet_varps.in_run] == 0 || runOf(player) != null) {
            return
        }
        clearKit(player, notify = false)
        VarPlayerIntMapSetter.set(player, gauntlet_varps.in_run, 0)
        VarPlayerIntMapSetter.set(player, gauntlet_varbits.in_gauntlet, 0)
        VarPlayerIntMapSetter.set(player, gauntlet_varbits.boss_started, 0)
        moveTo(player, LOBBY)
        player.mes("Your Gauntlet run is no longer available. You have been returned to the lobby.")
    }

    // Completions -------------------------------------------------------------------------------

    /** Counts a completed run and tells the client, so the entrance offers the Corrupted one. */
    fun markCompleted(player: Player, mode: GauntletMode) {
        val varp = if (mode.corrupted) gauntlet_varps.done_hm else gauntlet_varps.done
        VarPlayerIntMapSetter.set(player, varp, player.vars[varp] + 1)
        syncCompletion(player)
    }

    /** Mirrors the saved counts into the varps the client's entrance reads. */
    fun syncCompletion(player: Player) {
        VarPlayerIntMapSetter.set(
            player,
            gauntlet_varps.client_done,
            player.vars[gauntlet_varps.done],
        )
        VarPlayerIntMapSetter.set(
            player,
            gauntlet_varps.client_done_hm,
            player.vars[gauntlet_varps.done_hm],
        )
    }

    companion object {
        /** The lobby under Prifddinas: the entrance, the chest, the scoreboard and Bryn. */
        val LOBBY = CoordGrid(3032, 6127, 1)

        /** In front of the portal on the isle, where the lobby's platform brings a player back. */
        val PORTAL_FRONT = CoordGrid(2124, 2892, 0)

        /** `gauntlet_timer_update(ticks)` and `gauntlet_timer_end()` (cs2 2914 and 2916). */
        const val TIMER_UPDATE_SCRIPT = 2914
        const val TIMER_END_SCRIPT = 2916

        /** The league tasks the Gauntlet feeds. */
        const val TASK_COMPLETE = 534
        const val TASK_COMPLETE_CORRUPTED = 535
        const val TASK_CORRUPTED_50 = 547
        const val TASK_CORRUPTED_150 = 548

        /**
         * Ticks between the Hunllef's last breath and the team leaving: long enough for both parts
         * of its death (four and three ticks, see [GauntletHunllefScript]) to play out first.
         */
        const val COMPLETE_DELAY = 10

        /** The overlay counts down by itself; it is told the time left now and then. */
        const val TIMER_RESYNC = 50
        const val TIMER_FINAL_SYNC = 15
    }
}
