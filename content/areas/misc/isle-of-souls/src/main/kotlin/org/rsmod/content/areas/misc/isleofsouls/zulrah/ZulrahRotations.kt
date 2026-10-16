package org.rsmod.content.areas.misc.isleofsouls.zulrah

import org.rsmod.map.CoordGrid

/** The three forms Zulrah takes. Each is its own npc type, with its own defences. */
enum class ZulrahForm {
    /** Serpentine: shoots ranged, weak to magic. */
    Green,

    /** Magma: swings her tail (typeless melee), the toughest form. */
    Red,

    /** Tanzanite: shoots magic or ranged, weak to ranged. */
    Blue,
}

/** The four places Zulrah surfaces, named as the wiki does. */
enum class ZulrahSpot {
    Centre,
    West,
    East,
    South,
}

/** What kind of thing an action is, without the tiles it may carry. */
enum class ZulrahActionKind {
    Ranged,
    Magic,

    /** A blue form's attack: magic or ranged, picked when it is made. */
    Mixed,
    Melee,

    /** A venom cloud barrage. */
    Cloud,

    /** A snakeling orb. */
    Orb,
}

/** One thing Zulrah does in a phase, one after the other. */
sealed interface ZulrahAction {
    val kind: ZulrahActionKind

    data object Ranged : ZulrahAction {
        override val kind = ZulrahActionKind.Ranged
    }

    data object Magic : ZulrahAction {
        override val kind = ZulrahActionKind.Magic
    }

    data object Mixed : ZulrahAction {
        override val kind = ZulrahActionKind.Mixed
    }

    data object Melee : ZulrahAction {
        override val kind = ZulrahActionKind.Melee
    }

    /**
     * A venom cloud barrage: two clouds, centred on [first] and [second] (ordinary map
     * coordinates). The centres are always among the platform's fixed cloud sites.
     */
    data class Cloud(val first: CoordGrid, val second: CoordGrid) : ZulrahAction {
        override val kind = ZulrahActionKind.Cloud
        val centres: List<CoordGrid>
            get() = listOf(first, second)
    }

    /** A snakeling orb that lands on [tile] (an ordinary map coordinate). */
    data class Orb(val tile: CoordGrid) : ZulrahAction {
        override val kind = ZulrahActionKind.Orb
    }
}

/**
 * One phase of a rotation: Zulrah dives, comes up in [form] at [spot] and does [actions] in order.
 * [jad] marks the green phase that alternates ranged and magic (the wiki calls it the "Jad phase").
 */
class ZulrahPhase(val spot: ZulrahSpot, val form: ZulrahForm, val actions: List<ZulrahAction>) {
    val jad: Boolean
        get() =
            form == ZulrahForm.Green &&
                ZulrahAction.Magic in actions &&
                ZulrahAction.Ranged in actions

    override fun toString(): String = "$spot $form ${actions.map { it.kind }}"
}

/**
 * A fixed order of phases. Zulrah picks one at random when the fight begins. The first phase is the
 * opening fill and the last is the closing phase: five ranged shots and the same four barrages,
 * which "count as the first phase of the new rotation" (wiki), so when a rotation ends the next one
 * carries on from its second phase.
 */
class ZulrahRotation(val name: String, val phases: List<ZulrahPhase>)

/**
 * Zulrah's four rotations. The phases, forms, spots and the number of each action are from the
 * wiki's strategy page (Rotation 1 "Crimson A", 2 "Crimson B", 3 "Serp" and 4 "Tanz"). The tiles
 * each cloud barrage and snakeling orb lands on are not on the wiki: they come from the
 * Near-Reality server's data as ported by the RSPSApp/tsps project, which has not been checked
 * against the real game. Every barrage throws two clouds, centred on one of the fixed sites of
 * [ZulrahCloudSites].
 */
object ZulrahRotations {
    private fun phase(spot: ZulrahSpot, form: ZulrahForm, vararg actions: List<ZulrahAction>) =
        ZulrahPhase(spot, form, actions.toList().flatten())

    private fun ranged(n: Int) = List(n) { ZulrahAction.Ranged }

    private fun mixed(n: Int) = List(n) { ZulrahAction.Mixed }

    private fun melee(n: Int) = List(n) { ZulrahAction.Melee }

