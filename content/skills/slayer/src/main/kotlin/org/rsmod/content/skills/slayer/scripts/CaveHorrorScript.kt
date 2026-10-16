package org.rsmod.content.skills.slayer.scripts

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.front
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object CaveHorrorNpcs : NpcReferences() {
    val worker = find("harmless_island_cave_horror_worker")
    val young_worker = find("harmless_island_cave_horror_young_worker")
    val guard = find("harmless_island_cave_horror_guard")
    val small_guard = find("harmless_island_cave_horror_small_guard")
    val alpha = find("harmless_island_cave_horror_alpha")
}

object CaveHorrorSeqs : SeqReferences() {
    val howl = find("harmless_island_cave_horror_howl")
}

/**
 * How the cave horrors of the Mos Le'Harmless cave fight (OSRS Wiki). They have two attacks:
 * - a **kick**, a magic-based melee hit rolled against the player's Magic defence, up to 9, every 4
 *   ticks;
 * - a **scream**, which always does damage equal to 10% of the player's base Hitpoints level
 *   (rounded down) and never misses, every 5 ticks.
 *
 * Without a witchwood icon in the amulet slot they only scream. With one they pick at random
 * between the two, so the icon halves how often the scream comes. Both are melee hits, so Protect
 * from Melee stops all of the damage.
 *
 * Not built: the scream's area effect (it also hits players standing next to the horror who are not
 * fighting it), and the cave abomination (the superior horror) keeps the default attacks.
 */
class CaveHorrorScript
@Inject
constructor(
    private val attacks: NpcAttacks,
    private val accuracy: AccuracyFormulae,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in TYPES) {
            onAiOpPlayer2(type) { fight(it.target) }
        }
    }

    private fun StandardNpcAccess.fight(target: Player) {
        if (actionDelay > mapClock) {
            return
        }
        val scream = screams(target)
        attacks.attack(npc, target, if (scream) screamOf(target) else kickOf(npc, target))
        actionDelay = mapClock + if (scream) SCREAM_RATE else KICK_RATE
    }

    /** Whether the horror's next attack on [target] is the scream. */
    fun screams(target: Player): Boolean =
        !target.wearsIcon() || random.of(100) < ICON_SCREAM_CHANCE

    private fun Player.wearsIcon(): Boolean = front?.isType(SlayerObjs.witchwood_icon) == true

    /** The scream: 10% of [target]'s base Hitpoints level, whatever their armour. */
    fun screamOf(target: Player): NpcAttack {
        val damage = target.baseHitpointsLvl * SCREAM_PERCENT / 100
        return NpcAttack(
            style = NpcAttackStyle.Melee,
            anim = CaveHorrorSeqs.howl,
            minHit = damage,
            maxHit = damage,
            alwaysHits = true,
        )
    }

    /** The kick: it is melee for prayers, but the player's Magic defence decides if it lands. */
    fun kickOf(npc: Npc, target: Player): NpcAttack {
        val lands = accuracy.rollMagicAccuracy(npc, target, random)
        return NpcAttack(
            style = NpcAttackStyle.Melee,
            maxHit = if (lands) KICK_MAX_HIT else 0,
            alwaysHits = true,
        )
    }

    companion object {
        const val KICK_RATE = 4
        const val SCREAM_RATE = 5
        const val KICK_MAX_HIT = 9
        const val SCREAM_PERCENT = 10

        /** The odds, out of 100, that a horror screams at a player with the icon: "random". */
        const val ICON_SCREAM_CHANCE = 50

        val TYPES =
            listOf(
                CaveHorrorNpcs.worker,
                CaveHorrorNpcs.young_worker,
                CaveHorrorNpcs.guard,
                CaveHorrorNpcs.small_guard,
                CaveHorrorNpcs.alpha,
            )
    }
}
