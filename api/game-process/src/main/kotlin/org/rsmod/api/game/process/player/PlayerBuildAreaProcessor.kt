package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.rsmod.api.utils.map.BuildAreaUtils
import org.rsmod.game.entity.Player
import org.rsmod.map.zone.ZoneKey

public class PlayerBuildAreaProcessor @Inject constructor(private val anchors: BuildAreaAnchors) {
    public constructor() : this(BuildAreaAnchors())

    public fun process(player: Player) {
        player.processBuildAreaChange()
    }

    private fun Player.processBuildAreaChange() {
        val anchor = anchors.anchorFor(this)
        if (anchor != null && !BuildAreaUtils.isOutsideOfBuildArea(coords, anchor)) {
            if (buildArea != anchor) {
                buildArea = anchor
                // The client rebuilds its scene even when the player stays in the same zone, so
                // every visible zone's locs and objs are sent again.
                visibleZoneKeys.clear()
            }
            return
        }
        val rebuildBuildArea = BuildAreaUtils.requiresNewBuildArea(this)
        if (rebuildBuildArea) {
            enterBuildArea()
        }
    }

    private fun Player.enterBuildArea() {
        buildArea = BuildAreaUtils.calculateBuildArea(ZoneKey.from(coords))
    }
}
