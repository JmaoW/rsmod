package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.inv.InvObj
import org.rsmod.game.movement.BlockWalk
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

internal object AggroTestNpcs : NpcReferences() {
    val imp = find("godwars_ancient_imp")
    val icefiend = find("godwars_icefiend_1")
    val gorak = find("godwars_gorak")
    val knight = find("godwars_saradomin_knight_1")
    val graardor = find("godwars_bandos_avatar")
    val skree = find("godwars_armadyl_bodyguard_skree")
}

class DungeonGodAggressionTest {
    class Deps @Inject constructor(val registry: NpcRegistry)

    private val inside = CoordGrid(2882, 5310, 2)

    private val spawned = mutableListOf<Npc>()

    /** Npcs outlive a test in the shared world, so every npc a test spawns is removed after it. */
    private fun GameTestState.runAggroTest(body: GameTestScope.() -> Unit) =
        runInjectedGameTest(Deps::class, null, GodItemsScript::class) { deps ->
            try {
                body()
            } finally {
                spawned.forEach { deps.registry.del(it) }
                spawned.clear()
            }
        }

    private fun GameTestScope.wear(pos: Wearpos, internal: String) {
        val type = objTypes.values.first { it.internalName == internal }
        player.worn[pos.slot] = InvObj(type)
        eventBus.publish(HeldEquipEvents.WearposChange(player, pos, type))
    }

    private fun GameTestScope.remove(pos: Wearpos) {
        val type = objTypes[player.worn[pos.slot]!!]
        player.worn[pos.slot] = null
        eventBus.publish(HeldEquipEvents.WearposChange(player, pos, type))
    }

    // Clear tiles around `inside` with a line of sight back to it (found by trying them): east and
    // west 2-3, south 2-3, and 3 east 3 south. North is walled.
    private fun GameTestScope.setup() {
        allocZoneCollision(inside)
        player.placeAt(inside)
    }

    /**
     * Spawns [type] at [coords]. A test npc has no viewer, and an npc that nobody sees does not
     * hunt, so it is told that someone is watching.
     */
    private fun GameTestScope.spawn(type: NpcType, coords: CoordGrid): Npc {
        allocZoneCollision(coords)
        return spawnNpc(coords, npcTypes[type]).also {
            spawned += it
            it.infoProtocol =
                object : NpcInfoProtocol by NoopNpcInfo {
                    override fun isActive(): Boolean = true
                }
        }
    }

    private fun GameTestScope.wait(npc: Npc): Boolean {
        advance(ticks = 6)
        return npc.interaction != null
    }

    @Test
    fun GameTestState.`every dungeon npc is a follower, a leader or a bystander`() = runAggroTest {
        val listed =
            GodWarsNames.leaders +
                GodWarsNames.bystanders +
                God.entries.flatMap { GodWarsNames.followersOf(it) }
        assertEquals(listed.size, listed.toSet().size, "an npc is on two lists")
        val inCache = npcTypes.values.mapNotNull { it.internalName }
        val unlisted = inCache.filter { it.startsWith("godwars_") && it !in listed }.toSortedSet()
        assertTrue(unlisted.isEmpty(), "not on any list: $unlisted")
        val missing = listed.filter { it !in inCache }
        assertTrue(missing.isEmpty(), "not in the cache: $missing")
    }

    @Test
    fun GameTestState.`followers and leaders have their hunt modes after packing`() = runAggroTest {
        for (god in God.entries) {
            for (name in GodWarsNames.followersOf(god)) {
                val type = npcTypes.values.first { it.internalName == name }
                val ranged = name in GodWarsNames.ranged
                assertEquals(GodWarsHunt.of(god, ranged).id, type.huntMode, "$name hunt mode")
                assertEquals(5, type.huntRange, "$name hunt range")
                if (ranged) {
                    assertEquals(GodWarsNames.ATTACK_RANGE, type.attackRange, "$name range")
                }
            }
        }
        val rangedLeaders =
            setOf(
                GodWarsNames.KREE,
                GodWarsNames.SKREE,
                GodWarsNames.GEERIN,
                GodWarsNames.STEELWILL,
                GodWarsNames.GRIMSPIKE,
                GodWarsNames.GROWLER,
                GodWarsNames.BREE,
                GodWarsNames.ZAKLN,
                GodWarsNames.BALFRUG,
            )
        for (name in GodWarsNames.leaders) {
            val type = npcTypes.values.first { it.internalName == name }
            val mode = if (name in rangedLeaders) GodWarsHunt.leaderRanged else GodWarsHunt.leader
            assertEquals(mode.id, type.huntMode, "$name hunt mode")
            val range = if (name == GodWarsNames.KREE) 16 else 8
            assertEquals(range, type.huntRange, "$name hunt range")
            assertEquals(24, type.maxRange, "$name leash")
            assertEquals(150, type.respawnRate, "$name respawn")
            assertEquals(BlockWalk.None, type.blockWalk, "$name collision")
        }
    }

