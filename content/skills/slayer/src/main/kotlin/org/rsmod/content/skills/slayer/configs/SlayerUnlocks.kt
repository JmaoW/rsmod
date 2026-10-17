package org.rsmod.content.skills.slayer.configs

/**
 * The rewards screen's unlock bits that other skills check through `SlayerEngine.isUnlocked`. The
 * names are the wiki's, as in `unlocks.tsv`.
 */
object SlayerUnlocks {
    /** Craft Slayer rings and eternal Slayer rings. */
    const val RING_BLING = 6

    /** Fletch broad arrows, broad bolts and amethyst broad bolts. */
    const val BROADER_FLETCHING = 7

    /** Make a Slayer helmet from its parts (the "Malevolent Masquerade" unlock). */
    const val MALEVOLENT_MASQUERADE = 5

    /** Bigger and Badder: superiors appear. The Passives relic gives it free. */
    const val BIGGER_AND_BADDER = 35

    /** Stop the Wyvern: a toggle, switched off in the rewards screen. */
    const val STOP_THE_WYVERN = 43

    /** Task Storage: listed on the rewards screen, but it cannot be bought here. */
    const val TASK_STORAGE = 51

    /** Revenenenenenants: a toggle, switched off in the rewards screen. */
    const val REVENENENENENANTS = 53

    /** The bits the cache's "Extend everything" price leaves out in Leagues, named by the wiki. */
    const val SUQ_A_NOTHER_ONE = 9
    const val I_HOPE_YOU_MITH_ME = 16
    const val WATCH_THE_BIRDIE = 17
    const val HOT_STUFF = 18
    const val LIKE_A_BOSS = 19
    const val SMELL_YA_LATER = 21
    const val BIRDS_OF_A_FEATHER = 22
    const val REPTILE_GOT_RIPPED = 30
    const val SEEING_RED = 34
    const val ADAMIND_SOME_MORE = 40
    const val RUUUUUNE = 41
    const val BASILOCKED = 47
    const val ACTUAL_VAMPYRE_SLAYER = 50
}
