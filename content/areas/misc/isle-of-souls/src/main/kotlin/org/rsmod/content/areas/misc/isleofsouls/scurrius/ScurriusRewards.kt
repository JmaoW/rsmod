package org.rsmod.content.areas.misc.isleofsouls.scurrius

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.drops.DropRoller
import org.rsmod.api.drops.DropTables
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.interfaces.collectionlog.CollectionKillCounts
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/**
 * What a kill of Scurrius gives everyone in the lair besides the tasks (the kill-count tasks and
 * the combat mastery task are completed by the kill itself, see `LeagueKillTasks` and
 * `LeagueMasteryKills`). The player who did the most damage gets the ordinary drop table, rolled
 * where he dies. Everyone else in the lair gets the wiki's non-MVP table (`isle.toml`): big bones,
 * a share of the supplies, and a chance of the spine.
 */
class ScurriusRewards
@Inject
constructor(
    private val dropTables: DropTables,
    private val dropRoller: DropRoller,
    private val objRepo: ObjRepository,
    private val npcDeath: NpcDeath,
    private val eventBus: EventBus,
    private val killCounts: CollectionKillCounts,
) {
    fun win(players: List<Player>, run: ScurriusRun) {
        val hero = run.hero ?: return
        val boss = run.killed ?: return
        val at = run.dropAt ?: return
        val table = dropTables[scurrius_npcs.non_mvp_drops] ?: return
        for (player in players) {
            if (player === hero) {
                continue
            }
            // The hero's kill is counted by the death event; the rest of the team earned theirs.
            killCounts.addKill(player, boss.visType.name)
            val modify = NpcDropEvents.Modify(boss, player)
            eventBus.publish(modify)
            val drops = dropRoller.roll(table, modify.multiplier)
            val duration = player.lootDropDuration ?: constants.lootdrop_duration
            for (drop in drops) {
                objRepo.add(drop.obj, at, duration, player, count = drop.count)
            }
            // These drops skip `NpcDeath`, so the loot tracker must be told about them here.
            npcDeath.sendLootTracker(player, boss, drops)
            eventBus.publish(NpcDropEvents.Rolled(boss, player, drops))
        }
    }
}
