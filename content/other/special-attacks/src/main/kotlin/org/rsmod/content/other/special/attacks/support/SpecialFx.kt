package org.rsmod.content.other.special.attacks.support

import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.game.type.synth.SynthType

/**
 * What a special attack plays when it is made: an animation and graphic on the attacker, and a
 * graphic on the target. Any part can be left out.
 */
class SpecialFx(
    val anim: SeqType? = null,
    val spot: SpotanimType? = null,
    val spotHeight: Int = 96,
    val targetSpot: SpotanimType? = null,
    val targetSpotHeight: Int = 96,
    val sound: SynthType? = null,
) {
    /** Plays the attacker's part of the effect. */
    fun play(access: ProtectedAccess) {
        if (anim != null) {
            access.anim(anim)
        }
        if (spot != null) {
            access.spotanim(spot, height = spotHeight, slot = constants.spotanim_slot_combat)
        }
        if (sound != null) {
            access.soundSynth(sound)
        }
    }

    /** Plays the target's part of the effect, [delay] client cycles from now. */
    fun playOn(target: PathingEntity, delay: Int = 0) {
        if (targetSpot != null) {
            target.spotanim(targetSpot, height = targetSpotHeight, delay = delay)
        }
    }

    fun playAll(access: ProtectedAccess, target: PathingEntity) {
        play(access)
        playOn(target)
    }
}

/**
 * One melee roll: whether the accuracy roll [accurate] landed, the [damage] rolled (`0` if it did
 * not) and the [maxHit] the damage was rolled against.
 */
class MeleeRoll(val accurate: Boolean, val damage: Int, val maxHit: Int) {
    /** A hit that took something off. */
    val landed: Boolean
        get() = damage > 0
}
