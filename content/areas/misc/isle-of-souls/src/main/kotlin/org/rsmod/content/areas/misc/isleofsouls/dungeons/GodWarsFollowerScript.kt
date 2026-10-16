package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object GodWarsSeqs : SeqReferences() {
    val bandos_ranger = find("godwars_spiritual_ork_ranger_ranged_attack")
    val bandos_mage = find("godwars_spiritual_ork_mage_magic_attack")
    val armadyl_mage = find("godwars_spiritual_armadyl_warrior_magic")
    val armadyl_spear = find("godwars_armadyl_spear_attack")
    val armadyl_cannon = find("godwars_armadyl_cannon_attack")
    val armadyl_sword = find("godwars_armadyl_sword_attack")

    // Kree'arra's own set.
    val kree_wind = find("godwars_armadyl_avatar_wind_attack")
    val kree_claw = find("godwars_armadyl_avatar_claw_attack")
    val kree_defend = find("godwars_armadyl_avatar_defend")
    val kree_death = find("godwars_armadyl_avatar_death")

    // General Graardor's death (his attack and defend anims are already derived).
    val graardor_death = find("godwars_bandos_death")

    // Bree the centaur's defend and death (her ranged attack is in the Saradomin script).
    val centaur_defend = find("godwars_centaur_defend")
    val centaur_death = find("godwars_centaur_death")
}

object GodWarsSpotanims : SpotanimReferences() {
    val bandos_ranger_cast = find("godwars_spiritual_ork_ranger_ranged_attack_spotanim")
    val armadyl_mage_cast = find("godwars_spiritual_armadyl_warrior_magic_spotanim")
    val armadyl_spear_travel = find("godwars_armadyl_spear_travel_spotanim")

    // The bolt Wingman Skree shoots: release, flight and impact graphics.
    val skree_bolt_cast = find("godwars_armadyl_bolt_attack_spotanim")
    val skree_bolt_travel = find("godwars_armadyl_bolt_attack_projanim")
    val skree_bolt_hit = find("godwars_armadyl_bolt_hit_spotanim")

    // Kree'arra's grey (ranged) and blue (magic) dust tornadoes.
    val kree_wind_travel = find("godwars_armadyl_avatar_wind_attack_spotanim")
    val kree_magic_travel = find("godwars_armadyl_avatar_magic_attack_spotanim")
}

/**
 * How one follower attacks: [main] every [rate] ticks, or [close] one time in [closeOdds] while the
 * player is next to it. A [magicalMelee] follower hits as melee (so Protect from Melee stops it)
 * but the player's Magic defence decides whether it lands, as with the Wiki's "magical melee".
 */
class FollowerProfile(
    val rate: Int,
    val main: NpcAttack,
    val close: NpcAttack? = null,
    val closeOdds: Int = 2,
    val magicalMelee: Boolean = false,
)

/**
 * The God Wars followers that do not fight in melee only (OSRS Wiki, "Followers in each area" and
 * each monster's page): aviansies and the Armadyl spiritual warrior and ranger shoot, the spiritual
 * mages and the Saradomin wizard cast, and bloodvelds hit with "magical melee" from next to the
 * player. Every other follower keeps the standard melee fight with its cache stats.
 * - **Attack rates and max hits** are the Wiki's (spiritual rangers 4/4/4/3 ticks and 15/16/13/16
 *   for Zamorak/Saradomin/Bandos/Armadyl, spiritual mages 4/4/5/3 ticks and 19/20/17/17, the
 *   Armadyl spiritual warrior 3 ticks and 15, aviansies 4 ticks and 8 to 16 by level, the wizard 4
 *   ticks with 20 magic or 14 stab, bloodvelds 4 ticks and 6). Accuracy comes from the npc's cache
 *   stats.
 * - **Graphics** are the cache's where a name says whose they are (the Bandos spiritual ranger and
 *   mage, the Armadyl spiritual mage and warrior). The rest use a plain bow or cast animation and
 *   the standard arrow or magic projectile, which is a guess to check in a client.
 */
