package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Content that pins where a player's build area (the 104x104 scene the client loads) starts,
 * instead of centring it on the zone the player stands in. The real game does this in instances
 * such as the Tombs of Amascut, where every room is loaded at the same scene offset, and client
 * plugins rely on those scene coordinates.
 *
 * A rule answers with the south-west corner of the build area to use, zone aligned, or `null` to
 * leave the player to the normal rules. The first rule with an answer wins. An anchor is only used
 * while the player stands clear of its edges; otherwise the normal build area is used.
 */
@Singleton
public class BuildAreaAnchors @Inject constructor() {
    private val rules = ArrayList<(player: Player) -> CoordGrid?>()

    public fun register(rule: (player: Player) -> CoordGrid?) {
        rules += rule
    }

    public fun anchorFor(player: Player): CoordGrid? {
        for (rule in rules) {
            val anchor = rule(player)
            if (anchor != null) {
                return anchor
            }
        }
        return null
    }
}
