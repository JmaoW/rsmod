package org.rsmod.content.generic.locs.banks

import org.rsmod.api.config.refs.content
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.game.type.loc.LocType

internal typealias bank_locs = BankLocs

internal object BankLocs : LocReferences() {
    val bankbooth = find("aide_bankbooth")
    val bankbooth_multi = find("aide_bankbooth_multi")

    // Every "Bank chest" loc that has a "Use" (or "Bank") first option in the cache.
    val chests =
        listOf(
            find("thbankchest"),
            find("castlewars_bankchest"),
            find("champions_bankchest"),
            find("diary_guild_bankchest"),
            find("barbassault_bank_chest"),
            find("fris_bank_chest_open"),
            find("blast_bank_chest"),
            find("fairy_chest_bank"),
            find("lovakengj_bank_chest_orange"),
            find("lovakengj_bank_chest_grey"),
            find("mm_desk_bankchest"),
            find("wcguild_bankchest"),
            find("wint_bankchest"),
            find("tox_bank_chest"),
            find("tzhaar_bank_chest"),
            find("fossil_bank_chest_built"),
            find("fossil_volcano_bank_chest"),
            find("brimstone_bankchest"),
            find("soul_wars_bankchest"),
            find("tempoross_lobby_bank_chest"),
            find("bim_ruins_bankchest"),
            find("gotr_bankchest"),
            find("bh_bankchest"),
            find("raids_bank_chest_lobby_working"),
            find("colosseum_bank"),
            find("fortis_bank_chest"),
            find("fortis_bank_chest_small"),
            find("magictraining_bankchest"),
            find("giants_foundry_chest"),
            find("chest_alchemist01_closed01"),
            find("chest_alchemist01_open01"),
        )
}

internal object BankLocEditor : LocEditor() {
    init {
        booth(bank_locs.bankbooth)
        booth(bank_locs.bankbooth_multi)
        bank_locs.chests.forEach(::chest)
    }

    private fun booth(type: LocType) {
        edit(type) { contentGroup = content.bank_booth }
    }

    private fun chest(type: LocType) {
        edit(type) { contentGroup = content.bank_chest }
    }
}
