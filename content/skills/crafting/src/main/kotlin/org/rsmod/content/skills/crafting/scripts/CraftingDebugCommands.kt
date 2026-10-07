package org.rsmod.content.skills.crafting.scripts

import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.content.skills.crafting.configs.CraftingObjs
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Development helper: `::craftkit <kit>` puts what is needed to try one part of Crafting into the
 * player's inventory, since nothing on the isle gives the materials yet. Run it with no kit to list
 * them. An inventory only holds 28 things, so each kit is one part.
 */
internal class CraftingDebugCommands : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("craftkit") {
            modLevel = modlevels.admin
            desc = "Give the materials to test one part of Crafting"
            invalidArgs = "Use as ::craftkit <kit> (kits: ${KITS.keys.joinToString(", ")})"
            cheat {
                val name = args.getOrNull(0)?.lowercase()
                val kit = name?.let { KITS[it] }
                if (kit == null) {
                    player.mes("Kits: ${KITS.keys.joinToString(", ")}")
                    return@cheat
                }
                give(player, kit)
                player.mes("Gave the $name kit.")
            }
        }
    }

    private fun give(player: Player, kit: List<Pair<ObjType, Int>>) {
        for ((obj, count) in kit) {
            player.invAdd(player.inv, obj, count, strict = false)
        }
    }

    private companion object {
        private val o = CraftingObjs

        /** Each kit is a list of what to give and how many of it. */
        val KITS: Map<String, List<Pair<ObjType, Int>>> =
            linkedMapOf(
                "tools" to
                    listOf(
                        o.needle to 1,
                        o.thread to 100,
                        o.chisel to 1,
                        o.hammer to 1,
                        o.glassblowing_pipe to 1,
                        o.coins to 100_000,
                    ),
                "leather" to
                    listOf(
                        o.needle to 1,
                        o.thread to 100,
                        o.leather to 12,
                        o.hard_leather to 4,
                        o.steel_studs to 3,
                        o.kebbit_claws to 2,
                        o.snakeskin to 8,
                    ),
                "dhide" to
                    listOf(
                        o.needle to 1,
                        o.thread to 100,
                        o.green_dragon_leather to 6,
                        o.blue_dragon_leather to 6,
                        o.red_dragon_leather to 6,
                        o.black_dragon_leather to 6,
                    ),
                "shields" to
                    listOf(
                        o.hammer to 1,
                        o.oak_shield to 1,
                        o.maple_shield to 1,
                        o.yew_shield to 1,
                        o.magic_shield to 1,
                        o.redwood_shield to 1,
                        o.willow_shield to 1,
                        o.hard_leather to 2,
                        o.green_dragon_leather to 2,
                        o.blue_dragon_leather to 2,
                        o.red_dragon_leather to 2,
                        o.black_dragon_leather to 2,
                        o.snakeskin to 2,
                        o.bronze_nails to 15,
                        o.iron_nails to 15,
                        o.steel_nails to 15,
                        o.mithril_nails to 15,
                        o.adamant_nails to 15,
                        o.rune_nails to 15,
                    ),
                "gems" to
                    listOf(
                        o.chisel to 1,
                        o.uncut_opal to 2,
                        o.uncut_jade to 2,
                        o.uncut_red_topaz to 2,
                        o.uncut_sapphire to 2,
                        o.uncut_emerald to 2,
                        o.uncut_ruby to 2,
                        o.uncut_diamond to 2,
                        o.uncut_dragonstone to 2,
                        o.uncut_onyx to 2,
                        o.uncut_zenyte to 2,
                    ),
                "jewellery" to
                    listOf(
                        o.gold_bar to 6,
                        o.silver_bar to 4,
                        o.ring_mould to 1,
                        o.necklace_mould to 1,
                        o.amulet_mould to 1,
                        o.bracelet_mould to 1,
                        o.holy_mould to 1,
                        o.unholy_mould to 1,
                        o.sickle_mould to 1,
                        o.tiara_mould to 1,
                        o.bolt_mould to 1,
                        o.sapphire to 1,
                        o.emerald to 1,
                        o.ruby to 1,
                        o.diamond to 1,
                        o.dragonstone to 1,
                        o.onyx to 1,
                        o.zenyte to 1,
                        o.opal to 1,
                        o.jade to 1,
                        o.red_topaz to 1,
                        o.ball_of_wool to 1,
                        o.slayer_gem to 1,
                    ),
                "glass" to
                    listOf(
                        o.glassblowing_pipe to 1,
                        o.bucket_of_sand to 10,
                        o.soda_ash to 10,
                        o.oil_lantern_frame to 1,
                        o.cave_goblin_wire to 1,
                    ),
                "pottery" to
                    listOf(
                        o.clay to 12,
                        o.bucket_of_water to 6,
                        o.jug_of_water to 6,
                        o.soft_clay to 4,
                    ),
                "spin" to
                    listOf(
                        o.wool to 8,
                        o.flax to 8,
                        o.sinew to 4,
                        o.magic_roots to 2,
                        o.yak_hair to 2,
                        o.jute_fibre to 4,
                        o.willow_branch to 6,
                        o.ball_of_wool to 4,
                    ),
                "armour" to
                    listOf(
                        o.needle to 1,
                        o.thread to 100,
                        o.chisel to 1,
                        o.cured_yak_hide to 3,
                        o.xerician_fabric to 12,
                        o.bark to 4,
                        o.fine_cloth to 4,
                        o.mixed_hide_base to 2,
                        o.jaguar_fur to 1,
                        o.fox_fur to 3,
                        o.hueycoatl_hide to 5,
                        o.blamish_myre_shell_round to 1,
                        o.blamish_red_shell_pointed to 1,
                        o.fresh_crab_shell to 1,
                    ),
                "staves" to
                    listOf(
                        o.chisel to 1,
                        o.battlestaff to 4,
                        o.water_orb to 1,
                        o.earth_orb to 1,
                        o.fire_orb to 1,
                        o.air_orb to 1,
                        o.amethyst to 4,
                        o.hammer to 1,
                        o.clockwork to 2,
                        o.logs to 1,
                        o.oak_logs to 1,
                    ),
                "hides" to
                    listOf(
                        o.coins to 100_000,
                        o.cow_hide to 6,
                        o.snake_hide to 2,
                        o.swamp_snake_hide to 2,
                        o.green_dragonhide to 3,
                        o.blue_dragonhide to 3,
                        o.red_dragonhide to 3,
                        o.black_dragonhide to 3,
                    ),
            )
    }
}
