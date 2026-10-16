package org.rsmod.content.areas.misc.isleofsouls

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.combat.mastery.CombatMasteryProvider
import org.rsmod.api.game.process.player.RunDrainMod
import org.rsmod.api.shops.ShopNoting
import org.rsmod.api.stats.xpmod.XpMod
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueAnnouncer
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueEvent
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueScoreboard
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueStandings
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.FightCaves
import org.rsmod.content.areas.misc.isleofsouls.inferno.Inferno
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceLoginPipeline
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.magic.IsleTeleportProvider
import org.rsmod.content.areas.misc.isleofsouls.magic.LeagueSpellTeams
import org.rsmod.content.areas.misc.isleofsouls.masteries.LeagueMasteryTracker
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueGoldenGod
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueRunDrainMod
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueXpMod
import org.rsmod.content.areas.misc.isleofsouls.scurrius.Scurrius
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.zulrah.Zulrah
import org.rsmod.content.skills.magic.utility.scripts.AlchemyModifiers
import org.rsmod.content.skills.magic.utility.scripts.SpellTeams
import org.rsmod.content.skills.magic.utility.scripts.TeleportDestinations
import org.rsmod.plugin.module.PluginModule

class IsleOfSoulsModule : PluginModule() {
    override fun bind() {
        addSetBinding<XpMod>(LeagueXpMod::class.java)
        addSetBinding<RunDrainMod>(LeagueRunDrainMod::class.java)
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
        bindInstance<Inferno>()
        bindInstance<Zulrah>()
        bindInstance<Scurrius>()

        // The teleport spells go to places on the isle, not to the cache's real-world towns.
        bindProvider<TeleportDestinations>(IsleTeleportProvider::class.java)

        // The Golden God relic changes alchemy and how shops hand over what is bought.
        bindInstance<LeagueGoldenGod>()
        bindBaseInstance<AlchemyModifiers>(LeagueGoldenGod::class.java)
        bindBaseInstance<ShopNoting>(LeagueGoldenGod::class.java)

        // Group spells and spells on other players reach the caster's league team only.
        bindBaseInstance<SpellTeams>(LeagueSpellTeams::class.java)

        // Moves a player saved inside an instance back out before their login map is built.
        addSetBinding<CharacterDataStage.Pipeline>(TeamInstanceLoginPipeline::class.java)
    }
}
