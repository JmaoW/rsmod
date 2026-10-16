package org.rsmod.content.areas.misc.isleofsouls.magic

import jakarta.inject.Inject
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.magic.utility.scripts.SpellTeams
import org.rsmod.game.entity.Player

/**
 * Group spells and spells cast on other players only reach the caster's own league team. A player
 * who is on no team has nobody to reach.
 */
class LeagueSpellTeams @Inject constructor(private val teams: LeagueTeams) : SpellTeams {
    override fun areTeammates(caster: Player, other: Player): Boolean {
        if (caster === other) {
            return false
        }
        val team = teams.teamOf(caster) ?: return false
        return teams.teamOf(other)?.id == team.id
    }

    override fun nearby(caster: Player, distance: Int): List<Player> {
        val team = teams.teamOf(caster) ?: return emptyList()
        return teams.onlineMembers(team).filter {
            it !== caster && caster.isWithinDistance(it, distance)
        }
    }
}
