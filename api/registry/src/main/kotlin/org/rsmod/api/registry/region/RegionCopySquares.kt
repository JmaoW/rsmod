package org.rsmod.api.registry.region

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.map.xtea.XteaMap
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey

/**
 * Which map squares a region (an instance) may copy zones from: only those the server can read.
 *
 * The server loads exactly the map squares `xteas.json` has keys for, and every map square in the
 * cache has encrypted locs. A square without a key therefore has nothing on the server (no
 * collision, no locs), and a client shown a zone copied from it waits for those locs forever: its
 * scene never finishes loading. Such zones are left out of regions, so they stay empty void, which
 * is what the real game shows there (its instances never copy them, which is why no key exists).
 *
 * Without any keys at all (a bare test world) every square may be copied.
 */
@Singleton
public class RegionCopySquares @Inject constructor(private val xteas: XteaMap) {
    private val reported = HashSet<MapSquareKey>()

    public fun isCopyable(normalZone: ZoneKey): Boolean {
        if (xteas.isEmpty()) {
            return true
        }
        val square = MapSquareKey.from(normalZone.toCoords())
        if (square in xteas) {
            return true
        }
        if (reported.add(square)) {
            logger.info {
                "Regions leave out map square ${square.x}_${square.z}: it has no xtea key, so it " +
                    "is either empty or unreadable."
            }
        }
        return false
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
