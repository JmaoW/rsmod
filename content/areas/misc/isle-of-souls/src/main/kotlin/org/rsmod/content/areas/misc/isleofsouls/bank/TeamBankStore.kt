package org.rsmod.content.areas.misc.isleofsouls.bank

import org.rsmod.content.areas.misc.isleofsouls.teams.teamJson

/** One obj in a saved bank slot. */
data class StoredObj(val id: Int, val count: Int, val vars: Int)

/**
 * A team's saved bank: what is in each slot, and the tab layout (how many items each bank tab
 * holds, in the order the bank module lists its tabs). The layout is what makes the slots read as
 * tabs, so it is saved with the items and must always travel with them.
 */
class TeamBankData(val layout: List<Int>, val objs: Map<Int, StoredObj>)

/**
 * Every team's saved bank, as `team-banks.json`. Plain data with no game dependencies.
 *
 * This is the source of truth for a team's bank. A player's own saved bank is only ever a copy of
 * it, and nothing reads a player's copy back.
 */
class TeamBankStore {
    private val banks = linkedMapOf<Int, TeamBankData>()

    val teamIds: Set<Int>
        get() = banks.keys

    operator fun get(teamId: Int): TeamBankData? = banks[teamId]

    operator fun set(teamId: Int, data: TeamBankData) {
        banks[teamId] = data
    }

    fun toJson(): String {
        val root = teamJson.createObjectNode()
        val teams = root.putObject("teams")
        for ((teamId, data) in banks) {
            val node = teams.putObject(teamId.toString())
            val layout = node.putArray("layout")
            data.layout.forEach(layout::add)
            val items = node.putObject("items")
            for ((slot, obj) in data.objs.toSortedMap()) {
                val entry = items.putArray(slot.toString())
                entry.add(obj.id)
                entry.add(obj.count)
                entry.add(obj.vars)
            }
        }
        return teamJson.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    companion object {
        /** Parses `team-banks.json`, throwing [IllegalArgumentException] with a reason. */
        fun parse(json: String): TeamBankStore {
            val root = teamJson.readTree(json)
            val teams = root?.get("teams")
            require(teams != null && teams.isObject) {
                "The file needs a top-level \"teams\" object."
            }

            val store = TeamBankStore()
            for ((key, node) in teams.properties()) {
                val teamId = requireNotNull(key.toIntOrNull()) { "\"$key\" is not a team id." }

                val layoutNode = node.get("layout")
                require(layoutNode != null && layoutNode.isArray) {
                    "Team $teamId needs a \"layout\" list."
                }
                val layout =
                    layoutNode.map {
                        require(it.isInt && it.intValue() >= 0) {
                            "Team $teamId has a layout entry that is not a size."
                        }
                        it.intValue()
                    }

                val objs = linkedMapOf<Int, StoredObj>()
                for ((slotKey, entry) in node.get("items")?.properties().orEmpty()) {
                    val slot =
                        requireNotNull(slotKey.toIntOrNull()) {
                            "\"$slotKey\" is not a slot (team $teamId)."
                        }
                    require(slot >= 0 && entry.isArray && entry.size() == 3) {
                        "Slot $slot (team $teamId) must be [obj, count, vars]."
                    }
                    val id = entry[0]
                    val count = entry[1]
                    val vars = entry[2]
                    require(id.isInt && count.isInt && vars.isInt && count.intValue() >= 0) {
                        "Slot $slot (team $teamId) must be three whole numbers, with a count of 0 or more."
                    }
                    objs[slot] = StoredObj(id.intValue(), count.intValue(), vars.intValue())
                }
                store.banks[teamId] = TeamBankData(layout, objs)
            }
            return store
        }
    }
}