class GodWarsFollowerScript
@Inject
constructor(
    private val attacks: NpcAttacks,
    private val accuracy: AccuracyFormulae,
    private val random: GameRandom,
    private val npcTypes: NpcTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (name in GodWarsNames.ranged + GodWarsNames.magicMelee) {
            val type = GodWarsRoster.byName.getValue(name)
            val profile = profileOf(name, npcTypes[type].vislevel)
            onAiOpPlayer2(type) { fight(profile, it.target) }
            if (name in GodWarsNames.ranged) {
                onAiApPlayer2(type) { fight(profile, it.target) }
            }
        }
    }

    private fun StandardNpcAccess.fight(profile: FollowerProfile, target: Player) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        val close = profile.close
        val useClose =
            close != null && isWithinDistance(target, 1) && random.of(profile.closeOdds) == 0
        val attack = if (useClose) close!! else profile.main
        attacks.attack(
            npc,
            target,
            if (profile.magicalMelee) magicalMelee(attack, target) else attack,
        )
        actionDelay = mapClock + profile.rate
    }

    /** [attack] rolled once as magical melee: it always hits, for nothing if the roll fails. */
    private fun StandardNpcAccess.magicalMelee(attack: NpcAttack, target: Player): NpcAttack =
        attack.copy(
            alwaysHits = true,
            maxHit =
                if (accuracy.rollMagicalMeleeAccuracy(npc, target, random)) attack.maxHit else 0,
        )

    companion object {
        private const val AVIANSIE_RATE = 4
        private const val WIZARD_RATE = 4
        private const val BLOODVELD_RATE = 4
        private const val WIZARD_STAB_MAX = 14
        private const val WIZARD_MAGIC_MAX = 20
        private const val BLOODVELD_MAX = 6

        /** Aviansie max hit by combat level, from the Wiki's page; (79 has two: 9 and 11). */
        private val AVIANSIE_MAX_HITS =
            sortedMapOf(
                69 to 8,
                71 to 10,
                73 to 10,
                79 to 10,
                83 to 9,
                84 to 10,
                89 to 12,
                92 to 10,
                94 to 11,
                97 to 11,
                131 to 15,
                137 to 15,
                148 to 16,
            )

        /** Rate and max hit per god (Armadyl, Bandos, Saradomin, Zamorak) of the spirituals. */
        private val RANGER =
            mapOf(
                God.ZAMORAK to (4 to 15),
                God.SARADOMIN to (4 to 16),
                God.BANDOS to (4 to 13),
                God.ARMADYL to (3 to 16),
            )
        private val MAGE =
            mapOf(
                God.ZAMORAK to (4 to 19),
                God.SARADOMIN to (4 to 20),
                God.BANDOS to (5 to 17),
                God.ARMADYL to (3 to 17),
            )
        private const val ARMADYL_WARRIOR_RATE = 3
        private const val ARMADYL_WARRIOR_MAX = 15

        private fun arrow(
            maxHit: Int,
            anim: org.rsmod.game.type.seq.SeqType? = null,
            cast: org.rsmod.game.type.spot.SpotanimType? = null,
        ) =
            NpcAttack(
                style = NpcAttackStyle.Ranged,
                anim = anim,
                castSpot = cast,
                projectile = RevenantSpotanims.arrow_travel,
                projanim = projanims.arrow,
                maxHit = maxHit,
            )

        private fun spell(
            maxHit: Int,
            anim: org.rsmod.game.type.seq.SeqType? = null,
            cast: org.rsmod.game.type.spot.SpotanimType? = null,
        ) =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = anim ?: seqs.human_castwave,
                castSpot = cast,
                projectile = RevenantSpotanims.magic_travel,
                projanim = projanims.magic_spell,
                impactSpot = RevenantSpotanims.magic_impact,
                maxHit = maxHit,
            )

        /** The Wiki's max hit for an aviansie of [level], using the nearest level at or below. */
        fun aviansieMaxHit(level: Int): Int =
            AVIANSIE_MAX_HITS.headMap(level + 1).let {
                if (it.isEmpty()) AVIANSIE_MAX_HITS.values.first() else it.getValue(it.lastKey())
            }

        /** The profile of the follower called [name], whose cache combat level is [level]. */
        fun profileOf(name: String, level: Int): FollowerProfile {
            val god = GodWarsNames.godOf(name)
            return when {
                "_armor" in name ->
                    FollowerProfile(
                        AVIANSIE_RATE,
                        NpcAttack(
                            style = NpcAttackStyle.Ranged,
                            anim = GodWarsSeqs.armadyl_spear,
                            projectile = GodWarsSpotanims.armadyl_spear_travel,
                            projanim = projanims.arrow,
                            maxHit = aviansieMaxHit(level),
                        ),
                    )
                name == "godwars_spiritual_armadyl_warrior" ->
                    FollowerProfile(
                        ARMADYL_WARRIOR_RATE,
                        NpcAttack(
                            style = NpcAttackStyle.Ranged,
                            anim = GodWarsSeqs.armadyl_spear,
                            projectile = GodWarsSpotanims.armadyl_spear_travel,
                            projanim = projanims.arrow,
                            maxHit = ARMADYL_WARRIOR_MAX,
                        ),
                    )
                name.endsWith("_ranger") -> {
                    val (rate, max) = RANGER.getValue(god!!)
                    val bandos = god == God.BANDOS
                    FollowerProfile(
                        rate,
                        arrow(
                            max,
                            anim = if (bandos) GodWarsSeqs.bandos_ranger else seqs.human_bow,
                            cast = if (bandos) GodWarsSpotanims.bandos_ranger_cast else null,
                        ),
                    )
                }
                name.endsWith("_mage") -> {
                    val (rate, max) = MAGE.getValue(god!!)
                    FollowerProfile(
                        rate,
                        when (god) {
                            God.BANDOS -> spell(max, anim = GodWarsSeqs.bandos_mage)
                            God.ARMADYL ->
                                spell(
                                    max,
                                    anim = GodWarsSeqs.armadyl_mage,
                                    cast = GodWarsSpotanims.armadyl_mage_cast,
                                )
                            else -> spell(max)
                        },
                    )
                }
                name == "godwars_ancient_saradomin_wizard" ->
                    FollowerProfile(
                        WIZARD_RATE,
                        spell(WIZARD_MAGIC_MAX),
                        close =
                            NpcAttack(
                                style = NpcAttackStyle.Melee,
                                meleeType = MeleeAttackType.Stab,
                                maxHit = WIZARD_STAB_MAX,
                            ),
                    )
                name == "godwars_bloodveld" ->
                    FollowerProfile(
                        BLOODVELD_RATE,
                        NpcAttack(
                            style = NpcAttackStyle.Melee,
                            meleeType = MeleeAttackType.Slash,
                            maxHit = BLOODVELD_MAX,
                        ),
                        magicalMelee = true,
                    )
                else -> error("No ranged or magic profile for $name")
            }
        }
    }
}
