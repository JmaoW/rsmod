package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.stat.stat
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

enum class NyloStyle(val code: String, val attackStyle: NpcAttackStyle, val hitType: HitType) {
    Melee("me", NpcAttackStyle.Melee, HitType.Melee),
    Ranged("ra", NpcAttackStyle.Ranged, HitType.Ranged),
    Magic("ma", NpcAttackStyle.Magic, HitType.Magic);

    companion object {
        fun of(code: String): NyloStyle = entries.first { it.code == code.lowercase() }
    }
}

object NylocasNpcs : NpcReferences() {
    private fun styles(prefix: String) =
        mapOf(
            NyloStyle.Melee to find("${prefix}_melee"),
            NyloStyle.Ranged to find("${prefix}_ranged"),
            NyloStyle.Magic to find("${prefix}_magic"),
        )

    val small = styles("tob_nylocas_incoming")
    val big = styles("tob_nylocas_big_incoming")
    val small_aggressive = styles("tob_nylocas_fighting")
    val big_aggressive = styles("tob_nylocas_big_fighting")
    val vasilias = styles("nylocas_boss")
    val vasilias_spawning = find("nylocas_boss_spawning")
    val support = find("tob_nylocas_support")

    val all: List<NpcType>
        get() = small.values + big.values + small_aggressive.values + big_aggressive.values
}

object NylocasLocs : LocReferences() {
    val support = find("tob_nylocas_support_pristine")
    val support_collapsing = find("tob_nylocas_support_collapsing")
    val support_broken = find("tob_nylocas_support_dead")
}

object NylocasSeqs : SeqReferences() {
    val attack =
        mapOf(
            NyloStyle.Magic to find("top_spider_magic_attack"),
            NyloStyle.Ranged to find("top_spider_ranged_attack"),
            NyloStyle.Melee to find("top_spider_melee_attack"),
        )
    val support_attack =
        mapOf(
            NyloStyle.Magic to find("top_spider_magic_meleeattack"),
            NyloStyle.Ranged to find("top_spider_ranged_meleeattack"),
            NyloStyle.Melee to find("top_spider_melee_attack"),
        )
    val death =
        mapOf(
            NyloStyle.Magic to find("top_spider_magic_death"),
            NyloStyle.Ranged to find("top_spider_ranged_death"),
            NyloStyle.Melee to find("top_spider_melee_death"),
        )
    val detonate =
        mapOf(
            NyloStyle.Magic to find("top_spider_magic_death_detonate"),
            NyloStyle.Ranged to find("top_spider_ranged_death_detonate"),
            NyloStyle.Melee to find("top_spider_melee_death_detonate"),
        )
    val vasilias_spawn = find("top_spider_melee_spawn")
    val support_collapse = find("nylocas_pillar_collapse")
}

object NylocasSpots : SpotanimReferences() {
    val ranged_small = find("tob_nylocas_rangedprojectile_size1")
    val ranged_big = find("tob_nylocas_rangedprojectile_size2")
    val magic_small = find("inferno_babysplitter_mage_big")
    val magic_big = find("inferno_babysplitter_mage_biggest")
    val detonate =
        mapOf(
            NyloStyle.Melee to find("tob_nylocas_death_melee_detonate"),
            NyloStyle.Ranged to find("tob_nylocas_death_ranged_detonate"),
            NyloStyle.Magic to find("tob_nylocas_death_magic_detonate"),
        )
}

/**
 * One entry of a wave: the style it fights as, big or small, whether it goes for the team rather
 * than the supports, and the styles it flickers through first.
 */
data class NyloSpawn(
    val style: NyloStyle,
    val big: Boolean,
    val aggressive: Boolean,
    val flickers: List<NyloStyle>,
) {
    companion object {
        /** `me`/`ra`/`ma`, upper case for a big one, `!` aggressive, `~` then the styles shown. */
        fun parse(entry: String): NyloSpawn {
            val base = entry.substringBefore('~')
            val shown = entry.substringAfter('~', "")
            val code = base.replace("!", "")
            return NyloSpawn(
                style = NyloStyle.of(code),
                big = code == code.uppercase(),
                aggressive = base.endsWith("!"),
                flickers = shown.chunked(2).map(NyloStyle::of),
            )
        }
    }
}

