package org.rsmod.content.other.drops

/**
 * Items the drop data names but the league never drops. The loader skips every line for these (no
 * warning), so a fresh wiki import cannot bring them back. Keyed by lowercase display name.
 */
internal object ExcludedDrops {
    private val wilderness = setOf("looting bag")

    /**
     * The bones handed in for the Rag and Bone Man quests, exactly as the wiki lists them (not the
     * buryable bones, and not other items that merely look similar).
     */
    private val ragAndBoneMan =
        setOf(
            // Rag and Bone Man I.
            "giant rat bone",
            "unicorn bone",
            "bear ribs",
            "ram skull",
            "goblin skull",
            "big frog leg",
            "monkey paw",
            "giant bat wing",
            // Rag and Bone Man II.
            "wolf bone",
            "bat wing",
            "rat bone",
            "baby dragon bone",
            "ogre ribs",
            "jogre bone",
            "zogre bone",
            "mogre bone",
            "dagannoth ribs",
            "snake spine",
            "zombie bone",
            "werewolf bone",
            "moss giant bone",
            "fire giant bone",
            "ice giant ribs",
            "terrorbird wing",
            "ghoul bone",
            "troll bone",
            "seagull wing",
            "undead cow ribs",
            "experiment bone",
            "rabbit bone",
            "basilisk bone",
            "desert lizard bone",
            "cave goblin skull",
            "vulture wing",
            "jackal bone",
        )

    private val all: Set<String> = wilderness + ragAndBoneMan

    fun isExcluded(displayName: String): Boolean = displayName.lowercase() in all
}
