package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.MagicSpellType
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.spells.events.SpellCast
import org.rsmod.content.areas.misc.isleofsouls.configs.IsleOfSoulsMagicTasks
import org.rsmod.content.skills.magic.utility.scripts.ArceuusUtilities
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Completes the league's Magic tasks. Almost all of them are about casting one spell, which is
 * found by the spell's name in the [SpellCast] event, so a new spell needs no new code to be
 * counted (only a task for it). The rest:
 * - kills with magic: a kill counts when the player cast a combat spell in the few ticks before it.
 *   The spell lands a couple of ticks after the cast, and the kill is given to whoever did the most
 *   damage, so this is a close fit rather than an exact one
 * - "Switch to the Lunar Spellbook": casting from the Lunar book, or logging in with it
 * - the 26 herb Degrime, from `ArceuusUtilities.HerbsCleaned`
 *
 * The tasks that need content the isle does not have are left out of the task list (the kill with
 * Inferior Demonbane, the teleport tablets, wizard robes, and the God capes).
 */
class LeagueMagicTasks @Inject constructor(private val tracker: LeagueTaskTracker) :
    PluginScript() {
    private val lastCombatCast = WeakHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        onEvent<SpellCast> { onCast(player, spell) }
        onEvent<NpcDeathEvents.Killed> { hero?.let { onKill(it) } }
        onEvent<ArceuusUtilities.HerbsCleaned> {
            if (count >= FULL_DEGRIME) {
                tracker.complete(player, TASK_DEGRIME_FULL)
            }
        }
        onPlayerLogin {
            if (player.vars[varbits.spellbook] == Spellbook.Lunars.varValue) {
                tracker.complete(player, TASK_LUNAR_SPELLBOOK)
            }
        }
    }

    private fun onCast(player: Player, spell: MagicSpell) {
        tracker.addProgress(
            player,
            IsleOfSoulsMagicTasks.taskId("casts_1000"),
            league_progress.magic_casts_1000,
            1000,
        )
        for (key in byName[spell.name].orEmpty()) {
            tracker.complete(player, IsleOfSoulsMagicTasks.taskId(key))
        }
        for (taskId in vanillaByName[spell.name].orEmpty()) {
            tracker.complete(player, taskId)
        }

        val name = spell.name
        if (spell.spellbook == Spellbook.Lunars) {
            tracker.complete(player, TASK_LUNAR_SPELLBOOK)
        }
        if (name.endsWith(" Blast") && name != "Iban Blast") {
            tracker.complete(player, TASK_BLAST)
        }
        if (name.endsWith(" Wave")) {
            tracker.complete(player, TASK_WAVE)
        }
        if (name.endsWith(" Surge")) {
            tracker.complete(player, TASK_SURGE)
        }
        if (name.startsWith("Resurrect ")) {
            tracker.complete(player, IsleOfSoulsMagicTasks.taskId("thrall"))
        }
        if (name.startsWith("Tele Group ")) {
            tracker.complete(player, IsleOfSoulsMagicTasks.taskId("tele_group"))
        }
        if (isTeleport(name) && spell.objReqs.any { it.obj.id == objs.law_rune.id }) {
            tracker.complete(player, TASK_LAW_TELEPORT)
        }
        if (name == "High Level Alchemy") {
            tracker.addProgress(
                player,
                IsleOfSoulsMagicTasks.taskId("high_alchemy_100"),
                league_progress.magic_high_alchemy_100,
                100,
            )
        }
        if (isAttackSpell(spell)) {
            lastCombatCast[player] = player.currentMapClock
        }
    }

    private fun onKill(player: Player) {
        val cast = lastCombatCast[player] ?: return
        if (player.currentMapClock - cast > KILL_WINDOW) {
            return
        }
        tracker.addProgress(
            player,
            IsleOfSoulsMagicTasks.taskId("kills_25"),
            league_progress.magic_kills_25,
            25,
        )
        tracker.addProgress(
            player,
            IsleOfSoulsMagicTasks.taskId("kills_250"),
            league_progress.magic_kills_250,
            250,
        )
    }

    /** The cache types many Lunar utility spells as combat spells, so those are left out. */
    private fun isAttackSpell(spell: MagicSpell): Boolean =
        spell.type == MagicSpellType.Combat && spell.spellbook != Spellbook.Lunars

    private fun isTeleport(name: String): Boolean =
        name.contains("Teleport") && !name.contains("Teleother") || name.startsWith("Tele Group ")

    private companion object {
        const val TASK_LUNAR_SPELLBOOK = 565
        const val TASK_LAW_TELEPORT = 432
        const val TASK_BLAST = 434
        const val TASK_WAVE = 437
        const val TASK_SURGE = 438
        const val TASK_DEGRIME_FULL = 1124

        const val FULL_DEGRIME = 26

        /** Ticks after the last combat spell in which a kill still counts as its. */
        const val KILL_WINDOW = 10

        /** Spell name to the isle's own tasks (by key) it completes. */
        val byName: Map<String, List<String>> =
            mapOf(
                "Wind Strike" to listOf("wind_strike"),
                "Low Level Alchemy" to listOf("low_alchemy"),
                "Bones to Bananas" to listOf("bones_to_bananas"),
                "Varrock Teleport" to listOf("varrock_teleport"),
                "Lvl-1 Enchant" to listOf("enchant_sapphire"),
                "Telekinetic Grab" to listOf("telekinetic_grab"),
                "Superheat Item" to listOf("superheat"),
                "Smoke Rush" to listOf("smoke_rush"),
                "Mark of Darkness" to listOf("mark_of_darkness"),
                "Charge" to listOf("charge"),
                "Magic Imbue" to listOf("magic_imbue"),
                "Death Charge" to listOf("death_charge"),
                "Vile Vigour" to listOf("vile_vigour"),
                "Demonic Offering" to listOf("demonic_offering"),
                "Blood Barrage" to listOf("blood_barrage"),
                "Vengeance" to listOf("vengeance"),
                "Plank Make" to listOf("plank_make"),
                "Sinister Offering" to listOf("sinister_offering"),
            )

        /** Spell name to the vanilla task ids it completes. */
        val vanillaByName: Map<String, List<Int>> =
            mapOf(
                "High Level Alchemy" to listOf(435),
                "Ice Rush" to listOf(868),
                "Ice Burst" to listOf(866),
                "Ice Blitz" to listOf(869),
                "Ice Barrage" to listOf(872),
                "Saradomin Strike" to listOf(1023),
                "Claws of Guthix" to listOf(1024),
                "Flames of Zamorak" to listOf(1025),
                "Civitas illa Fortis Teleport" to listOf(1654),
                "Kourend Castle Teleport" to listOf(178),
                "Moonclan Teleport" to listOf(566),
                "Spellbook Swap" to listOf(568),
            )
    }
}
