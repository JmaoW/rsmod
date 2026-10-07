package org.rsmod.content.areas.misc.isleofsouls.teams

/**
 * Each team's shared league progress: when every task was completed (epoch milliseconds) and the
 * running total of each counted-task counter. Plain data with no game dependencies.
 *
 * This is the source of truth for team members. Their own league varps are only a copy of it, and
 * nothing ever copies a player's varps back into the store.
 */
class TeamProgressStore {
    private class Entry {
        val completed = linkedMapOf<Int, Long>()
        val counters = linkedMapOf<String, Int>()
    }

    private val entries = linkedMapOf<Int, Entry>()

    val teamIds: Set<Int>
        get() = entries.keys

    /** Task id to completion time, in completion order. */
    fun completed(teamId: Int): Map<Int, Long> = entries[teamId]?.completed ?: emptyMap()

    fun isComplete(teamId: Int, taskId: Int): Boolean =
        entries[teamId]?.completed?.containsKey(taskId) == true

    /** Records [taskId] as done at [at]. Returns `false` if it was already done. */
    fun complete(teamId: Int, taskId: Int, at: Long): Boolean {
        val entry = entries.getOrPut(teamId, ::Entry)
        return entry.completed.putIfAbsent(taskId, at) == null
    }

    fun counter(teamId: Int, name: String): Int = entries[teamId]?.counters?.get(name) ?: 0

    /** Adds one to the counter, never going above [cap]. Returns the new value. */
    fun increment(teamId: Int, name: String, cap: Int): Int {
        val entry = entries.getOrPut(teamId, ::Entry)
        val next = minOf((entry.counters[name] ?: 0) + 1, cap)
        entry.counters[name] = next
        return next
    }

    /** Sets the counter back to zero. */
    fun reset(teamId: Int, name: String) {
        entries[teamId]?.counters?.remove(name)
    }

    /** Forgets everything about [teamId]. Returns how many tasks that cleared. */
    fun clear(teamId: Int): Int = entries.remove(teamId)?.completed?.size ?: 0

    fun toJson(): String {
        val root = teamJson.createObjectNode()
        val teams = root.putObject("teams")
        for ((teamId, entry) in entries) {
            val node = teams.putObject(teamId.toString())
            val completed = node.putObject("completed")
            for ((taskId, at) in entry.completed) {
                completed.put(taskId.toString(), at)
            }
            val counters = node.putObject("counters")
            for ((name, value) in entry.counters) {
                counters.put(name, value)
            }
        }
        return teamJson.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    companion object {
        /** Parses `team-progress.json`, throwing [IllegalArgumentException] with a reason. */
        fun parse(json: String): TeamProgressStore {
            val root = teamJson.readTree(json)
            val teams = root?.get("teams")
            require(teams != null && teams.isObject) {
                "The file needs a top-level \"teams\" object."
            }

            val store = TeamProgressStore()
            for ((key, node) in teams.properties()) {
                val teamId = requireNotNull(key.toIntOrNull()) { "\"$key\" is not a team id." }
                val entry = store.entries.getOrPut(teamId, ::Entry)
                for ((taskKey, at) in node.get("completed")?.properties().orEmpty()) {
                    val taskId =
                        requireNotNull(taskKey.toIntOrNull()) {
                            "\"$taskKey\" is not a task id (team $teamId)."
                        }
                    require(at.isIntegralNumber) { "Task $taskId (team $teamId) has no time." }
                    entry.completed[taskId] = at.longValue()
                }
                for ((name, value) in node.get("counters")?.properties().orEmpty()) {
                    require(value.isInt) { "Counter $name (team $teamId) is not a number." }
                    entry.counters[name] = value.intValue()
                }
            }
            return store
        }
    }
}
