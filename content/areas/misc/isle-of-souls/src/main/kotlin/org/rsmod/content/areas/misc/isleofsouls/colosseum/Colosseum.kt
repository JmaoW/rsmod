package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statRestoreAll
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.event.AnnouncementMessages
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueAnnouncer
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.pets.Pets
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid

/** What happened when a player tried the tunnel into the arena. */
enum class ColosseumEntry {
    Entered,
    Rejoined,
    AlreadyInside,

    /** Their team is fighting a wave: they can come back at the next intermission. */
    WaveUnderway,

    /** They gave up this run, or left it, and cannot go back into it. */
    Barred,
    NoSpace,
    InvalidEntry,
}

/**
 * Runs the Fortis Colosseum: one [ColosseumRun] per team's arena, advanced every game tick.
 *
 * The flow (wiki, and tsps' capture of the live game): the team arrives south of the middle, where
 * Minimus waits. His Start-wave opens the intermission screen with three modifiers; the first
 * teammate to confirm one picks it for the team, and the wave comes in 5 ticks later. A wave is
 * cleared when its monsters are dead (reinforcements arrive after 40 seconds), and Minimus comes
 * back for the next. Each player earns their own loot and Glory for every wave they clear. Wave 12
 * is Sol Heredit; beating him, or giving up between waves, brings the rewards chest out.
 *
 * A player who dies respawns in the lobby and keeps their chest: they can walk back in at the next
 * intermission (user decision). The run ends, and everyone still owed loot loses it, when nobody is
 * left in the arena. Teleporting or logging out loses a player's loot and the run's Glory; dying
 * keeps the Glory (wiki).
 */
