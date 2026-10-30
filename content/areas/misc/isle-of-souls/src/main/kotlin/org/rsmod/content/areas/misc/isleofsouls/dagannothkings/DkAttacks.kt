package org.rsmod.content.areas.misc.isleofsouls.dagannothkings

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.NpcTargeting
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PlayerPoison
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The attacks of the Dagannoth Kings and the spinolyps (OSRS Wiki). Each king attacks every 4 ticks
 * with its one style; the protection prayer for that style blocks it.
 * - **Rex**: a slash, up to 26.
 * - **Prime**: a water-wave-like magic attack, up to 50, that also hits every other player within a
 *   tile of its target (the wiki's "3x3 radius around his target"), each with their own roll.
 * - **Supreme**: a spine, ranged, up to 30, also fired at every player within 3 tiles of its target
 *   (the wiki's "6x6 area around his target").
 *
 * A **spinolyp** has two attacks, each up to 10, and picks one at random (a guess: the wiki gives
 * no odds). Both roll its Ranged accuracy against the player's Ranged defence: a water-strike-like
 * one that does magic damage and drains a prayer point when it hits, and a ranged spine that can
 * poison (starting at 6). How often the spine poisons is not on the wiki; 1 in 4 hits is a guess.
 *
 * Not built: the spectral spirit shield halving the prayer drain, and the suspicious water's quick
 * melee attack on a player standing next to it.
 */
class DkAttacks
@Inject
constructor(
    private val attacks: NpcAttacks,
    private val targeting: NpcTargeting,
    private val accuracy: AccuracyFormulae,
    private val random: GameRandom,
    private val poison: PlayerPoison,
) {
    /** [npc], which is [king], attacks [primary], and for Prime and Supreme those round them. */
    fun king(npc: Npc, king: DkKing, primary: Player): List<Player> {
        val (attack, radius) =
            when (king) {
                DkKing.Rex -> REX_MELEE to 0
                DkKing.Prime -> PRIME_MAGIC to PRIME_SPLASH
                DkKing.Supreme -> SUPREME_RANGED to SUPREME_SPLASH
            }
        val targets = listOf(primary) + splashTargets(npc, primary, radius)
        for ((index, target) in targets.withIndex()) {
            attacks.attack(npc, target, attack, playAnim = index == 0)
        }
        return targets
    }

    /** The other players within [radius] tiles of [primary] that [npc] can reach. */
    fun splashTargets(npc: Npc, primary: Player, radius: Int): List<Player> {
        if (radius <= 0) {
            return emptyList()
        }
        return targeting.inRange(npc).filter {
            it !== primary && it.coords.chebyshevDistance(primary.coords) <= radius
        }
    }

    /** A spinolyp shoots at [target]: its magic or its ranged attack, at random. */
    fun spinolyp(npc: Npc, target: Player) {
        val lands = accuracy.rollRangedAccuracy(npc, target, random)
        val maxHit = if (lands) SPINOLYP_MAX else 0
        if (random.of(2) == 0) {
            val attack = SPINOLYP_MAGIC.copy(maxHit = maxHit)
            attacks.attack(npc, target, attack, onImpact = drainPrayer)
        } else {
            val attack = SPINOLYP_RANGED.copy(maxHit = maxHit)
            attacks.attack(npc, target, attack, onImpact = maybePoison)
        }
    }

    private val drainPrayer = NpcImpactListener { target, hit ->
        if (hit.damage > 0) {
            target.statSub(stats.prayer, PRAYER_DRAIN, 0)
        }
    }

    private val maybePoison = NpcImpactListener { target, hit ->
        if (hit.damage > 0 && random.of(POISON_ODDS) == 0) {
            poison.poison(target, POISON_DAMAGE)
        }
    }

    companion object {
        const val REX_MAX = 26
        const val PRIME_MAX = 50
        const val SUPREME_MAX = 30
        const val SPINOLYP_MAX = 10

        /** Prime also hits players within this many tiles of its target. */
        const val PRIME_SPLASH = 1

        /** Supreme also shoots at players within this many tiles of its target. */
        const val SUPREME_SPLASH = 3

        const val PRAYER_DRAIN = 1
        const val POISON_DAMAGE = 6

        /** One in this many of a spinolyp's ranged hits that do damage poisons. A guess. */
        const val POISON_ODDS = 4

        val REX_MELEE =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                anim = dk_seqs.rex_attack,
                meleeType = MeleeAttackType.Slash,
                maxHit = REX_MAX,
            )

        val PRIME_MAGIC =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = dk_seqs.prime_attack,
                projectile = dk_spotanims.prime_travel,
                projanim = projanims.magic_spell,
                impactSpot = dk_spotanims.prime_impact,
                maxHit = PRIME_MAX,
            )

        val SUPREME_RANGED =
            NpcAttack(
                style = NpcAttackStyle.Ranged,
                anim = dk_seqs.supreme_attack,
                projectile = dk_spotanims.supreme_travel,
                projanim = projanims.arrow,
                maxHit = SUPREME_MAX,
            )

        /** Its accuracy is rolled by [spinolyp], so it always "hits" for 0 up to the max set. */
        val SPINOLYP_MAGIC =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = dk_seqs.spinolyp_attack,
                projectile = dk_spotanims.spinolyp_magic_travel,
                projanim = projanims.magic_spell,
                impactSpot = dk_spotanims.spinolyp_magic_impact,
                maxHit = SPINOLYP_MAX,
                alwaysHits = true,
            )

        val SPINOLYP_RANGED =
            NpcAttack(
                style = NpcAttackStyle.Ranged,
                anim = dk_seqs.spinolyp_attack,
                projectile = dk_spotanims.spinolyp_ranged_travel,
                projanim = projanims.arrow,
                maxHit = SPINOLYP_MAX,
                alwaysHits = true,
            )
    }
}
