package org.rsmod.content.skills.thieving.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.script.onEvent
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.thieving.configs.PickpocketTargets
import org.rsmod.content.skills.thieving.configs.ThievingVarps
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Stands in for the Dodgy Deals relic: area, noted loot and three times the coin pouches. */
class DodgyDealsStub : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<Pickpocketing.Modifiers> {
            area = true
            noted = true
            pouchMultiplier = 3
        }
    }
}

class PickpocketingTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(level: Int, vararg items: Pair<String, Int>) {
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
        player.stats[stats.thieving] = level
        player.stats[stats.hitpoints] = 99
        player.statMap.setXP(stats.thieving, 0)
        for (piece in 0 until WORN_SLOTS) {
            player.worn[piece] = null
        }
        for (varp in listOf(ThievingVarps.gloves_fails, ThievingVarps.necklace_saves)) {
            player.withProtectedAccess { vars[varp] = 0 }
        }
    }

    private fun GameTestScope.victim(key: String, at: CoordGrid = coords): Npc {
        val target = PickpocketTargets.byKey(key)
        val type = npcTypes.values.first { target.matches(it) }
        val npc = spawnNpc(at, type)
        player.teleport(coords.translateX(-1))
        // A new player counts as just out of combat until the map clock has run on a little.
        advance(ticks = QUIET_TICKS)
        return npc
    }

    /** Clicks the option that robs [npc]. */
    private fun GameTestScope.rob(npc: Npc) {
        val target = checkNotNull(PickpocketTargets.forNpc(npc.type))
        when (target.opIndex(npc.type) + 1) {
            1 -> player.opNpc1(npc)
            2 -> player.opNpc2(npc)
            3 -> player.opNpc3(npc)
            4 -> player.opNpc4(npc)
            else -> player.opNpc5(npc)
        }
    }

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 12) {
        repeat(timeoutTicks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text)
    }

    private fun GameTestScope.fineXp(): Double = player.statMap.getFineXP(stats.thieving) / 10.0

    private fun GameTestScope.wear(slot: Wearpos, internal: String) {
        player.worn[slot.slot] = InvObj(obj(internal))
    }

    private fun GameTestScope.rogueSet() {
        wear(Wearpos.Hat, "roguesden_helm")
        wear(Wearpos.Torso, "roguesden_body")
        wear(Wearpos.Legs, "roguesden_legs")
        wear(Wearpos.Hands, "roguesden_gloves")
        wear(Wearpos.Feet, "roguesden_boots")
    }

    @Test
    fun GameTestState.`picking a man's pocket gives a coin pouch and experience`() =
        runGameTest(Pickpocketing::class) {
            val man = victim("man")
            prepare(1)
            repeat(4) { random.next = 0 } // Success, then the pouch's coins.

            rob(man)
            advanceUntilMessage("You attempt to pick the man's pocket.", timeoutTicks = 3)
            advanceUntilMessage("You pick the man's pocket.")

            check(player.count(obj("pickpocket_coin_pouch_citizen")) == 1) { "Expected a pouch." }
            check(fineXp() == 8.0) { "Expected 8 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a target needs its thieving level`() =
        runGameTest(Pickpocketing::class) {
            val guard = victim("guard")
            prepare(39)

            rob(guard)
            advanceUntilMessage("You need a Thieving level of 40 to pickpocket the guard.", 3)
            check(player.inv.isEmpty()) { "Expected nothing taken." }
        }

    @Test
    fun GameTestState.`getting caught stuns the thief and hurts`() =
        runGameTest(Pickpocketing::class, PlayerHitScript::class) {
            val man = victim("man")
            prepare(1)
            random.next = 100 // Fail.
            random.next = 1 // The stun damage.

            rob(man)
            advanceUntilMessage("You fail to pick the man's pocket.")
            check(player.isFrozen) { "Expected the thief to be stunned." }
            advanceUntilMessage("You've been stunned!", timeoutTicks = 4)
            advance(ticks = 2)
            check(player.stats[stats.hitpoints] == 98) {
                "Expected 1 damage, hitpoints were ${player.stats[stats.hitpoints]}."
            }
            check(fineXp() == 0.0) { "Expected no experience." }
        }

    @Test
    fun GameTestState.`a stunned thief cannot pickpocket again straight away`() =
        runGameTest(Pickpocketing::class, PlayerHitScript::class) {
            val man = victim("man")
            prepare(1)
            random.next = 100

            rob(man)
            advanceUntilMessage("You fail to pick the man's pocket.")
            advance(ticks = 2)

            rob(man)
            advance(ticks = 3)
            assertMessageNotSent("You attempt to pick the man's pocket.")

            advance(ticks = 10)
            repeat(4) { random.next = 0 }
            rob(man)
            advanceUntilMessage("You attempt to pick the man's pocket.", timeoutTicks = 3)
        }

    @Test
    fun GameTestState.`the full rogue set doubles the loot`() =
        runGameTest(Pickpocketing::class) {
            val man = victim("man")
            prepare(1)
            rogueSet()
            repeat(4) { random.next = 0 }

            rob(man)
            advanceUntilMessage("You pick the man's pocket.")

            // A doubled pouch is the pouch and the coins in it.
            check(player.count(obj("pickpocket_coin_pouch_citizen")) == 1) { "Expected a pouch." }
            check(player.count(obj("coins")) == 3) { "Expected the pouch's 3 coins as well." }
        }

    @Test
    fun GameTestState.`Dodgy Deals robs the neighbours too and notes the loot`() =
        runGameTest(Pickpocketing::class, DodgyDealsStub::class) {
            val first = victim("man")
            spawnNpc(coords.translateZ(2), first.type)
            spawnNpc(coords.translateX(20), first.type)
            prepare(1)
            repeat(12) { random.next = 0 }

            rob(first)
            advanceUntilMessage("You pick the man's pocket.")

            val pouches = player.count(obj("pickpocket_coin_pouch_citizen"))
            check(pouches == 2) {
                "Expected a pouch from the man and from the one near him, was $pouches."
            }
            check(fineXp() == 8.0) { "Expected one pickpocket's experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`coin pouches pile up to 28 before they block the thief`() =
        runGameTest(Pickpocketing::class) {
            val man = victim("man")
            prepare(1, "pickpocket_coin_pouch_citizen" to 28)

            rob(man)
            advanceUntilMessage(
                "You have too many coin pouches. Open them before pickpocketing again.",
                timeoutTicks = 3,
            )
        }

    @Test
    fun GameTestState.`Dodgy Deals lets 84 coin pouches pile up`() =
        runGameTest(Pickpocketing::class, DodgyDealsStub::class) {
            val man = victim("man")
            prepare(1, "pickpocket_coin_pouch_citizen" to 28)
            repeat(4) { random.next = 0 }

            rob(man)
            advanceUntilMessage("You pick the man's pocket.")
            check(player.count(obj("pickpocket_coin_pouch_citizen")) == 29)
        }

    @Test
    fun GameTestState.`a tzhaar needs a fire cape`() =
        runGameTest(Pickpocketing::class) {
            val hur = victim("tzhaar_hur")
            prepare(90)

            rob(hur)
            advanceUntilMessage(
                "You need a fire cape to pickpocket the TzHaar-Hur.",
                timeoutTicks = 3,
            )

            prepare(90, "tzhaar_cape_fire" to 1)
            rob(hur)
            advanceUntilMessage("You attempt to pick the TzHaar-Hur's pocket.", timeoutTicks = 3)
        }

    @Test
    fun GameTestState.`gloves of silence wear out after 62 caught pickpockets`() =
        runGameTest(Pickpocketing::class, PlayerHitScript::class) {
            val man = victim("man")
            prepare(1)
            wear(Wearpos.Hands, "hunting_silent_gloves")
            player.withProtectedAccess { vars[ThievingVarps.gloves_fails] = 61 }
            advance()
            random.next = 100

            rob(man)
            advanceUntilMessage("Your gloves of silence have worn out.")
            check(player.worn[Wearpos.Hands.slot] == null) { "Expected the gloves to be gone." }
        }

    @Test
    fun GameTestState.`a dodgy necklace can stop the stun and the damage`() =
        runGameTest(Pickpocketing::class, PlayerHitScript::class) {
            val man = victim("man")
            prepare(1)
            wear(Wearpos.Front, "dodgy_necklace")
            random.next = 100 // Fail.
            random.next = 0 // The necklace works.

            rob(man)
            advanceUntilMessage("Your dodgy necklace protects you. It has 9 charges left.")
            check(!player.isFrozen) { "Expected no stun." }
            advance(ticks = 3)
            check(player.stats[stats.hitpoints] == 99) { "Expected no damage." }
        }

    @Test
    fun GameTestState.`coin pouches open into coins`() =
        runGameTest(Pickpocketing::class, CoinPouches::class, HeldOpScript::class) {
            prepare(1, "pickpocket_coin_pouch_citizen" to 5)
            val slot = player.inv.indexOfFirst { it?.id == obj("pickpocket_coin_pouch_citizen").id }
            val type = objTypes[checkNotNull(player.inv[slot])]

            // Open-all is the first option, so the second button option.
            eventBus.publish(
                IfOverlayButton(player, components.inv_items, slot, type, IfButtonOp.Op2)
            )
            advance()

            check(player.count(obj("pickpocket_coin_pouch_citizen")) == 0) {
                "Expected no pouches."
            }
            check(player.count(obj("coins")) == 15) { "Expected 5 pouches of 3 coins." }
        }

    private companion object {
        const val WORN_SLOTS = 14
        const val QUIET_TICKS = 12
    }
}
