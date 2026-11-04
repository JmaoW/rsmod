package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.stat.ProductionBatch
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.herblore.scripts.Herblore
import org.rsmod.content.skills.herblore.scripts.HerbloreModifiers
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class LeagueProductionMasterTest {
    class Mods @Inject constructor(val modifiers: HerbloreModifiers)

    @AfterEach
    fun removeRule() {
        ProductionBatch.rule = null
    }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.choose(chosen: Boolean) {
        val option = if (chosen) LeagueProductionMaster.OPTION else 0
        VarPlayerIntMapSetter.set(
            player,
            RelicVarBits.selection[LeagueProductionMaster.TIER],
            option,
        )
    }

    private fun GameTestScope.count(internal: String): Int =
        player.inv.count { it?.id == obj(internal).id }

    private fun GameTestScope.cleanFirstHerb() {
        player.teleport(CoordGrid(0, 50, 50, 34, 31))
        player.clearInv()
        player.stats[stats.herblore] = 99
        for (slot in 0 until 5) {
            player.inv[slot] = InvObj(obj("unidentified_guam"))
        }
        val grimy = obj("unidentified_guam")
        eventBus.publish(IfOverlayButton(player, components.inv_items, 0, grimy, IfButtonOp.Op2))
        advance(1)
    }

    @Test
    fun GameTestState.`with the relic every herb of the kind is cleaned at once`() =
        runInjectedGameTest(
            Mods::class,
            null,
            LeagueHerblore::class,
            LeagueProductionMaster::class,
            Herblore::class,
            HeldOpScript::class,
        ) {
            choose(chosen = true)
            cleanFirstHerb()
            assertEquals(0, count("unidentified_guam"))
            assertEquals(5, count("guam_leaf"))
        }

    @Test
    fun GameTestState.`without the relic one herb is cleaned`() =
        runInjectedGameTest(
            Mods::class,
            null,
            LeagueHerblore::class,
            LeagueProductionMaster::class,
            Herblore::class,
            HeldOpScript::class,
        ) {
            choose(chosen = false)
            cleanFirstHerb()
            assertEquals(4, count("unidentified_guam"))
            assertEquals(1, count("guam_leaf"))
        }

    @Test
    fun GameTestState.`the batch covers the rest of a job up to the cap`() =
        runGameTest(LeagueProductionMaster::class) {
            choose(chosen = true)
            assertEquals(14, ProductionBatch.actions(player, 14))
            assertEquals(10, ProductionBatch.actions(player, 14, ProductionBatch.AMMO_CAP))
            choose(chosen = false)
            assertEquals(1, ProductionBatch.actions(player, 14))
        }
}
