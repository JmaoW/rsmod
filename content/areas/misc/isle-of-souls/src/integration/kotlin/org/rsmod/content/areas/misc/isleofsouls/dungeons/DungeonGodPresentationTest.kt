package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.type.proj.ProjAnimType
import org.rsmod.game.type.proj.ProjAnimTypeList

/** The captured animations, projectiles and sounds of the generals and their bodyguards. */
class DungeonGodPresentationTest {
    class Deps @Inject constructor(val projAnims: ProjAnimTypeList)

    private fun GameTestState.runPresentationTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, null) { deps -> body(deps) }

    private val attacks: Map<String, NpcAttack> =
        mapOf(
            "graardor smash" to GodWarsBandosScript.GRAARDOR_MELEE,
            "strongstack" to GodWarsBandosScript.STRONGSTACK_MELEE,
            "steelwill" to GodWarsBandosScript.STEELWILL_MAGIC,
            "grimspike" to GodWarsBandosScript.GRIMSPIKE_RANGED,
            "skree" to GodWarsArmadylScript.SKREE_MAGIC,
            "geerin" to GodWarsArmadylScript.GEERIN_RANGED,
            "zilyana melee" to GodWarsSaradominScript.ZILYANA_MELEE,
            "zilyana magic" to GodWarsSaradominScript.ZILYANA_MAGIC,
            "starlight" to GodWarsSaradominScript.STARLIGHT_MELEE,
            "growler" to GodWarsSaradominScript.GROWLER_MAGIC,
            "bree" to GodWarsSaradominScript.BREE_RANGED,
            "k'ril melee" to GodWarsZamorakScript.KRIL_MELEE,
            "k'ril magic" to GodWarsZamorakScript.KRIL_MAGIC,
            "k'ril smash" to GodWarsZamorakScript.KRIL_SMASH,
            "tstanon" to GodWarsZamorakScript.TSTANON_MELEE,
            "zakl'n" to GodWarsZamorakScript.ZAKLN_RANGED,
            "balfrug" to GodWarsZamorakScript.BALFRUG_MAGIC,
        )

    @Test
    fun GameTestState.`every captured attack plays its sound`() = runPresentationTest { _ ->
        for ((name, attack) in attacks) {
            assertNotNull(attack.sound, "$name has a sound")
        }
        // The slam and Flight Kilisa's slash have none in the captures.
        assertNull(GodWarsBandosScript.GRAARDOR_SLAM.sound)
        assertNull(GodWarsArmadylScript.KILISA_MELEE.sound)
    }

    @Test
    fun GameTestState.`graardor's slam is a projectile that lands with it`() =
        runPresentationTest { _ ->
            val slam = GodWarsBandosScript.GRAARDOR_SLAM
            assertNotNull(slam.projectile)
            assertEquals(GodWarsProjAnims.graardor_slam, slam.projanim)
            assertEquals(30, slam.castHeight)
        }

    @Test
    fun GameTestState.`bosses use their own projectile flights`() = runPresentationTest { _ ->
        assertEquals(GodWarsProjAnims.steelwill_sonic, GodWarsBandosScript.STEELWILL_MAGIC.projanim)
        assertEquals(GodWarsProjAnims.goblin_axe, GodWarsBandosScript.GRIMSPIKE_RANGED.projanim)
        assertEquals(GodWarsProjAnims.skree_bolt, GodWarsArmadylScript.SKREE_MAGIC.projanim)
        assertEquals(GodWarsProjAnims.geerin_spear, GodWarsArmadylScript.GEERIN_RANGED.projanim)
        assertEquals(GodWarsProjAnims.growler_spell, GodWarsSaradominScript.GROWLER_MAGIC.projanim)
        assertEquals(GodWarsProjAnims.bree_arrow, GodWarsSaradominScript.BREE_RANGED.projanim)
        assertEquals(GodWarsProjAnims.kril_flames, GodWarsZamorakScript.KRIL_MAGIC.projanim)
        assertEquals(GodWarsProjAnims.goblin_axe, GodWarsZamorakScript.ZAKLN_RANGED.projanim)
        assertEquals(GodWarsProjAnims.balfrug_fireball, GodWarsZamorakScript.BALFRUG_MAGIC.projanim)
    }

    private fun Deps.flight(type: ProjAnimType, distance: Int): Int =
        ProjAnim.calculateEndTime(projAnims[type], distance)

    @Test
    fun GameTestState.`each flight takes base plus per-tile cycles, as captured`() =
        runPresentationTest { deps ->
            // (flight, captured base, captured cycles per tile)
            val captured =
                listOf(
                    Triple(GodWarsProjAnims.graardor_slam, 60, 5),
                    Triple(GodWarsProjAnims.steelwill_sonic, 46, 10),
                    Triple(GodWarsProjAnims.goblin_axe, 46, 5),
                    Triple(GodWarsProjAnims.kree_wind, 60, 8),
                    Triple(GodWarsProjAnims.skree_bolt, 46, 10),
                    Triple(GodWarsProjAnims.geerin_spear, 46, 5),
                    Triple(GodWarsProjAnims.growler_spell, 26, 10),
                    Triple(GodWarsProjAnims.bree_arrow, 46, 5),
                    Triple(GodWarsProjAnims.kril_flames, 56, 0),
                    Triple(GodWarsProjAnims.balfrug_fireball, 26, 10),
                )
            for ((type, base, perTile) in captured) {
                for (distance in listOf(1, 4, 9)) {
                    assertEquals(
                        base + perTile * distance,
                        deps.flight(type, distance),
                        "$type at $distance tiles",
                    )
                }
            }
        }
}
