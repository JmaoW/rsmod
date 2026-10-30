package org.rsmod.content.areas.misc.isleofsouls.toa

import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.vars.varMoveSpeed
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
import org.rsmod.game.map.Direction
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionStrategy

typealias zebak_npcs = ZebakNpcs

typealias zebak_locs = ZebakLocs

typealias zebak_seqs = ZebakSeqs

typealias zebak_spotanims = ZebakSpotanims

object ZebakNpcs : NpcReferences() {
    val zebak = find("toa_zebak")
    val tail = find("toa_zebak_tail")
    val enraged = find("toa_zebak_enraged")
    val dead = find("toa_zebak_dead")
    val tail_dead = find("toa_zebak_tail_dead")
    val jug = find("toa_zebak_jug")
    val jug_rolling = find("toa_zebak_jug_rolling")
    val rock = find("toa_zebak_safespot")
    val wave = find("toa_zebak_wave")
    val wave_bloody = find("toa_zebak_wave_bloody")
    val water_croc = find("toa_zebak_watercroc")
    val blood_cloud = find("toa_zebak_blood_cloud")
    val blood_cloud_small = find("toa_zebak_blood_cloud_small")

    /** A 7x7 helper the volley's burst plays on, so it can sit where the projectile peaks. */
    val split_helper = find("spotanim_zebak_ranged01_npc")
}

object ZebakLocs : LocReferences() {
    val acid = (1..6).map { find("toa_zebak_vomit0$it") }
    val rock_block = find("invisible_type8_blocking_active")
    val blood = listOf(find("bloodsplatter1"), find("bloodsplatter2"), find("bloodsplatter3"))
    val climbing_rock = find("toa_zebak_climbing_rock")
}

object ZebakSeqs : SeqReferences() {
    val shoot = find("npc_zebak01_attack_ranged")
    val tail_shoot = find("npc_zebak02_attack_ranged")
    val shoot_enraged = find("npc_zebak01_attack_ranged_enraged")
    val tail_shoot_enraged = find("npc_zebak02_attack_ranged_enraged")
    val melee = find("npc_zebak01_attack_melee")
    val tail_melee = find("npc_zebak02_attack_melee")
    val melee_enraged = find("npc_zebak01_attack_melee_enraged")
    val tail_melee_enraged = find("npc_zebak02_attack_melee_enraged")
    val roar = find("npc_zebak01_attack_roar")
    val tail_roar = find("npc_zebak02_attack_roar")
    val call_wave = find("npc_zebak01_attack_tail")
    val tail_call_wave = find("npc_zebak02_attack_tail")
    val death = find("npc_zebak01_death")
    val tail_death = find("npc_zebak02_death")
    val pushed = find("warguild_parry_defend")
    val slide = find("agilityarena_player_spikedback")
    val jug_move = find("human_leverdown")
    val croc_bite = find("croc_attack_merge")
}

object ZebakSpotanims : SpotanimReferences() {
    val acid = find("tob_xarpus_acidspit")
    val rock = find("zebak_safespot_travel")
    val jug = find("zebak_waterjug_travel")
    val acid_rock = find("zebak_vomit_projectile0")
    val magic_rise = find("zebak_mage_projanim_initial")
    val magic_rise_enraged = find("zebak_mage_projanim_initial_enraged")
    val ranged_rise = find("zebak_range_projanim_initial")
    val ranged_rise_enraged = find("zebak_range_projanim_initial_enraged")
    val magic_fragment = find("zebak_mage_projanim_split")
    val ranged_fragment = find("zebak_ranged_fragment01")
    val magic_burst = find("zebak_mage_split")
    val ranged_burst = find("zebak_ranged_split")
    val magic_impact = find("fireblast_impact")
    val ranged_impact = find("darkbow_smoke_arrow_impact")
    val jug_splash = find("zebak_waterjug_splash_travel")
    val blood = find("spell_blood_barrage_impact")
    val rocks = find("zebak_rock_fall")
    val splash = find("watersplash")
    val roar = find("zebak_roar_wave_dust")
    val jug_break = find("zebak_waterjug_break")
    val acid_gone = find("waterstrike_impact")
}

/**
 * Zebak, Jaws of Gluttony. He lies in the water at the west end and attacks with split ranged and
 * magic volleys (fully blocked by the right prayer) and bites that prayer only halves, sometimes
 * leaving a bleed that hurts while the player moves. At 85, 70, 55 and 40 percent he alternates two
 * specials: rocks, jugs and acid followed by a great roar (shelter up to three tiles behind a rock,
 * which takes 50 a wave), and tidal waves sweeping the arena that wash players into the water,
 * where crocodiles bite. Below 25 percent he enrages and attacks faster. Not Just a Head adds blood
 * barrages and blood clouds.
 *
 * From the wiki: the max hits (38 melee, 16 volleys, waves 6-10), attack speed 7 (faster by path
 * level, 4 enraged), the thresholds, the rock rules, his Defence drain floor (50), the barrage
 * healing, the clouds losing 2 per tile, the invocations. From TSPS (OpenRune captures): the
 * timings of everything, the acid and barrage damage, the jug rules, the volley's flight.
 */
