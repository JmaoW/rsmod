package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos

class DungeonGodItemsTest {
    class Deps @Inject constructor(val items: GodItems)

    private fun GameTestState.runItemsTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, null, GodItemsScript::class) { deps -> body(deps) }

    private fun GameTestScope.wear(pos: Wearpos, internal: String) {
        val type = objTypes.values.first { it.internalName == internal }
        player.worn[pos.slot] = InvObj(type)
        eventBus.publish(HeldEquipEvents.WearposChange(player, pos, type))
    }

    private fun GameTestScope.remove(pos: Wearpos) {
        val type = objTypes[player.worn[pos.slot]!!]
        player.worn[pos.slot] = null
        eventBus.publish(HeldEquipEvents.WearposChange(player, pos, type))
    }

    private fun GameTestScope.aligned(): Set<God> =
        God.entries.filterTo(mutableSetOf()) { player.vars[it.aligned] == 1 }

    @Test
    fun GameTestState.`every listed item is in the cache`() = runItemsTest { _ ->
        val internalNames = objTypes.values.mapNotNullTo(hashSetOf()) { it.internalName }
        for (god in God.entries) {
            val names = GodItemNames.of(god)
            assertEquals(names.toSet().size, names.size, "${god.title} list has duplicates")
            val missing = names.filter { it !in internalNames }
            assertTrue(missing.isEmpty(), "${god.title} items not in the cache: $missing")
        }
    }

    @Test
    fun GameTestState.`wearing one item aligns only its god and taking it off clears it`() =
        runItemsTest { _ ->
            assertEquals(emptySet<God>(), aligned())

            wear(Wearpos.Back, "saradomin_cape")
            assertEquals(setOf(God.SARADOMIN), aligned())

            remove(Wearpos.Back)
            assertEquals(emptySet<God>(), aligned())
        }

    @Test
    fun GameTestState.`each god's items align that god`() = runItemsTest { _ ->
        wear(Wearpos.Back, "zamorak_cape")
        assertEquals(setOf(God.ZAMORAK), aligned())

        wear(Wearpos.Torso, "bandos_chestplate")
        assertEquals(setOf(God.ZAMORAK, God.BANDOS), aligned())

        wear(Wearpos.Hat, "armadyl_helmet")
        assertEquals(setOf(God.ZAMORAK, God.BANDOS, God.ARMADYL), aligned())

        wear(Wearpos.Feet, "blessed_boots_saradomin")
        assertEquals(God.entries.toSet(), aligned())
    }

    @Test
    fun GameTestState.`a god with two items stays aligned until both are off`() =
        runItemsTest { _ ->
            wear(Wearpos.Back, "saradomin_cape")
            wear(Wearpos.Hat, "rune_full_helm_saradomin")
            remove(Wearpos.Back)
            assertEquals(setOf(God.SARADOMIN), aligned())
            remove(Wearpos.Hat)
            assertEquals(emptySet<God>(), aligned())
        }

    @Test
    fun GameTestState.`the staff of light counts for both saradomin and zamorak`() =
        runItemsTest { _ ->
            wear(Wearpos.RightHand, "staff_of_light")
            assertEquals(setOf(God.SARADOMIN, God.ZAMORAK), aligned())
        }

    @Test
    fun GameTestState.`ordinary gear and other gods' lookalikes do not align anyone`() =
        runItemsTest { _ ->
            wear(Wearpos.Back, "tzhaar_cape_fire")
            wear(Wearpos.Torso, "rune_platebody")
            wear(Wearpos.Hat, "rune_full_helm")
            wear(Wearpos.RightHand, "abyssal_whip")
            assertEquals(emptySet<God>(), aligned())
        }

    @Test
    fun GameTestState.`a sync fixes the switches when the worn items changed without an event`() =
        runItemsTest { deps ->
            player.worn[Wearpos.Back.slot] =
                InvObj(objTypes.values.first { it.internalName == "armadyl_helmet" })
            assertEquals(emptySet<God>(), aligned())

            deps.items.sync(player)

            assertEquals(setOf(God.ARMADYL), aligned())
        }
}