class Colosseum
@Inject
constructor(
    private val instances: TeamInstances,
    private val world: ColosseumWorld,
    private val players: PlayerList,
    private val teams: LeagueTeams,
    private val enemies: ColosseumEnemies,
    private val hazards: ColosseumHazards,
    private val effects: ColosseumEffects,
    private val sol: SolHeredit,
    private val screens: ColosseumScreens,
    private val completions: ColosseumCompletions,
    private val collectionLog: CollectionLog,
    private val announcer: LeagueAnnouncer,
    private val objRepo: ObjRepository,
    private val eventBus: EventBus,
    private val pets: Pets,
) {
    /** Set when the server starts, from `event-settings.json`. */
    var settings: ColosseumSettings = ColosseumSettings()

    private val runs = LinkedHashMap<String, ColosseumRun>()

    /** Players who died in a run and wake up in the lobby. */
    private val respawning = HashSet<Int>()

    fun liveRuns(): List<ColosseumRun> = runs.values.toList()

    /** The run whose arena [player] stands in. */
    fun runOf(player: Player): ColosseumRun? =
        runs.values.firstOrNull { it.instance.registered && it.instance.contains(player.coords) }

    /** [player]'s team's run, wherever they stand. */
    fun teamRun(player: Player): ColosseumRun? = runs[ownerOf(player)]

    fun runOf(npc: Npc): ColosseumRun? =
        runs.values.firstOrNull { it.instance.registered && it.instance.contains(npc.coords) }

    private fun ownerOf(player: Player): String {
        val team = teams.teamOf(player)
        return if (team != null) "team:${team.id}" else "solo:${player.accountId}"
    }

    // Entering ---------------------------------------------------------------------------------

    /** The tunnel: into the team's arena, starting a run if the team has none. */
    fun enter(player: Player): ColosseumEntry {
        sweep()
        if (runOf(player) != null) {
            return ColosseumEntry.AlreadyInside
        }
        val existing = runs[ownerOf(player)]
        if (existing != null) {
            return rejoin(existing, player)
        }
        val result = instances.enter(player, ColosseumInstance.type)
        when (result) {
            EnterResult.Entered -> {}
            EnterResult.AlreadyInside -> return ColosseumEntry.AlreadyInside
            EnterResult.NoSpace -> return ColosseumEntry.NoSpace
            EnterResult.InvalidEntry -> return ColosseumEntry.InvalidEntry
        }
        val instance = checkNotNull(instances.instanceOf(player))
        val run = ColosseumRun(ownerOf(player), instance, settings.waves)
        runs[run.owner] = run
        setUpArena(run, player)
        join(run, player)
        return ColosseumEntry.Entered
    }

    private fun rejoin(run: ColosseumRun, player: Player): ColosseumEntry {
        val member = run.memberOf(player)
        if (member != null && !member.dead) {
            return ColosseumEntry.Barred
        }
        if (run.stage != ColosseumStage.Intermission && run.stage != ColosseumStage.Finished) {
            return ColosseumEntry.WaveUnderway
        }
        when (instances.enter(player, ColosseumInstance.type)) {
            EnterResult.Entered -> {}
            EnterResult.AlreadyInside -> return ColosseumEntry.AlreadyInside
            EnterResult.NoSpace -> return ColosseumEntry.NoSpace
            EnterResult.InvalidEntry -> return ColosseumEntry.InvalidEntry
        }
        if (member != null) {
            member.player = player
            member.dead = false
            screens.syncTiers(player, run.modifiers)
            VarPlayerIntMapSetter.set(player, colosseum_varps.current_glory, member.glory)
            player.mes("You rejoin your team in the arena. Your rewards are still waiting for you.")
            return ColosseumEntry.Rejoined
        }
        join(run, player)
        return ColosseumEntry.Entered
    }

    private fun join(run: ColosseumRun, player: Player) {
        val member = ColosseumMember(player.accountId, player)
        member.future = rollLoot(player, run.nextWave ?: ColosseumWaves.FINAL_WAVE)
        run.members[player.accountId] = member
        for (varp in RUN_VARPS) {
            VarPlayerIntMapSetter.set(player, varp(), 0)
        }
        VarPlayerIntMapSetter.set(player, colosseum_varbits.selected_modifier, 0)
        screens.syncTiers(player, run.modifiers)
        player.client.setNpcViewDistance(NPC_VIEW_DISTANCE)
        // Capture: from the next tick the player walks up to Minimus, stopping a tile short.
        world.later(run, 1) {
            if (run.stage == ColosseumStage.Intermission && run.cleared == 0) {
                player.walk(run.inst(ColosseumArena.arrivalWalk))
            }
        }
    }

    /** Sol Heredit on his seat, and Minimus to greet the team. */
    private fun setUpArena(run: ColosseumRun, player: Player) {
        run.seatedSol = world.spawn(run, colosseum_npcs.sol_seated, ColosseumArena.solSeat)
        run.seatBlocker = world.addLoc(run, colosseum_locs.seat_blocker, ColosseumArena.solSeat)
        spawnMinimus(run, ColosseumArena.minimusStart)
        val minimus = run.minimus ?: return
        minimus.say("A ${rankOf(player)} approaches!")
        world.later(run, GREETING_DELAY) { minimus.say("Let me know when you want to begin.") }
    }

    private fun spawnMinimus(run: ColosseumRun, real: CoordGrid) {
        world.remove(run.minimus)
        val npc = world.spawn(run, colosseum_npcs.minimus, real)
        npc.anim(colosseum_seqs.minimus_spawn)
        run.minimus = npc
    }

    /** Minimus comes back a tile north of the first fighter, or beside them. */
    private fun minimusNear(run: ColosseumRun) {
        val near = run.fighters().firstOrNull()?.player ?: return
        val at = run.real(near.coords)
        val tile =
            listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
                .map { (dx, dz) -> at.translate(dx, dz) }
                .firstOrNull { world.free(run, it) } ?: at.translate(0, 1)
        spawnMinimus(run, tile)
    }

    // The tick ----------------------------------------------------------------------------------

    fun tick() {
        if (runs.isEmpty()) {
            return
        }
        for (run in runs.values.toList()) {
            advance(run)
        }
        sweep()
    }

    private fun advance(run: ColosseumRun) {
        checkMembers(run)
        if (!live(run)) {
            return
        }
        runLater(run)
        when (run.stage) {
            ColosseumStage.Starting ->
                if (world.now >= run.startAt) {
                    startWave(run)
                }
            ColosseumStage.Wave -> waveTick(run)
            ColosseumStage.SolIntro ->
                if (world.now >= run.startAt) {
                    run.stage = ColosseumStage.SolJump
                    run.startAt = world.now + sol.jump(run)
                }
            ColosseumStage.SolJump ->
                if (world.now >= run.startAt) {
                    run.stage = ColosseumStage.Wave
                    sol.land(run)
                    spawnFinalExtras(run)
                    hazards.waveStarted(run)
                }
            ColosseumStage.Intermission,
            ColosseumStage.Finished -> {}
        }
        for (member in run.fighters()) {
            effects.frailty(run, member)
        }
        tickBar(run)
    }

    /**
     * Sol Heredit's health bar for everyone fighting him, from his landing until he is gone; it
     * fades for anyone who dies or leaves.
     */
    private fun tickBar(run: ColosseumRun) {
        val boss = run.sol?.npc?.takeIf { it.isSlotAssigned }
        val watching = HashSet<Int>()
        if (boss != null) {
            for (member in run.fighters()) {
                run.bar.show(member.player, boss)
                watching += member.accountId
            }
        }
        if (run.bar.viewers.size > watching.size) {
            hideBar(run, except = watching)
        }
    }

    private fun hideBar(run: ColosseumRun, except: Set<Int> = emptySet()) {
        for (accountId in run.bar.viewers.toList()) {
            if (accountId in except) {
                continue
            }
            val player = players.firstOrNull { it.accountId == accountId }
            if (player != null) {
                run.bar.hide(player)
            } else {
                run.bar.forget(accountId)
            }
        }
    }

    /**
     * Anyone who left the arena any way but Minimus's Leave or a death (a teleport, a log-out) is
     * out of the run with nothing.
     */
    private fun checkMembers(run: ColosseumRun) {
        val iterator = run.members.values.iterator()
        while (iterator.hasNext()) {
            val member = iterator.next()
            if (member.dead) {
                continue
            }
            val online = players.firstOrNull { it.accountId == member.accountId }
            if (online != null) {
                member.player = online
            }
            if (online == null || !run.instance.contains(online.coords)) {
                iterator.remove()
                online?.let {
                    sol.endCutscene(member)
                    lostRun(it, member)
                }
            }
        }
    }

    private fun lostRun(player: Player, member: ColosseumMember) {
        clearPlayer(player)
        if (member.rewards.isNotEmpty()) {
            player.mes("You left the Colosseum without claiming your rewards: they are lost.")
        }
    }

    private fun live(run: ColosseumRun): Boolean =
        run.instance.registered && instances.playersIn(run.instance).isNotEmpty()

    private fun runLater(run: ColosseumRun) {
        if (run.later.isEmpty()) {
            return
        }
        val due = run.later.filter { it.at <= world.now }
        run.later.removeAll(due.toSet())
        for (task in due) {
            task.action()
        }
    }

    /** Ends every run that has nobody left in its arena. */
    fun sweep(except: Player? = null) {
        val iterator = runs.values.iterator()
        while (iterator.hasNext()) {
            val run = iterator.next()
            val inside =
                run.instance.registered && instances.playersIn(run.instance).any { it !== except }
            if (!inside) {
                end(run)
                iterator.remove()
            }
        }
    }

    private fun end(run: ColosseumRun) {
        for (member in run.members.values) {
            if (member.dead) {
                val player = players.firstOrNull { it.accountId == member.accountId }
                player?.mes("Your team's Colosseum run is over. Your unclaimed rewards are lost.")
                player?.let { clearPlayer(it) }
            }
        }
        for (member in run.members.values) {
            if (players.any { it.accountId == member.accountId }) {
                sol.endCutscene(member)
            }
        }
        hideBar(run)
        sol.clear(run)
        hazards.cleared(run)
        for (npc in run.allNpcs()) {
            world.remove(npc)
        }
        world.removeLoc(run.chest)
        world.removeLoc(run.seatBlocker)
        run.monsters.clear()
        run.states.clear()
        run.later.clear()
    }

    // The intermission ----------------------------------------------------------------------------

    /** Minimus's Start-wave: the intermission screen, unless a wave is already coming. */
    fun startWaveRequested(player: Player) {
        val run = runOf(player) ?: return
        val member = run.memberOf(player) ?: return
        if (run.stage != ColosseumStage.Intermission || member.forfeited) {
            return
        }
        val leaving = run.members.values.filter { it.forfeited }
        if (leaving.isNotEmpty()) {
            player.mes("Wait for your teammate to collect their rewards and leave first.")
            return
        }
        val next = run.nextWave ?: return
        if (run.offered.isEmpty()) {
            run.offered = run.modifiers.offer(next, world::roll)
        }
        screens.openIntermission(run, member)
    }

    fun selectModifier(player: Player, choice: Int) {
        val run = runOf(player) ?: return
        if (run.stage != ColosseumStage.Intermission || choice !in 1..run.offered.size) {
            return
        }
        screens.select(run, player, choice)
    }

    /** Continue: the first teammate to confirm picks the team's modifier. */
    fun confirm(player: Player) {
        val run = runOf(player) ?: return
        if (run.stage != ColosseumStage.Intermission) {
            return
        }
        val choice = player.vars[colosseum_varbits.selected_modifier]
        val modifier = run.offered.getOrNull(choice - 1) ?: return
        run.modifiers.add(modifier)
        run.offered = emptyList()
        for (member in run.members.values) {
            val other = member.player
            VarPlayerIntMapSetter.set(other, colosseum_varps.last_modifier_glory, modifier.glory)
            screens.syncTiers(other, run.modifiers)
            screens.closeIntermission(other)
            if (other !== player && run.instance.contains(other.coords)) {
                other.mes("${player.displayName} has chosen ${modifier.label} for the next wave.")
            }
        }
        world.remove(run.minimus)
        run.minimus = null
        run.stage = ColosseumStage.Starting
        run.startAt = world.now + WAVE_START_DELAY
    }

    // Waves -------------------------------------------------------------------------------------

    private fun startWave(run: ColosseumRun) {
        val wave = run.nextWave ?: return
        run.wave = wave
        run.waveStart = world.now
        run.reinforced = false
        for (member in run.members.values) {
            member.hitByEnemy = false
            member.damageTaken = 0
            member.missedWave = member.dead || !run.instance.contains(member.player.coords)
            if (member.missedWave) {
                continue
            }
            val player = member.player
            if (wave > player.vars[colosseum_varbits.highest_wave]) {
                VarPlayerIntMapSetter.set(player, colosseum_varbits.highest_wave, wave)
            }
            VarPlayerIntMapSetter.set(player, colosseum_varps.wave_start_time, world.now)
            VarPlayerIntMapSetter.set(player, colosseum_varps.wave_damage_taken, 0)
            player.mes("<col=ef1020>Wave: $wave</col>")
        }
        if (wave >= ColosseumWaves.FINAL_WAVE) {
            startSol(run)
            return
        }
        run.stage = ColosseumStage.Wave
        spawnWave(run, ColosseumWaves.starting(wave, run.modifiers, world::roll))
        hazards.waveStarted(run)
    }

    /** Wave 12: Sol Heredit greets each player who has not beaten him, then jumps down. */
    private fun startSol(run: ColosseumRun) {
        run.stage = ColosseumStage.SolIntro
        var longest = 0
        for (member in run.fighters()) {
            val player = member.player
            val beaten = player.vars[colosseum_varps.sol_kills] > 0
            val lines = sol.greeting(player, beaten)
            if (!beaten) {
                VarPlayerIntMapSetter.set(
                    player,
                    colosseum_varbits.sol_failures,
                    minOf(SOL_FAILURES_MAX, player.vars[colosseum_varbits.sol_failures] + 1),
                )
            }
            if (lines.isNotEmpty()) {
                pendingGreetings[player.accountId] = lines
                player.strongQueue(colosseum_queues.sol_greeting, 1)
            }
            longest = maxOf(longest, lines.size)
        }
        run.startAt = world.now + SOL_JUMP_DELAY + longest * SOL_LINE_TICKS
    }

    private val pendingGreetings = HashMap<Int, List<String>>()

    /** The lines Sol Heredit greets [player] with, once. */
    fun takeGreeting(player: Player): List<String> =
        pendingGreetings.remove(player.accountId) ?: emptyList()

    /** With Quartet, a random warbander joins Sol Heredit's wave (wiki). */
    private fun spawnFinalExtras(run: ColosseumRun) {
        if (!run.modifiers.has(ColosseumModifier.Quartet)) {
            return
        }
        val kind = ColosseumMonster.WARBAND[world.roll(ColosseumMonster.WARBAND.size)]
        val tile = ColosseumArena.solPlayerStart.translate(0, -1)
        spawnMonster(run, kind, tile, ColosseumEnemies.EXTRA_SIDE)
    }

    /**
     * Puts a wave's monsters down: the trio round a random tile in the middle (berserker north,
     * archer west, seer east, an extra one south), the rest on distinct spawn points at least 4
     * tiles from every player (any free one if all are close).
     */
    private fun spawnWave(run: ColosseumRun, monsters: List<ColosseumMonster>) {
        val sw = ColosseumArena.trioSouthWest
        val ne = ColosseumArena.trioNorthEast
        val base = CoordGrid(world.between(sw.x, ne.x), world.between(sw.z, ne.z), 0)
        val sides = HashMap(ColosseumEnemies.SIDES)
        val players = run.fighters().map { run.real(it.player.coords) }
        val used = HashSet<Int>()
        for (kind in monsters) {
            if (kind.isWarband) {
                val side = sides.remove(kind) ?: ColosseumEnemies.EXTRA_SIDE
                spawnMonster(run, kind, base.translate(side.first, side.second), side)
                continue
            }
            val points = ColosseumArena.spawnPoints.indices.filter { it !in used }
            val far =
                points.filter { index ->
                    players.all {
                        ColosseumWorld.chebyshev(it, ColosseumArena.spawnPoints[index]) >
                            ColosseumArena.SPAWN_EXCLUSION
                    }
                }
            val pool =
                far.ifEmpty { points.ifEmpty { ColosseumArena.spawnPoints.indices.toList() } }
            val index = pool[world.roll(pool.size)]
            used += index
            spawnMonster(run, kind, ColosseumArena.spawnPoints[index], null)
        }
    }

    private fun spawnMonster(
        run: ColosseumRun,
        kind: ColosseumMonster,
        real: CoordGrid,
        side: Pair<Int, Int>?,
    ): Npc {
        val redFlag = run.modifiers.has(ColosseumModifier.RedFlag)
        val npc = world.spawn(run, enemies.typeOf(kind, redFlag), real)
        val state = ColosseumMonsterState(kind, world.now)
        if (side != null) {
            state.side = side
        }
        run.monsters += npc
        run.states[npc] = state
        enemies.spawned(run, npc, state)
        return npc
    }

    private fun waveTick(run: ColosseumRun) {
        if (run.fighters().isEmpty()) {
            teamFell(run)
            return
        }
        val fight = run.sol
        if (fight != null && fight.dead) {
            if (world.now >= fight.deathAt) {
                waveCleared(run)
            }
            return
        }
        run.monsters.removeAll { !it.isSlotAssigned }
        if (run.monsters.isEmpty() && !run.isFinalWave) {
            waveCleared(run)
            return
        }
        enemies.aim(run)
        enemies.tick(run)
        hazards.tick(run)
        sol.tick(run)
        if (
            !run.reinforced &&
                !run.isFinalWave &&
                world.now - run.waveStart >= ColosseumWaves.REINFORCEMENT_TICKS
        ) {
            run.reinforced = true
            reinforce(run)
        }
    }

    /** Reinforcements come through the gate on the half where most players stand. */
    private fun reinforce(run: ColosseumRun) {
        val players = run.fighters().map { run.real(it.player.coords) }
        val north = players.count { it.z >= ColosseumArena.NORTH_HALF_FROM }
        val gateZ =
            if (north * 2 >= players.size && north > 0) ColosseumArena.NORTH_GATE_Z
            else ColosseumArena.SOUTH_GATE_Z
        for (kind in ColosseumWaves.reinforcements(run.wave)) {
            val x = ColosseumArena.GATE_WEST_X + world.roll(ColosseumArena.GATE_WIDTH)
            spawnMonster(run, kind, CoordGrid(x, gateZ, 0), null)
        }
    }

    /** Everyone still fighting is dead: the wave is lost, and so is the loot of the fallen. */
    private fun teamFell(run: ColosseumRun) {
        clearWave(run)
        for (member in run.members.values) {
            if (member.dead) {
                member.rewards.clear()
                member.future = emptyList()
            }
        }
        val claiming = run.members.values.any { it.forfeited }
        run.stage = if (claiming) ColosseumStage.Finished else ColosseumStage.Intermission
    }

    private fun clearWave(run: ColosseumRun) {
        for (npc in run.monsters) {
            world.remove(npc)
        }
        run.monsters.clear()
        run.states.clear()
        hazards.waveEnded(run)
        sol.clear(run)
        run.sol = null
    }

    /** A wave is cleared: each player in it earns its loot and Glory, and Minimus comes back. */
    private fun waveCleared(run: ColosseumRun) {
        val wave = run.wave
        val ticks = world.now - run.waveStart
        run.cleared++
        val final = wave >= ColosseumWaves.FINAL_WAVE
        clearWave(run)
        val next = run.nextWave
        for (member in run.members.values) {
            val player = member.player
            if (member.dead || member.missedWave) {
                member.previous = emptyList()
                member.future = next?.let { rollLoot(player, it) } ?: emptyList()
                continue
            }
            val earned = member.future
            member.rewards.merge(earned)
            member.previous = earned
            member.future = next?.let { rollLoot(player, it) } ?: emptyList()
            member.glory += gloryFor(run, member, wave, ticks)
            VarPlayerIntMapSetter.set(player, colosseum_varps.current_glory, member.glory)
            VarPlayerIntMapSetter.set(player, colosseum_varps.last_wave_duration, ticks)
            VarPlayerIntMapSetter.set(
                player,
                colosseum_varps.waves_completed,
                player.vars[colosseum_varps.waves_completed] + 1,
            )
            effects.waveCompleted(run, member)
            completions.waveCleared(player, wave)
            if (!final) {
                player.mes("Wave $wave completed! Duration: <col=ef1020>${duration(ticks)}</col>")
            }
        }
        if (final) {
            won(run)
            return
        }
        run.stage = ColosseumStage.Intermission
        minimusNear(run)
    }

    /** Sol Heredit is beaten: the quiver and the pet roll, the chest, and Minimus beside it. */
    private fun won(run: ColosseumRun) {
        // "Once he is slain, the ground hazards subside" (wiki).
        hazards.cleared(run)
        for (member in run.fighters()) {
            val player = member.player
            if (pets.roll(ColosseumLoot.PET_ODDS)) {
                givePet(player)
            }
            keepGlory(member)
            player.mes("<col=ef1020>Congratulations! You have defeated Sol Heredit.</col>")
        }
        finish(run)
        val first = run.fighters().firstOrNull()?.player ?: return
        val who = AnnouncementMessages.who(teams.nameOf(first), teams.teamOf(first)?.name)
        announcer.broadcast(
            "${LeagueChat.blue("League:")} $who has defeated Sol Heredit in the Fortis Colosseum" +
                (if (settings.mode == ColosseumMode.Shortened) " (shortened)" else "") +
                "."
        )
    }

    fun givePet(player: Player) {
        pets.award(player, pets.pet("smol_heredit"))
    }

    /** The run is over with its loot: the chest comes out with Minimus beside it. */
    private fun finish(run: ColosseumRun) {
        run.stage = ColosseumStage.Finished
        bringOutChest(run)
        spawnMinimus(run, ColosseumArena.minimusByChest)
    }

    private fun bringOutChest(run: ColosseumRun) {
        if (run.chest != null) {
            return
        }
        val chest =
            world.addLoc(
                run,
                colosseum_locs.reward_chest,
                ColosseumArena.chest,
                ColosseumArena.CHEST_ANGLE,
            )
        world.worldRepo.locAnim(chest, colosseum_seqs.chest_appear)
        run.chest = chest
    }

    private fun gloryFor(run: ColosseumRun, member: ColosseumMember, wave: Int, ticks: Int): Int =
        glory(wave, ticks, member.hitByEnemy, run.modifiers.glory())

    /** Only a player's best run counts: it sets their title, and the Fortis Salute reads it. */
    private fun keepGlory(member: ColosseumMember) {
        val player = member.player
        if (member.glory > player.vars[colosseum_varps.glory]) {
            VarPlayerIntMapSetter.set(player, colosseum_varps.glory, member.glory)
        }
        completions.glory(player, player.vars[colosseum_varps.glory])
    }

    private fun rollLoot(player: Player, wave: Int): List<ColosseumItem> =
        ColosseumLoot.roll(wave, world::roll) { piece ->
            collectionLog.owned(player, piece.id) + pendingPieces(player, piece.id)
        }

    /** Fanatic pieces already waiting in [player]'s chest count towards duplicate protection. */
    private fun pendingPieces(player: Player, obj: Int): Int {
        val member = teamRun(player)?.memberOf(player) ?: return 0
        return (member.rewards + member.future).filter { it.obj.id == obj }.sumOf { it.count }
    }

    private fun MutableList<ColosseumItem>.merge(items: List<ColosseumItem>) {
        for (item in items) {
            val index = indexOfFirst { it.obj.id == item.obj.id }
            if (index >= 0) {
                this[index] = this[index].copy(count = this[index].count + item.count)
            } else {
                this += item
            }
        }
    }

    // Monsters dying ------------------------------------------------------------------------------

    /** A monster of a run died (its death animation is about to play). */
    fun monsterDied(npc: Npc) {
        val run = runOf(npc) ?: return
        val state = run.states.remove(npc) ?: return
        run.monsters.remove(npc)
        eventBus.publish(NpcDeathEvents.Killed(npc, npc.findHero(players)))
        hazards.monsterDied(run, npc, state.kind)
        val fight = run.sol
        if (state.kind == ColosseumMonster.Sol && fight != null && fight.npc === npc) {
            sol.died(run, fight)
            fight.deathAt = world.now + SOL_DEATH_TICKS
            // Whatever else is in the arena goes with him.
            for (other in run.monsters.toList()) {
                world.remove(other)
            }
            run.monsters.clear()
        }
    }

    // Leaving -----------------------------------------------------------------------------------

    /** What Minimus's Leave does for [player] now. */
    enum class LeaveKind {
        /** Before wave 1, or once their chest is out: straight back to the lobby. */
        Leave,

        /** Between waves: they give up the run and get their chest. */
        Forfeit,

        /** A wave is being fought: Minimus cannot help. */
        None,
    }

    fun leaveKind(player: Player): LeaveKind {
        val run = runOf(player) ?: return LeaveKind.None
        val member = run.memberOf(player) ?: return LeaveKind.Leave
        return when {
            member.forfeited || run.stage == ColosseumStage.Finished -> LeaveKind.Leave
            run.stage != ColosseumStage.Intermission -> LeaveKind.None
            run.cleared == 0 || member.rewards.isEmpty() -> LeaveKind.Leave
            else -> LeaveKind.Forfeit
        }
    }

    /** [player] ends their run early: their rewards go into the chest for them to claim. */
    fun forfeit(player: Player) {
        val run = runOf(player) ?: return
        val member = run.memberOf(player) ?: return
        member.forfeited = true
        keepGlory(member)
        bringOutChest(run)
        if (run.fighters().isEmpty()) {
            finish(run)
        }
    }

    /** Through Minimus: back to the lobby with every stat restored (wiki). */
    fun leave(player: Player) {
        val run = runOf(player) ?: return
        val member = run.members.remove(player.accountId)
        if (member != null) {
            keepGlory(member)
            if (member.rewards.isNotEmpty()) {
                player.mes("You leave your unclaimed rewards behind.")
            }
        }
        clearPlayer(player)
        player.statRestoreAll(RESTORED_STATS)
        PathingEntityCommon.telejump(player, world.collision, ColosseumArena.lobbyRespawn)
        sweep(except = player)
    }

    private fun clearPlayer(player: Player) {
        screens.closeIntermission(player)
        for (varp in RUN_VARPS) {
            VarPlayerIntMapSetter.set(player, varp(), 0)
        }
        player.client.resetNpcViewDistance()
    }

    // Players dying and logging out -------------------------------------------------------------

    fun died(player: Player) {
        val run = runOf(player) ?: return
        val member = run.memberOf(player) ?: return
        if (member.forfeited) {
            run.members.remove(player.accountId)
            return
        }
        member.dead = true
        member.doomStacks = 0
        sol.endCutscene(member)
        keepGlory(member)
        sol.playerDied(run)
        respawning += player.accountId
        screens.closeIntermission(player)
    }

    /** Where a player who just died in the arena wakes up, once: the lobby. */
    fun respawnFor(player: Player): CoordGrid? =
        if (respawning.remove(player.accountId)) ColosseumArena.lobbyRespawn else null

    fun loggedOut(player: Player) {
        val run = teamRun(player)
        run?.members?.remove(player.accountId)
        sweep(except = player)
    }

    // Damage -------------------------------------------------------------------------------------

    /** [player] took [damage] (from [source], if a monster dealt it). */
    fun impact(player: Player, damage: Int, source: Npc?) {
        val run = runOf(player) ?: return
        val member = run.memberOf(player) ?: return
        if (member.dead || run.stage != ColosseumStage.Wave) {
            return
        }
        val fromEnemy = source != null && run.states.containsKey(source)
        effects.damageTaken(run, member, damage, fromEnemy)
    }

    fun rankOf(player: Player): String = rankOf(player.vars[colosseum_varps.glory])

    // Developer commands ------------------------------------------------------------------------

    /** Between waves: makes the next wave [wave] (12 is Sol Heredit), with its loot rolled. */
    fun setNextWave(player: Player, wave: Int): Boolean {
        val run = runOf(player) ?: return false
        if (run.stage != ColosseumStage.Intermission) {
            return false
        }
        val index = run.waves.indexOf(wave)
        if (index < 0) {
            return false
        }
        run.cleared = index
        run.offered = emptyList()
        for (member in run.members.values) {
            member.future = rollLoot(member.player, wave)
        }
        return true
    }

    /** Kills every monster of the wave on the floor (they die as if a player killed them). */
    fun killWave(player: Player): Boolean {
        val run = runOf(player) ?: return false
        if (run.stage != ColosseumStage.Wave) {
            return false
        }
        for (npc in run.monsters.toList()) {
            npc.hitpoints = 0
            npc.queueDeath()
        }
        return true
    }

    /** Between waves: sets a modifier's tier for the run. */
    fun setModifier(player: Player, modifier: ColosseumModifier, tier: Int): Boolean {
        val run = runOf(player) ?: return false
        if (run.stage != ColosseumStage.Intermission) {
            return false
        }
        while (run.modifiers.tier(modifier) < tier.coerceAtMost(modifier.maxTier)) {
            run.modifiers.add(modifier)
        }
        run.offered = emptyList()
        for (member in run.members.values) {
            screens.syncTiers(member.player, run.modifiers)
        }
        return true
    }

    companion object {
        const val NPC_VIEW_DISTANCE = 32
        const val GREETING_DELAY = 3
        const val WAVE_START_DELAY = 5
        const val SOL_DEATH_TICKS = 5
        const val SOL_JUMP_DELAY = 3
        const val SOL_LINE_TICKS = 3
        const val SOL_FAILURES_MAX = 3

        const val GLORY_COMPLETION = 100
        const val GLORY_NO_DAMAGE = 100
        const val GLORY_TIME_START = 500

        /** The varps a run shows, reset when a player joins or leaves one. */
        private val RUN_VARPS =
            listOf(
                { colosseum_varps.current_glory },
                { colosseum_varps.wave_start_time },
                { colosseum_varps.wave_damage_taken },
                { colosseum_varps.last_modifier_glory },
            )

        private val RESTORED_STATS by lazy {
            listOf(
                stats.attack,
                stats.strength,
                stats.defence,
                stats.ranged,
                stats.magic,
                stats.prayer,
                stats.hitpoints,
            )
        }

        /** The Glory titles (wiki). */
        val RANKS =
            listOf(
                20_000 to "Grand Champion",
                16_000 to "Champion",
                12_000 to "Hero",
                8_000 to "Gladiator",
                5_000 to "Challenger",
                2_000 to "Brawler",
                0 to "Rookie",
            )

        fun rankOf(glory: Int): String = RANKS.first { glory >= it.first }.second

        /**
         * A wave's Glory (wiki, "Glory"): 100 a wave number for clearing it, as much again if no
         * monster hurt the player, every modifier's Glory, and a time bonus of 500 a wave number
         * less one a wave number for every tick, never below 0 and rounded down to even.
         */
        fun glory(wave: Int, ticks: Int, hitByEnemy: Boolean, modifierGlory: Int): Int {
            val completion = GLORY_COMPLETION * wave
            val noDamage = if (hitByEnemy) 0 else GLORY_NO_DAMAGE * wave
            val time = maxOf(0, (GLORY_TIME_START - ticks) * wave)
            return completion + noDamage + modifierGlory + time - time % 2
        }

        fun duration(ticks: Int): String {
            val centis = ticks * 60
            val minutes = centis / 6000
            val seconds = (centis % 6000) / 100
            val hundredths = centis % 100
            return "%d:%02d.%02d".format(minutes, seconds, hundredths)
        }
    }
}
