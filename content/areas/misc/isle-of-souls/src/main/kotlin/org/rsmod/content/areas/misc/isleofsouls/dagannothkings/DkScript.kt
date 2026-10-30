package org.rsmod.content.areas.misc.isleofsouls.dagannothkings

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.npc.combatDefaultRetaliateAp
import org.rsmod.api.config.refs.queues
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Kings' ladders in and out of the lair, the timer that runs it, the kings' and spinolyps'
 * attacks and deaths, and the spinolyps' suspicious water, which cannot be hurt. The lair itself is
 * [DagannothKings]; the attacks are [DkAttacks].
 *
 * The real lair is at the bottom of the Waterbirth Island Dungeon, past its door puzzles, and its
 * ladder offers a Slayer-only lair for players on a dagannoth task. The isle has the ladder on the
 * field south of the plaza and one lair per team: "Standard" and "Slayer" both go into it (the
 * kings count for a Dagannoth task wherever they die), and "Peek" says how many of the team are
 * down there.
 */
class DkScript
@Inject
constructor(
    private val dks: DagannothKings,
    private val attacks: DkAttacks,
    private val instances: TeamInstances,
    private val npcDeath: NpcDeath,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val interactions: AiPlayerInteractions,
) : PluginScript() {
    override fun ScriptContext.startup() {
        instances.register(DkInstance.type)

        onGameStartup { placeLadder() }

        onOpLoc1(dk_locs.entrance) { climbDown() }
        onOpLoc2(dk_locs.entrance) {
            mes("The kings below count towards a Dagannoth Slayer task wherever you fight them.")
            climbDown()
        }
        onOpLoc4(dk_locs.entrance) { peek() }
        onOpLoc1(dk_locs.exit) { climbUp() }

        onPlayerSoftTimer(dk_timers.tick) {
            if (!dks.tick(player)) {
                player.clearSoftTimer(dk_timers.tick)
            }
        }
        onPlayerLogout { dks.sweep(except = player) }

        for (king in DkKing.entries) {
            onAiOpPlayer2(king.type) { fight(king, it.target) }
            if (king.ranged) {
                onAiApPlayer2(king.type) { fight(king, it.target) }
                // Prime and Supreme fight back from where they stand, not by walking up.
                onNpcQueue(king.type, queues.com_retaliate_player) {
                    npc.combatDefaultRetaliateAp(interactions)
                }
            }
            onNpcQueue(king.type, queues.death) { dieKing() }
        }

        for (kind in DkSpinolyps.kinds) {
            // A spinolyp never moves: it does not walk up to whoever hits it, it just shoots.
            onNpcQueue(kind.visible, queues.com_retaliate_player) {}
            onNpcQueue(kind.submerged, queues.com_retaliate_player) {}
            onNpcQueue(kind.visible, queues.death) { dieSpinolyp() }
            onNpcQueue(kind.submerged, queues.death) { dieSpinolyp() }
        }

        onEvent<NpcHitEvents.ModifyAny> {
            if (DkSpinolyps.isSubmerged(npc)) {
                hit.damage = 0
            }
        }
    }

    /** Puts the Kings' ladder down on the isle's field. */
    fun placeLadder() {
        locRepo.add(
            DkLadder.coords,
            locTypes[dk_locs.entrance],
            duration = Int.MAX_VALUE,
            angle = DkLadder.angle,
            shape = LocShape.CentrepieceStraight,
        )
    }

    private suspend fun ProtectedAccess.climbDown() {
        anim(dk_seqs.climb_ladder)
        delay(2)
        when (dks.enter(player)) {
            EnterResult.Entered -> {}
            EnterResult.AlreadyInside -> mes("You are already in the lair.")
            EnterResult.NoSpace -> mes("There is no room for another lair right now. Try again.")
            EnterResult.InvalidEntry -> mes("The ladder will not take you down.")
        }
    }

    private suspend fun ProtectedAccess.climbUp() {
        anim(dk_seqs.climb_ladder)
        delay(2)
        dks.leave(player)
    }

    private fun ProtectedAccess.peek() {
        mes(peekMessage(dks.teammatesInside(player)))
    }

    private fun StandardNpcAccess.fight(king: DkKing, target: Player) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        attacks.king(npc, king, target)
        actionDelay = mapClock + DkKing.ATTACK_RATE
    }

    /** A king dies where it stands; the drops fall at the middle of its 3x3. */
    private suspend fun StandardNpcAccess.dieKing() {
        val dropAt = dks.kingDied(npc) ?: coords
        npcDeath.deathWithDrops(this, dropAt)
    }

    private suspend fun StandardNpcAccess.dieSpinolyp() {
        dks.spinolypDied(npc)
        npcDeath.deathWithDrops(this)
    }

    companion object {
        fun peekMessage(inside: Int): String =
            when (inside) {
                0 -> "You peek down the ladder. Nobody from your team is in the lair."
                1 -> "You peek down the ladder. One of your team is in the lair."
                else -> "You peek down the ladder. $inside of your team are in the lair."
            }
    }
}
