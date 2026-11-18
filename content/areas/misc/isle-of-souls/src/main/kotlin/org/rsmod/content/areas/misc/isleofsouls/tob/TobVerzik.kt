package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.synth.SynthReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.map.Direction
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object VerzikNpcs : NpcReferences() {
    val waiting = find("verzik_initial")
    val phase1 = find("verzik_phase1")
    val to_phase2 = find("verzik_phase1_to2_transition")
    val phase2 = find("verzik_phase2")
    val to_phase3 = find("verzik_phase2_to3_transition")
    val phase3 = find("verzik_phase3")
    val dead = find("verzik_death_bat")
    val web = find("verzik_web_npc")
    val pillar = find("verzik_pillar_npc")
    val collapsing = find("verzik_collapsing_pillar_npc")
    val fading = find("verzik_fading_rubble_npc")
    val nylos =
        mapOf(
            NyloStyle.Melee to find("verzik_nylocas_melee"),
            NyloStyle.Ranged to find("verzik_nylocas_ranged"),
            NyloStyle.Magic to find("verzik_nylocas_magic"),
        )
    val athanatos = find("tob_verzik_phase2_armourednylocas")
    val matomenos = find("tob_verzik_phase2_bloodnylocas")
    val tornado = find("tob_verzik_creeper")

    val forms: List<NpcType>
        get() = listOf(waiting, phase1, to_phase2, phase2, to_phase3, phase3, dead)
}

object VerzikLocs : LocReferences() {
    val pillar = find("tob_dungeon_verzik_pillar")
    val web = find("verzik_webspin_loc")
}

object VerzikSeqs : SeqReferences() {
    val p1_blast = find("verzik_phase1_attack_magic")
    val off_throne = find("verzik_phase1_death")
    val p2_spawn = find("verzik_phase2_spawn")
    val p2_attack = find("verzik_phase2_attack_magic")
    val p2_bounce = find("verzik_phase2_attack_melee")
    val p2_heal = find("verzik_phase2_heal")
    val p2_death = find("verzik_phase2_death")
    val p3_spawn = find("verzik_phase3_spawn")
    val p3_melee = find("verzik_phase3_attack_melee")
    val p3_magic = find("verzik_phase3_attack_magic")
    val p3_ranged = find("verzik_phase3_attack_ranged")
    val p3_dots = find("verzik_phase3_attack_powerblast")
    val p3_webs = find("verzik_phase3_attack_webspin")
    val p3_death = find("verzik_phase3_death_a")
    val pillar_collapse = find("verzik_pillar_collapse")
    val pillar_fade = find("verzik_pillar_fade")
}

object VerzikSpots : SpotanimReferences() {
    val blast = find("verzik_phase1_projectile")
    val blast_player = find("verzik_phase1_impact")
    val blast_pillar = find("verzik_phase1_impactpillar")
    val urn = find("verzik_phase2_ranged")
    val urn_land = find("verzik_ranged_impact")
    val lightning = find("verzik_phase2_lightning")
    val purple = find("verzik_phase2_spawn_armouredtank_proj")
    val heal_globule = find("verzik_phase2_bloodglobule")
    val poison_globule = find("verzik_phase2_poisonglobule")
    val poisoned = find("verzik_phase2_poisonaura")
    val blood = find("verzik_phase2_bloodproj")
    val blood_land = find("verzik_phase2_bloodimpact")
    val p3_ranged = find("verzik_phase3_rangeproj")
    val p3_magic = find("verzik_phase3_mageproj")
    val dot = find("verzik_powerblast_safezone")
    val dot_blast = find("verzik_powerblast_projanim_down")
    val dot_safe = find("verzik_powerblast_protection")
    val green_ball = find("verzik_acidbomb_projanim")
    val blast_hit = find("verzik_acidbomb_impact")
    val web = find("verzik_p3_web_proj")
    val tornado_hit = find("verzik_p3_creeper_spot")
}

object VerzikSynths : SynthReferences() {
    val attack = find("tob_verzik_attack")
    val heal = find("tob_verzik_heal")
    val death = find("tob_verzik_death")
}

/**
 * Wave 6, Verzik Vitur, in three phases. The leader starts the fight by talking to her.
 * - Phase 1, on her throne: every 14 ticks a blast at everyone she can see (up to 137, half through
 *   Protect from Magic); a player behind a pillar is sheltered and the pillar takes the blast
 *   instead, collapsing on anyone beside it when it breaks. Only the Dawnbringer's special
 *   (Pulsate) hurts her properly: other hits are capped at 10 (melee) or 3.
 * - Phase 2: the pillars come down and she flies to the middle. Every 4 ticks she slams anyone
 *   under her, or throws urns at everyone's tiles (up to 44, halved by Protect from Missiles);
 *   every fifth attack a lightning ball runs through the team; now and then a purple ball becomes a
 *   Nylocas Athanatos (which heals her unless hit with a poisoned weapon) and she calls one nylocas
 *   per player that chases them and explodes. Below 35% she casts blood spells that heal her and
 *   calls Matomenos, briefly turning hits into healing.
 * - Phase 3: she walks after her target; melee (63, everyone beside them), ranged or magic (33 at
 *   everyone, half through prayer) every 7 ticks, and every fifth attack a special in turn:
 *   nylocas, webs, safe spots (80 to everyone off one), a green ball that bounces. Below 20% she
 *   attacks every 5 ticks and sends a tornado after each player.
 *
 * Wiki numbers. Guesses (TSPS's): her tiles and the pillars', the blast on a pillar (40-60), the
 * phase 2 attack order, the nylocas' fuse and fall-off, the Athanatos's life and heals, the blood
 * spell's 29, the web spin and the green ball's bounces, the tornadoes' 15-tick return.
 */
