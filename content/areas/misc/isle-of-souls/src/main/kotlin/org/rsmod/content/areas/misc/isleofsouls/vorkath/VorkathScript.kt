package org.rsmod.content.areas.misc.isleofsouls.vorkath

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.queues
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The boat to the crater and the ice chunks out of it, the timer that runs the fight, the poke that
 * wakes Vorkath, the deaths of Vorkath and its spawn, and what hits on them really do. The fight
 * itself is [Vorkath]; its attacks are [VorkathAttacks].
 *
 * The real crater is on Ungael, reached by Torfinn's boat from Rellekka and private to each player.
 * The isle has the boat on the field south of the plaza, no fee, and one crater per team.
 */
class VorkathScript
@Inject
constructor(
    private val vorkath: Vorkath,
    private val instances: TeamInstances,
    private val npcDeath: NpcDeath,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        instances.register(VorkathInstance.type)

        onGameStartup { placeBoat() }

        onOpLoc1(vorkath_locs.boat) { travel() }
        onOpLoc1(vorkath_locs.ice_chunks) { leave() }

        onPlayerSoftTimer(vorkath_timers.tick) {
            if (!vorkath.tick(player)) {
                player.clearSoftTimer(vorkath_timers.tick)
            }
        }
        onPlayerLogout { vorkath.sweep(except = player) }

        onOpNpc1(vorkath_npcs.sleeping) { poke(it.npc) }

        // Vorkath never moves: it does not walk up to whoever hits it, the run aims its attacks.
        onNpcQueue(vorkath_npcs.boss, queues.com_retaliate_player) {}
        onNpcQueue(vorkath_npcs.boss, queues.death) { dieBoss() }

        // The spawn only walks up to its target and explodes (the run sees to that): it never
        // attacks, and does not turn on whoever hits it.
        onAiOpPlayer2(vorkath_npcs.spawn) {}
        onNpcQueue(vorkath_npcs.spawn, queues.com_retaliate_player) {}
        onNpcQueue(vorkath_npcs.spawn, queues.death) { dieSpawn() }

        onEvent<NpcHitEvents.ModifyAny> {
            when (npc.type.id) {
                vorkath_npcs.boss.id -> {
                    if (vorkath.isImmune(npc)) {
                        source?.mes(IMMUNE)
                    }
                    hit.damage = vorkath.incomingDamage(npc, hit.damage)
                }
                // Crumble Undead always kills the spawn (wiki).
                vorkath_npcs.spawn.id ->
                    if (hit.isSecondaryObj(objs.spell_crumble_undead)) {
                        hit.damage = npc.hitpoints
                    }
            }
        }

        onCommand("vorkathspecial") {
            modLevel = modlevels.admin
            desc = "Make your crater's Vorkath use a special next: acid or spawn"
            invalidArgs = "Use as ::vorkathspecial acid|spawn"
            cheat {
                val run = vorkath.runOf(player)
                val special =
                    when (args.getOrNull(0)?.lowercase()) {
                        "acid" -> VorkathSpecial.Acid
                        "spawn" -> VorkathSpecial.Spawn
                        else -> null
                    }
                if (run == null || special == null) {
                    player.mes("Stand in the crater: ::vorkathspecial acid|spawn")
                } else {
                    run.nextSpecial = special
                    run.attacks = Vorkath.STANDARDS_BEFORE_SPECIAL
                    player.mes("Vorkath will use its $special special next.")
                }
            }
        }
    }

    /** Puts the boat to Ungael on the isle's field. */
    fun placeBoat() {
        locRepo.add(
            VorkathBoat.coords,
            locTypes[vorkath_locs.boat],
            duration = Int.MAX_VALUE,
            angle = VorkathBoat.angle,
            shape = LocShape.CentrepieceStraight,
        )
    }

    private suspend fun ProtectedAccess.travel() {
        mes("You board the boat and sail to Ungael.")
        delay(2)
        when (vorkath.enter(player)) {
            EnterResult.Entered -> {}
            EnterResult.AlreadyInside -> mes("You are already on Ungael.")
            EnterResult.NoSpace -> mes("There is no room for another crater right now. Try again.")
            EnterResult.InvalidEntry -> mes("The boat will not leave.")
        }
    }

    private suspend fun ProtectedAccess.leave() {
        delay(1)
        vorkath.leave(player)
    }

    private fun ProtectedAccess.poke(npc: Npc) {
        anim(vorkath_seqs.poke)
        if (!vorkath.poke(player, npc)) {
            mes("Vorkath is already stirring.")
        }
    }

    /** Vorkath dies where it lies; the drops fall beside it, nearest whoever gets them. */
    private suspend fun StandardNpcAccess.dieBoss() {
        val dropAt = vorkath.bossDied(npc) ?: coords
        npcDeath.deathWithDrops(this, dropAt)
    }

    private suspend fun StandardNpcAccess.dieSpawn() {
        vorkath.spawnDied(npc)
        npcDeath.deathNoDrops(this)
    }

    companion object {
        const val IMMUNE = "The dragon is currently immune to your attacks."
    }
}
