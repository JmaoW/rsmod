package org.rsmod.content.other.dragonfire

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.category.isType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * How dragons breathe fire (OSRS Wiki, "Dragonfire"). A dragon fights in melee and breathes at its
 * [DragonKind.fireOdds]:
 * - the chromatic dragons (green, blue, red, black, lava, reanimated) only breathe next to their
 *   target, one attack in six;
 * - the brutal and metal dragons, the King Black Dragon and Elvarg breathe from a distance too (up
 *   to [BREATH_RANGE] tiles) and mix breath into their melee up close.
 *
 * The breath rolls the dragon's Magic accuracy against the player's Magic defence, and what it can
 * hit for then depends on the shield, prayer and antifire potion the player has (see [Breath] and
 * [Dragonfire]). Baby dragons never breathe.
 *
 * **Drakes** are different (OSRS Wiki, "Drake"). They shoot, a ranged attack of up to
 * [DRAKE_MAX_HIT] that Protect from Missiles stops, and after every [DRAKE_SHOTS] shots they send a
 * volcanic breath at the tile the player stands on. It is treated as dragonfire ([DrakeBreath]): it
 * burns the player four times, but a player who steps off the tile before it lands is not hit.
 *
 * Not built: the King Black Dragon's poison, freeze and stat-drain side effects, the dragonfire
 * shield's charges and special attack, the brutal dragons' magic attack, wyverns, Vorkath and
 * Galvek.
 */
