package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos

/** The singing bowl on the isle and the league tasks for crystal gear. */
class CrystalSingingTest {
    private object CrystalModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val singing: CrystalSinging,
        val launcher: ProtectedAccessLauncher,
        val objTypes: ObjTypeList,
        val tracker: LeagueTaskTracker,
    )

    private fun GameTestState.runCrystalTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, CrystalModule, CrystalGearTasks::class) { deps ->
            player.stats[stats.crafting] = 99
            player.stats[stats.smithing] = 99
            body(deps)
        }

    private fun GameTestScope.give(obj: ObjType, count: Int) {
        player.invAdd(player.inv, obj, count)
    }

    private fun GameTestScope.count(deps: Deps, obj: ObjType) = player.inv.count(deps.objTypes[obj])

    private fun GameTestScope.sing(deps: Deps, recipe: CrystalRecipe): Boolean {
        var made = false
        with(deps.singing) { deps.launcher.launch(player) { made = make(recipe) } }
        advance()
        return made
    }

    private fun recipe(product: ObjType) = CrystalRecipes.all.first { it.product.id == product.id }

    @Test
    fun GameTestState.`the recipes are the wiki ones`() = runCrystalTest { _ ->
        val shard = CrystalObjs.shard.id
        fun shards(product: ObjType) = recipe(product).inputs.first { it.first.id == shard }.second
        assertEquals(50, shards(CrystalObjs.helmet))
        assertEquals(100, shards(CrystalObjs.platelegs))
        assertEquals(150, shards(CrystalObjs.chestplate))
        assertEquals(
            3,
            recipe(CrystalObjs.chestplate)
                .inputs
                .first { it.first.id == CrystalObjs.armour_seed.id }
                .second,
        )
        assertEquals(40, shards(CrystalObjs.bow))
        assertEquals(120, shards(CrystalObjs.pickaxe))
        assertEquals(100, shards(CrystalObjs.blade_of_saeldor))
        assertEquals(70, recipe(CrystalObjs.helmet).level)
        assertEquals(78, recipe(CrystalObjs.shield).level)
        assertEquals(82, recipe(CrystalObjs.bow_of_faerdhinen).level)
        assertEquals(7_500.0, recipe(CrystalObjs.chestplate).xp)
    }

    @Test
    fun GameTestState
        .`singing a helmet takes a seed and fifty shards and gives xp in both skills`() =
        runCrystalTest { deps ->
            give(CrystalObjs.armour_seed, 1)
            give(CrystalObjs.shard, 60)
            val crafting = player.statMap.getFineXP(stats.crafting)
            val smithing = player.statMap.getFineXP(stats.smithing)

            assertTrue(sing(deps, recipe(CrystalObjs.helmet)))

            assertEquals(1, count(deps, CrystalObjs.helmet))
            assertEquals(0, count(deps, CrystalObjs.armour_seed))
            assertEquals(10, count(deps, CrystalObjs.shard))
            assertTrue(player.statMap.getFineXP(stats.crafting) > crafting)
            assertTrue(player.statMap.getFineXP(stats.smithing) > smithing)
            assertTrue(deps.tracker.isComplete(player, CrystalSinging.TASK_CRAFT_ARMOUR))
        }

    @Test
    fun GameTestState.`a body takes three seeds`() = runCrystalTest { deps ->
        give(CrystalObjs.armour_seed, 2)
        give(CrystalObjs.shard, 150)
        assertFalse(sing(deps, recipe(CrystalObjs.chestplate)))
        assertEquals(2, count(deps, CrystalObjs.armour_seed))
        give(CrystalObjs.armour_seed, 1)
        assertTrue(sing(deps, recipe(CrystalObjs.chestplate)))
        assertEquals(1, count(deps, CrystalObjs.chestplate))
    }

    @Test
    fun GameTestState.`the levels are needed and a tool needs its dragon tool and seed`() =
        runCrystalTest { deps ->
            give(CrystalObjs.weapon_seed, 1)
            give(CrystalObjs.shard, 40)
            player.stats[stats.smithing] = 77
            assertFalse(sing(deps, recipe(CrystalObjs.bow)))
            assertEquals(1, count(deps, CrystalObjs.weapon_seed))

            player.stats[stats.smithing] = 78
            assertTrue(sing(deps, recipe(CrystalObjs.bow)))

            give(CrystalObjs.tool_seed, 1)
            give(CrystalObjs.shard, 120)
            assertFalse(sing(deps, recipe(CrystalObjs.pickaxe)), "no dragon pickaxe")
            give(CrystalObjs.dragon_pickaxe, 1)
            assertTrue(sing(deps, recipe(CrystalObjs.pickaxe)))
            assertEquals(1, count(deps, CrystalObjs.pickaxe))
            assertEquals(0, count(deps, CrystalObjs.dragon_pickaxe))
        }

    @Test
    fun GameTestState.`the bowl offers only what the player holds the seed for`() =
        runCrystalTest { deps ->
            var none = false
            with(deps.singing) { deps.launcher.launch(player) { none = offered().isEmpty() } }
            advance()
            assertTrue(none)

            give(CrystalObjs.weapon_seed, 1)
            give(CrystalObjs.armour_seed, 1)
            var names = emptyList<Int>()
            with(deps.singing) {
                deps.launcher.launch(player) { names = offered().map { it.product.id } }
            }
            advance()
            assertEquals(
                setOf(
                    CrystalObjs.helmet.id,
                    CrystalObjs.bow.id,
                    CrystalObjs.halberd.id,
                    CrystalObjs.shield.id,
                ),
                names.toSet(),
            )
        }

    @Test
    fun GameTestState.`wearing crystal gear completes the league equip tasks`() =
        runCrystalTest { deps ->
            fun wear(pos: Wearpos, obj: ObjType) {
                player.worn[pos.slot] = InvObj(obj)
                eventBus.publish(HeldEquipEvents.WearposChange(player, pos, deps.objTypes[obj]))
            }
            wear(Wearpos.RightHand, CrystalObjs.bow)
            assertTrue(deps.tracker.isComplete(player, CrystalGearTasks.TASK_BOW))
            assertFalse(deps.tracker.isComplete(player, CrystalGearTasks.TASK_ANY_ARMOUR))

            wear(Wearpos.Hat, CrystalObjs.helmet)
            assertTrue(deps.tracker.isComplete(player, CrystalGearTasks.TASK_ANY_ARMOUR))
            assertFalse(deps.tracker.isComplete(player, CrystalGearTasks.TASK_FULL_SET))
            wear(Wearpos.Torso, CrystalObjs.chestplate)
            wear(Wearpos.Legs, CrystalObjs.platelegs)
            assertTrue(deps.tracker.isComplete(player, CrystalGearTasks.TASK_FULL_SET))

            wear(Wearpos.LeftHand, CrystalObjs.shield)
            assertTrue(deps.tracker.isComplete(player, CrystalGearTasks.TASK_SHIELD))
            wear(Wearpos.RightHand, CrystalObjs.bow_of_faerdhinen)
            assertTrue(deps.tracker.isComplete(player, CrystalGearTasks.TASK_ENHANCED))
        }
}