    /** [n] attacks alternating ranged and magic, starting with [first]. */
    private fun alternating(first: ZulrahAction, n: Int): List<ZulrahAction> {
        val second = if (first == ZulrahAction.Ranged) ZulrahAction.Magic else ZulrahAction.Ranged
        return List(n) { if (it % 2 == 0) first else second }
    }

    /** One barrage of two clouds. */
    private fun c(x1: Int, z1: Int, x2: Int, z2: Int) =
        listOf(ZulrahAction.Cloud(CoordGrid(x1, z1, 0), CoordGrid(x2, z2, 0)))

    /** One snakeling orb. */
    private fun s(x: Int, z: Int) = listOf(ZulrahAction.Orb(CoordGrid(x, z, 0)))

    private val C = ZulrahSpot.Centre
    private val W = ZulrahSpot.West
    private val E = ZulrahSpot.East
    private val S = ZulrahSpot.South
    private val green = ZulrahForm.Green
    private val red = ZulrahForm.Red
    private val blue = ZulrahForm.Blue

    /** The four barrages that open the fight and close every rotation. */
    private val fill =
        c(2269, 3069, 2272, 3070) +
            c(2266, 3069, 2263, 3070) +
            c(2273, 3072, 2273, 3075) +
            c(2263, 3073, 2263, 3076)

    private val opening = phase(C, green, fill)
    private val closing = phase(C, green, ranged(5), fill)

    val one =
        ZulrahRotation(
            "Rotation 1 (Crimson A)",
            listOf(
                opening,
                phase(C, red, melee(2)),
                phase(C, blue, mixed(4)),
                phase(
                    S,
                    green,
                    ranged(5),
                    s(2263, 3076),
                    s(2263, 3073),
                    c(2263, 3070, 2266, 3069),
                    c(2272, 3069, 2273, 3072),
                    s(2273, 3075),
                    s(2273, 3077),
                ),
                phase(C, red, melee(2)),
                phase(W, blue, mixed(5)),
                phase(
                    S,
                    green,
                    c(2269, 3069, 2272, 3069),
                    c(2263, 3070, 2266, 3069),
                    c(2263, 3073, 2263, 3076),
                    s(2272, 3071),
                    s(2273, 3075),
                    s(2273, 3077),
                    s(2273, 3072),
                ),
                phase(
                    S,
                    blue,
                    mixed(5),
                    s(2263, 3070),
                    c(2266, 3069, 2269, 3069),
                    s(2263, 3076),
                    c(2272, 3069, 2273, 3072),
                    s(2263, 3073),
                ),
                phase(
                    W,
                    green,
                    alternating(ZulrahAction.Ranged, 10),
                    c(2263, 3070, 2266, 3069),
                    c(2269, 3069, 2272, 3069),
                    c(2263, 3073, 2263, 3076),
                    c(2273, 3072, 2273, 3075),
                ),
                phase(C, red, melee(2)),
                closing,
            ),
        )

    val two =
        ZulrahRotation(
            "Rotation 2 (Crimson B)",
            listOf(
                opening,
                phase(C, red, melee(2)),
                phase(C, blue, mixed(4)),
                phase(
                    W,
                    green,
                    c(2273, 3072, 2272, 3069),
                    c(2273, 3075, 2273, 3078),
                    c(2269, 3069, 2266, 3069),
                    s(2266, 3069),
                    s(2263, 3070),
                    s(2263, 3073),
                    s(2263, 3076),
                ),
                phase(
                    S,
                    blue,
                    mixed(5),
                    s(2263, 3076),
                    s(2263, 3073),
                    c(2263, 3070, 2266, 3069),
                    c(2272, 3069, 2273, 3072),
                    s(2273, 3075),
                    s(2273, 3077),
                ),
                phase(C, red, melee(2)),
                phase(E, green, ranged(5)),
                phase(
                    S,
                    blue,
                    mixed(5),
                    s(2263, 3070),
                    c(2266, 3069, 2269, 3069),
                    s(2263, 3076),
                    c(2272, 3069, 2273, 3072),
                    s(2263, 3073),
                ),
                phase(
                    W,
                    green,
                    alternating(ZulrahAction.Ranged, 10),
                    c(2263, 3070, 2266, 3069),
                    c(2269, 3069, 2272, 3069),
                    c(2263, 3073, 2263, 3076),
                    c(2273, 3072, 2273, 3075),
                ),
                phase(C, red, melee(2)),
                closing,
            ),
        )

