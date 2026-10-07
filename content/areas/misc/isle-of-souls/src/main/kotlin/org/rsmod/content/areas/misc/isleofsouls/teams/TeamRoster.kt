package org.rsmod.content.areas.misc.isleofsouls.teams

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

internal val teamJson = ObjectMapper()

/** A league team. [members] are login names, always trimmed and lowercase. */
class Team internal constructor(val id: Int, val name: String) {
    private val memberSet = LinkedHashSet<String>()

    val members: Set<String>
        get() = memberSet

    internal fun add(username: String) {
        memberSet += username
    }

    internal fun remove(username: String) {
        memberSet -= username
    }

    override fun toString(): String = "Team(id=$id, name=$name, members=$memberSet)"
}

/**
 * Which players belong to which team. A player is on at most one team, and is matched by login
 * name. A player can also have a display name written in the roster, for names that capitalise
 * unusually. This is plain data with no game dependencies; [LeagueTeams] adds the files and the
 * online-player lookups.
 */
class TeamRoster {
    private val byId = linkedMapOf<Int, Team>()
    private val teamByMember = hashMapOf<String, Int>()
    private val displayNames = hashMapOf<String, String>()

    val teams: Collection<Team>
        get() = byId.values

    operator fun get(id: Int): Team? = byId[id]

    fun teamOf(username: String): Team? = teamByMember[normalize(username)]?.let(byId::get)

    /** The display name written in the roster for [username], if there is one. */
    fun displayName(username: String): String? = displayNames[normalize(username)]

    /** Adds an empty team. A blank [name] falls back to `Team <id>`. */
    fun create(id: Int, name: String): Team {
        require(id !in byId) { "Team $id already exists." }
        val team = Team(id, name.trim().ifEmpty { "Team $id" })
        byId[id] = team
        return team
    }

    /**
     * Puts [username] on team [teamId], taking them off any other team first. Returns the team they
     * were on before, which is the same team if they were already on it. A non-blank [displayName]
     * replaces the one written in the roster; otherwise an existing one is kept.
     */
    fun assign(username: String, teamId: Int, displayName: String? = null): Team? {
        val team = requireNotNull(byId[teamId]) { "There is no team $teamId." }
        val key = normalize(username)
        require(key.isNotEmpty()) { "A player name must not be blank." }
        val previous = detach(key)
        team.add(key)
        teamByMember[key] = teamId
        displayName?.trim()?.takeIf(String::isNotEmpty)?.let { displayNames[key] = it }
        return previous
    }

    /**
     * Takes [username] off their team and forgets their display name. Returns the team they were
     * on, or `null` if none.
     */
    fun remove(username: String): Team? {
        val key = normalize(username)
        displayNames.remove(key)
        return detach(key)
    }

    private fun detach(key: String): Team? {
        val previousId = teamByMember.remove(key) ?: return null
        val team = byId[previousId]
        team?.remove(key)
        return team
    }

    fun toJson(): String {
        val root = teamJson.createObjectNode()
        val array = root.putArray("teams")
        for (team in teams) {
            val node = array.addObject()
            node.put("id", team.id)
            node.put("name", team.name)
            val players = node.putArray("players")
            for (member in team.members) {
                val shown = displayNames[member]
                if (shown == null) {
                    players.add(member)
                } else {
                    players.addObject().put("login", member).put("name", shown)
                }
            }
        }
        return teamJson.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    companion object {
        fun normalize(name: String): String = name.trim().lowercase()

        /** Parses `teams.json`, throwing [IllegalArgumentException] with a readable reason. */
        fun parse(json: String): TeamRoster {
            val root = teamJson.readTree(json)
            val teamsNode = root?.get("teams")
            require(teamsNode != null && teamsNode.isArray) {
                "The file needs a top-level \"teams\" list."
            }

            val roster = TeamRoster()
            for ((index, node) in teamsNode.withIndex()) {
                val idNode = node.get("id")
                require(idNode != null && idNode.isInt) {
                    "Team number ${index + 1} needs a whole-number \"id\"."
                }
                val id = idNode.intValue()
                require(roster[id] == null) { "Team id $id is used more than once." }

                val team = roster.create(id, node.get("name")?.asText().orEmpty())
                val players = node.get("players") ?: continue
                require(players.isArray) { "Team $id: \"players\" must be a list of names." }
                for (player in players) {
                    val (login, shown) = parsePlayer(id, player)
                    val key = normalize(login)
                    val other = roster.teamOf(key)
                    require(other == null) {
                        "\"$key\" is listed on team ${other?.id} and on team $id."
                    }
                    roster.assign(key, team.id, shown)
                }
            }
            return roster
        }

        /** A player is either a login name, or `{ "login": ..., "name": ... }`. */
        private fun parsePlayer(teamId: Int, player: JsonNode): Pair<String, String?> {
            val login: String
            var shown: String? = null
            if (player.isTextual) {
                login = player.asText()
            } else if (player.isObject && player.get("login")?.isTextual == true) {
                login = player.get("login").asText()
                val name = player.get("name")
                require(name == null || name.isTextual) {
                    "Team $teamId: the \"name\" of \"$login\" must be text."
                }
                shown = name?.asText()
            } else {
                throw IllegalArgumentException(
                    "Team $teamId has a player entry that is not a name."
                )
            }
            require(login.isNotBlank()) { "Team $teamId has a player entry that is not a name." }
            return login to shown
        }
    }
}
