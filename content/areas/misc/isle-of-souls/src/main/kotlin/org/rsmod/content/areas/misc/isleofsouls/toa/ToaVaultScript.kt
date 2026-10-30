package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.map.Direction
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Osmumten's burial chamber: when the Wardens fall the loot is rolled, each player's own chest
 * shows as theirs (and, once emptied, as empty), the flames turn purple if a unique dropped, and
 * Osmumten sees the party out.
 *
 * The chest interface (771) is [ToaRewards]'; this script routes its buttons.
 */
class ToaVaultScript
@Inject
constructor(
    private val raids: ToaRaids,
    private val rewards: ToaRewards,
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<ToaEvents.RaidWon> {
            rewards.rollRaid(raid)
            val type = npcTypes[toa_npcs.osmumten_vault]
            val osmumten = Npc(type, raid.regionOf(OSMUMTEN))
            // Set before the add, when the client is told (TSPS has him facing north).
            osmumten.respawnDir = Direction.North
            // He waits by the chests for the party: no wandering about the vault.
            osmumten.mode = NpcMode.None
            npcRepo.add(osmumten, Int.MAX_VALUE)
        }
        onEvent<ToaEvents.RoomEntered> {
            if (room == ToaRoom.Vault) {
                syncVault(raid, player)
            }
        }

        onOpLoc1(toa_locs.vault_chest_mine) { openChest() }
        onOpLoc1(toa_locs.vault_chest_other) { mes("That chest belongs to someone else.") }
        for (chest in toa_locs.vault_chest_open + toa_locs.vault_chest_empty) {
            onOpLoc1(chest) { openChest() }
        }
        onOpLoc1(toa_locs.sarcophagus_rare) { openChest() }
        onOpLoc1(toa_locs.sarcophagus_open) { openChest() }
        onOpLoc1(toa_locs.rewards_niche) { openChest() }

        onOpNpc1(toa_npcs.osmumten_vault) { leave() }

        onIfModalButton(toa_components.chests_bank) { rewards.takeAll(player, toBank = true) }
        onIfModalButton(toa_components.chests_inventory) { rewards.takeAll(player, toBank = false) }
        onIfModalButton(toa_components.chests_discard) { discard() }
        onIfModalButton(toa_components.chests_items) {
            rewards.take(player, it.comsub, amountOf(it.op))
            syncVault(player)
        }
    }

    private fun amountOf(op: IfButtonOp): Int =
        when (op) {
            IfButtonOp.Op2 -> 5
            IfButtonOp.Op3 -> 10
            IfButtonOp.Op4 -> Int.MAX_VALUE
            else -> 1
        }

    private fun ProtectedAccess.openChest() {
        rewards.open(player)
        syncVault(player)
    }

    private suspend fun ProtectedAccess.discard() {
        if (!rewards.hasLoot(player)) {
            mes("There is nothing to discard.")
            return
        }
        startDialogue {
            val sure = choice2("No.", 1, "Yes, discard everything.", 2)
            if (sure == 2) {
                rewards.discardAll(player)
            }
        }
        syncVault(player)
    }

    private suspend fun ProtectedAccess.leave() {
        startDialogue {
            val answer = choice2("Yes.", 1, "No.", 2)
            if (answer == 1) {
                if (rewards.hasLoot(player)) {
                    mes("Your unclaimed rewards can be collected from the chest in the lobby.")
                }
                raids.leave(player, ToaExit.Completed)
            }
        }
    }

    private fun syncVault(player: Player) {
        val raid = raids.raidOf(player) ?: return
        syncVault(raid, player)
    }

    /**
     * Each player sees their own chest as theirs while it holds loot and as emptied after, everyone
     * else's as not theirs; the flames are purple when a unique dropped.
     */
    private fun syncVault(raid: ToaRaid, player: Player) {
        val own = raid.member(player)?.slot
        for (slot in 0 until ToaParties.MAX_SIZE) {
            val state =
                when {
                    slot != own -> CHEST_OTHERS
                    rewards.hasLoot(player) -> CHEST_MINE
                    else -> CHEST_EMPTY
                }
            VarPlayerIntMapSetter.set(player, toa_varbits.vault_chests[slot], state)
        }
        VarPlayerIntMapSetter.set(
            player,
            toa_varbits.vault_sarcophagus,
            if (raid.uniqueFound) 1 else 0,
        )
        rewards.sync(player)
    }

    private companion object {
        const val CHEST_OTHERS = 0
        const val CHEST_MINE = 2
        const val CHEST_EMPTY = 4

        /** Where Osmumten stands in the vault, in real coordinates. */
        val OSMUMTEN = CoordGrid(3680, 5143, 0)
    }
}
