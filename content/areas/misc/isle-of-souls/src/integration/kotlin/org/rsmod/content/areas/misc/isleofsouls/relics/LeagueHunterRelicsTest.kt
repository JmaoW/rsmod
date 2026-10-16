package org.rsmod.content.areas.misc.isleofsouls.relics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.invs
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.content.skills.hunter.configs.HunterCreatures
import org.rsmod.content.skills.hunter.configs.TrapKind
import org.rsmod.content.skills.hunter.scripts.Birdhouses
import org.rsmod.content.skills.hunter.scripts.HunterCatching
import org.rsmod.content.skills.hunter.scripts.HunterTraps
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

class LeagueHunterRelicsTest {
    private fun Player.choose(relic: RelicItem) {
        VarPlayerIntMapSetter.set(this, RelicVarBits.selection[relic.tier], relic.option)
    }

    private fun Player.give(relic: RelicItem) {
        inv[0] = InvObj(relic.item)
    }

    private val herbs =
        listOf(
            isle_objs.grimy_guam,
            isle_objs.grimy_marrentill,
            isle_objs.grimy_tarromin,
            isle_objs.grimy_harralander,
            isle_objs.grimy_ranarr,
        )

    private fun GameTestState.bank(player: Player) =
        player.invMap.getOrPut(cacheTypes.invs[invs.bank])

    private fun GameTestScope.foundHerbs(bank: Inventory): Int =
        herbs.sumOf { bank.count(objTypes[it]) }

    private fun GameTestScope.roll(): HunterTraps.Roll {
        val event = HunterTraps.Roll(player, HunterCreatures["crimson_swift"])
        eventBus.publish(event)
        return event
    }

    private fun GameTestScope.collecting(key: String): HunterTraps.Collecting {
        val event = HunterTraps.Collecting(player, HunterCreatures[key])
        eventBus.publish(event)
        return event
    }

    @Test
    fun GameTestState.`animal wrangler makes every trap catch`() =
        runGameTest(LeagueHunterRelics::class) {
            assertFalse(roll().success)

            // The hunter effects do not need the harpoon.
            player.choose(RelicItem.AnimalWrangler)
            assertTrue(roll().success)
        }

    @Test
    fun GameTestState.`animal wrangler doubles a chinchompa catch and its experience`() =
        runGameTest(LeagueHunterRelics::class) {
            player.choose(RelicItem.AnimalWrangler)

            for (key in listOf("chinchompa", "carnivorous_chinchompa", "black_chinchompa")) {
                assertEquals(2, collecting(key).catches, key)
            }
            assertEquals(1, collecting("ferret").catches)
            assertEquals(1, collecting("crimson_swift").catches)
        }

    @Test
    fun GameTestState.`animal wrangler hurries box traps only`() =
        runGameTest(LeagueHunterRelics::class) {
            player.choose(RelicItem.AnimalWrangler)

            val box = HunterTraps.Modifiers(player, TrapKind.Box)
            eventBus.publish(box)
            assertEquals(LeagueHunterRelics.FAST_SCAN_TICKS, box.scanTicks)
            assertEquals(LeagueHunterRelics.FAST_SETTLE_TICKS, box.settleTicks)

            val bird = HunterTraps.Modifiers(player, TrapKind.Bird)
            eventBus.publish(bird)
            assertEquals(HunterTraps.SCAN_TICKS, bird.scanTicks)
        }

    @Test
    fun GameTestState.`animal wrangler keeps impling jars from breaking`() =
        runGameTest(LeagueHunterRelics::class) {
            val impling = org.rsmod.content.skills.hunter.configs.HunterCatchables.implings.first()
            val plain = HunterCatching.Opening(player, impling).also { it.breaks = true }
            eventBus.publish(plain)
            assertTrue(plain.breaks)

            player.choose(RelicItem.AnimalWrangler)
            val kept = HunterCatching.Opening(player, impling).also { it.breaks = true }
            eventBus.publish(kept)
            assertFalse(kept.breaks)
        }

    @Test
    fun GameTestState.`friendly forager finds a herb with each kind of hunting`() =
        runGameTest(LeagueHunterRelics::class) {
            player.choose(RelicItem.FriendlyForager)
            player.give(RelicItem.FriendlyForager)
            player.xpRate = 0.0

            eventBus.publish(
                HunterTraps.Trapped(player, HunterCreatures["crimson_swift"], 1, emptyList())
            )
            assertEquals(1, foundHerbs(bank(player)))
            eventBus.publish(HunterCatching.Caught(player, "Baby impling", true))
            assertEquals(2, foundHerbs(bank(player)))
            eventBus.publish(Birdhouses.Collected(player, "normal", 0))
            assertEquals(3, foundHerbs(bank(player)))
        }

    @Test
    fun GameTestState.`friendly forager needs the relic and its pouch`() =
        runGameTest(LeagueHunterRelics::class) {
            player.give(RelicItem.FriendlyForager)
            eventBus.publish(
                HunterTraps.Trapped(player, HunterCreatures["crimson_swift"], 1, emptyList())
            )
            assertEquals(0, foundHerbs(bank(player)))
        }
}
