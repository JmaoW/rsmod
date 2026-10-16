package org.rsmod.content.areas.misc.isleofsouls.event

import com.fasterxml.jackson.databind.JsonNode
import org.rsmod.content.areas.misc.isleofsouls.teams.teamJson

/** How a finished task should be announced. */
enum class TaskAnnouncement {
    /** Every completion is announced. */
    Completed,

    /** Only the first team to complete it is announced. */
    FirstTeam,
}

/**
 * What the league announces server-wide, read from `announcements.json` so it can be changed
 * without touching code. Tasks can be picked by tier or by id, and either announced every time or
 * only for the first team to complete them.
 */
data class AnnouncementConfig(
    val leadChanges: Boolean = true,
    val announceTiers: Set<String> = setOf("elite", "master"),
    val firstCompletionTiers: Set<String> = setOf("hard"),
    val announceTaskIds: Set<Int> = emptySet(),
    val firstCompletionTaskIds: Set<Int> = emptySet(),
    val levels: Set<Int> = setOf(99),
    val deaths: Boolean = true,
    val dropObjs: Set<String> = emptySet(),
    val dropBoosted: Boolean = true,
    val dropPets: Boolean = true,
) {
    /**
     * How to announce a completed task, or `null` for no announcement. A first completion wins over
     * a plain one, so the first team to finish an announced task is not announced twice.
     */
    fun forTask(taskId: Int, tier: String, firstTeam: Boolean): TaskAnnouncement? =
        when {
            firstTeam && (taskId in firstCompletionTaskIds || tier in firstCompletionTiers) ->
                TaskAnnouncement.FirstTeam
            taskId in announceTaskIds || tier in announceTiers -> TaskAnnouncement.Completed
            else -> null
        }

    fun announcesLevel(level: Int): Boolean = level in levels

    fun announcesDrop(objInternalName: String?): Boolean =
        objInternalName != null && objInternalName.lowercase() in dropObjs

    /** Whether a drop is announced for being [boosted] by the relics' drop rate, or a pet. */
    fun announcesDrop(boosted: Boolean, pet: Boolean): Boolean =
        (dropBoosted && boosted) || (dropPets && pet)

    fun toJson(): String {
        val root = teamJson.createObjectNode()
        root.put("leadChanges", leadChanges)
        val tasks = root.putObject("tasks")
        tasks.putArray("announceTiers").also { array -> announceTiers.sorted().forEach(array::add) }
        tasks.putArray("firstCompletionTiers").also { array ->
            firstCompletionTiers.sorted().forEach(array::add)
        }
        tasks.putArray("announceIds").also { array -> announceTaskIds.sorted().forEach(array::add) }
        tasks.putArray("firstCompletionIds").also { array ->
            firstCompletionTaskIds.sorted().forEach(array::add)
        }
        root.putObject("levels").putArray("announce").also { array ->
            levels.sorted().forEach(array::add)
        }
        root.put("deaths", deaths)
        val drops = root.putObject("drops")
        drops.put("boosted", dropBoosted)
        drops.put("pets", dropPets)
        drops.putArray("objs").also { array -> dropObjs.sorted().forEach(array::add) }
        return teamJson.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    companion object {
        /** The tier names a task can have, as used in the file. */
        val TIERS: Set<String> = setOf("easy", "medium", "hard", "elite", "master")

        private val topLevelKeys = setOf("leadChanges", "tasks", "levels", "deaths", "drops")
        private val taskKeys =
            setOf("announceTiers", "firstCompletionTiers", "announceIds", "firstCompletionIds")

        /**
         * Parses `announcements.json`. Anything left out keeps its default. Throws
         * [IllegalArgumentException] with a readable reason, including for a misspelt setting, so a
         * typo cannot silently switch an announcement off.
         */
        fun parse(json: String): AnnouncementConfig {
            val root = teamJson.readTree(json)
            require(root != null && root.isObject) { "The file needs to be a JSON object." }
            rejectUnknown(root, topLevelKeys, "the file")

            var config = AnnouncementConfig()
            root.get("leadChanges")?.let {
                config = config.copy(leadChanges = boolean(it, "leadChanges"))
            }
            root.get("deaths")?.let { config = config.copy(deaths = boolean(it, "deaths")) }

            root.get("tasks")?.let { tasks ->
                require(tasks.isObject) { "\"tasks\" must be an object." }
                rejectUnknown(tasks, taskKeys, "\"tasks\"")
                tasks.get("announceTiers")?.let {
                    config = config.copy(announceTiers = tiers(it, "tasks.announceTiers"))
                }
                tasks.get("firstCompletionTiers")?.let {
                    config =
                        config.copy(firstCompletionTiers = tiers(it, "tasks.firstCompletionTiers"))
                }
                tasks.get("announceIds")?.let {
                    config = config.copy(announceTaskIds = ints(it, "tasks.announceIds"))
                }
                tasks.get("firstCompletionIds")?.let {
                    config =
                        config.copy(firstCompletionTaskIds = ints(it, "tasks.firstCompletionIds"))
                }
            }
            root.get("levels")?.let { levels ->
                require(levels.isObject) { "\"levels\" must be an object." }
                rejectUnknown(levels, setOf("announce"), "\"levels\"")
                levels.get("announce")?.let {
                    config = config.copy(levels = ints(it, "levels.announce"))
                }
            }
            root.get("drops")?.let { drops ->
                require(drops.isObject) { "\"drops\" must be an object." }
                rejectUnknown(drops, setOf("boosted", "pets", "objs"), "\"drops\"")
                drops.get("boosted")?.let {
                    config = config.copy(dropBoosted = boolean(it, "drops.boosted"))
                }
                drops.get("pets")?.let {
                    config = config.copy(dropPets = boolean(it, "drops.pets"))
                }
                drops.get("objs")?.let {
                    config =
                        config.copy(
                            dropObjs = strings(it, "drops.objs").map(String::lowercase).toSet()
                        )
                }
            }
            return config
        }

        private fun rejectUnknown(node: JsonNode, known: Set<String>, where: String) {
            val unknown = node.fieldNames().asSequence().filter { it !in known }.toList()
            require(unknown.isEmpty()) {
                "Unknown setting ${unknown.joinToString(", ") { "\"$it\"" }} in $where. " +
                    "Known settings: ${known.joinToString(", ")}."
            }
        }

        private fun boolean(node: JsonNode, name: String): Boolean {
            require(node.isBoolean) { "\"$name\" must be true or false." }
            return node.booleanValue()
        }

        private fun strings(node: JsonNode, name: String): List<String> {
            require(node.isArray) { "\"$name\" must be a list." }
            return node.map {
                require(it.isTextual) { "\"$name\" must only contain text." }
                it.asText().trim()
            }
        }

        private fun ints(node: JsonNode, name: String): Set<Int> {
            require(node.isArray) { "\"$name\" must be a list." }
            return node
                .map {
                    require(it.isInt) { "\"$name\" must only contain whole numbers." }
                    it.intValue()
                }
                .toSet()
        }

        private fun tiers(node: JsonNode, name: String): Set<String> {
            val values = strings(node, name).map(String::lowercase)
            val unknown = values.filter { it !in TIERS }
            require(unknown.isEmpty()) {
                "Unknown tier ${unknown.joinToString(", ") { "\"$it\"" }} in \"$name\". " +
                    "Tiers are ${TIERS.joinToString(", ")}."
            }
            return values.toSet()
        }
    }
}
