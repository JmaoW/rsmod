package org.rsmod.content.areas.misc.isleofsouls.toa

import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid

/**
 * The Wardens, final room. The Warden that fell in the first room rises again and slams the floor
 * in patterns; at 80, 60, 40 and 20 percent it sends out energy siphons that only melee breaks (all
 * broken in time and it takes 5% of its health; any left and the whole floor erupts), and from the
 * second siphons on the phantoms of two bosses join (Zebak and Ba-Ba for Tumeken's Warden, Akkha
 * and Kephri for Elidinis'). At 5% it heals 20% and enrages: the floor falls away row by row from
 * the back while lightning strikes. Its fall wins the raid and raises the teleport crystal to the
 * burial chamber.
 *
 * The wiki gives the phases, the 20% heal at 5%, the melee-only siphons and the phantom pairs, the
 * defence floor (120). The tiles (generated into [WardensData]), timings and damage numbers are
 * TSPS's (Near-Reality).
 */
class WardensFinalRuntime(env: ToaRoomEnv, raid: ToaRaid) :
    WardensRuntimeBase(env, raid, ToaRoom.WardensFinal) {
    private class Phantom(val npc: Npc, val type: NpcType) {
        var ticks = 7
        var magic = false
        var akkhaAttacks = 0
        var dropSpeed = 0
    }

    private class Siphon(val npc: Npc, val tile: CoordGrid) {
        var pulse = 1
        var broken = false
    }

    var warden: Npc? = null
        private set

    /** 2 the fight, 3 the last stand, 4 won. */
    var phase = 2
        private set

    /** The tests turn its floor slams off to work on one rule at a time. */
    var fightEnabled = true

    var skullsOut = false
        private set

    private var tumeken = true
    private var floorRotation = 0
    private var attackTicks = 6
    private var thresholdsPassed = 0
    private val siphons = ArrayList<Siphon>()
    private var skullTimer = 0
    private val phantoms = ArrayList<Phantom>()
    private var thunderTicks = 0
    private val thunderTaken = HashSet<CoordGrid>()
    private var floorIndex = 0
    private var floorTicks = 10
    private var enragedTicks = 1
    private val voidArt = ArrayList<org.rsmod.game.loc.LocInfo>()

    val siphonCount: Int
        get() = siphons.count { !it.broken }

    val phantomCount: Int
        get() = phantoms.size

    private val insanity: Boolean
        get() = raid.settings.isActive(Inv.INSANITY)

    /** Every overclocking invocation takes a tick off its slams. */
    private val attackSpeed: Int
        get() =
            BASE_SPEED -
                (if (raid.settings.isActive(Inv.OVERCLOCKED)) 1 else 0) -
                (if (raid.settings.isActive(Inv.OVERCLOCKED_2)) 1 else 0) -
                (if (insanity) 1 else 0)

    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun build() {
        hideVoidArt()
    }

    /**
     * The map keeps the collapsed floor's "Void" pieces on the level above the arena; the real room
     * is built without them, so they are taken away for this party (TSPS).
     */
    private fun hideVoidArt() {
        for (x in VOID_MIN.x..VOID_MAX.x) {
            for (z in VOID_MIN.z..VOID_MAX.z) {
                val tile = raid.regionOf(CoordGrid(x, z, VOID_LEVEL))
                val loc = env.locRepo.findExact(tile, LocShape.GroundDecor) ?: continue
                if (env.locTypes[loc.id]?.name == "Void") {
                    env.locRepo.del(loc, Int.MAX_VALUE)
                    voidArt += loc
                }
            }
        }
    }

    /** The fight is already on when the party arrives: the Warden rises as they land. */
    override fun onIdle() {
        if (challengePlayers().isEmpty() && roomPlayers().isEmpty()) {
            return
        }
        start()
    }

    override fun onStart() {
        if (warden == null) {
            setUp()
        }
    }

    private fun setUp() {
        tumeken = raid.finalWarden == Warden.Tumeken
        phase = 2
        floorRotation = 0
        attackTicks = BASE_SPEED
        thresholdsPassed = 0
        skullsOut = false
        floorIndex = 0
        floorTicks = 10
        enragedTicks = 1
        thunderTicks = if (insanity) 6 else 7
        val type = if (tumeken) wardens_npcs.tumeken_final else wardens_npcs.elidinis_final
        // North, over the floor the party stands on (TSPS: south has its back to them).
        val npc =
            spawn(
                type,
                WARDEN_SPOT,
                points = ToaPoints.Multiplier.WARDENS_P3,
                facing = Direction.North,
            )
        warden = npc
        bossBar = npc
    }

    override fun onComplete() {
        clearAdds()
        phase = 4
        // Its collapse plays on the Warden itself, then the fall back into the void on the trail
        // of death npcs behind it, which stay down when it ends. The Warden goes as they take over,
        // or it would stand up again once its own animation ends.
        later(ticksOf(wardens_seqs.death)) { fallIntoVoid() }
        later(CRYSTAL_TICKS) {
            placeLoc(
                CRYSTAL_SPOT,
                wardens_locs.final_crystal,
                LocShape.CentrepieceStraight,
                LocAngle[0],
            )
        }
    }

    private fun fallIntoVoid() {
        warden?.let(::despawn)
        for ((index, spot) in HIDDEN_SPOTS.withIndex()) {
            // Each animation is offset towards the npc's front to line up with the Warden, so
            // they face the way it did (north); facing south they showed mirrored copies behind.
            val npc =
                spawn(
                    if (tumeken) wardens_npcs.tumeken_death else wardens_npcs.elidinis_death,
                    spot,
                    scale = false,
                    points = 0.0,
                    facing = Direction.North,
                )
            npc.anim(wardens_seqs.hidden[index])
        }
    }

    /** A wiped party starts the Wardens over from the obelisk. */
    override fun onReset() {
        clearAdds()
        warden?.let(::despawn)
        warden = null
        restoreLocs()
        for (tile in collapsed.toList()) {
            removeLoc(tile, LocShape.GroundDecor)
        }
        collapsed.clear()
        for (member in raid.active) {
            if (member.room == room) {
                env.raids.place(raid, member, ToaRoom.WardensFirst)
            }
        }
        env.eventBus.publish(ToaEvents.RoomReset(raid, ToaRoom.WardensFirst))
    }

    override fun onDestroy() {
        voidArt.clear()
    }

    private fun clearAdds() {
        for (siphon in siphons) {
            removeLoc(siphon.tile)
            despawn(siphon.npc)
        }
        siphons.clear()
        for (phantom in phantoms) {
            despawn(phantom.npc)
        }
        phantoms.clear()
    }

    // Hits
    // --------------------------------------------------------------------------------------------

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        val boss = warden
        if (npc === boss) {
            filterWardenHit(boss, hit)
            return
        }
        val siphon = siphons.firstOrNull { it.npc === npc } ?: return
        // Only melee breaks a siphon, a point at a time (TSPS; the wiki: melee).
        hit.damage = if (hit.type == HitType.Melee) 1 else 0
        if (hit.damage >= npc.hitpoints && !siphon.broken) {
            later(0) { siphonBroken(siphon) }
        }
    }

    /**
     * Its thresholds: siphons at 80/60/40/20%, the last stand at 5%; no damage while skulls fly.
     */
    private fun filterWardenHit(boss: Npc, hit: HitBuilder) {
        // Its Defence can be drained to 120 at the lowest (wiki).
        if (boss.defenceLvl < DEFENCE_FLOOR) {
            boss.defenceLvl = DEFENCE_FLOOR
        }
        if (skullsOut) {
            hit.damage = 0
            return
        }
        val max = boss.baseHitpointsLvl
        val hp = boss.hitpoints
        if (phase == 2) {
            val after = hp - hit.damage
            if (thresholdsPassed < SKULL_THRESHOLDS.size) {
                val threshold = max * SKULL_THRESHOLDS[thresholdsPassed] / 100
                if (hp > threshold && after <= threshold) {
                    hit.damage = hp - threshold
                    val index = thresholdsPassed++
                    later(0) { sendSkulls(boss, index) }
                    return
                }
            }
            val last = max * LAST_STAND_PERCENT / 100
            if (hp > last && after <= last) {
                hit.damage = hp - last
                later(0) { startLastPhase(boss) }
            }
            return
        }
        if (phase == 3 && hit.damage >= hp) {
            hit.damage = hp - 1
            later(0) {
                if (phase == 3) {
                    boss.hitpoints = 1
                    complete()
                    boss.anim(wardens_seqs.death)
                }
            }
        }
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc.hitpoints < 1) {
            npc.hitpoints = 1
        }
    }

    // The tick
    // ----------------------------------------------------------------------------------------

    override fun onTick() {
        val boss = warden ?: return
        if (phase == 2) {
            if (!skullsOut) {
                if (fightEnabled && attackTicks > 0 && --attackTicks <= 0) {
                    floorSlam(boss)
                }
            } else if (siphons.isNotEmpty() && skullTimer > 0 && --skullTimer <= 0) {
                skullsFailed(boss)
            }
            tickSiphons(boss)
        } else if (phase == 3) {
            if (enragedTicks > 0 && --enragedTicks == 0) {
                boss.anim(wardens_seqs.enraged)
            }
            tickCollapse()
            tickThunder()
            for (player in challengePlayers()) {
                val edge = FLOOR_MIN.z + 8 - floorIndex / 4
                val at = raid.realOf(player.coords) ?: continue
                if (at.z > edge) {
                    pushOff(player, edge)
                }
            }
        }
        tickPhantoms()
    }

    // Floor slams
    // -----------------------------------------------------------------------------------------

    private fun floorSlam(boss: Npc) {
        attackTicks = attackSpeed
        boss.anim(wardens_seqs.floor_slams[floorRotation * 2 + if (attackSpeed <= 4) 1 else 0])
        val first = ArrayList<CoordGrid>()
        val second = ArrayList<CoordGrid>()
        for ((group, tiles) in WardensData.floor_attacks[floorRotation].withIndex()) {
            for (tile in tiles) {
                val delay = 90 + chebyshev(tile, FLOOR_BASE) * 6 - if (floorRotation == 2) 6 else 0
                (if (delay < 120) first else second) += tile
                graphic(wardens_spotanims.floor[group], tile, delay = delay)
            }
        }
        floorRotation = (floorRotation + 1) % WardensData.floor_attacks.size
        val strike = { tiles: List<CoordGrid> ->
            if (!skullsOut && phase == 2) {
                for (tile in tiles) {
                    for (player in challengePlayers()) {
                        if (standingOn(player, tile)) {
                            tileHit(player, FLOOR_DAMAGE, 3)
                        }
                    }
                }
            }
        }
        val delay = if (attackSpeed < 5) 1 else 2
        later(delay) { strike(first) }
        later(delay + 1) { strike(second) }
    }

    // Skulls and siphons
    // ------------------------------------------------------------------------------------------

    private fun sendSkulls(boss: Npc, index: Int) {
        skullsOut = true
        sound(toa_synths.wardens_skull_send, roomPlayers())
        if (!insanity) {
            floorRotation = 0
        }
        boss.anim(wardens_seqs.skull_send)
        boss.transmog(
            env.npcTypes[
                    if (tumeken) wardens_npcs.tumeken_charging_skulls
                    else wardens_npcs.elidinis_charging_skulls],
            Int.MAX_VALUE,
        )
        val players = challengePlayers()
        val tiles = WardensData.skulls[if (players.size >= 2) 1 else 0][index]
        val from = raid.realOf(boss.coords)?.translate(2, 2)
        for (tile in tiles) {
            graphic(wardens_spotanims.incoming, tile)
            if (from != null) {
                projectile(from, tile, wardens_spotanims.skull, toa_projanims.lob)
            }
        }
        if (index == 1) {
            spawnPhantom(
                if (tumeken) wardens_npcs.zebak_phantom else wardens_npcs.akkha_phantom,
                PHANTOM_SPOTS[0],
            )
        } else if (index == 2) {
            spawnPhantom(
                if (tumeken) wardens_npcs.baba_phantom else wardens_npcs.kephri_phantom,
                PHANTOM_SPOTS[1],
            )
        }
        later(4) { spawnSiphons(tiles, players.size) }
    }

    private fun spawnSiphons(tiles: List<CoordGrid>, groupSize: Int) {
        if (phase != 2) {
            return
        }
        skullTimer = 3 + tiles.size * if (insanity) 2 else 3
        val hitpoints = 1 + if (groupSize < 4) 0 else (groupSize - 2) / 2
        for (tile in tiles) {
            val npc =
                spawn(wardens_npcs.siphon, tile, scale = false, points = 0.0, hitpoints = hitpoints)
            placeLoc(tile, wardens_locs.siphon_block, LocShape.CentrepieceStraight, LocAngle[0])
            siphons += Siphon(npc, tile)
            for (player in challengePlayers()) {
                if (standingOn(player, tile)) {
                    shove(player)
                }
            }
        }
    }

    private fun tickSiphons(boss: Npc) {
        if (!skullsOut) {
            return
        }
        val to = raid.realOf(boss.coords)?.translate(2, 2) ?: return
        for (siphon in siphons) {
            if (siphon.broken || --siphon.pulse > 0) {
                continue
            }
            siphon.pulse = 2
            projectile(siphon.tile, to, wardens_spotanims.siphon, toa_projanims.splash)
        }
    }

    private fun siphonBroken(siphon: Siphon) {
        val boss = warden ?: return
        if (siphon.broken || !skullsOut) {
            return
        }
        siphon.broken = true
        removeLoc(siphon.tile)
        // A reversed siphon turns to the Warden and flies back into it (wiki).
        siphon.npc.faceNpc(boss)
        raid.realOf(boss.coords)?.translate(2, 2)?.let { to ->
            projectile(siphon.tile, to, wardens_spotanims.siphon, toa_projanims.splash)
        }
        later(1) { despawn(siphon.npc) }
        if (siphons.any { !it.broken }) {
            return
        }
        // Every siphon broken in time: it takes 5% of its health (TSPS).
        attackTicks = 8
        siphons.clear()
        later(1) {
            boss.anim(wardens_seqs.skull_return)
            boss.resetTransmog()
        }
        later(2) {
            skullsOut = false
            boss.hitpoints = (boss.hitpoints - boss.baseHitpointsLvl * 5 / 100).coerceAtLeast(1)
        }
    }

    /** Siphons left standing: the whole floor erupts, harder for each one missed. */
    private fun skullsFailed(boss: Npc) {
        val missing = siphons.count { !it.broken }
        if (missing == 0) {
            return
        }
        attackTicks = 9
        boss.anim(wardens_seqs.skull_fail)
        sound(toa_synths.wardens_skull_fail, roomPlayers())
        val first = ArrayList<CoordGrid>()
        val second = ArrayList<CoordGrid>()
        for ((group, tiles) in WardensData.full_floor_attack.withIndex()) {
            for (tile in tiles) {
                val delay = 30 + chebyshev(tile, FLOOR_BASE) * 6
                (if (delay < 60) first else second) += tile
                graphic(wardens_spotanims.floor[group], tile, delay = delay)
            }
        }
        for (siphon in siphons) {
            if (!siphon.broken) {
                graphic(wardens_spotanims.explosion, siphon.tile)
            }
            removeLoc(siphon.tile)
            despawn(siphon.npc)
        }
        siphons.clear()
        val damage = (if (insanity) 33 else 11) * missing
        val strike = { tiles: List<CoordGrid> ->
            for (tile in tiles) {
                for (player in challengePlayers()) {
                    if (standingOn(player, tile)) {
                        hurtFlat(player, maxHit(damage))
                    }
                }
            }
        }
        strike(first)
        later(1) { strike(second) }
        later(3) { boss.resetTransmog() }
        later(4) { skullsOut = false }
    }

    // Phantoms
    // -----------------------------------------------------------------------------------------

    private fun spawnPhantom(type: NpcType, spot: CoordGrid) {
        val npc = spawn(type, spot, scale = false, points = 0.0, facing = Direction.North)
        val phantom = Phantom(npc, type)
        phantom.ticks = if (type == wardens_npcs.zebak_phantom) 8 else 7
        phantom.magic = env.random.of(2) == 0
        if (type == wardens_npcs.baba_phantom) {
            phantom.dropSpeed = minOf(2, raid.pathLevels[ToaPath.Apmeken.index] / 2)
        }
        phantoms += phantom
    }

    private fun tickPhantoms() {
        for (phantom in phantoms) {
            if (phantom.ticks > 0 && --phantom.ticks <= 0) {
                phantom.ticks = phantomAttack(phantom)
            }
        }
    }

    /**
     * Each phantom fights as its boss did, a little weaker (TSPS). Returns the ticks to its next.
     */
    private fun phantomAttack(phantom: Phantom): Int {
        val npc = phantom.npc
        val players = challengePlayers()
        when (phantom.type.id) {
            wardens_npcs.zebak_phantom.id -> {
                if (env.random.of(3) == 0) {
                    phantom.magic = !phantom.magic
                }
                npc.anim(wardens_seqs.zebak_phantom)
                val from = raid.realOf(npc.coords)?.translate(2, 2) ?: return 8
                projectile(
                    from,
                    ZEBAK_LAUNCH,
                    if (phantom.magic) wardens_spotanims.zebak_magic_rise
                    else wardens_spotanims.zebak_ranged_rise,
                    toa_projanims.volley_rise,
                )
                val style = if (phantom.magic) NpcAttackStyle.Magic else NpcAttackStyle.Ranged
                later(3) {
                    for (player in challengePlayers()) {
                        val to = raid.realOf(player.coords) ?: continue
                        projectile(
                            ZEBAK_LAUNCH,
                            to,
                            if (phantom.magic) wardens_spotanims.zebak_magic_split
                            else wardens_spotanims.zebak_ranged_split,
                            toa_projanims.volley_fragment,
                        )
                        player.spotanim(
                            if (phantom.magic) wardens_spotanims.zebak_magic
                            else wardens_spotanims.zebak_ranged,
                            delay = 90,
                            height = 90,
                        )
                        strike(
                            npc,
                            player,
                            NpcAttack(style, maxHit = PHANTOM_MAX, hitDelay = 2),
                            through = 0,
                            playAnim = false,
                        )
                    }
                }
                return 8
            }
            wardens_npcs.akkha_phantom.id -> {
                val vigilant = raid.settings.isActive(Inv.STAY_VIGILANT)
                if (
                    (vigilant && env.random.of(4) == 0) || (!vigilant && ++phantom.akkhaAttacks > 3)
                ) {
                    phantom.akkhaAttacks = 0
                    npc.anim(wardens_seqs.akkha_switch)
                    phantom.magic = !phantom.magic
                    return 7
                }
                npc.anim(if (phantom.magic) wardens_seqs.akkha_magic else wardens_seqs.akkha_ranged)
                val style = if (phantom.magic) NpcAttackStyle.Magic else NpcAttackStyle.Ranged
                later(1) {
                    for (player in challengePlayers().filter { it in players }) {
                        val from = raid.realOf(npc.coords)?.translate(2, 2) ?: continue
                        val to = raid.realOf(player.coords) ?: continue
                        projectile(
                            from,
                            to,
                            if (phantom.magic) wardens_spotanims.akkha_magic
                            else wardens_spotanims.akkha_ranged,
                            toa_projanims.heavy,
                            // Lands with the hit, two ticks on (TSPS's capture: 0 to 54).
                            flight = 0..54,
                        )
                        strike(
                            npc,
                            player,
                            NpcAttack(style, maxHit = PHANTOM_MAX, hitDelay = 2),
                            through = 15,
                            playAnim = false,
                        )
                    }
                }
                return 7
            }
            wardens_npcs.baba_phantom.id -> {
                npc.anim(wardens_seqs.baba_phantom)
                val tiles = players.mapNotNull { raid.realOf(it.coords) }.toSet()
                for (tile in tiles) {
                    graphic(wardens_spotanims.rubble, tile, delay = 20)
                }
                later(5 - phantom.dropSpeed * 2) {
                    for (tile in tiles) {
                        for (player in challengePlayers()) {
                            if (standingOn(player, tile)) {
                                tileHit(player, 19, 5)
                            }
                        }
                    }
                }
                return 7 - phantom.dropSpeed
            }
            else -> {
                npc.anim(wardens_seqs.kephri_phantom)
                val from = raid.realOf(npc.coords)?.translate(2, 2) ?: return 7
                projectile(
                    from,
                    KEPHRI_LAUNCH,
                    wardens_spotanims.kephri_launch,
                    toa_projanims.volley_rise,
                )
                later(2) {
                    val tiles = LinkedHashSet<CoordGrid>()
                    val aerial = raid.settings.isActive(Inv.AERIAL_ASSAULT)
                    for (player in challengePlayers()) {
                        val at = raid.realOf(player.coords) ?: continue
                        projectile(
                            KEPHRI_LAUNCH,
                            at,
                            wardens_spotanims.kephri_bomb,
                            toa_projanims.lob,
                        )
                        if (aerial) {
                            for (dx in -1..1) {
                                for (dz in -1..1) {
                                    tiles += at.translate(dx, dz)
                                }
                            }
                        } else {
                            tiles += at
                        }
                    }
                    tiles.forEach { graphic(wardens_spotanims.incoming, it) }
                    later(3) {
                        for (tile in tiles) {
                            if (!walkable(tile)) {
                                continue
                            }
                            graphic(wardens_spotanims.kephri_explode, tile, height = 38)
                            for (player in challengePlayers()) {
                                if (!standingOn(player, tile)) {
                                    continue
                                }
                                val base = maxHit(PHANTOM_MAX) + env.random.of(4)
                                hurtFlat(
                                    player,
                                    if (player.vars[varbits.protect_from_magic] == 1) base * 3 / 4
                                    else base,
                                )
                            }
                        }
                    }
                }
                return 7
            }
        }
    }

    // The last stand
    // ------------------------------------------------------------------------------------------

    /** At 5% it heals 20% and enrages (wiki); its Defence goes back up to 180. */
    private fun startLastPhase(boss: Npc) {
        if (phase != 2) {
            return
        }
        phase = 3
        skullsOut = false
        boss.hitpoints =
            minOf(boss.baseHitpointsLvl, boss.hitpoints + boss.baseHitpointsLvl * 20 / 100)
        boss.defenceLvl = ENRAGED_DEFENCE
        boss.anim(wardens_seqs.enraging)
        sound(toa_synths.wardens_revive, roomPlayers())
        later(3) { sound(toa_synths.wardens_last_phase) }
        for (player in challengePlayers()) {
            player.mes("<col=ff3045>The Warden is enraged!</col>")
        }
    }

    private val collapsed = HashSet<CoordGrid>()

    /** The floor falls away row by row from the back, pushing anyone on it forward. */
    private fun tickCollapse() {
        if (floorIndex >= WardensData.floor_collapse.size || floorTicks <= 0 || --floorTicks > 0) {
            return
        }
        floorTicks = if (insanity) 10 else 15
        val row = FLOOR_MIN.z + 8 - floorIndex / 4
        var ticks = 0
        fun step() {
            if (ticks++ >= 4 || phase != 3 || floorIndex >= WardensData.floor_collapse.size) {
                return
            }
            for (piece in WardensData.floor_collapse[floorIndex]) {
                val tile = CoordGrid(piece.x, piece.z, Z)
                if (piece.z == row) {
                    projectile(tile, FLOOR_VOID, wardens_spotanims.floor_removal, toa_projanims.lob)
                }
                placeLoc(
                    tile,
                    env.locTypes.getValue(piece.id),
                    LocShape[piece.shape],
                    LocAngle[piece.angle],
                )
                collapsed += tile
                for (player in challengePlayers()) {
                    if (standingOn(player, tile)) {
                        pushOff(player, row - 1)
                    }
                }
            }
            floorIndex++
            later(1) { step() }
        }
        step()
    }

    private fun pushOff(player: Player, row: Int) {
        val at = raid.realOf(player.coords) ?: return
        for (radius in 0..2) {
            for (dx in -radius..radius) {
                val tile = CoordGrid(at.x + dx, row, Z)
                if (!walkable(tile) || tile in collapsed) {
                    continue
                }
                player.mes("You are pushed off.")
                player.anim(wardens_seqs.pushed)
                knockback(player, tile, cycles = 30)
                return
            }
        }
    }

    /** Red lightning on 15% of the open floor every few ticks, warned four ticks before. */
    private fun tickThunder() {
        if (thunderTicks <= 0) {
            return
        }
        thunderTicks--
        if (thunderTicks > 2) {
            return
        }
        if (thunderTicks == 0) {
            thunderTicks = 6
            thunderTaken.clear()
        }
        val open = ArrayList<CoordGrid>()
        for (z in 0..8 - floorIndex / 4) {
            for (x in 0..20) {
                val tile = CoordGrid(FLOOR_MIN.x + x, FLOOR_MIN.z + z, Z)
                if (tile !in thunderTaken && tile !in collapsed && walkable(tile)) {
                    open += tile
                }
            }
        }
        val strikes = shuffled(open).take(maxOf(2, open.size * 15 / 100))
        for (tile in strikes) {
            graphic(wardens_spotanims.thunder_incoming, tile)
            thunderTaken += tile
        }
        later(4) {
            if (phase != 3) {
                return@later
            }
            sound(toa_synths.wardens_thunder, roomPlayers())
            for (tile in strikes) {
                graphic(wardens_spotanims.lightning, tile)
                for (player in challengePlayers()) {
                    if (standingOn(player, tile)) {
                        tileHit(player, 9, 4)
                    }
                }
            }
        }
    }

    /** For `::toaroom`: the Warden falls at once. */
    fun skipToEnd() {
        val boss = warden ?: return
        clearAdds()
        phase = 3
        boss.hitpoints = 1
        complete()
        boss.anim(wardens_seqs.death)
    }

    /** The crystal over the void: "Use" takes the party to the burial chamber. */
    fun useCrystal() {
        if (phase == 4) {
            env.raids.wardensDefeated(raid)
        }
    }

    companion object {
        const val Z = 1
        val WARDEN_SPOT = CoordGrid(3934, 5152, Z)
        val FLOOR_BASE = CoordGrid(3936, 5157, Z)
        val FLOOR_MIN = CoordGrid(3926, 5157, Z)
        val FLOOR_VOID = CoordGrid(3936, 5130, Z)
        /** The first row of the arena, in front of the Warden: the one row that never falls. */
        val CRYSTAL_SPOT = CoordGrid(3936, 5157, Z)
        val PHANTOM_SPOTS = listOf(CoordGrid(3943, 5153, Z), CoordGrid(3925, 5153, Z))
        val ZEBAK_LAUNCH = CoordGrid(3941, 5159, Z)
        val KEPHRI_LAUNCH = CoordGrid(3928, 5156, Z)
        val HIDDEN_SPOTS = listOf(5152, 5147, 5142, 5137, 5132).map { CoordGrid(3934, it, Z) }
        val VOID_MIN = CoordGrid(3920, 5130, 2)
        val VOID_MAX = CoordGrid(3952, 5190, 2)
        const val VOID_LEVEL = 2

        val SKULL_THRESHOLDS = intArrayOf(80, 60, 40, 20)
        const val LAST_STAND_PERCENT = 5
        const val BASE_SPEED = 6
        const val FLOOR_DAMAGE = 18
        const val PHANTOM_MAX = 20
        const val DEFENCE_FLOOR = 120
        const val ENRAGED_DEFENCE = 180
        const val CRYSTAL_TICKS = 12
    }
}
