package org.rsmod.content.areas.misc.isleofsouls.colosseum

import java.util.IdentityHashMap
import org.rsmod.content.areas.misc.isleofsouls.instances.BossHealthBar
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

/** Where a run is between its waves and fights. */
enum class ColosseumStage {
    /** Between waves: Minimus waits for Start-wave (or Leave). */
    Intermission,

    /** A modifier was picked; the wave comes in at [ColosseumRun.startAt]. */
    Starting,

    /** Sol Heredit greets the team (wave 12). */
    SolIntro,

    /** Sol Heredit's cutscene: he jumps from his seat and lands at [ColosseumRun.startAt]. */
    SolJump,

    /** A wave is being fought. */
    Wave,

    /** Sol Heredit is beaten, or everyone left early: the chest is out to claim from. */
    Finished,
}

/**
 * One player's part in a team's run. The team shares the waves and the modifiers; the loot, the
 * Glory and the hits taken are each player's own (as if each did a solo run).
 */
class ColosseumMember(val accountId: Int, var player: Player) {
    /** What has been earned and not yet claimed: the chest's contents. */
    val rewards = ArrayList<ColosseumItem>()

    /** The next wave's loot (rolled ahead so the intermission shows it) and the last wave's. */
    var future: List<ColosseumItem> = emptyList()
    var previous: List<ColosseumItem> = emptyList()

    /** This run's Glory so far. */
    var glory = 0

    /** An enemy hurt this player this wave: no "no damage" Glory for it. */
    var hitByEnemy = false

    /** Damage taken this wave (varp `colosseum_wave_damage_taken`). */
    var damageTaken = 0

    /** Doom's stacks; they clear when a wave is completed. */
    var doomStacks = 0

    /** Died in the run: they wait in the lobby and can come back at the next intermission. */
    var dead = false

    /** Gave up between waves: their chest is out to claim, and they leave through Minimus. */
    var forfeited = false

    /** Was outside the arena when the last wave began, so it earns them nothing. */
    var missedWave = false

    /** The tick Sol Heredit's perfect parry empowers this player's attacks until (`-1` none). */
    var maxHitUntil = -1

    /** Watching Sol Heredit's jump: their camera and clicks are the cutscene's until it ends. */
    var inCutscene = false
}

/** One step of the Spear, Shield or a beam: a tile and when (in ticks from now) it shows. */
data class TimedTile(val tile: CoordGrid, val ring: Int)

/** A pool of molten sand, on a real arena tile. Sol Heredit's is weaker than the modifiers'. */
class SandPool(val loc: LocInfo, var permanent: Boolean, val solSand: Boolean)

/** A bee swarm, and when a killed one comes back. */
class BeeSwarm(var npc: Npc?, var respawnAt: Int = 0)

/** The Solarflare orb: the ring of tiles it circles, where it is in it, and when it moves on. */
class SolarFlare(val npc: Npc, val path: List<Pair<CoordGrid, Boolean>>, var index: Int) {
    var restUntil = 0
    val touching = HashSet<Int>()
}

/** A healing totem by a wounded monster: when it next heals, and when a destroyed one returns. */
class HealingTotem(var npc: Npc?, var nextHeal: Int, var respawnAt: Int = 0)

/** What the director remembers about one living monster of a wave. */
class ColosseumMonsterState(val kind: ColosseumMonster, val spawnedAt: Int) {
    /** The player this monster is after. */
    var target: Player? = null

    /** The warband's side of its target (berserker north, seer east, archer west, extra south). */
    var side: Pair<Int, Int> = 0 to 0

    /** The Javelin Colossus's attacks so far: every fifth is the sky javelin. */
    var attacks = 0

    /** The tick the warband last moved: a swing that comes while moving is skipped. */
    var movedAt = -1

    /** Where it was last told to walk, so it is not told again every tick. */
    var walkTarget: CoordGrid? = null
}

/** A thing to do on a later tick (a hit landing, sand appearing). */
class ColosseumLater(val at: Int, val action: () -> Unit)

/**
 * One team's run of the Fortis Colosseum, alive while anyone is in its [instance]. [waves] are the
 * real wave numbers the run plays (all twelve, or the event's shortened list), fixed when it
 * starts.
 */
class ColosseumRun(val owner: String, val instance: TeamInstance, val waves: List<Int>) {
    val members = LinkedHashMap<Int, ColosseumMember>()
    val modifiers = ColosseumModifierSet()
    var stage = ColosseumStage.Intermission

    /** How many of [waves] are cleared: the next is `waves[cleared]`. */
    var cleared = 0

    /** The real number of the wave on the floor (or the last one). */
    var wave = 0

    val nextWave: Int?
        get() = waves.getOrNull(cleared)

    val isFinalWave: Boolean
        get() = wave >= ColosseumWaves.FINAL_WAVE

    /** The three modifiers on offer this intermission, and who has the screen open. */
    var offered: List<ColosseumModifier> = emptyList()

    /** When a picked wave comes in, and when the current one began. */
    var startAt = 0
    var waveStart = 0
    var reinforced = false

    /** The monsters that count towards clearing the wave. */
    val monsters = ArrayList<Npc>()
    val states = IdentityHashMap<Npc, ColosseumMonsterState>()

    /** Minimus, the seated Sol Heredit and the hazards the modifiers bring. */
    var minimus: Npc? = null
    var seatedSol: Npc? = null
    val bees = ArrayList<BeeSwarm>()
    var solarFlare: SolarFlare? = null
    val totems = IdentityHashMap<Npc, HealingTotem>()
    val sand = HashMap<CoordGrid, SandPool>()

    /** The seat's blocker, the barricade and the chest, while they stand. */
    var seatBlocker: LocInfo? = null
    val barricade = ArrayList<LocInfo>()
    var chest: LocInfo? = null

    /** Sol Heredit's fight, once his wave has begun, and his health bar. */
    var sol: SolFight? = null
    val bar = BossHealthBar()

    /** Things to do on later ticks. */
    val later = ArrayList<ColosseumLater>()

    /** Every npc the run owns, so that it can remove them all when it ends. */
    fun allNpcs(): List<Npc> =
        monsters +
            listOfNotNull(minimus, seatedSol, solarFlare?.npc) +
            bees.mapNotNull { it.npc } +
            totems.values.mapNotNull { it.npc } +
            (sol?.crystals?.map { it.npc } ?: emptyList())

    fun memberOf(player: Player): ColosseumMember? = members[player.accountId]

    /** The members in the arena and alive, who fight the wave. */
    fun fighters(): List<ColosseumMember> =
        members.values.filter { !it.dead && !it.forfeited && instance.contains(it.player.coords) }

    fun stateOf(npc: Npc): ColosseumMonsterState? = states[npc]

    /** [real], a tile of the real arena, in this run's copy. */
    fun inst(real: CoordGrid): CoordGrid = ColosseumArena.toInstance(instance, real)

    /** [coords], a tile in this run's copy, as the real arena's. */
    fun real(coords: CoordGrid): CoordGrid = ColosseumArena.toNormal(instance, coords)
}
