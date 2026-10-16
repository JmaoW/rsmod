package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.script.onEvent
import org.rsmod.content.skills.hunter.configs.TrapKind
import org.rsmod.content.skills.hunter.scripts.Birdhouses
import org.rsmod.content.skills.hunter.scripts.Falconer
import org.rsmod.content.skills.hunter.scripts.HunterCatching
import org.rsmod.content.skills.hunter.scripts.HunterTraps
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Hunter side of two relics.
 *
 * Animal Wrangler (tier 1), which works whether or not the Echo harpoon is carried: traps never
 * fail; box traps look for a creature more often and show the catch sooner, and a chinchompa catch
 * gives twice the chinchompas and twice the experience; impling jars never break when they are
 * opened.
 *
 * Friendly Forager (tier 2): collecting from a trap or a pit, catching a butterfly or an impling,
 * falconry and emptying a birdhouse each find a grimy herb (see [LeagueForager]).
 *
 * The Hunter skill asks each time through its events, so it knows nothing of either relic.
 */
class LeagueHunterRelics
@Inject
constructor(private val tools: LeagueRelicTools, private val forager: LeagueForager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<HunterTraps.Roll> {
            if (tools.isChosen(player, RelicItem.AnimalWrangler)) {
                success = true
            }
        }
        onEvent<HunterTraps.Modifiers> {
            if (kind == TrapKind.Box && tools.isChosen(player, RelicItem.AnimalWrangler)) {
                scanTicks = FAST_SCAN_TICKS
                settleTicks = FAST_SETTLE_TICKS
            }
        }
        onEvent<HunterTraps.Collecting> {
            if (
                creature.key.endsWith("chinchompa") &&
                    tools.isChosen(player, RelicItem.AnimalWrangler)
            ) {
                catches *= CHINCHOMPA_MULTIPLIER
            }
        }
        onEvent<HunterCatching.Opening> {
            if (tools.isChosen(player, RelicItem.AnimalWrangler)) {
                breaks = false
            }
        }

        onEvent<HunterTraps.Trapped> { forager.onGather(player) }
        onEvent<HunterCatching.Caught> { forager.onGather(player) }
        onEvent<Falconer.Hunted> { forager.onGather(player) }
        onEvent<Birdhouses.Collected> { forager.onGather(player) }
    }

    companion object {
        /** How often, in ticks, a box trap looks for a creature with Animal Wrangler. */
        const val FAST_SCAN_TICKS = 2

        /** How long, in ticks, a box trap shows a creature going in with Animal Wrangler. */
        const val FAST_SETTLE_TICKS = 1

        /** The multiple a chinchompa catch (and its experience) is raised by. */
        const val CHINCHOMPA_MULTIPLIER = 2
    }
}
