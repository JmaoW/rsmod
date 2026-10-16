package org.rsmod.content.skills.herblore.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.stat.StatType
import org.rsmod.map.CoordGrid

/** The potions other content hands out: mixes, overloads, Chambers of Xeric potions and more. */
class MinigamePotionsTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(vararg potions: String) {
        player.teleport(coords)
        player.clearInv()
        potions.forEachIndexed { slot, name -> player.inv[slot] = InvObj(obj(name)) }
    }

    private fun GameTestScope.level(stat: StatType, level: Int, current: Int = level) {
        player.stats[stat] = level
        player.stats.setCurrentLevel(stat, current)
    }

    private fun GameTestScope.drink(slot: Int = 0) {
        val type = objTypes[checkNotNull(player.inv[slot])]
        eventBus.publish(IfOverlayButton(player, components.inv_items, slot, type, IfButtonOp.Op2))
    }

    @Test
    fun GameTestState.`a barbarian mix boosts the stat and heals`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("brutal_2dose1attack")
            level(stats.attack, 50)
            level(stats.hitpoints, 99, 50)

            drink()
            advance(ticks = 1)

            // 3 + 10% of 50, and 3 hitpoints for an attack mix.
            check(player.stat(stats.attack) == 58) { "Expected Attack 58." }
            check(player.hitpoints == 53) { "Expected 53 hitpoints, got ${player.hitpoints}." }
            check(player.inv[0]?.id == obj("brutal_1dose1attack").id) { "Expected one dose left." }
        }

    @Test
    fun GameTestState.`the last dose of a mix leaves an empty vial`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("brutal_1dose2restore")

            drink()
            advance(ticks = 1)

            check(player.inv[0]?.id == obj("vial_empty").id) { "Expected an empty vial." }
        }

    @Test
    fun GameTestState.`overload boosts the combat stats and takes fifty hitpoints`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("nzone4doseoverloadpotion")
            for (stat in listOf(stats.attack, stats.strength, stats.defence)) level(stat, 80)
            level(stats.ranged, 80)
            level(stats.magic, 80)
            level(stats.hitpoints, 99)

            drink()
            advance(ticks = 8)

            // 5 + 15% of 80.
            for (stat in listOf(stats.attack, stats.strength, stats.defence, stats.ranged)) {
                check(player.stat(stat) == 97) { "Expected 97, got ${player.stat(stat)}." }
            }
            check(player.stat(stats.magic) == 97) { "Expected Magic 97." }
            check(player.hitpoints == 49) { "Expected 49 hitpoints, got ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`overload needs more than fifty hitpoints`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("nzone4doseoverloadpotion")
            level(stats.hitpoints, 99, 50)

            drink()
            assertMessageSent("You need more than 50 Hitpoints to drink this potion.")

            check(player.inv[0]?.id == obj("nzone4doseoverloadpotion").id) { "Expected it kept." }
        }

    @Test
    fun GameTestState.`overload wears off after five minutes and heals fifty`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("nzone4doseoverloadpotion")
            level(stats.attack, 80)
            level(stats.hitpoints, 99)

            drink()
            advance(ticks = 8)
            check(player.hitpoints == 49)

            advance(ticks = 505)

            check(player.stat(stats.attack) == 80) { "Expected the boost gone." }
            check(player.hitpoints == 99) { "Expected 99 hitpoints, got ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`a Chambers of Xeric potion boosts and disappears with the last dose`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("raids_vial_elder_strong_1")
            for (stat in listOf(stats.attack, stats.strength, stats.defence)) level(stat, 80)

            drink()
            advance(ticks = 1)

            // 6 + 16% of 80.
            for (stat in listOf(stats.attack, stats.strength, stats.defence)) {
                check(player.stat(stat) == 98) { "Expected 98, got ${player.stat(stat)}." }
            }
            check(player.inv[0] == null) { "The gourd potion should leave nothing." }
        }

    @Test
    fun GameTestState.`xerics aid lowers the offensive stats and boosts defence and hitpoints`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("raids_vial_xericaid_4")
            for (stat in listOf(stats.attack, stats.strength, stats.ranged, stats.magic)) {
                level(stat, 80)
            }
            level(stats.defence, 80)
            level(stats.hitpoints, 99)

            drink()
            advance(ticks = 1)

            check(player.stat(stats.attack) == 73) { "Expected Attack 73." }
            check(player.stat(stats.defence) == 97) { "Expected Defence 97." }
            // 15% of 99, over the base level.
            check(player.hitpoints == 113) { "Expected 113 hitpoints, got ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`nectar heals above the base level`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("toa_supply_heal_4")
            level(stats.hitpoints, 99)

            drink()
            advance(ticks = 1)

            // 15% of 99 and 3.
            check(player.hitpoints == 116) { "Expected 116 hitpoints, got ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`ambrosia restores everything`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("toa_supply_panicheal_2")
            level(stats.hitpoints, 99, 10)
            level(stats.prayer, 80, 5)
            player.runEnergy = 0

            drink()
            advance(ticks = 1)

            check(player.hitpoints == 125) { "Expected 125 hitpoints, got ${player.hitpoints}." }
            check(player.stat(stats.prayer) == 101) { "Expected 101 prayer points." }
            check(player.runEnergy >= 10_000) { "Expected full run energy." }
        }

    @Test
    fun GameTestState.`a blighted super restore restores like a super restore`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("blighted_4dose2restore")
            level(stats.attack, 80, 20)

            drink()
            advance(ticks = 1)

            // 8 + 25% of 80.
            check(player.stat(stats.attack) == 48) { "Expected Attack 48." }
        }

    @Test
    fun GameTestState.`a boosted stat does not stop a restore potion`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("blighted_4dose2restore")
            level(stats.attack, 80, 95)
            level(stats.defence, 80, 20)

            drink()
            advance(ticks = 1)

            check(player.stat(stats.attack) == 95) { "A boost should stay." }
            check(player.stat(stats.defence) == 48) { "Expected Defence 48." }
        }

    @Test
    fun GameTestState.`menaphite remedy restores the combat stats again every 25 ticks`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("1dosestatrenewal")
            level(stats.attack, 80, 10)

            drink()
            advance(ticks = 1)
            // 6 + 16% of 80.
            check(player.stat(stats.attack) == 28) { "Expected Attack 28." }

            advance(ticks = 25)
            check(player.stat(stats.attack) == 46) { "Expected a second restore, got Attack 46." }
        }

    @Test
    fun GameTestState.`prayer enhance restores a prayer point every six ticks`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("raids_vial_prayer_4")
            level(stats.prayer, 80, 10)

            drink()
            advance(ticks = 20)

            val points = player.stat(stats.prayer) - 10
            check(points in 2..4) { "Expected about three prayer points, got $points." }
        }

    @Test
    fun GameTestState.`guthix rest heals above the base level and leaves a cup`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("cup_guthix_rest_1")
            level(stats.hitpoints, 99)

            drink()
            advance(ticks = 1)

            check(player.hitpoints == 104) { "Expected 104 hitpoints, got ${player.hitpoints}." }
            check(player.inv[0]?.id == obj("cup_empty").id) { "Expected an empty cup." }
        }

    @Test
    fun GameTestState.`the Barbarian Assault copy of a prayer potion restores prayer`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare("br_prayer4")
            level(stats.prayer, 80, 10)

            drink()
            advance(ticks = 1)

            // 7 + 25% of 80.
            check(player.stat(stats.prayer) == 37) { "Expected 37 prayer points." }
            check(player.inv[0]?.id == obj("br_prayer3").id) { "Expected three doses left." }
        }
}
