package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.misc.isleofsouls.configs.IsleOfSoulsTaskTexts
import org.rsmod.game.type.TypeListMap

class LeagueTaskStructEditsTest {
    class Deps @Inject constructor(val types: TypeListMap)

    /** `./gradlew packCache` has to have run for the edits to be in the cache the test loads. */
    @Test
    fun GameTestState.`edited league task structs carry the new text and no area`() =
        runInjectedGameTest(Deps::class) { deps ->
            val structIds =
                mapOf(985 to 2499, 1379 to 5125, 450 to 1944, 700 to 5141, 644 to 4891, 972 to 5203)
            for ((taskId, structId) in structIds) {
                val edit = IsleOfSoulsTaskTexts.edits.single { it.taskId == taskId }
                val params = checkNotNull(deps.types.structs[structId]?.paramMap).primitiveMap
                assertEquals(taskId, params[873], "task id of struct $structId")
                edit.name?.let { assertEquals(it, params[874], "name of task $taskId") }
                edit.description?.let { assertEquals(it, params[875], "desc of task $taskId") }
                assertEquals(0, params[1017], "area of task $taskId")
            }
        }
}
