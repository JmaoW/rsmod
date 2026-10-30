package org.rsmod.content.areas.misc.isleofsouls.wilderness

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.objs
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.drops.DropResult
import org.rsmod.api.drops.DropRoller
import org.rsmod.api.drops.DropTables
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid

/**
 * Hands out a Wilderness boss's loot by damage (OSRS Wiki, "Callisto", Drops, as tsps builds it):
 * up to ten players get a share of the drops, each of the top ten at least a quarter of what a
 * player alone would get, and more if they did more of the damage. The loot a share is made of is
 * the boss's ordinary drop table, rolled for each player with their own drop modifiers.
 * - The quantity of an ordinary drop is the table's quantity times the player's share (at least a
 *   quarter), never less than one.
 * - A unique (the rings, the pieces of the Voidwaker, the weapons, the rare bones) is decided once
 *   per kill: one roll of the table, for the player who did the most damage, and the item goes to
 *   one of the ten, chosen by their share of the damage.
 * - A pet is rolled for each of the ten and kept with a chance equal to their share.
 * - A clue scroll is rolled for each of the ten and handed over whole.
 * - Every team that has a player in the ten gets the kill once (their tasks, Slayer, masteries) as
 *   with any other boss: the league's kill counters are shared by a team, so the player of the team
 *   who did the most damage is the one credited.
 */
class WildRewards
@Inject
constructor(
    private val players: PlayerList,
    private val dropTables: DropTables,
    private val dropRoller: DropRoller,
    private val objRepo: ObjRepository,
    private val objTypes: ObjTypeList,
    private val npcDeath: NpcDeath,
    private val eventBus: EventBus,
    private val random: GameRandom,
    private val teams: LeagueTeams,
) {
    /** Who gets a share of [npc]'s loot, with the share (0 to 1) of the damage they did. */
    fun recipients(npc: Npc): List<Pair<Player, Double>> {
        val top =
            npc.heroPoints
                .toMutableList()
                .filter { it.points > 0 }
                .sortedByDescending { it.points }
                .take(MAX_RECIPIENTS)
        // The shares are of the damage of those who get a share (tsps).
        val total = top.sumOf { it.points.toLong() }
        if (total <= 0) {
            return emptyList()
        }
        return top.mapNotNull { hero ->
            val player = players.firstOrNull { it.uuid == hero.uuid } ?: return@mapNotNull null
            player to hero.points.toDouble() / total
        }
    }

    /** The boss died at [at]: give everyone who earned it their kill and their loot. */
    fun award(npc: Npc, at: CoordGrid) {
        val winners = recipients(npc)
        if (winners.isEmpty()) {
            return
        }
        val table = dropTables[npc.type]
        val credited = HashSet<Any>()
        for ((player, _) in winners) {
            if (credited.add(teams.teamOf(player)?.id ?: player.username)) {
                eventBus.publish(NpcDeathEvents.Killed(npc, player))
            }
        }
        if (table == null) {
            for ((player, _) in winners) {
                objRepo.add(
                    objs.bones,
                    at,
                    player.lootDropDuration ?: constants.lootdrop_duration,
                    player,
                )
            }
            return
        }
        // Each of the ten rolls the table with their own modifiers.
        val rolls =
            winners.map { (player, _) ->
                val modify = NpcDropEvents.Modify(npc, player)
                eventBus.publish(modify)
                dropRoller.roll(table, modify.multiplier)
            }
        // One unique roll per kill: the top damager's, given to one of the ten by share.
        val unique = rolls.first().firstOrNull { kind(it) == Kind.Unique }
        var pick = random.of(PERCENT * PERCENT) / (PERCENT * PERCENT).toDouble()
        var uniqueOwner = winners.first().first
        for ((player, damageShare) in winners) {
            pick -= damageShare
            if (pick < 0) {
                uniqueOwner = player
                break
            }
        }
        for ((index, entry) in winners.withIndex()) {
            val (player, damageShare) = entry
            val scale = minOf(1.0, maxOf(MIN_SHARE, damageShare))
            val duration = player.lootDropDuration ?: constants.lootdrop_duration
            val kept = ArrayList<DropResult>()
            for (drop in rolls[index]) {
                when (kind(drop)) {
                    Kind.Unique -> Unit
                    Kind.Pet ->
                        if (
                            random.of(PERCENT * PERCENT) < (damageShare * PERCENT * PERCENT).toInt()
                        ) {
                            kept += drop
                        }
                    Kind.Clue -> kept += drop
                    Kind.Common -> {
                        val count = maxOf(1, Math.round(drop.count * scale).toInt())
                        kept += DropResult(drop.obj, count)
                    }
                }
            }
            if (unique != null && player === uniqueOwner) {
                kept += unique
            }
            for (drop in kept) {
                objRepo.add(drop.obj, at, duration, player, count = drop.count)
            }
            // These drops skip `NpcDeath`, so the loot tracker must be told about them here.
            npcDeath.sendLootTracker(player, npc, kept)
            eventBus.publish(NpcDropEvents.Rolled(npc, player, kept))
        }
    }

    private enum class Kind {
        Unique,
        Pet,
        Clue,
        Common,
    }

    private fun kind(drop: DropResult): Kind {
        val name = objTypes[drop.obj].name.lowercase()
        return when {
            PET_WORDS.any { it in name } -> Kind.Pet
            CLUE_WORDS.any { it in name } -> Kind.Clue
            UNIQUE_WORDS.any { it in name } -> Kind.Unique
            else -> Kind.Common
        }
    }

    companion object {
        /** The wiki: the top ten players receive drops. */
        const val MAX_RECIPIENTS = 10

        /** The wiki: at least a quarter of the solo loot. */
        const val MIN_SHARE = 0.25

        private const val PERCENT = 100

        private val UNIQUE_WORDS =
            listOf(
                "claws of callisto",
                "fangs of venenatis",
                "skull of vet'ion",
                "tyrannical ring",
                "treasonous ring",
                "ring of the gods",
                "voidwaker",
                "dragon 2h sword",
                "dragon pickaxe",
                "long bone",
                "curved bone",
                "wilderness weapon",
            )

        private val PET_WORDS = listOf("callisto cub", "venenatis spiderling", "vet'ion jr")

        private val CLUE_WORDS = listOf("clue scroll")
    }
}