/** The wiki's table of the 31 waves ("Theatre of Blood/Strategies/Nylocas"), lane by lane. */
object NylocasWaves {
    class Wave(val east: List<String>, val south: List<String>, val west: List<String>)

    val waves =
        listOf(
            Wave(listOf("me"), listOf("ma"), listOf("ra!")),
            Wave(listOf("ra"), listOf("me!"), listOf("ma")),
            Wave(listOf("ma!"), listOf("ra"), listOf("me")),
            Wave(listOf("me"), listOf("MA"), listOf("ra")),
            Wave(listOf("ma"), listOf("me"), listOf("RA")),
            Wave(listOf("ME"), listOf("ra"), listOf("ma")),
            Wave(listOf("ME"), listOf("ma", "RA!"), listOf()),
            Wave(listOf("ra"), listOf("me"), listOf("MA!")),
            Wave(listOf("ma"), listOf(), listOf("RA!", "me")),
            Wave(listOf("ra!", "RA"), listOf("ra", "ra"), listOf("ra", "ra!")),
            Wave(listOf("ma!", "ma"), listOf("ma", "ma"), listOf("MA!")),
            Wave(listOf("me!", "me"), listOf("ME"), listOf("me", "me!")),
            Wave(listOf("ME!"), listOf("ra", "me"), listOf("ra", "ma!")),
            Wave(listOf("RA!"), listOf("ma", "ra"), listOf("ma", "me!")),
            Wave(listOf("ma!", "ra"), listOf("MA"), listOf("me", "ra!")),
            Wave(listOf("ra~mame"), listOf("ra~mema"), listOf("ra~rama")),
            Wave(listOf("MA~mame"), listOf("MA~mame"), listOf("MA~mame")),
            Wave(listOf("RA~rama"), listOf("RA~rama"), listOf("RA!~rama")),
            Wave(listOf("MA!~mame"), listOf("MA~mame"), listOf("MA~mame")),
            Wave(listOf("ME~mera"), listOf("ME!~mara"), listOf("ME~mera")),
            Wave(
                listOf("ra~rame", "ra!~rame"),
                listOf("me~mema", "me!~mera"),
                listOf("ra~mame", "ma~mara"),
            ),
            Wave(listOf("ME!~mara"), listOf("me~rama", "me~mara"), listOf("ME~mera")),
            Wave(listOf("ME~mara"), listOf("ME!~rama"), listOf("me~mara", "ra~rama")),
            Wave(listOf("ME!"), listOf("MA!"), listOf("ME!~rama")),
            Wave(listOf("MA~mame"), listOf("RA"), listOf("ME!")),
            Wave(listOf("MA"), listOf("ME~mema"), listOf("MA!")),
            Wave(listOf("MA~mame"), listOf("RA!~mema"), listOf("MA")),
            Wave(
                listOf("ma~mame", "me~rama"),
                listOf("me~mera", "ra~mame"),
                listOf("me~mera", "ma!~rame"),
            ),
            Wave(listOf("me~mara", "ma!~rame"), listOf("ME"), listOf("ma!~mera", "ra~rame")),
            Wave(listOf("MA!"), listOf("me~mera", "me~mara"), listOf("RA~rame")),
            Wave(
                listOf("ma~mara", "ra~rame"),
                listOf("ra~mema", "ra~mame"),
                listOf("ma~mera", "ra~rama"),
            ),
        )
}

/**
 * Wave 3, the Nylocas. Thirty-one fixed waves come out of the east, south and west tunnels, one
 * every four ticks while fewer than 12 (24 from wave 20) are alive. Each nylocas takes damage only
 * from its own style; most attack the four supports, the aggressive ones the team. Big ones split
 * into two small ones when they die; any of them explodes 30 seconds after appearing. A support
 * that falls drops rocks on the team; when the last one falls everyone in the fight dies. After the
 * last wave, Nylocas Vasilias drops in: it changes style every 10 ticks (everyone's attack stops),
 * hits up to 70 every 4 ticks (Protect from Melee blocks its bite, the other prayers leave a
 * quarter) and turns a wrong-style hit back on the attacker, healing from it.
 *
 * Wiki numbers and the wave table. Guesses (TSPS's): the tunnels' start tiles and mouths, which
 * supports a lane goes for and the tiles they are hit from, 500 hitpoints a support, flickering
 * every 4 ticks, explosions reaching one tile, Vasilias's tile.
 */
