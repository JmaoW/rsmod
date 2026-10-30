package org.rsmod.content.areas.misc.isleofsouls.kingblackdragon

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PlayerPoison
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.other.dragonfire.BreathEffect
import org.rsmod.content.other.dragonfire.DragonBreathLanded
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The levers in and out of the King Black Dragon's lair, the timer that runs the fight, the
 * dragon's death, and what its special breaths do besides burn. The fight itself is
 * [KingBlackDragon]; how it breathes is `DragonScript`.
 *
 * The real lair is reached by a ladder and a lever surrounded by spiders, and asks 50,000 coins for
 * a private instance. The isle has the lever on a wall in the Revenant Caves, no fee, and only the
 * private lair: every team has a lair of its own.
 */
class KbdScript
@Inject
constructor(
    private val kbd: KingBlackDragon,
    private val instances: TeamInstances,
    private val npcDeath: NpcDeath,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val poison: PlayerPoison,
    private val seqTypes: SeqTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        instances.register(KbdInstance.type)

        onGameStartup { placeLever() }

        // The wiki's "normal" and "private" ways in are the same here: the isle has only the
        // private lair. "Commune" has nothing to say.
        onOpLoc1(kbd_locs.entrance) { pull() }
        onOpLoc2(kbd_locs.entrance) { mes("You commune with the lever, but nothing answers.") }
        onOpLoc5(kbd_locs.entrance) { pull() }
        onOpLoc1(kbd_locs.exit) { leave() }

        onPlayerSoftTimer(kbd_timers.tick) {
            if (!kbd.tick(player)) {
                player.clearSoftTimer(kbd_timers.tick)
            }
        }
        onPlayerLogout { kbd.sweep(except = player) }

        onNpcQueue(kbd_npcs.boss, queues.death) { die() }

        onEvent<DragonBreathLanded> { applyEffect(player, effect) }

        onCommand("kbdanim") {
            modLevel = modlevels.admin
            desc = "Play a seq on the King Black Dragon of the lair you are in"
            invalidArgs = "Use as ::kbdanim seqId (e.g. 80 claw, 84 breath, 91 bite, 4638 block)"
            cheat {
                val id = args.getOrNull(0)?.toIntOrNull()
                val seq = id?.let { seqTypes[it] }
                val boss = kbd.runOf(player)?.boss
                if (seq == null || boss == null) {
                    player.mes("Stand in the lair: ::kbdanim seqId")
                } else {
                    boss.anim(seq)
                    player.mes("The King Black Dragon plays seq $id")
                }
            }
        }
    }

    /** Hangs the lever on its wall in the Revenant Caves. */
    fun placeLever() {
        locRepo.add(
            KbdLever.coords,
            locTypes[kbd_locs.entrance],
            duration = Int.MAX_VALUE,
            angle = KbdLever.angle,
            shape = LocShape.WallDecorStraightNoOffset,
        )
    }

    private suspend fun ProtectedAccess.pull() {
        anim(kbd_seqs.pull)
        delay(2)
        when (kbd.enter(player)) {
            EnterResult.Entered -> {}
            EnterResult.AlreadyInside -> mes("You are already in the lair.")
            EnterResult.NoSpace -> mes("There is no room for another lair right now. Try again.")
            EnterResult.InvalidEntry -> mes("The lever will not budge.")
        }
    }

    private suspend fun ProtectedAccess.leave() {
        anim(kbd_seqs.pull)
        delay(2)
        kbd.leave(player)
    }

    /** It dies where it stands; the drops fall at the middle of its 5x5. */
    private suspend fun StandardNpcAccess.die() {
        kbd.bossDied(npc)
        val dropAt = kbd.runOf(npc)?.dropAt ?: coords
        npcDeath.deathWithDrops(this, dropAt)
    }

    /**
     * The second effect of a special breath that has hurt [player] (wiki): poison starting at 8 (an
     * antipoison stops it), a freeze of 10 ticks, or all stats lowered by 2.
     */
    private fun applyEffect(player: Player, effect: BreathEffect) {
        when (effect) {
            BreathEffect.None -> {}
            BreathEffect.Poison -> poison.poison(player, POISON_DAMAGE)
            BreathEffect.Freeze -> {
                player.freeze(FREEZE_TICKS)
                player.mes("You have been frozen!")
            }
            BreathEffect.Shock -> {
                for (stat in DRAINED) {
                    player.statSub(stat, SHOCK_DRAIN, 0)
                }
                player.mes("You feel weaker as the lightning shocks you.")
            }
        }
    }

    companion object {
        const val POISON_DAMAGE = 8
        const val FREEZE_TICKS = 10
        const val SHOCK_DRAIN = 2

        /** Every stat but Hitpoints, which the damage of the breath already took from. */
        val DRAINED =
            listOf(
                stats.attack,
                stats.defence,
                stats.strength,
                stats.ranged,
                stats.prayer,
                stats.magic,
                stats.cooking,
                stats.woodcutting,
                stats.fletching,
                stats.fishing,
                stats.firemaking,
                stats.crafting,
                stats.smithing,
                stats.mining,
                stats.herblore,
                stats.agility,
                stats.thieving,
                stats.slayer,
                stats.farming,
                stats.runecrafting,
                stats.hunter,
                stats.construction,
            )
    }
}
