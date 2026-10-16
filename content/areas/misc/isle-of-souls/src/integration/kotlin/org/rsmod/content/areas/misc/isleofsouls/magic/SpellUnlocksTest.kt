package org.rsmod.content.areas.misc.isleofsouls.magic

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.type.TypeListMap

class SpellUnlocksTest {
    class Deps @Inject constructor(val types: TypeListMap)

    private companion object {
        const val SPELL_NAME = 601
        const val QUEST = 1189
        const val DIARY = 1190
        const val EXTRA_UNLOCK = 1416

        /** Teleport to Target waits for a bounty target, which is a different lock. */
        const val TELEPORT_TO_TARGET = 17152
    }

    /** `./gradlew packCache` has to have run for the edits to be in the cache the test loads. */
    @Test
    fun GameTestState.`no spell waits for a quest or a diary in the spellbook`() =
        runInjectedGameTest(Deps::class) { deps ->
            val spells =
                deps.types.objs.values.filter {
                    it.paramMap?.primitiveMap?.containsKey(SPELL_NAME) == true
                }
            assertTrue(spells.size > 150, "Expected the spell objs, found ${spells.size}.")

            for (spell in spells) {
                val params = checkNotNull(spell.paramMap).primitiveMap
                assertEquals(-1, params[QUEST] ?: -1, "quest of ${spell.internalName}")
                assertEquals(0, params[DIARY] ?: 0, "diary of ${spell.internalName}")
                if (spell.id != TELEPORT_TO_TARGET) {
                    assertEquals(0, params[EXTRA_UNLOCK] ?: 0, "unlock of ${spell.internalName}")
                }
            }
        }
}
