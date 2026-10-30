package org.rsmod.content.areas.misc.isleofsouls.toa

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.config.refs.varps
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.output.mes
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

typealias kephri_npcs = KephriNpcs

typealias kephri_seqs = KephriSeqs

typealias kephri_spotanims = KephriSpotanims

object KephriNpcs : NpcReferences() {
    /** Awake (her shield up), dazed while her children restore it, the final phase, dead. */
    val kephri = find("toa_kephri_boss_shielded")
    val dazed = find("toa_kephri_boss_weak")
    val enraged = find("toa_kephri_boss_enrage")
    val dead = find("toa_kephri_boss_dead")
    val swarm = find("toa_kephri_shield_scarab")
    val soldier = find("toa_kephri_guardian_melee")
    val spitter = find("toa_kephri_guardian_ranged")
    val arcane = find("toa_kephri_guardian_mage")
    val agile = find("toa_kephri_scarab_rangekite")
    val egg = find("kephri_egg_explode")
    val egg_hatching = find("kephri_egg_hatch")
}

object KephriLocs : LocReferences() {
    val dung = find("kephri_dung")
    val block = find("invisible_type8_blocking_size5")
}

object KephriSeqs : SeqReferences() {
    val throw_fireball = find("npc_kephri_attack_magic_radial")
    val throw_eggs = find("npc_kephri_attack_special_eggs")
    val dazed = find("npc_kephri_stunned")
    val wake = find("npc_kephri_stunned_wakeup")
    val death = find("npc_kephri_death")
    val egg_spawn = find("nightmare_infection_spotanim_spawn")
    val player_thrown = find("human_troll_flyback_merge")
    val player_slide = find("agilityarena_player_spikedback")
    val scarab_spawn = find("npc_scarab_spawn")
    val scarab_attack = find("npc_scarab_attack")
    val spit = find("npc_scarab_ranged_attack")
    val agile_attack = find("npc_scarab_flying_attack")
    val arcane_up = find("npc_scarab_flying_takeoff")
    val arcane_down = find("npc_scarab_flying_landing")
    val swarm_spawn = find("npc_swarm01_scarab01_spawn01")
    val swarm_fade = find("npc_swarm01_scarab01_integrate01")

    /** A killed swarm (the RuneLite plugin counts kills by it). */
    val swarm_death = find("npc_swarm01_scarab01_death01")
    val scarab_death = find("npc_scarab_death")
}

object KephriSpotanims : SpotanimReferences() {
    val to_sleep = find("kephri_shield_death")
    val wake = find("kephri_shield_reform")

    /** The swarm shield around her, looped while it is up and while it lies broken. */
    val shield_alive = find("kephri_shield_alive")
    val shield_dead = find("kephri_shield_dead")
    val telegraph = find("gargboss_debris_shadow_120")
    val egg_explode = find("vfx_scarab_explosion01")
    val fly_explode = find("kephri_kamikaze_scarab_explode")
    val bomb_explode = find("vfx_scarab_explosion02")
    val player_flies = find("kephri_indicate_player_for_dung")
    val stun = find("stunned_thieving")
    val egg_break = find("spotanim_dungball_explosion01")
    val dung = find("kephri_dung_drop")
    val arcane_charge = find("toa_kephri_mage_charge")
    val throw_first = find("vorkath_area_travel")
    val throw_second = find("vorkath_area_travel_small")
    val brown_egg = find("projectile_dungball01")
    val egg = find("projectile_dungball02")
    val fly_bomb = find("kephri_kamikaze_scarab")
    val heal = find("toa_kephri_melee_heal")
    val spit = find("toa_scarab_ranged_projectile01")
}

/**
 * Kephri, A Mother's Curse. She never moves. She bombards the tiles players stand on with fireballs
 * (a shadow warns where), lays eggs that explode or hatch agile scarabs, and flings a player back
 * through a trail of dung. The first two times her health runs out she is dazed while scarab swarms
 * crawl in to restore her (each that reaches her heals her) and her overlords join the fight: a
 * spitting scarab, then a soldier, an arcane one and another spitter. The third time she is down
 * for good.
 *
 * From the wiki: the fireball's 24 max hit (a third through Protect from Magic), her health (150,
 * 80 in the last phase), the swarm schedule (28 then 24 swarms, every 4, 3, 2 and 1 ticks), the
 * 115% heal cap, players' max hits on swarms and eggs, the overlords and their rules, the
 * invocations. From TSPS: the tiles, the egg and dung rules in detail, the overlords' timings and
 * damage.
 */
