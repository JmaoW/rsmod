package org.rsmod.content.areas.misc.isleofsouls.pvp

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import kotlin.reflect.KClass
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.plugin.PlayerDeathScript
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.interact.PlayerInteractions
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.api.spells.autocast.scripts.AutocastScript
import org.rsmod.api.spells.runes.combo.ComboRuneRepository
import org.rsmod.api.spells.runes.combo.ComboRuneScript
import org.rsmod.api.spells.runes.compact.CompactRuneRepository
import org.rsmod.api.spells.runes.compact.CompactRuneScript
import org.rsmod.api.spells.runes.fake.FakeRuneRepository
import org.rsmod.api.spells.runes.fake.FakeRuneScript
import org.rsmod.api.spells.runes.staves.StaffSubstituteRepository
import org.rsmod.api.spells.runes.staves.StaffSubstituteScript
import org.rsmod.api.spells.runes.subs.RuneSubstituteRepository
import org.rsmod.api.spells.runes.subs.RuneSubstituteScript
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.capture.CaptureClient
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRegistry
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.content.areas.misc.isleofsouls.lobby.LobbySpawnScript
import org.rsmod.content.areas.misc.isleofsouls.lobby.SoulWarsLobby
import org.rsmod.content.areas.misc.isleofsouls.revenant.AvariceScript
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.wilderness.EscapeCaves
import org.rsmod.content.areas.misc.isleofsouls.wilderness.WildLair
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript

private val RUNE_REPOSITORIES =
    listOf(
        ComboRuneRepository::class,
        CompactRuneRepository::class,
        FakeRuneRepository::class,
        RuneSubstituteRepository::class,
        StaffSubstituteRepository::class,
        UnlimitedRuneRepository::class,
    )

/** Player-versus-player combat in the zones the event settings open. */
@OptIn(InternalApi::class)
class PvpTest {
    private val before = EventSettings.current

    @AfterEach
    fun restoreSettings() {
        EventSettings.current = before
    }

