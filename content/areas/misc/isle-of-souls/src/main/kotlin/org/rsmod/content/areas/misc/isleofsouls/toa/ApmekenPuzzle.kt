package org.rsmod.content.areas.misc.isleofsouls.toa

import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.config.refs.varps
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid

typealias apmeken_npcs = ApmekenNpcs

typealias apmeken_locs = ApmekenLocs

typealias apmeken_objs = ApmekenObjs

typealias apmeken_seqs = ApmekenSeqs

typealias apmeken_spotanims = ApmekenSpotanims

object ApmekenNpcs : NpcReferences() {
    /** Small (waves 1-4) and large (waves 5-8) baboons of each style, by the wiki's hitpoints. */
    val brawler = find("toa_path_apmeken_baboon_melee_1")
    val thrower = find("toa_path_apmeken_baboon_ranged_1")
    val mage = find("toa_path_apmeken_baboon_magic_1")
    val brawler_large = find("toa_path_apmeken_baboon_melee_2")
    val thrower_large = find("toa_path_apmeken_baboon_ranged_2")
    val mage_large = find("toa_path_apmeken_baboon_magic_2")
    val shaman = find("toa_path_apmeken_baboon_shaman")
    val volatile = find("toa_path_apmeken_baboon_zombie")
    val cursed = find("toa_path_apmeken_baboon_cursed")
    val thrall = find("toa_path_apmeken_baboon_thrall")
}

object ApmekenLocs : LocReferences() {
    val pillar = find("toa_path_apmeken_pillar")
    val pillar_shaking = find("toa_path_apmeken_pillar_shaking")
    val venom_pool = find("toa_path_apmeken_venom_pool")
    val hammers = find("toa_path_apmeken_hammers")
    val potions = find("toa_path_apmeken_potions")
}

object ApmekenObjs : ObjReferences() {
    val potion = find("toa_path_apmeken_potion")
    val hammer = find("hammer")
    val dragon_warhammer = find("dragon_warhammer")
}

object ApmekenSeqs : SeqReferences() {
    val melee = find("npc_mandrill_attack_melee")
    val ranged = find("npc_mandrill_attack_ranged_02")
    val magic = find("npc_mandrill_attack_magic")
    val summon = find("npc_mandrill_attack_magic_02")
    val explode = find("npc_mandrill_explode")
    val spawn = find("npc_mandrill_spawn01")
    val death = find("npc_mandrill_despawn01")
    val repair = find("human_poh_build")
    val pour = find("farming_ingredient_sprinkle")
    val take = find("human_pickupfloor")
}

object ApmekenSpotanims : SpotanimReferences() {
    val gain_sight = find("toa_path_apmeken_gain_sight")
    val lose_sight = find("toa_path_apmeken_lose_sight")
    val warning_north = find("toa_path_apmeken_sight_warning")
    val warning_south = find("toa_path_apmeken_sight_warning02")
    val cursed = find("toa_path_apmeken_gain_cursed")
    val gas = find("toa_path_apmeken_gas")
    val rubble = find("toa_path_apmeken_rubble")
    val ranged = find("toa_baboon_ranged_travel_1")
    val magic = find("toa_baboon_magic_travel_1")
    val explode = find("spotanim_mandrill_explode")
    val explode_fire = find("fireblast_impact")
    val thrall = find("nex_summon")
}

/**
 * Path of Apmeken, the Test of Companionship: eight waves of baboons, while one player at a time
 * holds Apmeken's Sight. The sighted player alone sees which vents are about to erupt, which roof
 * supports are failing, or (in a group) who has been corrupted; the party fixes them with the
 * neutralising potions and hammers from the crates, or everyone takes the damage.
 *
 * From the wiki: the wave list (8 waves since September 2022, one more combat baboon in a group),
 * the baboons' hitpoints (the same at every raid level), their max hits and weaknesses (the weak
 * style always lands a maximum hit), how many fixes a party needs (one per player, up to four),
 * venom pools (6-8 a tick plus venom, 20 seconds), volatile explosions killing other baboons. From
 * TSPS (Near-Reality): the cycle timings, the 22 punishment damage, the wave hand-over counts, the
 * spawn and vent tiles (checked against the map). Guesses are marked where they are made.
 */
