package org.rsmod.content.other.drops

import jakarta.inject.Inject
import kotlin.reflect.KClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.config.refs.objs
import org.rsmod.api.death.plugin.NpcDeathScript
import org.rsmod.api.drops.DropEntry
import org.rsmod.api.drops.DropOutcome
import org.rsmod.api.drops.DropStack
import org.rsmod.api.drops.DropTable
import org.rsmod.api.drops.DropTables
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.script.onEvent
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NpcDropsTest {
    class Deps @Inject constructor(val dropTables: DropTables)

    private fun GameTestState.runDropTest(
        vararg extra: KClass<out PluginScript>,
        body: GameTestScope.(Deps) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            null,
            NpcHitScript::class,
            NpcDeathScript::class,
            *extra,
            testBody = body,
        )

    /** Makes every drop table roll certain to land on its chance-based entries. */
    class GuaranteedDrops : PluginScript() {
        override fun ScriptContext.startup() {
            onEvent<NpcDropEvents.Modify> { multiplier = 10_000.0 }
        }
    }

    /** Kills a fresh [goblin] next to the player and returns where it died. */
    private fun GameTestScope.killGoblin(): CoordGrid {
        val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), goblin)
        player.observerUUID = player.uuid
        player.placeAt(npc.coords.translateZ(1))
        val coords = npc.coords

        npc.heroPoints(player, goblin.hitpoints)
        npc.queueCombatRetaliate(player, delay = 1)
        npc.queueHit(player, delay = 1, HitType.Melee, damage = goblin.hitpoints)
        advanceUntil({ !npc.isSlotAssigned }, timeoutTicks = 50) {
            "slot=${npc.isSlotAssigned} delayed=${npc.isDelayed} hp=${npc.hitpoints} mode=${npc.mode} heroes=${npc.heroPoints}"
        }
        return coords
    }

    private fun GameTestScope.groundCount(coords: CoordGrid, obj: ObjType) =
        objRepo.findAll(coords).filter { it.type == obj.id }.sumOf { it.count }

    @Test
    fun GameTestState.`a kill hands out the always and chance drops of its table`() =
        runDropTest { deps ->
            val table =
                DropTable(
                    name = "test goblin",
                    always = listOf(DropStack(objs.bones, 1, 1)),
                    main = listOf(DropEntry(1.0, DropOutcome.Item(DropStack(objs.logs, 5, 5)))),
                )
            deps.dropTables.replaceAll(mapOf(goblin.id to table))

            val coords = killGoblin()

            assertEquals(1, groundCount(coords, objs.bones))
            assertEquals(5, groundCount(coords, objs.logs))
        }

    @Test
    fun GameTestState.`a monster without a drop table still drops bones`() = runDropTest { deps ->
        deps.dropTables.replaceAll(emptyMap())

        val coords = killGoblin()

        assertEquals(1, groundCount(coords, objs.bones))
        assertEquals(0, groundCount(coords, objs.logs))
    }

    @Test
    fun GameTestState.`subscribers can change the drop multiplier`() =
        runDropTest(GuaranteedDrops::class) { deps ->
            // A boosted 1/1000 entry can only land if the multiplier pushes its chance to 100%. An
            // entry that is not boosted stays at 1/1000.
            val table =
                DropTable(
                    name = "test goblin",
                    tertiary =
                        listOf(
                            DropEntry(0.001, DropOutcome.Item(DropStack(objs.logs, 1, 1)), true),
                            DropEntry(0.001, DropOutcome.Item(DropStack(objs.coins, 1, 1))),
                        ),
                )
            deps.dropTables.replaceAll(mapOf(goblin.id to table))

            val coords = killGoblin()

            assertEquals(1, groundCount(coords, objs.logs))
            assertEquals(0, groundCount(coords, objs.coins))
        }

    private companion object {
        private val goblin =
            npcTypeFactory.create {
                name = "Test goblin"
                op[1] = "Attack"
                size = 1
                hitpoints = 7
                defaultMode = NpcMode.None
            }
    }
}
