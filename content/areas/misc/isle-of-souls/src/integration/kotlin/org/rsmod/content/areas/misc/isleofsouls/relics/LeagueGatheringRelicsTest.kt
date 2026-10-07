package org.rsmod.content.areas.misc.isleofsouls.relics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.invs
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.inv.WornOpScript
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.content.skills.cooking.scripts.Cooking
import org.rsmod.content.skills.fishing.scripts.Fishing
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

internal object WornTestComponents : ComponentReferences() {
    val weapon_slot = find("wornitems:slot3")
}

class LeagueGatheringRelicsTest {
    private val rock = loc(CoordGrid(0, 50, 50, 0, 0))
    private val otherRock = loc(CoordGrid(0, 50, 51, 0, 0))

    private fun loc(coords: CoordGrid) =
        BoundLocInfo(
            coords = coords,
            entity = LocEntity(1, LocShape.CentrepieceStraight.id, LocAngle.West.id),
            layer = 0,
            width = 1,
            length = 1,
            forceApproachFlags = 0,
        )

    private fun Player.choose(relic: RelicItem) {
        VarPlayerIntMapSetter.set(this, RelicVarBits.selection[relic.tier], relic.option)
    }

    private fun Player.give(relic: RelicItem) {
        inv[0] = InvObj(relic.item)
    }

