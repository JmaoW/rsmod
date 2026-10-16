package org.rsmod.content.areas.misc.isleofsouls.relics

import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.hunter.scripts.HunterTraps
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.map.CoordGrid

@OptIn(InternalApi::class)
class LeagueHunterRelicsFlowTest {
    private val coords = CoordGrid(0, 10, 10, 20, 20)

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 40) {
        repeat(timeoutTicks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) return
        }
        assertMessageSent(text)
    }

    @Test
    fun GameTestState.`a snare laid with the real relic catches even on a failing roll`() =
        runGameTest(HunterTraps::class, HeldOpScript::class, LeagueHunterRelics::class) {
            for (dx in -3..3) for (dz in -3..3) allocZoneCollision(coords.translate(dx * 8, dz * 8))
            player.observerUUID = player.uuid
            player.teleport(coords)
            player.clearInv()
            VarPlayerIntMapSetter.set(
                player,
                RelicVarBits.selection[RelicItem.AnimalWrangler.tier],
                RelicItem.AnimalWrangler.option,
            )
            player.inv[0] = InvObj(RelicItem.AnimalWrangler.item)
            player.inv[1] =
                InvObj(objTypes.values.first { it.internalName == "hunting_ojibway_bird_snare" })
            player.stats[stats.hunter] = 1
            advance(ticks = 12)
            val type = objTypes[checkNotNull(player.inv[1])]
            eventBus.publish(IfOverlayButton(player, components.inv_items, 1, type, IfButtonOp.Op2))
            advanceUntilMessage("You set up the bird snare.", 8)
            spawnNpc(
                coords.translate(1, 0),
                npcTypes.values.first { it.internalName == "hunting_bird_jungle" },
            ) {
                noneMode()
            }
            random.next = 99
            advanceUntilMessage("Something has been caught in your trap!")
        }
}
