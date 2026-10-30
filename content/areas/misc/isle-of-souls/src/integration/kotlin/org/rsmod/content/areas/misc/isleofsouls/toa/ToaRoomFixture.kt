package org.rsmod.content.areas.misc.isleofsouls.toa

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import kotlin.reflect.KClass
import org.rsmod.api.combat.NpcAttackDelays
import org.rsmod.api.combat.PvnRules
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.routefinder.collision.CollisionFlagMap

/** What every ToA room test needs: the raid, its rooms and a world with the tombs allocated. */
object ToaTestModule : AbstractModule() {
    override fun configure() {
        bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
    }
}

class ToaTestDeps
@Inject
constructor(
    val parties: ToaParties,
    val raids: ToaRaids,
    val rooms: ToaRoomRegistry,
    val collision: CollisionFlagMap,
    val supplies: ToaSupplies,
    val objTypes: ObjTypeList,
    val locTypes: LocTypeList,
    val attackDelays: NpcAttackDelays,
    val pvnRules: PvnRules,
    val npcHitModifier: NpcHitModifier,
)

/** A party of one or two in [room] of a fresh raid. */
class ToaTestRun(
    val deps: ToaTestDeps,
    val alice: Player,
    val bob: Player,
    val raid: ToaRaid,
    val room: ToaRoom,
) {
    fun stand(player: Player, real: CoordGrid) {
        PathingEntityCommon.telejump(player, deps.collision, raid.regionOf(real))
    }

    fun real(player: Player): CoordGrid = checkNotNull(raid.realOf(player.coords))

    fun real(npc: Npc): CoordGrid = checkNotNull(raid.realOf(npc.coords))

    fun runtime(): ToaRoomRuntime = checkNotNull(deps.rooms.ensure(raid, room))

    /** Clears [box] of the collision the test world gives tiles it has no map for. */
    fun open(box: ToaBox) {
        for (x in box.minX..box.maxX) {
            for (z in box.minZ..box.maxZ) {
                val tile = raid.regionOf(CoordGrid(x, z, box.level))
                deps.collision[tile.x, tile.z, tile.level] = 0
            }
        }
    }
}

fun GameTestScope.allocateTombs(deps: ToaTestDeps) {
    for (column in ToaPacking.columns) {
        for (zx in column.realZoneX until column.realZoneX + column.zoneWidth) {
            for (zz in ToaPacking.REAL_ZONE_Z until ToaPacking.REAL_ZONE_Z + ToaPacking.ZONE_ROWS) {
                for (level in 0 until CoordGrid.LEVEL_COUNT) {
                    deps.collision.allocateIfAbsent(
                        zx * ZoneGrid.LENGTH,
                        zz * ZoneGrid.LENGTH,
                        level,
                    )
                }
            }
        }
    }
}

/**
 * Runs [body] with the party placed in [room] of a new raid, its floor (or [floor]) open, and the
 * room's runtime built. [scripts] are the room's own scripts.
 */
fun GameTestState.runRoomTest(
    room: ToaRoom,
    vararg scripts: KClass<out PluginScript>,
    twoPlayers: Boolean = false,
    floor: ToaBox? = room.bounds,
    body: GameTestScope.(ToaTestRun) -> Unit,
) =
    runInjectedGameTest(
        ToaTestDeps::class,
        ToaTestModule,
        WeaponAttackStylesScript::class,
        *scripts,
    ) { deps ->
        allocateTombs(deps)
        val alice = player
        alice.displayName = "Alice"
        alice.observerUUID = 1L
        alice.stats[stats.hitpoints] = 99
        PathingEntityCommon.telejump(alice, deps.collision, ToaLobby.RETURN)
        deps.collision.allocateIfAbsent(ToaLobby.RETURN.x, ToaLobby.RETURN.z, 0)
        val bob = registerPlayer(coords = ToaLobby.RETURN)
        bob.displayName = "Bob"
        bob.observerUUID = 2L
        bob.stats[stats.hitpoints] = 99
        val party = deps.parties.create(alice)
        if (twoPlayers) {
            deps.parties.apply(party, bob)
            deps.parties.answer(party, bob, accept = true)
        }
        // The moves between rooms fade the screen first; these tests move at once.
        deps.raids.fadeTicks = 0
        deps.raids.enterFromLobby(alice)
        if (twoPlayers) {
            deps.raids.enterFromLobby(bob)
        }
        val raid = checkNotNull(deps.raids.raidOf(alice))
        val run = ToaTestRun(deps, alice, bob, raid, room)
        floor?.let(run::open)
        for (member in raid.active) {
            deps.raids.place(raid, member, room)
        }
        deps.rooms.ensure(raid, room)
        body(run)
    }

/** Game ticks with the raid and its rooms ticked as the plugin scripts would. */
fun GameTestScope.tickRoom(run: ToaTestRun, ticks: Int = 1) {
    repeat(ticks) {
        advance()
        run.deps.raids.tick()
        run.deps.rooms.tick()
    }
}

/** Kills [npc] the ordinary way and lets its death play out. */
fun GameTestScope.kill(run: ToaTestRun, npc: Npc) {
    npc.hitpoints = 0
    npc.queueDeath()
    tickRoom(run, 6)
}

/** Hits built by hand, for the rooms' damage rules. */
object TestHits {
    fun hit(type: org.rsmod.game.hit.HitType, damage: Int): org.rsmod.game.hit.HitBuilder =
        org.rsmod.game.hit.HitBuilder(
            type = type,
            damage = damage,
            sourceUid = null,
            sourceSlot = null,
            isFromNpc = false,
            isFromPlayer = true,
            clientDelay = 0,
            righthandType = null,
            secondaryType = null,
            targetHitmark = 0,
            sourceHitmark = 0,
            publicHitmark = null,
            zeroDamageHitmarkLit = null,
            zeroDamageHitmarkTint = null,
            maxDamageHitmarkLit = null,
            targetMaxDamageThreshold = 0,
            sourceMaxDamageThreshold = 0,
        )
}
