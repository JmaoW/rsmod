package org.rsmod.content.skills.prayer.scripts

import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

@OptIn(InternalApi::class)
class BuryingTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(vararg bones: String) {
        player.teleport(coords)
        player.clearInv()
        bones.forEachIndexed { slot, name -> player.inv[slot] = InvObj(obj(name)) }
        player.xpRate = 1.0
    }

    /** Clicking "Bury" is the first inventory option, which is the second button option. */
    private fun GameTestScope.bury(slot: Int) {
        val type = objTypes[checkNotNull(player.inv[slot])]
        eventBus.publish(IfOverlayButton(player, components.inv_items, slot, type, IfButtonOp.Op2))
    }

    private fun GameTestScope.prayerXp() = player.statMap.getXP(stats.prayer)

    @Test
    fun GameTestState.`burying dragon bones uses the bones and gives 72 xp`() =
        runGameTest(Burying::class, HeldOpScript::class) {
            prepare("dragon_bones")
            check(prayerXp() == 0) { "Expected no prayer xp to start with." }

            bury(0)
            assertMessageSent("You dig a hole in the ground...")
            check(player.count(obj("dragon_bones")) == 0) { "Expected the bones to be used." }
            check(prayerXp() == 72) { "Expected 72 xp, got ${prayerXp()}." }

            advance(ticks = 1)
            assertMessageSent("You bury the dragon bones.")
        }

    @Test
    fun GameTestState.`burying plain bones gives 4 and a half xp`() =
        runGameTest(Burying::class, HeldOpScript::class) {
            prepare("bones", "bones")

            bury(0)
            advance(ticks = 2)
            bury(1)
            advance(ticks = 1)

            check(player.count(obj("bones")) == 0) { "Expected both bones to be used." }
            check(prayerXp() == 9) { "Expected 9 xp, got ${prayerXp()}." }
        }

    @Test
    fun GameTestState.`xp modifiers apply to burying`() =
        runGameTest(Burying::class, HeldOpScript::class) {
            prepare("dragon_bones")
            player.xpRate = 2.0

            bury(0)
            advance(ticks = 1)

            check(prayerXp() == 144) { "Expected 144 xp at double rate, got ${prayerXp()}." }
        }

    @Test
    fun GameTestState.`bones cannot be buried faster than every two ticks`() =
        runGameTest(Burying::class, HeldOpScript::class) {
            prepare("bones", "bones", "bones")

            bury(0)
            check(!player.isDelayed) { "Expected the player to never be held in place." }
            bury(1)
            check(player.count(obj("bones")) == 2) { "Expected the second click to be ignored." }

            advance(ticks = 2)
            bury(1)
            check(player.count(obj("bones")) == 1) { "Expected the bone to be buried by now." }
        }
}
