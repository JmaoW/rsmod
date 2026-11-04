package org.rsmod.content.skills.herblore.configs

import java.io.File
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.type.obj.ObjType

/**
 * Writes every Herblore recipe and herb as JSON for the player wiki (`tools/wiki/gen_skills.py`
 * reads it). Skipped unless the `WIKI_DUMP` environment variable names the folder to write into:
 * ```
 * WIKI_DUMP=tools/wiki/dumps ./gradlew :content:skills:herblore:test --tests "*WikiDumpTest*"
 * ```
 */
class WikiDumpTest {
    @Test
    fun `dump recipes for the wiki`() {
        val dir = System.getenv("WIKI_DUMP")
        assumeTrue(dir != null)
        val recipes =
            HerbloreRecipes.all.joinToString(",\n") { r ->
                val inputs =
                    r.inputs.joinToString(",") { "{\"obj\":${str(it.obj)},\"count\":${it.count}}" }
                val tools = r.tools.joinToString(",") { str(it) }
                "{\"method\":\"${r.method.name}\",\"product\":${str(r.product)},\"level\":${r.level}," +
                    "\"xp\":${r.xp},\"output\":${r.output},\"outputMax\":${r.outputMax}," +
                    "\"inputs\":[$inputs],\"tools\":[$tools]}"
            }
        val herbs =
            Herbs.all.joinToString(",\n") { h ->
                "{\"name\":\"${h.name}\",\"grimy\":${str(h.grimy)},\"clean\":${str(h.clean)}," +
                    "\"unfinished\":${str(h.unfinished)},\"cleanLevel\":${h.cleanLevel}," +
                    "\"cleanXp\":${h.cleanXp},\"unfinishedLevel\":${h.unfinishedLevel}}"
            }
        val json = "{\"recipes\":[\n$recipes\n],\"herbs\":[\n$herbs\n]}\n"
        File(dir!!).also { it.mkdirs() }.resolve("herblore.json").writeText(json)
    }

    private fun str(obj: ObjType): String = "\"${obj.internalNameValue}\""
}
