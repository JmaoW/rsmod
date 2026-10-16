package org.rsmod.content.skills.hunter.configs

import org.rsmod.api.random.GameRandom
import org.rsmod.game.type.obj.ObjType

/** What a catch gives. */
object HunterLoot {
    /** One kind of item and how many. */
    class Item(val obj: ObjType, val count: Int = 1)

    /** One in this many tecu salamanders caught is the adult one. */
    const val TECU_RARITY = 1000

    /** The items for one catch of [creature] by a trapper at [level]. */
    fun roll(creature: Creature, level: Int, random: GameRandom): List<Item> {
        val loot = ArrayList<Item>()
        for (reward in creature.loot) {
            var obj = reward.obj
            if (creature.key == "tecu_salamander" && random.of(TECU_RARITY) == 0) {
                obj = hunter_objs.tecu_salamander
            }
            val count =
                if (reward.max > reward.min) reward.min + random.of(reward.max - reward.min + 1)
                else reward.min
            loot += Item(obj, count)
        }
        val fur = creature.fur
        if (fur != null) {
            // Nobody has published how a big cat's fur goes tatty; this is a straight line in the
            // trapper's level.
            val perfect = random.randomDouble() < Creature.chance(0, 255, level)
            loot += Item(if (perfect) fur.perfect else fur.tatty)
        }
        return loot
    }
}