class NylocasRuntime(env: TobRoomEnv, raid: TobRaid) : TobRoomRuntime(env, raid, TobRoom.Nylocas) {
    /**
     * A tunnel: [start] is its far end (the map's tunnels run x 3280-3289 west, x 3302-3311 east
     * and z 4233-4241 south, two tiles wide), [mouth] where it opens into the room.
     */
    enum class Lane(val start: CoordGrid, val mouth: CoordGrid, val supports: List<Corner>) {
        East(CoordGrid(3311, 4248, 0), CoordGrid(3301, 4248, 0), listOf(Corner.NE, Corner.SE)),
        South(CoordGrid(3295, 4233, 0), CoordGrid(3295, 4243, 0), listOf(Corner.SW, Corner.SE)),
        West(CoordGrid(3280, 4248, 0), CoordGrid(3290, 4248, 0), listOf(Corner.NW, Corner.SW));

        /** Where a nylocas of [size] appears: a big one still fits inside the tunnel's end. */
        fun startFor(size: Int): CoordGrid =
            if (this == East) start.translate(1 - size, 0) else start
    }

    enum class Corner(val sw: CoordGrid, val angle: LocAngle, val spots: List<CoordGrid>) {
        NW(
            CoordGrid(3289, 4253, 0),
            LocAngle.North,
            tiles(3290 to 4252, 3291 to 4252, 3292 to 4254, 3292 to 4253),
        ),
        SW(
            CoordGrid(3289, 4242, 0),
            LocAngle.West,
            tiles(3290 to 4245, 3291 to 4245, 3292 to 4243, 3292 to 4244),
        ),
        NE(
            CoordGrid(3300, 4253, 0),
            LocAngle.East,
            tiles(3301 to 4252, 3300 to 4252, 3299 to 4254, 3299 to 4253),
        ),
        SE(
            CoordGrid(3300, 4242, 0),
            LocAngle.South,
            tiles(3301 to 4245, 3300 to 4245, 3299 to 4243, 3299 to 4244),
        ),
    }

    private class Nylo(val npc: Npc, val spawn: NyloSpawn, val lane: Lane, entered: Boolean) {
        var style: NyloStyle = spawn.flickers.firstOrNull() ?: spawn.style
        val flickers =
            ArrayDeque(
                spawn.flickers.drop(1) +
                    if (spawn.flickers.isNotEmpty()) listOf(spawn.style) else emptyList()
            )
        var age = 0
        var attackIn = ATTACK_TICKS
        var entered = entered
        var corner: Corner? = null
        var spot: CoordGrid? = null
    }

    private val nylos = LinkedHashMap<Npc, Nylo>()
    val supports = LinkedHashMap<Corner, Npc>()

    var wave = 0
        private set

    private var waveTicks = 0

    var vasilias: Npc? = null
        private set

    var vasiliasStyle: NyloStyle = NyloStyle.Melee
        private set

    private var arriving = 0
    private var formTicks = 0
    private var dying = false

    val alive: Int
        get() = nylos.size

    override fun onStart() {
        for (corner in Corner.entries) {
            val npc = spawn(NylocasNpcs.support, corner.sw, hitpoints = SUPPORT_HITPOINTS)
            supports[corner] = npc
        }
    }

    override fun bar(): TobBar {
        vasilias?.let {
            return barOf(it)
        }
        val total = supports.values.sumOf { it.hitpoints.coerceAtLeast(0) }
        return TobBar.of(TobBar.REGULAR, total, SUPPORT_HITPOINTS * Corner.entries.size)
    }

    override fun onTick() {
        if (dying) {
            return
        }
        if (vasilias != null) {
            tickVasilias()
            return
        }
        tickNylos()
        tickSupports()
        if (wave < NylocasWaves.waves.size) {
            val cap = if (wave + 1 >= CAP_LATE_FROM) CAP_LATE else CAP_EARLY
            if (++waveTicks >= WAVE_TICKS && nylos.size < cap) {
                waveTicks = 0
                spawnWave(NylocasWaves.waves[wave++])
            }
        } else if (nylos.isEmpty()) {
            summonVasilias()
        }
    }