class DragonScript
@Inject
constructor(
    private val attacks: NpcAttacks,
    private val accuracy: AccuracyFormulae,
    private val dragonfire: Dragonfire,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (kind in DragonKind.entries) {
            for (type in DragonNpcs.of(kind)) {
                // The op handler is next to the target (melee reach), the ap handler is from a
                // distance, up to the type's attack range.
                onAiOpPlayer2(type) { fight(kind, it.target, nextToTarget = true) }
                if (kind.longRanged) {
                    onAiApPlayer2(type) { fight(kind, it.target, nextToTarget = false) }
                }
            }
        }
    }

    /** How many shots each drake has taken since its last breath. */
    private val drakeShots = WeakHashMap<Npc, Int>()

    private fun StandardNpcAccess.fight(kind: DragonKind, target: Player, nextToTarget: Boolean) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        if (kind == DragonKind.Drake) {
            drakeAttack(target)
        } else if (!nextToTarget || random.of(kind.fireOdds) == 0) {
            breathe(kind, target)
        } else {
            attacks.attack(npc, target, meleeOf(npc))
        }
        actionDelay = mapClock + npc.attackRate()
    }

    private fun StandardNpcAccess.breathe(kind: DragonKind, target: Player) {
        val style = styleOf(kind)
        val burnt = accuracy.rollMagicAccuracy(npc, target, random)
        attacks.attack(
            npc,
            target,
            style.attack,
            onImpact =
                NpcImpactListener { player, hit ->
                    dragonfire.resolve(player, hit, style.breath, burnt)
                },
        )
    }

    /** A drake shoots seven times, then breathes. */
    private fun StandardNpcAccess.drakeAttack(target: Player) {
        val shots = drakeShots.getOrDefault(npc, 0)
        if (shots < DRAKE_SHOTS) {
            drakeShots[npc] = shots + 1
            attacks.attack(npc, target, DRAKE_SHOT)
            return
        }
        drakeShots[npc] = 0
        // The breath is aimed at the tile the player is on now. Whether it hits is settled when it
        // lands, and the three hits after the first follow the first.
        val aimedAt = target.coords
        var struck: Boolean? = null
        attacks.attack(
            npc,
            target,
            DRAKE_BREATH,
            onImpact =
                NpcImpactListener { player, hit ->
                    val landed = struck ?: (player.coords == aimedAt)
                    struck = landed
                    dragonfire.resolveDrake(player, hit, landed)
                },
        )
    }

    /** The breath [kind] uses this time. The King Black Dragon picks one of its four at random. */
    private fun styleOf(kind: DragonKind): BreathStyle =
        when (kind) {
            DragonKind.Chromatic -> CHROMATIC
            DragonKind.Brutal -> BRUTAL
            DragonKind.Metallic -> METALLIC
            DragonKind.Elvarg -> ELVARG
            DragonKind.Drake -> error("Drakes do not use a breath style")
            DragonKind.KingBlackDragon ->
                if (random.of(2) == 0) KING_FIRE else KING_SPECIAL[random.of(KING_SPECIAL.size)]
        }

    /** The dragon's own bite or claw: a normal melee attack with its stats and animation. */
    private fun meleeOf(npc: Npc): NpcAttack {
        val category = npc.visType.paramOrNull(params.npc_attack_type)
        val type =
            when {
                category.isType(categories.attacktype_stab) -> MeleeAttackType.Stab
                category.isType(categories.attacktype_slash) -> MeleeAttackType.Slash
                else -> MeleeAttackType.Crush
            }
        return NpcAttack(NpcAttackStyle.Melee, meleeType = type)
    }

    /** One way of breathing: the table it uses and how it looks. */
    class BreathStyle(val breath: Breath, anim: SeqType, projectile: SpotanimType) {
        /**
         * The hit is made typeless and always lands with a placeholder damage: [Dragonfire] works
         * out the real damage when the breath lands, from what the player has on then.
         */
        val attack =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = anim,
                projectile = projectile,
                projanim = projanims.magic_spell,
                maxHit = breath.maxHit(DragonfireProtection.NONE, burntRoll = true),
                alwaysHits = true,
                typeless = true,
            )
    }

    companion object {
        /** How far from its target a long-ranged dragon breathes. A guess, the Wiki gives none. */
        const val BREATH_RANGE = 5

        private val CHROMATIC =
            BreathStyle(Breath.Chromatic, DragonSeqs.breath, DragonSpotanims.fireball)
        private val BRUTAL = BreathStyle(Breath.Chromatic, DragonSeqs.breath, DragonSpotanims.fire)
        private val METALLIC = BreathStyle(Breath.Metallic, DragonSeqs.breath, DragonSpotanims.fire)
        private val ELVARG = BreathStyle(Breath.Elvarg, DragonSeqs.breath, DragonSpotanims.fire)

        const val DRAKE_MAX_HIT = 15

        /** How many shots a drake takes before it breathes. */
        const val DRAKE_SHOTS = 7

        private val DRAKE_SHOT =
            NpcAttack(
                style = NpcAttackStyle.Ranged,
                anim = DragonSeqs.drake_attack,
                projectile = DragonSpotanims.drake_range,
                projanim = projanims.magic_spell,
                maxHit = DRAKE_MAX_HIT,
            )

        /**
         * The first hit lands when the breath does; the damage here is a placeholder, [Dragonfire]
         * works out the real damage of each of the four when it lands.
         */
        private val DRAKE_BREATH =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = DragonSeqs.drake_attack,
                projectile = DragonSpotanims.drake_burn,
                projanim = projanims.magic_spell,
                impactSpot = DragonSpotanims.drake_burn_impact,
                maxHit = DrakeBreath.UNPROTECTED.last,
                minHit = DrakeBreath.UNPROTECTED.first,
                alwaysHits = true,
                typeless = true,
                extraHits = DrakeBreath.HITS - 1,
            )
        private val KING_FIRE =
            BreathStyle(Breath.KingBlackDragon, DragonSeqs.breath, DragonSpotanims.fire)
        private val KING_SPECIAL =
            listOf(
                BreathStyle(
                    Breath.KingBlackDragonSpecial,
                    DragonSeqs.breath,
                    DragonSpotanims.toxic,
                ),
                BreathStyle(Breath.KingBlackDragonSpecial, DragonSeqs.breath, DragonSpotanims.ice),
                BreathStyle(Breath.KingBlackDragonSpecial, DragonSeqs.breath, DragonSpotanims.shock),
            )
    }
}
