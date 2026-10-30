package org.rsmod.content.generic.locs.banks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.invs
import org.rsmod.api.inv.LocUOpScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.interfaces.bank.configs.DepositBoxInterfaces
import org.rsmod.content.interfaces.bank.scripts.BankInvScript
import org.rsmod.content.interfaces.bank.scripts.DepositBoxScript
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.util.UncheckedType
import org.rsmod.map.CoordGrid

/* Obj transaction system is not thread-safe. */
@Execution(ExecutionMode.SAME_THREAD)
@OptIn(UncheckedType::class)
class DepositBoxTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.placeBox(internal: String): BoundLocInfo {
        val type = locTypes.values.first { it.internalName == internal }
        val box = placeMapLoc(coords, type)
        player.teleport(box.coords.translateX(-1))
        return box
    }

    @Test
    fun GameTestState.`using the gauntlet deposit box opens the deposit box`() =
        runGameTest(DepositBox::class, DepositBoxScript::class, BankInvScript::class) {
            val box = placeBox("gauntlet_deposit_box")

            player.opLoc1(box)
            advance(ticks = 3)

            check(player.ui.containsModal(DepositBoxInterfaces.deposit_box)) {
                "Deposit box should be open."
            }
        }

    @Test
    fun GameTestState.`using an item on a deposit box deposits all of it`() =
        runGameTest(
            DepositBox::class,
            DepositBoxScript::class,
            BankInvScript::class,
            LocUOpScript::class,
        ) {
            val box = placeBox("bank_deposit_box")
            val bank = player.invMap.getOrPut(cacheTypes.invs[invs.bank])
            val stackable = objTypes.values.first { it.isStackable }
            val other = objTypes.values.first { !it.isStackable && it.id != stackable.id }
            player.inv[2] = InvObj(stackable.id, 40)
            player.inv[4] = InvObj(other.id, 1)

            player.opLocT(box, stackable, 2)
            advance(ticks = 3)

            assertEquals(40, bank[0]?.count) // The bank may store the uncertified form.
            assertEquals(1, bank.occupiedSpace())
            assertNull(player.inv[2])
            assertEquals(InvObj(other.id, 1), player.inv[4])
        }

    @Test
    fun GameTestState.`every bank deposit box in the cache is a deposit box`() =
        runGameTest(DepositBox::class) {
            val boxes =
                locTypes.values.filter { isBankDepositName(it) && it.op.getOrNull(0) == "Deposit" }
            check(boxes.isNotEmpty()) { "Expected deposit box locs in the cache." }
            val missing = boxes.filterNot { it.isContentType(content.bank_deposit_box) }
            check(missing.isEmpty()) {
                "Deposit boxes without the bank_deposit_box content group: " +
                    missing.joinToString { it.internalName ?: it.id.toString() }
            }
        }

    private fun isBankDepositName(type: UnpackedLocType): Boolean {
        val name = type.name?.lowercase() ?: return false
        return name == "bank deposit box" ||
            name == "bank deposit chest" ||
            name == "bank deposit pot"
    }
}
