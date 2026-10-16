package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.content.skills.cooking.scripts.Cooking
import org.rsmod.content.skills.firemaking.scripts.Firemaking
import org.rsmod.content.skills.fishing.scripts.Fishing
import org.rsmod.content.skills.fletching.scripts.Fletching
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.smithing.scripts.Smelting
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting
import org.rsmod.events.EventBus
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gathering effects of the tier 1 relics:
 * - Power Miner: a failed mining roll gets a 50% second chance, ore goes straight to the bank, and
 *   a rock lasts for [ORES_PER_ROCK] ores before it can deplete. With auto-smelt on, each ore is
 *   banked as a bar (see [Smelting.autoSmelt]) with Smithing experience, and with auto-gem cut on a
 *   gem found while mining is cut for Crafting experience, both with no level requirement.
 * - Lumberjack: a failed woodcutting roll gets a 50% second chance and logs go to the bank. With
 *   auto-burn on the logs are burnt for Firemaking experience instead, and with auto-fletch on they
 *   are fletched into arrow shafts (see [Fletching.autoShafts]: better logs give more shafts and
 *   more experience), both with no level requirement. Logs that make no shafts, such as teak, are
 *   left alone.
 * - Animal Wrangler: a failed fishing roll gets a 50% second chance, fish go straight to the bank
 *   (apart from stackable karambwanji, which is bait), fish are cooked for Cooking experience with
 *   no level requirement while auto-cook is on, and food never burns. The Echo harpoon's faster
 *   fishing and tool substitution live in the fishing skill itself.
 * - Friendly Forager: gathering also finds a grimy herb (see [LeagueForager]).
 *
 * Each tool's "Toggle" option switches these effects (see [LeagueRelicToggleScript]), and auto-bank
 * can be turned off for each one. The Hunter effects of Animal Wrangler and Friendly Forager are in
 * [LeagueHunterRelics].
 */
class LeagueGatheringRelics
@Inject
constructor(
    private val tools: LeagueRelicTools,
    private val forager: LeagueForager,
    private val random: GameRandom,
    private val protectedAccess: ProtectedAccessLauncher,
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
    private val eventBus: EventBus,
    private val toggles: RelicToggles,
) : PluginScript() {
    /** Ores mined from each rock since it last depleted, for rocks mined with Power Miner. */
    private val oresMined = HashMap<CoordGrid, Int>()

    override fun ScriptContext.startup() {
        onPlayerLogin { protectedAccess.launch(player) { with(tools) { grantMissing() } } }

        onEvent<Mining.BankOreCheck> {
            bank =
                tools.isActive(player, RelicItem.PowerMiner) &&
                    toggles.isOn(player, RelicToggle.MiningBank)
        }

        onEvent<Mining.MineRoll> {
            if (!tools.isActive(player, RelicItem.PowerMiner)) {
                return@onEvent
            }
            if (!success) {
                success = random.randomDouble() < SECOND_CHANCE
            }
            bank = toggles.isOn(player, RelicToggle.MiningBank)
            if (success) {
                autoSmelt(this)
                autoCutGem(this)
            }
        }

        onEvent<Mining.MineOre> {
            forager.onGather(player)
            if (!tools.isActive(player, RelicItem.PowerMiner)) {
                return@onEvent
            }
            val mined = (oresMined[rock.coords] ?: 0) + 1
            if (mined < ORES_PER_ROCK) {
                oresMined[rock.coords] = mined
                keepRock = true
            } else {
                oresMined.remove(rock.coords)
            }
        }

        onEvent<Woodcutting.CutLogs> { forager.onGather(player) }

        onEvent<Fishing.CatchRoll> {
            if (!tools.isActive(player, RelicItem.AnimalWrangler)) {
                return@onEvent
            }
            if (!success) {
                success = random.randomDouble() < SECOND_CHANCE
            }
            if (!success) {
                return@onEvent
            }

            // Stackable fish (raw karambwanji) stay in the inventory, as they are used as bait.
            bank = toggles.isOn(player, RelicToggle.FishingBank) && !objTypes[fish].isStackable
            if (!toggles.isOn(player, RelicToggle.AutoCook)) {
                return@onEvent
            }
            val recipe = Cooking.recipe(fish) ?: return@onEvent
            product = recipe.cooked
            player.statAdvance(stats.cooking, recipe.xp * xpMods.get(player, stats.cooking))
            eventBus.publish(Cooking.Cooked(player, fish, recipe.cooked))
        }

        onEvent<Fishing.FishCaught> { forager.onGather(player) }

        onEvent<Cooking.CookRoll> {
            if (tools.isActive(player, RelicItem.AnimalWrangler)) {
                success = true
            }
        }

        onEvent<Woodcutting.CutRoll> {
            if (!tools.isActive(player, RelicItem.Lumberjack)) {
                return@onEvent
            }
            if (!success) {
                success = random.randomDouble() < SECOND_CHANCE
            }
            bank = toggles.isOn(player, RelicToggle.WoodcuttingBank)
            if (success) {
                processLogs(this)
            }
        }
    }

    /** Swaps the ore of a successful roll for its bar when the player has auto-smelt on. */
    private fun autoSmelt(roll: Mining.MineRoll) {
        val player = roll.player
        if (!toggles.isOn(player, RelicToggle.AutoSmelt)) {
            return
        }
        val smelt = roll.ore?.let(Smelting::autoSmelt) ?: return
        roll.product = smelt.bar
        player.statAdvance(stats.smithing, smelt.xp * xpMods.get(player, stats.smithing))
        eventBus.publish(Smelting.BarSmelted(player, smelt.bar))
    }

    /** Cuts the gem found with a successful roll when the player has auto-gem cut on. */
    private fun autoCutGem(roll: Mining.MineRoll) {
        val player = roll.player
        if (!toggles.isOn(player, RelicToggle.AutoGemCut)) {
            return
        }
        val cut = roll.gem?.let(LeagueGemCutting::cut) ?: return
        roll.gemProduct = cut.gem
        player.statAdvance(stats.crafting, cut.xp * xpMods.get(player, stats.crafting))
    }

    /** Burns or fletches the logs of a successful roll, whichever of the two is switched on. */
    private fun processLogs(roll: Woodcutting.CutRoll) {
        val player = roll.player
        val logs = roll.logs ?: return
        val name = objTypes[logs].name.lowercase()
        if (toggles.isOn(player, RelicToggle.AutoBurn)) {
            val xp = Firemaking.logXp(logs) ?: return
            roll.consume = true
            player.spam("You burn the $name.")
            player.statAdvance(stats.firemaking, xp * xpMods.get(player, stats.firemaking))
            eventBus.publish(Firemaking.LogLit(player, logs, player.coords))
        } else if (toggles.isOn(player, RelicToggle.AutoFletch)) {
            val shafts = Fletching.autoShafts(logs) ?: return
            roll.product = isle_objs.arrow_shaft
            roll.productCount = shafts.count
            player.spam("You fletch the $name into arrow shafts.")
            player.statAdvance(stats.fletching, shafts.xp * xpMods.get(player, stats.fletching))
            eventBus.publish(Fletching.ItemFletched(player, isle_objs.arrow_shaft, shafts.count))
        }
    }

    private companion object {
        const val SECOND_CHANCE = 0.5
        const val ORES_PER_ROCK = 4
    }
}