    // The waves
    // ------------------------------------------------------------------------------------------

    private fun spawnWave(wave: NylocasWaves.Wave) {
        for ((lane, entries) in
            listOf(Lane.East to wave.east, Lane.South to wave.south, Lane.West to wave.west)) {
            for (entry in entries) {
                spawnNylo(NyloSpawn.parse(entry), lane, null)
            }
        }
    }

    private fun typeOf(spawn: NyloSpawn, style: NyloStyle): NpcType {
        val table =
            when {
                spawn.aggressive && spawn.big -> NylocasNpcs.big_aggressive
                spawn.aggressive -> NylocasNpcs.small_aggressive
                spawn.big -> NylocasNpcs.big
                else -> NylocasNpcs.small
            }
        return table.getValue(style)
    }

    private fun spawnNylo(spawn: NyloSpawn, lane: Lane, at: CoordGrid?): Npc {
        val first = spawn.flickers.firstOrNull() ?: spawn.style
        val type = typeOf(spawn, first)
        val npc =
            spawn(
                type,
                at ?: lane.startFor(env.npcTypes[type].size),
                hitpoints = if (spawn.big) BIG_HITPOINTS else SMALL_HITPOINTS,
                ignoreCollision = at == null,
            )
        val nylo = Nylo(npc, spawn, lane, entered = at != null)
        nylos[npc] = nylo
        if (at == null) {
            npc.walk(regionOf(lane.mouth))
        } else {
            engage(nylo)
        }
        return npc
    }

    private fun tickNylos() {
        val players = fightPlayers()
        for (nylo in nylos.values.toList()) {
            val npc = nylo.npc
            if (!npc.isSlotAssigned) {
                nylos.remove(npc)
                continue
            }
            nylo.age++
            if (nylo.age >= LIFE_TICKS) {
                detonate(nylo, players)
                continue
            }
            if (nylo.flickers.isNotEmpty() && nylo.age % FLICKER_TICKS == 0) {
                nylo.style = nylo.flickers.removeFirst()
                transform(npc, typeOf(nylo.spawn, nylo.style))
                monsters[npc]?.let {
                    it.attack = nyloAttack(nylo)
                    it.range = rangeOf(nylo.style)
                }
            }
            if (!nylo.entered) {
                if (npc.coords != regionOf(nylo.lane.mouth)) {
                    if (npc.routeDestination.isEmpty()) {
                        npc.walk(regionOf(nylo.lane.mouth))
                    }
                    continue
                }
                nylo.entered = true
                npc.collisionOverride = null
                engage(nylo)
            }
            if (!nylo.spawn.aggressive) {
                attackSupport(nylo)
            }
        }
        driveMonsters()
    }

    private fun engage(nylo: Nylo) {
        if (nylo.spawn.aggressive) {
            monster(nylo.npc, nyloAttack(nylo), rangeOf(nylo.style), ATTACK_TICKS)
            return
        }
        val standing = nylo.lane.supports.filter { it in supports }
        val corner = (standing.ifEmpty { supports.keys.toList() }).randomOrNull() ?: return
        nylo.corner = corner
        nylo.spot = pick(corner.spots)
    }

    private fun <T> List<T>.randomOrNull(): T? = if (isEmpty()) null else pick(this)

    private fun rangeOf(style: NyloStyle): Int = if (style == NyloStyle.Melee) 1 else 7

    private fun nyloAttack(nylo: Nylo): NpcAttack =
        NpcAttack(
            nylo.style.attackStyle,
            anim = NylocasSeqs.attack.getValue(nylo.style),
            maxHit = if (nylo.spawn.big) BIG_MAX else SMALL_MAX,
        )

