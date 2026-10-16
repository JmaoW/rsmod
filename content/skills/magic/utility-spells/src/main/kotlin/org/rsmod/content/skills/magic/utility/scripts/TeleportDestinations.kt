package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.params
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid

/**
 * Where teleport spells take the caster instead of the cache's destination. A world (such as the
 * Isle of Souls) fills this when it is built; every teleporting spell asks it where to go.
 *
 * Destinations are looked up when the spell is cast, so they may depend on the map having loaded.
 */
@Singleton
class TeleportDestinations @Inject constructor(private val objTypes: ObjTypeList) {
    private val overrides = HashMap<Int, () -> CoordGrid>()

    /**
     * When set, a teleport spell with no override cannot be cast: the cache's destinations are
     * places in the real world, which do not exist in this one.
     */
    var overridesOnly: Boolean = false

    fun override(spell: ObjType, destination: () -> CoordGrid) {
        overrides[spell.id] = destination
    }

    /** Whether [spell] has a destination, which is cheap to ask and may be asked at startup. */
    fun has(spell: ObjType): Boolean =
        spell.id in overrides || (!overridesOnly && cacheDestination(spell) != null)

    /** Where [spell] takes whoever it teleports, or `null` if it has nowhere to take them. */
    fun destination(spell: ObjType): CoordGrid? {
        val override = overrides[spell.id]?.invoke()
        if (override != null || overridesOnly) {
            return override
        }
        return cacheDestination(spell)
    }

    private fun cacheDestination(spell: ObjType): CoordGrid? =
        objTypes[spell].paramOrNull(params.spell_telecoord)
}
