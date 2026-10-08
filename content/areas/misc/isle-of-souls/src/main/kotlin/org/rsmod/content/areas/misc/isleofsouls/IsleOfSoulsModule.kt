package org.rsmod.content.areas.misc.isleofsouls

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.combat.mastery.CombatMasteryProvider
import org.rsmod.api.stats.xpmod.XpMod
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueAnnouncer
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueEvent
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueScoreboard
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueStandings
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.FightCaves
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceLoginPipeline
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.masteries.LeagueMasteryTracker
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueXpMod
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.plugin.module.PluginModule

class IsleOfSoulsModule : PluginModule() {
    override fun bind() {
        addSetBinding<XpMod>(LeagueXpMod::class.java)
        addSetBinding<CombatMasteryProvider>(LeagueMasteryTracker::class.java)

        // One shared roster, progress store and set of live instances: every script must see the
        // same ones.
        bindInstance<LeagueTeams>()
        bindInstance<TeamInstances>()
        bindInstance<LeagueEvent>()
        bindInstance<LeagueStandings>()
        bindInstance<LeagueAnnouncer>()
        bindInstance<LeagueScoreboard>()
        bindInstance<FightCaves>()

        // Moves a player saved inside an instance back out before their login map is built.
        addSetBinding<CharacterDataStage.Pipeline>(TeamInstanceLoginPipeline::class.java)
    }
}
