package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.combat.mastery.CombatMasteryEffects
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.queues
import org.rsmod.api.death.death
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What the engine needs to know about the Hunllef: it has no default combat (the fight in
 * [GauntletHunllef] makes every attack), a player's hit on it goes through its protection, it never
 * turns on a player who hits it, and its death wins the run and drops nothing (the reward is the
 * chest in the lobby).
 */
class GauntletHunllefScript
@Inject
constructor(
    private val gauntlet: Gauntlet,
    private val hunllef: GauntletHunllef,
    private val npcRepo: NpcRepository,
    private val seqTypes: SeqTypeList,
    private val players: PlayerList,
    private val masteries: CombatMasteryEffects,
) : PluginScript() {
    private val forms: Set<Int> by lazy {
        GauntletMode.entries
            .flatMap { mode -> HunllefStyle.entries.map { hunllef.form(mode, it).id } }
            .toSet()
    }

    override fun ScriptContext.startup() {
        for (mode in GauntletMode.entries) {
            for (style in HunllefStyle.entries) {
                val form = hunllef.form(mode, style)
                // The fight attacks by itself; these keep the engine's own melee away.
                onAiOpPlayer2(form) {}
                onAiApPlayer2(form) {}
                onNpcQueue(form, queues.com_retaliate_player) {}
                onNpcQueue(form, queues.death) { die() }
            }
        }
        // A hit of the style it protects against does nothing, unless the attacker has the combat
        // mastery that penetrates a protection prayer: that part of the damage goes through.
        onEvent<NpcHitEvents.ModifyAny> {
            if (npc.type.id !in forms) {
                return@onEvent
            }
            val run = gauntlet.runOfHunllef(npc) ?: return@onEvent
            if (hunllef.onHit(run, HunllefStyle.of(hit.type))) {
                val through = source?.let(masteries::prayerPenetration) ?: 0
                hit.damage = hit.damage * through / 100
            }
        }
    }

    private suspend fun StandardNpcAccess.die() {
        gauntlet.runOfHunllef(npc)?.let(gauntlet::hunllefDied)
        anim(gauntlet_seqs.hunllef_death_a)
        delay(seqTypes[gauntlet_seqs.hunllef_death_a])
        // Part a holds its last frame for a further 36 client ticks, which the seq's tick duration
        // leaves out; without this part b cuts the hold short.
        delay(1)
        death(npcRepo, seqTypes, players)
    }
}

/**
 * The Hunllef walks to within five tiles of its target and holds there (its forms are 5x5), and its
 * tornadoes are moved by the fight, never by the engine. Needs `packCache`.
 */
internal object GauntletHunllefEdits : NpcEditor() {
    init {
        val forms =
            listOf(
                gauntlet_npcs.hunllef_melee,
                gauntlet_npcs.hunllef_ranged,
                gauntlet_npcs.hunllef_magic,
                gauntlet_npcs.hunllef_melee_hm,
                gauntlet_npcs.hunllef_ranged_hm,
                gauntlet_npcs.hunllef_magic_hm,
            )
        for (type in forms) {
            edit(type) {
                maxRange = LEASH
                giveChase = true
                defaultMode = NpcMode.None
                attackRange = GauntletHunllef.ATTACK_DISTANCE
                // It does not flinch; it dies in two parts, the first played by `die`.
                param[params.defend_anim] = gauntlet_seqs.hunllef_ready
                param[params.death_anim] = gauntlet_seqs.hunllef_death_b
            }
        }
        for (type in listOf(gauntlet_npcs.tornado, gauntlet_npcs.tornado_hm)) {
            edit(type) { defaultMode = NpcMode.None }
        }
    }

    /** Further than any two tiles of the arena are apart. */
    private const val LEASH = 30
}
