package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.type.seq.SeqType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object KalphiteNpcTypes : NpcReferences() {
    val soldier = find("kalphite_soldier")
    val soldier_cave = find("kalphite_soldier_strongholdcave")
    val guardian = find("kalphite_lord")
    val guardian_cave = find("kalphite_lord_strongholdcave")
    val guardian_chamber = find("kalphite_lord_chamber")
}

object KalphiteMeleeSeqs : SeqReferences() {
    /** The soldier's pincers. */
    val soldier_claws = find("kalphite_update_claw_attack")

    /** The guardian's claws. */
    val guardian_claws = find("kalphite_update_claw_lord_attack")
}

/**
 * Kalphite soldiers and guardians poison (OSRS Wiki, "Kalphite Soldier", "Kalphite Guardian" and
 * "Poison"): a soldier's bite starts a poison at 4, a guardian's at 6. They fight with the normal
 * melee otherwise (their stats, accuracy and max hit are the cache's), but with their own attack
 * animations, which the cache does not name for them. A hit that does damage poisons one time in
 * [POISON_ODDS] (a guess; the Wiki gives no chance, and this is K'ril's), and an antipoison stops
 * it ([PlayerPoison]). Workers and the Queen do not poison.
 *
 * The poison timer itself is run by `GodWarsZamorakScript`.
 */
class KalphitePoisonScript
@Inject
constructor(
    private val attacks: NpcAttacks,
    private val poison: PlayerPoison,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in listOf(KalphiteNpcTypes.soldier, KalphiteNpcTypes.soldier_cave)) {
            onAiOpPlayer2(type) { bite(it.target, KalphiteMeleeSeqs.soldier_claws, SOLDIER_POISON) }
        }
        val guardians =
            listOf(
                KalphiteNpcTypes.guardian,
                KalphiteNpcTypes.guardian_cave,
                KalphiteNpcTypes.guardian_chamber,
            )
        for (type in guardians) {
            onAiOpPlayer2(type) {
                bite(it.target, KalphiteMeleeSeqs.guardian_claws, GUARDIAN_POISON)
            }
        }
    }

    private fun StandardNpcAccess.bite(target: Player, anim: SeqType, damage: Int) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        val onImpact = NpcImpactListener { victim, hit ->
            if (hit.damage > 0 && random.of(POISON_ODDS) == 0) {
                poison.poison(victim, damage)
            }
        }
        attacks.attack(
            npc,
            target,
            NpcAttack(style = NpcAttackStyle.Melee, anim = anim, meleeType = MeleeAttackType.Crush),
            onImpact = onImpact,
        )
        actionDelay = mapClock + npc.attackRate()
    }

    companion object {
        /** The Wiki's poison severities: soldier 4, guardian 6. */
        const val SOLDIER_POISON = 4
        const val GUARDIAN_POISON = 6

        /** One damaging hit in this many poisons. */
        const val POISON_ODDS = 4
    }
}
