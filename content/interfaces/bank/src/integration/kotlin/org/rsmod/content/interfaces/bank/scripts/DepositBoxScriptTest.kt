package org.rsmod.content.interfaces.bank.scripts

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.vars.enumVarBit
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.interfaces.bank.DepositQuantity
import org.rsmod.content.interfaces.bank.configs.DepositBoxComponents
import org.rsmod.content.interfaces.bank.configs.DepositBoxInterfaces
import org.rsmod.content.interfaces.bank.configs.DepositBoxVarBits
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.util.UncheckedType

/* Obj transaction system is not thread-safe. */
@Execution(ExecutionMode.SAME_THREAD)
@OptIn(UncheckedType::class)
class DepositBoxScriptTest {
    private var Player.depositMode by enumVarBit<DepositQuantity>(DepositBoxVarBits.quantity_mode)

    private fun GameTestState.openBox(player: Player) {
        player.ifOpenMainModal(DepositBoxInterfaces.deposit_box, eventBus)
    }

    @Test
    fun GameTestState.`opening the box shows the interface`() =
        runGameTest(DepositBoxScript::class, BankInvScript::class) {
            openBox(player)
            check(player.ui.containsModal(DepositBoxInterfaces.deposit_box))
        }

    @Test
    fun GameTestState.`deposit one, five and all from an inventory slot`() =
        runGameTest(DepositBoxScript::class, BankInvScript::class) {
            val bank = bank(player)
            openBox(player)
            player.inv[3] = InvObj(2, 100)

            player.ifButton(DepositBoxComponents.inventory, comsub = 3, op = IfButtonOp.Op2)
            advance()
            assertEquals(InvObj(2, 1), bank[0])
            assertEquals(InvObj(2, 99), player.inv[3])

            player.ifButton(DepositBoxComponents.inventory, comsub = 3, op = IfButtonOp.Op3)
            advance()
            assertEquals(InvObj(2, 6), bank[0])

            player.ifButton(DepositBoxComponents.inventory, comsub = 3, op = IfButtonOp.Op4)
            advance()
            assertEquals(InvObj(2, 16), bank[0])

            player.ifButton(DepositBoxComponents.inventory, comsub = 3, op = IfButtonOp.Op6)
            advance()
            assertEquals(InvObj(2, 100), bank[0])
            assertNull(player.inv[3])
        }

    @Test
    fun GameTestState.`left click follows the chosen amount button`() =
        runGameTest(DepositBoxScript::class, BankInvScript::class) {
            val bank = bank(player)
            openBox(player)
            player.inv[0] = InvObj(2, 100)

            player.ifButton(DepositBoxComponents.quantity_5)
            advance()
            assertEquals(DepositQuantity.Five, player.depositMode)
            player.ifButton(DepositBoxComponents.inventory, comsub = 0, op = IfButtonOp.Op1)
            advance()
            assertEquals(InvObj(2, 5), bank[0])

            player.ifButton(DepositBoxComponents.quantity_10)
            advance()
            assertEquals(DepositQuantity.Ten, player.depositMode)
            player.ifButton(DepositBoxComponents.inventory, comsub = 0, op = IfButtonOp.Op1)
            advance()
            assertEquals(InvObj(2, 15), bank[0])

            player.ifButton(DepositBoxComponents.quantity_all)
            advance()
            assertEquals(DepositQuantity.All, player.depositMode)
            player.ifButton(DepositBoxComponents.inventory, comsub = 0, op = IfButtonOp.Op1)
            advance()
            assertEquals(InvObj(2, 100), bank[0])
            assertNull(player.inv[0])
        }

    @Test
    fun GameTestState.`deposit inventory and worn buttons`() =
        runGameTest(DepositBoxScript::class, BankInvScript::class) {
            val bank = bank(player)
            openBox(player)
            player.inv[0] = InvObj(1, 1)
            player.inv[5] = InvObj(2, 50)
            player.worn[3] = InvObj(3, 1)
            player.worn[12] = InvObj(4, 1)

            player.ifButton(DepositBoxComponents.deposit_inventory)
            advance()
            assertEquals(2, bank.occupiedSpace())
            assertEquals(0, player.inv.occupiedSpace())

            player.ifButton(DepositBoxComponents.deposit_worn)
            advance()
            assertEquals(4, bank.occupiedSpace())
            assertEquals(0, player.worn.occupiedSpace())
        }

    @Test
    fun GameTestState.`worn slot deposits with bank and removes with remove`() =
        runGameTest(DepositBoxScript::class, BankInvScript::class) {
            val bank = bank(player)
            openBox(player)
            val ringType = objTypes.values.first { it.wearpos1 == 12 && !it.isStackable }
            player.worn[12] = InvObj(ringType.id, 1)

            val ring = DepositBoxComponents.worn_slots.getValue(12)
            player.ifButton(ring, op = IfButtonOp.Op2)
            advance()
            assertEquals(InvObj(ringType.id, 1), bank[0])
            assertNull(player.worn[12])
        }
}
