package org.rsmod.content.areas.misc.isleofsouls.prayer

import jakarta.inject.Inject
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.content.interfaces.prayer.tab.PrayerRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal object LeaguePrayerLocs : LocReferences() {
    val altar = find("altar")
    val chaos_altar = find("chaosaltar")
}

/**
 * The prayer parts of the league that are not the skill itself: the two altars beside the bank, and
 * every prayer being unlocked from the start.
 *
 * Prayers that the real game gates behind a quest, a scroll or a diary (Chivalry, Piety, Rigour,
 * Augury and Preserve) check an unlock varbit on the prayer's own config, so the unlock is to set
 * each of those varbits to the value the prayer asks for. Deadeye and Mystic Vigour are not unlocks
 * but upgrades of Eagle Eye and Mystic Might that need a prayer scroll, so they are left alone.
 */
class LeaguePrayerScript
@Inject
constructor(
    private val prayers: PrayerRepository,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { placeAltars() }
        onPlayerLogin { player.unlockAllPrayers() }
    }

    /** Puts the normal altar and the chaos altar on the plaza south of the bank. */
    fun placeAltars() {
        place(ALTAR, LeaguePrayerLocs.altar)
        place(CHAOS_ALTAR, LeaguePrayerLocs.chaos_altar)
    }

    private fun place(coords: CoordGrid, type: LocType) {
        locRepo.add(
            coords,
            locTypes[type],
            duration = Int.MAX_VALUE,
            angle = ALTAR_ANGLE,
            shape = LocShape.CentrepieceStraight,
        )
    }

    private fun Player.unlockAllPrayers() {
        for (prayer in prayers.prayerList) {
            val unlock = prayer.unlocked ?: continue
            if (vars[unlock] < prayer.unlockState) {
                VarPlayerIntMapSetter.set(this, unlock, prayer.unlockState)
            }
        }
    }

    companion object {
        /**
         * Both altars are 2 wide and 1 deep and stand in the open plaza just south of the bank
         * chest (2212, 2860), with a free tile in front of each. A flood fill over the isle's
         * collision from the plaza showed this ground is connected to the rest of the plaza; the
         * strip north of the bank is cut off from it by the bank's crates and barrels.
         */
        val ALTAR = CoordGrid(2208, 2857, 0)
        val CHAOS_ALTAR = CoordGrid(2212, 2857, 0)

        /** Both face south, towards the bank. Turn them round here if they face the wrong way. */
        val ALTAR_ANGLE = LocAngle.South
    }
}
