package org.rsmod.content.skills.agility.configs

/**
 * Builds the steps, animations and messages of a crossing for each [ShortcutKind]. The isle has no
 * shortcut of its own any more (the five it had were removed 2026-10-10); the Brimhaven Agility
 * Arena makes its obstacles out of [Shortcut]s and crosses them with these.
 */
object Shortcuts {
    private const val SPOT_JUMP = "human_spot_jump"

    /** The steps that take a player across [shortcut] from the end [from] to the end [to]. */
    fun steps(shortcut: Shortcut, from: Tile, to: Tile): List<Step> {
        val forward = from == shortcut.a
        return when (shortcut.kind) {
            ShortcutKind.Stones ->
                hops(from, (if (forward) shortcut.stones else shortcut.stones.reversed()) + to)
            ShortcutKind.Pillar ->
                if (shortcut.stones.isEmpty()) {
                    hops(from, pillarHops(from, to))
                } else {
                    hops(from, (if (forward) shortcut.stones else shortcut.stones.reversed()) + to)
                }
            ShortcutKind.Log,
            ShortcutKind.RopeBalance,
            ShortcutKind.Plank -> listOf(Step.Walk(listOf(to)))
            ShortcutKind.RopeSwing ->
                listOf(
                    Step.Face(to),
                    Step.Anim("human_ropeswing_long"),
                    Step.Wait(1),
                    Step.Move(to, null, 15, 60, 2, direction(from, to)),
                )
            ShortcutKind.LowWall ->
                listOf(
                    Step.Face(to),
                    Step.Move(to, "human_jump_hurdle", 0, 90, 3, direction(from, to)),
                )
            ShortcutKind.Ledge ->
                listOf(Step.Anim("human_into_sidestepl"), Step.Wait(1), Step.Walk(listOf(to)))
            ShortcutKind.MonkeyBars ->
                listOf(
                    Step.Anim("human_monkeybars_on"),
                    Step.Wait(1),
                    Step.Walk(listOf(to)),
                    Step.Anim("human_monkeybars_off"),
                    Step.Wait(1),
                )
            ShortcutKind.HandHolds -> listOf(Step.Walk(listOf(to)))
            ShortcutKind.Pipe -> {
                val via = checkNotNull(shortcut.via)
                val squeeze = "human_doublepipesqueeze"
                listOf(
                    Step.Move(via, squeeze, 30, 126, 4, direction(from, via)),
                    Step.Wait(1),
                    Step.Move(to, squeeze, 30, 126, 4, direction(via, to)),
                )
            }
        }
    }

    /** A jump to each tile of [path] in turn, one stone at a time. */
    private fun hops(from: Tile, path: List<Tile>): List<Step> =
        path.flatMapIndexed { index, tile ->
            val last = if (index == 0) from else path[index - 1]
            listOfNotNull(
                if (index > 0) Step.Wait(1) else null,
                Step.Move(tile, SPOT_JUMP, 15, 35, 1, direction(last, tile)),
            )
        }

    /** The tiles a jump across pillars lands on: every other one, then the far side. */
    private fun pillarHops(from: Tile, to: Tile): List<Tile> {
        val dx = Integer.signum(to.x - from.x)
        val dz = Integer.signum(to.z - from.z)
        val length = Math.abs(to.x - from.x) + Math.abs(to.z - from.z)
        val hops = ArrayList<Tile>()
        var step = 2
        while (step < length - 1) {
            hops += Tile(from.x + dx * step, from.z + dz * step, from.level)
            step += 2
        }
        hops += to
        return hops
    }

    fun render(shortcut: Shortcut): String? =
        when (shortcut.kind) {
            ShortcutKind.Log,
            ShortcutKind.RopeBalance,
            ShortcutKind.Plank -> "human_walk_logbalance"
            ShortcutKind.Ledge -> "human_walk_sidestepl"
            ShortcutKind.MonkeyBars -> "human_monkeybars_walk"
            ShortcutKind.HandHolds -> "agilityarena_handholds_middle"
            else -> null
        }

    fun start(shortcut: Shortcut): String? =
        when (shortcut.kind) {
            ShortcutKind.Log -> "You walk carefully across the slippery log..."
            ShortcutKind.RopeSwing -> "You skillfully swing across."
            ShortcutKind.Pipe -> "You squeeze into the pipe."
            ShortcutKind.Stones -> "You attempt to balance on the stepping stone."
            else -> null
        }

    fun end(shortcut: Shortcut): String? =
        when (shortcut.kind) {
            ShortcutKind.Log -> "...You make it safely to the other side."
            else -> null
        }

    /**
     * An exact move direction (0 south, 512 west, 1024 north, 1536 east) from one tile to another.
     */
    fun direction(from: Tile, to: Tile): Int {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return if (Math.abs(dx) >= Math.abs(dz)) {
            if (dx >= 0) 1536 else 512
        } else {
            if (dz >= 0) 1024 else 0
        }
    }
}
