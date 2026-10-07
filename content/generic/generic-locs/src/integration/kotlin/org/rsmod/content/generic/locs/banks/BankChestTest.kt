package org.rsmod.content.generic.locs.banks

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.map.CoordGrid

class BankChestTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.placeChest(type: UnpackedLocType): BoundLocInfo {
        val chest = placeMapLoc(coords, type)
        player.teleport(chest.coords.translateX(-1))
        return chest
    }

    @Test
    fun GameTestState.`using a bank chest opens the bank`() =
        runGameTest(BankChest::class) {
            val type = locTypes.values.first { it.internalName == "soul_wars_bankchest" }
            val chest = placeChest(type)

            player.opLoc1(chest)
            advance(ticks = 3)

            check(player.ui.containsModal(interfaces.bank_main)) { "Bank should be open." }
        }

    @Test
    fun GameTestState.`every usable bank chest in the cache is a bank chest`() =
        runGameTest(BankChest::class) {
            val usable =
                locTypes.values.filter {
                    it.name.equals("Bank chest", ignoreCase = true) &&
                        it.op.getOrNull(0).let { op -> op == "Use" || op == "Bank" }
                }
            check(usable.isNotEmpty()) { "Expected bank chest locs in the cache." }
            val missing = usable.filterNot { it.isContentType(content.bank_chest) }
            check(missing.isEmpty()) {
                "Bank chests without the bank_chest content group: " +
                    missing.joinToString { it.internalName ?: it.id.toString() }
            }
        }
}