class VerzikRuntime(env: TobRoomEnv, raid: TobRaid, private val objTypes: ObjTypeList) :
    TobRoomRuntime(env, raid, TobRoom.Verzik) {
    private class Pillar(val npc: Npc, val tile: CoordGrid)

    private class Minion(val npc: Npc, val kind: Kind) {
        var target: Player? = null
        var style: NyloStyle = NyloStyle.Melee
        var age = 0
        var poisoned = false
    }

    private enum class Kind {
        Nylo,
        Athanatos,
        Matomenos,
        Tornado,
    }

    private class Web(val tile: CoordGrid, val npc: Npc) {
        var age = 0
        val stuck = HashSet<Player>()
    }

    var verzik: Npc? = null
        private set

    var phase = 0
        private set

    private var transition = false
    private var ticks = 0
    private val pillars = ArrayList<Pillar>()
    private val minions = LinkedHashMap<Npc, Minion>()
    private val webs = LinkedHashMap<CoordGrid, Web>()

    // Phase 2
    private var attackCounter = -1
    private var lightnings = 0
    private var healing = false
    private var shieldUntil = 0
    private val matomenos = ArrayList<Npc>()

    // Phase 3
    private var target: Player? = null
    private var attacks = 0
    private var special = 0
    private var busyUntil = 0
    private var enraged = false

    override fun build() {
        val npc = spawn(VerzikNpcs.waiting, THRONE, scale = true, facing = Direction.South)
        verzik = npc
        bossBar = npc
        for (tile in PILLARS) {
            placeLoc(tile, VerzikLocs.pillar, angle = LocAngle.West)
            pillars += Pillar(spawn(VerzikNpcs.pillar, tile), tile)
        }
    }

    override fun onStart() {
        val npc = verzik ?: return
        transform(npc, VerzikNpcs.phase1)
        phase = 1
        ticks = 0
        npc.say("Oh I'm going to enjoy this...")
    }

    override fun bar(): TobBar {
        val npc = verzik ?: return TobBar.NONE
        if (phase == 0 || transition) {
            return TobBar.NONE
        }
        return barOf(npc, if (phase == 1) TobBar.CYAN else TobBar.REGULAR)
    }

    override fun onTick() {
        val npc = verzik ?: return
        ticks++
        joinLateArrivals()
        tickPillars()
        tickMinions(npc)
        tickWebs()
        if (transition) {
            return
        }
        when (phase) {
            1 -> tickPhase1(npc)
            2 -> tickPhase2(npc)
            3 -> tickPhase3(npc)
        }
    }

    /** No barrier: whoever walks in while the fight is on is in it. */
    private fun joinLateArrivals() {
        for (member in raid.active) {
            if (member.room == room && !member.inFight && !member.jailed) {
                member.inFight = true
                member.beforeBarrier = realOf(member.player.coords)
            }
        }
    }

    override fun hitpointsGone(npc: Npc) {
        when {
            npc === verzik && !transition ->
                when (phase) {
                    1 -> toPhase2(npc)
                    2 -> toPhase3(npc)
                    3 -> die(npc)
                }
            npc === verzik -> npc.hitpoints = 1
            pillars.any { it.npc === npc } -> npc.hitpoints = 0
            else -> {
                val minion = minions.remove(npc) ?: return
                matomenos.remove(npc)
                despawn(minion.npc)
            }
        }
    }

    // Hits on her and her minions
    // -------------------------------------------------------------------------------------------

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc === verzik) {
            if (transition) {
                hit.damage = 0
                return
            }
            if (phase == 1) {
                val pulsate =
                    hit.isRighthandObj(objTypes[tob_objs.dawnbringer]) && hit.damage >= PULSATE_MIN
                if (!pulsate) {
                    val cap = if (hit.type == HitType.Melee) CAP_MELEE else CAP_OTHER
                    hit.damage = minOf(cap, hit.damage)
                }
            } else if (phase == 2 && now < shieldUntil) {
                heal(npc, hit.damage)
                hit.damage = 0
            }
            return
        }
        val minion = minions[npc] ?: return
        when (minion.kind) {
            Kind.Nylo ->
                if (hit.type != HitType.Typeless && hit.type != minion.style.hitType) {
                    hit.damage = 0
                    source?.mes(
                        "You can only damage this Nylocas with ${minion.style.name.lowercase()}."
                    )
                }
            Kind.Athanatos -> {
                if (source != null && poisonedWeapon(source)) {
                    if (!minion.poisoned) {
                        minion.poisoned = true
                        npc.spotanim(VerzikSpots.poisoned)
                    }
                } else {
                    hit.damage = 0
                    source?.mes("You can only damage the Nylocas Athanatos with poisoned weapons.")
                    source?.clearInteraction()
                }
            }
            else -> Unit
        }
    }

    override fun landing(npc: Npc, hit: Hit, damage: Int): Int =
        if (npc === verzik && transition) 0 else damage

    /** A poisoned melee weapon, poisoned ammunition, or a venomous weapon (wiki). */
    private fun poisonedWeapon(player: Player): Boolean {
        val names =
            listOfNotNull(player.worn[WEAPON_SLOT], player.worn[AMMO_SLOT]).mapNotNull {
                objTypes[it.id]?.name?.lowercase()
            }
        return names.any { name ->
            "(p" in name ||
                "(kp)" in name ||
                "toxic blowpipe" in name ||
                "serpentine" in name ||
                "trident of the swamp" in name ||
                "toxic staff" in name ||
                "venator" in name
        }
    }

    // Phase 1
    // ------------------------------------------------------------------------------------------

    private fun tickPhase1(npc: Npc) {
        if (ticks < P1_FIRST || (ticks - P1_FIRST) % P1_EVERY != 0) {
            return
        }
        npc.anim(VerzikSeqs.p1_blast)
        sound(VerzikSynths.attack)
        later(2) {
            if (phase != 1) return@later
            val sheltering = HashMap<Pillar, Int>()
            val from = centre(npc)
            for (player in fightPlayers()) {
                val pillar = pillarCovering(npc, player)
                if (pillar != null) {
                    sheltering.merge(pillar, 1, Int::plus)
                    continue
                }
                val to = realOf(player.coords) ?: continue
                val flight =
                    projectileBy(from, to, VerzikSpots.blast, 20, 0, 15, heights = 100 to 25)
                later(flight) {
                    if (phase == 1 && player in fightPlayers()) {
                        player.spotanim(VerzikSpots.blast_player)
                        val damage = roll(0, P1_MAX)
                        hurt(
                            player,
                            if (prays(player, NpcAttackStyle.Magic)) damage / 2 else damage,
                        )
                    }
                }
            }
            val pillar = sheltering.entries.minByOrNull { it.value }?.key ?: return@later
            val flight =
                projectileBy(
                    from,
                    pillar.tile.translate(1, 1),
                    VerzikSpots.blast,
                    20,
                    0,
                    15,
                    heights = 100 to 37,
                )
            later(flight) {
                if (pillar in pillars) {
                    pillar.npc.spotanim(VerzikSpots.blast_pillar)
                    pillar.npc.hitpoints = (pillar.npc.hitpoints - roll(40, 60)).coerceAtLeast(0)
                }
            }
        }
    }

    private fun pillarCovering(npc: Npc, player: Player): Pillar? {
        if (canSee(npc, player.coords)) {
            return null
        }
        return pillars
            .filter { gap(it.npc, player.coords) <= 4 }
            .minByOrNull { gap(it.npc, player.coords) }
    }

    private fun tickPillars() {
        for (pillar in pillars.toList()) {
            if (pillar.npc.isSlotAssigned && pillar.npc.hitpoints > 0) {
                continue
            }
            collapse(pillar)
        }
    }

    private fun collapse(pillar: Pillar) {
        pillars.remove(pillar)
        removeLoc(pillar.tile)
        despawn(pillar.npc)
        val rubble =
            spawn(VerzikNpcs.collapsing, pillar.tile.translate(-1, -1), ignoreCollision = true)
        rubble.anim(VerzikSeqs.pillar_collapse)
        later(1) {
            for (player in fightPlayers()) {
                if (gap(rubble, player.coords) <= 1) {
                    hurt(player, roll(COLLAPSE_MAX / 2, COLLAPSE_MAX))
                    player.mes("The collapsing pillar greatly harms you.")
                }
            }
        }
        later(2) {
            transform(rubble, VerzikNpcs.fading)
            rubble.anim(VerzikSeqs.pillar_fade)
        }
        later(4) { despawn(rubble) }
    }

    // Phase 2
    // ------------------------------------------------------------------------------------------

    private fun toPhase2(old: Npc) {
        transition = true
        phase = 2
        old.anim(VerzikSeqs.off_throne)
        for (player in roomPlayers()) {
            val had =
                player.inv.count(objTypes[tob_objs.dawnbringer]) > 0 ||
                    player.worn.count(objTypes[tob_objs.dawnbringer]) > 0
            env.raids.removeDawnbringer(player)
            if (had) {
                player.mes("The weapon falls apart in your hand as Verzik's shield is destroyed.")
            }
        }
        later(3) {
            despawn(old)
            val flying =
                spawn(
                    VerzikNpcs.to_phase2,
                    THRONE,
                    scale = true,
                    hitpoints = PHASE_HITPOINTS,
                    ignoreCollision = true,
                )
            verzik = flying
            bossBar = flying
            flying.anim(VerzikSeqs.p2_spawn)
            flying.say("You think you can defeat me?")
            for (pillar in pillars.toList()) {
                collapse(pillar)
            }
            later(1) { flying.walk(regionOf(PHASE2_TILE)) }
            later(TO_PHASE2_TICKS) {
                despawn(flying)
                val npc =
                    spawn(
                        VerzikNpcs.phase2,
                        PHASE2_TILE,
                        scale = true,
                        hitpoints = PHASE_HITPOINTS,
                        facing = Direction.South,
                    )
                verzik = npc
                bossBar = npc
                ticks = 0
                attackCounter = -1
                lightnings = 0
                healing = false
                transition = false
            }
        }
    }

    private fun tickPhase2(npc: Npc) {
        if (ticks % P2_EVERY != 0) {
            return
        }
        val players = fightPlayers()
        if (players.isEmpty()) {
            return
        }
        if (!healing && npc.hitpoints * 100 <= npc.baseHitpointsLvl * HEAL_BELOW) {
            healing = true
            attackCounter = 0
            callMatomenos(npc)
            return
        }
        val under = players.filter { gap(npc, it.coords) <= 0 }
        if (under.isNotEmpty()) {
            bounce(npc, under)
            return
        }
        if (!healing) {
            when {
                attackCounter == 4 -> {
                    attackCounter = 0
                    lightnings++
                    lightning(npc)
                }
                attackCounter == -1 || lightnings == 5 -> {
                    lightnings = 0
                    attackCounter = 0
                    npc.anim(VerzikSeqs.p2_attack)
                    purpleBall(npc)
                    callNylocas(npc)
                }
                else -> {
                    attackCounter++
                    urns(npc)
                }
            }
            return
        }
        if (attackCounter > 0 && attackCounter % MATOMENOS_EVERY == 0) {
            callMatomenos(npc)
        } else {
            val roll = env.random.of(100)
            when {
                roll >= 25 -> bloodSpell(npc)
                roll >= 15 -> lightning(npc)
                else -> urns(npc)
            }
        }
        attackCounter++
    }

    private fun bounce(npc: Npc, players: List<Player>) {
        npc.anim(VerzikSeqs.p2_bounce)
        sound(VerzikSynths.attack)
        val centre = centre(npc)
        for (player in players) {
            player.abortRoute()
            player.freeze(BOUNCE_STUN)
            later(1) {
                hurt(player, roll(0, BOUNCE_MAX))
                val from = realOf(player.coords) ?: return@later
                val dx = (from.x - centre.x).sign
                val dz = (from.z - centre.z).sign.let { if (it == 0 && dx == 0) -1 else it }
                val to = from.translate(dx * 2, dz * 2)
                if (room.inArena(to) && walkable(to)) {
                    knockback(player, to)
                }
            }
        }
    }

    private fun urns(npc: Npc) {
        npc.anim(VerzikSeqs.p2_attack)
        sound(VerzikSynths.attack)
        val from = centre(npc)
        for (player in fightPlayers()) {
            val tile = realOf(player.coords) ?: continue
            val flight = projectileBy(from, tile, VerzikSpots.urn, 21, 36, 18, heights = 70 to 0)
            later(flight) {
                graphic(VerzikSpots.urn_land, tile)
                if (player in fightPlayers() && realOf(player.coords) == tile) {
                    val damage = roll(0, URN_MAX)
                    hurt(player, if (prays(player, NpcAttackStyle.Ranged)) damage / 2 else damage)
                }
            }
        }
    }

    private fun lightning(npc: Npc) {
        val order = shuffled(fightPlayers())
        if (order.isEmpty()) {
            return
        }
        npc.anim(VerzikSeqs.p2_attack)
        sendLightning(centre(npc), order, 0)
    }

    private fun sendLightning(from: CoordGrid, order: List<Player>, index: Int) {
        val to = order[index]
        val tile = realOf(to.coords) ?: return
        val flight =
            projectileBy(
                from,
                tile,
                VerzikSpots.lightning,
                if (index == 0) 21 else 0,
                30,
                18,
                heights = 70 to 24,
            )
        later(flight) {
            if (phase != 2 || transition || to !in fightPlayers()) {
                return@later
            }
            if (index + 1 >= order.size) {
                hurt(to, roll(LIGHTNING_LAST_MIN, LIGHTNING_LAST_MAX))
                return@later
            }
            hurt(to, roll(LIGHTNING_MIN, LIGHTNING_MAX))
            sendLightning(realOf(to.coords) ?: tile, order, index + 1)
        }
    }

    private fun purpleBall(npc: Npc) {
        val target = fightPlayers().takeIf { it.isNotEmpty() }?.let(::pick) ?: return
        val tile = realOf(target.coords) ?: return
        val flight =
            projectileBy(centre(npc), tile, VerzikSpots.purple, 21, 99, 60, heights = 70 to 1)
        later(flight) {
            if (phase != 2 || transition) {
                return@later
            }
            if (target in fightPlayers() && realOf(target.coords) == tile) {
                hurt(target, roll(0, PURPLE_MAX))
            }
            val athanatos = spawn(VerzikNpcs.athanatos, tile.translate(-1, -1))
            minions[athanatos] = Minion(athanatos, Kind.Athanatos)
        }
    }

    private fun callNylocas(npc: Npc) {
        val centre = centre(npc)
        for (player in fightPlayers()) {
            val from = realOf(player.coords) ?: continue
            val dx = (from.x - centre.x).sign
            val dz = (from.z - centre.z).sign
            var tile: CoordGrid? = null
            for (distance in 11 downTo 2) {
                val candidate = from.translate(dx * distance, dz * distance)
                if (room.inArena(candidate) && walkable(candidate)) {
                    tile = candidate
                    break
                }
            }
            tile ?: continue
            val style = pick(NyloStyle.entries)
            val nylo = spawn(VerzikNpcs.nylos.getValue(style), tile)
            minions[nylo] =
                Minion(nylo, Kind.Nylo).also {
                    it.style = style
                    it.target = player
                }
        }
    }

    private fun bloodSpell(npc: Npc) {
        val target = fightPlayers().takeIf { it.isNotEmpty() }?.let(::pick) ?: return
        npc.anim(VerzikSeqs.p2_attack)
        val to = realOf(target.coords) ?: return
        val blocked = prays(target, NpcAttackStyle.Magic)
        val flight =
            projectileBy(centre(npc), to, VerzikSpots.blood, 21, 36, 18, heights = 70 to 24)
        later(flight) {
            if (blocked || target !in fightPlayers()) return@later
            val damage = roll(0, BLOOD_MAX)
            if (damage > 0) {
                target.spotanim(VerzikSpots.blood_land)
                hurt(target, damage)
                verzik?.let { heal(it, damage / 2) }
            }
        }
    }

    private fun callMatomenos(npc: Npc) {
        npc.anim(VerzikSeqs.p2_heal)
        sound(VerzikSynths.heal)
        shieldUntil = now + SHIELD_TICKS
        for (old in matomenos.toList()) {
            if (old.isSlotAssigned && old.hitpoints > 0) {
                heal(npc, old.hitpoints)
            }
            minions.remove(old)
            despawn(old)
        }
        matomenos.clear()
        val count = if (fightPlayers().size > 1) 2 else 1
        for (tile in MATOMENOS_TILES.take(count)) {
            val nylo = spawn(VerzikNpcs.matomenos, tile)
            matomenos += nylo
            minions[nylo] = Minion(nylo, Kind.Matomenos)
        }
    }

    // Minions
    // ------------------------------------------------------------------------------------------

    private fun tickMinions(boss: Npc) {
        for (minion in minions.values.toList()) {
            val npc = minion.npc
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                minions.remove(npc)
                continue
            }
            minion.age++
            when (minion.kind) {
                Kind.Nylo -> tickNylo(minion)
                Kind.Athanatos -> tickAthanatos(boss, minion)
                Kind.Tornado -> tickTornado(boss, minion)
                Kind.Matomenos -> Unit
            }
        }
    }

    private fun tickNylo(minion: Minion) {
        val target = minion.target
        val npc = minion.npc
        val alive = target != null && target in fightPlayers()
        if ((alive && gap(npc, checkNotNull(target).coords) <= 1) || minion.age >= NYLO_FUSE) {
            minions.remove(npc)
            for (player in fightPlayers()) {
                val distance = gap(npc, player.coords)
                val damage = NYLO_FALLOFF.getOrNull((distance - 1).coerceAtLeast(0)) ?: 0
                if (distance < 4 && damage > 0) {
                    hurt(player, damage)
                }
            }
            despawn(npc)
            return
        }
        if (alive) {
            npc.facePlayer(checkNotNull(target))
            stepToward(npc, target.coords, keepOff = true)
        }
    }

    private fun tickAthanatos(boss: Npc, minion: Minion) {
        val npc = minion.npc
        npc.faceSquare(boss.coords, boss.size, boss.size)
        if (minion.age >= ATHANATOS_LIFE) {
            heal(boss, npc.hitpoints)
            minions.remove(npc)
            despawn(npc)
            return
        }
        if (minion.age % ATHANATOS_EVERY != 0 || transition) {
            return
        }
        val from = realOf(npc.coords) ?: return
        val delay = roll(0, 5)
        val spot = if (minion.poisoned) VerzikSpots.poison_globule else VerzikSpots.heal_globule
        val flight = projectile(from, centre(boss), spot, delay..22, heights = 5 to 70)
        if (minion.poisoned) {
            minions.remove(npc)
            later(flight) {
                verzik?.let { v -> v.hitpoints = (v.hitpoints - roll(70, 75)).coerceAtLeast(1) }
            }
            despawn(npc)
            return
        }
        later(flight) { verzik?.let { heal(it, roll(9, 11)) } }
    }

    // Phase 3
    // ------------------------------------------------------------------------------------------

    private fun toPhase3(old: Npc) {
        transition = true
        phase = 3
        for (nylo in matomenos.toList()) {
            minions.remove(nylo)
            despawn(nylo)
        }
        matomenos.clear()
        old.resetMovement()
        old.anim(VerzikSeqs.p2_death)
        later(1) {
            transform(old, VerzikNpcs.to_phase3)
            old.anim(VerzikSeqs.p3_spawn)
        }
        later(3) {
            despawn(old)
            val npc =
                spawn(
                    VerzikNpcs.phase3,
                    PHASE3_TILE,
                    scale = true,
                    hitpoints = PHASE_HITPOINTS,
                    facing = Direction.South,
                )
            verzik = npc
            bossBar = npc
            npc.say("Behold my true nature!")
            ticks = 0
            attacks = 0
            special = 0
            busyUntil = 0
            enraged = false
            transition = false
        }
    }

    private fun tickPhase3(npc: Npc) {
        if (!enraged && npc.hitpoints * 100 <= npc.baseHitpointsLvl * ENRAGE_BELOW) {
            enraged = true
            npc.say("I'm not finished with you just yet!")
            for (player in fightPlayers()) {
                sendTornado(player)
            }
        }
        if (now < busyUntil) {
            return
        }
        val players = fightPlayers()
        if (target?.let { it in players } != true) {
            target = if (players.isEmpty()) null else pick(players)
        }
        val target = target ?: return
        chase(npc, target)
        if (ticks % (if (enraged) P3_ENRAGED_EVERY else P3_EVERY) != 0) {
            return
        }
        if (attacks == SPECIAL_EVERY) {
            attacks = 0
            val next = special++ % 4
            when (next) {
                0 -> callNylocas(npc)
                1 -> webs(npc)
                2 -> dots(npc)
                else -> greenBall(npc)
            }
            if (next != 0) {
                return
            }
        }
        attacks++
        autoAttack(npc, target)
    }

    private fun chase(npc: Npc, target: Player) {
        npc.facePlayer(target)
        if (npc.isFrozen || gap(npc, target.coords) <= 1) {
            return
        }
        stepToward(npc, target.coords, keepOff = true)
    }

    private fun autoAttack(npc: Npc, target: Player) {
        val players = fightPlayers()
        if (gap(npc, target.coords) == 1 && env.random.of(2) == 0) {
            npc.anim(VerzikSeqs.p3_melee)
            for (player in players) {
                val beside =
                    maxOf(
                        abs(player.coords.x - target.coords.x),
                        abs(player.coords.z - target.coords.z),
                    ) <= 1
                if (!beside || gap(npc, player.coords) != 1) {
                    continue
                }
                strike(
                    npc,
                    player,
                    NpcAttack(NpcAttackStyle.Melee, maxHit = P3_MELEE, hitDelay = 1),
                    through = 0,
                    playAnim = false,
                )
            }
            return
        }
        val magic = env.random.of(2) == 0
        npc.anim(if (magic) VerzikSeqs.p3_magic else VerzikSeqs.p3_ranged)
        val from = centre(npc)
        for (player in players) {
            val to = realOf(player.coords) ?: continue
            val flight =
                if (magic)
                    projectileBy(from, to, VerzikSpots.p3_magic, 53, 0, 15, heights = 100 to 25)
                else projectileBy(from, to, VerzikSpots.p3_ranged, 62, 0, 15, heights = 100 to 25)
            val style = if (magic) NpcAttackStyle.Magic else NpcAttackStyle.Ranged
            val attack =
                NpcAttack(style, maxHit = P3_RANGED_MAGIC, hitDelay = (flight - 1).coerceAtLeast(1))
            strike(npc, player, attack, through = 50, playAnim = false)
        }
    }

    private fun webs(npc: Npc) {
        busyUntil = now + WEB_SPIN
        npc.walk(regionOf(WEB_CENTRE))
        later(3) {
            if (phase != 3) return@later
            npc.resetMovement()
            val centre = centre(npc)
            for (player in fightPlayers()) {
                if (gap(npc, player.coords) > 3) continue
                val from = realOf(player.coords) ?: continue
                val to = from.translate((from.x - centre.x).sign * 4, (from.z - centre.z).sign * 4)
                if (room.inArena(to) && walkable(to)) {
                    knockback(player, to)
                }
            }
            npc.anim(VerzikSeqs.p3_webs)
            val players = fightPlayers()
            for (i in 0 until WEB_SPIN - 10) {
                if (players.isEmpty()) break
                later(i) {
                    val target = players[(i / 3) % players.size]
                    if (target !in fightPlayers()) return@later
                    val end = realOf(target.coords) ?: return@later
                    shootWeb(npc, end)
                    repeat(2) { shootWeb(npc, end.translate(roll(-3, 3), roll(-3, 3))) }
                }
            }
        }
    }

    private fun shootWeb(npc: Npc, tile: CoordGrid) {
        if (
            !room.inArena(tile) || tile in webs || !walkable(tile) || gap(npc, regionOf(tile)) == 0
        ) {
            return
        }
        val delay = roll(0, 10)
        val flight =
            projectileBy(centre(npc), tile, VerzikSpots.web, delay, 0, 15, heights = 87 to 0)
        later(flight) {
            if (phase != 3 || tile in webs) return@later
            placeLoc(tile, VerzikLocs.web)
            webs[tile] = Web(tile, spawn(VerzikNpcs.web, tile))
        }
    }

    private fun tickWebs() {
        for (web in webs.values.toList()) {
            web.age++
            val onIt = fightPlayers().filter { realOf(it.coords) == web.tile }
            if (!web.npc.isSlotAssigned || web.npc.hitpoints <= 0) {
                for (player in onIt) {
                    player.frozenUntil = Int.MIN_VALUE
                    player.mes("The web crumbles under your attack and you are released safely.")
                }
                removeWeb(web)
                continue
            }
            for (player in onIt) {
                if (web.stuck.add(player)) {
                    player.abortRoute()
                    player.freeze(WEB_FREEZE)
                    player.mes("You're stuck in the web!")
                }
            }
            if (web.age < WEB_LIFE) continue
            for (player in onIt) {
                player.frozenUntil = Int.MIN_VALUE
                player.spotanim(VerzikSpots.blast_hit)
                player.mes("The web explodes and you are released.")
                hurt(player, roll(0, WEB_MAX))
            }
            removeWeb(web)
        }
    }

    private fun removeWeb(web: Web) {
        webs.remove(web.tile)
        removeLoc(web.tile)
        despawn(web.npc)
    }

    private fun dots(npc: Npc) {
        busyUntil = now + DOTS_TICKS
        npc.resetMovement()
        npc.anim(VerzikSeqs.p3_dots)
        val players = fightPlayers()
        val arena = checkNotNull(room.arena)
        val dots = LinkedHashSet<CoordGrid>()
        var tries = 0
        while (dots.size < players.size && tries++ < 200) {
            val tile = CoordGrid(roll(arena.minX, arena.maxX), roll(arena.minZ, arena.maxZ), 0)
            if (walkable(tile) && gap(npc, regionOf(tile)) > 0) {
                dots += tile
            }
        }
        val show = { dots.forEach { graphic(VerzikSpots.dot, it) } }
        show()
        later(5) { show() }
        later(10) { show() }
        later(9) {
            val from = centre(npc)
            for (player in fightPlayers()) {
                realOf(player.coords)?.let {
                    projectile(from, it, VerzikSpots.dot_blast, 261..405, heights = 250 to 30)
                }
            }
        }
        later(DOTS_TICKS) {
            val safe = HashSet<Player>()
            for (dot in dots) {
                fightPlayers()
                    .firstOrNull { it !in safe && realOf(it.coords) == dot }
                    ?.let(safe::add)
            }
            for (player in fightPlayers()) {
                if (player in safe) {
                    player.spotanim(VerzikSpots.dot_safe)
                    player.mes("The power resonating here protects you from the blast.")
                } else {
                    player.spotanim(VerzikSpots.blast_hit, height = 96)
                    hurt(player, DOTS_DAMAGE)
                }
            }
        }
    }

    private fun greenBall(npc: Npc) {
        val first = fightPlayers().takeIf { it.isNotEmpty() }?.let(::pick) ?: return
        npc.anim(VerzikSeqs.p3_ranged)
        later(2) { bounceGreen(centre(npc), first, 0) }
    }

    private fun bounceGreen(from: CoordGrid, target: Player, bounces: Int) {
        val to = realOf(target.coords) ?: return
        val flight =
            if (bounces == 0)
                projectile(from, to, VerzikSpots.green_ball, 62..242, heights = 100 to 30)
            else projectile(from, to, VerzikSpots.green_ball, 9..189, heights = 30 to 30)
        later(flight) {
            if (phase != 3 || target !in fightPlayers()) return@later
            val closest =
                fightPlayers()
                    .filter { it !== target }
                    .minByOrNull {
                        maxOf(
                            abs(it.coords.x - target.coords.x),
                            abs(it.coords.z - target.coords.z),
                        )
                    }
            val distance =
                closest?.let {
                    maxOf(abs(it.coords.x - target.coords.x), abs(it.coords.z - target.coords.z))
                } ?: Int.MAX_VALUE
            if (closest == null || distance > GREEN_NEAR || bounces >= GREEN_BOUNCES) {
                target.spotanim(VerzikSpots.blast_hit, height = 96)
                hurt(
                    target,
                    roll(1, (target.statBase(stats.hitpoints) * GREEN_SHARE / 100).coerceAtLeast(1)),
                )
                return@later
            }
            hurt(target, roll(0, GREEN_BOUNCE_MAX))
            bounceGreen(realOf(target.coords) ?: to, closest, bounces + 1)
        }
    }

    private fun sendTornado(player: Player) {
        val from = realOf(player.coords) ?: return
        repeat(32) {
            val tile = from.translate(roll(-5, 5), roll(-5, 5))
            val distance = maxOf(abs(tile.x - from.x), abs(tile.z - from.z))
            if (distance >= 4 && room.inArena(tile) && walkable(tile)) {
                val tornado = spawn(VerzikNpcs.tornado, tile, passThrough = true)
                minions[tornado] = Minion(tornado, Kind.Tornado).also { it.target = player }
                return
            }
        }
    }

    private fun tickTornado(boss: Npc, minion: Minion) {
        val target = minion.target
        val npc = minion.npc
        if (target == null || target !in fightPlayers()) {
            minions.remove(npc)
            despawn(npc)
            return
        }
        if (npc.coords == target.coords) {
            minions.remove(npc)
            despawn(npc)
            target.spotanim(VerzikSpots.tornado_hit)
            val damage = maxOf(TORNADO_MIN, target.stat(stats.hitpoints) / 2)
            hurt(target, damage)
            heal(boss, damage * TORNADO_HEAL)
            target.mes("Verzik Vitur saps your health and powers up!")
            later(TORNADO_RESPAWN) {
                if (phase == 3 && isStarted) {
                    sendTornado(target)
                }
            }
            return
        }
        stepToward(npc, target.coords, keepOff = false)
    }

    // The end
    // ------------------------------------------------------------------------------------------

    private fun die(npc: Npc) {
        transition = true
        npc.resetMovement()
        for (minion in minions.values.toList()) {
            despawn(minion.npc)
        }
        minions.clear()
        for (web in webs.values.toList()) {
            removeWeb(web)
        }
        npc.anim(VerzikSeqs.p3_death)
        sound(VerzikSynths.death)
        later(3) { transform(npc, VerzikNpcs.dead) }
        later(8) {
            despawn(npc)
            verzik = null
            complete()
        }
    }

    override fun onComplete() {
        placeLoc(TREASURE_DOOR, tob_locs.treasure_door, angle = LocAngle.West)
        env.raids.raidWon(raid)
    }

    private fun centre(npc: Npc): CoordGrid =
        (realOf(npc.coords) ?: THRONE).translate(npc.size / 2, npc.size / 2)

    companion object {
        val THRONE = CoordGrid(3166, 4323, 0)
        val PHASE2_TILE = CoordGrid(3167, 4313, 0)
        val PHASE3_TILE = CoordGrid(3165, 4311, 0)
        val WEB_CENTRE = CoordGrid(3165, 4310, 0)
        val TREASURE_DOOR = CoordGrid(3167, 4324, 0)
        val PILLARS =
            listOf(
                CoordGrid(3161, 4306, 0),
                CoordGrid(3173, 4306, 0),
                CoordGrid(3161, 4312, 0),
                CoordGrid(3173, 4312, 0),
                CoordGrid(3161, 4318, 0),
                CoordGrid(3173, 4318, 0),
            )
        val MATOMENOS_TILES = listOf(CoordGrid(3174, 4312, 0), CoordGrid(3162, 4312, 0))

        /** Phases 2 and 3 have 3500 hitpoints for five (the cache's), scaled like the rest. */
        const val PHASE_HITPOINTS = 3500
        private const val TO_PHASE2_TICKS = 11

        private const val P1_FIRST = 19
        private const val P1_EVERY = 14
        const val P1_MAX = 137
        const val CAP_MELEE = 10
        const val CAP_OTHER = 3

        /** Pulsate always does at least 75: a Dawnbringer hit that big is its special. */
        private const val PULSATE_MIN = 75
        private const val COLLAPSE_MAX = 70

        private const val P2_EVERY = 4
        private const val HEAL_BELOW = 35
        private const val BOUNCE_MAX = 50
        private const val BOUNCE_STUN = 7
        private const val URN_MAX = 44
        private const val LIGHTNING_MIN = 4
        private const val LIGHTNING_MAX = 8
        private const val LIGHTNING_LAST_MIN = 45
        private const val LIGHTNING_LAST_MAX = 48
        private const val PURPLE_MAX = 78
        private const val BLOOD_MAX = 29
        private const val SHIELD_TICKS = 5
        private const val MATOMENOS_EVERY = 7
        private const val NYLO_FUSE = 25
        private val NYLO_FALLOFF = listOf(63, 26, 8)
        private const val ATHANATOS_LIFE = 50
        private const val ATHANATOS_EVERY = 4

        private const val P3_EVERY = 7
        private const val P3_ENRAGED_EVERY = 5
        private const val ENRAGE_BELOW = 20
        private const val P3_MELEE = 63
        private const val P3_RANGED_MAGIC = 33
        private const val SPECIAL_EVERY = 4
        private const val WEB_SPIN = 35
        private const val WEB_LIFE = 12
        private const val WEB_MAX = 40
        private const val WEB_FREEZE = 20
        private const val DOTS_TICKS = 13
        private const val DOTS_DAMAGE = 80
        private const val GREEN_BOUNCES = 3
        private const val GREEN_BOUNCE_MAX = 15
        private const val GREEN_NEAR = 2
        private const val GREEN_SHARE = 74
        private const val TORNADO_MIN = 5
        private const val TORNADO_HEAL = 3
        private const val TORNADO_RESPAWN = 15

        private const val WEAPON_SLOT = 3
        private const val AMMO_SLOT = 13
    }
}

