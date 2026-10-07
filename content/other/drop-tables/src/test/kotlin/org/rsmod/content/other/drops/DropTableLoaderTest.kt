package org.rsmod.content.other.drops

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rsmod.api.drops.DropOutcome
import org.rsmod.api.drops.DropStack
import org.rsmod.api.testing.factory.npc.TestNpcTypeFactory
import org.rsmod.api.testing.factory.obj.TestObjTypeFactory
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList

class DropTableLoaderTest {
    private val mapper: ObjectMapper =
        ObjectMapper(TomlFactory())
            .registerKotlinModule()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    private val objFactory = TestObjTypeFactory()
    private val npcFactory = TestNpcTypeFactory()

    private val bones = objFactory.create(526) { name = "Bones" }
    private val bonesDupe = objFactory.create(9000) { name = "Bones" }
    private val bonesPlaceholder =
        objFactory.create(10) {
            name = "Bones"
            placeholdertemplate = 14401
        }
    private val sword =
        objFactory.create(1277) {
            name = "Bronze sword"
            internal = "bronze_sword"
        }
    private val coins =
        objFactory.create(995) {
            name = "Coins"
            stackable = true
        }
    private val coinsNoted =
        objFactory.create(996) {
            name = "Coins"
            certlink = 995
            certtemplate = 799
        }

    private val goblin2 =
        npcFactory.create(3029) {
            name = "Goblin"
            vislevel = 2
            internal = "goblin_2"
        }
    private val goblin13 =
        npcFactory.create(3030) {
            name = "Goblin"
            vislevel = 13
            internal = "goblin_13"
        }
    private val goblin5 =
        npcFactory.create(3031) {
            name = "Goblin"
            vislevel = 5
            internal = "goblin_5"
        }
    private val cow =
        npcFactory.create(2790) {
            name = "Cow"
            vislevel = 2
            internal = "cow"
        }

    private val loader =
        DropTableLoader(
            mapper,
            ObjTypeList(
                listOf(bones, bonesDupe, bonesPlaceholder, sword, coins, coinsNoted)
                    .associateBy { it.id }
                    .toMutableMap()
            ),
            NpcTypeList(listOf(goblin2, goblin13, goblin5, cow).associateBy { it.id }),
        )

    private fun source(name: String, text: String) = DropSource(name, text.toByteArray())

    @Test
    fun `a monster table applies to every npc with the name and level`() {
        val result =
            loader.load(
                listOf(
                    source(
                        "osrs.toml",
                        """
                        [[monster]]
                        name = "goblin"
                        levels = [2, 13]
                        always = [{ item = "Bones" }]
                        main = [{ item = "Coins", qty = "5-9", chance = "3/128" }]
                        """,
                    )
                )
            )

        assertEquals(setOf(goblin2.id, goblin13.id), result.tables.keys)
        assertEquals(emptyList<String>(), result.warnings)

        val table = result.tables.getValue(goblin2.id)
        // The lowest id wins among objs with the same name, and placeholders are never used.
        assertEquals(listOf(DropStack(bones, 1, 1)), table.always)
        val entry = table.main.single()
        assertEquals(3.0 / 128.0, entry.chance)
        assertEquals(DropOutcome.Item(DropStack(coins, 5, 9)), entry.outcome)
    }

    @Test
    fun `without levels every npc with the name gets the table`() {
        val result =
            loader.load(
                listOf(
                    source(
                        "a.toml",
                        "[[monster]]\nname = \"Goblin\"\nalways = [{ item = \"Bones\" }]",
                    )
                )
            )
        assertEquals(setOf(goblin2.id, goblin13.id, goblin5.id), result.tables.keys)
    }

    @Test
    fun `noted items and internal names`() {
        val result =
            loader.load(
                listOf(
                    source(
                        "a.toml",
                        """
                        [[monster]]
                        name = "Cow"
                        always = [{ item = "Coins", qty = "10", noted = true }, { obj = "bronze_sword" }]
                        """,
                    )
                )
            )
        val always = result.tables.getValue(cow.id).always
        assertEquals(listOf(DropStack(coinsNoted, 10, 10), DropStack(sword, 1, 1)), always)
    }