    override fun swing(monster: TobMonster, target: Player) {
        val nylo = nylos[monster.npc]
        val vas = monster.npc === vasilias
        val style = if (vas) vasiliasStyle else nylo?.style ?: return
        val big = vas || nylo?.spawn?.big == true
        val max = if (vas) VASILIAS_MAX else if (big) BIG_MAX else SMALL_MAX
        monster.npc.anim(NylocasSeqs.attack.getValue(style))
        var delay = 1
        if (style != NyloStyle.Melee) {
            val from = realOf(monster.npc.coords) ?: return
            val to = realOf(target.coords) ?: return
            val spot =
                when (style) {
                    NyloStyle.Ranged ->
                        if (big) NylocasSpots.ranged_big else NylocasSpots.ranged_small
                    else -> if (big) NylocasSpots.magic_big else NylocasSpots.magic_small
                }
            val leave = if (style == NyloStyle.Ranged) 39 else 25
            delay = projectileBy(from, to, spot, leave, 0, 10, heights = 8 to 16)
        }
        val through = if (vas) (if (style == NyloStyle.Melee) 0 else VASILIAS_THROUGH) else 0
        val attack = NpcAttack(style.attackStyle, maxHit = max, hitDelay = delay.coerceAtLeast(1))
        strike(monster.npc, target, attack, through = through, playAnim = false)
    }

    private fun attackSupport(nylo: Nylo) {
        val corner = nylo.corner
        val support = corner?.let(supports::get)
        if (corner == null || support == null) {
            if (nylo.entered) {
                engage(nylo)
            }
            return
        }
        val npc = nylo.npc
        if (gap(support, npc.coords) > 1) {
            if (!npc.isFrozen) {
                stepToward(npc, regionOf(checkNotNull(nylo.spot)), keepOff = false)
            }
            return
        }
        if (npc.isFrozen || --nylo.attackIn > 0) {
            return
        }
        nylo.attackIn = ATTACK_TICKS
        npc.faceSquare(support.coords, support.size, support.size)
        npc.anim(NylocasSeqs.support_attack.getValue(nylo.style))
        val damage = roll(0, if (nylo.spawn.big) BIG_MAX else SMALL_MAX)
        support.hitpoints = (support.hitpoints - damage).coerceAtLeast(0)
    }

    private fun detonate(nylo: Nylo, players: List<Player>) {
        val npc = nylo.npc
        npc.anim(NylocasSeqs.detonate.getValue(nylo.style))
        realOf(npc.coords)?.let { graphic(NylocasSpots.detonate.getValue(nylo.style), it) }
        val max = if (nylo.spawn.big) BIG_EXPLODE else SMALL_EXPLODE
        for (player in players) {
            if (gap(npc, player.coords) <= 1) {
                hurt(player, roll(1, max))
            }
        }
        nylos.remove(npc)
        monsters.remove(npc)
        later(1) { despawn(npc) }
    }

    override fun monsterDied(npc: Npc) {
        val nylo = nylos.remove(npc) ?: return
        if (nylo.spawn.big) {
            val at = realOf(npc.coords) ?: return
            repeat(2) { i ->
                val style = pick(NyloStyle.entries)
                spawnNylo(
                    NyloSpawn(style, big = false, aggressive = false, flickers = emptyList()),
                    nylo.lane,
                    at.translate(i, 0),
                )
            }
        }
    }