    @Test
    fun GameTestState.`power miner retries failed rolls and banks ore`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.PowerMiner)
            player.give(RelicItem.PowerMiner)

            var successes = 0
            repeat(400) {
                val roll = Mining.MineRoll(player, rock, success = false)
                eventBus.publish(roll)
                assertTrue(roll.bank)
                if (roll.success) successes++
            }
            // A separate 50% chance: well away from never or always across 400 rolls.
            assertTrue(successes in 140..260, "successes=$successes")

            val success = Mining.MineRoll(player, rock, success = true)
            eventBus.publish(success)
            assertTrue(success.success)
        }

    @Test
    fun GameTestState.`power miner needs the relic and its pickaxe`() =
        runGameTest(LeagueGatheringRelics::class) {
            // Pickaxe without the relic.
            player.give(RelicItem.PowerMiner)
            val noRelic = Mining.MineRoll(player, rock, success = false)
            eventBus.publish(noRelic)
            assertFalse(noRelic.bank)
            repeat(100) {
                val roll = Mining.MineRoll(player, rock, success = false)
                eventBus.publish(roll)
                assertFalse(roll.success)
            }

            // Relic without the pickaxe.
            player.inv[0] = null
            player.choose(RelicItem.PowerMiner)
            val noTool = Mining.MineRoll(player, rock, success = false)
            eventBus.publish(noTool)
            assertFalse(noTool.bank)
        }

    @Test
    fun GameTestState.`power miner banks ore so a full inventory does not stop mining`() =
        runGameTest(LeagueGatheringRelics::class) {
            fun banks(): Boolean {
                val check = Mining.BankOreCheck(player)
                eventBus.publish(check)
                return check.bank
            }

            // Pickaxe without the relic.
            player.give(RelicItem.PowerMiner)
            assertFalse(banks())

            player.choose(RelicItem.PowerMiner)
            assertTrue(banks())

            // Auto-bank turned off: mining needs inventory space again.
            player.turn(RelicToggle.MiningBank, false)
            assertFalse(banks())
        }

    @Test
    fun GameTestState.`rocks last four ores with power miner`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.PowerMiner)
            player.give(RelicItem.PowerMiner)
            val ore = objTypes[objs.copper_ore]

            repeat(2) {
                // Two full cycles: three ores keep the rock, the fourth lets it deplete.
                repeat(3) {
                    val mined = Mining.MineOre(player, rock, ore)
                    eventBus.publish(mined)
                    assertTrue(mined.keepRock)
                }
                val fourth = Mining.MineOre(player, rock, ore)
                eventBus.publish(fourth)
                assertFalse(fourth.keepRock)
            }

            // Counts are tracked per rock.
            val other = Mining.MineOre(player, otherRock, ore)
            eventBus.publish(other)
            assertTrue(other.keepRock)
        }

    private val toggles = RelicToggles()

    private fun Player.isOn(toggle: RelicToggle) = toggles.isOn(this, toggle)

    private fun Player.turn(toggle: RelicToggle, on: Boolean) = toggles.set(this, toggle, on)

    /** Clicks "Toggle" on the Echo tool in the first inventory slot. */
    private fun GameTestScope.clickToggle(tool: org.rsmod.game.type.obj.ObjType) {
        val obj = objTypes[tool]
        eventBus.publish(IfOverlayButton(player, components.inv_items, 0, obj, IfButtonOp.Op4))
    }

    /** Clicks the equipped weapon's "Toggle" (its `wear_op1`, so `Op2`) in the equipment tab. */
    private fun GameTestScope.clickWornToggle(tool: org.rsmod.game.type.obj.ObjType) {
        val obj = objTypes[tool]
        eventBus.publish(
            IfOverlayButton(player, WornTestComponents.weapon_slot, 0, obj, IfButtonOp.Op2)
        )
    }

    /** Picks the nth (1-based) option of the chat menu that is open. */
    private fun GameTestScope.pick(option: Int) {
        player.resumePauseButton(components.chatmenu_pbutton, option)
        advance()
    }

    private fun GameTestScope.smeltRoll(ore: org.rsmod.game.type.obj.ObjType): Mining.MineRoll {
        val roll = Mining.MineRoll(player, rock, success = true)
        roll.ore = objTypes[ore]
        eventBus.publish(roll)
        return roll
    }

    private fun GameTestScope.cutRoll(logs: org.rsmod.game.type.obj.ObjType): Woodcutting.CutRoll {
        val roll = Woodcutting.CutRoll(player, rock, success = true)
        roll.logs = objTypes[logs]
        eventBus.publish(roll)
        return roll
    }

    @Test
    fun GameTestState.`the echo pickaxe menu toggles each switch`() =
        runGameTest(
            LeagueGatheringRelics::class,
            LeagueRelicToggleScript::class,
            HeldOpScript::class,
        ) {
            player.choose(RelicItem.PowerMiner)
            player.give(RelicItem.PowerMiner)
            assertFalse(player.isOn(RelicToggle.AutoSmelt))
            assertFalse(player.isOn(RelicToggle.AutoGemCut))
            assertTrue(player.isOn(RelicToggle.MiningBank))

            clickToggle(objs.league_trailblazer_pickaxe)
            advance()
            pick(1)
            assertTrue(player.isOn(RelicToggle.AutoSmelt))
            assertMessageSent("Auto-smelt is now <col=1a6b1a>on</col>.")

            clickToggle(objs.league_trailblazer_pickaxe)
            advance()
            pick(2)
            assertTrue(player.isOn(RelicToggle.AutoGemCut))
            assertMessageSent("Auto-gem cut is now <col=1a6b1a>on</col>.")

            clickToggle(objs.league_trailblazer_pickaxe)
            advance()
            pick(3)
            assertFalse(player.isOn(RelicToggle.MiningBank))
            assertMessageSent("Auto-bank is now <col=8b1a1a>off</col>.")

            clickToggle(objs.league_trailblazer_pickaxe)
            advance()
            pick(1)
            assertFalse(player.isOn(RelicToggle.AutoSmelt))
            assertMessageSent("Auto-smelt is now <col=8b1a1a>off</col>.")
        }

    @Test
    fun GameTestState.`the echo tools toggle while they are equipped`() =
        runGameTest(
            LeagueGatheringRelics::class,
            LeagueRelicToggleScript::class,
            WornOpScript::class,
        ) {
            player.choose(RelicItem.PowerMiner)
            player.worn[Wearpos.RightHand.slot] = InvObj(RelicItem.PowerMiner.item)
            assertFalse(player.isOn(RelicToggle.AutoSmelt))

            clickWornToggle(objs.league_trailblazer_pickaxe)
            advance()
            pick(1)
            assertTrue(player.isOn(RelicToggle.AutoSmelt))
            assertMessageSent("Auto-smelt is now <col=1a6b1a>on</col>.")

            clickWornToggle(objs.league_trailblazer_pickaxe)
            advance()
            pick(3)
            assertFalse(player.isOn(RelicToggle.MiningBank))
        }

    @Test
    fun GameTestState.`burning and fletching cannot both be on`() =
        runGameTest(
            LeagueGatheringRelics::class,
            LeagueRelicToggleScript::class,
            HeldOpScript::class,
        ) {
            player.choose(RelicItem.Lumberjack)
            player.give(RelicItem.Lumberjack)

            clickToggle(objs.league_trailblazer_axe)
            advance()
            pick(1)
            assertTrue(player.isOn(RelicToggle.AutoBurn))

            clickToggle(objs.league_trailblazer_axe)
            advance()
            pick(2)
            assertTrue(player.isOn(RelicToggle.AutoFletch))
            assertFalse(player.isOn(RelicToggle.AutoBurn))
            assertMessagesSent(
                "Auto-fletch is now <col=1a6b1a>on</col>.",
                "Auto-burn is now <col=8b1a1a>off</col>.",
            )
        }

    @Test
    fun GameTestState.`the echo harpoon menu toggles cooking and banking`() =
        runGameTest(
            LeagueGatheringRelics::class,
            LeagueRelicToggleScript::class,
            HeldOpScript::class,
        ) {
            player.choose(RelicItem.AnimalWrangler)
            player.give(RelicItem.AnimalWrangler)
            assertTrue(player.isOn(RelicToggle.AutoCook))

            clickToggle(objs.league_trailblazer_harpoon)
            advance()
            pick(1)
            assertFalse(player.isOn(RelicToggle.AutoCook))
            assertMessageSent("Auto-cook is now <col=8b1a1a>off</col>.")

            clickToggle(objs.league_trailblazer_harpoon)
            advance()
            pick(2)
            assertFalse(player.isOn(RelicToggle.FishingBank))
        }

    @OptIn(InternalApi::class)
    @Test
    fun GameTestState.`auto-smelt turns each ore into its bar`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.PowerMiner)
            player.give(RelicItem.PowerMiner)
            VarPlayerIntMapSetter.set(player, RelicToggleVars.auto_smelt, 1)

            val expected =
                listOf(
                    objs.copper_ore to isle_objs.bronze_bar,
                    objs.tin_ore to isle_objs.bronze_bar,
                    objs.iron_ore to isle_objs.iron_bar,
                    objs.coal to isle_objs.steel_bar,
                    objs.mithril_ore to isle_objs.mithril_bar,
                )
            for ((ore, bar) in expected) {
                val before = player.statMap.getXP(stats.smithing)
                val roll = smeltRoll(ore)
                assertEquals(bar.id, roll.product?.id, "bar for ${ore.id}")
                assertTrue(player.statMap.getXP(stats.smithing) > before, "xp for ${ore.id}")
                assertTrue(roll.bank)
            }
        }

    @Test
    fun GameTestState.`auto-smelt leaves other things alone`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.PowerMiner)
            player.give(RelicItem.PowerMiner)

            // Off by default.
            assertEquals(null, smeltRoll(objs.copper_ore).product)

            // Rocks that give no ore a furnace could use, and failed rolls, give nothing.
            VarPlayerIntMapSetter.set(player, RelicToggleVars.auto_smelt, 1)
            assertEquals(null, smeltRoll(objs.clay).product)
            val failed = Mining.MineRoll(player, rock, success = false)
            failed.ore = objTypes[objs.copper_ore]
            random.next = 100 // Fail the relic's second chance too.
            eventBus.publish(failed)
            assertFalse(failed.success)
            assertEquals(null, failed.product)
        }

    @Test
    fun GameTestState.`auto-smelt needs power miner and its pickaxe`() =
        runGameTest(LeagueGatheringRelics::class) {
            VarPlayerIntMapSetter.set(player, RelicToggleVars.auto_smelt, 1)

            // Pickaxe without the relic.
            player.give(RelicItem.PowerMiner)
            assertEquals(null, smeltRoll(objs.copper_ore).product)

            // Relic without the pickaxe.
            player.inv[0] = null
            player.choose(RelicItem.PowerMiner)
            assertEquals(null, smeltRoll(objs.copper_ore).product)
        }

    @OptIn(InternalApi::class)
    @Test
    fun GameTestState.`auto-gem cut cuts a mined gem for crafting xp`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.PowerMiner)
            player.give(RelicItem.PowerMiner)

            fun roll(): Mining.MineRoll {
                val roll = Mining.MineRoll(player, rock, success = true)
                roll.ore = objTypes[objs.copper_ore]
                roll.gem = objTypes[isle_objs.uncut_ruby]
                eventBus.publish(roll)
                return roll
            }

            // Off by default: the uncut gem is kept.
            assertEquals(null, roll().gemProduct)
            assertEquals(0, player.statMap.getXP(stats.crafting))

            player.turn(RelicToggle.AutoGemCut, true)
            val on = roll()
            assertEquals(isle_objs.ruby.id, on.gemProduct?.id)
            assertTrue(player.statMap.getXP(stats.crafting) > 0)
        }

    @Test
    fun GameTestState.`auto-bank can be turned off for mining`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.PowerMiner)
            player.give(RelicItem.PowerMiner)
            assertTrue(smeltRoll(objs.copper_ore).bank)

            player.turn(RelicToggle.MiningBank, false)
            assertFalse(smeltRoll(objs.copper_ore).bank)
        }

    @OptIn(InternalApi::class)
    @Test
    fun GameTestState.`auto-burn burns logs for firemaking xp`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.Lumberjack)
            player.give(RelicItem.Lumberjack)

            // Off by default: logs are only banked.
            val plain = cutRoll(objs.oak_logs)
            assertTrue(plain.bank)
            assertFalse(plain.consume)
            assertEquals(0, player.statMap.getXP(stats.firemaking))

            player.turn(RelicToggle.AutoBurn, true)
            val burnt = cutRoll(objs.oak_logs)
            assertTrue(burnt.consume)
            assertTrue(player.statMap.getXP(stats.firemaking) > 0)
        }

    @OptIn(InternalApi::class)
    @Test
    fun GameTestState.`auto-fletch makes more arrow shafts and xp from better logs`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.Lumberjack)
            player.give(RelicItem.Lumberjack)
            player.turn(RelicToggle.AutoFletch, true)

            val expected =
                listOf(
                    objs.logs to 15,
                    objs.oak_logs to 30,
                    objs.willow_logs to 45,
                    objs.maple_logs to 60,
                    objs.yew_logs to 75,
                    objs.magic_logs to 90,
                    objs.redwood_logs to 105,
                )
            var lastXp = 0
            for ((logs, shafts) in expected) {
                val roll = cutRoll(logs)
                assertEquals(isle_objs.arrow_shaft.id, roll.product?.id)
                assertEquals(shafts, roll.productCount)
                assertFalse(roll.consume)
                val xp = player.statMap.getXP(stats.fletching)
                assertTrue(xp > lastXp)
                lastXp = xp
            }
        }

    @OptIn(InternalApi::class)
    @Test
    fun GameTestState.`auto-fletch leaves logs that make no shafts alone`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.Lumberjack)
            player.give(RelicItem.Lumberjack)
            player.turn(RelicToggle.AutoFletch, true)

            val roll = cutRoll(objs.teak_logs)
            assertEquals(null, roll.product)
            assertEquals(0, player.statMap.getXP(stats.fletching))
        }

    @Test
    fun GameTestState.`auto-bank can be turned off for woodcutting`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.Lumberjack)
            player.give(RelicItem.Lumberjack)
            player.turn(RelicToggle.WoodcuttingBank, false)
            assertFalse(cutRoll(objs.logs).bank)
        }

    @Test
    fun GameTestState.`auto-cook and auto-bank can be turned off for fishing`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.AnimalWrangler)
            player.give(RelicItem.AnimalWrangler)
            val shark = objTypes[isle_objs.raw_shark]

            player.turn(RelicToggle.AutoCook, false)
            val uncooked = Fishing.CatchRoll(player, spot(), shark, success = true)
            eventBus.publish(uncooked)
            assertEquals(shark.id, uncooked.product.id)
            assertTrue(uncooked.bank)

            player.turn(RelicToggle.FishingBank, false)
            val kept = Fishing.CatchRoll(player, spot(), shark, success = true)
            eventBus.publish(kept)
            assertFalse(kept.bank)
        }

    @Test
    fun GameTestState.`lumberjack retries failed rolls and banks logs`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.Lumberjack)
            player.give(RelicItem.Lumberjack)

            var successes = 0
            repeat(400) {
                val roll = Woodcutting.CutRoll(player, rock, success = false)
                eventBus.publish(roll)
                assertTrue(roll.bank)
                if (roll.success) successes++
            }
            assertTrue(successes in 140..260, "successes=$successes")
        }

    @Test
    fun GameTestState.`lumberjack does not affect mining and vice versa`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.Lumberjack)
            player.give(RelicItem.Lumberjack)
            val roll = Mining.MineRoll(player, rock, success = false)
            eventBus.publish(roll)
            assertFalse(roll.bank)
            assertEquals(false, roll.success)
        }

    private val herbs =
        listOf(
            isle_objs.grimy_guam,
            isle_objs.grimy_marrentill,
            isle_objs.grimy_tarromin,
            isle_objs.grimy_harralander,
            isle_objs.grimy_ranarr,
            isle_objs.grimy_toadflax,
            isle_objs.grimy_irit,
            isle_objs.grimy_avantoe,
            isle_objs.grimy_kwuarm,
            isle_objs.grimy_snapdragon,
            isle_objs.grimy_cadantine,
            isle_objs.grimy_lantadyme,
            isle_objs.grimy_dwarf_weed,
            isle_objs.grimy_torstol,
        )

    private fun GameTestState.bank(player: Player) =
        player.invMap.getOrPut(cacheTypes.invs[invs.bank])

    /** Herbs in [bank] by obj id and count. */
    private fun GameTestScope.bankedHerbs(bank: Inventory): Map<Int, Int> =
        herbs.associate { it.id to bank.count(objTypes[it]) }.filterValues { it > 0 }

    @Test
    fun GameTestState.`friendly forager finds herbs the herblore level can clean`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.FriendlyForager)
            player.give(RelicItem.FriendlyForager)
            val ore = objTypes[objs.copper_ore]

            // The herbs give Herblore xp, so stop the level rising while counting what is found.
            player.xpRate = 0.0
            repeat(200) { eventBus.publish(Mining.MineOre(player, rock, ore)) }

            // Level 1 Herblore plus 25 can clean herbs up to level 26: guam through ranarr.
            val found = bankedHerbs(bank(player))
            assertEquals(200, found.values.sum())
            val allowed = herbs.take(5).map { it.id }.toSet()
            assertTrue(allowed.containsAll(found.keys), "found=$found")
            assertTrue(found.size > 1, "found=$found")
            assertEquals(0, player.statMap.getXP(stats.herblore))

            player.xpRate = 1.0
            eventBus.publish(Mining.MineOre(player, rock, ore))
            assertTrue(player.statMap.getXP(stats.herblore) > 0)
        }

    @Test
    fun GameTestState.`friendly forager also finds herbs while woodcutting at higher levels`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.FriendlyForager)
            player.give(RelicItem.FriendlyForager)
            player.stats[stats.herblore] = 99

            repeat(300) { eventBus.publish(Woodcutting.CutLogs(player, rock, objTypes[objs.logs])) }

            val found = bankedHerbs(bank(player))
            assertEquals(300, found.values.sum())
            assertTrue(found.size > 5, "found=$found")
        }

    @Test
    fun GameTestState.`friendly forager needs the relic and its pouch`() =
        runGameTest(LeagueGatheringRelics::class) {
            val ore = objTypes[objs.copper_ore]

            // Pouch without the relic.
            player.give(RelicItem.FriendlyForager)
            eventBus.publish(Mining.MineOre(player, rock, ore))
            assertTrue(bankedHerbs(bank(player)).isEmpty())

            // Relic without the pouch.
            player.inv[0] = null
            player.choose(RelicItem.FriendlyForager)
            eventBus.publish(Mining.MineOre(player, rock, ore))
            assertTrue(bankedHerbs(bank(player)).isEmpty())
        }

    private fun GameTestScope.spot() =
        spawnNpc(CoordGrid(0, 50, 50, 34, 31), npcTypes.values.first())

    @Test
    fun GameTestState.`animal wrangler retries failed fishing rolls and banks fish`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.AnimalWrangler)
            player.give(RelicItem.AnimalWrangler)
            val spot = spot()
            val tuna = objTypes[isle_objs.raw_tuna]

            var successes = 0
            repeat(400) {
                val roll = Fishing.CatchRoll(player, spot, tuna, success = false)
                eventBus.publish(roll)
                if (roll.success) {
                    successes++
                    assertTrue(roll.bank)
                }
            }
            assertTrue(successes in 140..260, "successes=$successes")
        }

    @Test
    fun GameTestState.`animal wrangler cooks every fish for cooking xp`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.AnimalWrangler)
            player.give(RelicItem.AnimalWrangler)
            val spot = spot()
            val shark = objTypes[isle_objs.raw_shark]
            check(player.statMap.getXP(stats.cooking) == 0)

            val roll = Fishing.CatchRoll(player, spot, shark, success = true)
            eventBus.publish(roll)

            // No Cooking level is needed: the shark needs 80 and the player has 1.
            assertEquals(objTypes[isle_objs.cooked_shark].id, roll.product.id)
            assertTrue(roll.bank)
            assertTrue(player.statMap.getXP(stats.cooking) > 0)
        }

    @Test
    fun GameTestState.`animal wrangler keeps stackable karambwanji in the inventory`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.AnimalWrangler)
            player.give(RelicItem.AnimalWrangler)
            val karambwanji = objTypes[isle_objs.raw_karambwanji]

            val roll = Fishing.CatchRoll(player, spot(), karambwanji, success = true)
            eventBus.publish(roll)

            assertFalse(roll.bank)
            // It cannot be cooked either, so the raw item is kept.
            assertEquals(karambwanji.id, roll.product.id)
        }

    @Test
    fun GameTestState.`animal wrangler needs the relic and its harpoon`() =
        runGameTest(LeagueGatheringRelics::class) {
            val spot = spot()
            val shark = objTypes[isle_objs.raw_shark]

            // Harpoon without the relic.
            player.give(RelicItem.AnimalWrangler)
            val noRelic = Fishing.CatchRoll(player, spot, shark, success = true)
            eventBus.publish(noRelic)
            assertFalse(noRelic.bank)
            assertEquals(shark.id, noRelic.product.id)
            repeat(100) {
                val roll = Fishing.CatchRoll(player, spot, shark, success = false)
                eventBus.publish(roll)
                assertFalse(roll.success)
            }

            // Relic without the harpoon.
            player.inv[0] = null
            player.choose(RelicItem.AnimalWrangler)
            val noTool = Fishing.CatchRoll(player, spot, shark, success = true)
            eventBus.publish(noTool)
            assertFalse(noTool.bank)
            assertEquals(shark.id, noTool.product.id)
        }

    @Test
    fun GameTestState.`animal wrangler stops food burning`() =
        runGameTest(LeagueGatheringRelics::class) {
            val raw = objTypes[isle_objs.raw_lobster]

            val before = Cooking.CookRoll(player, raw, success = false)
            eventBus.publish(before)
            assertFalse(before.success)

            player.choose(RelicItem.AnimalWrangler)
            player.give(RelicItem.AnimalWrangler)
            val roll = Cooking.CookRoll(player, raw, success = false)
            eventBus.publish(roll)
            assertTrue(roll.success)
        }

    @Test
    fun GameTestState.`friendly forager finds herbs while fishing`() =
        runGameTest(LeagueGatheringRelics::class) {
            player.choose(RelicItem.FriendlyForager)
            player.give(RelicItem.FriendlyForager)
            val tuna = objTypes[isle_objs.raw_tuna]

            repeat(50) { eventBus.publish(Fishing.FishCaught(player, spot(), tuna, tuna)) }

            assertEquals(50, bankedHerbs(bank(player)).values.sum())
        }
}
