package org.rsmod.content.areas.misc.isleofsouls.kingblackdragon

import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceType
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveDirector
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveRun
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The King Black Dragon's lair in ordinary map coordinates (the real map, region 9033). The
 * instance is a copy of the block of 8x8 zones x 2248-2295, z 4672-4719 on all levels; the lair is
 * the open floor of x 2256-2287 by z 4680-4711, walled all round, and its way out is the lever on
 * the south wall at (2271, 4680). Read from the cache with `tools/dungeons/collision.py`.
 */
object KbdArena {
    val template: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(copyZoneX = 281, copyZoneZ = 584) {
                zoneWidth = 6
                zoneLength = 6
            }
        }

    /** Where players appear: just north of the lever they came in by. */
    val start = CoordGrid(2271, 4682, 0)

    /** Where players leave to: in the Revenant Caves, in front of the lever that took them in. */
    val exit = KbdLever.front

    /**
     * The dragon is 5x5. This is the south-west tile of the spot it stands on (the map's spawn).
     */
    const val SIZE = 5
    val bossSpot = CoordGrid(2269, 4697, 0)
}

/**
 * The lever in the Revenant Caves: a copy of the real entrance lever, put on a wall beside the
 * cave's arrival from the surface. [coords] is the floor tile it hangs over (the wall is its south
 * edge) and [front] the tile north of it, where players land when they leave the lair.
 */
object KbdLever {
    val coords = CoordGrid(3220, 10061, 0)
    val angle = LocAngle.South
    val front = CoordGrid(3220, 10062, 0)
}

/** The lair as a kind of team instance. Registered with [TeamInstances] by `KbdScript`. */
object KbdInstance {
    const val NAME = "king_black_dragon"

    val type =
        TeamInstanceType(
            name = NAME,
            template = KbdArena.template,
            entry = KbdArena.start,
            exit = KbdArena.exit,
        )
}

/** One team's fight with the King Black Dragon. It lives as long as its [instance] has someone. */
class KbdRun(instance: TeamInstance, startClock: Int) : WaveRun(instance, startClock) {
    override val waveCount: Int
        get() = 1

    var boss: Npc? = null

    /** Set when the dragon is dying, so the run no longer owns it. */
    var dead = false

    /** Who got the kill, the dead dragon and where the drops fall, set when it dies. */
    var hero: Player? = null
    var killed: Npc? = null
    var dropAt: CoordGrid? = null

    override fun allNpcs(): List<Npc> = listOfNotNull(boss?.takeIf { !dead })

    override fun clearNpcs() {
        super.clearNpcs()
        boss = null
    }
}

/**
 * Runs a team's fight with the King Black Dragon (OSRS Wiki, "King Black Dragon"). The wave loop
 * (enter, leave, the run tick, clean-up) is [WaveDirector]; the fight is one wave: the dragon
 * appears a few ticks after the team arrives, chases the nearest player and breathes or bites as
 * [org.rsmod.content.other.dragonfire.DragonScript] decides. When it dies the drops fall and, after
 * the wiki's 16 ticks, the next one appears, for whoever is still in the lair.
 */
class KingBlackDragon
@Inject
constructor(
    instances: TeamInstances,
    npcRepo: NpcRepository,
    npcTypes: NpcTypeList,
    interactions: AiPlayerInteractions,
    collision: CollisionFlagMap,
    mapClock: MapClock,
    private val players: PlayerList,
) : WaveDirector<KbdRun>(instances, npcRepo, npcTypes, interactions, collision, mapClock) {
    override val instanceType: TeamInstanceType
        get() = KbdInstance.type

    override fun createRun(instance: TeamInstance): KbdRun = KbdRun(instance, mapClock.cycle)

    override fun waveMessage(run: KbdRun): String? = null

    override fun startPlayer(player: Player) {
        player.softTimer(kbd_timers.tick, 1)
        player.mes("You pull the lever and are drawn into the King Black Dragon's lair.")
    }

    override fun spawnWave(run: KbdRun, inside: List<Player>) {
        run.dead = false
        run.boss = spawn(run, kbd_npcs.boss, KbdArena.bossSpot)
    }

    /**
     * The dragon walks up to breathing range of its target and fights from there. It is aimed from
     * [retargetExtras]; it is not one of the run's `monsters`.
     */
    override fun engage(run: KbdRun, npc: Npc, target: Player): Boolean {
        if (npc !== run.boss) {
            return false
        }
        engage(npc, target, ranged = true)
        return true
    }

    override fun retargetExtras(run: KbdRun, targets: List<Player>) {
        val boss = run.boss?.takeIf { !run.dead } ?: return
        retarget(run, boss, targets)
    }

    override fun onWin(run: KbdRun, inside: List<Player>) {
        // It comes back in the same lair after a while (the wiki's respawn is 16 ticks), for
        // whoever is still in it: each kill counts, and the next starts with the next spawn.
        run.state = WaveState.WaitingForNextWave
        run.nextWaveAt = mapClock.cycle + npcRepo.respawnSpeed.scale(RESPAWN_TICKS)
    }

    /**
     * The dragon has died (called by the death script, which then spawns its drops: that publishes
     * the kill).
     */
    fun bossDied(npc: Npc) {
        val run = runOf(npc) ?: return
        if (run.boss !== npc || run.dead) {
            return
        }
        run.dead = true
        run.hero = npc.findHero(players)
        run.killed = npc
        run.dropAt = npc.coords.translate(KbdArena.SIZE / 2, KbdArena.SIZE / 2)
        win(run)
    }

    companion object {
        /** Ticks between the dragon's death and the next one (wiki: 16). */
        const val RESPAWN_TICKS = 16
    }
}
