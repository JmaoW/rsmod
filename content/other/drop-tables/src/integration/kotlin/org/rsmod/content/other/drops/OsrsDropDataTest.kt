package org.rsmod.content.other.drops

import jakarta.inject.Inject
import java.io.File
import kotlin.reflect.KClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.drops.DropOutcome
import org.rsmod.api.drops.DropTable
import org.rsmod.api.drops.DropTables
import org.rsmod.api.drops.DropVariant
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript

/**
 * Loads the real data files (`osrs.toml`, `boosted.toml`, `revenants.toml`, `isle.toml`) against
 * the real cache, so a bad import shows up here and not as a stopped server. Every warning is also
 * written to `isle-drop-warnings.txt` in the temp directory, since the server only logs the first
 * few.
 */
class OsrsDropDataTest {
    class Deps
    @Inject
    constructor(val dropTables: DropTables, val npcTypes: NpcTypeList, val objTypes: ObjTypeList)

    @Suppress("UNCHECKED_CAST")
    private val dropsScript: KClass<out PluginScript> =
        Class.forName("org.rsmod.content.other.drops.DropsScript").kotlin
            as KClass<out PluginScript>

    private fun GameTestState.runRealData(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, null, dropsScript, testBody = body)

    private fun Deps.items(table: DropTable): Set<String> =
        (table.always.map { objTypes[it.obj].name } +
                (table.main + table.tertiary).mapNotNull {
                    (it.outcome as? DropOutcome.Item)?.stack?.let { stack ->
                        objTypes[stack.obj].name
                    }
                })
            .toSet()

    private fun Deps.itemsOf(name: String): Set<String> =
        npcTypes.values
            .filter { it.name == name }
            .mapNotNull { dropTables.forNpc(it.id) }
            .flatMap { items(it) }
            .toSet()

    @Test
    fun GameTestState.`the real drop data loads and covers the world's monsters`() =
        runRealData { deps ->
            File(System.getProperty("java.io.tmpdir"), "isle-drop-warnings.txt")
                .writeText(DropDataReport.warnings.joinToString("\n"))
            File(System.getProperty("java.io.tmpdir"), "isle-drop-unmatched-boosts.txt")
                .writeText(DropDataReport.unmatchedBoosts.joinToString("\n"))

            // The world spawn list has about 1,640 attackable npc types that have a table.
            assertTrue(deps.dropTables.size > 1_500, "only ${deps.dropTables.size} tables loaded")
            assertEquals(
                emptyList<String>(),
                DropDataReport.warnings.filter { "no npc with id" in it },
            )
        }

    @Test
    fun GameTestState
        .`the hill giants' and moss giants' keys are boosted, the bosses' own are not`() =
        runRealData { deps ->
            fun boostedKeys(name: String, key: String): List<Boolean> =
                deps.npcTypes.values
                    .filter { it.name == name }
                    .mapNotNull { deps.dropTables.forNpc(it.id) }
                    .flatMap { it.main + it.tertiary }
                    .filter {
                        val stack = (it.outcome as? DropOutcome.Item)?.stack
                        stack != null && deps.objTypes[stack.obj].name == key
                    }
                    .map { it.boosted }

            for ((monster, key) in
                listOf("Hill Giant" to "Giant key", "Moss giant" to "Mossy key")) {
                val flags = boostedKeys(monster, key)
                assertTrue(flags.isNotEmpty(), "$monster drops a $key")
                assertTrue(flags.all { it }, "$monster's $key is boosted")
            }
            for ((boss, key) in listOf("Obor" to "Giant key", "Bryophyta" to "Mossy key")) {
                val flags = boostedKeys(boss, key)
                assertTrue(flags.isNotEmpty(), "$boss drops a $key")
                assertFalse(flags.any { it }, "$boss's $key is not boosted")
            }
        }

    @Test
    fun GameTestState.`the cave kraken has a table, and so does its whirlpool`() =
        runRealData { deps ->
            val items = deps.itemsOf("Cave kraken")
            assertTrue("Kraken tentacle" in items, "kraken tentacle in $items")
            assertTrue("Uncharged trident" in items, "uncharged trident in $items")
            assertTrue(deps.dropTables.forNpc(KRAKEN_ID) != null, "the cave kraken npc")
        }

    @Test
    fun GameTestState.`drops use the real item when several share a name`() = runRealData { deps ->
        val ids = HashSet<Int>()
        for (npc in deps.npcTypes.values) {
            val table = deps.dropTables.forNpc(npc.id) ?: continue
            table.always.forEach { ids.add(it.obj.id) }
            (table.main + table.tertiary).forEach {
                (it.outcome as? DropOutcome.Item)?.let { item -> ids.add(item.stack.obj.id) }
            }
        }
        // `fake_coins` (617) has the same name as the real coins (995).
        assertTrue(995 in ids)
        assertTrue(617 !in ids)
        assertTrue(712 !in ids, "cup of tea resolved to the display copy")
        assertTrue(2513 !in ids, "dragon chainbody resolved to the wrong copy")
        assertTrue(4150 !in ids, "broad arrows resolved to the slayer guide display copy")
        val dropped =
            deps.npcTypes.values
                .mapNotNull { deps.dropTables.forNpc(it.id) }
                .flatMap { deps.items(it) }
                .toSet()
        for (name in listOf("Looting bag", "Wolf bone", "Jackal bone", "Cave goblin skull")) {
            assertTrue(name !in dropped, "$name is excluded from drops")
        }
        for (name in listOf("Monkey bones", "Snake corpse", "Mangled bones", "Wolf bones")) {
            assertTrue(name in dropped, "$name still drops")
        }
    }

