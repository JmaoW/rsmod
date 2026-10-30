package org.rsmod.content.areas.misc.isleofsouls.toa

import org.rsmod.api.type.builders.bas.BasBuilder
import org.rsmod.api.type.builders.proj.ProjAnimBuilder
import org.rsmod.api.type.refs.proj.ProjAnimReferences
import org.rsmod.api.type.refs.seq.SeqReferences

typealias toa_projanims = ToaProjAnims

/** How the raid's thrown and spat projectiles fly. */
object ToaProjAnims : ProjAnimReferences() {
    /** Thrown in a high arc onto a tile: Zebak's rocks, jugs and acid, Ba-Ba's boulders. */
    val lob = find("toa_lob")

    /** Zebak's volley rising to the split point, and its fragments falling on each player. */
    val volley_rise = find("toa_volley_rise")
    val volley_fragment = find("toa_volley_fragment")

    /** A short low splash from tile to tile: acid spreading, a jug's water. */
    val splash = find("toa_splash")

    /** A heavy rock thrown at a player: Ba-Ba's boulder, the Wardens' skulls. */
    val heavy = find("toa_heavy")

    /** A slow orb across the room: the Wardens' and Kephri's. */
    val orb = find("toa_orb")
}

/**
 * The timings come from TSPS's projectile calls (OpenRune captures, and the Wiki for Zebak's
 * volley): `delay` is when it leaves, the flight is `base + perTile * distance` client cycles.
 * Needs `packCache` (new `projanim.sym` ids).
 */
internal object ToaProjAnimBuilder : ProjAnimBuilder() {
    init {
        flight("toa_lob", 62, 0, start = 30, base = 120, perTile = 0, angle = 16, progress = 64)
        flight(
            "toa_volley_rise",
            50,
            175,
            start = 60,
            base = 120,
            perTile = 0,
            angle = 30,
            progress = 0,
        )
        flight(
            "toa_volley_fragment",
            175,
            22,
            start = 0,
            base = 90,
            perTile = 0,
            angle = 127,
            progress = 0,
        )
        flight("toa_splash", 7, 0, start = 0, base = 30, perTile = 10, angle = 16, progress = 0)
        flight("toa_heavy", 100, 31, start = 90, base = 120, perTile = 0, angle = 16, progress = 64)
        flight("toa_orb", 40, 20, start = 30, base = 60, perTile = 6, angle = 0, progress = 64)
    }

    private fun flight(
        name: String,
        startHeight: Int,
        endHeight: Int,
        start: Int,
        base: Int,
        perTile: Int,
        angle: Int,
        progress: Int,
    ) {
        build(name) {
            this.startHeight = startHeight
            this.endHeight = endHeight
            this.delay = start
            this.angle = angle
            this.lengthAdjustment = base - start
            this.progress = progress
            this.stepMultiplier = perTile
        }
    }
}

object ToaSwimSeqs : SeqReferences() {
    val ready = find("human_swim_ready")
    val swim = find("human_swim")
}

/** A player washed into Zebak's water swims (stand, turn and walk all swim). */
object ToaBas : BasBuilder() {
    val swim =
        build("toa_swim") {
            readyAnim = ToaSwimSeqs.ready
            turnAnim = ToaSwimSeqs.swim
            walkAnim = ToaSwimSeqs.swim
            walkAnimBack = ToaSwimSeqs.swim
            walkAnimLeft = ToaSwimSeqs.swim
            walkAnimRight = ToaSwimSeqs.swim
            runAnim = ToaSwimSeqs.swim
        }
}
