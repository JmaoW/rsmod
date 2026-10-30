package org.rsmod.content.areas.misc.isleofsouls.toa

import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid

typealias wardens_npcs = WardensNpcs

typealias wardens_locs = WardensLocs

typealias wardens_seqs = WardensSeqs

typealias wardens_spotanims = WardensSpotanims

object WardensNpcs : NpcReferences() {
    /** Osmumten before the fight: "Talk-to", "Begin". */
    val osmumten = find("toa_osmumten_wardens_vis")

    val obelisk_inactive = find("toa_wardens_p1_obelisk_npc_inactive")
    val obelisk = find("toa_wardens_p1_obelisk_npc")
    val obelisk_p2 = find("toa_wardens_p2_obelisk_npc")
    val energy = find("toa_wardens_energy")

    val elidinis_inactive = find("toa_warden_elidinis_phase1_inactive")
    val tumeken_inactive = find("toa_warden_tumeken_phase1_inactive")
    val elidinis_charging = find("toa_warden_elidinis_phase1")
    val tumeken_charging = find("toa_warden_tumeken_phase1")

    /** Phase two: weak to magic (praying melee and ranged), weak to ranged, and kneeling. */
    val elidinis_mage = find("toa_warden_elidinis_phase2_mage")
    val elidinis_range = find("toa_warden_elidinis_phase2_range")
    val elidinis_exposed = find("toa_warden_elidinis_phase2_exposed")
    val tumeken_mage = find("toa_warden_tumeken_phase2_mage")
    val tumeken_range = find("toa_warden_tumeken_phase2_range")
    val tumeken_exposed = find("toa_warden_tumeken_phase2_exposed")

    /** Phase three: fallen (the one not fought in phase two), the fight, and the skulls out. */
    val elidinis_fallen = find("toa_warden_elidinis_phase3_inactive")
    val tumeken_fallen = find("toa_warden_tumeken_phase3_inactive")
    val elidinis_final = find("toa_warden_elidinis_phase3")
    val tumeken_final = find("toa_warden_tumeken_phase3")
    val elidinis_charging_skulls = find("toa_warden_elidinis_phase3_charging")
    val tumeken_charging_skulls = find("toa_warden_tumeken_phase3_charging")
    val tumeken_death = find("toa_warden_p3_death_tumeken")
    val elidinis_death = find("toa_warden_p3_death_elidinis")

    val tumeken_core = find("toa_warden_tumeken_core")
    val elidinis_core = find("toa_warden_elidinis_core")
    val siphon = find("wardens_p3_orb_blue")

    val zebak_phantom = find("toa_wardens_zebak")
    val baba_phantom = find("toa_wardens_baba")
    val kephri_phantom = find("toa_wardens_kephri")
    val akkha_phantom = find("toa_wardens_akkha")
}

object WardensLocs : LocReferences() {
    val crystal = find("wardens_teleport")

    /** The crystal that rises once the final Warden falls (TSPS's id). */
    val final_crystal = find("toa_teleport_crystal_continue_wardens")
    val platform = find("toa_wardens_default_charging_platform")

    /** The charge platforms, one for each 5% (0-20). */
    val platforms = (0..20).map { find("toa_wardens_default_charging_platform_$it") }
    val elidinis_trap = find("toa_wardens_pyramid01")
    val tumeken_trap = find("toa_wardens_pyramid02")
    val elidinis_trap_load = find("toa_wardens_pyramid01_load")
    val tumeken_trap_load = find("toa_wardens_pyramid02_load")
    val siphon_block = find("invisible_type8_blocking")
}

object WardensSeqs : SeqReferences() {
    val osmumten_leave = find("ghost_update_normal_despawn")
    val obelisk_cast = find("fx_obelisk_wardens01_cast")
    val obelisk_idle = find("fx_obelisk_wardens01_charge")
    val obelisk_death = find("fx_obelisk_wardens02_death")
    val obelisk_open = find("fx_obelisk_wardens02_open")
    val obelisk_bomb = find("fx_obelisk_wardens02_bomb")
    val obelisk_close = find("fx_obelisk_wardens02_close")
    val obelisk_charging =
        listOf(
            find("fx_obelisk_wardens01_charge_part1"),
            find("fx_obelisk_wardens01_charge_part2"),
            find("fx_obelisk_wardens01_charge_part3"),
        )
    val trap_attack = find("toa_wardens01_pyramid_attack")
    val trap_leave = find("toa_wardens01_pyarmid_leave")
    val entomb = find("toa_wardens_entomb")
    val entomb_loop = find("toa_wardens_entomb_loop")
    val entomb_end = find("toa_wardens_entomb_end")
    val pushed = find("agilityarena_player_spikedback")
    val warden_special = find("npc_wardens_impact01")
    val warden_explode = find("npc_wardens_explosion01")
    val warden_revived = find("npc_wardens_wake02")
    val energy_move = find("zuk_proj")
    val energy_charge = find("fx_wardens_energy_death01")
    val awake = find("npc_wardens_wake01")
    val melee = find("npc_wardens_melee01")
    val kneel = find("npc_wardens_kneel01")
    val stand = find("npc_wardens_standup01")
    val throw_magic = find("npc_wardens_magic01")
    val throw_ranged = find("npc_wardens_ranged01")
    val death = find("npc_wardens_death01")
    val floor_slams =
        listOf(
            find("npc_wardens_attackleft01"),
            find("npc_wardens_attackleft02"),
            find("npc_wardens_attackright01"),
            find("npc_wardens_attackright02"),
            find("npc_wardens_attackcenter01"),
            find("npc_wardens_attackcenter02"),
        )
    val skull_send = find("npc_wardens_charge01")
    val skull_return = find("npc_wardens_stagger01")
    val skull_fail = find("npc_wardens_release01")
    val enraged = find("npc_wardens_enraged01")
    val enraging = find("npc_wardens_enraging01")
    val hidden = (1..5).map { find("spotanim_wardens01_death02_offset0$it") }
    val zebak_phantom = find("npc_zebak01_attack_ranged_enraged")
    val akkha_ranged = find("npc_akkha_attack_ranged_spear")
    val akkha_magic = find("npc_akkha_attack_magic_spear")
    val akkha_switch = find("npc_akkha_attack_magic_02_spear")
    val baba_phantom = find("npc_baba_attack_melee")
    val kephri_phantom = find("npc_kephri_attack_magic_radial")
}

