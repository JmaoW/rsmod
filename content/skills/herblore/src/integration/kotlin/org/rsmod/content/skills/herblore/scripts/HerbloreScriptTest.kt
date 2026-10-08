package org.rsmod.content.skills.herblore.scripts

import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.mastery.MasteryStyle
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.timers
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.inv.HeldUOpScript
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.stats.plugin.StatRegenScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.testing.scope.TestCombatMasteries
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class HerbloreScriptTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(level: Int, vararg items: Pair<String, Int>) {
        player.teleport(coords)
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            val type = obj(name)
            if (type.isStackable) {
                player.inv[slot++] = InvObj(type, count)
            } else {
                repeat(count) { player.inv[slot++] = InvObj(type) }
            }
        }
        player.stats[stats.herblore] = level
        player.statMap.setXP(stats.herblore, 0)
    }

    private fun GameTestScope.useOn(first: String, second: String) {
        val comp = Component(components.inv_items.packed)
        val firstSlot = player.inv.indexOfFirst { it?.id == obj(first).id }
        val secondSlot = player.inv.indexOfFirst { it?.id == obj(second).id }
        eventBus.publish(
            IfOverlayButtonT(player, firstSlot, obj(first), secondSlot, obj(second), comp, comp)
        )
    }

    private fun GameTestScope.option(internal: String, op: IfButtonOp) {
        val slot = player.inv.indexOfFirst { it?.id == obj(internal).id }
        val type = objTypes[checkNotNull(player.inv[slot])]
        eventBus.publish(IfOverlayButton(player, components.inv_items, slot, type, op))
    }

    /** Clean (op 1) or drink (op 1) is the first inventory option, which is button op 2. */
    private fun GameTestScope.click(internal: String) = option(internal, IfButtonOp.Op2)

    /** Waits for the menu, then clicks its nth item with the given quantity. */
    private fun GameTestScope.choose(index: Int, quantity: Int) {
        advanceUntil({ player.ui.containsModal(interfaces.skillmulti) }, timeoutTicks = 10)
        player.resumePauseButton(components.skillmulti_buttons[index], quantity)
    }

    private fun GameTestScope.xp(): Double = player.statMap.getFineXP(stats.herblore) / 10.0

    private fun GameTestScope.level(stat: org.rsmod.game.type.stat.StatType) = player.stat(stat)

    @Test
    fun GameTestState.`cleaning a grimy herb gives the herb and experience`() =
        runGameTest(Herblore::class, HeldOpScript::class) {
            prepare(3, "unidentified_guam" to 1)

            click("unidentified_guam")
            advance(ticks = 1)

            check(player.count(obj("guam_leaf")) == 1) { "Expected a clean guam leaf." }
            check(player.count(obj("unidentified_guam")) == 0) { "Expected the grimy herb gone." }
            check(xp() >= 2.5) { "Expected 2.5 Herblore experience, got ${xp()}." }
        }

    @Test
    fun GameTestState.`a herb is only cleaned at the level`() =
        runGameTest(Herblore::class, HeldOpScript::class) {
            prepare(1, "unidentified_ranarr" to 1)

            click("unidentified_ranarr")
            assertMessageSent("You do not have the Herblore level required to clean this herb.")

            check(player.count(obj("unidentified_ranarr")) == 1) { "Expected the herb kept." }
            check(player.count(obj("ranarr_weed")) == 0) { "Expected no clean herb." }
        }

    @Test
    fun GameTestState.`a herb in a vial of water makes an unfinished potion`() =
        runGameTest(Herblore::class, HeldUOpScript::class) {
            prepare(3, "vial_water" to 1, "guam_leaf" to 1)

            useOn("vial_water", "guam_leaf")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("guamvial")) == 1 }, timeoutTicks = 10)

            check(player.count(obj("vial_water")) == 0) { "Expected the vial used." }
            check(player.count(obj("guam_leaf")) == 0) { "Expected the herb used." }
            check(xp() == 0.0) { "An unfinished potion gives no experience." }
        }

    @Test
    fun GameTestState.`the secondary makes a three dose potion and gives experience`() =
        runGameTest(Herblore::class, HeldUOpScript::class) {
            prepare(3, "guamvial" to 1, "eye_of_newt" to 1)

            useOn("eye_of_newt", "guamvial")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("3dose1attack")) == 1 }, timeoutTicks = 10)

            check(player.count(obj("eye_of_newt")) == 0) { "Expected the secondary used." }
            check(player.count(obj("guamvial")) == 0) { "Expected the unfinished potion used." }
            check(xp() >= 25.0) { "Expected 25 Herblore experience, got ${xp()}." }
        }

    @Test
    fun GameTestState.`a potion is only made at the level`() =
        runGameTest(Herblore::class, HeldUOpScript::class) {
            prepare(1, "tarrominvial" to 1, "limpwurt_root" to 1)

            useOn("limpwurt_root", "tarrominvial")
            choose(index = 0, quantity = 1)
            advanceUntilMessage("You need a Herblore level of 12 to make strength potion(3).")

            check(player.count(obj("tarrominvial")) == 1) { "Expected the potion kept." }
            check(player.count(obj("limpwurt_root")) == 1) { "Expected the secondary kept." }
        }

    @Test
    fun GameTestState.`the quantity asked for is made`() =
        runGameTest(Herblore::class, HeldUOpScript::class) {
            prepare(3, "vial_water" to 3, "guam_leaf" to 3)

            useOn("vial_water", "guam_leaf")
            choose(index = 0, quantity = 2)
            advanceUntil({ player.count(obj("guamvial")) >= 2 }, timeoutTicks = 20)
            advance(ticks = 6)

            check(player.count(obj("guamvial")) == 2) { "Expected two unfinished potions." }
            check(player.count(obj("guam_leaf")) == 1) { "Expected one herb left." }
        }

    @Test
    fun GameTestState.`walking away stops the job and never holds the player`() =
        runGameTest(Herblore::class, HeldUOpScript::class) {
            prepare(3, "vial_water" to 3, "guam_leaf" to 3)

            useOn("vial_water", "guam_leaf")
            choose(index = 0, quantity = 3)
            check(!player.isDelayed) { "A herblore job must not delay the player." }
            player.ifClose(eventBus)
            advance(ticks = 12)

            check(player.count(obj("guamvial")) == 0) { "Expected the job to have stopped." }
        }

    @Test
    fun GameTestState.`an upgrade needs the same number of ingredients as doses`() =
        runGameTest(Herblore::class, HeldUOpScript::class) {
            prepare(77, "3dose2energy" to 1, "amylase" to 3)

            useOn("amylase", "3dose2energy")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("3dosestamina")) == 1 }, timeoutTicks = 10)

            check(player.count(obj("amylase")) == 0) { "Expected three amylase crystals used." }
            check(xp() >= 76.5) { "Expected 76.5 Herblore experience, got ${xp()}." }
        }

    @Test
    fun GameTestState.`a super combat potion takes the three supers and torstol`() =
        runGameTest(Herblore::class, HeldUOpScript::class) {
            prepare(
                90,
                "4dose2attack" to 1,
                "4dose2strength" to 1,
                "4dose2defense" to 1,
                "torstol" to 1,
            )

            useOn("torstol", "4dose2attack")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("4dose2combat")) == 1 }, timeoutTicks = 10)

            check(player.count(obj("torstol")) == 0) { "Expected the torstol used." }
            check(player.count(obj("4dose2strength")) == 0) { "Expected the super strength used." }
            check(xp() >= 150.0) { "Expected 150 Herblore experience, got ${xp()}." }
        }

    @Test
    fun GameTestState.`a pestle and mortar grinds a unicorn horn`() =
        runGameTest(Herblore::class, HeldUOpScript::class) {
            prepare(1, "pestle_and_mortar" to 1, "unicorn_horn" to 1)

            useOn("pestle_and_mortar", "unicorn_horn")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("unicorn_horn_dust")) == 1 }, timeoutTicks = 10)

            check(player.count(obj("unicorn_horn")) == 0) { "Expected the horn used." }
            check(player.count(obj("pestle_and_mortar")) == 1) { "Expected the tool kept." }
        }

    @Test
    fun GameTestState.`a chisel cuts a lava scale into shards`() =
        runGameTest(Herblore::class, HeldUOpScript::class) {
            prepare(1, "chisel" to 1, "lava_scale" to 1)

            useOn("chisel", "lava_scale")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("lava_shard")) > 0 }, timeoutTicks = 10)

            val shards = player.count(obj("lava_shard"))
            check(shards in 3..6) { "Expected three to six shards, got $shards." }
        }

    class Mods @Inject constructor(val modifiers: HerbloreModifiers)

    private class BonusMod : HerbloreMod {
        override fun extraDoses(player: Player) = 1

        override fun secondarySaveChance(player: Player) = 100
    }

    @Test
    fun GameTestState.`a mod can add a dose and keep the secondary`() =
        runInjectedGameTest(Mods::class, null, Herblore::class, HeldUOpScript::class) { deps ->
            deps.modifiers.register(BonusMod())
            prepare(3, "guamvial" to 1, "eye_of_newt" to 1)

            useOn("eye_of_newt", "guamvial")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("4dose1attack")) == 1 }, timeoutTicks = 10)

            check(player.count(obj("eye_of_newt")) == 1) { "Expected the secondary kept." }
            check(player.count(obj("guamvial")) == 0) { "Expected the unfinished potion used." }
        }

    @Test
    fun GameTestState.`drinking takes a dose and boosts the stat`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "3dose1attack" to 1)
            player.stats[stats.attack] = 50
            player.stats.setCurrentLevel(stats.attack, 50)

            click("3dose1attack")
            assertMessageSent("You have 2 doses of potion left.")
            advance(ticks = 1)

            // 3 + 10% of 50.
            check(level(stats.attack) == 58) { "Expected 58 Attack, got ${level(stats.attack)}." }
            check(player.count(obj("2dose1attack")) == 1) { "Expected two doses left." }
        }

    @Test
    fun GameTestState.`the last dose leaves an empty vial`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "1dose1attack" to 1)

            click("1dose1attack")
            assertMessageSent("You have finished your potion.")
            advance(ticks = 1)

            check(player.count(obj("vial_empty")) == 1) { "Expected an empty vial." }
        }

    @Test
    fun GameTestState.`a potion cannot be drunk again for three ticks`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4dose1attack" to 1)

            click("4dose1attack")
            click("3dose1attack")
            advance(ticks = 1)
            check(player.count(obj("3dose1attack")) == 1) { "The second drink must wait." }

            advance(ticks = 4)
            click("3dose1attack")
            advance(ticks = 1)
            check(player.count(obj("2dose1attack")) == 1) { "Expected the next drink to work." }
        }

    @Test
    fun GameTestState.`a super restore restores prayer and the other stats`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4dose2restore" to 1)
            player.stats[stats.prayer] = 70
            player.stats.setCurrentLevel(stats.prayer, 10)
            player.stats[stats.attack] = 70
            player.stats.setCurrentLevel(stats.attack, 10)

            click("4dose2restore")
            advance(ticks = 1)

            // 8 + 25% of 70.
            check(level(stats.prayer) == 35) { "Expected 35 Prayer, got ${level(stats.prayer)}." }
            check(level(stats.attack) == 35) { "Expected 35 Attack, got ${level(stats.attack)}." }
        }

    @Test
    fun GameTestState.`the fifth mastery passive raises the prayer points a potion restores`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4dose2restore" to 1)
            player.stats[stats.prayer] = 70
            player.stats.setCurrentLevel(stats.prayer, 10)
            player.stats[stats.attack] = 70
            player.stats.setCurrentLevel(stats.attack, 10)
            TestCombatMasteries.set(player, MasteryStyle.Melee, 5)

            click("4dose2restore")
            advance(ticks = 1)

            // The 25 points the potion restores are 25% more, rounded down: 31. Other stats are
            // not affected.
            check(level(stats.prayer) == 41) { "Expected 41 Prayer, got ${level(stats.prayer)}." }
            check(level(stats.attack) == 35) { "Expected 35 Attack, got ${level(stats.attack)}." }
        }

    @Test
    fun GameTestState.`a restore potion leaves prayer alone`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4dosestatrestore" to 1)
            player.stats[stats.prayer] = 70
            player.stats.setCurrentLevel(stats.prayer, 10)
            player.stats[stats.attack] = 70
            player.stats.setCurrentLevel(stats.attack, 10)

            click("4dosestatrestore")
            advance(ticks = 1)

            check(level(stats.prayer) == 10) { "Prayer must not change." }
            // 10 + 30% of 70.
            check(level(stats.attack) == 41) { "Expected 41 Attack, got ${level(stats.attack)}." }
        }

    @Test
    fun GameTestState.`a prayer potion restores prayer points`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4doseprayerrestore" to 1)
            player.stats[stats.prayer] = 80
            player.stats.setCurrentLevel(stats.prayer, 0)

            click("4doseprayerrestore")
            advance(ticks = 1)

            // 7 + 25% of 80.
            check(level(stats.prayer) == 27) { "Expected 27 Prayer, got ${level(stats.prayer)}." }
        }

    @Test
    fun GameTestState.`an energy potion restores run energy`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4dose2energy" to 1)
            player.runEnergy = 1000

            click("4dose2energy")
            advance(ticks = 1)

            check(player.runEnergy >= 3000) { "Expected 20% more energy, got ${player.runEnergy}." }
        }

    @Test
    fun GameTestState.`a saradomin brew trades attack for defence`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4dosepotionofsaradomin" to 1)
            for (stat in listOf(stats.attack, stats.defence, stats.hitpoints)) {
                player.stats[stat] = 80
                player.stats.setCurrentLevel(stat, 80)
            }

            click("4dosepotionofsaradomin")
            advance(ticks = 1)

            // Defence 2 + 20% of 80, attack 2 + 10% of 80 lower, hitpoints 2 + 15% of 80.
            check(level(stats.defence) == 98) {
                "Expected 98 Defence, got ${level(stats.defence)}."
            }
            check(level(stats.attack) == 70) { "Expected 70 Attack, got ${level(stats.attack)}." }
            check(level(stats.hitpoints) == 94) { "Expected 94 hitpoints." }
        }

    @Test
    fun GameTestState.`a stamina potion switches the stamina effect on and then off`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4dosestamina" to 1)

            click("4dosestamina")
            advance(ticks = 1)
            check(player.vars[org.rsmod.api.config.refs.varbits.stamina_active] == 1) {
                "Expected the stamina effect to be on."
            }

            advance(ticks = 205)
            check(player.vars[org.rsmod.api.config.refs.varbits.stamina_active] == 0) {
                "Expected the stamina effect to have ended."
            }
        }

    @Test
    fun GameTestState.`prayer regeneration gives a point every twelve ticks`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4dose1prayer_regeneration" to 1)
            player.stats[stats.prayer] = 50
            player.stats.setCurrentLevel(stats.prayer, 10)

            click("4dose1prayer_regeneration")
            advance(ticks = 40)

            val gained = level(stats.prayer) - 10
            check(gained in 3..4) { "Expected three points in 40 ticks, got $gained." }
        }

    @Test
    fun GameTestState.`an antipoison protects for a while and then wears off`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4doseantipoison" to 1)

            click("4doseantipoison")
            assertMessageSent("You drink some of your antipoison.")

            advanceUntilMessage("Your immunity to poison has worn off.", timeoutTicks = 160)
        }

    @Test
    fun GameTestState.`a divine potion costs hitpoints and holds the boost`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4dosedivineattack" to 1)
            player.stats[stats.attack] = 80
            player.stats.setCurrentLevel(stats.attack, 80)
            player.stats[stats.hitpoints] = 80
            player.stats.setCurrentLevel(stats.hitpoints, 80)

            click("4dosedivineattack")
            advance(ticks = 2)

            check(level(stats.attack) == 97) { "Expected 97 Attack, got ${level(stats.attack)}." }
            check(level(stats.hitpoints) == 70) {
                "Expected 10 damage, got ${level(stats.hitpoints)}."
            }

            advanceUntilMessage(
                "The effects of your divine potion have worn off.",
                timeoutTicks = 510,
            )
            check(level(stats.attack) == 80) { "Expected the boost gone after five minutes." }
        }

    @Test
    fun GameTestState.`a divine potion needs more than ten hitpoints`() =
        runGameTest(Drinking::class, HeldOpScript::class) {
            prepare(1, "4dosedivineattack" to 1)
            player.stats[stats.hitpoints] = 80
            player.stats.setCurrentLevel(stats.hitpoints, 10)

            click("4dosedivineattack")
            assertMessageSent("You do not have enough Hitpoints to drink this potion.")

            check(player.count(obj("4dosedivineattack")) == 1) { "Expected the potion kept." }
        }

    @Test
    fun GameTestState.`a divine boost is held while an ordinary boost wears off`() =
        runGameTest(Drinking::class, HeldOpScript::class, StatRegenScript::class) {
            prepare(1, "4dosedivineattack" to 1, "4dose1defense" to 1)
            for (stat in listOf(stats.attack, stats.defence, stats.hitpoints)) {
                player.stats[stat] = 80
                player.stats.setCurrentLevel(stat, 80)
            }

            click("4dosedivineattack")
            advance(ticks = 4)
            click("4dose1defense")
            advance(ticks = 1)
            check(level(stats.defence) == 91) {
                "Expected 91 Defence, got ${level(stats.defence)}."
            }

            player.softTimer(timers.stat_boost_restore, 1)
            advance(ticks = 5)

            check(level(stats.attack) == 97) { "The divine boost must not wear off." }
            check(level(stats.defence) < 91) { "An ordinary boost must wear off." }
        }

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 20) {
        repeat(timeoutTicks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text)
    }
}
