package org.rsmod.content.areas.misc.isleofsouls.bank

import org.rsmod.api.type.refs.timer.TimerReferences

typealias team_bank_timers = TeamBankTimers

object TeamBankTimers : TimerReferences() {
    val sync = find("team_bank_sync")
}
