package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.map.CoordGrid

internal object GodWarsTestNpcs : NpcReferences() {
    val gorak = find("godwars_gorak")
    val armadyl_mage = find("godwars_spiritual_armadyl_mage")
    val imp = find("godwars_ancient_imp")
    val knight = find("godwars_saradomin_knight_1")
    val icefiend = find("godwars_icefiend_1")
    val graardor = find("godwars_bandos_avatar")
}

class DungeonGodWarsTest {
    class Deps @Inject constructor(val script: DungeonScript, val crossings: GodWarsCrossings)

    private fun GameTestState.runGodWarsTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            null,
            DungeonScript::class,
            DungeonGateScript::class,
            GodWarsScript::class,
        ) { deps ->
            body(deps)
        }

    private val inside = CoordGrid(2882, 5310, 2)

    private fun GameTestScope.set(varbit: VarBitType, value: Int) =
        VarPlayerIntMapSetter.set(player, varbit, value)

    private fun GameTestScope.level(stat: StatType, level: Int) {
        player.setBaseLevel(stat, level)
        player.setCurrentLevel(stat, level)
    }

    private fun GameTestScope.loc(
        coords: CoordGrid,
        type: LocType,
        shape: LocShape = LocShape.CentrepieceStraight,
        angle: LocAngle = LocAngle.West,
    ): BoundLocInfo {
        allocZoneCollision(coords)
        return placeMapLoc(coords, type, shape, angle)
    }

    /** Uses [loc] from [stand] and returns where the player ends up. */
    private fun GameTestScope.use(loc: BoundLocInfo, stand: CoordGrid, ticks: Int = 8): CoordGrid {
        allocZoneCollision(stand)
        player.placeAt(stand)
        player.opLoc1(loc)
        advance(ticks = ticks)
        return player.coords
    }

    /**
     * A refused player walked up to the object, which can leave them a tile off the crossing end.
     */
    private fun assertStays(end: CoordGrid, actual: CoordGrid, message: String) =
        assertTrue(actual.chebyshevDistance(end) <= 1, "$message: ended at $actual, end is $end")

    @Test
    fun GameTestState.`coming in ties the ropes and keeps the kill counts`() =
        runGodWarsTest { deps ->
            val dungeon = Dungeon.GOD_WARS_DUNGEON
            allocZoneCollision(dungeon.entrance)
            allocZoneCollision(dungeon.arrival)
            deps.script.placeEntrances()
            set(GodWarsVarbits.armadyl_kills, 7)
            set(GodWarsVarbits.saradomin_rope_1, 0)
            set(GodWarsVarbits.door_state, 0)
            val hole = bound(dungeon.entrance, dungeon.entranceType)

            val landed = use(hole, dungeon.front)

            assertEquals(dungeon.arrival, landed)
            assertEquals(1, player.vars[GodWarsVarbits.saradomin_rope_1])
            assertEquals(1, player.vars[GodWarsVarbits.saradomin_rope_2])
            assertEquals(1, player.vars[GodWarsVarbits.door_state], "the private option is shown")
            assertEquals(7, player.vars[GodWarsVarbits.armadyl_kills])
        }

    @Test
    fun GameTestState.`kill counts survive leaving the dungeon and coming back`() =
        runGodWarsTest { _ ->
            val outside = CoordGrid(2200, 2815, 0)
            allocZoneCollision(inside)
            allocZoneCollision(outside)
            set(GodWarsVarbits.bandos_kills, 12)
            set(GodWarsVarbits.zamorak_kills, 3)

            player.placeAt(outside)
            advance(2)
            player.placeAt(inside)
            eventBus.publish(DungeonEntered(player, Dungeon.GOD_WARS_DUNGEON))

            assertEquals(12, player.vars[GodWarsVarbits.bandos_kills])
            assertEquals(3, player.vars[GodWarsVarbits.zamorak_kills])
        }

    @Test
    fun GameTestState.`the kill count box is open inside the dungeon and closed outside`() =
        runGodWarsTest { _ ->
            val outside = CoordGrid(2200, 2815, 0)
            allocZoneCollision(inside)
            allocZoneCollision(outside)
            player.softTimer(GodWarsTimers.overlay, 1)

            player.placeAt(outside)
            advance(2)
            assertFalse(player.ui.containsOverlay(GodWarsInterfaces.overlay), "outside")

            player.placeAt(inside)
            advance(2)
            assertTrue(player.ui.containsOverlay(GodWarsInterfaces.overlay), "inside")

            player.placeAt(outside)
            advance(2)
            assertFalse(player.ui.containsOverlay(GodWarsInterfaces.overlay), "left again")
        }

    private fun GameTestScope.bound(coords: CoordGrid, type: LocType): BoundLocInfo =
        BoundLocInfo(
            checkNotNull(findLocs(coords).firstOrNull { it.coords == coords }),
            locTypes[type],
        )

    @Test
    fun GameTestState.`followers count for their god and the gods themselves do not`() =
        runGodWarsTest { _ ->
            assertEquals(God.BANDOS, God.ofFollower("godwars_gorak"))
            assertEquals(God.BANDOS, God.ofFollower("godwars_goblin3"))
            assertEquals(God.BANDOS, God.ofFollower("godwars_spiritual_bandos_mage"))
            assertEquals(God.ARMADYL, God.ofFollower("godwars_armadyl_male_armor02_red"))
            assertEquals(God.SARADOMIN, God.ofFollower("godwars_icefiend_1"))
            assertEquals(God.SARADOMIN, God.ofFollower("godwars_ancient_saradomin_wizard"))
            assertEquals(God.ZAMORAK, God.ofFollower("godwars_ancient_werewolf2"))
            assertEquals(God.ZAMORAK, God.ofFollower("godwars_bloodveld"))
            assertNull(God.ofFollower("godwars_bandos_avatar"))
            assertNull(God.ofFollower("godwars_armadyl_bodyguard_kilisa"))
            assertNull(God.ofFollower("godwars_saradomin_lion"))
            assertNull(God.ofFollower("cow"))
        }

    @Test
    fun GameTestState.`killing a follower inside the dungeon counts for its god`() =
        runGodWarsTest { _ ->
            allocZoneCollision(inside)
            player.placeAt(inside)
            val kills =
                listOf(
                    GodWarsTestNpcs.gorak to GodWarsVarbits.bandos_kills,
                    GodWarsTestNpcs.armadyl_mage to GodWarsVarbits.armadyl_kills,
                    GodWarsTestNpcs.imp to GodWarsVarbits.zamorak_kills,
                    GodWarsTestNpcs.knight to GodWarsVarbits.saradomin_kills,
                    GodWarsTestNpcs.icefiend to GodWarsVarbits.saradomin_kills,
                )
            for ((type, counter) in kills) {
                val npc = spawnNpc(inside.translateX(3), npcTypes[type])
                eventBus.publish(NpcDeathEvents.Killed(npc, player))
            }
            assertEquals(1, player.vars[GodWarsVarbits.bandos_kills])
            assertEquals(1, player.vars[GodWarsVarbits.armadyl_kills])
            assertEquals(1, player.vars[GodWarsVarbits.zamorak_kills])
            assertEquals(2, player.vars[GodWarsVarbits.saradomin_kills])

            val boss = spawnNpc(inside.translateX(3), npcTypes[GodWarsTestNpcs.graardor])
            eventBus.publish(NpcDeathEvents.Killed(boss, player))
            assertEquals(
                1,
                player.vars[GodWarsVarbits.bandos_kills],
                "the general is not a follower",
            )
        }

    @Test
    fun GameTestState.`a kill outside the dungeon does not count`() = runGodWarsTest { _ ->
        val outside = CoordGrid(2200, 2815, 0)
        allocZoneCollision(outside)
        player.placeAt(outside)
        val npc = spawnNpc(outside.translateX(2), npcTypes[GodWarsTestNpcs.gorak])
        eventBus.publish(NpcDeathEvents.Killed(npc, player))
        assertEquals(0, player.vars[GodWarsVarbits.bandos_kills])
    }

    private class Chamber(
        val gate: DungeonGate,
        val shape: LocShape,
        val angle: LocAngle,
        val god: God,
    )

    private val chambers =
        listOf(
            Chamber(
                DungeonGates.armadylChamber,
                LocShape.CentrepieceStraight,
                LocAngle.South,
                God.ARMADYL,
            ),
            Chamber(
                DungeonGates.bandosChamber,
                LocShape.CentrepieceStraight,
                LocAngle.West,
                God.BANDOS,
            ),
            Chamber(
                DungeonGates.saradominChamber,
                LocShape.CentrepieceStraight,
                LocAngle.East,
                God.SARADOMIN,
            ),
            Chamber(
                DungeonGates.zamorakChamber,
                LocShape.CentrepieceStraight,
                LocAngle.North,
                God.ZAMORAK,
            ),
        )

    @Test
    fun GameTestState.`a chamber door asks for 40 kills once and keeps them`() =
        runGodWarsTest { _ ->
            for (chamber in chambers) {
                val gate = chamber.gate
                val click = gate.clicks.first()
                val door = loc(checkNotNull(click.coords), click.type, chamber.shape, chamber.angle)
                allocZoneCollision(gate.b)

                set(chamber.god.kills, KILL_COUNT - 1)
                assertEquals(gate.a, use(door, gate.a), "${chamber.god} at 39")

                set(chamber.god.kills, KILL_COUNT + 5)
                assertEquals(gate.b, use(door, gate.a), "${chamber.god} at 45")
                assertEquals(45, player.vars[chamber.god.kills], "${chamber.god} not spent")

                assertEquals(gate.a, use(door, gate.b), "${chamber.god} out")
                assertEquals(gate.b, use(door, gate.a), "${chamber.god} in again")
                assertEquals(45, player.vars[chamber.god.kills], "${chamber.god} still not spent")
            }
        }

    @Test
    fun GameTestState.`a short door says what it wants and a chamber door swings open`() =
        runGodWarsTest { deps ->
            val gate = DungeonGates.bandosChamber
            val click = gate.clicks.first()
            val door =
                loc(
                    checkNotNull(click.coords),
                    click.type,
                    LocShape.CentrepieceStraight,
                    LocAngle.West,
                )
            allocZoneCollision(gate.b)
            set(God.BANDOS.kills, 10)
            allocZoneCollision(gate.a)
            player.placeAt(gate.a)
            player.opLoc1(door)
            // Messages are only kept for the tick they were sent in, so look at every tick.
            var locked = false
            repeat(6) {
                if (!locked) {
                    advance()
                    locked =
                        runCatching {
                                assertMessageSent(
                                    "This door is locked by the power of Bandos! You will need " +
                                        "to collect the essence of at least 40 of his " +
                                        "followers before the door will open."
                                )
                            }
                            .isSuccess
                }
            }
            assertTrue(locked, "the locked message")

            // The god's own door loc is taken away for two ticks and an open pair stands in.
            val at = checkNotNull(click.coords)
            loc(at, DoorLocs.bandos, LocShape.CentrepieceStraight, LocAngle.West)
            deps.crossings.swingDoor(God.BANDOS)
            val swung = findLocs(at).map { it.id }.toSet()
            assertEquals(setOf(DoorLocs.open.id, DoorLocs.open_visible.id), swung, "swung open")
            advance(ticks = 3)
            assertTrue(findLocs(at).any { it.id == DoorLocs.bandos.id }, "closed again")
        }

    @Test
    fun GameTestState.`the kill count stops at 4000`() = runGodWarsTest { _ ->
        allocZoneCollision(inside)
        player.placeAt(inside)
        set(GodWarsVarbits.bandos_kills, GodWarsScript.MAX_KILLS - 1)
        repeat(2) {
            val npc = spawnNpc(inside.translateX(3), npcTypes[GodWarsTestNpcs.gorak])
            eventBus.publish(NpcDeathEvents.Killed(npc, player))
        }
        assertEquals(4000, player.vars[GodWarsVarbits.bandos_kills])
    }

    @Test
    fun GameTestState.`bandos' big door asks for 70 strength going in only`() =
        runGodWarsTest { _ ->
            val gate = DungeonGates.bandosDoor
            val click = gate.clicks.first()
            val door =
                loc(checkNotNull(click.coords), click.type, LocShape.WallStraight, LocAngle.West)
            allocZoneCollision(gate.b)

            level(stats.strength, 69)
            assertStays(gate.a, use(door, gate.a), "refused at 69")
            level(stats.strength, 70)
            assertEquals(gate.b, use(door, gate.a), "opens at 70")
            level(stats.strength, 1)
            assertEquals(gate.a, use(door, gate.b), "out is free")
        }

    private fun GameTestScope.allocPillar() {
        allocZoneCollision(
            CoordGrid(GodWarsCrossings.PILLAR_X, GodWarsCrossings.PILLAR_MIDDLE_Z, 2)
        )
    }

    @Test
    fun GameTestState.`armadyl's pillar asks for 70 ranged each way`() = runGodWarsTest { _ ->
        val gate = DungeonGates.armadylPillar
        val click = gate.clicks.first()
        val pillar = loc(checkNotNull(click.coords), click.type, angle = LocAngle.East)
        allocZoneCollision(gate.b)
        allocPillar()

        level(stats.ranged, 69)
        assertStays(gate.a, use(pillar, gate.a), "refused at 69")
        level(stats.ranged, 70)
        assertEquals(gate.b, use(pillar, gate.a, ticks = 16), "crosses at 70")
        level(stats.ranged, 69)
        assertStays(gate.b, use(pillar, gate.b), "refused coming back at 69")
        level(stats.ranged, 70)
        assertEquals(gate.a, use(pillar, gate.b, ticks = 16), "crosses back at 70")
    }

    private fun GameTestScope.allocBridge() {
        for (z in listOf(5333, 5334, 5344, 5345)) {
            allocZoneCollision(CoordGrid(GodWarsCrossings.BRIDGE_X, z, 2))
        }
    }

    @Test
    fun GameTestState
        .`zamorak's bridge asks for 70 hitpoints and drains prayer going north only`() =
        runGodWarsTest { _ ->
            val gate = DungeonGates.zamorakBridge
            val south = loc(CoordGrid(2885, 5329, 2), DungeonGateLocs.bridge_middle_1)
            val north = loc(CoordGrid(2885, 5348, 2), DungeonGateLocs.bridge_middle_1)
            allocBridge()
            allocZoneCollision(gate.b)

            level(stats.hitpoints, 69)
            player.setBaseLevel(stats.prayer, 50)
            player.setCurrentLevel(stats.prayer, 50)
            assertEquals(gate.a, use(south, gate.a), "refused at 69")
            assertEquals(50, player.stats[stats.prayer], "a refusal keeps the prayer")

            level(stats.hitpoints, 70)
            val landed = use(south, gate.a, ticks = 16)
            assertEquals(
                CoordGrid(2885, GodWarsCrossings.NORTH_BANK_Z, 2),
                landed,
                "crosses at 70, onto Zamorak's side",
            )
            assertEquals(0, player.stats[stats.prayer], "Zamorak's side drains it")

            player.setCurrentLevel(stats.prayer, 50)
            val back = use(north, gate.b, ticks = 16)
            assertEquals(CoordGrid(2885, GodWarsCrossings.SOUTH_BANK_Z, 2), back, "and back")
            assertEquals(50, player.stats[stats.prayer], "coming back keeps it")
        }

    /** Runs [body] with the crossings asking for their Wiki items, then turns that off again. */
    private fun withItemsRequired(body: () -> Unit) {
        GodWarsCrossingItems.required = true
        try {
            body()
        } finally {
            GodWarsCrossingItems.required = false
        }
    }

    private fun GameTestScope.give(internal: String) {
        player.inv.objs[0] = InvObj(objTypes.values.first { it.internalName == internal })
    }

    private fun GameTestScope.wearItem(pos: Wearpos, internal: String) {
        player.worn[pos.slot] = InvObj(objTypes.values.first { it.internalName == internal })
    }

    @Test
    fun GameTestState.`with items required the gong needs a hammer`() = runGodWarsTest { _ ->
        val gate = DungeonGates.bandosDoor
        val click = gate.clicks.first()
        val door = loc(checkNotNull(click.coords), click.type, LocShape.WallStraight, LocAngle.West)
        allocZoneCollision(gate.b)
        level(stats.strength, 70)
        withItemsRequired {
            player.inv.objs[0] = null
            assertStays(gate.a, use(door, gate.a), "no hammer")
            give("hammer")
            assertEquals(gate.b, use(door, gate.a), "with a hammer")
            player.inv.objs[0] = null
        }
        assertEquals(gate.b, use(door, gate.a), "and off again, none needed")
    }

    @Test
    fun GameTestState.`with items required the pillar needs a crossbow and a grapple bolt`() =
        runGodWarsTest { _ ->
            val gate = DungeonGates.armadylPillar
            val click = gate.clicks.first()
            val pillar = loc(checkNotNull(click.coords), click.type, angle = LocAngle.East)
            allocZoneCollision(gate.b)
            allocPillar()
            level(stats.ranged, 70)
            withItemsRequired {
                player.worn[Wearpos.RightHand.slot] = null
                player.worn[Wearpos.Quiver.slot] = null
                assertStays(gate.a, use(pillar, gate.a), "nothing worn")
                wearItem(Wearpos.RightHand, "crossbow")
                assertStays(gate.a, use(pillar, gate.a), "no grapple bolt")
                wearItem(Wearpos.Quiver, "xbows_grapple_tip_bolt_mithril_rope")
                assertEquals(gate.b, use(pillar, gate.a, ticks = 16), "both worn")
                player.worn[Wearpos.RightHand.slot] = null
                player.worn[Wearpos.Quiver.slot] = null
            }
        }

    @Test
    fun GameTestState.`with items required the rocks start untied and tying takes a rope`() =
        runGodWarsTest { _ ->
            val first = CoordGrid(2913, 5300, 2)
            val untied = loc(first, GodWarsLocs.rock_1_untied, angle = LocAngle.East)
            level(stats.agility, 70)
            withItemsRequired {
                set(GodWarsVarbits.saradomin_rope_1, 0)
                set(GodWarsVarbits.saradomin_rope_2, 0)
                eventBus.publish(DungeonEntered(player, Dungeon.GOD_WARS_DUNGEON))
                assertEquals(0, player.vars[GodWarsVarbits.saradomin_rope_1], "not tied for you")
                assertEquals(0, player.vars[GodWarsVarbits.saradomin_rope_2], "not tied for you")

                player.inv.objs[0] = null
                use(untied, first.translateX(-1))
                assertEquals(0, player.vars[GodWarsVarbits.saradomin_rope_1], "no rope")
                give("rope")
                use(untied, first.translateX(-1))
                assertEquals(1, player.vars[GodWarsVarbits.saradomin_rope_1], "tied")
                assertNull(player.inv.objs[0], "the rope was spent")
            }
        }

    @Test
    fun GameTestState.`the ropes down to saradomin's encampment tie, climb down and climb up`() =
        runGodWarsTest { _ ->
            val first = CoordGrid(2913, 5300, 2)
            val untied = loc(first, GodWarsLocs.rock_1_untied, angle = LocAngle.East)
            set(GodWarsVarbits.saradomin_rope_1, 0)
            level(stats.agility, 69)
            use(untied, first.translateX(-1))
            assertEquals(0, player.vars[GodWarsVarbits.saradomin_rope_1], "not tied at 69")
            level(stats.agility, 70)
            use(untied, first.translateX(-1))
            assertEquals(1, player.vars[GodWarsVarbits.saradomin_rope_1], "tied")

            val tied = loc(first, GodWarsLocs.rock_1_tied, angle = LocAngle.East)
            allocZoneCollision(CoordGrid(2915, 5300, 1))
            allocZoneCollision(CoordGrid(2912, 5300, 2))
            allocZoneCollision(first)
            player.placeAt(first.translateX(-1))
            level(stats.agility, 69)
            player.opLoc2(tied)
            advance(ticks = 6)
            assertStays(first.translateX(-1), player.coords, "refused at 69")
            level(stats.agility, 70)
            player.opLoc2(tied)
            advance(ticks = 6)
            assertEquals(CoordGrid(2915, 5300, 1), player.coords, "down")

            val bottom =
                loc(CoordGrid(2914, 5300, 1), GodWarsLocs.rope_bottom, angle = LocAngle.East)
            player.opLoc1(bottom)
            advance(ticks = 6)
            assertEquals(CoordGrid(2912, 5300, 2), player.coords, "up")
        }

    @Test
    fun GameTestState.`the second rope climbs up to the middle level, not the top`() =
        runGodWarsTest { _ ->
            // The cache's second bottom reuses the first rope's tied loc type, so only its
            // coordinates tell the two apart.
            val bottomAt = CoordGrid(2920, 5274, 0)
            val bottom = loc(bottomAt, GodWarsLocs.rope_bottom, angle = LocAngle.East)
            allocZoneCollision(CoordGrid(2920, 5276, 1))
            player.placeAt(bottomAt.translateX(-1))
            player.opLoc1(bottom)
            advance(ticks = 6)
            assertEquals(CoordGrid(2920, 5276, 1), player.coords, "up to the middle level")
        }

    @Test
    fun GameTestState.`the god wars exit rope leads back to the hole`() = runGodWarsTest { _ ->
        val dungeon = Dungeon.GOD_WARS_DUNGEON
        val exit = dungeon.exits.single()
        allocZoneCollision(dungeon.front)
        val rope = loc(exit.coords, exit.type)
        assertNotEquals(null, rope)
        val landed = use(rope, inside)
        assertEquals(dungeon.front, landed)
    }

    @Test
    fun GameTestState.`karuulm's alchemical doors let you through both ways`() =
        runGodWarsTest { _ ->
            for (gate in listOf(DungeonGates.hydraDoorUpper, DungeonGates.hydraDoorLower)) {
                val click = gate.clicks.first()
                val door =
                    loc(
                        checkNotNull(click.coords),
                        click.type,
                        LocShape.WallStraight,
                        LocAngle.East,
                    )
                allocZoneCollision(gate.b)
                assertEquals(gate.b, use(door, gate.a), "${gate.key} in")
                assertEquals(gate.a, use(door, gate.b), "${gate.key} out")
            }
        }

    private class KaruulmCase(
        val link: DungeonLink,
        val stand: CoordGrid,
        val shape: LocShape,
        val angle: LocAngle,
    )

    private fun karuulmCases(): List<KaruulmCase> {
        fun link(type: LocType, at: CoordGrid) =
            Dungeon.links.first { it.type.id == type.id && it.coords == at }
        fun case(type: LocType, at: CoordGrid, stand: CoordGrid, angle: LocAngle) =
            KaruulmCase(link(type, at), stand, LocShape.CentrepieceStraight, angle)
        return listOf(
            case(
                DungeonLocs.karuulm_stairs,
                CoordGrid(1330, 10205, 0),
                CoordGrid(1329, 10205, 0),
                LocAngle.South,
            ),
            case(
                DungeonLocs.karuulm_stairs_top,
                CoordGrid(1330, 10205, 1),
                CoordGrid(1334, 10205, 1),
                LocAngle.North,
            ),
            case(
                DungeonLocs.karuulm_stairs,
                CoordGrid(1314, 10188, 1),
                CoordGrid(1313, 10188, 1),
                LocAngle.South,
            ),
            case(
                DungeonLocs.karuulm_stairs_top,
                CoordGrid(1314, 10188, 2),
                CoordGrid(1318, 10188, 2),
                LocAngle.North,
            ),
            case(
                DungeonLocs.karuulm_pipe,
                CoordGrid(1316, 10214, 0),
                CoordGrid(1315, 10214, 0),
                LocAngle.South,
            ),
            case(
                DungeonLocs.karuulm_pipe,
                CoordGrid(1346, 10231, 0),
                CoordGrid(1345, 10231, 0),
                LocAngle.North,
            ),
        )
    }

    @Test
    fun GameTestState.`karuulm's stairs join its levels and its pipe asks for 88 agility`() =
        runGodWarsTest { _ ->
            for (case in karuulmCases()) {
                val link = case.link
                val isStairs =
                    link.type.id == DungeonLocs.karuulm_stairs.id ||
                        link.type.id == DungeonLocs.karuulm_stairs_top.id
                assertEquals(!isStairs, link.climb, "${link.coords}: only the pipe is climbed")
                allocZoneCollision(link.dest)
                val obj = loc(link.coords, link.type, case.shape, case.angle)
                level(stats.agility, 87)
                if (link.agility > 0) {
                    assertEquals(case.stand, use(obj, case.stand), "${link.coords} refused at 87")
                }
                level(stats.agility, 99)
                assertEquals(link.dest, use(obj, case.stand), "${link.coords}")
                assertEquals(Dungeon.KARUULM_SLAYER_DUNGEON, Dungeon.containing(link.dest))
            }
        }
}
