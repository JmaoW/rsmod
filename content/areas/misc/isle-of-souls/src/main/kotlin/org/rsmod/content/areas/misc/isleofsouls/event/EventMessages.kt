package org.rsmod.content.areas.misc.isleofsouls.event

import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.commands
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.green
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.red

/** The replies admins get from the event commands (freeze, unfreeze, announcement reload). */
object EventMessages {
    fun frozen(): String =
        "${green("Frozen:")} the standings are locked. " +
            "Run ${commands("::leagueunfreeze")} to make them live again."

    fun alreadyFrozen(): String = "${red("Not changed:")} the standings are already frozen."

    fun unfrozen(): String = "${green("Unfrozen:")} the standings are live again."

    fun notFrozen(): String = "${red("Not changed:")} the standings are not frozen."

    /** Added to a full reset, which also lifts a freeze left over from testing. */
    fun freezeCleared(): String = "The standings were frozen; the freeze was lifted too."

    fun announcementsReloaded(): String = "${green("Reloaded:")} the announcement settings."

    fun announcementsNotReloaded(reason: String): String =
        "${red("Not reloaded:")} $reason. The current settings are unchanged."
}
