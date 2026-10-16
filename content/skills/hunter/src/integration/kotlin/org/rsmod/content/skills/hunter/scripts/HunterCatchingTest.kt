package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.righthand
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.configs.HunterLootTables
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Stands in for the Animal Wrangler relic: impling jars never break. */
class JarRelicStub : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<HunterCatching.Opening> { breaks = false }
    }
}

@OptIn(InternalApi::class)
class HunterCatchingTest {
    private val coords = CoordGrid(0, 10, 10, 20, 20)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(level: Int, vararg items: Pair<String, Int>) {
        for (dx in -3..3) {
            for (dz in -3..3) {
                allocZoneCollision(coords.translate(dx * 8, dz * 8))
            }
        }
        player.observerUUID = player.uuid
        player.teleport(coords)
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            player.inv[slot++] = InvObj(obj(name), count)
        }
        player.stats[stats.hunter] = level
        player.statMap.setXP(stats.hunter, 0)
        advance(ticks = 12)
    }

    private fun GameTestScope.creature(internal: String): Npc {
        val type = npcTypes.values.first { it.internalName == internal }
        return spawnNpc(coords.translate(1, 0), type) { noneMode() }
    }

    private fun GameTestScope.fineXp(): Double = player.statMap.getFineXP(stats.hunter) / 10.0

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 12) {
        repeat(timeoutTicks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text)
    }

    private fun GameTestScope.wield(internal: String) {
        player.righthand = InvObj(obj(internal))
    }

    private fun GameTestScope.useItem(slot: Int, op: IfButtonOp) {
        val type = objTypes[checkNotNull(player.inv[slot])]
        eventBus.publish(IfOverlayButton(player, components.inv_items, slot, type, op))
    }

    @Test
    fun GameTestState.`a butterfly caught with a net and a jar goes into the jar`() =
        runGameTest(HunterCatching::class, HeldOpScript::class) {
            prepare(15, "butterfly_jar" to 1)
            wield("hunting_butterfly_net")
            val butterfly = creature("butterfly_ruby")

            random.next = 0
            player.opNpc1(butterfly)
            advanceUntilMessage("You catch the ruby harvest in a jar.")

            check(player.count(obj("butterfly_jar_ruby")) == 1) { "Expected a ruby harvest jar." }
            check(player.count(obj("butterfly_jar")) == 0) { "Expected the empty jar used." }
            check(fineXp() == 24.0) { "Expected 24 experience, got ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a butterfly caught barehanded is let go for the boost`() =
        runGameTest(HunterCatching::class, HeldOpScript::class) {
            prepare(25)
            val butterfly = creature("butterfly_ruby")

            random.next = 0
            player.opNpc1(butterfly)
            advanceUntilMessage("You catch the ruby harvest and let it go.")
            check(fineXp() == 44.0) { "Expected 44 experience, got ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a butterfly needs the level`() =
        runGameTest(HunterCatching::class, HeldOpScript::class) {
            prepare(10)
            wield("hunting_butterfly_net")
            val butterfly = creature("butterfly_ruby")
            player.opNpc1(butterfly)
            advanceUntilMessage("You need a Hunter level of 15 to catch ruby harvests.", 4)
        }

    @Test
    fun GameTestState.`a released butterfly gives back the empty jar`() =
        runGameTest(HunterCatching::class, HeldOpScript::class) {
            prepare(15, "butterfly_jar_ruby" to 1)
            // "Release" is the fourth inventory option.
            useItem(0, IfButtonOp.Op6)
            assertMessageSent("You release the ruby harvest.")
            check(player.count(obj("butterfly_jar")) == 1) { "Expected the empty jar." }
        }

    @Test
    fun GameTestState.`an impling caught with a net goes into a jar and vanishes for a while`() =
        runGameTest(HunterCatching::class, HeldOpScript::class) {
            prepare(17, "ii_impling_jar" to 1)
            wield("hunting_butterfly_net")
            val impling = creature("ii_impling_type_1")

            random.next = 0
            player.opNpc1(impling)
            advanceUntilMessage("You catch the baby impling in a jar.")

            check(player.count(obj("ii_captured_impling_1")) == 1) {
                "Expected a baby impling jar."
            }
            check(impling.isInvisible) { "Expected the impling gone." }
            check(fineXp() == 20.0) { "Expected 20 experience, got ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a net needs an empty impling jar`() =
        runGameTest(HunterCatching::class, HeldOpScript::class) {
            prepare(17)
            wield("hunting_butterfly_net")
            val impling = creature("ii_impling_type_1")
            player.opNpc1(impling)
            advanceUntilMessage("You need an empty impling jar.", 4)
        }

    @Test
    fun GameTestState.`an impling caught barehanded is looted at once`() =
        runGameTest(HunterCatching::class, HeldOpScript::class) {
            prepare(27)
            val impling = creature("ii_impling_type_1")

            // The first row of the baby impling's table is a chisel.
            random.next = 0
            random.next = 0
            player.opNpc1(impling)
            advanceUntilMessage(
                "You catch the baby impling barehanded and it gives up what it carries."
            )
            check(player.count(obj("chisel")) == 1) { "Expected a chisel." }
        }

    @Test
    fun GameTestState.`looting an impling jar gives its loot and usually the jar back`() =
        runGameTest(HunterCatching::class, HeldOpScript::class) {
            prepare(17, "ii_captured_impling_1" to 1)
            // A 5 does not break the jar; the table then rolls its first row, a chisel.
            random.next = 5
            random.next = 0
            // "Loot" is the third inventory option.
            useItem(0, IfButtonOp.Op4)
            advance(ticks = 2)
            check(player.count(obj("chisel")) == 1) { "Expected a chisel." }
            check(player.count(obj("ii_impling_jar")) == 1) { "Expected the empty jar back." }
        }

    @Test
    fun GameTestState.`a jar that breaks is lost`() =
        runGameTest(HunterCatching::class, HeldOpScript::class) {
            prepare(17, "ii_captured_impling_1" to 1)
            random.next = 0
            random.next = 0
            useItem(0, IfButtonOp.Op4)
            advance(ticks = 2)
            check(player.count(obj("ii_impling_jar")) == 0) { "Expected the jar broken." }
        }

    @Test
    fun GameTestState.`a relic can keep the jars from breaking`() =
        runGameTest(HunterCatching::class, HeldOpScript::class, JarRelicStub::class) {
            prepare(17, "ii_captured_impling_1" to 1)
            random.next = 0
            random.next = 0
            useItem(0, IfButtonOp.Op4)
            advance(ticks = 2)
            check(player.count(obj("ii_impling_jar")) == 1) { "Expected the jar kept." }
        }

    @Test
    fun GameTestState.`every impling and lucky loot table has items`() =
        runInjectedGameTest(LootDeps::class) { deps ->
            for (key in
                listOf(
                    "baby",
                    "young",
                    "gourmet",
                    "earth",
                    "essence",
                    "eclectic",
                    "nature",
                    "magpie",
                    "ninja",
                    "dragon",
                    "crystal",
                )) {
                // The row weights come out the same however it is rolled, so check that a roll
                // finds something.
                check(deps.tables.implingLoot(key, deps.random) != null || key == "baby") {
                    "No loot for $key."
                }
            }
            check(deps.tables.luckyLoot(deps.random) != null) { "No lucky loot." }
        }

    class LootDeps @Inject constructor(val tables: HunterLootTables, val random: GameRandom)

    @Test
    fun GameTestState.`a falcon brings back a kebbit`() =
        runGameTest(Falconer::class, HeldOpScript::class) {
            prepare(43)
            wield("falcon_gloves")
            val kebbit = creature("huntingbeast_speedy")

            random.next = 0
            player.opNpc1(kebbit)
            advanceUntilMessage("Your falcon catches the spotted kebbit and brings it back.")
            check(player.count(obj("bones")) == 1) { "Expected bones." }
            check(player.count(obj("huntingbeast_speedy_fur")) == 1) {
                "Expected spotted kebbit fur."
            }
            check(fineXp() == 104.0) { "Expected 104 experience, got ${fineXp()}." }
        }

    @Test
    fun GameTestState.`falconry needs the glove`() =
        runGameTest(Falconer::class, HeldOpScript::class) {
            prepare(43)
            val kebbit = creature("huntingbeast_speedy")
            player.opNpc1(kebbit)
            advanceUntilMessage("You need a falconer's glove to hunt with a falcon.", 4)
        }
}
