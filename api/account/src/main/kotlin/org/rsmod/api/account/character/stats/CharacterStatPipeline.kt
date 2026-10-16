package org.rsmod.api.account.character.stats

import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatTypeList

private typealias Stat = CharacterStatData.Stat

public class CharacterStatPipeline
@Inject
constructor(private val applier: CharacterStatApplier, private val statTypes: StatTypeList) :
    CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val select =
            connection.prepareStatement(
                """
                    SELECT stat_id, vis_level, base_level, fine_xp
                    FROM stats
                    WHERE character_id = ?
                """
                    .trimIndent()
            )

        val stats = ArrayList<Stat>(25)
        select.use {
            select.setInt(1, metadata.characterId)
            it.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    val statId = resultSet.getInt("stat_id")
                    val visLevel = resultSet.getInt("vis_level")
                    val baseLevel = resultSet.getInt("base_level")
                    val fineXp = resultSet.getInt("fine_xp")

                    val stat = Stat(statId, visLevel, baseLevel, fineXp)
                    stats += stat
                }
            }
        }

        metadata.add(applier, CharacterStatData(stats))
    }

    override fun snapshot(player: Player, characterId: Int): CharacterDataStage.Snapshot {
        val stats =
            statTypes.values.map { stat ->
                val visLevel = player.statMap.getCurrentLevel(stat).toInt()
                val baseLevel = player.statMap.getBaseLevel(stat).toInt()
                val fineXp = player.statMap.getFineXP(stat)
                Stat(stat.id, visLevel, baseLevel, fineXp)
            }
        return CharacterDataStage.Snapshot { connection -> save(connection, stats, characterId) }
    }

    private fun save(connection: DatabaseConnection, stats: List<Stat>, characterId: Int) {
        val upsert =
            connection.prepareStatement(
                """
                    INSERT INTO stats (character_id, stat_id, vis_level, base_level, fine_xp)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(character_id, stat_id) DO UPDATE SET
                        vis_level = excluded.vis_level,
                        base_level = excluded.base_level,
                        fine_xp = excluded.fine_xp,
                        updated_at = CASE
                            WHEN stats.fine_xp != excluded.fine_xp THEN CURRENT_TIMESTAMP
                            ELSE stats.updated_at
                        END
                """
                    .trimIndent()
            )

        upsert.use {
            for (stat in stats) {
                it.setInt(1, characterId)
                it.setInt(2, stat.type)
                it.setInt(3, stat.vis)
                it.setInt(4, stat.base)
                it.setInt(5, stat.fineXp)
                upsert.addBatch()
            }
            upsert.executeBatch()
        }
    }
}
