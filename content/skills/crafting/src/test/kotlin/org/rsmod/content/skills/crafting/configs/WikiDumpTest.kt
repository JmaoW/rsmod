package org.rsmod.content.skills.crafting.configs

import java.io.File
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.type.obj.ObjType

/**
 * Writes every Crafting recipe as JSON for the player wiki (`tools/wiki/gen_skills.py` reads it).
 * Skipped unless the `WIKI_DUMP` environment variable names the folder to write into:
 * ```
 * WIKI_DUMP=tools/wiki/dumps ./gradlew :content:skills:crafting:test --tests "*WikiDumpTest*"
 * ```
 */
class WikiDumpTest {
    @Test
    fun `dump recipes for the wiki`() {
        val dir = System.getenv("WIKI_DUMP")
        assumeTrue(dir != null)
        val rows = ArrayList<String>()
        for (recipe in CraftingRecipes.all) rows += row("item", recipe)
        for (recipe in StationRecipes.all) rows += row("station", recipe)
        for (item in JewelleryRecipes.all) rows += row("jewellery", item.recipe, item.slayerUnlock)
        val tanning =
            Tanning.slots.joinToString(",") {
                "{\"hide\":${str(it.hide)},\"leather\":${str(it.leather)},\"cost\":${it.cost}}"
            }
        val json = "{\"recipes\":[\n${rows.joinToString(",\n")}\n],\"tanning\":[$tanning]}\n"
        File(dir!!).also { it.mkdirs() }.resolve("crafting.json").writeText(json)
    }

    private fun row(group: String, r: CraftingRecipe, slayerUnlock: Int? = null): String {
        val inputs =
            r.inputs.joinToString(",") { input ->
                val options = input.options.joinToString(",") { str(it) }
                "{\"count\":${input.count},\"options\":[$options]}"
            }
        val tools = r.tools.joinToString(",") { str(it) }
        return "{\"group\":\"$group\",\"method\":\"${r.method.name}\"," +
            "\"station\":${r.station?.let { "\"${it.name}\"" } ?: "null"}," +
            "\"product\":${str(r.product)},\"output\":${r.output},\"level\":${r.level}," +
            "\"xp\":${r.xp},\"inputs\":[$inputs],\"tools\":[$tools]," +
            "\"thread\":${r.thread},\"crush\":${r.crush != null},\"slayerUnlock\":$slayerUnlock}"
    }

    private fun str(obj: ObjType): String = "\"${obj.internalNameValue}\""
}