object WardensSpotanims : SpotanimReferences() {
    val special_traps = find("spotanim_wardens01_magic01_activate01")
    val special_ball = find("spotanim_wardens01_magic02_activate01")
    val warden_death = find("spotanim_wardens01_explosion01_explode01")
    val warden_legs = find("spotanim_wardens01_explosion01_idle01")
    val warden_revive = find("spotanim_wardens01_wakeup02")
    val ball_land = find("tob_sotetseg_sharedattack_impact")
    val core_explode = find("vfx_scarab_explosion02")
    val lightning_warn = find("wardens_lightning_warn")
    val wheel_start = find("spotanim_amascut01_strike01_highlight01")
    val wheel_end = find("spotanim_amascut01_strike01")
    val incoming = find("gargboss_debris_shadow_120")
    val isolation = find("spotanim_wardens01_dronestrike01_fire01")
    val imprison_miss = find("toa_wardens_entomb_miss")
    val path = find("obelisk_explode_energy")
    val explosion = find("vfx_scarab_explosion03")
    val floor =
        listOf(
            find("spotanim_wardens01_undertile01_explode01"),
            find("spotanim_wardens01_undertile01_explode02"),
            find("spotanim_wardens01_undertile01_explode03"),
            find("spotanim_wardens01_undertile01_explode04"),
        )
    val thunder_incoming = find("gargboss_debris_shadow_90")
    val lightning = find("wardens_lightning")
    val zebak_magic = find("fireblast_impact")
    val zebak_ranged = find("darkbow_smoke_arrow_impact")
    val rubble = find("toa_baba_rock_fall")
    val elidinis_ball = find("spotanim_wardens_phase01_ball02")
    val tumeken_ball = find("spotanim_wardens_phase01_ball01")
    val core = find("projectile_warden_elidinis_core")
    val skull_bomb = find("fx_wardens_bomb02")
    val magic = find("fx_wardens_bomb01")
    val ranged = find("projectile_warden_ranged01")
    val imprison = find("toa_wardens_entomb_travel")
    val divine_melee = find("toa_wardens_prayer_melee_travel")
    val divine_magic = find("toa_wardens_prayer_magic_travel")
    val divine_ranged = find("toa_wardens_prayer_ranged_travel")
    val siphon = find("fx_wardens_charging01")
    val skull = find("fx_wardens_bomb03")
    val floor_removal = find("spotanim_wardens_tiles01")
    val zebak_magic_rise = find("zebak_mage_projanim_initial")
    val zebak_ranged_rise = find("zebak_range_projanim_initial")
    val zebak_magic_split = find("zebak_mage_projanim_split")
    val zebak_ranged_split = find("zebak_ranged_fragment01")
    val akkha_magic = find("akkha_magic_travel")
    val akkha_ranged = find("akkha_ranged_travel")
    val kephri_launch = find("vorkath_area_travel")
    val kephri_bomb = find("vorkath_area_travel_small")
    val kephri_explode = find("vfx_scarab_explosion02")
}

/** The two Wardens, the side each stands on and the way each faces: in, at the other (TSPS). */
enum class Warden(val spot: CoordGrid, val orbSpawn: CoordGrid, val facing: Direction) {
    Elidinis(CoordGrid(3792, 5152, 1), CoordGrid(3806, 5154, 1), Direction.East),
    Tumeken(CoordGrid(3820, 5152, 1), CoordGrid(3810, 5154, 1), Direction.West);

    val other: Warden
        get() = if (this == Elidinis) Tumeken else Elidinis
}

/**
 * Shared bits of the two Wardens rooms: flat tile damage that the raid level scales, protection
 * prayers switched off, and a shove off a tile.
 */
abstract class WardensRuntimeBase(env: ToaRoomEnv, raid: ToaRaid, room: ToaRoom) :
    ToaRoomRuntime(env, raid, room) {
    /** Unblockable tile damage, scaled for the raid level, plus up to [spread] more. */
    fun tileHit(player: Player, base: Int, spread: Int = 0) {
        hurtFlat(player, maxHit(base) + if (spread > 0) env.random.of(spread + 1) else 0)
    }

    fun disablePrayers(player: Player) {
        for (varbit in
            listOf(
                varbits.protect_from_melee,
                varbits.protect_from_missiles,
                varbits.protect_from_magic,
            )) {
            VarPlayerIntMapSetter.set(player, varbit, 0)
        }
        player.overheadIcon = null
        player.mes("<col=ff3045>Your protection prayers have been disabled!</col>")
    }

    /** Pushes [player] off their tile to the nearest free one, one or two tiles away. */
    fun shove(player: Player) {
        val at = raid.realOf(player.coords) ?: return
        for (radius in 1..2) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (abs(dx) != radius && abs(dz) != radius) {
                        continue
                    }
                    val tile = at.translate(dx, dz)
                    if (walkable(tile) && room.inChallenge(tile)) {
                        player.anim(wardens_seqs.pushed)
                        knockback(player, tile, cycles = 30)
                        return
                    }
                }
            }
        }
    }

    fun standingOn(player: Player, real: CoordGrid): Boolean = raid.realOf(player.coords) == real

    fun chebyshev(a: CoordGrid, b: CoordGrid): Int = max(abs(a.x - b.x), abs(a.z - b.z))
}

/**
 * The Wardens, first room (Amascut's Promise). Osmumten waits until everyone chooses "Begin" (or
 * two minutes after the first does). Phase one: the obelisk is attacked while charging orbs flow to
 * the two Wardens' platforms (a player standing on an orb takes it, for 3 damage); a full platform
 * calls rotating blades or an energy ball (spread out for Elidinis, stack for Tumeken). Phase two:
 * the more charged Warden fights, praying melee and one other style; breaking its shield exposes
 * its core, whose damage passes through five-fold, while the obelisk casts beams, skull bombs and
 * the wheel in turn. Its fall sends the party to the final room.
 *
 * The wiki gives the phases, the 3 per orb, the alternating specials and how the balls punish
 * spacing, the phase-two prayers and specials, the shield (140) and core rules, the core exposure
 * times. TSPS (Near-Reality) gives the tiles (generated into [WardensData]), the timings and the
 * damage numbers.
 */
