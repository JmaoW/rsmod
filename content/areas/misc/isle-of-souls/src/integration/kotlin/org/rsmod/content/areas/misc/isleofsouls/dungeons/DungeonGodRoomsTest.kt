package org.rsmod.content.areas.misc.isleofsouls.dungeons

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.NpcTargeting
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

/** The generals' private rooms: each team's own copy of a chamber behind "Open (private)". */
class DungeonGodRoomsTest {
    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val instances: TeamInstances,
        val rooms: GodRooms,
        val npcList: NpcList,
        val targeting: NpcTargeting,
        val armadyl: GodWarsArmadylScript,
        val bandos: GodWarsBandosScript,
        val locRepo: LocRepository,
    )

    private object RoomsModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(GodRooms::class.java).`in`(Scopes.SINGLETON)
            bind(GodWarsArmadylScript::class.java).`in`(Scopes.SINGLETON)
            bind(GodWarsBandosScript::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private val extras = mutableListOf<Player>()

    /** alice and bob are on team 1, dave on team 2, carol has no team. */
    private fun GameTestState.runRoomsTest(
        body: GameTestScope.(Deps, Player, Player, Player, Player) -> Unit
    ) =
        runInjectedGameTest(
            Deps::class,
            RoomsModule,
            DungeonGateScript::class,
            GodWarsArmadylScript::class,
            GodWarsBandosScript::class,
            GodWarsAltarScript::class,
            PlayerHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            val dave = registerPlayer(player = Player().apply { username = "dave" })
            extras += listOf(bob, carol, dave)
            deps.teams.createTeam(1, "Alpha")
            deps.teams.createTeam(2, "Beta")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            deps.teams.assign("dave", 2)
            for (each in listOf(alice, bob, carol, dave)) {
                each.observerUUID = each.uuid
            }
            advance(ticks = 40)
            try {
                body(deps, alice, bob, carol, dave)
            } finally {
                deps.rooms.sweep()
                extras.forEach { unregisterPlayer(it) }
                extras.clear()
            }
        }

    private fun GameTestScope.door(gate: DungeonGate): BoundLocInfo {
        val click = gate.clicks.first { it.type == DungeonGateLocs.boss_door_private }
        val angle =
            when (gate) {
                DungeonGates.armadylChamber -> LocAngle.South
                DungeonGates.bandosChamber -> LocAngle.West
                DungeonGates.saradominChamber -> LocAngle.East
                else -> LocAngle.North
            }
        allocZoneCollision(gate.a)
        allocZoneCollision(gate.b)
        val at = checkNotNull(click.coords)
        allocZoneCollision(at)
        return placeMapLoc(at, click.type, LocShape.CentrepieceStraight, angle)
    }

    private fun GameTestScope.kills(who: Player, god: God, count: Int) {
        VarPlayerIntMapSetter.set(who, god.kills, count)
    }

    /** [who] uses the "Open (private)" option of [gate]'s door from outside it. */
    private fun GameTestScope.openPrivate(who: Player, gate: DungeonGate) {
        val door = door(gate)
        who.placeAt(gate.a)
        who.opLoc2(door)
        advance(ticks = 10)
    }

    private fun Deps.leadersIn(
        instance: org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
    ): List<Npc> =
        npcList.filter {
            instance.contains(it.coords) && (it.visType.internalName in GodWarsNames.armadylLeaders)
        }

    private val armadyl = DungeonGates.armadylChamber

    @Test
    fun GameTestState.`open private puts a team in its own copy of the chamber with the general`() =
        runRoomsTest { deps, alice, _, _, _ ->
            kills(alice, God.ARMADYL, 45)
            openPrivate(alice, armadyl)

            val instance = checkNotNull(deps.rooms.instanceOf(alice))
            assertTrue(RegionRegistry.inWorkingArea(alice.coords))
            assertNotEquals(armadyl.b, alice.coords, "not the public chamber")
            assertEquals(armadyl.b, deps.rooms.toNormal(alice.coords), "but the same spot in it")
            assertEquals(45, alice.vars[God.ARMADYL.kills], "the kill count was kept")
            val leaders = deps.leadersIn(instance)
            assertEquals(
                GodWarsNames.armadylLeaders.toSet(),
                leaders.map { it.visType.internalName }.toSet(),
            )
            assertTrue(leaders.all { it.respawns }, "they come back after they die")
        }

    @Test
    fun GameTestState
        .`a teammate shares the room, needs the count too, and nothing is spawned twice`() =
        runRoomsTest { deps, alice, bob, _, _ ->
            kills(alice, God.ARMADYL, 40)
            kills(bob, God.ARMADYL, 50)
            openPrivate(alice, armadyl)
            openPrivate(bob, armadyl)

            val instance = checkNotNull(deps.rooms.instanceOf(alice))
            assertEquals(instance, deps.rooms.instanceOf(bob))
            assertEquals(40, alice.vars[God.ARMADYL.kills])
            assertEquals(50, bob.vars[God.ARMADYL.kills], "bob needed 40 as well, keeps them")
            assertEquals(4, deps.leadersIn(instance).size)
        }

    @Test
    fun GameTestState.`another team and a lone player each get a room of their own`() =
        runRoomsTest { deps, alice, _, carol, dave ->
            for (each in listOf(alice, carol, dave)) {
                kills(each, God.ARMADYL, 40)
                openPrivate(each, armadyl)
            }
            val rooms = listOf(alice, carol, dave).map { checkNotNull(deps.rooms.instanceOf(it)) }
            assertEquals(3, rooms.toSet().size)
            for (instance in rooms) {
                assertEquals(4, deps.leadersIn(instance).size)
            }
        }

    @Test
    fun GameTestState.`the private door asks for the kill count but takes a key instead`() =
        runRoomsTest { deps, alice, _, _, _ ->
            kills(alice, God.ARMADYL, 39)
            openPrivate(alice, armadyl)
            assertNull(deps.rooms.instanceOf(alice), "39 kills is not enough")

            alice.inv.objs[0] =
                InvObj(objTypes.values.first { it.internalName == "ecumenical_key" })
            openPrivate(alice, armadyl)
            assertNotNull(deps.rooms.instanceOf(alice), "a key is")
            assertNull(alice.inv.objs[0], "the key was used")
        }

    @Test
    fun GameTestState.`the door inside the room leads out and the last one out closes it`() =
        runRoomsTest { deps, alice, bob, _, _ ->
            kills(alice, God.ARMADYL, 40)
            kills(bob, God.ARMADYL, 40)
            openPrivate(alice, armadyl)
            openPrivate(bob, armadyl)
            val instance = checkNotNull(deps.rooms.instanceOf(alice))
            assertEquals(4, deps.leadersIn(instance).size)

            // The copy of the door stands at other coordinates than the real one.
            val inner = instance.region.normal[checkNotNull(armadyl.clicks.first().coords)]
            val door =
                placeMapLoc(
                    inner,
                    DungeonGateLocs.boss_door_private,
                    LocShape.CentrepieceStraight,
                    LocAngle.South,
                )
            alice.opLoc1(door)
            advance(ticks = 10)
            assertEquals(armadyl.a, alice.coords, "out of the room")
            assertNull(deps.rooms.instanceOf(alice))
            assertEquals(4, deps.leadersIn(instance).size, "bob is still in")

            bob.opLoc1(door)
            advance(ticks = 10)
            assertEquals(armadyl.a, bob.coords)
            assertEquals(0, deps.leadersIn(instance).size, "the room's monsters went with it")
        }

    @Test
    fun GameTestState.`leaving by the altar's teleport closes the room too`() =
        runRoomsTest { deps, alice, _, _, _ ->
            kills(alice, God.ARMADYL, 40)
            openPrivate(alice, armadyl)
            val instance = checkNotNull(deps.rooms.instanceOf(alice))
            val altar = instance.region.normal[CoordGrid(2821, 5301, 2)]
            // A loc added to an instance has to be a real (dynamic) one, as Zulrah's exit scroll
            // is.
            val info =
                deps.locRepo.add(
                    altar,
                    locTypes[GodWarsAltarLocs.armadyl],
                    duration = Int.MAX_VALUE,
                    angle = LocAngle.South,
                    shape = LocShape.CentrepieceStraight,
                )
            val loc = BoundLocInfo(info, locTypes[GodWarsAltarLocs.armadyl])
            alice.placeAt(altar.translate(3, 1))
            alice.opLoc2(loc)
            advance(ticks = 10)
            assertEquals(armadyl.a, alice.coords)
            assertEquals(0, deps.leadersIn(instance).size)
        }

    @Test
    fun GameTestState.`a general is only aimed at the players in its own room`() =
        runRoomsTest { deps, alice, _, _, dave ->
            kills(alice, God.ARMADYL, 40)
            kills(dave, God.ARMADYL, 40)
            openPrivate(alice, armadyl)
            openPrivate(dave, armadyl)
            val aliceRoom = checkNotNull(deps.rooms.instanceOf(alice))
            val daveRoom = checkNotNull(deps.rooms.instanceOf(dave))
            val aliceKree =
                deps.leadersIn(aliceRoom).first { it.visType.internalName == GodWarsNames.KREE }
            val daveKree =
                deps.leadersIn(daveRoom).first { it.visType.internalName == GodWarsNames.KREE }

            assertEquals(listOf(alice), deps.targeting.inRange(aliceKree, 24))
            assertEquals(listOf(dave), deps.targeting.inRange(daveKree, 24))
        }

    @Test
    fun GameTestState.`the public chamber and a private room do not see each other`() =
        runRoomsTest { deps, alice, _, carol, _ ->
            kills(alice, God.ARMADYL, 40)
            openPrivate(alice, armadyl)
            allocZoneCollision(armadyl.b)
            carol.placeAt(armadyl.b)

            val privateRoom = checkNotNull(deps.rooms.roomOf(GodChambers.armadyl, alice.coords))
            val publicRoom = checkNotNull(deps.rooms.roomOf(GodChambers.armadyl, carol.coords))
            assertTrue(privateRoom.isPrivate)
            assertFalse(publicRoom.isPrivate)
            assertNotEquals(privateRoom, publicRoom)
            assertEquals(listOf(alice), deps.rooms.playersIn(privateRoom))
            assertEquals(listOf(carol), deps.rooms.playersIn(publicRoom))
            assertEquals(1, deps.rooms.publicCount(deps.rooms.defOf(armadyl)!!))
            assertFalse(deps.rooms.sameRoom(GodChambers.armadyl, alice.coords, carol.coords))
        }

    @Test
    fun GameTestState.`kree's knockback keeps a player inside their own room`() =
        runRoomsTest { deps, alice, _, _, _ ->
            kills(alice, God.ARMADYL, 40)
            openPrivate(alice, armadyl)
            val instance = checkNotNull(deps.rooms.instanceOf(alice))
            val kree =
                deps.leadersIn(instance).first { it.visType.internalName == GodWarsNames.KREE }
            val start = instance.region.normal[CoordGrid(2837, 5304, 2)]
            alice.placeAt(start)

            deps.armadyl.knockBack(kree, alice)

            assertEquals(start.translateX(1), alice.coords)
            assertEquals(instance, deps.rooms.instanceOf(alice))
        }

    @Test
    fun GameTestState.`graardor's sergeants follow the favourite of their own room only`() =
        runRoomsTest { deps, alice, _, _, dave ->
            val bandos = DungeonGates.bandosChamber
            kills(alice, God.BANDOS, 40)
            kills(dave, God.BANDOS, 40)
            openPrivate(alice, bandos)
            openPrivate(dave, bandos)

            deps.bandos.recordHit(alice)
            assertEquals(alice, deps.bandos.focusOf(alice))
            assertEquals(
                dave,
                deps.bandos.focusOf(dave),
                "alice's hit means nothing in dave's room",
            )
            assertNull(deps.bandos.lastHitter, "nor in the public chamber")
        }

    @Test
    fun GameTestState.`a room's monsters are removed when everyone just vanishes from it`() =
        runRoomsTest { deps, alice, _, _, _ ->
            kills(alice, God.ARMADYL, 40)
            openPrivate(alice, armadyl)
            val instance = checkNotNull(deps.rooms.instanceOf(alice))
            assertEquals(4, deps.leadersIn(instance).size)

            alice.placeAt(CoordGrid(2200, 2815, 0))
            advance(ticks = GodWarsNames.RESET_TIMER * 3)
            assertEquals(0, deps.leadersIn(instance).size)
        }

    @Test
    fun GameTestState.`the other chambers have rooms too`() = runRoomsTest { deps, alice, _, _, _ ->
        for ((gate, god) in
            listOf(
                DungeonGates.bandosChamber to God.BANDOS,
                DungeonGates.saradominChamber to God.SARADOMIN,
                DungeonGates.zamorakChamber to God.ZAMORAK,
            )) {
            kills(alice, god, 40)
            openPrivate(alice, gate)
            val instance = checkNotNull(deps.rooms.instanceOf(alice)) { "${god.title} room" }
            assertEquals(gate.b, deps.rooms.toNormal(alice.coords))
            val def = checkNotNull(deps.rooms.defOf(gate))
            val here =
                deps.npcList.filter {
                    instance.contains(it.coords) && it.visType.internalName != null
                }
            assertEquals(
                def.spawns.map { it.name }.toSet(),
                here.map { it.visType.internalName }.toSet(),
            )
            deps.rooms.leave(alice)
        }
    }
}
