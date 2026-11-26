package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/** The skilling pets, each rolled by the activities of one skill. */
public enum class SkillingPet {
    /** Fishing. */
    Heron,

    /** Mining. [SkillingPetRoll.variant] is the ore ("tin", "coal", ...) for the golem's look. */
    RockGolem,

    /** Woodcutting. */
    Beaver,

    /** Hunter (chinchompas). [SkillingPetRoll.variant] is "grey", "red" or "black". */
    BabyChinchompa,

    /** Agility. */
    GiantSquirrel,

    /** Farming. */
    Tangleroot,

    /** Thieving. */
    Rocky,

    /** Runecraft. [SkillingPetRoll.variant] is the altar's rune ("air", "fire", ...). */
    RiftGuardian,
}

/**
 * Published by a skill when [player] finishes an action that can give a skilling pet: one roll of
 * the wiki's `1 in (baseChance - level * 25)`, where the level is the player's base level in the
 * pet's skill (fifteen times as likely at 200 million experience). The pet module does the roll and
 * hands the pet out. Skills publish this before they hand over the resource, so a pet that has to
 * go into the inventory takes the last free slot ahead of it (as in the game).
 *
 * [rolls] repeats the roll that many times (runecraft rolls once per essence).
 */
public class SkillingPetRoll(
    public val player: Player,
    public val pet: SkillingPet,
    public val baseChance: Int,
    public val variant: String? = null,
    public val rolls: Int = 1,
) : UnboundEvent
