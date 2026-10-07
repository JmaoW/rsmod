package org.rsmod.content.areas.misc.isleofsouls.instances

import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.game.entity.Player

/**
 * Moves a player who was saved inside an instance back out to the map before they log in.
 *
 * This has to happen while the account loads. The client's first map is built from the saved
 * position as the session starts, so a player left in an instance's coordinates would load an empty
 * area, or land in whichever instance later reused that slot.
 */
class TeamInstanceLoginPipeline @Inject constructor(private val applier: TeamInstanceLoginApplier) :
    CharacterDataStage.Pipeline {
    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        metadata.add(applier, TeamInstanceLoginSegment)
    }

    // Nothing to save: the position is already saved, and the fix is applied when it is loaded.
    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {}
}

/** Carries no data; it only schedules [TeamInstanceLoginApplier] for the login. */
object TeamInstanceLoginSegment : CharacterDataStage.Segment

class TeamInstanceLoginApplier @Inject constructor(private val instances: TeamInstances) :
    CharacterDataStage.Applier<TeamInstanceLoginSegment> {
    override fun apply(player: Player, data: TeamInstanceLoginSegment) {
        if (!RegionRegistry.inWorkingArea(player.coords)) {
            return
        }
        val exit = instances.fallbackExit() ?: return
        player.coords = exit
    }
}
