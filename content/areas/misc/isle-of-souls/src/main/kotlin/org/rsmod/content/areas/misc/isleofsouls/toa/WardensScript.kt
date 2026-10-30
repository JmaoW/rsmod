package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackDelays
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Amascut's Promise: the two Wardens rooms, Osmumten's "Begin", the teleport crystals, and what in
 * the rooms never dies of damage.
 */
class WardensScript
@Inject
constructor(
    private val raids: ToaRaids,
    private val rooms: ToaRoomRegistry,
    private val npcRepo: NpcRepository,
    private val attackDelays: NpcAttackDelays,
) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(ToaRoom.WardensFirst) { env, raid -> WardensRuntime(env, raid) }
        rooms.register(ToaRoom.WardensFinal) { env, raid -> WardensFinalRuntime(env, raid) }
        // Hitting one of the skulls (siphons) costs no attack delay: the next attack, on another
        // skull or the Warden, can follow at once.
        val siphon = wardens_npcs.siphon.id
        attackDelays.register { _, target, _ -> if (target.type.id == siphon) 0 else null }

        onOpNpc1(wardens_npcs.osmumten) { talkToOsmumten(it.npc) }
        onOpNpc3(wardens_npcs.osmumten) { first()?.setReady(player) }
        onOpLoc1(wardens_locs.crystal) { useCrystal() }
        onOpLoc1(wardens_locs.final_crystal) { useCrystal() }

        toaBosses(
            rooms,
            // Every form one can be hit in (handlers go by the transmogged type): without them the
            // engine's own combat has the obelisk fight back and chase.
            listOf(
                wardens_npcs.obelisk_inactive,
                wardens_npcs.obelisk,
                wardens_npcs.obelisk_p2,
                wardens_npcs.elidinis_charging,
                wardens_npcs.tumeken_charging,
                wardens_npcs.elidinis_exposed,
                wardens_npcs.tumeken_exposed,
                wardens_npcs.elidinis_charging_skulls,
                wardens_npcs.tumeken_charging_skulls,
                wardens_npcs.elidinis_mage,
                wardens_npcs.elidinis_range,
                wardens_npcs.tumeken_mage,
                wardens_npcs.tumeken_range,
                wardens_npcs.elidinis_final,
                wardens_npcs.tumeken_final,
                wardens_npcs.elidinis_core,
                wardens_npcs.tumeken_core,
            ),
        )
        toaMonsterDeaths(
            rooms,
            npcRepo,
            mapOf(wardens_npcs.siphon to null, wardens_npcs.energy to null),
        )
    }

    private suspend fun ProtectedAccess.talkToOsmumten(npc: Npc) {
        if (first() == null) {
            return
        }
        startDialogue(npc) {
            chatNpc(
                neutral,
                "Amascut's Wardens await beyond. Tell me when you are ready to begin, and we " +
                    "shall start once your whole party is.",
            )
        }
    }

    /**
     * The crystal at the top of the first room takes a player onto the arena floor; the one raised
     * over the void when the final Warden falls takes the party to the burial chamber.
     */
    private fun ProtectedAccess.useCrystal() {
        if (raids.isGhost(player)) {
            mes("A mysterious force prevents you from doing that.")
            return
        }
        val raid = raids.raidOf(player) ?: return
        when (raid.member(player)?.room) {
            ToaRoom.WardensFirst -> {
                val challenge = ToaRoom.WardensFirst.challenge ?: return
                telejump(raid.regionOf(challenge))
            }
            ToaRoom.WardensFinal -> {
                (rooms.runtime(raid, ToaRoom.WardensFinal) as? WardensFinalRuntime)?.useCrystal()
            }
            else -> {}
        }
    }

    private fun ProtectedAccess.first(): WardensRuntime? {
        if (raids.isGhost(player)) {
            mes("A mysterious force prevents you from doing that.")
            return null
        }
        val raid = raids.raidOf(player) ?: return null
        if (raid.member(player)?.room != ToaRoom.WardensFirst) {
            return null
        }
        return rooms.ensure(raid, ToaRoom.WardensFirst) as? WardensRuntime
    }
}
