package org.rsmod.api.account.character.inv

import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player
import org.rsmod.game.type.inv.InvScope

private typealias CharacterInventory = CharacterInventoryData.Inventory

private typealias CharacterObj = CharacterInventoryData.Obj

public class CharacterInventoryPipeline
@Inject
constructor(private val applier: CharacterInventoryApplier) : CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val inventories = selectInventories(connection, metadata)

        // Avoid a malformed query if no inventories exist.
        if (inventories.isEmpty()) {
            metadata.add(applier, CharacterInventoryData(inventories))
            return
        }

        val rowInventories = inventories.associateBy { it.rowId }

        val placeholders = (0 until inventories.size).joinToString(",") { "?" }
        val select =
            connection.prepareStatement(
                """
                    SELECT inventories_id, slot, obj, count, vars
                    FROM inventory_objs
                    WHERE inventories_id IN ($placeholders)
                """
                    .trimIndent()
            )

        select.use {
            inventories.forEachIndexed { index, inventory -> it.setInt(1 + index, inventory.rowId) }
            it.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    val inventoriesRow = resultSet.getInt("inventories_id")
                    val slot = resultSet.getInt("slot")
                    val obj = resultSet.getInt("obj")
                    val count = resultSet.getInt("count")
                    val vars = resultSet.getInt("vars")

                    val inventory = rowInventories.getValue(inventoriesRow)
                    inventory.objs[slot] = CharacterObj(obj, count, vars)
                }
            }
        }

        metadata.add(applier, CharacterInventoryData(inventories))
    }

    private fun selectInventories(
        connection: DatabaseConnection,
        metadata: CharacterMetadataList,
    ): List<CharacterInventory> {
        val inventories = ArrayList<CharacterInventory>(4)

        val select =
            connection.prepareStatement(
                """
                    SELECT id, inv_type
                    FROM inventories
                    WHERE character_id = ?
                """
                    .trimIndent()
            )

        select.use {
            it.setInt(1, metadata.characterId)
            it.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    val id = resultSet.getInt("id")
                    val type = resultSet.getInt("inv_type")
                    inventories += CharacterInventory(rowId = id, type = type)
                }
            }
        }

        return inventories
    }

    override fun snapshot(player: Player, characterId: Int): CharacterDataStage.Snapshot {
        val persistentInvs =
            player.invMap.values
                .filter { it.type.scope == InvScope.Perm }
                .map { inventory ->
                    val objs = arrayOfNulls<CharacterObj>(inventory.size)
                    for (i in inventory.indices) {
                        val obj = inventory[i] ?: continue
                        objs[i] = CharacterObj(obj.id, obj.count, obj.vars)
                    }
                    InventorySnapshot(inventory.type.id, objs)
                }
        return CharacterDataStage.Snapshot { connection ->
            save(connection, persistentInvs, characterId)
        }
    }

    /** A copy of a persistent inventory: its type id, and the object (if any) in each slot. */
    private class InventorySnapshot(val type: Int, val objs: Array<CharacterObj?>)

    private fun save(
        connection: DatabaseConnection,
        persistentInvs: List<InventorySnapshot>,
        characterId: Int,
    ) {
        deleteStaleInventories(connection, characterId, persistentInvs)

        val delete =
            connection.prepareStatement(
                """
                    DELETE FROM inventory_objs
                    WHERE inventories_id = ? AND slot = ?
                """
                    .trimIndent()
            )

        // Note: Not all database engines support `ON CONFLICT`. This syntax works with our current
        // database setup (sqlite), but may need to be adapted for others (e.g., mysql uses
        // `ON DUPLICATE KEY UPDATE` for similar functionality).
        val upsert =
            connection.prepareStatement(
                """
                    INSERT INTO inventory_objs (inventories_id, slot, obj, count, vars)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(inventories_id, slot) DO UPDATE SET
                        obj = excluded.obj,
                        count = excluded.count,
                        vars = excluded.vars
                """
                    .trimIndent()
            )

        delete.use { delete ->
            upsert.use { upsert ->
                for (inventory in persistentInvs) {
                    val type = inventory.type

                    val inventoryRowId = getOrInsertInventoryRowId(connection, characterId, type)
                    if (inventoryRowId == null) {
                        val message =
                            "Fatal error fetching inventory row for: type=$type " +
                                "(characterId=$characterId)"
                        throw IllegalStateException(message)
                    }

                    for (i in inventory.objs.indices) {
                        if (inventory.objs[i] == null) {
                            delete.setInt(1, inventoryRowId)
                            delete.setInt(2, i)
                            delete.addBatch()
                        }
                    }

                    for (i in inventory.objs.indices) {
                        val obj = inventory.objs[i]
                        if (obj != null) {
                            upsert.setInt(1, inventoryRowId)
                            upsert.setInt(2, i)
                            upsert.setInt(3, obj.type)
                            upsert.setInt(4, obj.count)
                            upsert.setInt(5, obj.vars)
                            upsert.addBatch()
                        }
                    }
                }
                delete.executeBatch()
                upsert.executeBatch()
            }
        }
    }

    private fun deleteStaleInventories(
        connection: DatabaseConnection,
        characterId: Int,
        inventories: Collection<InventorySnapshot>,
    ) {
        // Important: This function assumes `inventory_objs` references `inventories` with
        // `ON DELETE CASCADE`, so deleting a parent inventory also deletes its associated
        // `inventory_objs` rows.
        if (inventories.isNotEmpty()) {
            val activeInvPlaceholders = (0 until inventories.size).joinToString(",") { "?" }
            val deleteStaleInventories =
                connection.prepareStatement(
                    """
                        DELETE FROM inventories
                        WHERE character_id = ? AND inv_type NOT IN ($activeInvPlaceholders)
                    """
                        .trimIndent()
                )

            deleteStaleInventories.use {
                it.setInt(1, characterId)
                inventories.forEachIndexed { index, inv -> it.setInt(2 + index, inv.type) }
                it.executeUpdate()
            }
        } else {
            val deleteAllInventories =
                connection.prepareStatement("DELETE FROM inventories WHERE character_id = ?")

            deleteAllInventories.use {
                it.setInt(1, characterId)
                it.executeUpdate()
            }
        }
    }

    private fun getOrInsertInventoryRowId(
        connection: DatabaseConnection,
        characterId: Int,
        invType: Int,
    ): Int? {
        // Note: Not all database engines support `ON CONFLICT`. This syntax works with our current
        // database setup (sqlite), but may need to be adapted for others (e.g., mysql uses
        // `INSERT IGNORE`).
        val insert =
            connection.prepareStatement(
                """
                    INSERT INTO inventories (character_id, inv_type)
                    VALUES (?, ?)
                    ON CONFLICT(character_id, inv_type) DO NOTHING
                """
                    .trimIndent()
            )

        insert.use {
            it.setInt(1, characterId)
            it.setInt(2, invType)
            it.executeUpdate()
        }

        val select =
            connection.prepareStatement(
                """
                    SELECT id FROM inventories
                    WHERE character_id = ? AND inv_type = ?
                """
                    .trimIndent()
            )

        select.use {
            it.setInt(1, characterId)
            it.setInt(2, invType)
            val rowId =
                select.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getInt("id")
                    } else {
                        null
                    }
                }
            return rowId
        }
    }
}