    @Test
    fun `later files add to earlier ones`() {
        val result =
            loader.load(
                listOf(
                    source(
                        "osrs.toml",
                        "[[monster]]\nname = \"Cow\"\nalways = [{ item = \"Bones\" }]",
                    ),
                    source(
                        "isle.toml",
                        "[[monster]]\nname = \"Cow\"\ntertiary = [{ item = \"Coins\", chance = \"1/50\" }]",
                    ),
                )
            )
        val table = result.tables.getValue(cow.id)
        assertEquals(1, table.always.size)
        assertEquals(1, table.tertiary.size)
    }

    @Test
    fun `replace throws the earlier table away`() {
        val result =
            loader.load(
                listOf(
                    source(
                        "osrs.toml",
                        "[[monster]]\nname = \"Cow\"\nalways = [{ item = \"Bones\" }]",
                    ),
                    source(
                        "isle.toml",
                        "[[monster]]\nname = \"Cow\"\nreplace = true\nalways = [{ item = \"Coins\" }]",
                    ),
                )
            )
        assertEquals(listOf(DropStack(coins, 1, 1)), result.tables.getValue(cow.id).always)
    }

    @Test
    fun `an add that makes a table too likely is an error`() {
        val sources =
            listOf(
                source(
                    "osrs.toml",
                    "[[monster]]\nname = \"Cow\"\nmain = [{ item = \"Coins\", chance = \"3/4\" }]",
                ),
                source(
                    "isle.toml",
                    "[[monster]]\nname = \"Cow\"\nmain = [{ item = \"Bones\", chance = \"1/2\" }]",
                ),
            )
        val error = assertThrows<IllegalArgumentException> { loader.load(sources) }
        assertTrue(error.message!!.contains("Cow"), error.message)
    }

    @Test
    fun `shared tables are nested into monster entries`() {
        val result =
            loader.load(
                listOf(
                    source(
                        "a.toml",
                        """
                        [[shared]]
                        name = "Rare drop table"
                        main = [{ item = "Bronze sword", chance = "1/2" }]

                        [[monster]]
                        name = "Cow"
                        main = [{ table = "rare drop table", chance = "1/128" }]
                        """,
                    )
                )
            )
        val outcome = result.tables.getValue(cow.id).main.single().outcome
        val nested = (outcome as DropOutcome.Nested).table
        assertEquals("Rare drop table", nested.name)
        assertEquals(1, nested.main.size)
    }

    @Test
    fun `a shared table that includes itself is an error`() {
        val sources =
            listOf(
                source(
                    "a.toml",
                    """
                    [[shared]]
                    name = "loop"
                    main = [{ table = "loop", chance = "1/2" }]

                    [[monster]]
                    name = "Cow"
                    main = [{ table = "loop", chance = "1/2" }]
                    """,
                )
            )
        assertThrows<IllegalStateException> { loader.load(sources) }
    }

    @Test
    fun `unknown items and monsters are warnings`() {
        val result =
            loader.load(
                listOf(
                    source(
                        "a.toml",
                        """
                        [[monster]]
                        name = "Cow"
                        always = [{ item = "Imaginary sword" }, { item = "Bones" }]
                        main = [{ item = "Nothing", chance = "1/2" }]

                        [[monster]]
                        name = "Ghost"
                        always = [{ item = "Bones" }]
                        """,
                    )
                )
            )
        assertEquals(1, result.tables.getValue(cow.id).always.size)
        assertEquals(2, result.warnings.size)
        assertTrue(result.warnings.any { it.contains("Imaginary sword") })
        assertTrue(result.warnings.any { it.contains("Ghost") })
    }

    @Test
    fun `malformed data stops loading`() {
        assertThrows<IllegalArgumentException> {
            loader.load(
                listOf(
                    source(
                        "a.toml",
                        "[[monster]]\nname = \"Cow\"\nmain = [{ item = \"Bones\", chance = \"Rare\" }]",
                    )
                )
            )
        }
        assertThrows<IllegalArgumentException> {
            loader.load(
                listOf(
                    source("a.toml", "[[monster]]\nname = \"Cow\"\nmain = [{ item = \"Bones\" }]")
                )
            )
        }
        assertThrows<IllegalArgumentException> {
            loader.load(listOf(source("a.toml", "[[monster]\nname")))
        }
    }

