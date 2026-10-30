package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** The three combat styles, for what the Hunllef protects against and what it attacks with. */
enum class HunllefStyle {
    Melee,
    Ranged,
    Magic;

    /** The hit type of a player's hit of this style. */
    val hitType: HitType
        get() =
            when (this) {
                Melee -> HitType.Melee
                Ranged -> HitType.Ranged
                Magic -> HitType.Magic
            }

    companion object {
        fun of(type: HitType): HunllefStyle? =
            when (type) {
                HitType.Melee -> Melee
                HitType.Ranged -> Ranged
                HitType.Magic -> Magic
                else -> null
            }
    }
}

/** One tile of the floor pattern, from blue to orange to plain again. */
class HunllefFloorTile(val x: Int, val z: Int, val orangeAt: Int, val clearAt: Int) {
    var orange: Boolean = false
}

/** A tornado: it chases a player for [GauntletHunllef.TORNADO_TICKS] ticks. */
class HunllefTornado(val npc: Npc, var left: Int, var target: Player?)

/**
 * The Hunllef's fight in one run: its form, the attack cycle, its tornadoes and the floor. Lives on
 * the run ([GauntletRun.hunllef]); [GauntletHunllef] drives it.
 */
class HunllefFight(val npc: Npc, val maxHitpoints: Int, var protecting: HunllefStyle) {
    var style: HunllefStyle = HunllefStyle.Ranged
    var started: Boolean = false
    var ticks: Int = 0
    var nextAttack: Int = 0
    var nextTornado: Int = 0
    var nextPattern: Int = 0
    var switchAt: Int = -1

    /** Attacks made since the start; every fourth changes the style. */
    var attacks: Int = 0

    /** Hits that were not of the style it protects against, since it last changed. */
    var offPrayerHits: Int = 0

    /** Who it attacked last, to alternate between the players. */
    var lastTarget: Int = -1

    val tornadoes: MutableList<HunllefTornado> = ArrayList()
    val floor: MutableMap<Pair<Int, Int>, HunllefFloorTile> = HashMap()

    /** Hits of a style it protected against, for the Combat Achievements nobody counts yet. */
    var wrongStyleHits: Int = 0

    /** The third of its hitpoints it is in: 0 above two thirds, 1 above one third, 2 below. */
    fun phase(): Int {
        val left = npc.hitpoints.toDouble() / maxHitpoints
        return when {
            left > 2.0 / 3.0 -> 0
            left > 1.0 / 3.0 -> 1
            else -> 2
        }
    }
}

/**
 * The Crystalline and Corrupted Hunllef (wiki, both share the mechanics):
 * - 600 / 1000 hitpoints, an attack every 5 ticks; Ranged first, switching between Ranged and Magic
 *   every 4 attacks (a tornado or a prayer-disabling attack counts, the stomp does not), with an
 *   animation as it switches;
 * - it protects against one style; every 6th hit that is not of that style, zero hits included,
 *   switches it to that hit's style. Hits of the style it protects against do nothing;
 * - a stomp for anyone standing under it when it attacks;
 * - tornadoes that chase a player for 20 ticks: Crystalline 1 / 2 / 3, Corrupted 2 / 3 / 4, above
 *   66% / 33-66% / below 33% of its hitpoints;
 * - floor tiles that turn blue (Corrupted: crimson) then orange; an orange tile hurts 10-20 a tick.
 *   Three sets of patterns by those thirds, turning orange faster each time.
 *
 * Max hits by the lowest tier of a full crystal set (none / basic / attuned / perfected), wiki:
 * Crystalline with the right prayer 12 / 10 / 8 / 6; Corrupted 16 / 13 / 10 / 8 with it and 68 / 55
 * / 45 / 35 without. The wiki gives no Crystalline maxes without prayer: they are the Corrupted
 * ratio per tier (51 / 42 / 36 / 26), and its stomp the unprotected, unarmoured max.
 *
 * Near-Reality's (the wiki gives none): the animations and projectiles, a tornado summon every 56
 * ticks, a floor pattern every 30, orange for 6, and a 15% chance for a magic attack to disable
 * prayers. In a duo run it alternates its attacks between the players still standing in the arena;
 * the style cycle, the protection and the thirds are shared.
 */
