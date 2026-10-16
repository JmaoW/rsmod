package org.rsmod.content.areas.misc.isleofsouls.masteries

import org.rsmod.api.type.builders.clientscript.ClientScriptBuilder

/**
 * Packs the clientscripts of the combat masteries screen that had to be changed.
 *
 * `[proc,league_combat_mastery_bar]` is the cache's own script with two strings changed. The bar
 * counts finished mastery tasks (see `MasteryVarBits.points_earned`), and its text said "Combat
 * mastery points 3 / 10", which reads as points when it is tasks. It now says "Combat mastery tasks
 * 3 / 10", and "You have completed all Combat Mastery tasks!" at 10. Only those two string
 * instructions differ from the original, so the file is that script from revision 233 of the cache.
 * If the cache is updated to a new revision, make the same two string changes to the new script and
 * replace the file.
 *
 * The client reads scripts from the cache, so this needs `./gradlew packCache` to reach it.
 */
object MasteryScripts : ClientScriptBuilder() {
    override fun onPackCs2Task() {
        resourceFile<MasteryScripts>("cs2/[proc,league_combat_mastery_bar]")
    }
}