class KephriRuntime(env: ToaRoomEnv, raid: ToaRaid) :
    ToaRoomRuntime(env, raid, ToaRoom.ScabarasBoss) {
    private enum class Overlord {
        Soldier,
        Spitter,
        Arcane,
    }

    private class OverlordState(val kind: Overlord) {
        var healTicks = 13
        var heals = 0
        var attackTicks = 4
        var chargeDelay = 5
        var charges = 0
        var tileIndex = 1
        var flying = 0
        var moderateHits = 0
    }

    private class Swarm(val npc: Npc, val medic: Boolean) {
        var delay = 4
        var fading = false
    }

    var kephri: Npc? = null
        private set

    /** The tests turn her own attacks off to work on one rule at a time. */
    var fightEnabled = true

    /** 0 before her first daze, 1 and 2 after each, 3 in the final phase. */
    var phase = 0
        private set

    private var intermission = 0
    private var swarmsLeft = 0
    private var swarmTicks = 0
    private var swarmsSpawned = 0
    private var lastSwarmAt = 0
    private var attackTicks = 0
    private var specialCycle = 3
    private var dungNext = true
    private var playerIndex = 0
    private var eggFuse = 0
    private var medicTicks = MEDIC_TICKS
    private var flyBombTicks = 8
    private var swarmIndex = 0
    private var tripleSwarmCycle = 0
    private var shieldLookAt = 0
    private val dung = HashSet<CoordGrid>()
    private val eggs = LinkedHashMap<Npc, Boolean>()
    private val agile = LinkedHashSet<Npc>()
    private val swarms = LinkedHashMap<Npc, Swarm>()
    private val overlords = LinkedHashMap<Npc, OverlordState>()
    private val downed = HashSet<Npc>()

    val isDazed: Boolean
        get() = intermission > 0

    val swarmCount: Int
        get() = swarms.size

    /** For the tests: swarms still to come, and when the last one came. */
    val debugIntermission: String
        get() =
            "left=$swarmsLeft last=$lastSwarmAt now=$now fading=${swarms.values.count { it.fading }}"

    val eggCount: Int
        get() = eggs.size

    val dungCount: Int
        get() = dung.size

    val overlordCount: Int
        get() = overlords.size

    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun build() {
        phase = 0
        intermission = 0
        attackTicks = attackSpeed()
        specialCycle = 3
        dungNext = true
        playerIndex = 0
        swarmIndex = env.random.of(SWARM_SPAWNS.size)
        tripleSwarmCycle = 0
        kephri = spawn(kephri_npcs.kephri, SPAWN, points = 1.0)
        placeLoc(SPAWN, KephriLocs.block, LocShape.CentrepieceStraight, LocAngle[0])
    }

    override fun onStart() {
        bossBar = kephri
        attackTicks = attackSpeed()
        shieldLookAt = now
    }

    override fun onComplete() {
        clearAdds()
        clearDung()
    }

    override fun onReset() {
        clearAdds()
        clearDung()
        kephri?.let(::despawn)
        downed.clear()
        build()
    }

    private fun clearAdds() {
        for (npc in eggs.keys + agile + swarms.keys + overlords.keys) {
            despawn(npc)
        }
        eggs.clear()
        agile.clear()
        swarms.clear()
        overlords.clear()
    }

    private fun clearDung() {
        for (tile in dung) {
            removeLoc(tile)
        }
        dung.clear()
    }

    /** Until her final phase her health is her swarm shield, and the bar shows it in blue. */
    override fun bossBarColours(npc: Npc): List<Int> =
        if (phase < 3) ToaBossBarScripts.SHIELD_COLOURS else ToaBossBarScripts.COLOURS

    /** Every two path levels make her attack a tick faster (wiki). */
    private fun attackSpeed(): Int = ATTACK_SPEED - minOf(2, pathLevel / 2)

    // Hits and downs
    // ----------------------------------------------------------------------------------------

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        val boss = kephri
        if (npc === boss) {
            // A hit that would take her last hitpoint downs her when it lands (landingDamage), not
            // when it is thrown: one still in flight as she is dazed must not count again.
            if (isDazed || isCompleted) {
                hit.damage = 0
            }
            return
        }
        // Players always land their max hit on swarms and eggs (wiki).
        if ((npc in swarms || npc in eggs) && source != null) {
            hit.damage = max(hit.damage, source.vars[varps.com_maxhit]).coerceAtLeast(1)
            return
        }
        val state = overlords[npc] ?: return
        if (state.kind == Overlord.Arcane && source != null) {
            // Three solid hits (one more for each extra player) or one heavy hit move it (wiki).
            if (hit.damage >= ARCANE_HEAVY) {
                arcaneFlyAway(npc, state)
            } else if (hit.damage >= ARCANE_MODERATE && ++state.moderateHits >= 2 + teamSize) {
                arcaneFlyAway(npc, state)
            }
        }
    }

    /**
     * A hit queued before she was dazed does nothing when it lands, and one that would take her
     * last hitpoint leaves one and downs her (once, however many land together).
     */
    override fun landingDamage(npc: Npc, damage: Int): Int {
        if (npc !== kephri) {
            return damage
        }
        if (isDazed || npc in downed || isCompleted) {
            return 0
        }
        if (damage >= npc.hitpoints) {
            downed += npc
            return (npc.hitpoints - 1).coerceAtLeast(0)
        }
        return damage
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc === kephri && isDazed) {
            npc.hitpoints = 1
            return
        }
        downed += npc
        if (npc.hitpoints < 1) {
            npc.hitpoints = 1
        }
    }

    override fun monsterDied(npc: Npc) {
        if (eggs.remove(npc) != null) {
            raid.realOf(npc.coords)?.let { graphic(kephri_spotanims.egg_break, it) }
        }
        agile -= npc
        overlords.remove(npc)
        swarms.remove(npc)
    }

    /** Her health ran out: dazed the first two times, the final phase the third, then dead. */
    private fun down(boss: Npc) {
        downed -= boss
        phase++
        if (phase >= 4) {
            finalDeath(boss)
            return
        }
        if (phase == 3) {
            val scaled = ToaScaling.hitpoints(FINAL_HITPOINTS, raid.raidLevel, teamSize, pathLevel)
            boss.baseHitpointsLvl = scaled
            boss.hitpoints = scaled
            boss.transmog(env.npcTypes[kephri_npcs.enraged], Int.MAX_VALUE)
            // Her swarm is gone: one dung strike on everyone opens the final phase (wiki).
            for (player in challengePlayers()) {
                dungAttack(boss, player, force = true)
            }
            return
        }
        intermission = phase
        swarmsLeft = if (phase == 1) FIRST_SWARMS else SECOND_SWARMS
        swarmsSpawned = 0
        swarmTicks = 1
        attackTicks = attackSpeed()
        medicTicks = MEDIC_TICKS
        flyBombTicks = 8
        boss.hitpoints = 1
        boss.anim(kephri_seqs.dazed)
        boss.spotanim(kephri_spotanims.to_sleep)
        shieldLookAt = now + SHIELD_BREAK_TICKS
        boss.transmog(env.npcTypes[kephri_npcs.dazed], Int.MAX_VALUE)
        val kinds = ArrayList<Overlord>()
        val more = raid.settings.isActive(Inv.MORE_OVERLORDS)
        // A spitter both times (the second time alongside the arcane and soldier scarabs).
        if (phase == 2) kinds += Overlord.Arcane
        kinds += Overlord.Spitter
        if (phase == 2 || more) kinds += Overlord.Soldier
        for ((index, kind) in kinds.withIndex()) {
            later(index) { spawnOverlord(kind) }
        }
    }

    private fun finalDeath(boss: Npc) {
        boss.hitpoints = 1
        // Dead, she does nothing: no target, no turning, no moving.
        boss.noneMode()
        boss.clearInteraction()
        complete()
        boss.anim(kephri_seqs.death)
        later(2) { boss.transmog(env.npcTypes[kephri_npcs.dead], Int.MAX_VALUE) }
    }

    /** The swarms have all come: she wakes with what they restored. */
    private fun wake(boss: Npc) {
        intermission = 0
        boss.spotanim(kephri_spotanims.wake)
        boss.anim(kephri_seqs.wake)
        shieldLookAt = now + SHIELD_BREAK_TICKS
        boss.resetTransmog()
        for (swarm in swarms.values.toList()) {
            fade(swarm)
        }
        attackTicks = attackSpeed()
    }

    /** Heals her by a share of her health, up to 115%; reaching the cap wakes her early. */
    private fun heal(boss: Npc, share: Double) {
        val cap = (boss.baseHitpointsLvl * HEAL_CAP).toInt()
        if (boss.hitpoints >= cap) {
            return
        }
        val amount = (boss.baseHitpointsLvl * share).roundToInt().coerceAtLeast(1)
        boss.hitpoints = minOf(cap, boss.hitpoints + amount)
        if (boss.hitpoints >= cap && isDazed) {
            swarmsLeft = 0
        }
    }

    // The tick
    // ----------------------------------------------------------------------------------------

    override fun onTick() {
        val boss = kephri ?: return
        if (boss in downed) {
            down(boss)
            if (isCompleted) {
                return
            }
        }
        val players = challengePlayers()
        if (players.isEmpty()) {
            return
        }
        tickShieldLook(boss)
        tickDung(players)
        if (eggFuse > 0 && --eggFuse == 0) {
            for (egg in eggs.keys.toList()) {
                hatch(egg)
            }
        }
        tickOverlords(boss, players)
        tickSwarms(boss)
        for (npc in agile.toList()) {
            if (!npc.isSlotAssigned) {
                agile -= npc
            }
        }
        tickAgile(players)
        driveMonsters()
        if (isDazed) {
            tickIntermission(boss)
            return
        }
        if (!fightEnabled) {
            return
        }
        if (raid.settings.isActive(Inv.MEDIC) && --medicTicks <= 0) {
            medicTicks = MEDIC_TICKS
            spawnSwarm(medic = true)
        }
        if (--attackTicks > 0) {
            return
        }
        attackTicks = attackSpeed()
        // The dung strike and the eggs are used only while she is shielded (wiki).
        if (phase < 3 && specialCycle-- == 0) {
            specialCycle = env.random.of(4, 5)
            if (dungNext || agile.size > 4 + teamSize) {
                dungNext = false
                prepareDung(boss, players)
            } else {
                dungNext = true
                throwEggs(boss, players)
            }
        } else {
            fireball(boss)
        }
    }

    /**
     * The swarms come at the wiki's pace: 4 every 4 ticks, 4 every 3, 4 every 2, the rest every
     * tick.
     */
    private fun tickIntermission(boss: Npc) {
        if (flyBombTicks > 0 && --flyBombTicks <= 0) {
            flyBombTicks = FLY_BOMB_TICKS
            bombs(regular = false)
        }
        if (swarmsLeft <= 0) {
            // She wakes once the last swarms have reached her, or soon after the last one came
            // (a swarm that cannot get through is lost).
            if (swarms.values.none { !it.fading } || now - lastSwarmAt > SWARM_GRACE) {
                wake(boss)
            }
            return
        }
        if (--swarmTicks > 0) {
            return
        }
        spawnSwarm(medic = false)
        lastSwarmAt = now
        swarmsLeft--
        swarmsSpawned++
        swarmTicks =
            when {
                swarmsSpawned < 4 -> 4
                swarmsSpawned < 8 -> 3
                swarmsSpawned < 12 -> 2
                else -> 1
            }
    }

    /** The shield's loop, sent again as each one ends: whole while it is up, broken while dazed. */
    private fun tickShieldLook(boss: Npc) {
        if (phase >= 3 || now < shieldLookAt) {
            return
        }
        if (isDazed) {
            boss.spotanim(kephri_spotanims.shield_dead)
            shieldLookAt = now + SHIELD_DEAD_TICKS
        } else {
            boss.spotanim(kephri_spotanims.shield_alive)
            shieldLookAt = now + SHIELD_ALIVE_TICKS
        }
    }

    /** Agile scarabs keep their distance: one with a player close by backs off (wiki). */
    private fun tickAgile(players: List<Player>) {
        for (npc in agile) {
            val monster = monsters[npc] ?: continue
            val near =
                players
                    .firstOrNull { gap(npc, it.coords) in 0..AGILE_FLEE_RANGE }
                    ?.let { raid.realOf(it.coords) }
            val at = raid.realOf(npc.coords)
            if (near == null || at == null) {
                monster.scripted = false
                continue
            }
            monster.scripted = true
            val dx = Integer.signum(at.x - near.x)
            val dz = Integer.signum(at.z - near.z)
            for ((sx, sz) in listOf(dx to dz, dx to 0, 0 to dz, dz to dx, -dz to -dx)) {
                if (sx == 0 && sz == 0) {
                    continue
                }
                val dest = npc.coords.translate(sx, sz)
                if (canStand(npc, dest) && raid.realOf(dest) !in dung) {
                    npc.walk(dest)
                    break
                }
            }
        }
    }

    // Her attacks
    // ----------------------------------------------------------------------------------------

    private fun fireball(boss: Npc) {
        boss.anim(kephri_seqs.throw_fireball)
        projectile(
            THROW_START,
            THROW_SECOND,
            kephri_spotanims.throw_first,
            toa_projanims.volley_rise,
        )
        later(2) {
            if (!isDazed) {
                bombs(regular = true)
            }
        }
    }

    /** Bombs on every player's tile (3x3 with Aerial Assault), landing three ticks later. */
    private fun bombs(regular: Boolean) {
        val players = challengePlayers()
        val tiles = LinkedHashSet<CoordGrid>()
        val centres = ArrayList<CoordGrid>()
        for (player in players) {
            val at = raid.realOf(player.coords) ?: continue
            if (!tiles.add(at)) {
                continue
            }
            centres += at
            if (regular) {
                // Her fireball went up out of sight; each one comes straight down on its tile,
                // landing as the bomb explodes.
                projectile(
                    at,
                    at,
                    kephri_spotanims.throw_second,
                    toa_projanims.lob,
                    flight = FIREBALL_FLIGHT,
                    heights = FIREBALL_HEIGHTS,
                )
            } else {
                val from = FLY_BOMB_SOURCES.minBy { max(abs(it.x - at.x), abs(it.z - at.z)) }
                projectile(from, at, kephri_spotanims.fly_bomb, toa_projanims.lob)
            }
        }
        if (raid.settings.isActive(Inv.AERIAL_ASSAULT)) {
            for (centre in centres) {
                for (dx in -1..1) {
                    for (dz in -1..1) {
                        tiles += centre.translate(dx, dz)
                    }
                }
            }
        }
        for (tile in tiles) {
            graphic(kephri_spotanims.telegraph, tile)
        }
        later(3) {
            for (tile in tiles) {
                graphic(kephri_spotanims.bomb_explode, tile)
                if (!regular) {
                    graphic(kephri_spotanims.fly_explode, tile)
                }
                for (player in challengePlayers()) {
                    if (raid.realOf(player.coords) != tile) {
                        continue
                    }
                    // The fireball's max hit is 24 (wiki); a third through Protect from Magic.
                    var base = maxHit(if (regular) FIREBALL_MAX / 2 else FLY_BOMB_BASE)
                    if (player.vars[varbits.protect_from_magic] == 1) {
                        base /= 3
                    }
                    hurtFlat(player, env.random.of(base, base * 2))
                }
            }
        }
    }

    private fun throwEggs(boss: Npc, players: List<Player>) {
        boss.anim(kephri_seqs.throw_eggs)
        val brown =
            if (raid.settings.isActive(Inv.LIVELY_LARVAE))
                env.random.of(players.size * 3, players.size * 5)
            else players.size * 2
        val tiles = shuffled(EGG_TILES)
        for ((index, tile) in tiles.withIndex()) {
            if (tile in dung) {
                continue
            }
            projectile(
                THROW_SECOND,
                tile,
                if (index < brown) kephri_spotanims.brown_egg else kephri_spotanims.egg,
                toa_projanims.lob,
            )
        }
        later(3) {
            if (!isStarted || challengePlayers().isEmpty()) {
                return@later
            }
            for ((index, tile) in tiles.withIndex()) {
                placeEgg(tile, brown = index < brown)
            }
            eggFuse = EGG_FUSE
        }
    }

    private fun placeEgg(tile: CoordGrid, brown: Boolean) {
        if (tile in dung) {
            return
        }
        val npc =
            spawn(
                if (brown) kephri_npcs.egg_hatching else kephri_npcs.egg,
                tile,
                scale = false,
                points = 0.0,
            )
        npc.anim(kephri_seqs.egg_spawn)
        eggs[npc] = brown
        for (player in challengePlayers()) {
            if (raid.realOf(player.coords) == tile) {
                hurtFlat(player, maxHit(2))
            }
        }
    }

    /** An egg left too long: every egg bursts in a 3x3, and the dark ones hatch an agile scarab. */
    private fun hatch(egg: Npc) {
        val brown = eggs.remove(egg) ?: return
        val at = raid.realOf(egg.coords)
        despawn(egg)
        at ?: return
        graphic(kephri_spotanims.egg_explode, at)
        for (player in challengePlayers()) {
            val there = raid.realOf(player.coords) ?: continue
            if (max(abs(there.x - at.x), abs(there.z - at.z)) <= 1) {
                player.mes("You are damaged by a nearby egg explosion!")
                hurtFlat(player, maxHit(8))
            }
        }
        if (!brown) {
            return
        }
        // Dung on a dark egg kills the scarab the moment it hatches (wiki).
        if (at in dung) {
            return
        }
        val scarab = spawn(kephri_npcs.agile, at, scale = false, points = 0.5)
        agile += scarab
        monster(
            scarab,
            NpcAttack(
                NpcAttackStyle.Ranged,
                anim = kephri_seqs.agile_attack,
                projectile = kephri_spotanims.spit,
                projanim = projanims.arrow,
                maxHit = AGILE_MAX,
            ),
            range = 6,
            speed = 4,
        )
    }

    override fun swing(monster: ToaMonster, target: Player) {
        // Agile scarabs and the soldier do not scale with the raid level (wiki).
        env.attacks.attack(monster.npc, target, monster.attack)
    }

    // Dung
    // ------------------------------------------------------------------------------------------

    private fun prepareDung(boss: Npc, players: List<Player>) {
        attackTicks += 7
        val first = players[playerIndex % players.size]
        val second = players[(playerIndex + 1) % players.size]
        val mud = raid.settings.isActive(Inv.BLOWING_MUD)
        first.spotanim(kephri_spotanims.player_flies)
        if (mud) {
            second.spotanim(kephri_spotanims.player_flies)
        }
        later(4) {
            dungAttack(boss, first, force = false)
            if (mud && second !== first) {
                dungAttack(boss, second, force = false)
            }
        }
        playerIndex += if (mud) 2 else 1
    }

    /** Throws a player away from her and leaves a trail of dung where they flew. */
    private fun dungAttack(boss: Npc, player: Player, force: Boolean) {
        if (isDazed && !force) {
            return
        }
        boss.anim(kephri_seqs.throw_eggs)
        if (player !in challengePlayers()) {
            return
        }
        val at = raid.realOf(player.coords) ?: return
        val dx = at.x - CENTRE.x
        val dz = at.z - CENTRE.z
        var stepX = Integer.signum(dx)
        var stepZ = Integer.signum(dz)
        if (abs(abs(dz) - abs(dx)) > 1) {
            if (abs(dx) > abs(dz)) stepZ = 0 else stepX = 0
        }
        if (stepX == 0 && stepZ == 0) {
            stepX = 1
        }
        var end = at
        repeat((8 - max(abs(dx), abs(dz))).coerceAtLeast(0)) {
            val next = end.translate(stepX, stepZ)
            if (!walkable(next) || !room.inChallenge(next) || next in dung) {
                return@repeat
            }
            end = next
        }
        player.mes("<col=ef0083>Kephri throws you back!</col>")
        player.anim(kephri_seqs.player_thrown)
        player.spotanim(kephri_spotanims.stun, height = 124)
        knockback(player, end, facing = facingToward(end, CENTRE), cycles = 60)
        val start = CoordGrid(CENTRE.x + dx.coerceIn(-2, 2), CENTRE.z + dz.coerceIn(-2, 2), Z)
        val path = line(start, end)
        if (path.isEmpty()) {
            return
        }
        later(2) {
            path.forEachIndexed { index, tile ->
                graphic(kephri_spotanims.dung, tile, delay = index * 10)
            }
        }
        later(4) { path.take(3).forEach(::addDung) }
        later(5) { path.drop(3).forEach(::addDung) }
    }

    private fun facingToward(from: CoordGrid, to: CoordGrid): Int {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return when {
            abs(dx) >= abs(dz) && dx > 0 -> 1536
            abs(dx) >= abs(dz) -> 512
            dz > 0 -> 1024
            else -> 0
        }
    }

    private fun line(from: CoordGrid, to: CoordGrid): List<CoordGrid> {
        val steps = max(abs(to.x - from.x), abs(to.z - from.z))
        return (1..steps).map { i ->
            CoordGrid(
                from.x + ((to.x - from.x) * i.toDouble() / steps).roundToInt(),
                from.z + ((to.z - from.z) * i.toDouble() / steps).roundToInt(),
                Z,
            )
        }
    }

    private fun addDung(tile: CoordGrid) {
        if (!dung.add(tile)) {
            return
        }
        placeLoc(tile, KephriLocs.dung, LocShape.CentrepieceStraight, LocAngle[env.random.of(4)])
    }

    /** Standing in dung hurts and shoves you off it; agile scarabs drown in it. */
    private fun tickDung(players: List<Player>) {
        if (dung.isEmpty()) {
            return
        }
        for (player in players) {
            val at = raid.realOf(player.coords) ?: continue
            if (at !in dung) {
                continue
            }
            hurtFlat(player, maxHit(3))
            val free = freeTileNear(at)
            if (free == null) {
                // Nowhere to go: the dung keeps hurting (wiki: a quick death).
                hurtFlat(player, maxHit(10))
                continue
            }
            player.say("Ouch!")
            player.anim(kephri_seqs.player_slide)
            knockback(player, free, cycles = 29)
        }
        for (npc in agile.toList()) {
            if (raid.realOf(npc.coords) in dung) {
                npc.hitpoints = 0
                npc.queueDeath()
            }
        }
    }

    private fun freeTileNear(at: CoordGrid): CoordGrid? {
        for (radius in 1..10) {
            for (x in -radius..radius) {
                for (z in -radius..radius) {
                    if (abs(x) != radius && abs(z) != radius) {
                        continue
                    }
                    val tile = at.translate(x, z)
                    if (walkable(tile) && room.inChallenge(tile) && tile !in dung) {
                        return tile
                    }
                }
            }
        }
        return null
    }

    // Swarms and overlords
    // ------------------------------------------------------------------------------------------

    private fun spawnSwarm(medic: Boolean) {
        val base = SWARM_SPAWNS[swarmIndex]
        val alongX = swarmIndex in SWARM_ALONG_X
        fun offset(delta: Int) = if (alongX) base.translate(delta, 0) else base.translate(0, delta)
        // From path level 2 a trio comes now and then (wiki).
        if (
            !medic &&
                pathLevel > 1 &&
                ++tripleSwarmCycle >=
                    if (pathLevel > 3) SWARM_SPAWNS.size / 2 else SWARM_SPAWNS.size
        ) {
            tripleSwarmCycle = 0
            for (i in 0 until 3) {
                addSwarm(offset(i), medic)
            }
        } else {
            addSwarm(offset(env.random.of(4)), medic)
        }
        swarmIndex = (swarmIndex + env.random.of(1, 2)) % SWARM_SPAWNS.size
    }

    private fun addSwarm(tile: CoordGrid, medic: Boolean) {
        val npc = spawn(kephri_npcs.swarm, tile, scale = false, points = 0.5, passThrough = true)
        npc.anim(kephri_seqs.swarm_spawn)
        swarms[npc] = Swarm(npc, medic)
    }

    /** Swarms crawl to her; one that reaches her while she is dazed restores her (wiki: ~10%). */
    private fun tickSwarms(boss: Npc) {
        for (swarm in swarms.values.toList()) {
            if (!swarm.npc.isSlotAssigned || swarm.fading) {
                continue
            }
            if (swarm.delay > 0) {
                swarm.delay--
                continue
            }
            val at = raid.realOf(swarm.npc.coords) ?: continue
            if (max(abs(at.x - CENTRE.x), abs(at.z - CENTRE.z)) <= 3) {
                if (isDazed) {
                    val low = if (swarm.medic) 0.18 else 0.08
                    val high = if (swarm.medic) 0.20 else 0.10
                    heal(boss, low + (high - low) * env.random.of(101) / 100.0)
                }
                fade(swarm)
                continue
            }
            stepToward(swarm.npc, raid.regionOf(CENTRE), keepOff = false)
        }
    }

    private fun fade(swarm: Swarm) {
        if (swarm.fading || !swarm.npc.isSlotAssigned) {
            return
        }
        swarm.fading = true
        swarm.npc.resetMovement()
        swarm.npc.anim(kephri_seqs.swarm_fade)
        later(ticksOf(kephri_seqs.swarm_fade, least = 2)) {
            swarms.remove(swarm.npc)
            despawn(swarm.npc)
        }
    }

    private fun spawnOverlord(kind: Overlord) {
        if (!isStarted || kephri == null) {
            return
        }
        val (type, tile) =
            when (kind) {
                Overlord.Soldier -> kephri_npcs.soldier to SOLDIER_SPAWN
                Overlord.Spitter -> kephri_npcs.spitter to SPITTER_SPAWN
                Overlord.Arcane -> kephri_npcs.arcane to ARCANE_TILES[0]
            }
        val npc = spawn(type, tile, points = 0.5)
        val state = OverlordState(kind)
        overlords[npc] = state
        npc.anim(if (kind == Overlord.Arcane) kephri_seqs.arcane_down else kephri_seqs.scarab_spawn)
        if (kind == Overlord.Soldier) {
            monster(
                npc,
                NpcAttack(
                    NpcAttackStyle.Melee,
                    anim = kephri_seqs.scarab_attack,
                    maxHit = SOLDIER_MAX,
                ),
                range = 1,
                speed = 6,
            )
        }
    }

    private fun tickOverlords(boss: Npc, players: List<Player>) {
        for ((npc, state) in overlords.toList()) {
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                continue
            }
            when (state.kind) {
                Overlord.Soldier -> tickSoldier(boss, npc, state)
                Overlord.Spitter -> tickSpitter(npc, state, players)
                Overlord.Arcane -> tickArcane(npc, state, players)
            }
        }
    }

    /** The soldier sends a swarm to her every 5 seconds or so, 5-7 times, scaled by its health. */
    private fun tickSoldier(boss: Npc, npc: Npc, state: OverlordState) {
        if (state.heals >= SOLDIER_HEALS || state.healTicks <= 0) {
            return
        }
        if (--state.healTicks == 1) {
            projectile(
                raid.realOf(npc.coords) ?: return,
                CENTRE,
                kephri_spotanims.heal,
                toa_projanims.splash,
            )
        } else if (state.healTicks <= 0) {
            state.healTicks = SOLDIER_HEAL_TICKS
            state.heals++
            val share = npc.hitpoints.toDouble() / npc.baseHitpointsLvl.coerceAtLeast(1)
            heal(boss, share * (0.05 + 0.02 * env.random.of(101) / 100.0))
        }
    }

    /** The spitter spits at everyone within 8 tiles every 3 ticks, through prayer, and poisons. */
    private fun tickSpitter(npc: Npc, state: OverlordState, players: List<Player>) {
        if (--state.attackTicks > 0) {
            return
        }
        state.attackTicks = 3
        npc.anim(kephri_seqs.spit)
        for (player in players) {
            if (gap(npc, player.coords) > SPITTER_RANGE) {
                continue
            }
            val at = raid.realOf(player.coords) ?: continue
            val from = raid.realOf(npc.coords) ?: continue
            projectile(from, at, kephri_spotanims.spit, projanims.arrow)
            later(2) {
                if (player !in challengePlayers()) {
                    return@later
                }
                var damage = env.random.of(0, maxHit(SPITTER_MAX))
                if (player.vars[varbits.protect_from_missiles] == 1) {
                    damage = damage / 5
                }
                hurtFlat(player, damage)
                env.poison.poison(player, SPITTER_POISON)
            }
        }
    }

    /** The arcane scarab charges a blast at everyone; hits make it fly off and land elsewhere. */
    private fun tickArcane(npc: Npc, state: OverlordState, players: List<Player>) {
        if (state.flying > 0) {
            if (--state.flying == 0) {
                npc.anim(kephri_seqs.arcane_down)
                npc.telejump(
                    env.collision,
                    raid.regionOf(ARCANE_TILES[state.tileIndex++ % ARCANE_TILES.size]),
                )
                state.chargeDelay = 5
            }
            return
        }
        if (state.chargeDelay <= 0 || --state.chargeDelay > 0) {
            return
        }
        state.chargeDelay = 3
        state.charges++
        val sw = raid.realOf(npc.coords) ?: return
        if (state.charges >= ARCANE_CHARGES) {
            val origin = sw.translate(1, 1)
            for (player in players) {
                val at = raid.realOf(player.coords) ?: continue
                projectile(origin, at, kephri_spotanims.arcane_charge, toa_projanims.orb)
                later(3) {
                    if (player !in challengePlayers()) {
                        return@later
                    }
                    val base = maxHit(ARCANE_MAX)
                    var damage = env.random.of(base / 2, base)
                    if (player.vars[varbits.protect_from_magic] == 1) {
                        damage = damage * 33 / 100
                    }
                    hurtFlat(player, damage)
                }
            }
            arcaneFlyAway(npc, state)
            return
        }
        for (i in 0 until minOf(state.charges, ARCANE_RING.size)) {
            graphic(
                kephri_spotanims.arcane_charge,
                sw.translate(ARCANE_RING[i].first, ARCANE_RING[i].second),
            )
        }
    }

    private fun arcaneFlyAway(npc: Npc, state: OverlordState) {
        npc.anim(kephri_seqs.arcane_up)
        state.charges = 0
        state.flying = 2
        state.moderateHits = 0
    }

    companion object {
        const val Z = 0
        val SPAWN = CoordGrid(3549, 5406, Z)
        val CENTRE = CoordGrid(3551, 5408, Z)
        val THROW_START = CoordGrid(3550, 5409, Z)
        val THROW_SECOND = CoordGrid(3551, 5408, Z)

        /** A falling fireball: from far above the tile to the floor, landing 3 ticks later. */
        val FIREBALL_HEIGHTS = 250 to 0
        val FIREBALL_FLIGHT = 0..90

        /**
         * The six swarm spawn points, clockwise from the north: N, NE, SE, S, SW, NW (wiki). Each
         * is the first of four tiles along its edge. The east and west ones are TSPS's tiles; the
         * north and south ones sit midway between TSPS's two on those edges.
         */
        val SWARM_SPAWNS =
            listOf(
                CoordGrid(3549, 5416, Z),
                CoordGrid(3559, 5410, Z),
                CoordGrid(3559, 5404, Z),
                CoordGrid(3549, 5400, Z),
                CoordGrid(3543, 5404, Z),
                CoordGrid(3543, 5410, Z),
            )

        /** Spawn points on the north and south edges, whose four tiles run east-west. */
        val SWARM_ALONG_X = setOf(0, 3)
        val EGG_TILES =
            listOf(
                CoordGrid(3551, 5404, Z),
                CoordGrid(3549, 5402, Z),
                CoordGrid(3545, 5406, Z),
                CoordGrid(3551, 5412, Z),
                CoordGrid(3549, 5414, Z),
                CoordGrid(3545, 5410, Z),
                CoordGrid(3547, 5408, Z),
                CoordGrid(3553, 5402, Z),
                CoordGrid(3557, 5406, Z),
                CoordGrid(3553, 5414, Z),
                CoordGrid(3555, 5408, Z),
                CoordGrid(3557, 5410, Z),
            )
        val FLY_BOMB_SOURCES =
            listOf(
                CoordGrid(3542, 5417, Z),
                CoordGrid(3560, 5417, Z),
                CoordGrid(3560, 5398, Z),
                CoordGrid(3542, 5398, Z),
            )
        val SOLDIER_SPAWN = CoordGrid(3556, 5407, Z)
        val SPITTER_SPAWN = CoordGrid(3556, 5413, Z)

        /**
         * Where the arcane scarab lands, in order: south-east, north-west, mid-south, north-east.
         */
        val ARCANE_TILES =
            listOf(
                CoordGrid(3556, 5401, Z),
                CoordGrid(3544, 5413, Z),
                CoordGrid(3550, 5401, Z),
                CoordGrid(3556, 5413, Z),
            )
        val ARCANE_RING = listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 2 to 2, 2 to 1, 2 to 0, 1 to 0)

        const val FINAL_HITPOINTS = 80
        const val ATTACK_SPEED = 6
        const val FIREBALL_MAX = 24
        const val FLY_BOMB_BASE = 5
        const val FLY_BOMB_TICKS = 12
        const val EGG_FUSE = 16
        const val HEAL_CAP = 1.15
        const val FIRST_SWARMS = 28
        const val SWARM_GRACE = 20
        const val SECOND_SWARMS = 24
        const val MEDIC_TICKS = 12
        const val AGILE_MAX = 5
        const val AGILE_FLEE_RANGE = 2
        const val SHIELD_ALIVE_TICKS = 6
        const val SHIELD_DEAD_TICKS = 3
        const val SHIELD_BREAK_TICKS = 6
        const val SOLDIER_MAX = 17
        const val SOLDIER_HEALS = 7
        const val SOLDIER_HEAL_TICKS = 8
        const val SPITTER_MAX = 10
        const val SPITTER_RANGE = 8
        const val SPITTER_POISON = 6
        const val ARCANE_CHARGES = 9
        const val ARCANE_MAX = 65
        const val ARCANE_HEAVY = 40
        const val ARCANE_MODERATE = 15
    }
}
