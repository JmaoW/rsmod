package org.rsmod.content.areas.misc.isleofsouls.tmpdump

import jakarta.inject.Inject
import java.io.File
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.type.TypeListMap

class StructDumpTest {
    class Deps @Inject constructor(val maps: TypeListMap)

    @Test
    fun GameTestState.dump() =
        runInjectedGameTest(Deps::class) { deps ->
            val out = StringBuilder()
            for (id in listOf(2083, 1945, 2203, 2280, 5153, 3880)) {
                val t = deps.maps.structs[id]!!
                out.append("$id name=${t.internalName} primitive=${t.paramMap?.primitiveMap}\n")
            }
            out.append("count=${deps.maps.structs.size} maxId=${deps.maps.structs.keys.max()}\n")
            File(System.getenv("DUMP_OUT") ?: "struct-dump.txt").writeText(out.toString())
        }
}
