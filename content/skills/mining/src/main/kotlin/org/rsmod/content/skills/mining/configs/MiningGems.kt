package org.rsmod.content.skills.mining.configs

import org.rsmod.api.random.GameRandom
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.type.obj.ObjType

internal object MiningGemObjs : ObjReferences() {
    val uncut_sapphire = find("uncut_sapphire")
    val uncut_emerald = find("uncut_emerald")
    val uncut_ruby = find("uncut_ruby")
    val uncut_diamond = find("uncut_diamond")
}

/**
 * The uncut gems that any ore can turn up while it is mined: one ore in [CHANCE] comes with a gem.
 *
 * The chance and the weights between gems are close to the wiki's but not exact.
 */
internal object MiningGems {
    private class Entry(val gem: ObjType, val weight: Int)

    private val table =
        listOf(
            Entry(MiningGemObjs.uncut_sapphire, 32),
            Entry(MiningGemObjs.uncut_emerald, 16),
            Entry(MiningGemObjs.uncut_ruby, 8),
            Entry(MiningGemObjs.uncut_diamond, 2),
        )

    private val totalWeight = table.sumOf { it.weight }

    /** Rolls for a gem with a mined ore. Returns the uncut gem, or `null` for no gem. */
    fun roll(random: GameRandom): ObjType? {
        if (random.of(1, CHANCE) != 1) {
            return null
        }
        var pick = random.of(1, totalWeight)
        for (entry in table) {
            pick -= entry.weight
            if (pick <= 0) {
                return entry.gem
            }
        }
        return table.last().gem
    }

    /** Whether [obj] is one of the gems, so a rock that gives gems as ore is not rolled twice. */
    fun isGem(obj: ObjType): Boolean = table.any { it.gem.id == obj.id }

    private const val CHANCE = 256
}
