package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object RevenantNpcs : NpcReferences() {
    val imp = find("wild_cave_imp")
    val goblin = find("wild_cave_goblin")
    val pyrefiend = find("wild_cave_pyrefiend")
    val hobgoblin = find("wild_cave_hobgoblin")
    val cyclops = find("wild_cave_cyclops")
    val hellhound = find("wild_cave_hellhound")
    val demon = find("wild_cave_demon")
    val ork = find("wild_cave_ork")
    val dark_beast = find("wild_cave_darkbeast")
    val knight = find("wild_cave_knight")
    val dragon = find("wild_cave_dragon")
}

object RevenantSpotanims : SpotanimReferences() {
    val magic_travel = find("revenant_magic_travel")
    val magic_impact = find("revenant_magic_impact")
    val arrow_launch = find("wild_cave_bow_arrow_launch")
    val arrow_travel = find("wild_cave_bow_arrow_travel")
}

/** One revenant kind and its Wiki max hit (the same for all three attack styles). */
data class RevenantKind(val type: NpcType, val maxHit: Int)

/**
 * How the Revenant Caves' revenants fight (OSRS Wiki): they use melee, ranged and magic, and pick
 * the style the player is weakest to (the lowest defence bonus), avoiding the one the player has a
 * protection prayer up against. They hit very reliably, and heal themselves when under half their
 * hitpoints: a heal is a fraction of their health, at most one every 15 ticks, and each revenant
 * has 8 to 36 of them.
 *
 * Guesses where the Wiki is silent: the attack speed (4 ticks), the size of a heal (a sixth of
 * their hitpoints), the odds of healing on a given attack (one in three while hurt) and how often
 * the weakest style is picked (60%). The bracelet of ethereum is not handled.
 */
class DungeonRevenantScript
@Inject
constructor(
    private val attacks: NpcAttacks,
    private val bonuses: WornBonuses,
    private val random: GameRandom,
) : PluginScript() {
    /** The cooldown and the heals left of each revenant that has started to heal. */
    private class HealState(var nextHeal: Int, var healsLeft: Int)

    private val healing = java.util.IdentityHashMap<Npc, HealState>()

    override fun ScriptContext.startup() {
        for (kind in KINDS) {
            onAiOpPlayer2(kind.type) { fight(kind, it.target) }
        }
    }

    private fun StandardNpcAccess.fight(kind: RevenantKind, target: Player) {
        if (actionDelay > mapClock) {
            return
        }
        val style = pickStyle(target)
        attacks.attack(npc, target, attackOf(kind, style))
        actionDelay = mapClock + ATTACK_RATE
        maybeHeal(npc)
    }

    /** The attack style the revenant uses against [target]. */
    fun pickStyle(target: Player): NpcAttackStyle {
        val options = NpcAttackStyle.entries.toMutableList()
        val protectedFrom = protectedStyles(target)
        if (options.size > protectedFrom.size) {
            options.removeAll(protectedFrom)
        }
        // The weakest defence is chosen most of the time, so a player cannot just wait for the
        // style to come round; the rest of the time it is a random one of the options.
        val weakest = options.minBy { defenceAgainst(target, it) }
        return if (random.of(100) < WEAKEST_CHANCE) weakest else options[random.of(options.size)]
    }

    private fun protectedStyles(target: Player): Set<NpcAttackStyle> {
        val result = HashSet<NpcAttackStyle>()
        if (target.vars[varbits.protect_from_melee] == 1) result += NpcAttackStyle.Melee
        if (target.vars[varbits.protect_from_missiles] == 1) result += NpcAttackStyle.Ranged
        if (target.vars[varbits.protect_from_magic] == 1) result += NpcAttackStyle.Magic
        return result
    }

    private fun defenceAgainst(target: Player, style: NpcAttackStyle): Int =
        when (style) {
            NpcAttackStyle.Melee ->
                maxOf(
                    bonuses.defensiveStabBonus(target),
                    bonuses.defensiveSlashBonus(target),
                    bonuses.defensiveCrushBonus(target),
                )
            NpcAttackStyle.Ranged -> bonuses.defensiveRangedBonus(target)
            NpcAttackStyle.Magic -> bonuses.defensiveMagicBonus(target)
        }

    /** Heals [npc] if it is under half its hitpoints and a heal is due. */
    fun maybeHeal(npc: Npc) {
        val max = npc.baseHitpointsLvl
        if (npc.hitpoints * 2 >= max || npc.hitpoints <= 0) {
            return
        }
        val now = npc.currentMapClock
        val state = healing.getOrPut(npc) { HealState(0, random.of(MIN_HEALS, MAX_HEALS)) }
        if (state.healsLeft <= 0 || now < state.nextHeal || random.of(HEAL_ODDS) != 0) {
            return
        }
        state.healsLeft--
        state.nextHeal = now + HEAL_COOLDOWN
        npc.hitpoints = minOf(max, npc.hitpoints + maxOf(1, max / HEAL_DIVISOR))
    }

    companion object {
        const val ATTACK_RATE = 4
        const val WEAKEST_CHANCE = 60
        const val HEAL_COOLDOWN = 15
        const val MIN_HEALS = 8
        const val MAX_HEALS = 36
        const val HEAL_ODDS = 3
        const val HEAL_DIVISOR = 6

        /** The Wiki's max hits. */
        val KINDS: List<RevenantKind> =
            listOf(
                RevenantKind(RevenantNpcs.imp, 2),
                RevenantKind(RevenantNpcs.goblin, 3),
                RevenantKind(RevenantNpcs.pyrefiend, 5),
                RevenantKind(RevenantNpcs.hobgoblin, 8),
                RevenantKind(RevenantNpcs.cyclops, 16),
                RevenantKind(RevenantNpcs.hellhound, 14),
                RevenantKind(RevenantNpcs.demon, 18),
                RevenantKind(RevenantNpcs.ork, 20),
                RevenantKind(RevenantNpcs.dark_beast, 23),
                RevenantKind(RevenantNpcs.knight, 27),
                RevenantKind(RevenantNpcs.dragon, 30),
            )

        /**
         * One attack of [kind] in [style]. Revenants rarely miss, so the accuracy roll is skipped.
         */
        fun attackOf(kind: RevenantKind, style: NpcAttackStyle): NpcAttack =
            when (style) {
                NpcAttackStyle.Melee ->
                    NpcAttack(style = style, maxHit = kind.maxHit, alwaysHits = true)
                NpcAttackStyle.Ranged ->
                    NpcAttack(
                        style = style,
                        castSpot = RevenantSpotanims.arrow_launch,
                        projectile = RevenantSpotanims.arrow_travel,
                        projanim = projanims.arrow,
                        maxHit = kind.maxHit,
                        alwaysHits = true,
                    )
                NpcAttackStyle.Magic ->
                    NpcAttack(
                        style = style,
                        projectile = RevenantSpotanims.magic_travel,
                        projanim = projanims.magic_spell,
                        impactSpot = RevenantSpotanims.magic_impact,
                        maxHit = kind.maxHit,
                        alwaysHits = true,
                    )
            }
    }
}
