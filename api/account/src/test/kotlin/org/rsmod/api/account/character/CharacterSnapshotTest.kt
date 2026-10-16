package org.rsmod.api.account.character

import java.sql.Connection
import java.sql.DriverManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.rsmod.api.account.character.inv.CharacterInventoryApplier
import org.rsmod.api.account.character.inv.CharacterInventoryPipeline
import org.rsmod.api.account.character.stats.CharacterStatApplier
import org.rsmod.api.account.character.stats.CharacterStatPipeline
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.testing.factory.invTypeFactory
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.stat.StatTypeBuilder
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.game.type.util.UncheckedType

/**
 * A save is written on another thread after it is taken, while the player keeps playing. These
 * check that a snapshot holds the state from when it was taken, not from when it was written.
 */
@OptIn(UncheckedType::class)
class CharacterSnapshotTest {
    private lateinit var connection: Connection

    private val statTypes =
        StatTypeList((0..1).associateWith { StatTypeBuilder("stat_$it").build(it) })
    private val invType = invTypeFactory.createInv()

    @BeforeEach
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use {
            it.execute("PRAGMA foreign_keys = ON")
            it.execute(
                """
                CREATE TABLE stats (
                    character_id INTEGER NOT NULL,
                    stat_id INTEGER NOT NULL,
                    vis_level INTEGER NOT NULL,
                    base_level INTEGER NOT NULL,
                    fine_xp INTEGER NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (character_id, stat_id)
                )
                """
            )
            it.execute(
                """
                CREATE TABLE inventories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    character_id INTEGER NOT NULL,
                    inv_type INTEGER NOT NULL,
                    UNIQUE (character_id, inv_type)
                )
                """
            )
            it.execute(
                """
                CREATE TABLE inventory_objs (
                    inventories_id INTEGER NOT NULL,
                    slot INTEGER NOT NULL,
                    obj INTEGER NOT NULL,
                    count INTEGER NOT NULL,
                    vars INTEGER NOT NULL,
                    PRIMARY KEY (inventories_id, slot),
                    FOREIGN KEY (inventories_id) REFERENCES inventories(id) ON DELETE CASCADE
                )
                """
            )
        }
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `stat snapshot keeps the xp from when it was taken`() {
        val player = Player()
        val stat = statTypes.getValue(0)
        player.statMap.setFineXP(stat, 1000)
        player.statMap.setBaseLevel(stat, 5)
        player.statMap.setCurrentLevel(stat, 4)

        val pipeline = CharacterStatPipeline(CharacterStatApplier(statTypes), statTypes)
        val snapshot = pipeline.snapshot(player, characterId = 1)

        player.statMap.setFineXP(stat, 2000)
        player.statMap.setBaseLevel(stat, 6)
        snapshot.save(DatabaseConnection(connection))

        val saved =
            query("SELECT vis_level, base_level, fine_xp FROM stats WHERE stat_id = 0") {
                listOf(it.getInt(1), it.getInt(2), it.getInt(3))
            }
        assertEquals(listOf(listOf(4, 5, 1000)), saved)
    }

    @Test
    fun `stat snapshot saves every stat`() {
        val player = Player()
        val pipeline = CharacterStatPipeline(CharacterStatApplier(statTypes), statTypes)
        pipeline.snapshot(player, characterId = 1).save(DatabaseConnection(connection))

        assertEquals(
            listOf(0, 1),
            query("SELECT stat_id FROM stats ORDER BY stat_id") { it.getInt(1) },
        )
    }

    @Test
    fun `inventory snapshot keeps the items from when it was taken`() {
        val player = Player()
        val inventory = Inventory.create(invType)
        player.invMap[invType] = inventory
        inventory[0] = InvObj(995, 100)
        inventory[3] = InvObj(1511, 2, 7)

        val pipeline = inventoryPipeline()
        val snapshot = pipeline.snapshot(player, characterId = 1)

        // Moves, removes and adds that happen after the snapshot must not be part of it.
        inventory[0] = null
        inventory[1] = InvObj(995, 100)
        inventory[3] = InvObj(1511, 9)
        snapshot.save(DatabaseConnection(connection))

        val saved =
            query("SELECT slot, obj, count, vars FROM inventory_objs ORDER BY slot") {
                listOf(it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4))
            }
        assertEquals(listOf(listOf(0, 995, 100, 0), listOf(3, 1511, 2, 7)), saved)
    }

    @Test
    fun `inventory snapshot replaces what an earlier save wrote`() {
        val player = Player()
        val inventory = Inventory.create(invType)
        player.invMap[invType] = inventory
        inventory[0] = InvObj(995, 100)
        inventory[1] = InvObj(1511, 2)

        val pipeline = inventoryPipeline()
        pipeline.snapshot(player, characterId = 1).save(DatabaseConnection(connection))

        inventory[0] = null
        inventory[1] = InvObj(1511, 5)
        pipeline.snapshot(player, characterId = 1).save(DatabaseConnection(connection))

        val saved =
            query("SELECT slot, obj, count FROM inventory_objs ORDER BY slot") {
                listOf(it.getInt(1), it.getInt(2), it.getInt(3))
            }
        assertEquals(listOf(listOf(1, 1511, 5)), saved)
    }

    private fun inventoryPipeline() =
        CharacterInventoryPipeline(
            CharacterInventoryApplier(InvTypeList(mutableMapOf(invType.id to invType)))
        )

    private fun <T> query(sql: String, map: (java.sql.ResultSet) -> T): List<T> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                val rows = ArrayList<T>()
                while (rs.next()) {
                    rows += map(rs)
                }
                rows
            }
        }
}