class TobVerzikScript
@Inject
constructor(
    private val rooms: TobRoomRegistry,
    private val raids: TobRaids,
    private val npcRepo: NpcRepository,
    private val objTypes: ObjTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(TobRoom.Verzik) { env, raid -> VerzikRuntime(env, raid, objTypes) }
        tobBosses(rooms, VerzikNpcs.forms + listOf(VerzikNpcs.pillar))
        val deaths = HashMap<NpcType, org.rsmod.game.type.seq.SeqType?>()
        for ((style, type) in VerzikNpcs.nylos) {
            deaths[type] = NylocasSeqs.death.getValue(style)
        }
        deaths[VerzikNpcs.athanatos] = null
        deaths[VerzikNpcs.matomenos] = MaidenSeqs.matomenos_death
        deaths[VerzikNpcs.web] = null
        deaths[VerzikNpcs.tornado] = null
        tobMonsterDeaths(rooms, npcRepo, deaths)
        onOpNpc1(VerzikNpcs.waiting) { talkToVerzik() }
        onOpLoc1(XarpusLocs.skeleton) { searchSkeleton() }
    }

    private suspend fun ProtectedAccess.talkToVerzik() {
        val raid = raids.raidOf(player) ?: return
        if (raid.member(player)?.room != TobRoom.Verzik || raids.isStarted(raid, TobRoom.Verzik)) {
            return
        }
        if (!raid.isLeader(player)) {
            val leader = raid.leader?.player?.displayName ?: "your leader"
            startDialogue { mesbox("Your leader, $leader, must speak to Verzik.") }
            return
        }
        var begin = false
        startDialogue {
            mesbox(
                "Verzik Vitur: Now that was quite the show! I haven't been that entertained in a long time."
            )
            mesbox(
                "Verzik Vitur: Of course, you know I can't let you leave here alive. Time for your final performance..."
            )
            begin =
                choice2(
                    "Yes, let's begin.",
                    true,
                    "No, don't start yet.",
                    false,
                    title = "Is your party ready to fight?",
                )
        }
        if (begin) {
            raids.startFightHere(raid, TobRoom.Verzik)
        }
    }

    /** The skeleton in Xarpus's alcove holds the Dawnbringer once Xarpus is beaten: one a party. */
    private fun ProtectedAccess.searchSkeleton() {
        val raid = raids.raidOf(player) ?: return
        if (TobRoom.Xarpus !in raid.completed) {
            mes("You can't search this until the challenge is complete.")
            return
        }
        val dawnbringer = objTypes[tob_objs.dawnbringer]
        val held =
            raid.active.any {
                it.player.inv.count(dawnbringer) > 0 || it.player.worn.count(dawnbringer) > 0
            }
        if (held) {
            mes("The skeleton is empty. Someone in your party has the Dawnbringer.")
            return
        }
        if (player.inv.freeSpace() <= 0) {
            mes("You don't have enough inventory space to take the Dawnbringer.")
            return
        }
        player.invAdd(player.inv, dawnbringer, 1)
        mes("You find the Dawnbringer; you feel a pulse of energy burst through it.")
    }
}
