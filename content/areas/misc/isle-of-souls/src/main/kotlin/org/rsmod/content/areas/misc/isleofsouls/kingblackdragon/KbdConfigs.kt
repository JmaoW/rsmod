package org.rsmod.content.areas.misc.isleofsouls.kingblackdragon

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.game.entity.npc.NpcMode

typealias kbd_npcs = KbdNpcs

typealias kbd_locs = KbdLocs

typealias kbd_seqs = KbdSeqs

typealias kbd_timers = KbdTimers

object KbdNpcs : NpcReferences() {
    /** The King Black Dragon. The drop table and the tasks know it by this type. */
    val boss = find("king_dragon")
}

object KbdLocs : LocReferences() {
    /**
     * The lever that opens the way into the lair: "Pull", "Commune" and "Private". The real one
     * stands in a cave in the Wilderness; the isle has a copy of it on a wall in the Revenant
     * Caves.
     */
    val entrance = find("dragonkinginlever")

    /** The lever inside the lair that leads back out: "Pull". */
    val exit = find("dragonkingoutlever")
}

object KbdSeqs : SeqReferences() {
    /** The animation of pulling a lever (2140). */
    val pull = find("macro_lever_switch_down")

    /**
     * The bite (91, "head attack"): tsps and the old games use it for the melee attack. The cache
     * only gives the dragons' claw swipe (80), which was the wrong animation. A guess: `::kbdanim`
     * plays any animation on the dragon to find the right one.
     */
    val bite = find("dragon_head_attack")
}

object KbdTimers : TimerReferences() {
    /** Drives a run: every player inside has it ticking each game tick. */
    val tick = find("kbd_tick")
}

/**
 * What the King Black Dragon needs to fight in a team's lair: [KingBlackDragon] aims it at the
 * players (it never hunts or turns on its own), it walks the whole lair after them, and every hit
 * on it is counted for whoever did the most damage. Needs `packCache`.
 */
internal object KbdNpcEdits : NpcEditor() {
    init {
        edit(kbd_npcs.boss) {
            defaultMode = NpcMode.None
            maxRange = LAIR_LEASH
            giveChase = true
            heroCount = HERO_SLOTS
            param[params.attack_anim] = kbd_seqs.bite
        }
    }

    /** Further than any two tiles of the lair are apart, so a chase is never dropped. */
    const val LAIR_LEASH = 60

    /** How many players' damage is kept to decide who gets the drops. */
    const val HERO_SLOTS = 16
}
