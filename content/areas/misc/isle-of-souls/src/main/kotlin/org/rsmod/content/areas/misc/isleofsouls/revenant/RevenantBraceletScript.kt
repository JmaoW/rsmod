package org.rsmod.content.areas.misc.isleofsouls.revenant

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.drops.DropResult
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.game.process.npc.hunt.HuntTolerance
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.worn.RevenantEther
import org.rsmod.api.script.onEvent
import org.rsmod.content.areas.misc.isleofsouls.dungeons.DungeonRevenantScript
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueProgressVarps
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Whether [npc] is one of the Revenant Caves' revenants. */
internal fun isRevenant(npc: Npc): Boolean =
    DungeonRevenantScript.KINDS.any { npc.type.isType(it.type) }

/**
 * Revenants do not start a fight with a player who wears a charged bracelet of ethereum (Wiki:
 * "Revenants also become tolerant while you wear it charged"). One a player attacks still fights
 * back.
 */
class RevenantTolerance : HuntTolerance {
    override fun tolerates(npc: Npc, player: Player): Boolean =
        isRevenant(npc) && EtherCharges.braceletEther(player) > 0
}

/**
 * What the rest of the bracelet of ethereum does (the hit it takes off a revenant's attack is in
 * [DungeonRevenantScript]):
 * - With "Toggle-absorption" on, the ether a revenant would drop for the wearer goes into the
 *   bracelet instead, as far as it has room (16,000), and the rest stays on the ground.
 * - Revenant weapons dropped by a revenant are counted for "Obtain Every Revenant Weapon".
 */
class RevenantBraceletScript @Inject constructor(private val tasks: LeagueTaskTracker) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcDropEvents.Claim> { absorb(hero, npc, drops) }
        onEvent<NpcDropEvents.Rolled> { countWeapons(hero, npc, drops) }
    }

    /** Takes the ether in [drops] into the bracelet [hero] wears. Returns how much it took. */
    fun absorb(hero: Player, npc: Npc, drops: MutableList<DropResult>): Int {
        if (!isRevenant(npc)) {
            return 0
        }
        val bracelet = EtherCharges.wornBracelet(hero) ?: return 0
        if (!EtherCharges.absorbing(bracelet)) {
            return 0
        }
        val stored = RevenantEther.stored(bracelet)
        var room = RevenantEther.BRACELET_CAPACITY - stored
        var absorbed = 0
        val iterator = drops.listIterator()
        while (iterator.hasNext() && room > 0) {
            val drop = iterator.next()
            // Drops carry unpacked obj types, which are never equal to the hashed references.
            if (drop.obj.id != ether_objs.ether.id) {
                continue
            }
            val taken = min(drop.count, room)
            room -= taken
            absorbed += taken
            if (taken >= drop.count) {
                iterator.remove()
            } else {
                iterator.set(drop.copy(count = drop.count - taken))
            }
        }
        if (absorbed > 0) {
            hero.worn[Wearpos.Hands.slot] =
                EtherCharges.withStored(bracelet, ether_objs.bracelet, stored + absorbed)
            hero.mes("Your bracelet of ethereum absorbs $absorbed revenant ether.")
        }
        return absorbed
    }

    /** Marks the revenant weapons in [drops] for [hero]; all three complete the task. */
    fun countWeapons(hero: Player, npc: Npc, drops: List<DropResult>) {
        if (!isRevenant(npc)) {
            return
        }
        for ((weapon, counter) in obtained) {
            if (drops.any { it.obj.id == weapon.id }) {
                tasks.mark(hero, counter)
            }
        }
        if (obtained.all { tasks.isMarked(hero, it.second) }) {
            tasks.complete(hero, TASK_OBTAIN_EVERY_WEAPON)
        }
    }

    companion object {
        const val TASK_OBTAIN_EVERY_WEAPON = 1019

        /** Craw's bow, Viggora's chainmace and Thammaron's sceptre, and what counts each one. */
        private val obtained
            get() =
                listOf(
                    ether_objs.craw_uncharged to LeagueProgressVarps.revenant_craw,
                    ether_objs.viggora_uncharged to LeagueProgressVarps.revenant_viggora,
                    ether_objs.thammaron_uncharged to LeagueProgressVarps.revenant_thammaron,
                )
    }
}