    /** Each nylocas (and Vasilias) takes damage only from its own style. */
    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        val style =
            when {
                npc === vasilias -> vasiliasStyle
                else -> nylos[npc]?.style ?: return
            }
        if (hit.type == HitType.Typeless || hit.type == style.hitType) {
            return
        }
        val damage = hit.damage
        hit.damage = 0
        if (npc === vasilias && damage > 0 && source != null) {
            hurt(source, damage)
            heal(npc, damage)
        }
    }

    // Supports
    // ----------------------------------------------------------------------------------------

    private fun tickSupports() {
        for ((corner, support) in supports.toList()) {
            if (support.hitpoints > 0) {
                continue
            }
            supports.remove(corner)
            despawn(support)
            swapLoc(
                corner.sw,
                listOf(NylocasLocs.support, NylocasLocs.support_collapsing),
                NylocasLocs.support_broken,
            )
            animLoc(corner.sw, NylocasLocs.support_broken, NylocasSeqs.support_collapse)
            val last = supports.isEmpty()
            for (player in fightPlayers()) {
                hurt(player, if (last) player.stat(stats.hitpoints) else roll(1, COLLAPSE_MAX))
            }
        }
    }

    // Vasilias
    // ------------------------------------------------------------------------------------------

    private fun summonVasilias() {
        val npc = spawn(NylocasNpcs.vasilias_spawning, VASILIAS_TILE, scale = true)
        npc.anim(NylocasSeqs.vasilias_spawn)
        vasilias = npc
        arriving = VASILIAS_ARRIVAL
        formTicks = 0
    }

    private fun tickVasilias() {
        val npc = vasilias ?: return
        if (arriving > 0) {
            if (--arriving > 0) {
                return
            }
            changeForm(npc, NyloStyle.Melee)
            monster(npc, NpcAttack(NpcAttackStyle.Melee, maxHit = VASILIAS_MAX), 1, VASILIAS_SPEED)
            return
        }
        if (++formTicks >= FORM_TICKS) {
            formTicks = 0
            changeForm(npc, pick(NyloStyle.entries.filter { it != vasiliasStyle }))
            stopAttacking(npc)
        }
        driveMonsters()
    }

    /**
     * Everyone attacking Vasilias stops when it changes form, so nobody swings the old style at the
     * new one: their attack is dropped, along with an auto-retaliate already queued from one of its
     * bites (which would otherwise start them attacking again straight away).
     */
    private fun stopAttacking(npc: Npc) {
        for (player in roomPlayers()) {
            val interaction = player.interaction
            if (interaction is InteractionNpc && interaction.target === npc) {
                player.clearInteraction()
                player.resetFaceEntity()
            }
            player.clearQueue(queues.com_retaliate_npc)
        }
    }

    private fun changeForm(npc: Npc, style: NyloStyle) {
        vasiliasStyle = style
        transform(npc, NylocasNpcs.vasilias.getValue(style))
        monsters[npc]?.let {
            it.attack = NpcAttack(style.attackStyle, maxHit = VASILIAS_MAX)
            it.range = if (style == NyloStyle.Melee) 1 else 10
        }
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc !== vasilias || dying) {
            return
        }
        dying = true
        monsters.remove(npc)
        npc.anim(NylocasSeqs.death.getValue(vasiliasStyle))
        complete()
        later(ticksOf(NylocasSeqs.death.getValue(vasiliasStyle), least = 3)) { despawn(npc) }
    }

    override fun onComplete() {
        for (npc in nylos.keys.toList()) {
            despawn(npc)
        }
        nylos.clear()
        for (support in supports.values) {
            despawn(support)
        }
        supports.clear()
    }

    companion object {
        const val WAVE_TICKS = 4
        const val CAP_EARLY = 12
        const val CAP_LATE = 24
        const val CAP_LATE_FROM = 20
        const val SMALL_HITPOINTS = 11
        const val BIG_HITPOINTS = 22
        const val SMALL_MAX = 17
        const val BIG_MAX = 24
        private const val SMALL_EXPLODE = 18
        private const val BIG_EXPLODE = 21
        const val ATTACK_TICKS = 3
        const val LIFE_TICKS = 50
        private const val FLICKER_TICKS = 4
        const val SUPPORT_HITPOINTS = 500
        private const val COLLAPSE_MAX = 50
        const val VASILIAS_MAX = 70
        private const val VASILIAS_THROUGH = 25
        private const val VASILIAS_SPEED = 4
        const val FORM_TICKS = 10
        private const val VASILIAS_ARRIVAL = 3
        val VASILIAS_TILE = CoordGrid(3294, 4247, 0)
    }
}

private fun tiles(vararg xz: Pair<Int, Int>): List<CoordGrid> =
    xz.map { CoordGrid(it.first, it.second, 0) }

class TobNylocasScript
@Inject
constructor(private val rooms: TobRoomRegistry, private val npcRepo: NpcRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(TobRoom.Nylocas) { env, raid -> NylocasRuntime(env, raid) }
        tobBosses(
            rooms,
            NylocasNpcs.vasilias.values.toList() +
                listOf(NylocasNpcs.vasilias_spawning, NylocasNpcs.support),
        )
        val deaths = HashMap<NpcType, SeqType?>()
        for (table in
            listOf(
                NylocasNpcs.small,
                NylocasNpcs.big,
                NylocasNpcs.small_aggressive,
                NylocasNpcs.big_aggressive,
            )) {
            for ((style, type) in table) {
                deaths[type] = NylocasSeqs.death.getValue(style)
            }
        }
        tobMonsterDeaths(rooms, npcRepo, deaths)
    }
}
