package org.rsmod.content.areas.misc.isleofsouls.instances

import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

/**
 * A 32x32 patch of the island, copied on all four levels. It is only a stand-in for trying the
 * instance system (`::instance patch`) until real content registers its own types: the entry and
 * exit are the same open tile, so leaving puts a player at the same spot on the real map.
 */
object TestPatchInstance {
    val type =
        TeamInstanceType(
            name = "patch",
            template =
                RegionTemplate.create {
                    copyAllLevels(copyZoneX = 272, copyZoneZ = 364) {
                        zoneWidth = 4
                        zoneLength = 4
                    }
                },
            entry = CoordGrid(2190, 2931, 0),
            exit = CoordGrid(2190, 2931, 0),
        )
}
