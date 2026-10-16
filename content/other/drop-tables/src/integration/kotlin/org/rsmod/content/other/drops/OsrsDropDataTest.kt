package org.rsmod.content.other.drops

import jakarta.inject.Inject
import java.io.File
import kotlin.reflect.KClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.drops.DropOutcome
import org.rsmod.api.drops.DropTable
import org.rsmod.api.drops.DropTables
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript

/**
 * Loads the real data files (`osrs.toml`, `boosted.toml`, `isle.toml`) against the real cache, so a
 * bad import shows up here and not as a stopped server. Every warning is also written to
 * `isle-drop-warnings.txt` in the temp directory, since the server only logs the first few.
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

    private companion object {
        const val KRAKEN_ID = 492
    }
}
