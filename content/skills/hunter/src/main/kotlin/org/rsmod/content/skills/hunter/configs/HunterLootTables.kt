package org.rsmod.content.skills.hunter.configs

import jakarta.inject.Inject
import org.rsmod.api.random.GameRandom
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList

/**
 * What an impling jar holds. The tables come from the OSRS Wiki through the tsps hunter data (see
 * `docs/isle_of_souls_hunter_plan.md`) and are kept in `impling_loot.tsv` (one table for each of
 * the twelve implings) and `lucky_loot.tsv` (the five reward casket tables a lucky impling draws
 * on). Items are named by their internal cache names; ones this cache does not have are left out.
 */
class HunterLootTables @Inject constructor(private val objTypes: ObjTypeList) {
    /** One line of a table: [obj] (nothing, when `null`), how often, how many and whether noted. */
    class Row(
        val obj: ObjType?,
        val weight: Double,
        val min: Int,
        val max: Int,
        val noted: Boolean,
    )

    /** What one roll gave: [count] of [obj], [noted] or not. */
    class Drop(val obj: ObjType, val count: Int, val noted: Boolean)

    private val impling: Map<String, List<Row>> by lazy { load("impling_loot.tsv") }

    private val lucky: Map<String, List<Row>> by lazy { load("lucky_loot.tsv") }

    /** One roll on the loot table of the impling called [key] (such as `nature`). */
    fun implingLoot(key: String, random: GameRandom): Drop? {
        val table = impling[key.lowercase()] ?: return null
        return roll(table, random)
    }

    /** One roll on a lucky impling's loot: one of the five casket tiers, then a roll on it. */
    fun luckyLoot(random: GameRandom): Drop? {
        val tiers = lucky.values.toList()
        if (tiers.isEmpty()) {
            return null
        }
        return roll(tiers[random.of(tiers.size)], random)
    }

    private fun roll(table: List<Row>, random: GameRandom): Drop? {
        val total = table.sumOf { it.weight }
        var draw = random.randomDouble() * total
        var pick = table.last()
        for (row in table) {
            draw -= row.weight
            if (draw < 0) {
                pick = row
                break
            }
        }
        val obj = pick.obj ?: return null
        val count =
            if (pick.max > pick.min) pick.min + random.of(pick.max - pick.min + 1) else pick.min
        return Drop(obj, count, pick.noted)
    }

    private fun load(file: String): Map<String, List<Row>> {
        val byName =
            objTypes.values.filter { it.internalName != null }.associateBy { it.internalName }
        val stream =
            javaClass.getResourceAsStream("/org/rsmod/content/skills/hunter/$file")
                ?: error("Missing hunter loot table: $file")
        val tables = LinkedHashMap<String, MutableList<Row>>()
        stream.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue
                }
                val parts = line.split('\t')
                val obj = if (parts[1] == "-") null else byName[parts[1]]
                if (obj == null && parts[1] != "-") {
                    continue
                }
                val row =
                    Row(
                        obj,
                        parts[2].toDouble(),
                        parts[3].toInt(),
                        parts[4].toInt(),
                        parts[5] == "1",
                    )
                tables.getOrPut(parts[0]) { ArrayList() } += row
            }
        }
        return tables
    }
}