class GauntletHunllef
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val collision: CollisionFlagMap,
    private val attacks: NpcAttacks,
    private val interactions: AiPlayerInteractions,
) {
    // The fight ---------------------------------------------------------------------------------

    /** Puts the Hunllef in its room, waiting. */
    fun spawn(run: GauntletRun, pick: (Int) -> Int): HunllefFight {
        val protecting = HunllefStyle.entries[pick(HunllefStyle.entries.size)]
        val room = run.layout.boss
        val npc = Npc(npcTypes[form(run.mode, protecting)], run.roomTile(room, SPAWN_X, SPAWN_Z))
        npc.mode = NpcMode.None
        val hitpoints = (npc.hitpoints * HITPOINTS_MULTIPLIER).toInt().coerceAtLeast(1)
        npc.hitpoints = hitpoints
        npcRepo.add(npc, GauntletMonsters.LIFETIME)
        showProtection(npc, protecting)
        val fight = HunllefFight(npc, hitpoints, protecting)
        run.hunllef = fight
        return fight
    }

    /** The fight begins: the Hunllef starts attacking. */
    fun start(run: GauntletRun) {
        val fight = run.hunllef ?: return
        if (fight.started) {
            return
        }
        fight.started = true
        fight.nextAttack = FIRST_ATTACK_TICKS
        fight.nextTornado = TORNADO_EVERY
        fight.nextPattern = FIRST_PATTERN_TICKS
    }

    /** Takes the Hunllef, its tornadoes and the floor out of the run. */
    fun clear(run: GauntletRun) {
        val fight = run.hunllef ?: return
        for (tornado in fight.tornadoes) {
            remove(tornado.npc)
        }
        fight.tornadoes.clear()
        for ((_, tile) in fight.floor.toList()) {
            setFloor(run, tile.x, tile.z, FloorLook.Plain)
        }
        fight.floor.clear()
        remove(fight.npc)
        run.hunllef = null
    }

    /** Takes the tornadoes and the floor away, leaving the Hunllef (it is dying). */
    fun clearExtras(run: GauntletRun) {
        val fight = run.hunllef ?: return
        for (tornado in fight.tornadoes) {
            remove(tornado.npc)
        }
        fight.tornadoes.clear()
        for ((_, tile) in fight.floor.toList()) {
            setFloor(run, tile.x, tile.z, FloorLook.Plain)
        }
        fight.floor.clear()
        fight.started = false
    }

    private fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    /** One tick of the fight. */
    fun tick(run: GauntletRun, pick: (Int) -> Int) {
        val fight = run.hunllef ?: return
        if (!fight.started || fight.npc.hitpoints <= 0) {
            return
        }
        val players = inArena(run)
        if (players.isEmpty()) {
            return
        }
        fight.ticks++
        approach(fight, players)
        tickTornadoes(run, fight, players, pick)
        tickFloor(run, fight, players, pick)
        if (fight.ticks == fight.nextPattern) {
            startPattern(run, fight, pick)
        }
        if (fight.ticks == fight.switchAt) {
            fight.npc.anim(gauntlet_seqs.hunllef_transition)
        }
        if (fight.ticks >= fight.nextAttack) {
            attack(run, fight, players, pick)
        }
    }

    /** The players that are alive and standing in the Hunllef's room. */
    private fun inArena(run: GauntletRun): List<Player> =
        run.members.filter {
            it.isValidTarget() && it.hitpoints > 0 && run.roomAt(it.coords) === run.layout.boss
        }

    /** The room's tile at [x], [z] of the 12 by 12 floor. */
    fun arenaTile(run: GauntletRun, x: Int, z: Int): CoordGrid =
        run.roomTile(run.layout.boss, ARENA_MIN + x, ARENA_MIN + z)

    // Moving and attacking ----------------------------------------------------------------------

    /** It closes in on the nearest player until it is within [ATTACK_DISTANCE] tiles of them. */
    private fun approach(fight: HunllefFight, players: List<Player>) {
        val nearest = players.minBy { it.distanceTo(fight.npc) }
        fight.npc.apPlayer2(nearest, interactions)
    }

    private fun underneath(fight: HunllefFight, player: Player): Boolean {
        val at = fight.npc.coords
        val x = player.coords.x - at.x
        val z = player.coords.z - at.z
        return x in 0 until SIZE && z in 0 until SIZE
    }

    private fun attack(
        run: GauntletRun,
        fight: HunllefFight,
        players: List<Player>,
        pick: (Int) -> Int,
    ) {
        fight.nextAttack = fight.ticks + ATTACK_SPEED
        val trampled = players.filter { underneath(fight, it) }
        if (trampled.isNotEmpty()) {
            // The stomp is not one of the four attacks before a switch.
            fight.npc.anim(gauntlet_seqs.hunllef_stomp)
            for (player in trampled) {
                val amount = pick(maxHits(run.mode).stomp + 1)
                player.queueHit(fight.npc, 1, HitType.Typeless, amount)
                player.mes(
                    "You're trampled beneath the ${if (run.mode.corrupted) "Corrupted" else "Crystalline"} Hunllef."
                )
            }
            return
        }
        val target = nextTarget(fight, players)
        if (fight.ticks >= fight.nextTornado) {
            summonTornadoes(run, fight, players, pick)
        } else {
            standardAttack(run, fight, target, pick)
        }
        fight.attacks++
        if (fight.attacks % ATTACKS_PER_STYLE == 0) {
            fight.style =
                if (fight.style == HunllefStyle.Ranged) HunllefStyle.Magic else HunllefStyle.Ranged
            fight.switchAt = fight.ticks + 1
        }
    }

    /** The next player in turn, so a pair take it in turns. */
    private fun nextTarget(fight: HunllefFight, players: List<Player>): Player {
        val ordered = players.sortedBy { it.accountId }
        val next = ordered.firstOrNull { it.accountId > fight.lastTarget } ?: ordered.first()
        fight.lastTarget = next.accountId
        return next
    }

    private fun standardAttack(
        run: GauntletRun,
        fight: HunllefFight,
        target: Player,
        pick: (Int) -> Int,
    ) {
        val mode = run.mode
        val style = fight.style
        val disable = style == HunllefStyle.Magic && pick(100) < DISABLE_PRAYER_PERCENT
        val tier = armourTier(target, mode)
        val table = maxHits(mode)
        val unprayed = table.unprayed[tier]
        val prayed = table.prayed[tier]
        val corrupted = mode.corrupted
        val projectile =
            when {
                disable ->
                    if (corrupted) gauntlet_spotanims.hunllef_prayer_hm
                    else gauntlet_spotanims.hunllef_prayer
                style == HunllefStyle.Magic ->
                    if (corrupted) gauntlet_spotanims.hunllef_magic_hm
                    else gauntlet_spotanims.hunllef_magic
                else ->
                    if (corrupted) gauntlet_spotanims.hunllef_range_hm
                    else gauntlet_spotanims.hunllef_range
            }
        val impact =
            when {
                disable ->
                    if (corrupted) gauntlet_spotanims.hunllef_prayer_impact_hm
                    else gauntlet_spotanims.hunllef_prayer_impact
                style == HunllefStyle.Magic ->
                    if (corrupted) gauntlet_spotanims.hunllef_magic_impact_hm
                    else gauntlet_spotanims.hunllef_magic_impact
                else -> null
            }
        val attack =
            NpcAttack(
                style =
                    if (style == HunllefStyle.Magic) NpcAttackStyle.Magic
                    else NpcAttackStyle.Ranged,
                anim = gauntlet_seqs.hunllef_ranged,
                projectile = projectile,
                projanim =
                    if (style == HunllefStyle.Magic) projanims.magic_spell else projanims.arrow,
                impactSpot = impact,
                maxHit = unprayed,
                alwaysHits = true,
                // The prayer is read when the hit lands, below, not by the engine's own rule.
                typeless = true,
            )
        val listener = NpcImpactListener { player, hit ->
            if (protects(player, style)) {
                hit.damage = if (unprayed > 0) hit.damage * prayed / unprayed else 0
            }
            if (disable) {
                player.disablePrayers()
                player.mes("<col=ff0000>Your prayers have been disabled!</col>")
            }
        }
        attacks.attack(fight.npc, target, attack, onImpact = listener)
    }

    private fun protects(player: Player, style: HunllefStyle): Boolean =
        when (style) {
            HunllefStyle.Melee -> player.vars[varbits.protect_from_melee] == 1
            HunllefStyle.Ranged -> player.vars[varbits.protect_from_missiles] == 1
            HunllefStyle.Magic -> player.vars[varbits.protect_from_magic] == 1
        }

    // Tornadoes ---------------------------------------------------------------------------------

    private fun summonTornadoes(
        run: GauntletRun,
        fight: HunllefFight,
        players: List<Player>,
        pick: (Int) -> Int,
    ) {
        fight.npc.anim(gauntlet_seqs.hunllef_special)
        fight.nextTornado = fight.ticks + TORNADO_EVERY
        val count = TORNADOES[if (run.mode.corrupted) 1 else 0][fight.phase()]
        repeat(count) {
            var at: CoordGrid
            do {
                at = arenaTile(run, pick(ARENA_SIZE), pick(ARENA_SIZE))
            } while (players.any { it.coords == at })
            val type = if (run.mode.corrupted) gauntlet_npcs.tornado_hm else gauntlet_npcs.tornado
            val npc = Npc(npcTypes[type], at)
            npc.mode = NpcMode.None
            npcRepo.add(npc, GauntletMonsters.LIFETIME)
            fight.tornadoes += HunllefTornado(npc, TORNADO_TICKS, players[pick(players.size)])
        }
    }

    private fun tickTornadoes(
        run: GauntletRun,
        fight: HunllefFight,
        players: List<Player>,
        pick: (Int) -> Int,
    ) {
        for (tornado in fight.tornadoes.toList()) {
            if (--tornado.left < 0 || !tornado.npc.isSlotAssigned) {
                fight.tornadoes.remove(tornado)
                remove(tornado.npc)
                continue
            }
            var target = tornado.target
            if (target == null || target !in players) {
                target = players.minBy { it.distanceTo(tornado.npc) }
                tornado.target = target
            }
            if (tornado.npc.coords == target.coords) {
                val (min, max) =
                    TORNADO_DAMAGE[if (run.mode.corrupted) 1 else 0][armourTier(target, run.mode)]
                target.queueHit(tornado.npc, 1, HitType.Typeless, min + pick(max - min + 1))
                target.spotanim(gauntlet_spotanims.tornado_hit)
            }
            step(tornado.npc, target.coords)
        }
    }

    /**
     * One step toward a tile, ignoring walls and other people (tornadoes cross the arena freely).
     */
    private fun step(npc: Npc, to: CoordGrid) {
        val at = npc.coords
        val dx = Integer.signum(to.x - at.x)
        val dz = Integer.signum(to.z - at.z)
        if (dx == 0 && dz == 0) {
            return
        }
        PathingEntityCommon.teleport(npc, collision, CoordGrid(at.x + dx, at.z + dz, at.level))
    }

    // The floor ---------------------------------------------------------------------------------

    private fun startPattern(run: GauntletRun, fight: HunllefFight, pick: (Int) -> Int) {
        val phase = fight.phase()
        val patterns = PATTERNS[phase]
        val pattern = patterns[pick(patterns.size)]
        fight.nextPattern = fight.ticks + PATTERN_EVERY
        val orangeAt = fight.ticks + BLUE_TICKS[phase]
        for ((x, z) in pattern) {
            if ((x to z) in fight.floor) {
                continue
            }
            fight.floor[x to z] = HunllefFloorTile(x, z, orangeAt, orangeAt + ORANGE_TICKS)
            setFloor(run, x, z, FloorLook.Warning)
        }
    }

    private fun tickFloor(
        run: GauntletRun,
        fight: HunllefFight,
        players: List<Player>,
        pick: (Int) -> Int,
    ) {
        for ((key, tile) in fight.floor.toList()) {
            if (fight.ticks >= tile.clearAt) {
                fight.floor.remove(key)
                setFloor(run, tile.x, tile.z, FloorLook.Plain)
                continue
            }
            if (!tile.orange && fight.ticks >= tile.orangeAt) {
                tile.orange = true
                setFloor(run, tile.x, tile.z, FloorLook.Hit)
            }
            if (tile.orange) {
                val at = arenaTile(run, tile.x, tile.z)
                for (player in players.filter { it.coords == at }) {
                    val amount = FLOOR_MIN + pick(FLOOR_MAX - FLOOR_MIN + 1)
                    player.queueHit(fight.npc, 1, HitType.Typeless, amount)
                }
            }
        }
    }

    private enum class FloorLook {
        Plain,
        Warning,
        Hit,
    }

    /** Changes how the floor tile at arena [x], [z] looks: plain, blue (crimson) or orange. */
    private fun setFloor(run: GauntletRun, x: Int, z: Int, look: FloorLook) {
        val at = arenaTile(run, x, z)
        val corrupted = run.mode.corrupted
        val types =
            if (corrupted) {
                listOf(
                    gauntlet_locs.floor_hm,
                    gauntlet_locs.floor_warning_hm,
                    gauntlet_locs.floor_hit_hm,
                )
            } else {
                listOf(gauntlet_locs.floor, gauntlet_locs.floor_warning, gauntlet_locs.floor_hit)
            }
        val ids = types.map { it.id }
        val current = locRepo.findAll(at).firstOrNull { it.id in ids } ?: return
        val wanted = types[look.ordinal]
        if (current.id != wanted.id) {
            locRepo.change(current, locTypes[wanted], Int.MAX_VALUE)
        }
    }

    // The players' hits -------------------------------------------------------------------------

    /**
     * A hit of [style] on the Hunllef: nothing if it protects against that style, otherwise it
     * counts, and every sixth switches what it protects against to this style. Returns `true` if
     * the hit must do nothing.
     */
    fun onHit(run: GauntletRun, style: HunllefStyle?): Boolean {
        val fight = run.hunllef ?: return false
        if (style == null) {
            return false
        }
        if (style == fight.protecting) {
            fight.wrongStyleHits++
            return true
        }
        fight.offPrayerHits++
        if (fight.offPrayerHits >= OFF_PRAYER_HITS_PER_SWITCH) {
            fight.offPrayerHits = 0
            fight.protecting = style
            fight.npc.transmog(npcTypes[form(run.mode, style)], Int.MAX_VALUE)
            showProtection(fight.npc, style)
        }
        return false
    }

    /**
     * Shows the overhead prayer icon of the style it protects against, as a player's own overhead
     * does: the melee, missiles and magic icons are the first three of the prayer head icons.
     */
    private fun showProtection(npc: Npc, style: HunllefStyle) {
        npc.infoProtocol.setHeadIcon(0, HEAD_ICONS_PRAYER, style.ordinal)
    }

    // Armour and numbers ------------------------------------------------------------------------

    /**
     * The lowest tier of a full crystal set worn: 0 none (or not a full set), 1-3 basic-perfected.
     */
    fun armourTier(player: Player, mode: GauntletMode): Int {
        val items = mode.items
        var tier = 3
        for ((slot, pieces) in
            listOf(
                Wearpos.Hat to items.helm,
                Wearpos.Torso to items.body,
                Wearpos.Legs to items.legs,
            )) {
            val worn = pieces.indexOfFirst { player.worn[slot.slot].isType(it) }
            if (worn < 0) {
                return 0
            }
            tier = minOf(tier, worn + 1)
        }
        return tier
    }

    class MaxHits(val prayed: List<Int>, val unprayed: List<Int>, val stomp: Int)

    fun maxHits(mode: GauntletMode): MaxHits =
        if (mode.corrupted) CORRUPTED_HITS else CRYSTALLINE_HITS

    fun form(mode: GauntletMode, style: HunllefStyle): NpcType =
        when (style) {
            HunllefStyle.Melee ->
                if (mode.corrupted) gauntlet_npcs.hunllef_melee_hm else gauntlet_npcs.hunllef_melee
            HunllefStyle.Ranged ->
                if (mode.corrupted) gauntlet_npcs.hunllef_ranged_hm
                else gauntlet_npcs.hunllef_ranged
            HunllefStyle.Magic ->
                if (mode.corrupted) gauntlet_npcs.hunllef_magic_hm else gauntlet_npcs.hunllef_magic
        }

    companion object {
        /**
         * Multiplies the Hunllef's hitpoints (600 Crystalline, 1000 Corrupted) for the duo run: the
         * one number to change after the first client test. Anything above 1 makes its health bar
         * read full for longer than it should.
         */
        const val HITPOINTS_MULTIPLIER = 1.0

        val CRYSTALLINE_HITS =
            MaxHits(prayed = listOf(12, 10, 8, 6), unprayed = listOf(51, 42, 36, 26), stomp = 51)
        val CORRUPTED_HITS =
            MaxHits(prayed = listOf(16, 13, 10, 8), unprayed = listOf(68, 55, 45, 35), stomp = 68)

        /** Per tier of armour (none, basic, attuned, perfected): the damage range of a tornado. */
        val TORNADO_DAMAGE =
            listOf(
                listOf(10 to 20, 10 to 16, 7 to 13, 5 to 10),
                listOf(15 to 30, 15 to 25, 10 to 20, 7 to 15),
            )

        /** Tornadoes in each third of its hitpoints: Crystalline then Corrupted. */
        val TORNADOES = listOf(listOf(1, 2, 3), listOf(2, 3, 4))

        /** The `headicons_prayer` sprite group: melee, missiles, magic in that order. */
        const val HEAD_ICONS_PRAYER = 440

        const val ATTACK_SPEED = 5
        const val ATTACKS_PER_STYLE = 4
        const val OFF_PRAYER_HITS_PER_SWITCH = 6
        const val FIRST_ATTACK_TICKS = 2
        const val TORNADO_EVERY = 56
        const val TORNADO_TICKS = 20
        const val PATTERN_EVERY = 30
        const val FIRST_PATTERN_TICKS = 4
        val BLUE_TICKS = listOf(5, 4, 3)
        const val ORANGE_TICKS = 6
        const val FLOOR_MIN = 10
        const val FLOOR_MAX = 20
        const val DISABLE_PRAYER_PERCENT = 15

        /** From the boss room's corner: the arena floor, and where the Hunllef stands. */
        const val ARENA_MIN = 2
        const val ARENA_SIZE = 12
        const val SPAWN_X = 6
        const val SPAWN_Z = 7
        const val SIZE = 5

        /** It closes in until a player is this near, then holds (Near-Reality's). */
        const val ATTACK_DISTANCE = 5

        private fun square(size: Int, x: Int, z: Int): List<Pair<Int, Int>> =
            (0 until size).flatMap { i -> (0 until size).map { j -> (x + i) to (z + j) } }

        private fun rectangle(width: Int, height: Int, x: Int, z: Int): List<Pair<Int, Int>> =
            (0 until width).flatMap { i -> (0 until height).map { j -> (x + i) to (z + j) } }

        private val EASY =
            listOf(
                square(6, 0, 0),
                square(6, 6, 0),
                square(6, 0, 6),
                square(6, 6, 6),
                square(6, 2, 2),
                square(6, 4, 2),
                square(6, 2, 4),
                square(6, 4, 4),
                rectangle(4, 12, 0, 0),
                rectangle(4, 12, 8, 0),
                rectangle(12, 4, 0, 0),
                rectangle(12, 4, 0, 8),
            )
        private val MEDIUM =
            EASY +
                listOf(
                    rectangle(4, 12, 0, 0) + rectangle(4, 12, 8, 0),
                    rectangle(12, 4, 0, 0) + rectangle(12, 4, 0, 8),
                )
        private val HARD =
            listOf(
                square(3, 0, 0) +
                    square(3, 9, 0) +
                    square(3, 0, 9) +
                    square(3, 9, 9) +
                    square(4, 4, 4),
                rectangle(2, 12, 0, 0) +
                    rectangle(2, 12, 10, 0) +
                    rectangle(8, 2, 2, 0) +
                    rectangle(8, 2, 2, 10),
                square(4, 1, 1) + square(4, 7, 1) + square(4, 1, 7) + square(4, 7, 7),
                square(4, 0, 0) + square(4, 8, 0) + square(4, 0, 8) + square(4, 8, 8),
            )

        /** Near-Reality's three sets of floor patterns, as arena offsets, by third of hitpoints. */
        val PATTERNS: List<List<List<Pair<Int, Int>>>> = listOf(EASY, MEDIUM, HARD)
    }
}