class WardensRuntime(env: ToaRoomEnv, raid: ToaRaid) :
    WardensRuntimeBase(env, raid, ToaRoom.WardensFirst) {
    private class Orb(val npc: Npc, val path: List<CoordGrid>, val warden: Warden) {
        var index = 0
        var charged = false
    }

    private class WardenState(val warden: Warden) {
        var npc: Npc? = null
        var percent = 0
        var chargeTicks = 3
        var fullCharges = 0
        var legs = false
    }

    /** 0 the obelisk, 1 the Warden, 2 on the way to the final room. */
    var phase = 0
        private set

    var obelisk: Npc? = null
        private set

    /** The Warden fighting in phase two, and which one it is. */
    var fighting: Npc? = null
        private set

    var fightingWarden: Warden? = null
        private set

    var core: Npc? = null
        private set

    /** The tests turn the Warden's own attacks off to work on one rule at a time. */
    var fightEnabled = true

    private var osmumten: Npc? = null
    private val ready = HashSet<Int>()
    private var readyDeadline = -1
    private val orbs = ArrayList<Orb>()
    private val orbIndex = IntArray(2)
    private var orbTicks = 2
    private val states = Warden.entries.associateWith { WardenState(it) }
    private val trapsLive = BooleanArray(2)
    private val stoned = HashMap<Int, Int>()
    private var down = false
    private var canAttack = false
    private var shieldHp = 0

    /** The last tick a hit got through to the shield; left alone, the shield slowly comes back. */
    private var lastShieldHit = 0
    private var realHp = 0
    private var realTotal = 0
    private var coreTicks = 0
    private var magicForm = true
    private var nextAttack = 0
    private var canDoSpecial = false
    private var downCycles = 0
    private var obeliskCharge = 40
    private var obeliskTicks = 0
    private var thunderTicks = 0
    private var performingSpecial = false
    private var specialIndex = 0
    private var wheelIndex = 0
    private var chargeStage = -1

    val isDown: Boolean
        get() = down

    fun percentOf(warden: Warden): Int = states.getValue(warden).percent

    fun isStoned(player: Player): Boolean = (stoned[player.accountId] ?: -1) > now

    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun build() {
        phase = 0
        ready.clear()
        readyDeadline = -1
        down = false
        canAttack = false
        fighting = null
        fightingWarden = null
        core = null
        osmumten =
            spawn(
                wardens_npcs.osmumten,
                OSMUMTEN_SPOT,
                scale = false,
                points = 0.0,
                facing = Direction.South,
            )
        obelisk =
            spawn(
                wardens_npcs.obelisk_inactive,
                OBELISK_SPOT,
                points = ToaPoints.Multiplier.OBELISK,
            )
        for (state in states.values) {
            state.percent = 0
            state.chargeTicks = 3
            state.fullCharges = 0
            state.legs = false
            val type =
                if (state.warden == Warden.Elidinis) wardens_npcs.elidinis_inactive
                else wardens_npcs.tumeken_inactive
            state.npc =
                spawn(
                    type,
                    state.warden.spot,
                    scale = false,
                    points = 0.0,
                    facing = state.warden.facing,
                )
            setPlatform(state)
        }
    }

    private fun setPlatform(state: WardenState) {
        val stage = (state.percent / 5).coerceIn(0, 20)
        swapLoc(
            state.warden.spot,
            listOf(wardens_locs.platform) + wardens_locs.platforms,
            wardens_locs.platforms[stage],
        )
    }

    override fun onIdle() {
        if (readyDeadline > 0 && now >= readyDeadline) {
            start()
        }
    }

    /** Osmumten's "Begin": the fight starts once every player in the room is ready. */
    fun setReady(player: Player) {
        if (stage != Stage.Idle) {
            return
        }
        val fresh = ready.add(player.accountId)
        if (readyDeadline < 0) {
            readyDeadline = now + READY_TIMEOUT
        }
        val inRoom = roomPlayers()
        val count = inRoom.count { it.accountId in ready }
        if (count >= inRoom.size) {
            start()
            return
        }
        if (fresh) {
            for (other in inRoom) {
                other.mes("$count/${inRoom.size} party members are now ready.")
            }
        }
        player.mes(
            "The challenge will begin once everyone else in your party is ready or after 2 minutes " +
                "have passed from the first ready up."
        )
    }

    override fun onStart() {
        readyDeadline = -1
        sound(toa_synths.wardens_start, roomPlayers())
        osmumten?.let { npc ->
            npc.anim(wardens_seqs.osmumten_leave)
            osmumten = null
            later(ticksOf(wardens_seqs.osmumten_leave)) { despawn(npc) }
        }
        obelisk?.let {
            it.transmog(env.npcTypes[wardens_npcs.obelisk], Int.MAX_VALUE)
            bossBar = it
        }
        states
            .getValue(Warden.Elidinis)
            .npc
            ?.transmog(env.npcTypes[wardens_npcs.elidinis_charging], Int.MAX_VALUE)
        states
            .getValue(Warden.Tumeken)
            .npc
            ?.transmog(env.npcTypes[wardens_npcs.tumeken_charging], Int.MAX_VALUE)
    }

    override fun onReset() {
        for (npc in npcs.toList()) {
            despawn(npc)
        }
        orbs.clear()
        stoned.clear()
        restoreLocs()
        clear()
        build()
    }

    // Hits
    // --------------------------------------------------------------------------------------------

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (source != null && isStoned(source)) {
            hit.damage = 0
            return
        }
        when {
            npc === obelisk -> {
                if (phase != 0) {
                    hit.damage = 0
                } else if (hit.damage >= npc.hitpoints) {
                    hit.damage = npc.hitpoints - 1
                    later(0) { startWardenPhase() }
                }
            }
            npc === fighting -> {
                if (down || !canAttack) {
                    hit.damage = 0
                    return
                }
                // Only the style its form does not pray against breaks the shield, and every hit
                // lands (wiki: 100% accuracy on the shield). The engine has already rolled
                // accuracy, so a miss is turned into a hit of 1 up to the player's max hit. How the
                // real game scales that damage with accuracy bonuses is not published.
                val wanted = if (magicForm) HitType.Magic else HitType.Ranged
                if (hit.type != wanted) {
                    hit.damage = 0
                    return
                }
                if (hit.damage <= 0 && source != null) {
                    val max = source.vars[varps.com_maxhit]
                    if (max > 0) {
                        hit.damage = env.random.of(1, max)
                    }
                }
                if (hit.damage > 0) {
                    lastShieldHit = now
                }
                if (hit.damage >= npc.hitpoints) {
                    hit.damage = npc.hitpoints - 1
                    later(0) { wardenDown() }
                }
            }
            npc === core -> {
                // Players always land their max hit on the core with melee (wiki).
                if (source != null && hit.type == HitType.Melee) {
                    hit.damage = max(hit.damage, source.vars[varps.com_maxhit])
                }
                val dealt = coreHit(hit.damage)
                val warden = fighting
                if (source != null && warden != null && dealt > 0) {
                    damageDealt(source, warden, dealt)
                }
                // The hitsplat shows the whole hit, however big: the core is topped up as it lands
                // (see landing), so it never dies and nobody's attack on it stops.
            }
        }
    }

    /**
     * A hit landing on the core: the core is topped up first so it never runs out of health (a dead
     * core would end everyone's attack on it), and the kneeling Warden shows the damage the core
     * passed on to it (taken when the hit was queued, see [coreHit]).
     */
    override fun landing(npc: Npc, hit: Hit, damage: Int): Int {
        if (npc !== core) {
            return landingDamage(npc, damage)
        }
        npc.hitpoints = npc.baseHitpointsLvl + damage
        val warden = fighting
        if (warden != null && down && damage > 0) {
            val passed = (damage * CORE_MULTIPLIER).coerceAtMost(Hitmark.DAMAGE_BIT_MASK.toInt())
            warden.showHitmark(hit.hitmark.copy(damage = passed))
        }
        return damage
    }

    /**
     * Hits are trimmed when they are queued, so two in flight at once (two players, a multi-hit
     * special) can still take the last hitpoint: the obelisk then falls as it would have from the
     * one that should have killed it, and the Warden goes down, instead of either standing on at no
     * health where nobody can attack it.
     */
    override fun hitpointsGone(npc: Npc) {
        if (npc.hitpoints < 1) {
            npc.hitpoints = if (npc === core) npc.baseHitpointsLvl else 1
        }
        when {
            npc === obelisk && phase == 0 -> startWardenPhase()
            npc === fighting -> wardenDown()
        }
    }

    // The tick
    // ----------------------------------------------------------------------------------------

    override fun onTick() {
        tickStoned()
        when (phase) {
            0 -> {
                tickOrbSpawns()
                moveOrbs()
                chargeWardens()
                checkTraps()
            }
            1 -> {
                tickObelisk()
                tickCore()
                tickFighting()
                for (state in states.values) {
                    if (state.legs) {
                        state.npc?.spotanim(wardens_spotanims.warden_legs)
                    }
                }
            }
        }
    }

    private fun tickStoned() {
        for ((id, until) in stoned.toList()) {
            val player = roomPlayers().firstOrNull { it.accountId == id }
            if (player == null) {
                stoned.remove(id)
                continue
            }
            val left = until - now
            if (left > 0) {
                player.abortRoute()
                player.anim(if (left <= 6) wardens_seqs.entomb_loop else wardens_seqs.entomb)
                continue
            }
            player.anim(wardens_seqs.entomb_end)
            stoned.remove(id)
        }
    }

    // Phase one
    // ----------------------------------------------------------------------------------------

    private fun tickOrbSpawns() {
        if (orbTicks <= 0 || --orbTicks % 2 != 0) {
            return
        }
        for (warden in Warden.entries) {
            val paths = ORB_PATHS.getValue(warden)
            val path = walkTiles(warden.orbSpawn, paths[orbIndex[warden.ordinal]])
            orbIndex[warden.ordinal] = (orbIndex[warden.ordinal] + 1) % paths.size
            // The orbs set off away from their warden (TSPS), the other way to the warden's face.
            val npc =
                spawn(
                    wardens_npcs.energy,
                    warden.orbSpawn,
                    scale = false,
                    points = 0.0,
                    facing = warden.other.facing,
                    passThrough = true,
                )
            orbs += Orb(npc, path, warden)
        }
        if (orbTicks <= 0) {
            obelisk?.anim(wardens_seqs.obelisk_cast)
            orbTicks = 4
        }
    }

    private fun moveOrbs() {
        for (orb in orbs.toList()) {
            if (orb.charged) {
                orbs.remove(orb)
                despawn(orb.npc)
                continue
            }
            if (orb.index >= orb.path.size) {
                // Most of a Warden's charge comes down the troughs, so blocking orbs holds its
                // special back a good deal without stopping it (wiki: blocking "will significantly
                // reduce, although not prevent" the energy gain). The split is a guess; unblocked
                // the total is what it was (3% every two ticks).
                charge(
                    states.getValue(orb.warden),
                    if (raid.settings.isActive(Inv.ANCIENT_HASTE)) ORB_CHARGE * 2 else ORB_CHARGE,
                )
                orb.npc.anim(wardens_seqs.energy_charge)
                orb.charged = true
                continue
            }
            val here = raid.realOf(orb.npc.coords)
            val victim = challengePlayers().firstOrNull { here != null && standingOn(it, here) }
            if (victim != null) {
                // A player who blocks an orb takes 3 (wiki).
                hurtFlat(victim, ORB_DAMAGE)
                orbs.remove(orb)
                despawn(orb.npc)
                continue
            }
            orb.npc.walk(raid.regionOf(orb.path[orb.index++]))
        }
    }

    private fun chargeWardens() {
        for (state in states.values) {
            if (state.chargeTicks > 0 && --state.chargeTicks <= 0) {
                charge(state, PASSIVE_CHARGE)
                state.chargeTicks = if (raid.settings.isActive(Inv.ANCIENT_HASTE)) 1 else 2
            }
        }
    }

    /** A full platform fires the Warden's special: blades and the energy ball in turn (wiki). */
    private fun charge(state: WardenState, amount: Int) {
        val npc = state.npc ?: return
        if (phase != 0) {
            return
        }
        state.percent = minOf(100, state.percent + amount)
        if (state.percent < 100) {
            setPlatform(state)
            return
        }
        npc.anim(wardens_seqs.warden_special)
        if (state.fullCharges % 2 == 0) {
            npc.spotanim(wardens_spotanims.special_traps)
            sendTraps(state.warden)
        } else {
            npc.spotanim(wardens_spotanims.special_ball)
            sendBall(state)
        }
        state.fullCharges++
        state.percent = 0
        setPlatform(state)
    }

    private fun totalCharged(state: WardenState): Int = state.fullCharges * 100 + state.percent

    private fun sendTraps(warden: Warden) {
        val tiles =
            if (warden == Warden.Elidinis) WardensData.elidinis_traps else WardensData.tumeken_traps
        val load =
            if (warden == Warden.Elidinis) wardens_locs.elidinis_trap_load
            else wardens_locs.tumeken_trap_load
        val trap =
            if (warden == Warden.Elidinis) wardens_locs.elidinis_trap else wardens_locs.tumeken_trap
        later(2) { tiles.forEach { placeLoc(it, load, LocShape.CentrepieceStraight, LocAngle[0]) } }
        later(3) {
            tiles.forEach { placeLoc(it, trap, LocShape.CentrepieceStraight, LocAngle[0]) }
            sound(toa_synths.wardens_trap_land)
        }
        later(5) { trapsLive[warden.ordinal] = true }
        later(6) { tiles.forEach { animLoc(it, trap, wardens_seqs.trap_attack) } }
        later(10) {
            tiles.forEach { animLoc(it, trap, wardens_seqs.trap_leave) }
            trapsLive[warden.ordinal] = false
        }
        later(12) { tiles.forEach { removeLoc(it) } }
    }

    private fun checkTraps() {
        for (warden in Warden.entries) {
            if (!trapsLive[warden.ordinal]) {
                continue
            }
            val tiles =
                if (warden == Warden.Elidinis) WardensData.elidinis_traps
                else WardensData.tumeken_traps
            for (player in challengePlayers()) {
                val at = raid.realOf(player.coords) ?: continue
                if (tiles.any { at.x in it.x..it.x + 2 && at.z in it.z..it.z + 2 }) {
                    tileHit(player, TRAP_DAMAGE, 2)
                }
            }
        }
    }

    /** Elidinis' ball punishes standing beside others; Tumeken's punishes standing apart (wiki). */
    private fun sendBall(state: WardenState) {
        val npc = state.npc ?: return
        val players = challengePlayers()
        if (players.isEmpty()) {
            return
        }
        val elidinis = state.warden == Warden.Elidinis
        for (player in players) {
            player.mes(
                (if (elidinis) "<col=3366ff>" else "<col=ff8e32>") +
                    "A large ball of energy is shot your way...</col>"
            )
            player.soundSynth(
                if (elidinis) toa_synths.wardens_elidinis_ball else toa_synths.wardens_tumeken_ball
            )
            val from = raid.realOf(npc.coords)?.translate(2, 2) ?: continue
            val to = raid.realOf(player.coords) ?: continue
            projectile(
                from,
                to,
                if (elidinis) wardens_spotanims.elidinis_ball else wardens_spotanims.tumeken_ball,
                toa_projanims.orb,
            )
        }
        later(BALL_TICKS) {
            if (phase != 0) {
                return@later
            }
            val targets = challengePlayers().filter { it in players }
            val base = maxHit(if (elidinis) ELIDINIS_BALL else TUMEKEN_BALL)
            for (player in targets) {
                player.spotanim(wardens_spotanims.ball_land)
                player.soundSynth(toa_synths.wardens_ball_land)
                val at = raid.realOf(player.coords) ?: continue
                val others =
                    targets.count { other ->
                        val there = raid.realOf(other.coords) ?: return@count false
                        other !== player &&
                            if (elidinis) chebyshev(there, at) <= 1 else chebyshev(there, at) > 0
                    }
                hurtFlat(player, base + base * 3 * others)
            }
        }
    }

    // Phase two
    // ----------------------------------------------------------------------------------------

    /** The obelisk falls: the more charged Warden rises to fight, the other collapses (wiki). */
    fun startWardenPhase() {
        if (phase != 0) {
            return
        }
        phase = 1
        obelisk?.let {
            it.transmog(env.npcTypes[wardens_npcs.obelisk_p2], Int.MAX_VALUE)
            it.anim(wardens_seqs.obelisk_idle)
        }
        for (orb in orbs) {
            despawn(orb.npc)
        }
        orbs.clear()
        val elidinisFights =
            totalCharged(states.getValue(Warden.Elidinis)) >
                totalCharged(states.getValue(Warden.Tumeken))
        val warden = if (elidinisFights) Warden.Elidinis else Warden.Tumeken
        fightingWarden = warden
        raid.finalWarden = warden.other
        bossBar = null
        obeliskCharge = if (raid.settings.isActive(Inv.ACCELERATION)) 30 else 40
        obeliskTicks = obeliskCharge
        thunderTicks = 0
        performingSpecial = false
        specialIndex = env.random.of(3)
        wheelIndex = 0
        downCycles = 0
        canDoSpecial = false
        later(1) { raiseWarden(warden) }
    }

    private fun mageForm(warden: Warden): NpcType =
        if (warden == Warden.Elidinis) wardens_npcs.elidinis_mage else wardens_npcs.tumeken_mage

    private fun rangeForm(warden: Warden): NpcType =
        if (warden == Warden.Elidinis) wardens_npcs.elidinis_range else wardens_npcs.tumeken_range

    private fun exposedForm(warden: Warden): NpcType =
        if (warden == Warden.Elidinis) wardens_npcs.elidinis_exposed
        else wardens_npcs.tumeken_exposed

    private fun raiseWarden(warden: Warden) {
        val alive = states.getValue(warden)
        val fallen = states.getValue(warden.other)
        // Elidinis first prays against ranged (weak to magic), Tumeken against magic (wiki).
        magicForm = warden == Warden.Elidinis
        alive.npc?.let(::despawn)
        alive.npc = null
        restoreLocs()
        val npc =
            spawn(
                if (magicForm) mageForm(warden) else rangeForm(warden),
                warden.spot,
                points = ToaPoints.Multiplier.WARDENS_P2,
                facing = warden.facing,
            )
        fighting = npc
        npc.anim(wardens_seqs.awake)
        shieldHp = npc.hitpoints
        realTotal = ToaScaling.hitpoints(EXPOSED_HITPOINTS, raid.raidLevel, teamSize, 0)
        realHp = realTotal
        down = false
        canAttack = false
        fallen.npc?.let {
            it.transmog(
                env.npcTypes[
                        if (warden.other == Warden.Elidinis) wardens_npcs.elidinis_fallen
                        else wardens_npcs.tumeken_fallen],
                Int.MAX_VALUE,
            )
            it.anim(wardens_seqs.warden_explode)
            it.spotanim(wardens_spotanims.warden_death)
        }
        for (player in roomPlayers()) {
            player.mes(
                if (warden == Warden.Elidinis) {
                    "<col=3366ff>As Tumeken's Warden falls, Elidinis' Warden powers up!</col>"
                } else {
                    "<col=ff8e32>As Elidinis' Warden falls, Tumeken's Warden powers up!</col>"
                }
            )
        }
        bossBar = npc
        later(9) { fallen.legs = true }
        later(14) {
            val sw = raid.realOf(npc.coords) ?: return@later
            npc.walk(raid.regionOf(sw.translate(if (warden == Warden.Elidinis) 5 else -6, 0)))
        }
        later(20) {
            canAttack = true
            lastShieldHit = now
            nextAttack = now
        }
    }

    /** The Warden's turn: an occasional slam up close, otherwise bolts, divine throws or stone. */
    private fun tickFighting() {
        val npc = fighting ?: return
        tickShieldRegen(npc)
        if (!canAttack || down || !fightEnabled || now < nextAttack) {
            return
        }
        nextAttack = now + WARDEN_SPEED
        val players = challengePlayers()
        val centre = raid.realOf(npc.coords)?.translate(2, 2) ?: return
        val target =
            players.minByOrNull { chebyshev(centre, raid.realOf(it.coords) ?: centre) } ?: return
        npc.facePlayer(target)
        val gapToTarget = gap(npc, target.coords)
        if (gapToTarget <= 1 && env.random.of(4) == 0) {
            npc.anim(wardens_seqs.melee)
            strike(
                npc,
                target,
                NpcAttack(NpcAttackStyle.Melee, maxHit = MAX_WARDEN),
                through = 15,
                playAnim = false,
            )
            return
        }
        // Tumeken's Warden closes in; Elidinis' stops a few tiles short (wiki).
        val keep = if (fightingWarden == Warden.Tumeken) 1 else 4
        if (gapToTarget > keep) {
            stepToward(npc, target.coords)
        }
        if (canDoSpecial && env.random.of(6) == 0) {
            canDoSpecial = false
            npc.anim(wardens_seqs.throw_magic)
            // Above 55% its health it throws divine projectiles, below it imprisons (wiki).
            if (realHp * 100 > realTotal * 55) sendDivine(npc) else sendImprisonment(npc)
            return
        }
        val magic = env.random.of(2) == 0
        npc.anim(if (magic) wardens_seqs.throw_magic else wardens_seqs.throw_ranged)
        for (player in players) {
            val to = raid.realOf(player.coords) ?: continue
            val from = raid.realOf(npc.coords)?.translate(2, 2) ?: continue
            projectile(
                from,
                to,
                if (magic) wardens_spotanims.magic else wardens_spotanims.ranged,
                toa_projanims.heavy,
                flight = ATTACK_FLIGHT,
            )
            strike(
                npc,
                player,
                NpcAttack(
                    if (magic) NpcAttackStyle.Magic else NpcAttackStyle.Ranged,
                    maxHit = MAX_WARDEN,
                    hitDelay = 4,
                ),
                through = 15,
                playAnim = false,
            )
        }
        canDoSpecial = true
    }

    /** Every protection prayer drops; only re-praying the right one in time stops the hit. */
    private fun sendDivine(npc: Npc) {
        val kind = env.random.of(3)
        val synth =
            when (kind) {
                0 -> toa_synths.wardens_divine_melee
                1 -> toa_synths.wardens_divine_magic
                else -> toa_synths.wardens_divine_ranged
            }
        val (spot, message, prayer) =
            when (kind) {
                0 ->
                    Triple(
                        wardens_spotanims.divine_melee,
                        "<col=ff3045>The warden throws an arcane scimitar.</col>",
                        varbits.protect_from_melee,
                    )
                1 ->
                    Triple(
                        wardens_spotanims.divine_magic,
                        "<col=a53fff>The warden launches an arcane spell.</col>",
                        varbits.protect_from_magic,
                    )
                else ->
                    Triple(
                        wardens_spotanims.divine_ranged,
                        "<col=229628>The warden fires an arcane arrow.</col>",
                        varbits.protect_from_missiles,
                    )
            }
        val players = challengePlayers()
        for (player in players) {
            val from = raid.realOf(npc.coords)?.translate(2, 2) ?: continue
            val to = raid.realOf(player.coords) ?: continue
            projectile(from, to, spot, toa_projanims.heavy, flight = DIVINE_FLIGHT)
            disablePrayers(player)
            player.mes(message)
            player.soundSynth(synth)
        }
        later(5) {
            if (!canAttack) {
                return@later
            }
            for (player in challengePlayers().filter { it in players }) {
                if (player.vars[prayer] != 1) {
                    tileHit(player, MAX_WARDEN, 10)
                }
            }
        }
    }

    private fun sendImprisonment(npc: Npc) {
        val tiles = LinkedHashSet<CoordGrid>()
        for (player in challengePlayers()) {
            raid.realOf(player.coords)?.let { tiles += it }
        }
        for (tile in tiles) {
            val from = raid.realOf(npc.coords)?.translate(2, 2) ?: continue
            projectile(from, tile, wardens_spotanims.imprison, toa_projanims.heavy, IMPRISON_FLIGHT)
            graphic(wardens_spotanims.incoming, tile)
        }
        later(5) {
            if (!canAttack) {
                return@later
            }
            for (tile in tiles) {
                val victims = challengePlayers().filter { standingOn(it, tile) }
                if (victims.isEmpty()) {
                    graphic(wardens_spotanims.imprison_miss, tile)
                }
                for (victim in victims) {
                    entomb(victim)
                }
            }
        }
    }

    /** Entombed: no moving or attacking for a while, but prayers and food still work (wiki). */
    private fun entomb(player: Player) {
        player.mes("<col=ff3045>You've been entombed in stone!</col>")
        player.soundSynth(toa_synths.wardens_entombed)
        player.anim(wardens_seqs.entomb)
        player.abortRoute()
        stoned[player.accountId] = now + ENTOMB_TICKS
    }

    /**
     * Left alone, the Warden slowly restores its shield (wiki). The wiki gives no numbers: here a
     * twentieth of it every 5 ticks, starting 10 ticks after the last hit got through.
     */
    private fun tickShieldRegen(npc: Npc) {
        if (!canAttack || down || npc.hitpoints >= npc.baseHitpointsLvl) {
            return
        }
        val idle = now - lastShieldHit
        if (idle < SHIELD_REGEN_DELAY || (idle - SHIELD_REGEN_DELAY) % SHIELD_REGEN_EVERY != 0) {
            return
        }
        val amount = (npc.baseHitpointsLvl * SHIELD_REGEN_PERCENT / 100).coerceAtLeast(1)
        npc.hitpoints = minOf(npc.baseHitpointsLvl, npc.hitpoints + amount)
    }

    /** The shield breaks: the Warden kneels and throws out its core. */
    private fun wardenDown() {
        val npc = fighting ?: return
        val warden = fightingWarden ?: return
        if (down || !canAttack) {
            return
        }
        npc.anim(wardens_seqs.kneel)
        npc.resetMovement()
        canAttack = false
        downCycles++
        resetObelisk()
        later(4) {
            down = true
            npc.transmog(env.npcTypes[exposedForm(warden)], Int.MAX_VALUE)
            npc.baseHitpointsLvl = realTotal
            npc.hitpoints = realHp
            val target = challengePlayers().firstOrNull()
            val centre = raid.realOf(npc.coords)?.translate(2, 2) ?: return@later
            val at = target?.let { raid.realOf(it.coords) }
            val dx = if (at != null) Integer.signum(at.x - centre.x) else 0
            val dz = if (at != null) Integer.signum(at.z - centre.z) else -1
            val coreTile = freeTileNear(centre.translate(dx * 3, dz * 3))
            projectile(centre, coreTile, wardens_spotanims.core, toa_projanims.splash)
            later(2) { spawnCore(coreTile) }
        }
    }

    private fun freeTileNear(real: CoordGrid): CoordGrid {
        for (radius in 0 until 10) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val tile = real.translate(dx, dz)
                    if (walkable(tile) && room.inChallenge(tile)) {
                        return tile
                    }
                }
            }
        }
        return real
    }

    /** How long the core stays out, by the Warden's health (wiki). */
    private fun coreDuration(): Int {
        val share = realHp * 100 / realTotal.coerceAtLeast(1)
        return when {
            share >= 80 -> 21
            share >= 60 -> 29
            else -> 37
        }
    }

    private fun spawnCore(tile: CoordGrid) {
        val warden = fightingWarden ?: return
        if (!down) {
            return
        }
        val type =
            if (warden == Warden.Elidinis) wardens_npcs.elidinis_core else wardens_npcs.tumeken_core
        val npc = spawn(type, tile, scale = false, points = 0.0, hitpoints = CORE_HITPOINTS)
        core = npc
        coreTicks = coreDuration()
        for (player in challengePlayers()) {
            if (standingOn(player, tile)) {
                shove(player)
            }
        }
    }

    private fun tickCore() {
        if (core == null || !down) {
            return
        }
        if (--coreTicks <= 0) {
            coreReturns()
        }
    }

    /**
     * Damage on the core passes to the kneeling Warden five-fold (wiki). Returns what the Warden
     * took, which is what its points are for.
     */
    private fun coreHit(damage: Int): Int {
        val npc = fighting ?: return 0
        if (!down || damage <= 0) {
            return 0
        }
        val dealt = minOf(damage * CORE_MULTIPLIER, realHp)
        realHp -= dealt
        npc.hitpoints = realHp.coerceAtLeast(1)
        if (realHp <= 0) {
            later(0) { killFighting() }
        }
        return dealt
    }

    private fun coreReturns() {
        val npc = core ?: return
        core = null
        sound(toa_synths.wardens_core_return, roomPlayers())
        val from = raid.realOf(npc.coords)
        val to = fighting?.let { raid.realOf(it.coords)?.translate(2, 2) }
        if (from != null && to != null) {
            projectile(from, to, wardens_spotanims.core, toa_projanims.splash)
        }
        despawn(npc)
        later(2) { revive() }
    }

    private fun revive() {
        val npc = fighting ?: return
        val warden = fightingWarden ?: return
        if (!down || !isStarted || phase != 1) {
            return
        }
        down = false
        npc.anim(wardens_seqs.stand)
        magicForm = !magicForm
        npc.transmog(
            env.npcTypes[if (magicForm) mageForm(warden) else rangeForm(warden)],
            Int.MAX_VALUE,
        )
        npc.baseHitpointsLvl = shieldHp
        npc.hitpoints = shieldHp
        later(3) {
            canAttack = true
            lastShieldHit = now
            nextAttack = now + 2
        }
    }

    // The obelisk in phase two
    // --------------------------------------------------------------------------------------

    private fun tickObelisk() {
        val npc = obelisk ?: return
        if (!canAttack || performingSpecial) {
            return
        }
        if (thunderTicks > 0) {
            if (--thunderTicks == 0) {
                specialIndex = (specialIndex + 1) % 3
                performingSpecial = true
                when (specialIndex) {
                    0 -> sendIsolation(vertical = true)
                    1 -> sendSkullBombs()
                    else -> sendWheel()
                }
            } else {
                sendThunders()
            }
            return
        }
        obeliskTicks--
        when {
            obeliskTicks <= 0 -> {
                npc.anim(wardens_seqs.obelisk_bomb)
                thunderTicks = 5
            }
            obeliskTicks == 1 -> npc.anim(wardens_seqs.obelisk_open)
            else -> {
                val share = obeliskTicks * 100 / obeliskCharge
                val stage = if (share > 66) 0 else if (share > 33) 1 else 2
                if (stage != chargeStage) {
                    chargeStage = stage
                    npc.anim(wardens_seqs.obelisk_charging[stage])
                }
            }
        }
    }

    private fun resetObelisk() {
        val npc = obelisk ?: return
        obeliskTicks = obeliskCharge
        if (performingSpecial) {
            npc.anim(wardens_seqs.obelisk_close)
        }
        chargeStage = 0
        npc.anim(wardens_seqs.obelisk_charging[0])
        performingSpecial = false
        thunderTicks = 0
    }

    /** Penetration: the obelisk's hits are harder and switch protection prayers off. */
    private fun specialHit(player: Player, base: Int, penetrated: Int) {
        val penetration = raid.settings.isActive(Inv.PENETRATION)
        tileHit(player, if (penetration) penetrated else base, 2)
        if (penetration) {
            disablePrayers(player)
        }
    }

    private fun sendThunders() {
        val ring = ArrayList<CoordGrid>()
        for (dx in -2..2) {
            for (dz in -2..2) {
                if (abs(dx) == 2 || abs(dz) == 2) {
                    ring += OBELISK_MIDDLE.translate(dx, dz)
                }
            }
        }
        for (tile in shuffled(ring).take(env.random.of(3, 5))) {
            graphic(wardens_spotanims.lightning_warn, tile)
            for (player in challengePlayers()) {
                if (standingOn(player, tile)) {
                    specialHit(player, 9, 14)
                }
            }
        }
    }

    /** Lines out from the obelisk that close in on it, north-south and then east-west. */
    private fun sendIsolation(vertical: Boolean) {
        var delta = if (vertical) 12 else 11
        var ticks = 0
        fun step() {
            if (!canAttack || delta < 0) {
                if (!vertical) {
                    resetObelisk()
                }
                return
            }
            if (ticks == 0) {
                for (distance in 2..delta) {
                    graphic(
                        wardens_spotanims.wheel_end,
                        OBELISK_MIDDLE.translate(
                            if (vertical) 0 else distance,
                            if (vertical) distance else 0,
                        ),
                        delay = distance * 2,
                    )
                    graphic(
                        wardens_spotanims.wheel_end,
                        OBELISK_MIDDLE.translate(
                            if (vertical) 0 else -distance,
                            if (vertical) -distance else 0,
                        ),
                        delay = distance * 2,
                    )
                }
            } else {
                if (vertical && ticks == 6) {
                    sendIsolation(vertical = false)
                }
                val first =
                    OBELISK_MIDDLE.translate(if (vertical) 0 else delta, if (vertical) delta else 0)
                val second =
                    OBELISK_MIDDLE.translate(
                        if (vertical) 0 else -delta,
                        if (vertical) -delta else 0,
                    )
                val tiles = arrayListOf(first, second)
                for (spread in (if (vertical) 12 else 11) - delta downTo 1) {
                    if (vertical) {
                        tiles +=
                            listOf(
                                first.translate(-spread, spread),
                                first.translate(spread, spread),
                                second.translate(-spread, -spread),
                                second.translate(spread, -spread),
                            )
                    } else {
                        tiles +=
                            listOf(
                                first.translate(spread, spread),
                                first.translate(spread, -spread),
                                second.translate(-spread, spread),
                                second.translate(-spread, -spread),
                            )
                    }
                }
                for (tile in tiles) {
                    graphic(wardens_spotanims.isolation, tile)
                    for (player in challengePlayers()) {
                        if (standingOn(player, tile)) {
                            specialHit(player, 13, 18)
                        }
                    }
                }
                delta--
            }
            ticks++
            later(1) { step() }
        }
        step()
    }

    private fun sendSkullBombs() {
        var count = WardensData.skull_bombs.size
        var index = env.random.of(count)
        fun step() {
            if (!canAttack || count-- <= 0) {
                resetObelisk()
                return
            }
            val target = WardensData.skull_bombs[index]
            index = (index + 1) % WardensData.skull_bombs.size
            projectile(OBELISK_MIDDLE, target, wardens_spotanims.skull_bomb, toa_projanims.lob)
            graphic(wardens_spotanims.incoming, target)
            later(3) { explodeSkullBomb(target) }
            later(2) { step() }
        }
        step()
    }

    /**
     * A 7x7 of lightning, but the two tiles out from the middle in each direction are safe (wiki).
     */
    private fun explodeSkullBomb(centre: CoordGrid) {
        if (!canAttack) {
            return
        }
        val inner = ArrayList<CoordGrid>()
        val outer = ArrayList<CoordGrid>()
        for (dx in -3..3) {
            for (dz in -3..3) {
                if ((dx == 0 && dz != 0 && abs(dz) < 3) || (dz == 0 && dx != 0 && abs(dx) < 3)) {
                    continue
                }
                val tile = centre.translate(dx, dz)
                if (abs(dx) == 3 || abs(dz) == 3) outer += tile else inner += tile
            }
        }
        inner.forEach {
            graphic(wardens_spotanims.lightning_warn, it, delay = chebyshev(centre, it) * 10)
        }
        outer.forEach { graphic(wardens_spotanims.lightning_warn, it, delay = 30) }
        val strike = { tiles: List<CoordGrid> ->
            for (tile in tiles) {
                for (player in challengePlayers()) {
                    if (standingOn(player, tile)) {
                        specialHit(player, 13, 18)
                    }
                }
            }
        }
        strike(inner)
        later(1) { if (canAttack) strike(outer) }
    }

    private fun sendWheel() {
        var previous = -1
        var cycles = 0
        fun step() {
            if (!canAttack) {
                resetObelisk()
                return
            }
            if (previous != -1) {
                for (tile in WardensData.wheels[previous]) {
                    graphic(
                        wardens_spotanims.wheel_end,
                        tile,
                        delay = 2 * chebyshev(OBELISK_MIDDLE, tile),
                    )
                    for (player in challengePlayers()) {
                        if (standingOn(player, tile)) {
                            specialHit(player, 13, 18)
                        }
                    }
                }
            }
            if (++cycles >= WHEEL_TURNS) {
                resetObelisk()
                return
            }
            for (tile in WardensData.wheels[wheelIndex]) {
                graphic(wardens_spotanims.wheel_start, tile, delay = 30)
            }
            previous = wheelIndex
            wheelIndex = (wheelIndex + 1) % WardensData.wheels.size
            later(2) { step() }
        }
        step()
    }

    // Into the final room
    // -------------------------------------------------------------------------------------

    /** The kneeling Warden's last strength revives the other, who rises for the final phase. */
    private fun killFighting() {
        if (phase != 1) {
            return
        }
        phase = 2
        val warden = fightingWarden ?: return
        core?.let { npc ->
            raid.realOf(npc.coords)?.let {
                graphic(wardens_spotanims.core_explode, it, height = 50, delay = 30)
            }
            despawn(npc)
        }
        core = null
        for (player in roomPlayers()) {
            player.mes(
                if (warden == Warden.Elidinis) {
                    "<col=ff8e32>Elidinis' Warden uses the last of its power to restore Tumeken's Warden!</col>"
                } else {
                    "<col=3366ff>Tumeken's Warden uses the last of its power to restore Elidinis' Warden!</col>"
                }
            )
        }
        fighting?.anim(wardens_seqs.death)
        bossBar = null
        val revived = states.getValue(warden.other)
        revived.legs = false
        revived.npc?.anim(wardens_seqs.warden_revived)
        revived.npc?.spotanim(wardens_spotanims.warden_revive, delay = 120)
        later(5) { sound(toa_synths.wardens_revive, roomPlayers()) }
        later(8) { obelisk?.anim(wardens_seqs.obelisk_death) }
        later(11) {
            stoned.clear()
            complete()
            env.raids.advanceWardens(raid)
        }
    }

    /** For `::toaroom`: the first room is won and the party goes down to the final Warden. */
    fun skipToFinal() {
        if (phase == 0) {
            startWardenPhase()
        }
        phase = 1
        later(2) { killFighting() }
    }

    companion object {
        const val Z = 1
        val OSMUMTEN_SPOT = CoordGrid(3808, 5158, Z)
        val OBELISK_SPOT = CoordGrid(3807, 5153, Z)
        val OBELISK_MIDDLE = CoordGrid(3808, 5154, Z)

        /** Each Warden's orb paths: waypoints from its orb spawn to its platform. */
        val ORB_PATHS: Map<Warden, List<List<CoordGrid>>> =
            mapOf(
                Warden.Elidinis to
                    listOf(
                        listOf(CoordGrid(3797, 5154, Z)),
                        listOf(
                            CoordGrid(3805, 5154, Z),
                            CoordGrid(3803, 5156, Z),
                            CoordGrid(3797, 5156, Z),
                        ),
                        listOf(
                            CoordGrid(3805, 5154, Z),
                            CoordGrid(3803, 5152, Z),
                            CoordGrid(3797, 5152, Z),
                        ),
                    ),
                Warden.Tumeken to
                    listOf(
                        listOf(
                            CoordGrid(3811, 5154, Z),
                            CoordGrid(3813, 5152, Z),
                            CoordGrid(3819, 5152, Z),
                        ),
                        listOf(CoordGrid(3819, 5154, Z)),
                        listOf(
                            CoordGrid(3811, 5154, Z),
                            CoordGrid(3813, 5156, Z),
                            CoordGrid(3819, 5156, Z),
                        ),
                    ),
            )

        /**
         * The tiles a straight-then-diagonal walk takes towards each waypoint, as the orbs move.
         */
        fun walkTiles(start: CoordGrid, waypoints: List<CoordGrid>): List<CoordGrid> {
            val tiles = ArrayList<CoordGrid>()
            var x = start.x
            var z = start.z
            for (point in waypoints) {
                while (x != point.x || z != point.z) {
                    x += Integer.signum(point.x - x)
                    z += Integer.signum(point.z - z)
                    tiles += CoordGrid(x, z, Z)
                }
            }
            return tiles
        }

        const val READY_TIMEOUT = 200
        const val ORB_DAMAGE = 3

        /** A Warden's charge per orb that reaches it, and on its own every two ticks. */
        const val ORB_CHARGE = 2
        const val PASSIVE_CHARGE = 1
        const val TRAP_DAMAGE = 11
        const val BALL_TICKS = 12
        const val ELIDINIS_BALL = 12
        const val TUMEKEN_BALL = 13
        const val WARDEN_SPEED = 7
        const val MAX_WARDEN = 20
        const val ENTOMB_TICKS = 7
        const val EXPOSED_HITPOINTS = 4500
        const val SHIELD_REGEN_DELAY = 10
        const val SHIELD_REGEN_EVERY = 5
        const val SHIELD_REGEN_PERCENT = 5
        const val CORE_HITPOINTS = 100

        /**
         * When the Warden's projectiles leave and land, in client cycles (TSPS's capture): its
         * magic and ranged attacks, the divine projectiles and the imprisonment.
         */
        val ATTACK_FLIGHT = 25..115
        val DIVINE_FLIGHT = 25..145
        val IMPRISON_FLIGHT = 10..130
        const val CORE_MULTIPLIER = 5
        const val WHEEL_TURNS = 12
    }
}
