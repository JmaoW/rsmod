package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.type.TypeListMap
import org.rsmod.game.type.obj.ObjTypeList

/** What the raid reads from the real cache: the invocation board, the loot items and the refs. */
class ToaCacheTest {
    class Deps @Inject constructor(val types: TypeListMap, val objTypes: ObjTypeList)

    @Test
    fun GameTestState.`the invocation board is read from the cache`() =
        runInjectedGameTest(Deps::class) { deps ->
            val catalog =
                InvocationCatalog.build(
                    structs = { id -> deps.types.structs.types[id]?.paramMap?.primitiveMap },
                    enums = { id -> deps.types.enums.types[id]?.primitiveMap },
                )
            assertEquals(46, catalog.all.size)
            // The unit tests build the same board from a fixture; these are its totals.
            assertEquals(1_170, catalog.all.sumOf { it.level })
            assertEquals(46, catalog.all.map { it.bit }.toSet().size)
            assertEquals("Try Again", catalog.all.first().name)
            assertEquals("Insanity", catalog.all[43].name)
            assertEquals(InvocationCategory.Blazing, catalog.all.last().category)
            val overclocked2 = checkNotNull(catalog.byName(Inv.OVERCLOCKED_2))
            assertEquals(Inv.OVERCLOCKED, catalog.requirement(overclocked2)?.name)
            assertEquals(4_000, catalog.byName(Inv.WALK_FOR_IT)?.magnitude)
            assertTrue(catalog.all.all { it.category != null })
        }

    @Test
    fun GameTestState.`every item the chest can give is found by name`() =
        runInjectedGameTest(Deps::class) { deps ->
            val items = ToaItems(deps.objTypes)
            for (drop in ToaLootMath.common) {
                val base = items.named(drop.name)
                assertNotNull(base, "common item ${drop.name}")
                if (drop.noted) {
                    assertNotNull(items.noted(base!!), "noted ${drop.name}")
                }
            }
            for (unique in ToaUnique.entries) {
                assertNotNull(items.unique(unique), "unique ${unique.label}")
            }
            for (name in
                listOf(
                    "Thread of Elidinis",
                    "Breach of the scarab",
                    "Eye of the corruptor",
                    "Jewel of the sun",
                    "Jewel of Amascut",
                    "Tumeken's guardian",
                    "Fossilised dung",
                    "Clue scroll (elite)",
                )) {
                assertNotNull(items.named(name), name)
            }
        }

    @Test
    fun GameTestState.`the references the raid uses exist`() =
        runInjectedGameTest(Deps::class) { _ ->
            // Reading each reference resolves its name against the cache and the symbols.
            listOf(
                    toa_interfaces.hud.id,
                    toa_interfaces.lobby.id,
                    toa_interfaces.party_list.id,
                    toa_interfaces.party_details.id,
                    toa_interfaces.invocations.id,
                    toa_interfaces.chests.id,
                    toa_components.party_list_buttons.packed,
                    toa_components.party_list_rows.packed,
                    toa_components.details_buttons.packed,
                    toa_components.details_presets.packed,
                    toa_components.chests_items.packed,
                    toa_varbits.party_status.id,
                    toa_varbits.raid_level.id,
                    toa_varbits.should_have_loot.id,
                    toa_varbits.vault_sarcophagus.id,
                    toa_invs.chests.id,
                    toa_npcs.ghost.id,
                    toa_npcs.osmumten_vault.id,
                    toa_locs.nexus_het.id,
                    toa_locs.nexus_wardens.id,
                    toa_locs.vault_chest_mine.id,
                    toa_locs.pyramid_entry.id,
                )
                .forEach { assertTrue(it != 0) }
            assertEquals(8, toa_varbits.member_orbs.size)
            assertEquals(8, toa_varbits.vault_chests.size)
            assertEquals(5, toa_varps.presets.size)
            assertEquals(3, toa_varps.completions.size)
        }
}