class ZebakRuntime(env: ToaRoomEnv, raid: ToaRaid) :
    ToaRoomRuntime(env, raid, ToaRoom.CrondisBoss) {
    private enum class Special {
        Roar,
        Waves,
    }

    private class Jug(val npc: Npc) {
        var dx = 0
        var dz = 0
        var left = -1
        var rolling = false
        var breakAt = 0
    }

    private class Wave(val npc: Npc, val step: Int) {
        var tiles = WAVE_LENGTH
    }

    private class Cloud(val npc: Npc) {
        var delay = 4
        var target: Player? = null
        var switchAt = 0
        var last: CoordGrid = npc.coords
    }

    private class Bleed(var until: Int) {
        var last: CoordGrid? = null
    }

    private class Croc(val npc: Npc, val home: CoordGrid) {
        var nextBite = 0
    }

    var zebak: Npc? = null
        private set

    private var tail: Npc? = null

    /** The tests turn his own attacks off to work on one rule at a time. */
    var fightEnabled = true

    var enraged = false
        private set

    private var attackSpeed = BASE_SPEED
    private var attackTicks = FIRST_ATTACK
    private var specialsQueued = 0
    private val queued = ArrayDeque<Special>()
    private var busy = false
    private var roaring = false
    private var nextRoar = true
    private var useMagic = false
    private var barrageNext = true
    private var cloudsSouth = false
    private var wavesSouth = true
    private var bloodTicks = -1

    private val acid = LinkedHashMap<CoordGrid, Boolean>()
    private val standingIn = HashMap<Int, Int>()
    private val jugs = LinkedHashMap<Npc, Jug>()
    private val rocks = LinkedHashMap<Npc, CoordGrid>()
    private val waves = ArrayList<Wave>()
    private val clouds = LinkedHashMap<Npc, Cloud>()
    private val bleeding = HashMap<Int, Bleed>()
    private val swimmers = HashSet<Int>()

    /** Whether each swimmer was running when they went in, to put it back when they climb out. */
    private val wasRunning = HashMap<Int, Boolean>()
    private val crocs = ArrayList<Croc>()

    val acidCount: Int
        get() = acid.size

    val rockCount: Int
        get() = rocks.size

    val jugCount: Int
        get() = jugs.size

    val waveCount: Int
        get() = waves.size

    fun jugNpcs(): List<Npc> = jugs.keys.toList()

    fun waveNpcs(): List<Npc> = waves.map { it.npc }

    fun isSwimming(player: Player): Boolean = player.accountId in swimmers

    fun isBleeding(player: Player): Boolean = player.accountId in bleeding

    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun build() {
        enraged = false
        specialsQueued = 0
        queued.clear()
        busy = false
        roaring = false
        nextRoar = env.random.of(2) == 0
        useMagic = env.random.of(2) == 0
        barrageNext = env.random.of(2) == 0
        cloudsSouth = env.random.of(2) == 0
        // The first waves always come from the south, the next from the north (wiki).
        wavesSouth = true
        attackSpeed = BASE_SPEED - minOf(2, pathLevel / 2)
        attackTicks = FIRST_ATTACK
        // He lies facing east, out over the island (TSPS).
        zebak =
            spawn(
                zebak_npcs.zebak,
                SPAWN,
                points = ToaPoints.Multiplier.ZEBAK,
                facing = Direction.East,
            )
        tail =
            spawn(zebak_npcs.tail, TAIL_SPAWN, scale = false, points = 0.0, facing = Direction.East)
    }

    override fun onStart() {
        bossBar = zebak
        attackTicks = FIRST_ATTACK
        bloodTicks = if (raid.settings.isActive(Inv.NOT_JUST_A_HEAD)) attackSpeed * 6 - 1 else -1
        for (tile in WATER_CROCS) {
            val npc = spawn(zebak_npcs.water_croc, tile, scale = false, points = 0.0)
            crocs += Croc(npc, tile)
        }
    }

    override fun onComplete() {
        clearAll()
    }

    override fun onReset() {
        clearAll()
        zebak?.let(::despawn)
        tail?.let(::despawn)
        build()
    }

    override fun onDestroy() {
        for (member in raid.members) {
            stopSwimming(member.player)
        }
    }

    private fun clearAll() {
        for (npc in jugs.keys.toList()) {
            despawn(npc)
        }
        jugs.clear()
        for (wave in waves) {
            despawn(wave.npc)
        }
        waves.clear()
        for (npc in clouds.keys.toList()) {
            despawn(npc)
        }
        clouds.clear()
        for (npc in rocks.keys.toList()) {
            removeRock(npc)
        }
        for (tile in acid.keys.toList()) {
            removeAcid(tile)
        }
        for (croc in crocs) {
            despawn(croc.npc)
        }
        crocs.clear()
        for (member in raid.members) {
            stopSwimming(member.player)
        }
        bleeding.clear()
        standingIn.clear()
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc !== zebak || !isStarted) {
            return
        }
        complete()
        npc.anim(zebak_seqs.death)
        tail?.anim(zebak_seqs.tail_death)
        later(3) {
            npc.transmog(env.npcTypes[zebak_npcs.dead], Int.MAX_VALUE)
            tail?.transmog(env.npcTypes[zebak_npcs.tail_dead], Int.MAX_VALUE)
        }
    }

    // Hits
    // --------------------------------------------------------------------------------------------

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (source != null && isSwimming(source)) {
            hit.damage = 0
            return
        }
        val jug = jugs[npc]
        if (jug != null) {
            // A blow shatters a jug the tick after (TSPS); it takes no damage itself.
            hit.damage = 0
            if (source != null && jug.breakAt == 0) {
                jug.rolling = false
                jug.breakAt = now + 1
            }
        }
    }

    /** After every hit on him: his Defence floor, the special thresholds and the enrage. */
    private fun checkPhases(boss: Npc) {
        // His Defence can be drained by 20 at most, to 50 (wiki).
        val floor = boss.baseDefenceLvl - MAX_DEFENCE_DRAIN
        if (boss.defenceLvl < floor) {
            boss.defenceLvl = floor
        }
        if (enraged) {
            return
        }
        val max = boss.baseHitpointsLvl
        while (
            specialsQueued < THRESHOLDS.size &&
                boss.hitpoints <= max * THRESHOLDS[specialsQueued] / 100
        ) {
            queued += if (nextRoar) Special.Roar else Special.Waves
            nextRoar = !nextRoar
            specialsQueued++
        }
        if (boss.hitpoints <= max * ENRAGE_PERCENT / 100) {
            enraged = true
            // A special still queued is dropped (wiki).
            queued.clear()
            attackSpeed = ENRAGED_SPEED
            boss.transmog(env.npcTypes[zebak_npcs.enraged], Int.MAX_VALUE)
            sound(toa_synths.zebak_enrage)
            for (player in challengePlayers()) {
                player.mes("<col=ff3045>Zebak becomes enraged!</col>")
            }
        }
    }

    // The tick
    // ----------------------------------------------------------------------------------------

    override fun onTick() {
        val boss = zebak ?: return
        val players = challengePlayers()
        for (id in swimmers.toList()) {
            val member = raid.active.firstOrNull { it.player.accountId == id }
            if (member == null || member.player !in players) {
                member?.player?.let(::stopSwimming)
                swimmers.remove(id)
            }
        }
        for (player in players) {
            // Swimmers can only walk (wiki).
            if (isSwimming(player) && player.varMoveSpeed == MoveSpeed.Run) {
                player.varMoveSpeed = MoveSpeed.Walk
            }
        }
        checkPhases(boss)
        tickCrocs(players)
        tickBleeding(players)
        tickAcid(players)
        tickJugs()
        tickWaves(players)
        tickClouds(players)
        if (players.isEmpty() || !fightEnabled) {
            return
        }
        if (!busy && bloodTicks > 0 && --bloodTicks == 0) {
            bloodTicks = attackSpeed * (if (enraged) 8 else 6) - 1
            bloodSpell()
        }
        if (--attackTicks > 0) {
            return
        }
        attackTicks = attackSpeed
        // A special takes his attack's turn; his autos carry on while it plays out (TSPS).
        val special = if (!enraged && !busy) queued.removeFirstOrNull() else null
        when (special) {
            Special.Roar -> roarSpecial(boss)
            Special.Waves -> waveSpecial(boss)
            null -> normalAttack(boss, players)
        }
    }

    // His attacks
    // ----------------------------------------------------------------------------------------

    private fun animate(
        zebakSeq: org.rsmod.game.type.seq.SeqType,
        tailSeq: org.rsmod.game.type.seq.SeqType,
    ) {
        zebak?.anim(zebakSeq)
        tail?.anim(tailSeq)
    }

    fun normalAttack(boss: Npc, players: List<Player>) {
        if (env.random.of(3) == 0) {
            useMagic = !useMagic
        }
        val inReach = players.filter { gap(boss, it.coords) <= 1 && !diagonal(boss, it.coords) }
        if (inReach.isNotEmpty() && env.random.of(3) == 0) {
            bite(boss, inReach)
            return
        }
        volley(boss, useMagic)
    }

    private fun bite(boss: Npc, targets: List<Player>) {
        if (enraged) animate(zebak_seqs.melee_enraged, zebak_seqs.tail_melee_enraged)
        else animate(zebak_seqs.melee, zebak_seqs.tail_melee)
        val attack = NpcAttack(NpcAttackStyle.Melee, maxHit = MAX_BITE, hitDelay = BITE_TICKS)
        for (player in targets) {
            if (env.random.of(BLEED_CHANCE) == 0) {
                later(BITE_TICKS) { bleed(player) }
            } else {
                // Protect from Melee only halves it (wiki).
                strike(boss, player, attack, through = BITE_THROUGH, playAnim = false)
            }
        }
    }

    private fun volley(boss: Npc, magic: Boolean) {
        if (enraged) animate(zebak_seqs.shoot_enraged, zebak_seqs.tail_shoot_enraged)
        else animate(zebak_seqs.shoot, zebak_seqs.tail_shoot)
        sound(if (magic) toa_synths.zebak_magic else toa_synths.zebak_ranged)
        val rising =
            when {
                magic && enraged -> zebak_spotanims.magic_rise_enraged
                magic -> zebak_spotanims.magic_rise
                enraged -> zebak_spotanims.ranged_rise_enraged
                else -> zebak_spotanims.ranged_rise
            }
        projectile(VOLLEY_START, VOLLEY_SPLIT, rising, toa_projanims.volley_rise)
        later(VOLLEY_SPLIT_TICKS) {
            if (!isStarted) {
                return@later
            }
            val helper = spawn(zebak_npcs.split_helper, SPLIT_HELPER, scale = false, points = 0.0)
            helper.spotanim(
                if (magic) zebak_spotanims.magic_burst else zebak_spotanims.ranged_burst,
                height = 750,
            )
            later(3) { despawn(helper) }
            val fragment =
                if (magic) zebak_spotanims.magic_fragment else zebak_spotanims.ranged_fragment
            val style = if (magic) NpcAttackStyle.Magic else NpcAttackStyle.Ranged
            val attack = NpcAttack(style, maxHit = MAX_VOLLEY, hitDelay = VOLLEY_HIT_TICKS)
            for (player in challengePlayers()) {
                val at = raid.realOf(player.coords) ?: continue
                projectile(VOLLEY_SPLIT, at, fragment, toa_projanims.volley_fragment)
                player.spotanim(
                    if (magic) zebak_spotanims.magic_impact else zebak_spotanims.ranged_impact,
                    delay = 90,
                    height = 90,
                )
                strike(boss, player, attack, through = 0, playAnim = false)
            }
        }
    }

    // Blood magic
    // ---------------------------------------------------------------------------------------

    private fun bloodSpell() {
        for (tile in BLOOD_SPELL_TILES) {
            graphic(zebak_spotanims.blood, tile)
        }
        later(2) {
            val players = challengePlayers()
            if (players.isEmpty()) {
                return@later
            }
            if (barrageNext) bloodBarrage(players) else bloodClouds()
            barrageNext = !barrageNext
        }
    }

    /**
     * A barrage that always lands, worse for those standing together; he heals twice its damage.
     */
    private fun bloodBarrage(players: List<Player>) {
        val boss = zebak ?: return
        var heal = 0
        val base = maxHit(BARRAGE_MIN)
        val reach = if (raid.settings.isActive(Inv.ARTERIAL_SPRAY)) 2 else 1
        for (player in players) {
            val damage = env.random.of(base, base + BARRAGE_SPREAD)
            val at = raid.realOf(player.coords) ?: continue
            for (victim in players) {
                val there = raid.realOf(victim.coords) ?: continue
                if (max(abs(there.x - at.x), abs(there.z - at.z)) > reach) {
                    continue
                }
                if (victim.vars[varbits.protect_from_magic] == 1) {
                    continue
                }
                heal += damage * BARRAGE_HEAL
                hurtFlat(victim, damage)
            }
            player.spotanim(zebak_spotanims.blood)
            player.soundSynth(toa_synths.zebak_barrage)
        }
        if (heal > 0) {
            boss.hitpoints = minOf(boss.baseHitpointsLvl, boss.hitpoints + heal)
        }
    }

    private fun bloodClouds() {
        val many = raid.settings.isActive(Inv.BLOOD_THINNERS)
        val base = if (cloudsSouth) BLOOD_CLOUD_TILES[1] else BLOOD_CLOUD_TILES[0]
        repeat(if (many) 3 else 1) { i ->
            val tile = base.translate(i, if (cloudsSouth) i / 2 else -(i / 2))
            val type = if (many) zebak_npcs.blood_cloud_small else zebak_npcs.blood_cloud
            val npc = spawn(type, tile, scale = false, points = 0.0)
            clouds[npc] = Cloud(npc).also { it.switchAt = now + env.random.of(10, 20) }
        }
        cloudsSouth = !cloudsSouth
    }

    /**
     * Clouds chase someone, losing 2 a tile they move (wiki), and drain 2 a tick from anyone
     * beside.
     */
    private fun tickClouds(players: List<Player>) {
        for ((npc, cloud) in clouds.toList()) {
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                clouds.remove(npc)
                if (npc.isSlotAssigned) {
                    despawn(npc)
                }
                continue
            }
            val moved = max(abs(npc.coords.x - cloud.last.x), abs(npc.coords.z - cloud.last.z))
            cloud.last = npc.coords
            if (moved > 0) {
                npc.hitpoints -= 2 * moved
                if (npc.hitpoints <= 0) {
                    clouds.remove(npc)
                    despawn(npc)
                    continue
                }
            }
            if (cloud.delay > 0) {
                cloud.delay--
            }
            val target = cloud.target
            if (now >= cloud.switchAt || target == null || target !in players) {
                cloud.switchAt = now + env.random.of(10, 20)
                cloud.target =
                    players.filter { it !== target }.minByOrNull { gap(npc, it.coords) } ?: target
            }
            cloud.target?.let {
                if (gap(npc, it.coords) > 1) stepToward(npc, it.coords, keepOff = false)
            }
            if (cloud.delay > 0) {
                continue
            }
            for (player in players) {
                if (gap(npc, player.coords) <= 1) {
                    hurtFlat(player, 2)
                    npc.hitpoints = minOf(npc.baseHitpointsLvl, npc.hitpoints + 2)
                }
            }
        }
    }

    // Acid
    // ------------------------------------------------------------------------------------------

    /**
     * Standing in acid hurts more each tick spent in it (the wiki says it quickly spikes; the ramp
     * of 2 a tick is a guess on TSPS's 6-10) and poisons for 2. Pools placed last tick hurt from
     * now.
     */
    private fun tickAcid(players: List<Player>) {
        for (player in players) {
            val at = raid.realOf(player.coords) ?: continue
            if (acid[at] != true) {
                standingIn.remove(player.accountId)
                continue
            }
            val ticks = standingIn.merge(player.accountId, 1, Int::plus) ?: 1
            val low = maxHit(ACID_MIN)
            hurtFlat(player, env.random.of(low, maxHit(ACID_MAX)) + (ticks - 1) * ACID_RAMP)
            env.poison.poison(player, ACID_POISON)
        }
        for (tile in acid.keys) {
            acid[tile] = true
        }
    }

    private fun addAcid(tile: CoordGrid, spread: Boolean, guaranteed: Boolean) {
        if (tile in acid || !onGround(tile)) {
            return
        }
        acid[tile] = false
        placeLoc(
            tile,
            zebak_locs.acid[env.random.of(zebak_locs.acid.size)],
            LocShape.CentrepieceStraight,
            LocAngle[env.random.of(4)],
        )
        if (!spread) {
            return
        }
        val range = if (raid.settings.isActive(Inv.UPSET_STOMACH)) 2 else 1
        val spreads = ArrayList<CoordGrid>()
        for (dx in -range..range) {
            for (dz in -range..range) {
                if (dx == 0 && dz == 0) {
                    continue
                }
                if ((!guaranteed || dz != 0) && env.random.of(3) != 0) {
                    continue
                }
                val next = tile.translate(dx, dz)
                if (!onGround(next) || !walkable(next) || next in acid || isRockTile(next)) {
                    continue
                }
                projectile(tile, next, zebak_spotanims.acid, toa_projanims.splash)
                spreads += next
            }
        }
        if (spreads.isNotEmpty()) {
            later(1) { spreads.forEach { addAcid(it, spread = false, guaranteed = false) } }
        }
    }

    private fun removeAcid(tile: CoordGrid) {
        if (acid.remove(tile) == null) {
            return
        }
        removeLoc(tile)
    }

    // Bleeding
    // ----------------------------------------------------------------------------------------

    private fun bleed(player: Player) {
        if (player !in challengePlayers()) {
            return
        }
        player.mes("<col=ff3045>Zebak's fangs tear into your flesh, causing you to bleed.</col>")
        hurtFlat(player, env.random.of(maxHit(BLEED_APPLY_MIN), maxHit(BLEED_APPLY_MAX)))
        bleeding[player.accountId] =
            Bleed(now + BLEED_TICKS).also { it.last = raid.realOf(player.coords) }
    }

    /** A bleeding player who moved this tick takes one hit, and leaves blood behind. */
    private fun tickBleeding(players: List<Player>) {
        for ((id, bleed) in bleeding.toList()) {
            val player = players.firstOrNull { it.accountId == id }
            if (player == null || now >= bleed.until) {
                bleeding.remove(id)
                continue
            }
            val at = raid.realOf(player.coords) ?: continue
            if (bleed.last == at) {
                continue
            }
            bleed.last = at
            hurtFlat(player, env.random.of(maxHit(BLEED_MOVE_MIN), maxHit(BLEED_MOVE_MAX)))
            if (placedLoc(at, LocShape.GroundDecor) == null) {
                placeLoc(at, zebak_locs.blood[env.random.of(3)], LocShape.GroundDecor, LocAngle[0])
                later(10) { removeLoc(at, LocShape.GroundDecor) }
            }
        }
    }

    // The water
    // ------------------------------------------------------------------------------------------

    private fun onGround(tile: CoordGrid): Boolean =
        tile.x in GROUND_MIN.x..GROUND_MAX.x && tile.z in GROUND_MIN.z..GROUND_MAX.z

    private fun isWater(tile: CoordGrid): Boolean = tile.z < GROUND_MIN.z || tile.z > GROUND_MAX.z

    private fun startSwimming(player: Player) {
        if (!swimmers.add(player.accountId)) {
            return
        }
        wasRunning[player.accountId] = player.varMoveSpeed == MoveSpeed.Run
        player.varMoveSpeed = MoveSpeed.Walk
        player.bas = ToaBas.swim
        player.rebuildAppearance()
        player.mes("<col=ff3045>You're washed into the water!</col>")
    }

    fun stopSwimming(player: Player) {
        if (!swimmers.remove(player.accountId)) {
            return
        }
        if (wasRunning.remove(player.accountId) == true) {
            player.varMoveSpeed = MoveSpeed.Run
        }
        player.bas = null
        player.rebuildAppearance()
    }

    /** Swimmers climb out by the rock steps onto the first free tile of the island. */
    fun climbOut(player: Player, rock: CoordGrid) {
        if (!isSwimming(player)) {
            player.mes(
                "The eyes looking at you from below the surface make you reconsider going down there."
            )
            return
        }
        val step = if (rock.z < MIDDLE.z) 1 else -1
        var landing = rock.translate(0, step)
        for (i in 2..3) {
            if (walkable(landing)) {
                break
            }
            landing = rock.translate(0, step * i)
        }
        stopSwimming(player)
        PathingEntityCommon.telejump(player, env.collision, raid.regionOf(landing))
        player.mes("You use the steps to get yourself back onto the island.")
    }

    /** The water crocodiles drift about until someone is swimming, then go for the nearest. */
    private fun tickCrocs(players: List<Player>) {
        val inWater = players.filter { isSwimming(it) }
        for (croc in crocs) {
            val npc = croc.npc
            if (!npc.isSlotAssigned) {
                continue
            }
            val prey =
                inWater
                    .filter { gap(npc, it.coords) < CROC_HUNT }
                    .minByOrNull { gap(npc, it.coords) }
            if (prey == null) {
                if (env.random.of(8) == 0) {
                    swimToward(npc, croc.home.translate(env.random.of(-4, 4), env.random.of(-4, 4)))
                }
                continue
            }
            npc.facePlayer(prey)
            if (gap(npc, prey.coords) > 1 || diagonal(npc, prey.coords)) {
                raid.realOf(prey.coords)?.let { swimToward(npc, it) }
                continue
            }
            if (now < croc.nextBite) {
                continue
            }
            croc.nextBite = now + CROC_BITE_TICKS
            val attack =
                NpcAttack(NpcAttackStyle.Melee, anim = zebak_seqs.croc_bite, maxHit = CROC_MAX_HIT)
            strike(npc, prey, attack, through = 0)
        }
    }

    /** One step towards [real], never out of the water. */
    private fun swimToward(npc: Npc, real: CoordGrid) {
        val here = raid.realOf(npc.coords) ?: return
        val dx = Integer.signum(real.x - here.x)
        val dz = Integer.signum(real.z - here.z)
        for ((sx, sz) in listOf(dx to dz, dx to 0, 0 to dz)) {
            if (sx == 0 && sz == 0) {
                continue
            }
            val next = here.translate(sx, sz)
            if (isWater(next) && walkable(next) && room.inChallenge(next)) {
                npc.walk(raid.regionOf(next))
                return
            }
        }
    }

    // The roar: rocks, jugs and acid
    // -----------------------------------------------------------------------------------------

    private fun freeTiles(
        min: CoordGrid,
        max: CoordGrid,
        exclude: Collection<CoordGrid> = emptyList(),
    ): List<CoordGrid> {
        val tiles = ArrayList<CoordGrid>()
        for (x in min.x..max.x) {
            for (z in min.z..max.z) {
                val tile = CoordGrid(x, z, Z)
                if (tile in exclude || tile in acid || isRockTile(tile) || !walkable(tile)) {
                    continue
                }
                tiles += tile
            }
        }
        return shuffled(tiles)
    }

    private fun isRockTile(tile: CoordGrid): Boolean = rocks.values.any { it == tile }

    /**
     * Rocks, jugs and acid rain down; then he roars three times and only those behind a rock stay
     * put. His next attack comes 10 ticks on; the roar holds it 11 (TSPS).
     */
    private fun roarSpecial(boss: Npc) {
        busy = true
        attackTicks = 10
        animate(zebak_seqs.shoot, zebak_seqs.tail_shoot)
        roaring = false
        later(1) {
            roaring = throwRocks()
            if (!roaring) {
                busy = false
            }
        }
        later(33) {
            if (roaring) {
                animate(zebak_seqs.roar, zebak_seqs.tail_roar)
                attackTicks = max(attackTicks, 11)
            }
        }
        for ((index, tick) in listOf(36, 38, 40).withIndex()) {
            later(tick) {
                if (roaring) {
                    roar(first = index == 0)
                }
            }
        }
        later(58) {
            if (roaring) {
                roaring = false
                for (npc in rocks.keys.toList()) {
                    removeRock(npc)
                }
                busy = false
            }
        }
    }

    private fun throwRocks(): Boolean {
        val picked = pickRocks() ?: return false
        val jugTiles = pickJugs(picked)
        val pools = freeTiles(GROUND_MIN, GROUND_MAX, picked).take(6)
        val rockPools = picked.map { it.translate(2, 0) }.filter { it !in pools }
        sound(toa_synths.zebak_jugs)
        for (tile in pools) {
            projectile(VOLLEY_START, tile, zebak_spotanims.acid, toa_projanims.lob)
        }
        for (tile in rockPools) {
            projectile(VOLLEY_START, tile, zebak_spotanims.acid_rock, toa_projanims.lob)
        }
        for (tile in picked) {
            projectile(VOLLEY_START, tile, zebak_spotanims.rock, toa_projanims.lob)
        }
        for (tile in jugTiles) {
            projectile(VOLLEY_START, tile, zebak_spotanims.jug, toa_projanims.lob)
        }
        later(5) {
            for (tile in picked) {
                val npc =
                    spawn(
                        zebak_npcs.rock,
                        tile,
                        scale = false,
                        points = 0.0,
                        hitpoints = ROCK_HITPOINTS,
                    )
                removeAcid(tile)
                placeLoc(tile, zebak_locs.rock_block, LocShape.CentrepieceStraight, LocAngle[0])
                rocks[npc] = tile
                for (player in challengePlayers()) {
                    if (raid.realOf(player.coords) == tile) {
                        hurtFlat(player, env.random.of(2, 5))
                        nudgeOff(player)
                    }
                }
            }
            for (tile in rockPools) {
                addAcid(tile, spread = true, guaranteed = true)
            }
            for (tile in pools) {
                addAcid(tile, spread = true, guaranteed = false)
            }
            spawnJugs(jugTiles)
        }
        return true
    }

    /** Two or three rocks (three alone), each in a row of its own, with room behind them. */
    private fun pickRocks(): List<CoordGrid>? {
        val free = freeTiles(ROCK_MIN, ROCK_MAX)
        val base = free.firstOrNull() ?: return null
        val set = free.toHashSet()
        val picks = ArrayList<CoordGrid>()
        for (dz in -6..6) {
            val row =
                (-3..3)
                    .map { base.translate(it, dz) }
                    .filter { it in set && it.translate(1, 0) in set }
            if (row.isNotEmpty()) {
                picks += row[env.random.of(row.size)]
            }
        }
        if (picks.isEmpty()) {
            return null
        }
        return shuffled(picks).take(if (teamSize > 1) 2 else 3)
    }

    /** One jug for each rock that can be pushed into it, and about as many decoys (TSPS). */
    private fun pickJugs(rockTiles: List<CoordGrid>): List<CoordGrid> {
        val free =
            freeTiles(GROUND_MIN, GROUND_MAX, rockTiles).filter {
                it.x != GROUND_MIN.x &&
                    it.x != GROUND_MAX.x &&
                    it.z != GROUND_MIN.z &&
                    it.z != GROUND_MAX.z
            }
        val useful = ArrayList<CoordGrid>()
        val others = ArrayList<CoordGrid>()
        for (tile in free) {
            val lines =
                rockTiles.any { rock ->
                    val dx = rock.x - tile.x
                    val dz = rock.z - tile.z
                    when {
                        abs(dx) > 10 || abs(dz) > 10 -> false
                        dx >= 0 && dz == 0 -> false
                        abs(dz) < 2 && dx > -4 && dx < 0 -> false
                        else -> abs(abs(dz) - abs(dx)) < 2 || dx in -2..0 || abs(dz) <= 1
                    }
                }
            (if (lines) useful else others) += tile
        }
        val jugs = useful.take(rockTiles.size)
        return jugs + others.take(minOf(jugs.size, max(0, env.random.of(6, 8) - jugs.size)))
    }

    /** Jugs on the real [tiles] (public for the tests). */
    fun spawnJugs(tiles: List<CoordGrid>) {
        for (tile in tiles) {
            val npc = spawn(zebak_npcs.jug, tile, scale = false, points = 0.0)
            jugs[npc] = Jug(npc)
            graphic(zebak_spotanims.roar, tile)
            for (player in challengePlayers()) {
                if (raid.realOf(player.coords) == tile) {
                    hurtFlat(player, env.random.of(2, 5))
                }
            }
        }
    }

    /** One roar: the unsheltered are pushed and hurt, each rock takes 50, the first chips jugs. */
    private fun roar(first: Boolean) {
        for (x in GROUND_MIN.x..GROUND_MAX.x) {
            for (z in GROUND_MIN.z..GROUND_MAX.z) {
                if (sheltered(x, z) || isRockTile(CoordGrid(x, z, Z))) {
                    continue
                }
                val delay = 1 + max(abs(x - MIDDLE.x), abs(z - MIDDLE.z))
                graphic(zebak_spotanims.roar, CoordGrid(x, z, Z), delay = delay)
            }
        }
        for (player in challengePlayers()) {
            val at = raid.realOf(player.coords) ?: continue
            if (isSwimming(player) || sheltered(at.x, at.z)) {
                continue
            }
            push(player, 1, 0, 2, scream = true)
        }
        for ((npc, _) in rocks.toList()) {
            npc.hitpoints -= ROAR_ROCK_DAMAGE
            if (npc.hitpoints <= 0) {
                removeRock(npc)
            }
        }
        if (first) {
            for (jug in jugs.values) {
                jug.npc.hitpoints = (jug.npc.hitpoints - 5).coerceAtLeast(0)
                if (jug.npc.hitpoints <= 0 && jug.breakAt == 0) {
                    jug.breakAt = now
                }
            }
        }
    }

    /** The three tiles east of a rock are out of the roar. */
    private fun sheltered(x: Int, z: Int): Boolean =
        rocks.values.any { z == it.z && x > it.x && x <= it.x + 3 }

    private fun removeRock(npc: Npc) {
        val tile = rocks.remove(npc) ?: return
        removeLoc(tile)
        despawn(npc)
    }

    /**
     * "Push" or "Pull": the jug rolls 8 tiles, shattering on a rock or splashing into the water.
     */
    fun moveJug(player: Player, npc: Npc, push: Boolean) {
        val jug = jugs[npc] ?: return
        if (jug.rolling || jug.breakAt != 0) {
            return
        }
        val from = player.coords
        val at = npc.coords
        val dx = Integer.signum(if (push) at.x - from.x else from.x - at.x)
        val dz = Integer.signum(if (push) at.z - from.z else from.z - at.z)
        if (dx == 0 && dz == 0) {
            return
        }
        jug.dx = dx
        jug.dz = dz
        jug.left = JUG_ROLL_TILES
        jug.rolling = true
        npc.transmog(env.npcTypes[zebak_npcs.jug_rolling], Int.MAX_VALUE)
        player.anim(zebak_seqs.jug_move)
    }

    private fun tickJugs() {
        for ((npc, jug) in jugs.toList()) {
            if (jug.breakAt != 0 && now >= jug.breakAt) {
                breakJug(jug)
                continue
            }
            if (!jug.rolling) {
                continue
            }
            if (jug.left == 0) {
                // A pushed jug stops after 8 tiles; one a wave carries keeps going.
                jug.rolling = false
                npc.resetTransmog()
                continue
            }
            val here = raid.realOf(npc.coords) ?: continue
            val next = here.translate(jug.dx, jug.dz)
            if (isRockTile(next)) {
                breakJug(jug)
                continue
            }
            if (!walkable(next) || !onGround(next)) {
                graphic(zebak_spotanims.splash, next)
                jugs.remove(npc)
                despawn(npc)
                continue
            }
            npc.walk(raid.regionOf(next))
            if (jug.left > 0) {
                jug.left--
            }
        }
    }

    /** A broken jug washes away the acid around it. */
    private fun breakJug(jug: Jug) {
        val npc = jug.npc
        if (jugs.remove(npc) == null) {
            return
        }
        val at = raid.realOf(npc.coords)
        despawn(npc)
        at ?: return
        graphic(zebak_spotanims.jug_break, at)
        val range = if (raid.settings.isActive(Inv.UPSET_STOMACH)) 1 else 2
        for (dx in -range..range) {
            for (dz in -range..range) {
                val tile = at.translate(dx, dz)
                if (tile !in acid) {
                    continue
                }
                projectile(at, tile, zebak_spotanims.jug_splash, toa_projanims.splash)
                later(1) {
                    removeAcid(tile)
                    graphic(zebak_spotanims.acid_gone, tile)
                }
            }
        }
    }

    // Tidal waves
    // ----------------------------------------------------------------------------------------

    /**
     * Jugs and acid at tick 1, the tail slam at 5, rocks falling at 7, rows of waves at 14, 21 and
     * 28, done at 45; his next attack comes 15 ticks on (TSPS).
     */
    private fun waveSpecial(boss: Npc) {
        busy = true
        attackTicks = 15
        animate(zebak_seqs.shoot, zebak_seqs.tail_shoot)
        val jugTiles = freeTiles(GROUND_MIN, GROUND_MAX).take(env.random.of(6, 8))
        val pools = freeTiles(GROUND_MIN, GROUND_MAX, jugTiles).take(16)
        later(1) {
            for (tile in pools) {
                projectile(VOLLEY_START, tile, zebak_spotanims.acid, toa_projanims.lob)
            }
            for (tile in jugTiles) {
                projectile(VOLLEY_START, tile, zebak_spotanims.jug, toa_projanims.lob)
            }
        }
        val south = wavesSouth
        later(5) { animate(zebak_seqs.call_wave, zebak_seqs.tail_call_wave) }
        later(6) {
            for (tile in pools) {
                addAcid(tile, spread = true, guaranteed = false)
            }
            spawnJugs(jugTiles)
        }
        later(7) {
            val base = if (south) WAVE_SOUTH else WAVE_NORTH
            for (x in 0 until 7) {
                graphic(zebak_spotanims.splash, base.translate(x * 3, 0), delay = 200)
                graphic(zebak_spotanims.rocks, base.translate(x * 3, 0))
            }
            sound(toa_synths.zebak_rumbling)
        }
        // Every two path levels close one hole of the gap (wiki).
        val holes = 3 - minOf(2, pathLevel / 2)
        var skip = -1
        for (tick in listOf(14, 21, 28)) {
            later(tick) {
                val gap = if (skip == -1) env.random.of(13) else 12 - skip
                spawnWave(south, gap, holes)
                skip = if (skip == -1) gap else -1
            }
        }
        later(45) {
            busy = false
            wavesSouth = !wavesSouth
        }
    }

    /** One row of waves from the south or north edge, [holes] wide at [gap] (tests call it too). */
    fun spawnWave(south: Boolean, gap: Int, holes: Int) {
        val base = if (south) WAVE_SOUTH else WAVE_NORTH
        for (x in 0 until WAVE_WIDTH) {
            if (x >= 4) {
                val open =
                    (0 until holes).any { hole -> x - 4 == gap + if (gap < 6) hole else -hole }
                if (open) {
                    continue
                }
            }
            val npc = spawn(zebak_npcs.wave, base.translate(x, 0), scale = false, points = 0.0)
            // Waves sweep through everything: the island's invisible edge walls, Zebak, jugs and
            // the players they push (TSPS ignores clipping too).
            npc.collisionOverride = CollisionStrategy.Ignore
            waves += Wave(npc, if (south) 1 else -1)
        }
    }

    /** Waves push players, carry jugs, wash away some acid and smother blood clouds. */
    private fun tickWaves(players: List<Player>) {
        for (wave in waves.toList()) {
            val npc = wave.npc
            if (--wave.tiles <= 0 || !npc.isSlotAssigned) {
                waves.remove(wave)
                despawn(npc)
                continue
            }
            val here = raid.realOf(npc.coords) ?: continue
            for (player in players) {
                if (raid.realOf(player.coords) == here && !isSwimming(player)) {
                    push(player, 0, wave.step, 4, scream = false)
                }
            }
            for (jug in jugs.values) {
                val at = raid.realOf(jug.npc.coords) ?: continue
                if (at.x == here.x && at.z == here.z + wave.step * 2 && !jug.rolling) {
                    jug.dx = 0
                    jug.dz = wave.step
                    jug.left = -1
                    jug.rolling = true
                    jug.npc.transmog(env.npcTypes[zebak_npcs.jug_rolling], Int.MAX_VALUE)
                }
            }
            if (here in acid && env.random.of(4) == 0) {
                removeAcid(here)
            }
            for ((cloud, _) in clouds.toList()) {
                if (raid.realOf(cloud.coords) == here) {
                    clouds.remove(cloud)
                    despawn(cloud)
                    npc.transmog(env.npcTypes[zebak_npcs.wave_bloody], Int.MAX_VALUE)
                }
            }
            npc.walk(raid.regionOf(here.translate(0, wave.step)))
        }
    }

    /**
     * Pushed up to [distance] tiles as far as the floor allows, and hurt; a wave that runs out of
     * floor throws the player into the water (5 tiles, TSPS).
     */
    private fun push(player: Player, dx: Int, dz: Int, distance: Int, scream: Boolean) {
        val at = raid.realOf(player.coords) ?: return
        var moved = 0
        while (moved < distance && walkable(at.translate(dx * (moved + 1), dz * (moved + 1)))) {
            moved++
        }
        var intoWater = false
        if (!scream && moved < distance) {
            val water = at.translate(dx * WATER_JUMP, dz * WATER_JUMP)
            if (isWater(water) && walkable(water)) {
                moved = WATER_JUMP
                intoWater = true
            }
        }
        if (moved > 0) {
            knockback(player, at.translate(dx * moved, dz * moved), facing = facing(dx, dz))
        }
        if (intoWater) {
            startSwimming(player)
        }
        player.anim(zebak_seqs.pushed)
        player.soundSynth(if (scream) toa_synths.zebak_roar_push else toa_synths.zebak_wave_hit)
        if (scream) {
            val damage = maxHit(MAX_SCREAM)
            hurtFlat(player, env.random.of(damage, damage + 10))
        } else {
            hurtFlat(player, env.random.of(maxHit(MIN_WAVE), maxHit(MAX_WAVE)))
        }
    }

    /** Faces the way the push came from. */
    private fun facing(dx: Int, dz: Int): Int =
        when {
            dx > 0 -> 512
            dx < 0 -> 1536
            dz > 0 -> 0
            else -> 1024
        }

    private fun nudgeOff(player: Player) {
        val at = raid.realOf(player.coords) ?: return
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) {
                    continue
                }
                val tile = at.translate(dx, dz)
                if (walkable(tile) && !isRockTile(tile)) {
                    player.anim(zebak_seqs.slide)
                    knockback(player, tile, facing = facing(dx, dz))
                    return
                }
            }
        }
    }

    /** What stops a player attacking: swimming (wiki: no attacking in the water). */
    fun attackDenial(player: Player): String? =
        if (isSwimming(player)) "You can't attack while swimming." else null

    companion object {
        const val Z = 0
        val SPAWN = CoordGrid(3918, 5404, Z)
        val TAIL_SPAWN = CoordGrid(3909, 5403, Z)
        val GROUND_MIN = CoordGrid(3926, 5398, Z)
        val GROUND_MAX = CoordGrid(3942, 5418, Z)
        val ROCK_MIN = CoordGrid(3925, 5401, Z)
        val ROCK_MAX = CoordGrid(3935, 5415, Z)
        val MIDDLE = CoordGrid(3926, 5408, Z)
        val VOLLEY_START = CoordGrid(3925, 5408, Z)
        val VOLLEY_SPLIT = CoordGrid(3933, 5408, Z)
        val SPLIT_HELPER = CoordGrid(3930, 5405, Z)
        val WAVE_SOUTH = CoordGrid(3923, 5397, Z)
        val WAVE_NORTH = CoordGrid(3923, 5419, Z)
        val BLOOD_SPELL_TILES = listOf(CoordGrid(3924, 5406, Z), CoordGrid(3925, 5410, Z))
        val BLOOD_CLOUD_TILES = listOf(CoordGrid(3931, 5413, Z), CoordGrid(3934, 5401, Z))

        /** The rock steps out of the water (the map's own locs). */
        val CLIMBING_ROCKS = listOf(CoordGrid(3933, 5397, Z), CoordGrid(3933, 5419, Z))

        val WATER_CROCS =
            listOf(
                CoordGrid(3948, 5408, Z),
                CoordGrid(3935, 5422, Z),
                CoordGrid(3934, 5420, Z),
                CoordGrid(3921, 5418, Z),
                CoordGrid(3937, 5396, Z),
                CoordGrid(3937, 5395, Z),
                CoordGrid(3919, 5403, Z),
            )

        val THRESHOLDS = intArrayOf(85, 70, 55, 40)
        const val ENRAGE_PERCENT = 25
        const val BASE_SPEED = 7
        const val ENRAGED_SPEED = 4
        const val FIRST_ATTACK = 10
        const val MAX_DEFENCE_DRAIN = 20

        const val MAX_BITE = 38
        const val BITE_TICKS = 2
        const val BITE_THROUGH = 50
        const val MAX_VOLLEY = 16
        const val VOLLEY_SPLIT_TICKS = 4
        const val VOLLEY_HIT_TICKS = 3
        const val MAX_SCREAM = 20
        const val MIN_WAVE = 6
        const val MAX_WAVE = 10
        const val WATER_JUMP = 5
        const val WAVE_WIDTH = 21
        const val WAVE_LENGTH = 23
        const val ACID_MIN = 6
        const val ACID_MAX = 10
        const val ACID_RAMP = 2
        const val ACID_POISON = 2
        const val BARRAGE_MIN = 3
        const val BARRAGE_SPREAD = 2
        const val BARRAGE_HEAL = 2
        const val ROCK_HITPOINTS = 150
        const val ROAR_ROCK_DAMAGE = 50
        const val JUG_ROLL_TILES = 8
        const val BLEED_CHANCE = 4
        const val BLEED_TICKS = 10
        const val BLEED_APPLY_MIN = 5
        const val BLEED_APPLY_MAX = 10
        const val BLEED_MOVE_MIN = 1
        const val BLEED_MOVE_MAX = 8
        const val CROC_MAX_HIT = 8
        const val CROC_BITE_TICKS = 2
        const val CROC_HUNT = 16
    }
}
