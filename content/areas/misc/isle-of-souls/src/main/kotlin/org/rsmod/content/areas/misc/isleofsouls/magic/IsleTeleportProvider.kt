package org.rsmod.content.areas.misc.isleofsouls.magic

import com.google.inject.Provider
import jakarta.inject.Inject
import org.rsmod.content.skills.magic.utility.scripts.TeleportDestinations
import org.rsmod.game.type.obj.ObjTypeList

/**
 * Builds the one [TeleportDestinations] with the isle's teleports already in it, so that they are
 * there before any script starts up and decides which teleport spells to listen for.
 */
class IsleTeleportProvider
@Inject
constructor(private val isle: IsleTeleportDestinations, private val objTypes: ObjTypeList) :
    Provider<TeleportDestinations> {
    override fun get(): TeleportDestinations {
        val destinations = TeleportDestinations(objTypes)
        isle.apply(destinations)
        return destinations
    }
}