    @Test
    fun GameTestState.`followers still block movement`() = runAggroTest {
        for (name in God.entries.flatMap { GodWarsNames.followersOf(it) }) {
            val type = npcTypes.values.first { it.internalName == name }
            assertNotEquals(BlockWalk.None, type.blockWalk, "$name collision")
        }
    }

    @Test
    fun GameTestState.`icefiends and goraks are zamorakian for aggression only`() = runAggroTest {
        assertEquals(God.ZAMORAK, GodWarsNames.godOf("godwars_icefiend_1"))
        assertEquals(God.SARADOMIN, God.ofFollower("godwars_icefiend_1"))
        assertEquals(God.ZAMORAK, GodWarsNames.godOf("godwars_gorak"))
        assertEquals(God.BANDOS, God.ofFollower("godwars_gorak"))
        assertEquals(null, GodWarsNames.godOf("godwars_bandos_avatar"))
    }

    @Test
    fun GameTestState.`a follower attacks a player who wears nothing of its god`() = runAggroTest {
        setup()
        val imp = spawn(AggroTestNpcs.imp, inside.translateX(3))
        val knight = spawn(AggroTestNpcs.knight, inside.translate(3, -3))
        assertTrue(wait(imp), "zamorak imp")
        assertTrue(knight.interaction != null, "saradomin knight")
    }

    @Test
    fun GameTestState.`one item of a god makes its followers leave the player alone`() =
        runAggroTest {
            setup()
            wear(Wearpos.Back, "zamorak_cape")
            val imp = spawn(AggroTestNpcs.imp, inside.translateX(3))
            val icefiend = spawn(AggroTestNpcs.icefiend, inside.translateX(-3))
            val gorak = spawn(AggroTestNpcs.gorak, inside.translateZ(-3))
            val knight = spawn(AggroTestNpcs.knight, inside.translate(3, -3))
            advance(ticks = 6)
            assertFalse(imp.interaction != null, "imp is zamorak")
            assertFalse(icefiend.interaction != null, "icefiends are zamorakian")
            assertFalse(gorak.interaction != null, "goraks are zamorakian")
            assertTrue(knight.interaction != null, "another god's items do not help")
        }

    @Test
    fun GameTestState.`the other three gods' items do not protect from a god's followers`() =
        runAggroTest {
            setup()
            wear(Wearpos.Back, "saradomin_cape")
            wear(Wearpos.Torso, "bandos_chestplate")
            wear(Wearpos.Hat, "armadyl_helmet")
            val imp = spawn(AggroTestNpcs.imp, inside.translateX(3))
            assertTrue(wait(imp), "zamorak imp")
        }

    @Test
    fun GameTestState.`the generals and bodyguards ignore god items`() = runAggroTest {
        setup()
        wear(Wearpos.Back, "zamorak_cape")
        wear(Wearpos.Torso, "bandos_chestplate")
        wear(Wearpos.Hat, "armadyl_helmet")
        wear(Wearpos.Feet, "blessed_boots_saradomin")
        val graardor = spawn(AggroTestNpcs.graardor, inside.translateX(3))
        val skree = spawn(AggroTestNpcs.skree, inside.translateZ(-3))
        advance(ticks = 6)
        assertTrue(graardor.interaction != null, "graardor")
        assertTrue(skree.interaction != null, "skree")
    }

    @Test
    fun GameTestState.`a monster already on the player keeps attacking when the item goes on`() =
        runAggroTest {
            setup()
            val imp = spawn(AggroTestNpcs.imp, inside.translateX(3))
            assertTrue(wait(imp), "aggressive without the item")
            wear(Wearpos.Back, "zamorak_cape")
            advance(ticks = 3)
            assertTrue(imp.interaction != null, "still on the player")
            assertNotEquals(null, imp.interaction)
        }

    @Test
    fun GameTestState.`a monster that was calm turns aggressive when the item comes off`() =
        runAggroTest {
            setup()
            wear(Wearpos.Back, "zamorak_cape")
            val imp = spawn(AggroTestNpcs.imp, inside.translateX(3))
            advance(ticks = 4)
            assertFalse(imp.interaction != null, "calm while the item is worn")
            remove(Wearpos.Back)
            assertTrue(wait(imp), "aggressive once nothing is worn")
        }
}
