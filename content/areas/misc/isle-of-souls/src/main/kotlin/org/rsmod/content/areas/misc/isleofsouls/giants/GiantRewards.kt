package org.rsmod.content.areas.misc.isleofsouls.giants

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.drops.DropResult
import org.rsmod.api.drops.DropRoller
import org.rsmod.api.drops.DropTables
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList

/**
 * What opening a lair's chest gives (the chest tables are in `isle.toml`, on the chests' own npc
 * types, and are rolled like a monster's drops so that the relics' drop boosts apply to the
 * uniques). The loot lands on the floor beside the chest for the player who opened it.
 *
 * Uncut rubies and uncut diamonds always drop together (wiki): the table has the rubies, and the
 * diamonds are added here.
 */
class GiantRewards
@Inject
constructor(
    private val dropTables: DropTables,
    private val dropRoller: DropRoller,
    private val objRepo: ObjRepository,
    private val objTypes: ObjTypeList,
    private val npcDeath: NpcDeath,
    private val eventBus: EventBus,
) {
    /** Rolls [run]'s chest for [player] and drops what it gives at the lair's loot tile. */
    fun openChest(run: GiantRun, player: Player) {
        val lair = run.lair
        val table = dropTables[lair.chestNpc] ?: return
        // The boss that was just killed stands for the source of the drops (boosts, tracker).
        val source = run.killed ?: return
        val modify = NpcDropEvents.Modify(source, player)
        eventBus.publish(modify)
        val rolled = dropRoller.roll(table, modify.multiplier, modify.chanceMultipliers)
        val drops = rolled.map { DropResult(it.obj, modify.countOf(it), it.boosted) }.withDiamonds()
        val at = run.real(lair.loot)
        val duration = player.lootDropDuration ?: constants.lootdrop_duration
        // Claimed drops (a pet) are handed over by their claimer instead of the floor.
        val claim = NpcDropEvents.Claim(source, player, drops.toMutableList())
        eventBus.publish(claim)
        for (drop in claim.drops) {
            objRepo.add(drop.obj, at, duration, player, count = drop.count)
        }
        // These drops skip `NpcDeath`, so the loot tracker must be told about them here.
        npcDeath.sendLootTracker(player, source, drops)
        eventBus.publish(NpcDropEvents.Rolled(source, player, drops))
    }

    /** Adds the noted uncut diamonds that come with a stack of noted uncut rubies. */
    private fun List<DropResult>.withDiamonds(): List<DropResult> {
        val ruby = noted(giant_objs.uncut_ruby) ?: return this
        val diamond = noted(giant_objs.uncut_diamond) ?: return this
        val out = ArrayList(this)
        for (drop in this) {
            if (drop.obj.id == ruby.id) {
                out += DropResult(diamond, GEM_PAIR_COUNT, drop.boosted)
            }
        }
        return out
    }

    /** The noted version of [type], if it has one. */
    private fun noted(type: ObjType): ObjType? =
        objTypes.values.firstOrNull { it.isCert && it.certlink == type.id }

    private companion object {
        /** Five uncut rubies come with five uncut diamonds. */
        const val GEM_PAIR_COUNT = 5
    }
}
