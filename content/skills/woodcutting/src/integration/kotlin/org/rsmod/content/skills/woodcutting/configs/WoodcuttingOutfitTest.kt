package org.rsmod.content.skills.woodcutting.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos

class WoodcuttingOutfitTest {
    private val mod = WoodcuttingOutfitXpMod()

    private fun GameTestState.runOutfitTest(body: GameTestScope.() -> Unit) = runGameTest { body() }

    private fun GameTestScope.wear(slot: Wearpos, obj: ObjType) {
        player.worn[slot.slot] = InvObj(obj)
    }

    private fun GameTestScope.bonus(): Double = with(mod) { player.modifier(stats.woodcutting) }

    @Test
    fun GameTestState.`the lumberjack outfit gives 2 and a half percent as a set`() =
        runOutfitTest {
            assertEquals(0.0, bonus(), 1e-9)
            wear(Wearpos.Hat, objs.lumberjack_hat)
            assertEquals(0.004, bonus(), 1e-9)
            wear(Wearpos.Torso, objs.lumberjack_top)
            assertEquals(0.012, bonus(), 1e-9)
            wear(Wearpos.Legs, objs.lumberjack_legs)
            assertEquals(0.018, bonus(), 1e-9)
            wear(Wearpos.Feet, objs.lumberjack_boots)
            assertEquals(0.025, bonus(), 1e-9)
        }

    @Test
    fun GameTestState.`the forestry outfit gives the same bonus`() = runOutfitTest {
        wear(Wearpos.Hat, objs.forestry_hat)
        wear(Wearpos.Torso, objs.forestry_top)
        wear(Wearpos.Legs, objs.forestry_legs)
        wear(Wearpos.Feet, objs.forestry_boots)
        assertEquals(0.025, bonus(), 1e-9)
    }

    @Test
    fun GameTestState.`mixed outfits get their pieces but no set bonus`() = runOutfitTest {
        wear(Wearpos.Hat, objs.lumberjack_hat)
        wear(Wearpos.Torso, objs.forestry_top)
        wear(Wearpos.Legs, objs.lumberjack_legs)
        wear(Wearpos.Feet, objs.forestry_boots)
        assertEquals(0.020, bonus(), 1e-9)
    }

    @Test
    fun GameTestState.`only woodcutting benefits`() = runOutfitTest {
        wear(Wearpos.Hat, objs.lumberjack_hat)
        assertEquals(0.0, with(mod) { player.modifier(stats.mining) }, 1e-9)
    }
}