    @Test
    fun `ids pick npcs exactly and source labels the monster`() {
        val result =
            loader.load(
                listOf(
                    source(
                        "a.toml",
                        """
                        [[monster]]
                        source = "Goblin"
                        ids = [3029, 3031, 9999]
                        always = [{ item = "Bones" }]
                        """,
                    )
                )
            )
        assertEquals(setOf(goblin2.id, goblin5.id), result.tables.keys)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("9999"))
    }

    @Test
    fun `boost lines mark the matching entries as boosted`() {
        val result =
            loader.load(
                listOf(
                    source(
                        "osrs.toml",
                        """
                        [[monster]]
                        source = "Cow"
                        ids = [2790]
                        main = [
                          { item = "Coins", chance = "1/4" },
                          { item = "Bronze sword", chance = "1/512" },
                        ]
                        tertiary = [{ item = "Bones", chance = "1/10", boosted = true }]
                        """,
                    ),
                    source(
                        "boosted.toml",
                        """
                        [[boost]]
                        source = "cow"
                        item = "bronze sword"

                        [[boost]]
                        source = "Cow"
                        item = "Dragon thing"

                        [[boost]]
                        source = "Chicken"
                        item = "Bronze sword"
                        """,
                    ),
                )
            )
        val table = result.tables.getValue(cow.id)
        assertEquals(listOf(false, true), table.main.map { it.boosted })
        assertEquals(listOf(true), table.tertiary.map { it.boosted })

        assertEquals(1, result.boostedEntries)
        assertEquals(
            listOf("Cow: Dragon thing", "Chicken: Bronze sword").toSet(),
            result.unmatchedBoosts.toSet(),
        )
    }

    @Test
    fun `inherit rolls the drops of another source as well`() {
        val result =
            loader.load(
                listOf(
                    source(
                        "a.toml",
                        """
                        [[monster]]
                        source = "Goblin"
                        ids = [3029]
                        always = [{ item = "Bones" }]

                        [[monster]]
                        source = "Echo Goblin"
                        ids = [2790]
                        inherit = ["Goblin"]
                        tertiary = [{ item = "Bronze sword", chance = "1/25" }]
                        """,
                    )
                )
            )
        val table = result.tables.getValue(cow.id)
        assertEquals("Echo Goblin", table.name)
        assertEquals(1, table.always.size)
        assertEquals(1, table.tertiary.size)
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test
    fun `a table with no npc waits to be attached`() {
        val data =
            """
            [[monster]]
            source = "Echo Goblin"
            tertiary = [{ item = "Bronze sword", chance = "1/25" }]
            """
        val unattached = loader.load(listOf(source("osrs.toml", data)))
        assertEquals(emptyMap<Int, Any>(), unattached.tables)
        assertEquals(emptyList<String>(), unattached.warnings)

        val attached =
            loader.load(
                listOf(
                    source("osrs.toml", data),
                    source(
                        "isle.toml",
                        """
                        [[monster]]
                        source = "Echo Goblin"
                        npcs = ["cow"]
                        attach = true
                        """,
                    ),
                )
            )
        assertEquals(1, attached.tables.getValue(cow.id).tertiary.size)
    }

    @Test
    fun `inheriting or attaching a missing source is a warning`() {
        val result =
            loader.load(
                listOf(
                    source(
                        "a.toml",
                        """
                        [[monster]]
                        source = "Echo Goblin"
                        ids = [2790]
                        inherit = ["Nobody"]
                        always = [{ item = "Bones" }]

                        [[monster]]
                        source = "Ghost"
                        npcs = ["cow"]
                        attach = true
                        """,
                    )
                )
            )
        assertEquals(2, result.warnings.size)
    }

    @Test
    fun `lookup for an npc without a table is null`() {
        val result = loader.load(listOf(source("a.toml", "monster = []")))
        assertNull(result.tables[cow.id])
        assertNotNull(result)
    }
}