    val three =
        ZulrahRotation(
            "Rotation 3 (Serp)",
            listOf(
                opening,
                phase(E, green, ranged(5), s(2273, 3078), s(2273, 3075), s(2273, 3072)),
                phase(
                    C,
                    red,
                    c(2273, 3078, 2273, 3075),
                    s(2273, 3072),
                    c(2272, 3070, 2269, 3069),
                    s(2266, 3069),
                    c(2263, 3070, 2263, 3073),
                    s(2263, 3076),
                    melee(2),
                ),
                phase(W, blue, mixed(5)),
                phase(S, green, ranged(5)),
                phase(E, blue, mixed(5)),
                phase(
                    C,
                    green,
                    c(2273, 3078, 2273, 3075),
                    c(2273, 3072, 2272, 3069),
                    c(2269, 3069, 2266, 3069),
                    s(2263, 3070),
                    s(2263, 3076),
                    s(2263, 3073),
                ),
                phase(W, green, ranged(5)),
                phase(
                    C,
                    blue,
                    mixed(5),
                    c(2263, 3076, 2263, 3073),
                    c(2263, 3070, 2266, 3069),
                    s(2269, 3069),
                    s(2272, 3069),
                    s(2273, 3072),
                ),
                phase(E, green, alternating(ZulrahAction.Magic, 10)),
                phase(C, blue, s(2263, 3076), s(2263, 3070), s(2263, 3072), s(2273, 3078)),
                closing,
            ),
        )

    val four =
        ZulrahRotation(
            "Rotation 4 (Tanz)",
            listOf(
                opening,
                phase(
                    E,
                    blue,
                    s(2272, 3069),
                    s(2273, 3078),
                    s(2273, 3075),
                    s(2273, 3072),
                    mixed(6),
                ),
                phase(S, green, ranged(4), c(2263, 3070, 2269, 3069), c(2266, 3069, 2272, 3069)),
                phase(
                    W,
                    blue,
                    s(2263, 3076),
                    s(2263, 3073),
                    s(2266, 3069),
                    s(2269, 3069),
                    mixed(4),
                ),
                phase(C, red, melee(2), c(2263, 3070, 2269, 3069), c(2266, 3069, 2272, 3069)),
                phase(E, green, ranged(4)),
                phase(
                    S,
                    green,
                    s(2263, 3076),
                    s(2263, 3073),
                    s(2263, 3070),
                    s(2273, 3072),
                    s(2273, 3075),
                    s(2273, 3078),
                    c(2273, 3075, 2273, 3078),
                    c(2272, 3069, 2273, 3072),
                    c(2269, 3069, 2266, 3069),
                ),
                phase(
                    W,
                    blue,
                    mixed(5),
                    s(2263, 3076),
                    s(2263, 3073),
                    s(2263, 3070),
                    s(2266, 3069),
                ),
                phase(C, green, ranged(4)),
                phase(
                    C,
                    blue,
                    mixed(4),
                    c(2263, 3073, 2266, 3069),
                    c(2263, 3070, 2263, 3076),
                    c(2269, 3069, 2272, 3069),
                ),
                phase(E, green, alternating(ZulrahAction.Magic, 8)),
                phase(C, blue, s(2263, 3076), s(2263, 3073), s(2273, 3075), s(2273, 3078)),
                closing,
            ),
        )

    val all: List<ZulrahRotation> = listOf(one, two, three, four)

    /** The phase a rotation carries on from after the closing phase of the one before. */
    const val FIRST_PHASE_AFTER_WRAP = 1
}

/**
 * The fixed places venom clouds land: 3x3 clouds that tile the platform, so that a barrage always
 * puts a cloud on one of them and the arena fills up, one site at a time. Tiles are the middle of
 * the cloud, in ordinary map coordinates. (2272, 3070) is a variant of the south-east site that
 * only the opening and closing barrages use.
 */
object ZulrahCloudSites {
    val west = listOf(CoordGrid(2263, 3070, 0), CoordGrid(2263, 3073, 0), CoordGrid(2263, 3076, 0))
    val south =
        listOf(
            CoordGrid(2266, 3069, 0),
            CoordGrid(2269, 3069, 0),
            CoordGrid(2272, 3069, 0),
            CoordGrid(2272, 3070, 0),
        )
    val east = listOf(CoordGrid(2273, 3072, 0), CoordGrid(2273, 3075, 0), CoordGrid(2273, 3078, 0))

    val all: Set<CoordGrid> = (west + south + east).toSet()
}
