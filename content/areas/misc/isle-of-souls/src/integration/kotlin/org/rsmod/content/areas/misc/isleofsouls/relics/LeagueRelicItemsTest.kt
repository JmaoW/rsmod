package org.rsmod.content.areas.misc.isleofsouls.relics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.config.refs.varps
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.inv.HeldUOpScript
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class LeagueRelicItemsTest {
    private val scripts =
        arrayOf(
            HeldOpScript::class,
            HeldUOpScript::class,
            LeagueTotalRecall::class,
            LeagueBankersNote::class,
            LeagueGrimoire::class,
        )

    private val home = CoordGrid(0, 50, 50, 20, 20)
    private val away = CoordGrid(0, 50, 50, 40, 40)

    private fun GameTestScope.choose(relic: RelicItem) {
        for (tier in 0 until RelicData.tierCount) {
            VarPlayerIntMapSetter.set(player, RelicVarBits.selection[tier], 0)
        }
        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[relic.tier], relic.option)
    }

    private fun GameTestScope.give(slot: Int, obj: ObjType, count: Int = 1) {
        player.inv[slot] = InvObj(obj, count)
    }

    /** Clicks option [op] (1 to 5) of the item in [slot]. */
    private fun GameTestScope.option(slot: Int, op: Int) {
        val obj = objTypes[player.inv[slot]!!]
        val button =
            when (op) {
                1 -> IfButtonOp.Op2
                2 -> IfButtonOp.Op3
                3 -> IfButtonOp.Op4
                4 -> IfButtonOp.Op6
                else -> IfButtonOp.Op7
            }
        eventBus.publish(IfOverlayButton(player, components.inv_items, slot, obj, button))
    }

    /** Uses the item in [first] on the item in [second]. */
    private fun GameTestScope.useOn(first: Int, second: Int) {
        val comp = Component(components.inv_items.packed)
        val firstObj = objTypes[player.inv[first]!!]
        val secondObj = objTypes[player.inv[second]!!]
        eventBus.publish(IfOverlayButtonT(player, first, firstObj, second, secondObj, comp, comp))
    }

    private fun GameTestScope.total(obj: ObjType): Int =
        player.inv.sumOf { if (it?.id == obj.id) it.count else 0 }

    private fun GameTestScope.noted(obj: ObjType): ObjType = objTypes.cert(objTypes[obj])

    @Test
    fun GameTestState.`the crystal brings back the saved tile and stats`() =
        runGameTest(*scripts) {
            player.clearInv()
            choose(RelicItem.TotalRecall)
            give(0, isle_objs.crystal_of_echoes)
            player.stats[stats.hitpoints] = 99
            player.stats[stats.prayer] = 70
            player.stats.setCurrentLevel(stats.hitpoints, 50)
            player.stats.setCurrentLevel(stats.prayer, 40)
            VarPlayerIntMapSetter.set(player, varps.sa_energy, 600)
            player.teleport(home)
            advance(1)

            option(0, 3)
            advance(1)
            assertEquals(home, LeagueTotalRecall.savedCoord(player))

            player.teleport(away)
            player.stats.setCurrentLevel(stats.hitpoints, 99)
            player.stats.setCurrentLevel(stats.prayer, 70)
            VarPlayerIntMapSetter.set(player, varps.sa_energy, 1000)
            advance(1)

            option(0, 1)
            advance(6)
            assertEquals(home, player.coords)
            assertEquals(50, player.hitpoints)
            assertEquals(40, player.stat(stats.prayer))
            assertEquals(600, player.vars[varps.sa_energy])
            assertEquals(home, LeagueTotalRecall.savedCoord(player), "the memory stays")
        }

    @Test
    fun GameTestState.`the crystal does nothing without the relic`() =
        runGameTest(*scripts) {
            player.clearInv()
            choose(RelicItem.BankersNote)
            give(0, isle_objs.crystal_of_echoes)
            player.teleport(home)
            advance(1)
            option(0, 3)
            advance(1)
            assertNull(LeagueTotalRecall.savedCoord(player))
        }

    @Test
    fun GameTestState.`using an item on the note notes all of it`() =
        runGameTest(*scripts) {
            player.clearInv()
            choose(RelicItem.BankersNote)
            give(0, isle_objs.bankers_note)
            for (slot in 1..5) {
                give(slot, isle_objs.cooked_shark)
            }
            useOn(1, 0)
            advance(1)
            assertEquals(0, total(isle_objs.cooked_shark))
            assertEquals(5, total(noted(isle_objs.cooked_shark)))
        }

    @Test
    fun GameTestState.`using a noted item on the note un-notes what fits`() =
        runGameTest(*scripts) {
            player.clearInv()
            choose(RelicItem.BankersNote)
            give(0, isle_objs.bankers_note)
            give(1, noted(isle_objs.cooked_shark), 40)
            useOn(1, 0)
            advance(1)
            // 28 slots, one holds the note and one keeps the remaining notes.
            assertEquals(26, total(isle_objs.cooked_shark))
            assertEquals(14, total(noted(isle_objs.cooked_shark)))
        }

    @Test
    fun GameTestState.`activate repeats the last item with the chosen quantity`() =
        runGameTest(*scripts) {
            player.clearInv()
            choose(RelicItem.BankersNote)
            give(0, isle_objs.bankers_note)
            give(1, isle_objs.cooked_shark)
            useOn(1, 0)
            advance(1)

            option(0, 2)
            advance(1)
            player.resumeCountDialog(2)
            advance(1)
            for (slot in 2..6) {
                give(slot, isle_objs.cooked_shark)
            }
            option(0, 1)
            advance(1)
            assertEquals(3, total(isle_objs.cooked_shark))
            assertEquals(3, total(noted(isle_objs.cooked_shark)))

            option(0, 4)
            advance(1)
            option(0, 1)
            advance(1)
            assertEquals(6, total(isle_objs.cooked_shark), "toggled to un-noting")
            assertEquals(0, total(noted(isle_objs.cooked_shark)))
        }

    @Test
    fun GameTestState.`the note does nothing without the relic`() =
        runGameTest(*scripts) {
            player.clearInv()
            choose(RelicItem.TotalRecall)
            give(0, isle_objs.bankers_note)
            give(1, isle_objs.cooked_shark)
            useOn(1, 0)
            advance(1)
            assertEquals(1, total(isle_objs.cooked_shark))
        }

    @Test
    fun GameTestState.`the grimoire swaps spellbooks and back`() =
        runGameTest(*scripts) {
            player.clearInv()
            choose(RelicItem.Grimoire)
            give(0, isle_objs.arcane_grimoire)
            VarPlayerIntMapSetter.set(player, varbits.spellbook, Spellbook.Standard.varValue)

            option(0, 1)
            advance(1)
            player.resumePauseButton(components.chatmenu_pbutton, 3)
            advance(1)
            assertEquals(Spellbook.Lunars.varValue, player.vars[varbits.spellbook])

            option(0, 3)
            advance(1)
            assertEquals(Spellbook.Standard.varValue, player.vars[varbits.spellbook])
            option(0, 3)
            advance(1)
            assertEquals(Spellbook.Lunars.varValue, player.vars[varbits.spellbook])
        }

    @Test
    fun GameTestState.`the grimoire does nothing without the relic`() =
        runGameTest(*scripts) {
            player.clearInv()
            choose(RelicItem.TotalRecall)
            give(0, isle_objs.arcane_grimoire)
            VarPlayerIntMapSetter.set(player, varbits.spellbook, Spellbook.Standard.varValue)
            VarPlayerIntMapSetter.set(player, GrimoireVarBits.previous, Spellbook.Ancients.varValue)
            option(0, 3)
            advance(1)
            assertEquals(Spellbook.Standard.varValue, player.vars[varbits.spellbook])
        }
}
