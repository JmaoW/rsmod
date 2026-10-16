package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object SaradominSeqs : SeqReferences() {
    val zilyana_magic = find("godwars_saradomin_magic_attack")
    val growler_magic = find("godwars_lion_magic_attack")
    val bree_shoot = find("godwars_centaur_attack_ranged")
}

object SaradominSpotanims : SpotanimReferences() {
    val zilyana_strike = find("godwars_saradomin_light_attk_spot")
    val growler_cast = find("godwars_lion_magic_spot")
    val growler_travel = find("godwars_lion_magic_proj")
    val bree_cast = find("godwars_centaur_arrow_launch")
    val bree_travel = find("godwars_centaur_arrow_proj")
}

/**
 * Commander Zilyana and her three bodyguards (OSRS Wiki: Commander Zilyana, her Strategies page,
 * and the bodyguards' pages).
 *
 * **Zilyana** attacks every 2 ticks, which is why she is so deadly despite the low hits, and only
 * from next to her target:
 * - a **melee** hit (crush), up to 27 (Protect from Melee);
 * - a **magic** hit, 10 to 20 when it lands (Protect from Magic).
 *
 * **Her bodyguards** attack every 5 ticks: Starlight the unicorn in melee (crush, up to 15),
 * Growler the lion with magic (up to 16) and Bree the centaur with ranged (up to 16).
 *
 * When the chamber empties all four heal to full and their stats are restored.
 *
 * The animations, graphics, projectile flights and sounds are the captured ones (rsprox), via
 * [GodWarsProjAnims] and [GodWarsSynths]. Guesses where the Wiki is silent: how often she casts
 * instead of punching (1 attack in 3). Not built: Starlight's habit of wandering at random while
 * she stands under Zilyana.
 */
class GodWarsSaradominScript
@Inject
constructor(
    private val attacks: NpcAttacks,
    private val random: GameRandom,
    private val rooms: GodRooms,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val zilyana = GodWarsRoster.byName.getValue(GodWarsNames.ZILYANA)
        onAiOpPlayer2(zilyana) { fightZilyana(it.target) }

        val starlight = GodWarsRoster.byName.getValue(GodWarsNames.STARLIGHT)
        onAiOpPlayer2(starlight) { bodyguard(it.target, STARLIGHT_MELEE) }
        val growler = GodWarsRoster.byName.getValue(GodWarsNames.GROWLER)
        onAiApPlayer2(growler) { bodyguard(it.target, GROWLER_MAGIC) }
        onAiOpPlayer2(growler) { bodyguard(it.target, GROWLER_MAGIC) }
        val bree = GodWarsRoster.byName.getValue(GodWarsNames.BREE)
        onAiApPlayer2(bree) { bodyguard(it.target, BREE_RANGED) }
        onAiOpPlayer2(bree) { bodyguard(it.target, BREE_RANGED) }

        for (name in GodWarsNames.saradominLeaders) {
            onAiTimer(GodWarsRoster.byName.getValue(name)) { healIfEmpty(npc) }
        }
    }

    private fun StandardNpcAccess.fightZilyana(target: Player) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        val magic = random.of(MAGIC_ODDS) == 0
        attacks.attack(npc, target, if (magic) ZILYANA_MAGIC else ZILYANA_MELEE)
        actionDelay = mapClock + ZILYANA_RATE
    }

    private fun StandardNpcAccess.bodyguard(target: Player, attack: NpcAttack) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        attacks.attack(npc, target, attack)
        actionDelay = mapClock + BODYGUARD_RATE
    }

    /** Heals [npc] to full when nobody is in the chamber any more. */
    fun healIfEmpty(npc: Npc) {
        val room = rooms.checkIn(npc, GodChambers.saradomin) ?: return
        if (rooms.isEmpty(room)) {
            npc.resetForEmptyRoom()
        }
    }

    companion object {
        const val ZILYANA_RATE = 2
        const val BODYGUARD_RATE = 5
        const val ZILYANA_MELEE_MAX = 27
        const val ZILYANA_MAGIC_MIN = 10
        const val ZILYANA_MAGIC_MAX = 20

        /** One attack in this many is her magic. */
        const val MAGIC_ODDS = 3

        val ZILYANA_MELEE =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                meleeType = MeleeAttackType.Crush,
                sound = GodWarsSynths.zilyana_melee,
                maxHit = ZILYANA_MELEE_MAX,
            )

        val ZILYANA_MAGIC =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = SaradominSeqs.zilyana_magic,
                sound = GodWarsSynths.zilyana_magic,
                impactSpot = SaradominSpotanims.zilyana_strike,
                minHit = ZILYANA_MAGIC_MIN,
                maxHit = ZILYANA_MAGIC_MAX,
            )

        val STARLIGHT_MELEE =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                meleeType = MeleeAttackType.Crush,
                sound = GodWarsSynths.starlight_attack,
                maxHit = 15,
            )

        val GROWLER_MAGIC =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = SaradominSeqs.growler_magic,
                sound = GodWarsSynths.growler_attack,
                castSpot = SaradominSpotanims.growler_cast,
                castHeight = 30,
                projectile = SaradominSpotanims.growler_travel,
                projanim = GodWarsProjAnims.growler_spell,
                maxHit = 16,
            )

        val BREE_RANGED =
            NpcAttack(
                style = NpcAttackStyle.Ranged,
                anim = SaradominSeqs.bree_shoot,
                sound = GodWarsSynths.bree_attack,
                castSpot = SaradominSpotanims.bree_cast,
                projectile = SaradominSpotanims.bree_travel,
                projanim = GodWarsProjAnims.bree_arrow,
                maxHit = 16,
            )
    }
}
