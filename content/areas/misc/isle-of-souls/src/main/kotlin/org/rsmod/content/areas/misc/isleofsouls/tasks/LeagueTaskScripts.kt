package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.rsmod.api.type.builders.clientscript.ClientScriptBuilder

/**
 * Packs the clientscript of the League Tasks window that had to be changed.
 *
 * `[proc,script3211]` stores a filter dropdown's pick in the matching varbit (tier, completed,
 * type, skill or area, chosen by the dropdown's enum id). The file is that proc from revision 233
 * of the cache with one block added just before its `return`, where every path of the proc ends:
 * ```
 * leaguefilter <enum id> <entry index>    (arg0, arg1)
 * ```
 *
 * It is sent the way a typed `::` command is (`docheat`, op 5020), and [LeagueTaskWindow] handles
 * it. The block is plain instructions: `sconst "leaguefilter "`, `iload`, `append_num` (4100),
 * `sconst " "`, `append` (4101), `iload`, `append_num`, `docheat`. If the cache is updated to a new
 * revision, add the same block to the new script and replace the file.
 *
 * The client reads scripts from the cache, so this needs `./gradlew packCache` to reach it.
 */
object LeagueTaskScripts : ClientScriptBuilder() {
    override fun onPackCs2Task() {
        resourceFile<LeagueTaskScripts>("cs2/[proc,script3211]")
    }
}