class ApmekenPuzzleRuntime(env: ToaRoomEnv, raid: ToaRaid) :
    ToaRoomRuntime(env, raid, ToaRoom.ApmekenPuzzle) {
    private enum class Kind {
        Melee,
        Ranged,
        Magic,
        Shaman,
        Volatile,
        Cursed,
        Thrall,
    }

    enum class Hazard {
        Vents,
        Roof,
        Corruption,
    }

    private class Baboon(val kind: Kind, val critical: Boolean) {
        var thralls = 0
        var explodeTicks = 0
        var exploded = false
    }

    private val baboons = LinkedHashMap<Npc, Baboon>()

    var wave = 0
        private set

    private var waveDelay = 0
    private var critical = 0

    /** Who holds Apmeken's Sight. */
    var sight: Player? = null
        private set

    private val arrivals = ArrayList<Player>()
    private var cycleTicks = 0
    private var cycles = 0
    private var hazardInterval = FIRST_INTERVAL
    private var switchSight = false

    /** The hazard sensed and waiting to be fixed, if any. */
    var hazard: Hazard? = null
        private set

    private var required = 0
    private val fixed = BooleanArray(4)
    private val corrupted = HashSet<Int>()
    private val pools = LinkedHashMap<CoordGrid, Int>()
    private var roofResetAt = 0

    /** How many hammers each player took from the crates, to take back when the room is won. */
    private val hammersTaken = HashMap<Int, Int>()

    /** The tests make every wave's specials the first special, and pick hazards themselves. */
    var pickSpecial: (Int) -> Int = { env.random.of(it) }
    var nextHazard: (() -> Hazard)? = null

    val baboonCount: Int
        get() = baboons.size

    fun baboonNpcs(): List<Npc> = baboons.keys.toList()

    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun onStart() {
        wave = 0
        waveDelay = FIRST_WAVE_DELAY
        cycleTicks = 2
        cycles = 0
        hazardInterval = FIRST_INTERVAL
        hazard = null
        switchSight = false
    }

    override fun onTick() {
        val players = challengePlayers()
        for (player in players) {
            if (player !in arrivals) {
                arrivals += player
            }
        }
        if (waveDelay > 0 && --waveDelay == 0) {
            spawnWave()
            if (isCompleted) {
                return
            }
        }
        if (cycleTicks > 0 && --cycleTicks == 0) {
            cycle(players)
        }
        tickPools(players)
        if (roofResetAt != 0 && now >= roofResetAt) {
            roofResetAt = 0
            for (tile in ROOFS) {
                swapLoc(tile, listOf(apmeken_locs.pillar_shaking), apmeken_locs.pillar)
            }
        }
        tickBaboons(players)
        driveMonsters()
    }

    override fun onComplete() {
        // Completion points (OpenRune's number; the wiki gives none).
        for (member in raid.active) {
            if (member.room == room) {
                raid.points.award(member.player.accountId, COMPLETION_POINTS)
            }
        }
        clearBaboons()
        clearPools()
        restoreLocs()
        setSight(null)
        corrupted.clear()
        for (player in roomPlayers()) {
            val potions =
                player.inv
                    .filterNotNull()
                    .filter { it.id == apmeken_objs.potion.id }
                    .sumOf { it.count }
            if (potions > 0) {
                player.invDel(player.inv, apmeken_objs.potion, potions, strict = false)
            }
            val hammers = hammersTaken.remove(player.accountId) ?: 0
            if (hammers > 0) {
                player.invDel(player.inv, apmeken_objs.hammer, hammers, strict = false)
            }
        }
    }

    override fun onReset() {
        clearBaboons()
        clearPools()
        restoreLocs()
        setSight(null)
        corrupted.clear()
        arrivals.clear()
        hazard = null
        roofResetAt = 0
    }

    override fun onDestroy() {
        setSight(null)
    }

    private fun clearBaboons() {
        for (npc in baboons.keys.toList()) {
            despawn(npc)
        }
        baboons.clear()
        critical = 0
    }

    // Waves
    // ------------------------------------------------------------------------------------------

    /** One wave: its baboons on shuffled spawn tiles, an extra combat baboon in a group. */
    fun spawnWave() {
        if (wave >= WAVES.size) {
            complete()
            return
        }
        val large = wave >= LARGE_FROM_WAVE
        val kinds = ArrayList(WAVES[wave])
        if (teamSize > 1) {
            kinds += GROUP_EXTRA[wave]
        }
        val tiles = shuffled(SPAWNS.indices.toList())
        for ((index, entry) in kinds.withIndex()) {
            val kind = entry ?: SPECIALS[pickSpecial(SPECIALS.size)]
            val spawnIndex = tiles[index % tiles.size]
            // The north holes face south into the room, the south ones north (TSPS).
            val facing = if (spawnIndex < 3) Direction.South else Direction.North
            spawnBaboon(kind, SPAWNS[spawnIndex], large, critical = true, facing = facing)
        }
        wave++
    }

    private fun typeOf(kind: Kind, large: Boolean): NpcType =
        when (kind) {
            Kind.Melee -> if (large) apmeken_npcs.brawler_large else apmeken_npcs.brawler
            Kind.Ranged -> if (large) apmeken_npcs.thrower_large else apmeken_npcs.thrower
            Kind.Magic -> if (large) apmeken_npcs.mage_large else apmeken_npcs.mage
            Kind.Shaman -> apmeken_npcs.shaman
            Kind.Volatile -> apmeken_npcs.volatile
            Kind.Cursed -> apmeken_npcs.cursed
            Kind.Thrall -> apmeken_npcs.thrall
        }

    private fun spawnBaboon(
        kind: Kind,
        real: CoordGrid,
        large: Boolean,
        critical: Boolean,
        facing: Direction? = null,
    ): Npc {
        val type = typeOf(kind, large)
        // The baboons have the same hitpoints at every raid level (wiki); only their rolls and
        // damage scale.
        val npc = spawn(type, real, scale = false, points = BABOON_POINTS, facing = facing)
        npc.attackRollPercent = (ToaScaling.rollFactor(raid.raidLevel) * 100).toInt()
        val baboon = Baboon(kind, critical)
        if (kind == Kind.Shaman) {
            baboon.thralls = env.random.of(SHAMAN_THRALLS)
        }
        baboons[npc] = baboon
        if (critical) {
            this.critical++
        }
        val maxHit = if (large || kind == Kind.Shaman) MAX_LARGE else MAX_SMALL
        val attack =
            when (kind) {
                Kind.Ranged ->
                    NpcAttack(
                        NpcAttackStyle.Ranged,
                        anim = apmeken_seqs.ranged,
                        projectile = apmeken_spotanims.ranged,
                        projanim = projanims.arrow,
                        maxHit = maxHit,
                    )
                Kind.Magic,
                Kind.Shaman ->
                    NpcAttack(
                        NpcAttackStyle.Magic,
                        anim = apmeken_seqs.magic,
                        projectile = apmeken_spotanims.magic,
                        projanim = projanims.magic_spell,
                        maxHit = maxHit,
                    )
                else -> NpcAttack(NpcAttackStyle.Melee, anim = apmeken_seqs.melee, maxHit = maxHit)
            }
        val range =
            when (kind) {
                Kind.Ranged,
                Kind.Magic,
                Kind.Shaman -> RANGED_REACH
                else -> 1
            }
        val speed = if (kind == Kind.Shaman) SHAMAN_SPEED else BABOON_SPEED
        val monster = monster(npc, attack, range, speed, delay = 3, spawnAnim = apmeken_seqs.spawn)
        monster.scripted = kind == Kind.Cursed
        return npc
    }

    override fun monsterDied(npc: Npc) {
        val baboon = baboons.remove(npc) ?: return
        if (baboon.kind == Kind.Volatile && !baboon.exploded) {
            // Killed before it reached anyone, it still bursts where it falls, at full strength,
            // and the burst is its death: the same animation and timing as when it reaches
            // someone, then the blast and it is gone. The room keeps it until then, so a wipe in
            // between still clears it.
            baboon.exploded = true
            npcs += npc
            npc.resetMovement()
            npc.hideAllOps()
            npc.anim(apmeken_seqs.explode)
            val real = raid.realOf(npc.coords)
            later(EXPLODE_TICKS) {
                real?.let { blast(npc, it, maxHit(EXPLODE_BASE)) }
                despawn(npc)
            }
        }
        if (baboon.critical && wave < WAVES.size) {
            critical--
            if (critical == handOver()) {
                waveDelay = NEXT_WAVE_DELAY
            }
        }
        if (wave >= WAVES.size && baboons.isEmpty()) {
            waveDelay = NEXT_WAVE_DELAY
        }
    }

    /** How many baboons of a wave may still stand when the next one comes (TSPS). */
    private fun handOver(): Int =
        when {
            wave <= 2 -> 0
            wave <= 6 -> 1
            else -> 2
        }

    // The baboons' own rules
    // ---------------------------------------------------------------------------------------

    override fun swing(monster: ToaMonster, target: Player) {
        val npc = monster.npc
        val baboon = baboons[npc] ?: return
        when (baboon.kind) {
            Kind.Volatile -> {
                // It does not hit: on reaching its target it stops and explodes.
                monster.scripted = true
                npc.resetMovement()
                baboon.explodeTicks = EXPLODE_TICKS
                npc.anim(apmeken_seqs.explode)
            }
            Kind.Shaman -> {
                if (baboon.thralls > 0) {
                    npc.anim(apmeken_seqs.summon)
                    baboon.thralls--
                    later(1) { summonThrall(npc, target) }
                    return
                }
                strike(npc, target, monster.attack) { victim, hit ->
                    // Unprotected, the damage is drained from Prayer as well (wiki).
                    if (victim.vars[varbits.protect_from_magic] == 0 && hit.damage > 0) {
                        victim.statSub(stats.prayer, hit.damage, 0)
                    }
                }
            }
            Kind.Thrall -> {
                // A guess at the wiki's "a percentage of what the hit dealt": half, rounded up.
                strike(npc, target, monster.attack) { victim, hit ->
                    if (hit.damage > 0) {
                        victim.statSub(stats.prayer, (hit.damage + 1) / 2, 0)
                    }
                }
            }
            else -> super.swing(monster, target)
        }
    }

    private fun summonThrall(shaman: Npc, target: Player) {
        if (!shaman.isSlotAssigned || !isStarted) {
            return
        }
        val real = raid.realOf(shaman.coords) ?: return
        val free =
            (-2..2)
                .flatMap { dx -> (-2..2).map { dz -> real.translate(dx, dz) } }
                .filter { walkable(it) && room.inChallenge(it) && it != real }
        if (free.isEmpty()) {
            return
        }
        val tile = free[env.random.of(free.size)]
        val thrall = spawnBaboon(Kind.Thrall, tile, large = false, critical = false)
        graphic(apmeken_spotanims.thrall, tile)
        monsters[thrall]?.target = target
    }

    private fun tickBaboons(players: List<Player>) {
        for ((npc, baboon) in baboons.toList()) {
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                continue
            }
            if (baboon.explodeTicks > 0 && --baboon.explodeTicks == 0) {
                explode(npc, players)
                continue
            }
            if (baboon.kind == Kind.Cursed && !isHeld(npc)) {
                wanderCursed(npc)
            }
        }
    }

    /**
     * A volatile baboon bursts in a 3x3 around it: the damage follows the health it had left (the
     * wiki compares it with Vorkath's spawn; the 14 base is TSPS), and other baboons caught in it
     * take the same hit.
     */
    private fun explode(npc: Npc, players: List<Player>) {
        val real = raid.realOf(npc.coords) ?: return
        baboons[npc]?.exploded = true
        val share = npc.hitpoints.toDouble() / npc.baseHitpointsLvl.coerceAtLeast(1)
        val damage = (maxHit(EXPLODE_BASE) * share).toInt().coerceAtLeast(1)
        blast(npc, real, damage, players)
        // It counts as killed for the waves (a volatile baboon is a wave's baboon too).
        monsterDied(npc)
        despawn(npc)
    }

    /**
     * The burst of the volatile baboon [npc] on the real tile [real]: [damage] (and up to 1 more)
     * to every player in the 3x3 around it, and death to every other baboon caught in it.
     */
    private fun blast(
        npc: Npc,
        real: CoordGrid,
        damage: Int,
        players: List<Player> = challengePlayers(),
    ) {
        graphic(apmeken_spotanims.explode_fire, real)
        graphic(apmeken_spotanims.explode, real)
        for (player in players) {
            val at = raid.realOf(player.coords) ?: continue
            if (max(abs(at.x - real.x), abs(at.z - real.z)) <= 1) {
                hurtFlat(player, damage + env.random.of(2))
            }
        }
        for ((other, _) in baboons.toList()) {
            if (other === npc || !other.isSlotAssigned) {
                continue
            }
            val at = raid.realOf(other.coords) ?: continue
            if (max(abs(at.x - real.x), abs(at.z - real.z)) <= 1) {
                // A real hit, so the hitsplat and health bar show; one it kills dies as usual.
                val dealt = damage + env.random.of(2)
                other.queueHit(BLAST_HIT_DELAY, HitType.Typeless, dealt, env.npcHitModifier)
            }
        }
    }

    /**
     * A cursed baboon wanders about leaving venom pools behind it; it never walks onto a vent or
     * the tile in front of a pillar (wiki).
     */
    private fun wanderCursed(npc: Npc) {
        val real = raid.realOf(npc.coords) ?: return
        if (real !in VENTS && real !in PILLAR_FRONTS) {
            placePool(real)
        }
        if (npc.routeDestination.isNotEmpty()) {
            return
        }
        repeat(WANDER_TRIES) {
            val dest = real.translate(env.random.of(-6, 6), env.random.of(-6, 6))
            if (walkable(dest) && room.inChallenge(dest) && dest !in VENTS) {
                npc.walk(raid.regionOf(dest))
                return
            }
        }
    }

    // Venom pools
    // ---------------------------------------------------------------------------------------

    private fun placePool(real: CoordGrid) {
        if (real in pools) {
            return
        }
        pools[real] = now + POOL_TICKS
        // A centrepiece: the pool's models carry no shapes, and the client draws such models
        // only for that shape (as ground decor the server had the pool but nobody saw it).
        placeLoc(real, apmeken_locs.venom_pool, POOL_SHAPE, LocAngle[env.random.of(4)])
    }

    private fun tickPools(players: List<Player>) {
        if (pools.isEmpty()) {
            return
        }
        for ((tile, until) in pools.toList()) {
            if (now >= until) {
                pools.remove(tile)
                removeLoc(tile, POOL_SHAPE)
            }
        }
        for (player in players) {
            val at = raid.realOf(player.coords) ?: continue
            if (at in pools) {
                // 6-8 a tick (wiki), not scaled by the raid level.
                hurtFlat(player, POOL_DAMAGE + env.random.of(3))
                env.venom.envenom(player)
            }
        }
    }

    private fun clearPools() {
        for (tile in pools.keys.toList()) {
            removeLoc(tile, POOL_SHAPE)
        }
        pools.clear()
    }

    // Apmeken's Sight
    // ----------------------------------------------------------------------------------------

    private fun setSight(player: Player?) {
        sight = player
        for (member in raid.members) {
            for (viewer in raid.active) {
                val value = if (player != null && member.player === player) 1 else 0
                VarPlayerIntMapSetter.set(viewer.player, ToaApmekenBits.sight[member.slot], value)
            }
        }
    }

    /** The sight passes on, a hazard is sensed, then the hazard is judged (TSPS's cycle). */
    private fun cycle(players: List<Player>) {
        val holder = sight
        val gone = holder == null || holder !in players
        if (hazard == null && (gone || (players.size > 1 && switchSight))) {
            passSight(players)
            return
        }
        val pending = hazard
        if (pending == null) {
            sense(players)
            return
        }
        judge(pending, players)
        cycles++
        if (cycles == if (players.size == 2) 3 else 2) {
            switchSight = true
            cycleTicks = SIGHT_TICKS
        } else {
            hazardInterval = max(MIN_INTERVAL, hazardInterval - 1)
            cycleTicks = hazardInterval
        }
        hazard = null
    }

    private fun passSight(players: List<Player>) {
        val previous = sight
        if (previous != null && previous in players) {
            previous.mes("<col=3366ff>You no longer have Apmeken's Sight.</col>")
            previous.soundSynth(toa_synths.apmeken_sight)
            previous.spotanim(apmeken_spotanims.lose_sight, height = 180)
        }
        val next =
            if (previous == null) {
                // The first player into the room is given the sight first (wiki).
                arrivals.firstOrNull { it in players } ?: players.firstOrNull()
            } else {
                players
                    .filter { it !== previous }
                    .let { if (it.isEmpty()) previous else it[env.random.of(it.size)] }
            }
        setSight(next)
        sound(toa_synths.apmeken_sight, players)
        if (next != null) {
            next.spotanim(apmeken_spotanims.gain_sight, height = 180)
            for (player in players) {
                player.mes(
                    "<col=3366ff>${if (player === next) "You have" else "${next.displayName} has"} " +
                        "been granted Apmeken's Sight.</col>"
                )
            }
        }
        cycles = 0
        cycleTicks = SIGHT_TICKS
        switchSight = false
    }

    /** A hazard begins: only the sighted player is shown where. */
    fun sense(players: List<Player>) {
        val seer = sight ?: return
        val picked =
            nextHazard?.invoke()
                ?: when (env.random.of(if (players.size > 1) 3 else 2)) {
                    0 -> Hazard.Vents
                    1 -> Hazard.Roof
                    else -> Hazard.Corruption
                }
        hazard = picked
        fixed.fill(false)
        corrupted.clear()
        for (player in players) {
            if (player !== seer) {
                player.mes("<col=0000b2>You sense an issue somewhere in the room.</col>")
            }
        }
        when (picked) {
            Hazard.Vents -> {
                required = minOf(4, players.size)
                seer.mes(
                    "<col=6800bf>You sense some strange fumes coming from holes in the floor.</col>"
                )
                for ((index, tile) in VENTS.withIndex()) {
                    privateGraphic(seer, warning(index), tile, height = 46)
                }
            }
            Hazard.Roof -> {
                required = minOf(4, players.size)
                seer.mes("<col=6800bf>You sense an issue with the roof supports.</col>")
                for ((index, tile) in ROOF_WARNINGS.withIndex()) {
                    privateGraphic(seer, warning(index), tile, height = 96)
                }
            }
            Hazard.Corruption -> {
                // Everyone but the sighted player is corrupted, and only they can see it.
                val victims = players.filter { it !== seer }
                corrupted += victims.map { it.accountId }
                required = victims.size
                seer.mes(
                    "<col=6800bf>You sense a dark corruption spreading through your allies.</col>"
                )
                for (victim in victims) {
                    val at = raid.realOf(victim.coords) ?: continue
                    privateGraphic(seer, apmeken_spotanims.cursed, at, height = 100)
                }
            }
        }
        cycleTicks = HAZARD_TICKS
    }

    private fun warning(index: Int) =
        if (index < 2) apmeken_spotanims.warning_north else apmeken_spotanims.warning_south

    /**
     * Time is up: every fix short of what the party needed hurts everyone, scaled by how many were
     * missed (the wiki says the damage scales with the missed fixes; the 22 is TSPS).
     */
    fun judge(pending: Hazard, players: List<Player>) {
        val done =
            if (pending == Hazard.Corruption) required - corrupted.size else fixed.count { it }
        val missed = (required - done).coerceAtLeast(0)
        if (missed == 0) {
            val text =
                when (pending) {
                    Hazard.Vents ->
                        "Apmeken's Sight guides you into neutralising some dangerous fumes."
                    Hazard.Roof -> "Apmeken's Sight guides you into repairing the roof supports."
                    Hazard.Corruption -> "Apmeken's Sight guides you into cleansing the corruption."
                }
            for (player in players) {
                player.mes("<col=0000b2>$text</col>")
            }
            return
        }
        val damage = (maxHit(PUNISHMENT) * missed / required.coerceAtLeast(1)).coerceAtLeast(1)
        when (pending) {
            Hazard.Vents -> erupt(players, damage, all = true)
            Hazard.Roof -> collapse(players, damage, all = true)
            Hazard.Corruption -> {
                for (player in players) {
                    player.mes("<col=ff3045>The corruption overwhelms your party!</col>")
                    hurtFlat(player, damage)
                }
                corrupted.clear()
            }
        }
    }

    private fun erupt(players: List<Player>, damage: Int, all: Boolean) {
        for (player in players) {
            player.soundSynth(toa_synths.apmeken_vent)
            player.mes(
                if (all) "<col=ff3045>The fumes filling the room suddenly ignite!</col>"
                else
                    "<col=ff3045>Some toxic fumes errupt out of the hole! There was clearly " +
                        "nothing to neutralise.</col>"
            )
            hurtFlat(player, damage)
        }
        val vents = if (all) VENTS else players.mapNotNull { raid.realOf(it.coords) }
        for (tile in vents) {
            graphic(apmeken_spotanims.gas, tile, delay = 5)
        }
    }

    private fun collapse(players: List<Player>, damage: Int, all: Boolean) {
        for (player in players) {
            player.spotanim(apmeken_spotanims.rubble)
            player.mes(
                if (all) "<col=ff3045>Damaged roof supports cause some debris to fall on you!</col>"
                else
                    "<col=ff3045>Some debris falls on you! The roof support clearly didn't need " +
                        "repairing.</col>"
            )
            hurtFlat(player, damage)
        }
        if (all) {
            for (tile in ROOFS) {
                swapLoc(tile, listOf(apmeken_locs.pillar), apmeken_locs.pillar_shaking)
            }
            roofResetAt = now + ROOF_SHAKE_TICKS
        }
    }

    // What the players do
    // ---------------------------------------------------------------------------------------

    /** A crate gives a hammer, or a hundred neutralising potions. */
    fun takeSupplies(player: Player, hammer: Boolean): Boolean {
        val obj = if (hammer) apmeken_objs.hammer else apmeken_objs.potion
        val count = if (hammer) 1 else POTIONS
        if (!player.invAdd(player.inv, obj, count).success) {
            player.mes("You need more space in your inventory to do that.")
            return false
        }
        if (hammer) {
            hammersTaken.merge(player.accountId, 1, Int::plus)
        }
        player.anim(apmeken_seqs.take)
        player.soundSynth(toa_synths.take)
        player.mes(if (hammer) "You take a hammer." else "You take some potions.")
        return true
    }

    /** "Repair" on a roof support: right when the roof is failing, a hurt otherwise. */
    fun repair(player: Player, pillar: CoordGrid) {
        val index = ROOFS.indexOf(pillar)
        if (index < 0) {
            return
        }
        val hasHammer =
            player.inv.any {
                it?.id == apmeken_objs.hammer.id || it?.id == apmeken_objs.dragon_warhammer.id
            } || player.worn.any { it?.id == apmeken_objs.dragon_warhammer.id }
        if (!hasHammer) {
            player.mes("You need a hammer to repair this roof support.")
            return
        }
        if (hazard != Hazard.Roof) {
            collapse(listOf(player), maxHit(PUNISHMENT), all = false)
            return
        }
        if (fixed[index]) {
            player.mes("This roof support has already been repaired.")
            return
        }
        fixed[index] = true
        player.anim(apmeken_seqs.repair)
        player.soundSynth(toa_synths.apmeken_repair)
        player.mes("<col=229628>You repair the damaged roof support.</col>")
    }

    /**
     * "Pour": on a vent while the fumes rise it neutralises them; on the corrupted, standing with
     * the sighted player, it cleanses everyone on that tile; anything else hurts the pourer.
     */
    fun pour(player: Player) {
        val at = raid.realOf(player.coords) ?: return
        player.invDel(player.inv, apmeken_objs.potion, 1)
        player.anim(apmeken_seqs.pour)
        player.soundSynth(toa_synths.water_pour)
        if (hazard == Hazard.Corruption && player === sight) {
            val cleansed =
                challengePlayers().filter {
                    it.accountId in corrupted && raid.realOf(it.coords) == at
                }
            if (cleansed.isEmpty()) {
                player.mes("You pour the potion on the floor, but nothing happens.")
                return
            }
            for (other in cleansed) {
                corrupted -= other.accountId
                other.mes("<col=06600c>The corruption is cleansed from you.</col>")
            }
            return
        }
        val index = VENTS.indexOf(at)
        when {
            index < 0 -> player.mes("You pour the potion on the floor, but nothing happens.")
            hazard != Hazard.Vents -> erupt(listOf(player), maxHit(PUNISHMENT), all = false)
            fixed[index] -> player.mes("This vent has already been neutralised.")
            else -> {
                fixed[index] = true
                player.mes("<col=06600c>You neutralise the fumes coming from the hole.</col>")
            }
        }
    }

    /** The sighted player can also cleanse one ally by using a potion on them (wiki). */
    fun cleanse(player: Player, target: Player) {
        if (hazard != Hazard.Corruption || player !== sight) {
            player.mes("Nothing interesting happens.")
            return
        }
        if (!corrupted.remove(target.accountId)) {
            player.mes("${target.displayName} isn't corrupted.")
            return
        }
        player.invDel(player.inv, apmeken_objs.potion, 1)
        player.anim(apmeken_seqs.pour)
        target.mes("<col=06600c>The corruption is cleansed from you.</col>")
    }

    // Hits
    // ---------------------------------------------------------------------------------------

    /** The style each baboon is weak to always lands its maximum hit (wiki). */
    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        val baboon = baboons[npc] ?: return
        if (source == null || hit.type != weakness(baboon.kind)) {
            return
        }
        val max = source.vars[varps.com_maxhit]
        if (max > hit.damage) {
            hit.damage = max
        }
    }

    private fun weakness(kind: Kind): HitType? =
        when (kind) {
            Kind.Melee -> HitType.Magic
            Kind.Ranged -> HitType.Melee
            Kind.Magic,
            Kind.Shaman,
            Kind.Volatile,
            Kind.Cursed -> HitType.Ranged
            Kind.Thrall -> null
        }

    companion object {
        const val Z = 0

        val SPAWNS =
            listOf(
                CoordGrid(3800, 5288, Z),
                CoordGrid(3808, 5288, Z),
                CoordGrid(3816, 5288, Z),
                CoordGrid(3816, 5272, Z),
                CoordGrid(3808, 5272, Z),
                CoordGrid(3800, 5272, Z),
            )

        /** The vents (north-west, north-east, south-east, south-west), as the map has them. */
        val VENTS =
            listOf(
                CoordGrid(3800, 5284, Z),
                CoordGrid(3816, 5284, Z),
                CoordGrid(3816, 5276, Z),
                CoordGrid(3800, 5276, Z),
            )

        /** The roof supports' south-west tiles (3x3 locs), and where their warning shows. */
        val ROOFS =
            listOf(
                CoordGrid(3803, 5289, Z),
                CoordGrid(3811, 5289, Z),
                CoordGrid(3811, 5269, Z),
                CoordGrid(3803, 5269, Z),
            )
        val ROOF_WARNINGS =
            listOf(
                CoordGrid(3804, 5288, Z),
                CoordGrid(3812, 5288, Z),
                CoordGrid(3812, 5272, Z),
                CoordGrid(3804, 5272, Z),
            )

        /** The tiles in front of the supports, which a cursed baboon keeps off. */
        val PILLAR_FRONTS = ROOF_WARNINGS

        /**
         * The waves (wiki, solo): `null` is a special baboon (a shaman, volatile or cursed one at
         * random).
         */
        private val WAVES: List<List<Kind?>> =
            listOf(
                listOf(Kind.Melee, Kind.Melee, Kind.Shaman),
                listOf(Kind.Ranged, Kind.Ranged, Kind.Volatile),
                listOf(Kind.Magic, Kind.Magic, Kind.Cursed),
                listOf(Kind.Ranged, Kind.Ranged, null, null, null),
                listOf(Kind.Magic, Kind.Magic, Kind.Shaman, null, null),
                listOf(Kind.Melee, Kind.Melee, Kind.Shaman, null, null),
                listOf(Kind.Melee, Kind.Ranged, Kind.Shaman, Kind.Cursed, Kind.Cursed),
                listOf(Kind.Shaman, Kind.Volatile, Kind.Volatile, null, null),
            )

        /** The extra combat baboon a group gets each wave; which one is a guess. */
        private val GROUP_EXTRA: List<Kind> =
            listOf(
                Kind.Magic,
                Kind.Melee,
                Kind.Ranged,
                Kind.Magic,
                Kind.Melee,
                Kind.Ranged,
                Kind.Magic,
                Kind.Ranged,
            )

        private val SPECIALS = listOf(Kind.Shaman, Kind.Volatile, Kind.Cursed)

        val WAVE_COUNT: Int
            get() = WAVES.size

        /** From this wave (0-based) the combat baboons are the large ones; a guess. */
        const val LARGE_FROM_WAVE = 4

        const val FIRST_WAVE_DELAY = 6
        const val NEXT_WAVE_DELAY = 4
        const val BABOON_POINTS = 1.2
        const val MAX_SMALL = 5
        const val MAX_LARGE = 7
        const val BABOON_SPEED = 4
        const val SHAMAN_SPEED = 8
        const val RANGED_REACH = 8

        /** Thralls a shaman can summon: "four or five" (wiki). */
        val SHAMAN_THRALLS = 4..5

        const val EXPLODE_TICKS = 2
        const val EXPLODE_BASE = 14

        /** The blast's hit on baboons beside it lands the tick after, like the players'. */
        const val BLAST_HIT_DELAY = 1
        const val WANDER_TRIES = 6

        const val POOL_TICKS = 33

        /** How a venom pool is placed (see placePool). */
        val POOL_SHAPE = LocShape.CentrepieceStraight
        const val POOL_DAMAGE = 6

        const val SIGHT_TICKS = 30
        const val HAZARD_TICKS = 30
        const val FIRST_INTERVAL = 50
        const val MIN_INTERVAL = 30
        const val PUNISHMENT = 22
        const val ROOF_SHAKE_TICKS = 4
        const val POTIONS = 100
        const val COMPLETION_POINTS = 450
    }
}

/** Who holds Apmeken's Sight, by party slot: the HUD marks that member. */
object ToaApmekenBits : org.rsmod.api.type.refs.varbit.VarBitReferences() {
    val sight = (0..7).map { find("toa_client_primary$it") }
}
