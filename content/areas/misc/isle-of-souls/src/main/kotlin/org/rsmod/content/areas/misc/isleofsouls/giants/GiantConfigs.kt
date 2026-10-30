package org.rsmod.content.areas.misc.isleofsouls.giants

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.synth.SynthReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.entity.npc.NpcMode

typealias giant_npcs = GiantNpcs

typealias giant_locs = GiantLocs

typealias giant_objs = GiantObjs

typealias giant_seqs = GiantSeqs

typealias giant_spotanims = GiantSpotanims

typealias giant_synths = GiantSynths

typealias giant_timers = GiantTimers

typealias giant_varps = GiantVarps

object GiantNpcs : NpcReferences() {
    /** Obor, the hill giant boss (the cache calls him `hillgiant_boss`). */
    val obor = find("hillgiant_boss")

    /** Bryophyta, the moss giantess (`gb_mossgiant`), and the growthlings she summons. */
    val bryophyta = find("gb_mossgiant")
    val growthling = find("gb_growthling")

    /**
     * Npcs nothing spawns: the chests' drop tables are stored on them (`isle.toml`), since the drop
     * tables are looked up by npc type.
     */
    val obor_chest = find("gb_hillgiant_chest")
    val bryophyta_chest = find("gb_mossgiant_chest")
}

object GiantLocs : LocReferences() {
    /** The two halves of Obor's gate (the way in, in the Edgeville Dungeon). */
    val obor_gate_north = find("hillgiant_boss_entrance_r")
    val obor_gate_south = find("hillgiant_boss_entrance_l")

    /** The two halves of the gate inside his lair: "Open" asks first, "Quick-exit" does not. */
    val obor_exit_east = find("hillgiant_boss_exit_r")
    val obor_exit_west = find("hillgiant_boss_exit_l")

    /** The rocks into his pit: "Climb". */
    val obor_rocks = find("hillgiant_boss_climbingrocks")
    val obor_chest = find("giantkey_chest_closed")
    val obor_chest_open = find("giantkey_chest_open")

    /** Bryophyta's gate (in the Varrock Sewers): "Open", "Check-Count". */
    val bryophyta_gate = find("gb_moss_door_in")

    /** The pile of rocks inside her lair: "Clamber", "Quick-exit". */
    val bryophyta_exit = find("gb_moss_caverock_exit")
    val bryophyta_chest = find("mossykey_chest_closed")
    val bryophyta_chest_open = find("mossykey_chest_open")

    /** The pile of logs with a bronze axe in it, and what it looks like once the axe is taken. */
    val logs_with_axe = find("gb_log_withaxe")
    val logs_without_axe = find("log_withoutaxe")
}

object GiantObjs : ObjReferences() {
    val giant_key = find("hillgiant_boss_key")
    val mossy_key = find("mossy_key")
    val giant_bones = find("giant_bones")
    val bryophytas_essence = find("gb_moss_essence")
    val bryophytas_staff_uncharged = find("nature_staff_uncharged")
    val bryophytas_staff = find("nature_staff_charged")
    val battlestaff = find("battlestaff")
    val bronze_axe = find("bronze_axe")
    val secateurs_magic = find("fairy_enchanted_secateurs")
    val secateurs_magic_nz = find("nzone_fairysecateurs")
    val uncut_ruby = find("uncut_ruby")
    val uncut_diamond = find("uncut_diamond")
}

object GiantSeqs : SeqReferences() {
    /** Obor's punch (the cache calls it his fire attack), the stamp and his death. */
    val obor_punch = find("giant_update_fire_attack")
    val obor_stamp = find("giant_basic_stamp")
    val obor_death = find("giant_update_basic_death")

    /** Bryophyta's punch and her earth blast, and her death. */
    val bryophyta_punch = find("giant_update_moss_attack")
    val bryophyta_cast = find("giant_basic_spell")
    val bryophyta_death = find("giant_update_moss_death")

    val growthling_attack = find("tanglefoot_attack")
    val growthling_death = find("tanglefoot_death")

    /** What a player does when knocked back, knocked down, or caught in a rockfall. */
    val player_flyback = find("human_troll_flyback_withsound")
    val player_knockdown = find("human_knockdown")
    val player_rockfall = find("human_rockfall_merge")

    /** The slide into and out of Obor's pit. */
    val player_climb = find("death_human_climbing_down")
    val player_open_chest = find("human_openchest")
    val player_bury = find("human_pickupfloor")
}

object GiantSpotanims : SpotanimReferences() {
    /** The splash where Obor stamps, and a rock falling on a tile. */
    val stamp_splash = find("earthblast_impact")
    val rockfall = find("rockfall")

    /** The earth strike a thrown boulder makes where it lands. */
    val boulder_impact = find("earthstrike_impact")

    /** Bryophyta's earth blast and where it lands. */
    val earth_blast = find("earthblast_travel")
    val earth_blast_impact = find("earthblast_impact")
}

object GiantSynths : SynthReferences() {
    val rockfall_a = find("giant_rockfall_a")
    val rockfall_b = find("giant_rockfall_b")
    val boulder_impact = find("giant_boulder_impact")
    val pit_climb = find("giant_pit_climb")
    val bury = find("giant_bury")
    val chest_open = find("giant_chest_open")
    val earth_blast_cast = find("earthblast_cast_and_fire")
    val earth_blast_hit = find("earthblast_hit")
}

object GiantTimers : TimerReferences() {
    val obor_tick = find("obor_tick")
    val bryophyta_tick = find("bryophyta_tick")
}

object GiantVarps : VarpReferences() {
    /**
     * What the player has done in the giants' lairs: [GiantFlags.OBOR_UNLOCKED] and
     * [GiantFlags.BRYOPHYTA_UNLOCKED] once a key has opened the gate,
     * [GiantFlags.BURY_WITHOUT_ASKING] once they chose "Yes, and don't ask again" for giant bones.
     */
    val flags = find("isle_giant_lair_flags")

    /** The client's own count of chests opened ("kills" in its kill count tab). */
    val obor_chests = find("total_hillgiant_boss_kills")
    val bryophyta_chests = find("total_bryophyta_kills")
}

object GiantFlags {
    const val OBOR_UNLOCKED = 1
    const val BRYOPHYTA_UNLOCKED = 2
    const val BURY_WITHOUT_ASKING = 4
}

internal object GiantVarpBuilder : VarpBuilder() {
    init {
        build("isle_giant_lair_flags") { permanent = true }
    }
}

/**
 * What the giants need to fight. Both are moved and aimed by their lair director (they never hunt
 * or turn on a player who hits them), and neither has a flinch animation of its own beyond the
 * cache's defend one. The growthlings chase across the whole lair. Needs `packCache`.
 */
internal object GiantNpcEdits : NpcEditor() {
    init {
        for (type in listOf(giant_npcs.obor, giant_npcs.bryophyta)) {
            edit(type) { defaultMode = NpcMode.None }
        }
        edit(giant_npcs.growthling) {
            maxRange = LAIR_LEASH
            giveChase = true
            defaultMode = NpcMode.None
            attackRange = 1
        }
    }

    /** Further than any two tiles of the lair are apart, so a chase is never dropped. */
    const val LAIR_LEASH = 40
}
