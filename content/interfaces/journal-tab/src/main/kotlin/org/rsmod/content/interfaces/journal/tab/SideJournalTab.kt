package org.rsmod.content.interfaces.journal.tab

import org.rsmod.api.utils.vars.VarEnumDelegate

enum class SideJournalTab(override val varValue: Int) : VarEnumDelegate {
    Summary(varValue = 0),
    Quests(varValue = 1),
    Tasks(varValue = 2),
    // Tab slots 4 (adventure path) and 5 (leagues) follow the client's `side_journal_switchtab`
    // order, which maps `side_journal_tab` values 3 and 4 respectively.
    Leagues(varValue = 4),
}
