package org.rsmod.content.skills.prayer.configs

import org.rsmod.api.type.refs.queue.QueueReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences

internal object PrayerSeqs : SeqReferences() {
    /** The anim used for burying bones. */
    val bury = find("human_pickupfloor")

    /** The anim used for praying at an altar. */
    val pray = find("human_pray")

    /** The anim used for offering bones on an altar. */
    val offer = find("human_cooking")
}

internal object PrayerSpotanims : SpotanimReferences() {
    /** The smoke that rises from an altar when a bone is offered. */
    val offer = find("poh_bone_sacrifice")
}

internal object PrayerQueues : QueueReferences() {
    /** Fires a tick after a bone is dug in, with the second chat message. */
    val bury_message = find("prayer_bury_message")
}
