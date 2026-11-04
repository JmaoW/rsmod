package org.rsmod.content.areas.misc.isleofsouls.colosseum

/** The kinds of monster the Colosseum's waves send in. */
enum class ColosseumMonster {
    Berserker,
    Seer,
    Archer,
    Shaman,
    Jaguar,
    Javelin,
    Manticore,
    Shockwave,
    Minotaur,
    Sol;

    val isWarband: Boolean
        get() = this == Berserker || this == Seer || this == Archer

    companion object {
        val WARBAND = listOf(Berserker, Seer, Archer)
    }
}

/**
 * What each wave sends in. The wiki's wave breakdown ("Fortis Colosseum/Strategies"), which tsps'
 * port (from LlemonDuck's RuneLite plugin) matches:
 * - the Fremennik trio every wave but the last, the serpent shaman on waves 1-6;
 * - Javelin Colossi: one on wave 2, two on wave 3, then two on even and one on odd waves from 5;
 * - Manticores: one on waves 4-8, two on 9-11;
 * - the Shockwave Colossus on waves 7, 8 and 11 (two with Dynamic Duo);
 * - wave 12 is Sol Heredit alone.
 *
 * Reinforcements arrive if a wave is not cleared within 40 seconds: the jaguar on waves 1-6, a
 * shaman on waves 4-6 and 10-11, the Minotaur on waves 7-11.
 */
object ColosseumWaves {
    const val FINAL_WAVE = 12

    /** 40 seconds (wiki). */
    const val REINFORCEMENT_TICKS = 67

    /** The monsters wave [wave] starts with. [roll] picks Quartet's extra warbander. */
    fun starting(
        wave: Int,
        modifiers: ColosseumModifierSet,
        roll: (Int) -> Int,
    ): List<ColosseumMonster> {
        if (wave >= FINAL_WAVE) {
            return listOf(ColosseumMonster.Sol)
        }
        val monsters = ArrayList(ColosseumMonster.WARBAND)
        if (modifiers.has(ColosseumModifier.Quartet)) {
            monsters += ColosseumMonster.WARBAND[roll(ColosseumMonster.WARBAND.size)]
        }
        if (wave <= 6) {
            monsters += ColosseumMonster.Shaman
        }
        repeat(javelins(wave)) { monsters += ColosseumMonster.Javelin }
        if (wave >= 4) {
            repeat(if (wave <= 8) 1 else 2) { monsters += ColosseumMonster.Manticore }
        }
        if (wave == 7 || wave == 8 || wave == 11) {
            val duo = modifiers.has(ColosseumModifier.DynamicDuo)
            repeat(if (duo) 2 else 1) { monsters += ColosseumMonster.Shockwave }
        }
        return monsters
    }

    fun javelins(wave: Int): Int =
        when {
            wave == 2 || wave == 3 -> wave - 1
            wave >= 5 -> 2 - wave % 2
            else -> 0
        }

    /** The monsters that join wave [wave] after [REINFORCEMENT_TICKS]. */
    fun reinforcements(wave: Int): List<ColosseumMonster> {
        val monsters = ArrayList<ColosseumMonster>()
        if (wave <= 6) {
            monsters += ColosseumMonster.Jaguar
        }
        if (wave in 4..6 || wave == 10 || wave == 11) {
            monsters += ColosseumMonster.Shaman
        }
        if (wave in 7..11) {
            monsters += ColosseumMonster.Minotaur
        }
        return monsters
    }
}

/** Which waves a run plays: all twelve, or the event's shortened list. */
enum class ColosseumMode(val key: String) {
    Full("full"),
    Shortened("shortened");

    companion object {
        fun fromKey(key: String): ColosseumMode? = entries.firstOrNull { it.key == key }
    }
}