    @Test
    fun GameTestState.`chickens drop feathers for fletching`() = runRealData { deps ->
        val chickens = deps.npcTypes.values.filter { it.name == "Chicken" }
        val withTable = chickens.mapNotNull { deps.dropTables.forNpc(it.id) }
        assertTrue(withTable.isNotEmpty(), "no chicken has a drop table")
        // The Tutorial Island chicken is a separate wiki page that drops bones only.
        assertTrue(withTable.count { "Feather" in deps.items(it) } >= 10)
        for (table in withTable) {
            assertTrue("Bones" in deps.items(table))
        }
    }

    @Test
    fun GameTestState.`common monsters have their real drops`() = runRealData { deps ->
        assertTrue("Big bones" in deps.itemsOf("Hill Giant"))
        assertTrue("Cowhide" in deps.itemsOf("Cow"))
        assertTrue("Raw beef" in deps.itemsOf("Cow"))
        assertTrue("Bones" in deps.itemsOf("Man"))
        assertTrue("Grimy ranarr weed" in deps.itemsOf("Moss giant"))
        assertTrue("Limpwurt seed" in deps.itemsOf("Moss giant"))
        assertTrue("Chaos talisman" in deps.itemsOf("Dagannoth Rex"))
        assertTrue("Abyssal whip" in deps.itemsOf("Abyssal demon"))
    }

    /**
     * The chance of the unique drop table (the amulet is 2 in 5 of it, so this is 2.5 times it).
     */
    private fun Deps.uniqueChance(table: DropTable): Double =
        table.main
            .plus(table.tertiary)
            .first {
                (it.outcome as? DropOutcome.Item)?.stack?.let { stack ->
                    objTypes[stack.obj].name == "Amulet of avarice"
                } == true
            }
            .chance * 2.5

    @Test
    fun GameTestState.`revenant unique rates follow the wiki for off task, on task and skulled`() =
        runRealData { deps ->
            // The wiki's goblin rates: 1 in 19,546.91 off task and skulled, 1 in 3,909.09 on task.
            val goblin = deps.npcTypes.values.first { it.name == "Revenant goblin" }
            val usual = deps.dropTables[goblin]!!
            val onTask = deps.dropTables.variantOf(goblin, DropVariant.ON_TASK)!!
            val skulled = deps.dropTables.variantOf(goblin, DropVariant.SKULLED)!!
            val both = deps.dropTables.variantOf(goblin, DropVariant.ON_TASK_SKULLED)!!

            assertEquals(1.0 / 19546.9, deps.uniqueChance(usual), 1e-8)
            assertEquals(1.0 / 3909.09, deps.uniqueChance(onTask), 1e-8)
            assertEquals(1.0 / 10750.8, deps.uniqueChance(skulled), 1e-8)
            assertEquals(1.0 / 2149.89, deps.uniqueChance(both), 1e-8)
        }

    @Test
    fun GameTestState.`revenants share the wiki's table, with coins only below level 132`() =
        runRealData { deps ->
            assertEquals(emptyList<String>(), DropDataReport.warnings.filter { "revenants" in it })

            val revenants = REVENANT_NAMES.dropLast(1)
            for (name in revenants) {
                assertTrue("Revenant ether" in deps.itemsOf(name), "$name has no ether")
                assertTrue("Amulet of avarice" in deps.itemsOf(name), "$name has no amulet")
            }
            assertTrue("Coins" in deps.itemsOf("Revenant goblin"))
            assertFalse("Coins" in deps.itemsOf("Revenant dragon"))
            assertTrue("Imp champion scroll" in deps.itemsOf("Revenant imp"))
            assertTrue("Amulet of avarice" in deps.itemsOf("Revenant maledictus"))
        }

    private companion object {
        const val KRAKEN_ID = 492

        /** The wiki's revenants, Maledictus last (it is not implemented yet). */
        val REVENANT_NAMES =
            listOf(
                "Revenant goblin",
                "Revenant pyrefiend",
                "Revenant hobgoblin",
                "Revenant cyclops",
                "Revenant imp",
                "Revenant hellhound",
                "Revenant demon",
                "Revenant ork",
                "Revenant dark beast",
                "Revenant knight",
                "Revenant dragon",
                "Revenant maledictus",
            )
    }
}
