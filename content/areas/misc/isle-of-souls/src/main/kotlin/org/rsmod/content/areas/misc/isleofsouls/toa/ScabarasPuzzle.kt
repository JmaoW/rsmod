package org.rsmod.content.areas.misc.isleofsouls.toa

import kotlin.math.abs
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

typealias scabaras_npcs = ScabarasNpcs

typealias scabaras_locs = ScabarasLocs

typealias scabaras_seqs = ScabarasSeqs

object ScabarasNpcs : NpcReferences() {
    val scarab = find("toa_scabaras_scarab")
    val obelisk = find("toa_scabaras_guesser_obelisk")
    val obelisk_lit = find("toa_scabaras_guesser_obelisk_hit")
}

object ScabarasLocs : LocReferences() {
    val fire = find("toa_scabaras_barrier")
    val memory_button = find("toa_scabaras_simonsays_button")
    val sum_tablet = find("toa_scabaras_totaltiles_tablet")
    val memory_plate = find("toa_scabaras_simonsays_tile_up")
    val memory_shown = find("toa_scabaras_simonsays_tile_down")
    val memory_stepped = find("toa_scabaras_simonsays_tile_down_player")
    val passage = find("toa_scabaras_spawn_hole")

    /** The stepping stone between the halves in the east, jumped across. */
    val platform = find("toa_scabaras_stepping_stone")
    val light_plate = find("toa_scabaras_lightsout_tile_off")

    /** The addition plates, by value 1-9. */
    val sum_plates = (1..9).map { find("toa_scabaras_totaltiles_tile$it") }
    val match_tile = find("toa_scabaras_memorygame_button5")
    val light_on = find("toa_scabaras_fx01")

    /**
     * The glow on a plate that is counted, by value 1-9 (line, knives, triangle, diamond, hand,
     * bird, crook, wiggle, foot). From the RuneLite Tombs of Amascut plugin, which reads them in
     * the real game; TSPS had the star's glow (fx04) on the diamond.
     */
    val sum_glows =
        listOf(
            find("toa_scabaras_fx05"),
            find("toa_scabaras_fx06"),
            find("toa_scabaras_fx07"),
            find("toa_scabaras_fx08"),
            find("toa_scabaras_fx09"),
            find("toa_scabaras_fx10"),
            find("toa_scabaras_fx03"),
            find("toa_scabaras_fx11"),
            find("toa_scabaras_fx12"),
        )

    /**
     * What a turned matching tile shows, by key 0-8 (line, knives, crook, diamond, hand, star,
     * bird, W, boot): the matching game's own symbols, not the addition plates (the plugin).
     */
    val match_symbols = (1..9).map { find("toa_scabaras_memorygame_tile$it") }

    /** The glow on a matched pair, by key, in the same order (the plugin). */
    val match_glows =
        listOf(
            find("toa_scabaras_fx05"),
            find("toa_scabaras_fx06"),
            find("toa_scabaras_fx03"),
            find("toa_scabaras_fx08"),
            find("toa_scabaras_fx09"),
            find("toa_scabaras_fx04"),
            find("toa_scabaras_fx10"),
            find("toa_scabaras_fx11"),
            find("toa_scabaras_fx12"),
        )
    val obelisk_block = find("invisible_type8_blocking_active")
}

object ScabarasSeqs : SeqReferences() {
    val flip = find("toa_flip_tile")
    val flip_done = find("human_pickupfloor")
    val button = find("human_pickuptable")
    val scarab_spawn = find("npc_scarab_spawn")
    val scarab_attack = find("npc_scarab_attack")
    val crawl = find("human_longcrawl")
    val jump = find("human_spot_jump")
    val scarab_death = find("npc_scarab_death")
}

object ScabarasSpotanims : SpotanimReferences() {
    val small_rocks = find("mm_roofcollapse")
    val rockfall = find("ga_beast_rock_fall")
    val scarab_spit = find("nightmare_ranged_travel")
}

