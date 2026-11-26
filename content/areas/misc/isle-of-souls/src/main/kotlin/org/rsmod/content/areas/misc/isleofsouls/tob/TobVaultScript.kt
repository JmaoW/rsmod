package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Verzik Vitur's vault: when she falls the loot is rolled and a monumental chest appears for each
 * player still in the raid (at most five), each player's own showing as theirs (and purple with a
 * unique). Nothing is given or announced until a player opens their chest; whoever leaves first
 * finds their loot in the Rewards Chest by Ver Sinhaza's bank. The client draws five chests, so a
 * party of six to eight shares them: the player in slot six uses the first chest, and so on; each
 * player opening one sees only their own loot.
 *
 * The chests' interface (23) is [TobRewards]'; this script routes its buttons, and the Rewards
 * Chest by Ver Sinhaza's bank, which holds whatever was left behind.
 */
class TobVaultScript
@Inject
constructor(
    private val raids: TobRaids,
    private val rewards: TobRewards,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<TobEvents.RaidWon> {
            rewards.rollRaid(raid)
            val slots = raid.active.map { it.slot % CHESTS.size }.toSet()
            for ((slot, chest) in CHESTS.withIndex()) {
                if (slot !in slots) {
                    continue
                }
                locRepo.add(
                    raid.regionOf(chest.first),
                    locTypes[tob_locs.vault_chests[slot]],
                    Int.MAX_VALUE,
                    chest.second,
                    LocShape.CentrepieceStraight,
                )
            }
            for (member in raid.active) {
                sync(raid, member.player)
            }
        }
        onEvent<TobEvents.RoomEntered> {
            if (room == TobRoom.Vault) {
                sync(raid, player)
            }
        }
        // Loot nobody opened their chest for goes to the Rewards Chest in Ver Sinhaza.
        onEvent<TobEvents.PlayerLeft> { rewards.claim(raid, player, announce = false) }
        onEvent<TobEvents.RaidEnded> {
            for (member in raid.members) {
                rewards.claim(raid, member.player, announce = false)
            }
        }
        for ((slot, chest) in tob_locs.vault_chests.withIndex()) {
            onOpLoc1(chest) { openChest(slot) }
        }
        onOpLoc1(tob_locs.rewards_chest) { rewards.open(player) }
        onPlayerLogin { rewards.sync(player) }

        onIfModalButton(tob_components.chests_bank) { rewards.takeAll(player, toBank = true) }
        onIfModalButton(tob_components.chests_inventory) { rewards.takeAll(player, toBank = false) }
        onIfModalButton(tob_components.chests_discard) { discard() }
        onIfModalButton(tob_components.chests_items) {
            if (it.op == IfButtonOp.Op1) {
                rewards.take(player, it.comsub)
            }
        }
    }

    private fun ProtectedAccess.openChest(slot: Int) {
        val raid = raids.raidOf(player) ?: return
        val member = raid.member(player) ?: return
        if (member.slot % CHESTS.size != slot) {
            mes("That chest belongs to someone else.")
            return
        }
        anim(tob_seqs.open_chest)
        rewards.claim(raid, player)
        rewards.open(player)
        sync(raid, player)
    }

    private suspend fun ProtectedAccess.discard() {
        if (!rewards.hasLoot(player)) {
            mes("There is nothing to discard.")
            return
        }
        var sure = false
        startDialogue { sure = choice2("No.", false, "Yes, discard everything.", true) }
        if (sure) {
            rewards.discardAll(player)
        }
    }

    /** Each player's own chest (by slot) shows as theirs; a unique makes the chests glow. */
    private fun sync(raid: TobRaid, player: Player) {
        val own = raid.member(player)?.slot?.rem(CHESTS.size)
        for (slot in CHESTS.indices) {
            val mine = slot == own
            val state =
                when {
                    mine && raid.uniqueFound -> MINE_UNIQUE
                    mine -> MINE
                    raid.uniqueFound -> OTHERS_UNIQUE
                    else -> OTHERS
                }
            VarPlayerIntMapSetter.set(player, tob_varbits.vault_chests[slot], state)
        }
        rewards.sync(player)
    }

    private companion object {
        const val OTHERS = 0
        const val OTHERS_UNIQUE = 1
        const val MINE = 2
        const val MINE_UNIQUE = 3

        /** The chests' south-west tiles and facing (TSPS's reading of the wiki's map). */
        val CHESTS =
            listOf(
                CoordGrid(3233, 4330, 0) to LocAngle.West,
                CoordGrid(3240, 4327, 0) to LocAngle.North,
                CoordGrid(3226, 4327, 0) to LocAngle.South,
                CoordGrid(3226, 4323, 0) to LocAngle.South,
                CoordGrid(3240, 4323, 0) to LocAngle.North,
            )
    }
}