    private object PvpModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(PvpScript::class.java).`in`(Scopes.SINGLETON)
            bind(AvariceScript::class.java).`in`(Scopes.SINGLETON)
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            @Suppress("UNCHECKED_CAST")
            bind(Class.forName("org.rsmod.api.combat.PvPCombat") as Class<Any>)
                .`in`(Scopes.SINGLETON)
            bind(WeaponRegistry::class.java).`in`(Scopes.SINGLETON)
            bind(WeaponRepository::class.java).`in`(Scopes.SINGLETON)
            bind(WeaponAttackManager::class.java).`in`(Scopes.SINGLETON)
            Multibinder.newSetBinder(binder(), WeaponMap::class.java)
            install(MagicSpellModule())
            bind(SpellAttackManager::class.java).`in`(Scopes.SINGLETON)
            bind(SpellAttackRegistry::class.java).`in`(Scopes.SINGLETON)
            bind(SpellAttackRepository::class.java).`in`(Scopes.SINGLETON)
            Multibinder.newSetBinder(binder(), SpellAttackMap::class.java)
            for (repository in RUNE_REPOSITORIES) {
                bind(repository.java).`in`(Scopes.SINGLETON)
            }
        }
    }

    class Deps
    @Inject
    constructor(
        val pvp: PvpScript,
        val skull: PvpSkull,
        val teams: LeagueTeams,
        val avarice: AvariceScript,
        val interactions: PlayerInteractions,
    )

    @Suppress("UNCHECKED_CAST")
    private fun script(name: String) = Class.forName(name).kotlin as KClass<out PluginScript>

    private fun GameTestState.runPvpTest(body: GameTestScope.(Deps, Player, Player) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            PvpModule,
            PvpScript::class,
            AvariceScript::class,
            script("org.rsmod.api.combat.scripts.PvPCombatScript"),
            PlayerHitScript::class,
            NpcHitScript::class,
            PlayerDeathScript::class,
            LobbySpawnScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
            AutocastScript::class,
            script("org.rsmod.api.weapons.scripts.WeaponScript"),
            script("org.rsmod.api.spells.attack.scripts.SpellAttackScript"),
            ComboRuneScript::class,
            CompactRuneScript::class,
            FakeRuneScript::class,
            RuneSubstituteScript::class,
            StaffSubstituteScript::class,
            UnlimitedRuneScript::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            alice.displayName = "Alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            bob.displayName = "Bob"
            body(deps, alice, bob)
        }

    private fun open(
        arena: Boolean = false,
        revenants: Boolean = false,
        lairs: Boolean = false,
        escape: Boolean = false,
    ) {
        EventSettings.current = EventSettings(pvp = PvpSettings(arena, revenants, lairs, escape))
    }

    private val cave = CoordGrid(3217, 10062, 0)
    private val graveyard = CoordGrid(2205, 2847, 0)

    /** Puts [first] on [at] and [second] beside it. */
    private fun GameTestScope.stand(first: Player, second: Player, at: CoordGrid) {
        allocZoneCollision(at)
        allocZoneCollision(at.translateX(1))
        first.placeAt(at)
        second.placeAt(at.translateX(1))
        advance(ticks = 2)
    }

    /** Everything [player] was sent over [ticks] ticks: chat lines, and `op:` lines for Attack. */
    private fun GameTestScope.advanceSeeing(player: Player, ticks: Int): List<String> {
        val seen = mutableListOf<String>()
        repeat(ticks) {
            advance()
            val client = player.client as CaptureClient
            seen += client.filterIsInstance<MessageGame>().map { it.message }
            seen +=
                client
                    .filterIsInstance<Any>()
                    .filter { it.javaClass.simpleName == "SetPlayerOp" }
                    .map { it.toString() }
                    .filter { "id=2," in it }
                    .map { "op:$it" }
            seen +=
                client
                    .filterIsInstance<Any>()
                    .filter { it.javaClass.simpleName == "RunClientScript" }
                    .map { it.toString() }
                    .filter { "id=390," in it }
                    .map { "overlay:$it" }
        }
        return seen
    }

    /** Waits long enough for any fight to go quiet. */
    private fun GameTestScope.pause() = advance(ticks = PvpFights.RETALIATION_TICKS + 5)

    /** Has [attacker] attack [target] and returns what the attacker was sent meanwhile. */
    private fun GameTestScope.attack(deps: Deps, attacker: Player, target: Player): List<String> {
        deps.interactions.interact(attacker, target, InteractionOp.Op2)
        return advanceSeeing(attacker, 8)
    }

    @Test
    fun GameTestState.`players fight in an open zone and the attacker is skulled`() =
        runPvpTest { deps, alice, bob ->
            open(revenants = true)
            stand(alice, bob, cave)
            assertNull(alice.skullIcon)

            val seen = attack(deps, alice, bob)

            assertEquals(constants.skullicon_default, alice.skullIcon, "the white skull")
            assertTrue(deps.skull.active(alice))
            assertNull(bob.skullIcon, "the target is not skulled")
            assertFalse(seen.contains("You can't attack other players here."))
        }

    @Test
    fun GameTestState.`nobody can attack outside the open zones`() =
        runPvpTest { deps, alice, bob ->
            open(revenants = true)
            stand(alice, bob, graveyard)

            val seen = attack(deps, alice, bob)

            assertNull(alice.skullIcon)
            assertTrue(seen.contains("You can't attack other players here."), "$seen")
        }

    @Test
    fun GameTestState.`a zone that is turned off is peaceful`() = runPvpTest { deps, alice, bob ->
        open(arena = true, lairs = true, escape = true)
        stand(alice, bob, cave)

        val seen = attack(deps, alice, bob)

        assertNull(alice.skullIcon)
        assertTrue(seen.contains("You can't attack other players here."), "$seen")
    }

    @Test
    fun GameTestState.`a target outside the zone cannot be attacked from inside`() =
        runPvpTest { deps, alice, bob ->
            open(revenants = true)
            stand(alice, bob, cave)
            allocZoneCollision(graveyard)
            bob.placeAt(graveyard)
            advance(ticks = 2)

            assertEquals("You can't attack other players here.", deps.pvp.denial(alice, bob))
            assertEquals("You can't attack other players here.", deps.pvp.denial(bob, alice))
        }

    @Test
    fun GameTestState.`teammates cannot attack each other but other teams can be attacked`() =
        runPvpTest { deps, alice, bob ->
            open(revenants = true)
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.createTeam(2, "Beta")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            deps.teams.assign("carol", 2)
            stand(alice, bob, cave)
            allocZoneCollision(cave.translateX(2))
            carol.placeAt(cave.translateX(2))
            advance(ticks = 2)

            assertEquals("You can't attack your own teammates.", deps.pvp.denial(alice, bob))
            assertNull(deps.pvp.denial(alice, carol))

            attack(deps, alice, bob)
            assertNull(alice.skullIcon, "a refused attack skulls nobody")
        }

    @Test
    fun GameTestState.`players without a team can fight`() = runPvpTest { deps, alice, bob ->
        open(revenants = true)
        stand(alice, bob, cave)

        assertNull(deps.pvp.denial(alice, bob))
    }

    @Test
    fun GameTestState.`every zone is where it should be`() = runPvpTest { deps, alice, bob ->
        val all = PvpSettings(true, true, true, true)
        fun zoneAt(x: Int, z: Int, level: Int = 0) = PvpZones.open(CoordGrid(x, z, level), all)

        // The arena: both team portals, a corner of its box and the lobby just outside.
        assertEquals(PvpZone.SoulWarsArena, zoneAt(2142, 2905))
        assertEquals(PvpZone.SoulWarsArena, zoneAt(2272, 2919))
        assertNull(zoneAt(2205, 2847), "the lobby graveyards stay peaceful")
        assertNull(zoneAt(2190, 2811), "so does the isle outside")
        // A pocket of floor behind the arena's tree wall, inside the old bounding box.
        assertNull(zoneAt(2264, 2889), "ground behind the tree wall is not the arena")
        assertEquals(6037, PvpAreas.SOUL_WARS_ARENA.size, "every tile reached from the portals")
        // The middle island and the two bridges that lead to it.
        assertEquals(PvpZone.SoulWarsArena, zoneAt(2207, 2912), "the middle island")
        assertEquals(PvpZone.SoulWarsArena, zoneAt(2170, 2913), "the west bridge")
        assertEquals(PvpZone.SoulWarsArena, zoneAt(2240, 2911), "the east bridge")
        assertNull(zoneAt(2142, 2905, level = 1), "only level 0")

        assertEquals(PvpZone.RevenantCaves, zoneAt(3217, 10058))
        assertEquals(PvpZone.RevenantCaves, zoneAt(3244, 10215))

        for (lair in WildLair.entries) {
            assertEquals(PvpZone.WildernessBossLairs, PvpZones.open(lair.arrival, all), lair.title)
            assertEquals(
                PvpZone.WildernessBossLairs,
                PvpZones.open(lair.bossSpawn, all),
                lair.title,
            )
        }
        assertEquals(PvpZone.EscapeCavesZone, PvpZones.open(EscapeCaves.fronts[0], all))
        assertNull(zoneAt(3200, 3200))
    }

    @Test
    fun GameTestState.`each toggle opens only its own zone`() = runPvpTest { _, _, _ ->
        val spots =
            mapOf(
                PvpZone.SoulWarsArena to CoordGrid(2142, 2905, 0),
                PvpZone.RevenantCaves to CoordGrid(3217, 10058, 0),
                PvpZone.WildernessBossLairs to WildLair.CALLISTO.arrival,
                PvpZone.EscapeCavesZone to EscapeCaves.fronts[0],
            )
        for (zone in PvpZone.entries) {
            val only =
                PvpSettings(
                    soulWarsArena = zone == PvpZone.SoulWarsArena,
                    revenantCaves = zone == PvpZone.RevenantCaves,
                    wildernessBossLairs = zone == PvpZone.WildernessBossLairs,
                    escapeCaves = zone == PvpZone.EscapeCavesZone,
                )
            for ((other, spot) in spots) {
                val expected = if (other == zone) zone else null
                assertEquals(expected, PvpZones.open(spot, only), "$zone open, standing in $other")
            }
        }
        assertEquals(PvpSettings(), EventSettings().pvp, "everything is closed by default")
    }

    @Test
    fun GameTestState.`the Attack option comes with the zone and goes with it`() =
        runPvpTest { _, alice, _ ->
            open(revenants = true)
            allocZoneCollision(graveyard)
            alice.placeAt(graveyard)
            advance(ticks = 3)

            allocZoneCollision(cave)
            alice.placeAt(cave)
            val seenEntering = advanceSeeing(alice, 4)
            val entered = seenEntering.filter { it.startsWith("op:") }
            assertEquals(1, entered.size, "sent once, not every tick: $entered")
            assertEquals(1, seenEntering.count { it.startsWith("overlay:") }, "$seenEntering")
            assertTrue(seenEntering.first { it.startsWith("overlay:") }.contains("1"), "inside")
            assertEquals(PvpScript.OVERLAY_LEVEL, alice.vars[pvp_varps.overlay])
            assertTrue("op='Attack'" in entered[0], entered[0])

            alice.placeAt(graveyard)
            val seenLeaving = advanceSeeing(alice, 4)
            val left = seenLeaving.filter { it.startsWith("op:") }
            assertEquals(1, left.size, "$left")
            assertEquals(1, seenLeaving.count { it.startsWith("overlay:") }, "$seenLeaving")
            assertEquals(0, alice.vars[pvp_varps.overlay])
            assertTrue("Attack" !in left[0] && "null" in left[0], left[0])
        }

    @Test
    fun GameTestState.`closed zones never show the Attack option`() = runPvpTest { _, alice, _ ->
        open()
        allocZoneCollision(cave)
        alice.placeAt(cave)

        val seen = advanceSeeing(alice, 4)
        assertTrue(seen.none { "op='Attack'" in it })
        assertTrue(seen.none { it.startsWith("overlay:") }, "no overlay in a closed zone")
        assertEquals(0, alice.vars[pvp_varps.overlay])
    }

    @Test
    fun GameTestState.`answering an attack is not skulled and the aggressor keeps theirs`() =
        runPvpTest { deps, alice, bob ->
            deps.pvp.attacked(alice, bob)
            assertTrue(deps.skull.active(alice), "the first attacker is skulled")

            deps.pvp.attacked(bob, alice)
            assertFalse(deps.skull.active(bob), "answering is free")
            assertNull(bob.skullIcon)

            advance(ticks = 5)
            deps.pvp.attacked(bob, alice)
            deps.pvp.attacked(alice, bob)
            assertFalse(deps.skull.active(bob), "still answering")
            assertTrue(deps.skull.active(alice), "the aggressor stays skulled")
        }

    @Test
    fun GameTestState.`attacking someone who never attacked you is skulled`() =
        runPvpTest { deps, alice, bob ->
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            deps.pvp.attacked(alice, bob)

            deps.pvp.attacked(bob, carol)

            assertTrue(deps.skull.active(bob), "carol never attacked bob")
        }

    @Test
    fun GameTestState.`a fight that went quiet starts again with a new aggressor`() =
        runPvpTest { deps, alice, bob ->
            deps.pvp.attacked(alice, bob)
            pause()
            deps.skull.expire(alice)

            deps.pvp.attacked(bob, alice)

            assertTrue(deps.skull.active(bob), "bob attacked first this time")
            assertFalse(deps.skull.active(alice))
        }

    @Test
    fun GameTestState.`an attack that keeps a fight going keeps it from going quiet`() =
        runPvpTest { deps, alice, bob ->
            deps.pvp.attacked(alice, bob)
            repeat(4) {
                advance(ticks = PvpFights.RETALIATION_TICKS - 10)
                deps.pvp.attacked(alice, bob)
            }

            deps.pvp.attacked(bob, alice)

            assertFalse(deps.skull.active(bob), "the fight never paused for a minute")
        }

    @Test
    fun GameTestState.`the skull lasts twenty minutes and each attack restarts them`() =
        runPvpTest { deps, alice, _ ->
            deps.skull.refresh(alice)
            advance(ticks = PvpSkull.SKULL_TICKS / 2)
            deps.skull.refresh(alice)

            advance(ticks = PvpSkull.SKULL_TICKS - 10)
            assertEquals(constants.skullicon_default, alice.skullIcon, "restarted, still skulled")

            advance(ticks = 20)
            assertNull(alice.skullIcon)
            assertFalse(deps.skull.active(alice))
        }

    @Test
    fun GameTestState.`the avarice skull and the pvp skull keep each other's icon`() =
        runPvpTest { deps, alice, _ ->
            alice.worn[Wearpos.Front.slot] = InvObj(objs.amulet_of_avarice)
            eventBus.publish(
                HeldEquipEvents.WearposChange(
                    player,
                    Wearpos.Front,
                    objTypes[objs.amulet_of_avarice],
                )
            )
            deps.skull.refresh(alice)

            deps.skull.expire(alice)
            assertEquals(constants.skullicon_default, alice.skullIcon, "the amulet still skulls")

            deps.skull.refresh(alice)
            alice.worn[Wearpos.Front.slot] = null
            eventBus.publish(
                HeldEquipEvents.WearposChange(
                    player,
                    Wearpos.Front,
                    objTypes[objs.amulet_of_avarice],
                )
            )
            deps.avarice.expire(alice)
            assertEquals(constants.skullicon_default, alice.skullIcon, "the pvp skull remains")

            deps.skull.expire(alice)
            assertNull(alice.skullIcon)
        }

    @Test
    fun GameTestState.`dying to a player keeps every item and ends the skull`() =
        runPvpTest { deps, alice, bob ->
            open(revenants = true)
            stand(alice, bob, cave)
            bob.inv[0] = InvObj(objs.beer, 3)
            bob.worn[Wearpos.RightHand.slot] = InvObj(objs.abyssal_whip)
            deps.skull.refresh(bob)
            bob.setCurrentLevel(stats.hitpoints, 5)

            bob.queueHit(alice, delay = 1, HitType.Typeless, damage = 5)
            advance(ticks = 30)

            assertEquals(InvObj(objs.beer, 3), bob.inv[0], "inventory kept")
            assertEquals(InvObj(objs.abyssal_whip), bob.worn[Wearpos.RightHand.slot], "worn kept")
            assertNull(bob.skullIcon)
            assertFalse(deps.skull.active(bob))
            assertTrue(bob.coords in SoulWarsLobby.spawnTiles, "back at the lobby: ${bob.coords}")
            assertTrue(bob.hitpoints > 0, "healed up")
        }
}