/**
 * Path of Scabaras, the Test of Isolation: four puzzles in four chambers in a random order (the
 * memory plates, the obelisks, lights out, and the addition), each behind a fire gate that goes out
 * when it is solved, then the tile-matching puzzle that ends the room. Scarabs spit at both halves
 * of the room throughout. An east chamber's puzzle works only once the west one on the other half
 * is solved, so the party splits (alone, the passage between the halves is crawled through).
 *
 * The rules come from the wiki (the 20 damage flip, the 20-45 sum, four pairs pre-solved alone);
 * the layouts, timings and damage of the rockfalls are TSPS's (Near-Reality).
 */
class ScabarasPuzzleRuntime(env: ToaRoomEnv, raid: ToaRaid) :
    ToaRoomRuntime(env, raid, ToaRoom.ScabarasPuzzle) {
    enum class Puzzle {
        Light,
        Sum,
        Pillar,
        Memory,
    }

    /** Which puzzle is in which chamber (north-west, north-east, south-west, south-east). */
    var rotation: List<Puzzle> = Puzzle.entries.toList()
        private set

    private val solved = BooleanArray(4)
    private val lights = BooleanArray(LIGHT_OFFSETS.size)
    private val scarabs = LinkedHashMap<Npc, Boolean>()
    private val obelisks = ArrayList<Npc>()
    private var obeliskOrder: List<Int> = emptyList()
    private var obeliskIndex = 0
    private var obeliskTimeout = 0
    private var rockfallAt = 0
    var sumGoal = 0
        private set

    private var sum = 0
    private var sequence: List<Int>? = null
    private var sequenceStep = 0
    private var sequenceShown = -1
    private var matchNorth: List<Int> = emptyList()
    private var matchSouth: List<Int> = emptyList()
    private var northPick = -1
    private var southPick = -1
    var matches = 0
        private set

    private var scarabTicks = 0
    private val lastTile = HashMap<Int, CoordGrid>()

    fun isSolved(puzzle: Puzzle): Boolean = solved[rotation.indexOf(puzzle)]

    fun baseOf(puzzle: Puzzle): CoordGrid = BASES[rotation.indexOf(puzzle)]

    val scarabCount: Int
        get() = scarabs.size

    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun build() {
        rotation = shuffled(Puzzle.entries.toList())
        matchNorth = shuffled((0 until 9).toList())
        matchSouth = shuffled((0 until 9).toList())
        val light = baseOf(Puzzle.Light)
        for ((dx, dz) in LIGHT_OFFSETS) {
            placeLoc(
                light.translate(dx, dz),
                scabaras_locs.light_plate,
                LocShape.GroundDecor,
                LocAngle[0],
            )
        }
        val sumBase = baseOf(Puzzle.Sum)
        for ((value, tiles) in SUM_OFFSETS.withIndex()) {
            for ((dx, dz) in tiles) {
                placeLoc(
                    sumBase.translate(dx, dz),
                    scabaras_locs.sum_plates[value],
                    LocShape.GroundDecor,
                    LocAngle[1],
                )
            }
        }
        placeLoc(
            sumBase.translate(BUTTON.first, BUTTON.second),
            scabaras_locs.sum_tablet,
            LocShape.CentrepieceStraight,
            LocAngle[0],
        )
        val pillar = baseOf(Puzzle.Pillar)
        obelisks.clear()
        for ((index, offset) in OBELISK_OFFSETS.withIndex()) {
            val tile = pillar.translate(offset.first, offset.second)
            val type = if (index < 5) scabaras_npcs.obelisk else scabaras_npcs.obelisk_lit
            obelisks += spawn(type, tile, scale = false, points = 0.0)
            placeLoc(tile, scabaras_locs.obelisk_block, LocShape.CentrepieceStraight, LocAngle[0])
        }
        val memory = baseOf(Puzzle.Memory)
        placeLoc(
            memory.translate(BUTTON.first, BUTTON.second),
            scabaras_locs.memory_button,
            LocShape.CentrepieceStraight,
            LocAngle[0],
        )
        for ((dx, dz) in MEMORY_OFFSETS) {
            placeLoc(
                memory.translate(dx, dz),
                scabaras_locs.memory_plate,
                LocShape.GroundDecor,
                LocAngle[0],
            )
        }
        resetPuzzles()
    }

    private fun resetPuzzles() {
        solved.fill(false)
        for (tile in GATES) {
            placeLoc(tile, scabaras_locs.fire, LocShape.CentrepieceStraight, LocAngle[3])
        }
        for (i in 0 until EXIT_FIRE_LENGTH) {
            placeLoc(
                EXIT_FIRE.translate(0, i),
                scabaras_locs.fire,
                LocShape.CentrepieceStraight,
                LocAngle[3],
            )
        }
        resetObelisks()
        obeliskOrder = shuffled((0 until 5).toList())
        val light = baseOf(Puzzle.Light)
        for (index in lights.indices) {
            if (lights[index]) {
                toggleLight(light, index)
            }
        }
        rockfallAt = 0
        sumGoal = env.random.of(SUM_MIN, SUM_MAX)
        sum = 0
        setSumGlows(false)
        scarabTicks = SCARAB_FIRST
        for ((dx, dz) in MEMORY_OFFSETS) {
            setPlate(baseOf(Puzzle.Memory).translate(dx, dz), null)
        }
        for ((side, base) in MATCH_BASES.withIndex()) {
            for ((index, offset) in MATCH_OFFSETS.withIndex()) {
                val tile = base.translate(offset.first, offset.second)
                placeLoc(tile, scabaras_locs.match_tile, LocShape.GroundDecor, LocAngle[1])
                removeLoc(tile)
            }
        }
        northPick = -1
        southPick = -1
        matches = 0
        resetSequence()
    }

    override fun onStart() {
        // Alone, four of the nine pairs are shown from the start (wiki).
        if (teamSize < 2) {
            for (key in shuffled((0 until 9).toList()).take(SOLO_PAIRS)) {
                pickMatch(north = true, matchNorth.indexOf(key), null, initial = true)
                pickMatch(north = false, matchSouth.indexOf(key), null, initial = true)
            }
        }
        val light = baseOf(Puzzle.Light)
        for (index in lights.indices) {
            if (env.random.of(2) == 0) {
                toggleLight(light, index)
            }
        }
    }

    override fun onComplete() {
        killScarabs()
        for (i in 0 until EXIT_FIRE_LENGTH) {
            removeLoc(EXIT_FIRE.translate(0, i))
        }
        for (tile in GATES) {
            removeLoc(tile)
        }
        solved.fill(true)
        for (member in raid.active) {
            if (member.room == room) {
                raid.points.award(member.player.accountId, COMPLETION_POINTS)
            }
        }
    }

    override fun onReset() {
        killScarabs()
        resetPuzzles()
    }

    private fun killScarabs() {
        for (npc in scarabs.keys.toList()) {
            despawn(npc)
        }
        scarabs.clear()
    }

    private fun gateOf(index: Int): CoordGrid = GATES[index]

    /** A chamber's puzzle is solved: its gate goes out. */
    private fun solve(puzzle: Puzzle) {
        val index = rotation.indexOf(puzzle)
        if (solved[index]) {
            return
        }
        solved[index] = true
        removeLoc(gateOf(index))
        for (player in challengePlayers()) {
            player.mes("<col=06600c>Puzzle ${index + 1} has been completed!</col>")
        }
        sound(toa_synths.puzzle_done, chamberPlayers(index))
    }

    /** The east puzzles work only once the west puzzle on the other half is solved. */
    private fun canUse(player: Player, puzzle: Puzzle, refusal: String? = null): Boolean {
        if (!isStarted) {
            return false
        }
        val index = rotation.indexOf(puzzle)
        if ((index == 1 && !solved[2]) || (index == 3 && !solved[0])) {
            player.mes(refusal ?: "This device won't function yet.")
            return false
        }
        return true
    }

    // The tick
    // ----------------------------------------------------------------------------------------

    override fun onTick() {
        if (--scarabTicks <= 0) {
            scarabTicks = SCARAB_INTERVAL
            val wanted = (teamSize + 1) / 2
            val south = scarabs.values.count { it }
            val north = scarabs.size - south
            if (north < wanted) {
                spawnScarab(south = false)
            }
            if (south < wanted) {
                spawnScarab(south = true)
            }
        }
        if (
            !isSolved(Puzzle.Pillar) &&
                obeliskIndex in 1..4 &&
                obeliskTimeout > 0 &&
                --obeliskTimeout == 0
        ) {
            resetObelisks()
        }
        val shown = sequenceShown
        val seq = sequence
        if (!isSolved(Puzzle.Memory) && shown >= 0 && seq != null) {
            val tile =
                baseOf(Puzzle.Memory)
                    .translate(MEMORY_OFFSETS[seq[shown]].first, MEMORY_OFFSETS[seq[shown]].second)
            setPlate(tile, scabaras_locs.memory_shown)
            sound(toa_synths.scabaras_pressure, chamberPlayers(rotation.indexOf(Puzzle.Memory)))
            later(1) {
                if (!isSolved(Puzzle.Memory)) {
                    setPlate(tile, null)
                }
            }
            sequenceShown = if (shown + 1 >= SEQUENCE_LENGTH) -1 else shown + 1
        }
        for (npc in scarabs.keys.toList()) {
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                scarabs.remove(npc)
            }
        }
        tickSteps()
        driveMonsters()
    }

    /**
     * What each player stepped on since last tick: the tile they stand on, and the one they ran
     * over in between when that is clear (a run of two tiles in a line).
     */
    private fun tickSteps() {
        for (player in challengePlayers()) {
            val at = raid.realOf(player.coords) ?: continue
            val before = lastTile.put(player.accountId, at)
            if (before == null || before == at) {
                continue
            }
            val dx = at.x - before.x
            val dz = at.z - before.z
            if (abs(dx) == 2 && (dz == 0 || abs(dz) == 2) || abs(dz) == 2 && dx == 0) {
                stepOn(player, before.translate(dx / 2, dz / 2))
            }
            stepOn(player, at)
        }
    }

    fun stepOn(player: Player, tile: CoordGrid) {
        if (stepSum(player, tile)) {
            return
        }
        if (stepLight(player, tile, flipOnly = false)) {
            return
        }
        stepMemory(player, tile)
    }

    // Scarabs
    // --------------------------------------------------------------------------------------------

    private fun spawnScarab(south: Boolean) {
        val npc =
            spawn(
                scabaras_npcs.scarab,
                if (south) SCARAB_SOUTH else SCARAB_NORTH,
                scale = false,
                points = ToaPoints.Multiplier.KEPHRI_SCARAB,
            )
        npc.anim(scabaras_seqs.scarab_spawn)
        scarabs[npc] = south
        val attack =
            NpcAttack(
                NpcAttackStyle.Ranged,
                anim = scabaras_seqs.scarab_attack,
                projectile = ScabarasSpotanims.scarab_spit,
                projanim = projanims.arrow,
                maxHit = SCARAB_MAX_HIT,
            )
        monster(npc, attack, range = SCARAB_RANGE, speed = 4, delay = 3)
    }

    /** The scarabs' spit is not scaled with the raid (TSPS). */
    override fun swing(monster: ToaMonster, target: Player) {
        env.attacks.attack(monster.npc, target, monster.attack)
    }

    /** The nearest player it can see, so a scarab never spits through the chamber walls. */
    override fun pickTarget(monster: ToaMonster, players: List<Player>): Player? =
        players
            .filter { canSee(monster.npc, it.coords) }
            .minByOrNull { gap(monster.npc, it.coords) }

    // Lights out
    // ------------------------------------------------------------------------------------------

    private fun toggleLight(base: CoordGrid, index: Int) {
        lights[index] = !lights[index]
        val tile = base.translate(LIGHT_OFFSETS[index].first, LIGHT_OFFSETS[index].second)
        if (lights[index]) {
            placeLoc(tile, scabaras_locs.light_on, LocShape.CentrepieceStraight, LocAngle[1])
        } else {
            removeLoc(tile)
        }
    }

    val litCount: Int
        get() = lights.count { it }

    fun isLit(index: Int): Boolean = lights[index]

    /** The obelisks' order, for the tests. */
    val obeliskAnswer: List<Int>
        get() = obeliskOrder

    /** Stepping on a plate flips it and its two neighbours; flipping by hand flips only it. */
    fun stepLight(player: Player, tile: CoordGrid, flipOnly: Boolean): Boolean {
        if (isSolved(Puzzle.Light)) {
            return false
        }
        val base = baseOf(Puzzle.Light)
        val index = LIGHT_OFFSETS.indexOfFirst { base.translate(it.first, it.second) == tile }
        if (index < 0) {
            return false
        }
        if (!canUse(player, Puzzle.Light)) {
            return true
        }
        player.soundSynth(toa_synths.scabaras_pressure)
        toggleLight(base, index)
        if (!flipOnly) {
            toggleLight(base, (index + lights.size - 1) % lights.size)
            toggleLight(base, (index + 1) % lights.size)
        }
        if (lights.all { it }) {
            solve(Puzzle.Light)
        }
        return true
    }

    /** "Flip": four ticks of work, then that one plate flips, for 20 damage (wiki). */
    fun flip(player: Player, tile: CoordGrid) {
        if (!isStarted || isSolved(Puzzle.Light)) {
            return
        }
        player.mes("You attempt to get a grip of the tile in order to flip it.")
        player.anim(scabaras_seqs.flip)
        player.soundSynth(toa_synths.scabaras_flip)
        later(FLIP_TICKS) {
            if (player !in challengePlayers()) {
                return@later
            }
            if (stepLight(player, tile, flipOnly = true)) {
                player.mes("You manage to flip the tile over.")
                hurtFlat(player, FLIP_DAMAGE)
                player.anim(scabaras_seqs.flip_done)
            } else {
                player.mes("You fail to flip the tile over.")
            }
        }
    }

    // Addition
    // ---------------------------------------------------------------------------------------

    private fun setSumGlows(on: Boolean) {
        val base = baseOf(Puzzle.Sum)
        for ((value, tiles) in SUM_OFFSETS.withIndex()) {
            for ((dx, dz) in tiles) {
                val tile = base.translate(dx, dz)
                if (on) {
                    placeLoc(
                        tile,
                        scabaras_locs.sum_glows[value],
                        LocShape.CentrepieceStraight,
                        LocAngle[1],
                    )
                } else {
                    removeLoc(tile)
                }
            }
        }
    }

    fun stepSum(player: Player, tile: CoordGrid): Boolean {
        if (isSolved(Puzzle.Sum)) {
            return false
        }
        val base = baseOf(Puzzle.Sum)
        for ((value, tiles) in SUM_OFFSETS.withIndex()) {
            for ((dx, dz) in tiles) {
                if (base.translate(dx, dz) != tile) {
                    continue
                }
                if (!canUse(player, Puzzle.Sum)) {
                    return true
                }
                sum += value + 1
                if (sum <= sumGoal) {
                    player.soundSynth(toa_synths.scabaras_pressure)
                }
                when {
                    sum == sumGoal -> {
                        solve(Puzzle.Sum)
                        setSumGlows(true)
                    }
                    sum > sumGoal -> {
                        setSumGlows(false)
                        smallRockfall(player)
                        sum = 0
                    }
                    else ->
                        placeLoc(
                            tile,
                            scabaras_locs.sum_glows[value],
                            LocShape.CentrepieceStraight,
                            LocAngle[1],
                        )
                }
                return true
            }
        }
        return false
    }

    fun readTablet(player: Player) {
        if (!canUse(player, Puzzle.Sum, "You can't seem to make out the writing. Weird.")) {
            return
        }
        player.mes("The number <col=ef1020>$sumGoal</col> has been hastily chipped into the stone.")
        player.soundSynth(toa_synths.scabaras_tablet)
    }

    // Obelisks
    // ------------------------------------------------------------------------------------------

    private fun resetObelisks() {
        obeliskIndex = 0
        obeliskTimeout = 0
        for ((index, npc) in obelisks.withIndex()) {
            if (index < 5) {
                npc.resetTransmog()
            }
        }
    }

    /** A hit on an obelisk does no damage: it lights the right one, or brings the roof down. */
    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        val index = obelisks.indexOf(npc)
        if (index < 0) {
            return
        }
        hit.damage = 0
        if (source == null || index >= 5) {
            return
        }
        hitObelisk(source, index)
    }

    fun hitObelisk(player: Player, index: Int) {
        if (isSolved(Puzzle.Pillar) || !canUse(player, Puzzle.Pillar)) {
            return
        }
        player.soundSynth(toa_synths.scabaras_obelisk_hit)
        if (index == obeliskOrder[obeliskIndex]) {
            obeliskIndex++
            obeliskTimeout = OBELISK_TIMEOUT
            obelisks[index].transmog(env.npcTypes[scabaras_npcs.obelisk_lit], Int.MAX_VALUE)
            if (obeliskIndex >= 5) {
                solve(Puzzle.Pillar)
            }
            return
        }
        resetObelisks()
        if (rockfallAt >= now) {
            return
        }
        rockfallAt = now + 5
        pillarRockfall()
    }

    /** Seven tiles of the obelisk chamber and everyone in it are marked; the rocks land 5 later. */
    private fun pillarRockfall() {
        val index = rotation.indexOf(Puzzle.Pillar)
        val base = BASES[index]
        val gate = gateOf(index)
        val candidates = ArrayList<CoordGrid>()
        for (x in base.x - 6 until gate.x) {
            for (z in base.z..base.z + 6) {
                val tile = CoordGrid(x, z, Z)
                if (walkable(tile)) {
                    candidates += tile
                }
            }
        }
        val tiles = shuffled(candidates).take(7).toMutableList()
        sound(toa_synths.scabaras_rumbling, chamberPlayers(index))
        sound(toa_synths.scabaras_rockfall, chamberPlayers(index))
        for (player in chamberPlayers(index)) {
            raid.realOf(player.coords)?.let { if (it !in tiles) tiles += it }
        }
        for (tile in tiles) {
            graphic(ScabarasSpotanims.rockfall, tile)
        }
        later(5) {
            for (player in challengePlayers()) {
                if (raid.realOf(player.coords) in tiles) {
                    val low = maxHit(7)
                    hurtFlat(player, env.random.of(low, low + 6))
                }
            }
        }
    }

    private fun chamberPlayers(index: Int): List<Player> {
        val base = BASES[index]
        val gate = gateOf(index)
        return challengePlayers().filter {
            val at = raid.realOf(it.coords) ?: return@filter false
            at.x >= base.x - 4 && at.x < gate.x && at.z >= base.z && at.z <= base.z + 6
        }
    }

    // Memory
    // ------------------------------------------------------------------------------------------

    fun pressButton(player: Player) {
        if (!canUse(player, Puzzle.Memory)) {
            return
        }
        if (isSolved(Puzzle.Memory)) {
            player.mes("You have already completed this puzzle.")
            return
        }
        if (sequenceShown != -1) {
            player.mes(
                "You must wait until the sequence has finished before you can request another."
            )
            return
        }
        resetSequence()
        player.anim(scabaras_seqs.button)
        player.soundSynth(toa_synths.scabaras_button)
        sequence = shuffled(MEMORY_OFFSETS.indices.toList()).take(SEQUENCE_LENGTH)
        sequenceShown = 0
    }

    private fun resetSequence() {
        sequence = null
        sequenceStep = 0
        sequenceShown = -1
    }

    fun memorySequence(): List<Int>? = sequence

    fun stepMemory(player: Player, tile: CoordGrid): Boolean {
        val seq = sequence ?: return false
        if (isSolved(Puzzle.Memory) || sequenceShown != -1) {
            return false
        }
        val base = baseOf(Puzzle.Memory)
        val index = MEMORY_OFFSETS.indexOfFirst { base.translate(it.first, it.second) == tile }
        if (index < 0 || !canUse(player, Puzzle.Memory)) {
            return false
        }
        setPlate(tile, scabaras_locs.memory_stepped)
        later(1) {
            if (!isSolved(Puzzle.Memory)) {
                setPlate(tile, null)
            }
        }
        if (seq[sequenceStep] == index) {
            if (++sequenceStep == SEQUENCE_LENGTH) {
                solve(Puzzle.Memory)
                for ((dx, dz) in MEMORY_OFFSETS) {
                    setPlate(base.translate(dx, dz), scabaras_locs.memory_shown)
                }
            }
        } else {
            player.soundSynth(toa_synths.scabaras_memory_fail)
            smallRockfall(player)
            resetSequence()
        }
        return true
    }

    /**
     * A memory plate lit (the sequence) or stepped on: the glow is laid over the floor plate, which
     * stays (in the cache both glows are scenery models with their own animation, like the lights'
     * glow, and the RuneLite plugin reads them that way). `null` puts the plate back to plain.
     */
    private fun setPlate(tile: CoordGrid, glow: org.rsmod.game.type.loc.LocType?) {
        if (glow == null) {
            removeLoc(tile, LocShape.CentrepieceStraight)
        } else {
            placeLoc(tile, glow, LocShape.CentrepieceStraight, LocAngle[0])
        }
    }

    /** Rocks fall on the tile [player] stands on (a map graphic, as the plugin expects). */
    private fun smallRockfall(player: Player) {
        hurtFlat(player, maxHit(6))
        player.soundSynth(toa_synths.scabaras_sum_fail)
        val at = raid.realOf(player.coords)
        if (at != null) {
            graphic(ScabarasSpotanims.small_rocks, at)
        } else {
            player.spotanim(ScabarasSpotanims.small_rocks)
        }
    }

    // Matching
    // ------------------------------------------------------------------------------------------

    /** "Activate" on a matching tile. */
    fun turnTile(player: Player, tile: CoordGrid) {
        for ((side, base) in MATCH_BASES.withIndex()) {
            val index = MATCH_OFFSETS.indexOfFirst { base.translate(it.first, it.second) == tile }
            if (index >= 0) {
                pickMatch(north = side == 1, index, player, initial = false)
                return
            }
        }
    }

    /** Turns a tile on one board; once both boards have one up, they stay lit if they match. */
    private fun pickMatch(north: Boolean, index: Int, player: Player?, initial: Boolean) {
        if (!isStarted && !initial) {
            return
        }
        val order = if (north) matchNorth else matchSouth
        val key = order[index]
        val base = MATCH_BASES[if (north) 1 else 0]
        placeLoc(
            tileOf(base, index),
            scabaras_locs.match_symbols[key],
            LocShape.GroundDecor,
            LocAngle[1],
        )
        val previous = if (north) northPick else southPick
        if (previous != -1 && previous != key) {
            placeLoc(
                tileOf(base, order.indexOf(previous)),
                scabaras_locs.match_tile,
                LocShape.GroundDecor,
                LocAngle[1],
            )
        }
        if (north) northPick = key else southPick = key
        player?.soundSynth(toa_synths.scabaras_tile)
        if (northPick == -1 || southPick == -1) {
            return
        }
        val northKey = northPick
        val southKey = southPick
        val resolve = {
            val northTile = tileOf(MATCH_BASES[1], matchNorth.indexOf(northKey))
            val southTile = tileOf(MATCH_BASES[0], matchSouth.indexOf(southKey))
            if (northKey == southKey) {
                player?.soundSynth(toa_synths.scabaras_pressure)
                placeLoc(
                    northTile,
                    scabaras_locs.match_glows[northKey],
                    LocShape.CentrepieceStraight,
                    LocAngle[1],
                )
                placeLoc(
                    southTile,
                    scabaras_locs.match_glows[southKey],
                    LocShape.CentrepieceStraight,
                    LocAngle[1],
                )
                if (++matches >= MATCH_OFFSETS.size) {
                    for (member in challengePlayers()) {
                        member.mes("<col=06600c>Puzzle 5 has been completed!</col>")
                    }
                    complete()
                }
            } else {
                player?.soundSynth(toa_synths.scabaras_match_fail)
                placeLoc(northTile, scabaras_locs.match_tile, LocShape.GroundDecor, LocAngle[1])
                placeLoc(southTile, scabaras_locs.match_tile, LocShape.GroundDecor, LocAngle[1])
            }
            northPick = -1
            southPick = -1
        }
        if (initial) resolve() else later(1, resolve)
    }

    private fun tileOf(base: CoordGrid, index: Int): CoordGrid =
        base.translate(MATCH_OFFSETS[index].first, MATCH_OFFSETS[index].second)

    /** The matching keys, for the tests. */
    fun matchKeys(north: Boolean): List<Int> = if (north) matchNorth else matchSouth

    // The passage between the halves
    // ------------------------------------------------------------------------------------------

    fun crawl(player: Player) {
        val at = raid.realOf(player.coords) ?: return
        val to = if (at.z < MIDDLE_Z) PASSAGE_NORTH else PASSAGE_SOUTH
        player.abortRoute()
        player.anim(scabaras_seqs.crawl)
        later(1) {
            if (player in roomPlayers()) {
                PathingEntityCommon.telejump(player, env.collision, raid.regionOf(to))
            }
        }
    }

    /**
     * One jump of the platform crossing: onto the stone, or from it to the far half, landing on the
     * real tile [to] facing [facing] (a client angle).
     */
    fun jump(player: Player, to: CoordGrid, facing: Int) {
        player.abortRoute()
        player.anim(scabaras_seqs.jump)
        knockback(player, to, facing = facing, cycles = JUMP_CYCLES)
    }

    companion object {
        const val Z = 0

        /** The stepping stone, and where a jump across it lands on each half. */
        val PLATFORM = CoordGrid(3560, 5280, Z)
        val PLATFORM_NORTH = CoordGrid(3560, 5283, Z)
        val PLATFORM_SOUTH = CoordGrid(3560, 5277, Z)
        const val JUMP_CYCLES = 35

        /** Each chamber's south-west corner: north-west, north-east, south-west, south-east. */
        val BASES =
            listOf(
                CoordGrid(3539, 5283, Z),
                CoordGrid(3556, 5283, Z),
                CoordGrid(3539, 5271, Z),
                CoordGrid(3556, 5271, Z),
            )

        /** Each chamber's fire gate, in the same order. */
        val GATES =
            listOf(
                CoordGrid(3549, 5286, Z),
                CoordGrid(3564, 5286, Z),
                CoordGrid(3549, 5274, Z),
                CoordGrid(3564, 5274, Z),
            )

        val SCARAB_SOUTH = CoordGrid(3535, 5274, Z)
        val SCARAB_NORTH = CoordGrid(3535, 5286, Z)
        val MATCH_BASES = listOf(CoordGrid(3568, 5271, Z), CoordGrid(3568, 5283, Z))
        val EXIT_FIRE = CoordGrid(3575, 5272, Z)
        const val EXIT_FIRE_LENGTH = 17
        const val MIDDLE_Z = 5280
        val PASSAGE_NORTH = CoordGrid(3548, 5284, Z)
        val PASSAGE_SOUTH = CoordGrid(3548, 5276, Z)

        val MEMORY_OFFSETS =
            listOf(2 to 4, 1 to 3, 2 to 2, 3 to 5, 3 to 3, 3 to 1, 4 to 4, 4 to 2, 5 to 3)
        val SUM_OFFSETS =
            listOf(
                listOf(1 to 1, 4 to 4, 5 to 4),
                listOf(2 to 2, 4 to 2, 4 to 5),
                listOf(2 to 5, 3 to 3, 5 to 1),
                listOf(4 to 1, 5 to 2, 3 to 4),
                listOf(3 to 1, 5 to 3),
                listOf(1 to 2, 2 to 3, 2 to 4),
                listOf(1 to 4, 3 to 2, 3 to 5),
                listOf(1 to 3, 4 to 3),
                listOf(1 to 5, 2 to 1, 5 to 5),
            )
        val OBELISK_OFFSETS = listOf(1 to 6, 3 to 6, 5 to 6, 1 to 0, 3 to 0, 5 to 0)
        val LIGHT_OFFSETS = listOf(1 to 1, 1 to 3, 1 to 5, 3 to 5, 5 to 5, 5 to 3, 5 to 1, 3 to 1)
        val MATCH_OFFSETS =
            listOf(1 to 1, 3 to 1, 5 to 1, 1 to 3, 3 to 3, 5 to 3, 1 to 5, 3 to 5, 5 to 5)
        val BUTTON = -1 to 5

        const val SCARAB_MAX_HIT = 6
        const val SCARAB_RANGE = 8
        const val SCARAB_FIRST = 30
        const val SCARAB_INTERVAL = 10
        const val FLIP_DAMAGE = 20
        const val FLIP_TICKS = 4
        const val OBELISK_TIMEOUT = 5
        const val SUM_MIN = 20
        const val SUM_MAX = 45
        const val SEQUENCE_LENGTH = 5
        const val SOLO_PAIRS = 4
        const val COMPLETION_POINTS = 300
    }
}
