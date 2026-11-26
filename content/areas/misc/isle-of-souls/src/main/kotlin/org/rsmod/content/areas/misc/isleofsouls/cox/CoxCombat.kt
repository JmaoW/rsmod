package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.areas.misc.isleofsouls.toa.ToaProjAnims
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Small shared rules the rooms read. */
object CoxCombat {
    /** Whether [player]'s spell is a fire spell (by their autocast; set by [CoxCombatScript]). */
    var isFireSpell: (Player) -> Boolean = { false }

    /** The wielded weapon's name, lower case ("" when unarmed). */
    var weaponName: (Player) -> String = { "" }

    fun wieldsPickaxe(player: Player): Boolean = weaponName(player).endsWith("pickaxe")

    fun wieldsHalberd(player: Player): Boolean = weaponName(player).contains("halberd")
}

/** The combat rooms and scavengers, and the room locs they share (fires, tendrils, crystals). */
class CoxCombatScript
@Inject
constructor(
    private val rooms: CoxRoomRegistry,
    private val raids: CoxRaids,
    private val npcRepo: NpcRepository,
    private val objTypes: ObjTypeList,
    private val spells: MagicSpellRegistry,
) : PluginScript() {
    override fun ScriptContext.startup() {
        CoxCombat.isFireSpell = { player ->
            val spell = spells.getAutocastSpell(player.vars[varbits.autocast_spell])
            spell != null && objTypes[spell.obj].name.lowercase().startsWith("fire")
        }
        CoxCombat.weaponName = { player ->
            player.worn[WEAPON_SLOT]?.let { objTypes[it].name.lowercase() } ?: ""
        }

        rooms.register(CoxRoomType.SmallScavengers) { env, raid, room ->
            CoxScavengers(env, raid, room)
        }
        rooms.register(CoxRoomType.LargeScavengers) { env, raid, room ->
            CoxScavengers(env, raid, room)
        }
        rooms.register(CoxRoomType.Tekton) { env, raid, room -> CoxTekton(env, raid, room) }
        rooms.register(CoxRoomType.Muttadiles) { env, raid, room -> CoxMuttadiles(env, raid, room) }
        rooms.register(CoxRoomType.Vanguards) { env, raid, room -> CoxVanguards(env, raid, room) }
        rooms.register(CoxRoomType.Vasa) { env, raid, room -> CoxVasa(env, raid, room) }
        rooms.register(CoxRoomType.Vespula) { env, raid, room -> CoxVespula(env, raid, room) }
        rooms.register(CoxRoomType.Guardians) { env, raid, room -> CoxGuardians(env, raid, room) }
        rooms.register(CoxRoomType.Shamans) { env, raid, room -> CoxShamans(env, raid, room) }
        rooms.register(CoxRoomType.Mystics) { env, raid, room -> CoxMystics(env, raid, room) }

        for (loc in listOf(cox_locs.tekton_fire, cox_locs.vasa_fire)) onOpLoc1(loc) {
            crossOver(it.loc)
        }
        for (loc in
            listOf(cox_locs.mutt_tendrils, cox_locs.shaman_tendrils, cox_locs.vespula_tendrils)) {
            onOpLoc1(loc) { crossOver(it.loc) }
        }
        onOpLoc1(cox_locs.corridor_rocks) { shortcut(it.loc) }
        onOpLoc1(cox_locs.corridor_roots) { shortcut(it.loc) }
        onOpLoc1(cox_locs.vespula_herb) { pickBlossom() }
        for (grub in listOf(cox_npcs.grub_healthy, cox_npcs.grub_sickly, cox_npcs.grub_infected)) {
            onOpNpcU(grub, cox_objs.vespula_herb) { feedGrub(it.npc) }
        }
        onOpNpc1(cox_npcs.meat_tree) { chopTree(it.npc) }

        coxMonsterDeaths(
            rooms,
            npcRepo,
            mapOf(
                cox_npcs.scavenger_a to cox_seqs.scavenger_death,
                cox_npcs.scavenger_b to cox_seqs.scavenger_death,
                cox_npcs.shaman_a to cox_seqs.shaman_death,
                cox_npcs.shaman_b to cox_seqs.shaman_death,
                cox_npcs.shaman_spawn to null,
                cox_npcs.mystic_a to cox_seqs.mystic_death,
                cox_npcs.mystic_b to cox_seqs.mystic_death,
                cox_npcs.mystic_c to cox_seqs.mystic_death,
                cox_npcs.soldier_flying to null,
                cox_npcs.soldier_walking to null,
                cox_npcs.vasa_crystal to null,
                cox_npcs.mutt_small to null,
            ),
        )
        coxBosses(
            rooms,
            listOf(
                cox_npcs.tekton_waiting,
                cox_npcs.tekton_walking,
                cox_npcs.tekton_fighting,
                cox_npcs.tekton_walking_enraged,
                cox_npcs.tekton_fighting_enraged,
                cox_npcs.tekton_hammering,
                cox_npcs.mutt_submerged,
                cox_npcs.mutt_large,
                cox_npcs.vanguard_dormant,
                cox_npcs.vanguard_walking,
                cox_npcs.vanguard_melee,
                cox_npcs.vanguard_ranged,
                cox_npcs.vanguard_magic,
                cox_npcs.vasa_dormant,
                cox_npcs.vasa_walking,
                cox_npcs.vasa_healing,
                cox_npcs.vespula_flying,
                cox_npcs.vespula_enraged,
                cox_npcs.vespula_walking,
                cox_npcs.vespula_portal,
                cox_npcs.grub_healthy,
                cox_npcs.grub_sickly,
                cox_npcs.grub_infected,
                cox_npcs.guardian_left,
                cox_npcs.guardian_right,
                cox_npcs.meat_tree,
            ),
        )
    }

    private fun ProtectedAccess.runtime(): CoxRoomRuntime? {
        val raid = raids.raidOf(player) ?: return null
        val room = raid.roomAt(player.coords) ?: return null
        return rooms.runtime(raid, room)
    }

    private fun ProtectedAccess.crossOver(loc: BoundLocInfo) {
        val footprint =
            (0 until loc.adjustedWidth).flatMap { dx ->
                (0 until loc.adjustedLength).map { dz -> loc.coords.translate(dx, dz) }
            }
        raids.crossOver(player, footprint)
    }

    private suspend fun ProtectedAccess.shortcut(loc: BoundLocInfo) {
        val room = runtime() as? CoxScavengers ?: return
        room.swing(this, loc)
    }

    private fun ProtectedAccess.pickBlossom() {
        if (inv.isFull()) {
            mes("You don't have room for a blossom.")
            return
        }
        anim(cox_seqs.picking)
        invAdd(inv, cox_objs.vespula_herb, 1)
    }

    private fun ProtectedAccess.feedGrub(npc: Npc) {
        val room = runtime() as? CoxVespula ?: return
        invDel(inv, cox_objs.vespula_herb, 1)
        room.feed(player, npc)
    }

    private suspend fun ProtectedAccess.chopTree(npc: Npc) {
        val room = runtime() as? CoxMuttadiles ?: return
        anim(cox_seqs.woodcutting)
        mes("You swing your axe...")
        delay(CHOP_TICKS)
        room.chop(player)
    }

    private companion object {
        const val WEAPON_SLOT = 3
        const val CHOP_TICKS = 5
    }
}

// Scavengers
// -------------------------------------------------------------------------------------------------

/**
 * Scavenger beasts (Wiki): their number follows the party, each drops three items. TSPS's counts (1
 * solo, 2 up to 4, 3 to 7, 4 to 15, then one more each 8) and its captures (one comes back 4 ticks
 * after another dies). The large room's shortcut (rocks or a rotten sapling) gives 5 x its level in
 * points; the level is random between the party's lowest and highest (TSPS's guess). Drops: the
 * Wiki's secondaries (stinkhorn 3-11, cicely 3-6, juice 5-14, each 2/18); the rest of the table
 * (cave worms, planks, seeds, gourds) is a guess.
 */
class CoxScavengers(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private var shortcut: LocInfo? = null
    private var shortcutLevel = 1
    private var shortcutCleared = false

    override fun build() {
        complete()
        if (checkNotNull(room).type == CoxRoomType.LargeScavengers) {
            val type = if (chance(2)) cox_locs.corridor_rocks else cox_locs.corridor_roots
            shortcut = placeLoc(pick3(SHORTCUTS), type)
        }
    }

    override fun onEntered(player: Player) {
        if (!raid.started || monsters.isNotEmpty() || shortcutLevel > 1) return
        val levels = raid.raiders.map { it.player.statBase(stats.mining) }
        shortcutLevel = roll(levels.minOrNull() ?: 1, (levels.maxOrNull() ?: 1) + 2).coerceIn(1, 99)
        repeat(count(raid.scaling.size)) { spawnBeast() }
    }

    private fun count(raiders: Int): Int =
        when {
            raiders <= 1 -> 1
            raiders <= 4 -> 2
            raiders <= 7 -> 3
            raiders <= 15 -> 4
            else -> minOf(8, 4 + (raiders - 8) / 8)
        }

    private fun spawnBeast() {
        val centre = tile(checkNotNull(room).type.sourceX(variant) + 16, room.type.sourceZ + 16)
        repeat(30) {
            val at = centre.translate(roll(-5, 5), roll(-5, 5))
            if (walkable(at) && walkable(at.translate(1, 1)) && inRoom(at)) {
                val npc =
                    spawnAt(
                        if (chance(2)) cox_npcs.scavenger_a else cox_npcs.scavenger_b,
                        at,
                        CoxScaling.Bases.scavenger,
                    )
                val attack =
                    NpcAttack(
                        NpcAttackStyle.Melee,
                        anim = cox_seqs.scavenger_attack,
                        maxHit = scaledMaxHit(CoxScaling.Bases.scavenger, 13),
                    )
                monster(npc, attack, range = 1, speed = 4)
                return
            }
        }
    }

    override fun onAfter() {
        driveMonsters()
    }

    override fun monsterDied(npc: Npc) {
        val at = npc.coords
        repeat(3) { dropOne(at) }
        later(RESPAWN_TICKS) { if (!raid.ended) spawnBeast() }
    }

    private fun dropOne(at: CoordGrid) {
        when (roll(1, 18)) {
            1,
            2 -> drop(cox_objs.stinkhorn, at, roll(3, 11))
            3,
            4 -> drop(cox_objs.cicely, at, roll(3, 6))
            5,
            6 -> drop(cox_objs.endarkened_juice, at, roll(5, 14))
            7,
            8,
            9 -> drop(cox_objs.cave_worms, at, roll(5, 12))
            10,
            11,
            12 -> drop(cox_objs.plank, at, roll(1, 3))
            13 -> drop(cox_objs.golpar_seed, at, roll(1, 3))
            14 -> drop(cox_objs.buchu_seed, at, roll(1, 3))
            15 -> drop(cox_objs.noxifer_seed, at, roll(1, 2))
            16,
            17 -> drop(cox_objs.gourd_vial, at, roll(1, 3))
            else -> Unit
        }
    }

    suspend fun swing(access: ProtectedAccess, loc: BoundLocInfo) {
        val player = access.player
        if (shortcutCleared) return
        val rocks = env.locRepo.findExact(loc.coords, env.locTypes[cox_locs.corridor_rocks]) != null
        val stat = if (rocks) stats.mining else stats.woodcutting
        if (player.stat(stat) < shortcutLevel) {
            player.mes(
                "You need a ${if (rocks) "Mining" else "Woodcutting"} level of $shortcutLevel to clear this."
            )
            return
        }
        access.anim(if (rocks) cox_seqs.hammer else cox_seqs.woodcutting)
        access.delay(4)
        val odds = (raid.scaling.size / 3).coerceIn(5, 20)
        if (!chance(odds)) return
        shortcutCleared = true
        val current = shortcut ?: return
        swapLoc(
            current,
            if (rocks) cox_locs.corridor_rocks_cleared else cox_locs.corridor_roots_cleared,
        )
        env.raids.addPoints(raid, player, shortcutLevel * 5)
        player.mes("You clear the way.")
    }

    override fun pointsPerHitpoint(npc: Npc): Int = POINTS_PER_HP

    companion object {
        const val RESPAWN_TICKS = 4
        val SHORTCUTS =
            listOf(CoordGrid(3272, 5222, 0), CoordGrid(3314, 5238, 0), CoordGrid(3353, 5224, 0))
    }
}

// Tekton
// -------------------------------------------------------------------------------------------------

/**
 * Tekton (Wiki, TSPS's captures): immune to ranged, magic does a fifth. Woken, he rises and the
 * fire shuts the way in; he walks to the raiders and swings every 3 ticks (stab, slash or crush, 52
 * at the base scale; 59 enraged). After 9 swings (16 after each repair) he goes back to his anvil
 * for 12-21 ticks, sending sparks (2 a raider every 4 ticks, 10-20 damage) and healing a little
 * every 3 ticks; then he comes back enraged until his 6th swing. Drops 5 stinkhorn mushrooms.
 */
class CoxTekton(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private lateinit var tekton: Npc
    private var swings = 0
    private var limit = FIRST_SWINGS
    private var anvilUntil = -1
    private var enragedSwings = 0
    private var nextSwing = 0
    private var fire: LocInfo? = null

    override fun build() {
        tekton =
            spawn(cox_npcs.tekton_waiting, pick3(ANVIL), CoxScaling.Bases.tekton, tekton = true)
        blockExit(listOf(pick3(CRYSTAL)), cox_locs.crystal_orange)
        bossOwned = tekton
    }

    private var bossOwned: Npc? = null

    override fun onEntered(player: Player) {
        if (raid.started && !started) start()
    }

    override fun onStart() {
        fire = placeLoc(pick3(FIRE), cox_locs.tekton_fire)
        tekton.anim(cox_seqs.tekton_rise)
        transform(tekton, cox_npcs.tekton_walking)
        nextSwing = now + 5
    }

    override fun onTick() {
        if (!tekton.isSlotAssigned) return
        if (anvilUntil > 0) {
            anvil()
            return
        }
        val players = fightPlayers()
        val target = players.minByOrNull { gap(tekton, it.coords) } ?: return
        tekton.facePlayer(target)
        if (gap(tekton, target.coords) > 1) {
            stepToward(tekton, target.coords)
            return
        }
        tekton.resetMovement()
        if (now < nextSwing) return
        val enraged = enragedSwings > 0
        transform(
            tekton,
            if (enraged) cox_npcs.tekton_fighting_enraged else cox_npcs.tekton_fighting,
        )
        nextSwing = now + SWING_TICKS
        val type = pick(listOf(MeleeAttackType.Stab, MeleeAttackType.Slash, MeleeAttackType.Crush))
        strike(
            tekton,
            target,
            NpcAttack(
                NpcAttackStyle.Melee,
                maxHit = scaledMaxHit(CoxScaling.Bases.tekton, if (enraged) 59 else 52),
                meleeType = type,
            ),
        )
        if (enraged && --enragedSwings == 0) restat(tekton, CoxScaling.Bases.tekton, tekton = true)
        if (++swings >= limit) {
            swings = 0
            limit = AFTER_REPAIR
            toAnvil()
        }
    }

    private fun toAnvil() {
        tekton.anim(cox_seqs.tekton_to_anvil)
        transform(tekton, cox_npcs.tekton_hammering)
        tekton.teleportTo(tile(pick3(ANVIL), tekton.size))
        anvilUntil = now + pick(ANVIL_TICKS)
    }

    private fun Npc.teleportTo(at: CoordGrid) {
        org.rsmod.game.entity.util.PathingEntityCommon.telejump(this, env.collision, at)
    }

    private fun anvil() {
        val elapsed = anvilUntil - now
        if (elapsed % SPARK_TICKS == 0) {
            for (player in fightPlayers()) {
                repeat(2) {
                    val at = player.coords.translate(roll(-1, 1), roll(-1, 1))
                    val land =
                        projectileBy(
                            tekton.coords,
                            at,
                            cox_spots.meteor,
                            delay = 0,
                            base = 60,
                            perTile = 0,
                            type = ToaProjAnims.lob,
                        )
                    later(land) { if (player.coords == at) hurt(player, roll(10, 20)) }
                }
            }
        }
        if (elapsed % HEAL_TICKS == 0)
            heal(tekton, (tekton.baseHitpointsLvl / 100).coerceAtLeast(1))
        if (now >= anvilUntil) {
            anvilUntil = -1
            enragedSwings = ENRAGED_SWINGS
            CoxScaling.apply(
                tekton,
                CoxScaling.scale(ENRAGED_BASE, scaling, tekton = true),
                keepHitpoints = true,
            )
            transform(tekton, cox_npcs.tekton_walking_enraged)
            tekton.anim(cox_seqs.tekton_rise_enraged)
            nextSwing = now + 2
        }
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc !== tekton) return
        if (!started) {
            hit.damage = 0
            return
        }
        when (hit.type) {
            org.rsmod.game.hit.HitType.Ranged -> hit.damage = 0
            org.rsmod.game.hit.HitType.Magic -> hit.damage /= 5
            else -> Unit
        }
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc !== tekton || completed) return
        for (p in roomPlayers()) drop(cox_objs.stinkhorn, p.coords, 5)
        despawn(tekton)
        fire?.let { removeLoc(it) }
        complete()
    }

    companion object {
        const val FIRST_SWINGS = 9
        const val AFTER_REPAIR = 16
        const val ENRAGED_SWINGS = 6
        const val SWING_TICKS = 3
        const val SPARK_TICKS = 4
        const val HEAL_TICKS = 3
        val ANVIL_TICKS = listOf(12, 15, 18, 21)
        val ENRAGED_BASE = CoxBase(300, 390, 390, 280, 1, 280, magicDefends = true)
        val ANVIL =
            listOf(CoordGrid(3279, 5298, 0), CoordGrid(3307, 5294, 0), CoordGrid(3341, 5298, 0))
        val CRYSTAL =
            listOf(CoordGrid(3270, 5294, 0), CoordGrid(3310, 5304, 0), CoordGrid(3352, 5296, 0))
        val FIRE =
            listOf(CoordGrid(3279, 5281, 0), CoordGrid(3311, 5281, 0), CoordGrid(3343, 5281, 0))
    }
}

// Muttadiles
// -------------------------------------------------------------------------------------------------

/**
 * Muttadiles (Wiki, TSPS's captures): the small one fights first (bite, spit 28 at the base scale)
 * while the large one waits submerged, firing magic every 6 ticks. When the small one dies the
 * large one crawls out and fights (bite, spit, magic; 72 melee at the base scale). Below half
 * health either eats from the meat tree (3 bites of 15% of its hitpoints) up to three times, unless
 * the raiders have cut the tree down. Each drops 1-3 stinkhorn mushrooms.
 */
class CoxMuttadiles(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private lateinit var small: Npc
    private lateinit var large: Npc
    private lateinit var tree: Npc
    private var treeHp = TREE_HP
    private val meals = HashMap<Npc, Int>()
    private var eating: Npc? = null
    private var eatBites = 0
    private var largeOut = false
    private var nextMagic = 0
    private val tendrils = ArrayList<LocInfo>()

    override fun build() {
        tree = spawn(cox_npcs.meat_tree, pick3(TREE))
        small = spawn(cox_npcs.mutt_small, pick3(SMALL), CoxScaling.Bases.muttadile_small)
        large = spawn(cox_npcs.mutt_submerged, pick3(LARGE), CoxScaling.Bases.muttadile_large)
        blockExit(listOf(pick3(CRYSTAL)))
    }

    override fun onEntered(player: Player) {
        if (raid.started && !started) start()
    }

    override fun onStart() {
        for (t in pick3(TENDRILS)) tendrils += placeLoc(t, cox_locs.mutt_tendrils)
        val bite =
            NpcAttack(
                NpcAttackStyle.Melee,
                anim = cox_seqs.mutt_bite,
                maxHit = scaledMaxHit(CoxScaling.Bases.muttadile_small, 28),
            )
        monster(small, bite, range = 1, speed = 4)
        nextMagic = now + MAGIC_TICKS
    }

    override fun onTick() {
        if (eating != null) {
            eat()
            return
        }
        driveMonsters()
        if (!largeOut && large.isSlotAssigned && now >= nextMagic) {
            nextMagic = now + MAGIC_TICKS
            fightPlayers().randomOrNull()?.let { target ->
                strike(
                    large,
                    target,
                    NpcAttack(
                        NpcAttackStyle.Magic,
                        projectile = cox_spots.dragon_fire,
                        projanim = ToaProjAnims.orb,
                        maxHit = scaledMaxHit(CoxScaling.Bases.muttadile_large, 25),
                    ),
                    playAnim = false,
                )
            }
        }
        for (npc in listOf(small, large)) {
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) continue
            if (npc === large && !largeOut) continue
            val eaten = meals[npc] ?: 0
            if (treeHp > 0 && eaten < MEALS && npc.hitpoints * 2 < npc.baseHitpointsLvl) {
                eating = npc
                eatBites = 0
                meals[npc] = eaten + 1
                monsters[npc]?.scripted = true
                return
            }
        }
    }

    private fun eat() {
        val npc = eating ?: return
        if (!npc.isSlotAssigned || treeHp <= 0) {
            monsters[npc]?.scripted = false
            eating = null
            return
        }
        npc.anim(cox_seqs.mutt_bite)
        heal(npc, npc.baseHitpointsLvl * 15 / 100)
        if (++eatBites >= BITES) {
            monsters[npc]?.scripted = false
            eating = null
        }
    }

    fun chop(player: Player) {
        if (treeHp <= 0) return
        if (!chance(3)) {
            player.mes("You fail to cut the meat tree.")
            return
        }
        treeHp--
        player.mes("You hack away some of the meat.")
        env.raids.addPoints(raid, player, TREE_POINTS)
        if (treeHp <= 0) {
            despawn(tree)
            for (p in roomPlayers()) p.mes("The meat tree has been cut down.")
        }
    }

    override fun monsterDied(npc: Npc) {
        if (npc === small) emerge()
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc === large && largeOut && !completed) {
            despawn(large)
            for (p in roomPlayers()) drop(cox_objs.stinkhorn, p.coords, roll(1, 3))
            tendrils.forEach(::removeLoc)
            complete()
        }
    }

    private fun emerge() {
        for (p in roomPlayers()) drop(cox_objs.stinkhorn, p.coords, roll(1, 3))
        large.anim(cox_seqs.mutt_emerge)
        later(EMERGE_TICKS) {
            largeOut = true
            transform(large, cox_npcs.mutt_large)
            val attack =
                NpcAttack(
                    NpcAttackStyle.Melee,
                    anim = cox_seqs.mutt_bite,
                    maxHit = scaledMaxHit(CoxScaling.Bases.muttadile_large, 72),
                )
            monster(large, attack, range = 1, speed = 4)
        }
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc === large && !largeOut) hit.damage = 0
        if (npc === tree) hit.damage = 0
    }

    companion object {
        const val TREE_HP = 10
        const val TREE_POINTS = 50
        const val MEALS = 3
        const val BITES = 3
        const val MAGIC_TICKS = 6
        const val EMERGE_TICKS = 5
        val TREE =
            listOf(CoordGrid(3269, 5320, 0), CoordGrid(3301, 5320, 0), CoordGrid(3350, 5317, 0))
        val TENDRILS =
            listOf(
                listOf(CoordGrid(3279, 5314, 0), CoordGrid(3280, 5314, 0)),
                listOf(CoordGrid(3311, 5314, 0), CoordGrid(3312, 5314, 0)),
                listOf(CoordGrid(3343, 5313, 0), CoordGrid(3344, 5313, 0)),
            )
        val CRYSTAL =
            listOf(CoordGrid(3270, 5328, 0), CoordGrid(3308, 5335, 0), CoordGrid(3352, 5327, 0))
        val LARGE =
            listOf(CoordGrid(3282, 5332, 0), CoordGrid(3314, 5330, 0), CoordGrid(3336, 5330, 0))
        val SMALL =
            listOf(CoordGrid(3276, 5328, 0), CoordGrid(3307, 5325, 0), CoordGrid(3344, 5323, 0))
    }
}

// Vanguards
// -------------------------------------------------------------------------------------------------

/**
 * Vanguards (Wiki, TSPS's captures): three, one for each style, rising 5 ticks after the party
 * walks in; each attacks every 4 ticks (18 melee, 22 ranged, 22 magic at the base scale). If one
 * falls 40% below another (33.3% once one is dead) all still standing go back into their shells and
 * heal to full. Every 20-36 ticks they shuffle places. All three must die.
 */
class CoxVanguards(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private val guards = ArrayList<Npc>()
    private var nextShuffle = 0
    private var dead = 0
    private val forms =
        listOf(cox_npcs.vanguard_melee, cox_npcs.vanguard_ranged, cox_npcs.vanguard_magic)

    override fun build() {
        for (t in pick3(SPOTS)) guards +=
            spawn(cox_npcs.vanguard_dormant, t, CoxScaling.Bases.vanguard)
        blockExit(listOf(pick3(CRYSTAL)))
    }

    override fun onEntered(player: Player) {
        if (raid.started && !started) start()
    }

    override fun onStart() {
        guards.forEach { it.anim(cox_seqs.vanguard_spawn) }
        later(RISE_TICKS) {
            guards.forEachIndexed { i, npc ->
                transform(npc, forms[i])
                val attack =
                    when (i) {
                        0 ->
                            NpcAttack(
                                NpcAttackStyle.Melee,
                                anim = cox_seqs.vanguard_melee,
                                maxHit = scaledMaxHit(CoxScaling.Bases.vanguard, 18),
                            )
                        1 ->
                            NpcAttack(
                                NpcAttackStyle.Ranged,
                                anim = cox_seqs.vanguard_ranged,
                                projectile = cox_spots.vanguard_range,
                                projanim = ToaProjAnims.lob,
                                impactSpot = cox_spots.rock_splash,
                                maxHit = scaledMaxHit(CoxScaling.Bases.vanguard, 22),
                            )
                        else ->
                            NpcAttack(
                                NpcAttackStyle.Magic,
                                anim = cox_seqs.vanguard_magic,
                                projectile = cox_spots.vanguard_magic,
                                projanim = ToaProjAnims.lob,
                                impactSpot = cox_spots.meteor,
                                maxHit = scaledMaxHit(CoxScaling.Bases.vanguard, 22),
                            )
                    }
                monster(npc, attack, range = if (i == 0) 1 else 10, speed = 4).stationary = i != 0
            }
            nextShuffle = now + roll(20, 36)
        }
    }

    override fun onTick() {
        driveMonsters()
        val alive = guards.filter { it.isSlotAssigned && it.hitpoints > 0 }
        if (alive.size >= 2) {
            val shares = alive.map { it.hitpoints * 100 / it.baseHitpointsLvl.coerceAtLeast(1) }
            val limit = if (dead == 0) 40 else 33
            if (shares.max() - shares.min() >= limit) {
                for (npc in alive) {
                    npc.anim(cox_seqs.vanguard_heal)
                    heal(npc, npc.baseHitpointsLvl)
                }
                for (p in roomPlayers()) p.mes(
                    "The vanguards retreat into their shells to recover."
                )
            }
        }
        if (nextShuffle in 1..now && alive.size >= 2) {
            nextShuffle = now + roll(20, 36)
            val spots = shuffled(alive.map { it.coords })
            alive.forEachIndexed { i, npc ->
                org.rsmod.game.entity.util.PathingEntityCommon.telejump(
                    npc,
                    env.collision,
                    spots[i],
                )
                graphicAt(cox_spots.swirl_high, spots[i])
            }
        }
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc !in guards) return
        guards -= npc
        dead++
        despawn(npc)
        if (guards.isEmpty()) complete()
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc in guards && npc.visType.id == env.npcTypes[cox_npcs.vanguard_dormant].id)
            hit.damage = 0
    }

    companion object {
        const val RISE_TICKS = 5
        val SPOTS =
            listOf(
                listOf(
                    CoordGrid(3285, 5329, 0),
                    CoordGrid(3277, 5334, 0),
                    CoordGrid(3276, 5324, 0),
                ),
                listOf(
                    CoordGrid(3317, 5330, 0),
                    CoordGrid(3309, 5334, 0),
                    CoordGrid(3309, 5324, 0),
                ),
                listOf(CoordGrid(3347, 5325, 0), CoordGrid(3341, 5334, 0), CoordGrid(3337, 5325, 0)),
            )
        val CRYSTAL =
            listOf(CoordGrid(3268, 5327, 0), CoordGrid(3311, 5339, 0), CoordGrid(3350, 5328, 0))
    }
}

// Vasa Nistirio
// -------------------------------------------------------------------------------------------------

/**
 * Vasa Nistirio (Wiki, TSPS's captures): woken, he lights a crystal and walks to it, throwing a
 * boulder at each raider every 3 ticks (ranged, 18 at the base scale). At the crystal he siphons it
 * for 66-67 ticks, healing 1% of his hitpoints every 2 ticks and taking no damage, unless the
 * raiders smash the glowing crystal first. Between crystals a special: everyone beside him is
 * stunned and "Vasa Nistirio has blocked your use of prayers." for 8 ticks, then an explosion round
 * him. Drops 5 endarkened juice.
 */
class CoxVasa(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private lateinit var vasa: Npc
    private var crystal: Npc? = null
    private var siphonUntil = -1
    private var nextBoulder = 0
    private var nextSpecial = 0
    private var crystalIndex = 0
    private var fire: LocInfo? = null

    override fun build() {
        vasa = spawn(cox_npcs.vasa_dormant, pick3(CENTRE), CoxScaling.Bases.vasa)
        blockExit(pick3(EXITS), cox_locs.crystal_purple_small)
    }

    override fun onEntered(player: Player) {
        if (raid.started && !started) start()
    }

    override fun onStart() {
        fire = placeLoc(pick3(FIRE), cox_locs.vasa_fire)
        vasa.anim(cox_seqs.vasa_spawn)
        transform(vasa, cox_npcs.vasa_walking)
        nextBoulder = now + 4
        nextSpecial = now + 30
        crystalIndex = roll(0, 3)
        later(6) { siphon() }
    }

    private fun siphon() {
        if (completed || !vasa.isSlotAssigned) return
        val at = pick3(CRYSTALS)[crystalIndex % 4]
        crystalIndex++
        crystal = spawn(cox_npcs.vasa_crystal, at, CoxScaling.Bases.glowing_crystal, crystal = true)
        transform(vasa, cox_npcs.vasa_healing)
        vasa.anim(cox_seqs.vasa_heal_spawn)
        siphonUntil = now + roll(66, 67)
    }

    override fun onTick() {
        if (!vasa.isSlotAssigned) return
        if (siphonUntil > 0) {
            if ((siphonUntil - now) % 2 == 0)
                heal(vasa, (vasa.baseHitpointsLvl / 100).coerceAtLeast(1))
            if (now >= siphonUntil) endSiphon()
            return
        }
        if (now >= nextBoulder) {
            nextBoulder = now + BOULDER_TICKS
            for (target in fightPlayers()) {
                strike(
                    vasa,
                    target,
                    NpcAttack(
                        NpcAttackStyle.Ranged,
                        projectile = cox_spots.vasa_range_travel,
                        projanim = ToaProjAnims.lob,
                        impactSpot = cox_spots.vasa_range_impact,
                        maxHit = scaledMaxHit(CoxScaling.Bases.vasa, 18),
                    ),
                    playAnim = false,
                )
            }
        }
        if (now >= nextSpecial) {
            nextSpecial = now + SPECIAL_TICKS
            special()
        }
    }

    private fun special() {
        vasa.anim(cox_seqs.vasa_stun_spawn)
        for (player in fightPlayers()) {
            if (gap(vasa, player.coords) <= 2) {
                graphicAt(cox_spots.stunned, player.coords)
                player.delay = now + 3
            }
            player.mes("Vasa Nistirio has blocked your use of prayers.")
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(
                player,
                varbits.protect_from_melee,
                0,
            )
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(
                player,
                varbits.protect_from_missiles,
                0,
            )
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(
                player,
                varbits.protect_from_magic,
                0,
            )
        }
        later(8) {
            if (completed) return@later
            vasa.anim(cox_seqs.vasa_attack)
            for (player in fightPlayers()) {
                if (gap(vasa, player.coords) <= 3) hurt(player, roll(20, 40))
            }
        }
        later(10) { siphon() }
    }

    private fun endSiphon() {
        siphonUntil = -1
        crystal?.let { despawn(it) }
        crystal = null
        transform(vasa, cox_npcs.vasa_walking)
        vasa.anim(cox_seqs.vasa_healed)
        nextSpecial = now + roll(6, 10)
    }

    override fun monsterDied(npc: Npc) {
        if (npc === crystal) endSiphon()
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc === vasa && (!started || siphonUntil > 0)) hit.damage = 0
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc !== vasa || completed) return
        vasa.anim(cox_seqs.vasa_death)
        for (p in roomPlayers()) drop(cox_objs.endarkened_juice, p.coords, 5)
        crystal?.let { despawn(it) }
        later(3) { despawn(vasa) }
        fire?.let { removeLoc(it) }
        complete()
    }

    companion object {
        const val BOULDER_TICKS = 3
        const val SPECIAL_TICKS = 40
        val CRYSTALS =
            listOf(
                listOf(
                    CoordGrid(3270, 5285, 0),
                    CoordGrid(3270, 5303, 0),
                    CoordGrid(3287, 5303, 0),
                    CoordGrid(3287, 5285, 0),
                ),
                listOf(
                    CoordGrid(3301, 5285, 0),
                    CoordGrid(3301, 5302, 0),
                    CoordGrid(3319, 5302, 0),
                    CoordGrid(3319, 5285, 0),
                ),
                listOf(
                    CoordGrid(3333, 5285, 0),
                    CoordGrid(3333, 5303, 0),
                    CoordGrid(3350, 5303, 0),
                    CoordGrid(3350, 5285, 0),
                ),
            )
        val EXITS =
            listOf(
                listOf(CoordGrid(3268, 5295, 0), CoordGrid(3268, 5296, 0)),
                listOf(CoordGrid(3311, 5307, 0), CoordGrid(3312, 5307, 0)),
                listOf(CoordGrid(3355, 5296, 0), CoordGrid(3355, 5295, 0)),
            )
        val FIRE =
            listOf(CoordGrid(3279, 5281, 0), CoordGrid(3311, 5281, 0), CoordGrid(3343, 5281, 0))
        val CENTRE =
            listOf(CoordGrid(3278, 5293, 0), CoordGrid(3309, 5293, 0), CoordGrid(3341, 5294, 0))
    }
}

// Vespula
// -------------------------------------------------------------------------------------------------

/**
 * Vespula (Wiki, TSPS's captures): she flies over the room (only magic, ranged and halberds reach
 * her), stinging the lux grubs; a drained grub dies and a vespine soldier hatches (healthy again 37
 * ticks later). Medivaemia blossoms (the roots in the room) heal a grub. The abyssal portal is what
 * must die: when it falls everything dies with it ("As the link to the Abyss disintegrates, the
 * abyssal creatures perish."). Hitting the portal enrages her; she stings raiders (14 at the base
 * scale) and lands below 20% health for 33 ticks. The portal fires at raiders every 2 ticks.
 */
class CoxVespula(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private lateinit var vespula: Npc
    private lateinit var portal: Npc
    private val grubs = LinkedHashMap<Npc, Int>()
    private var nextSting = 0
    private var nextPortal = 0
    private var landedUntil = -1
    private var enraged = false
    private val tendrils = ArrayList<LocInfo>()

    override fun build() {
        portal = spawn(cox_npcs.vespula_portal, pick3(PORTAL), CoxScaling.Bases.abyssal_portal)
        vespula = spawn(cox_npcs.vespula_flying, pick3(VESPULA), CoxScaling.Bases.vespula)
        for (t in pick3(GRUBS)) grubs[spawn(cox_npcs.grub_healthy, t, hitpoints = GRUB_HP)] =
            GRUB_HP
        blockExit(pick3(BOILS), cox_locs.vespula_boil)
    }

    override fun onEntered(player: Player) {
        if (raid.started && !started) start()
    }

    override fun onStart() {
        for (t in pick3(TENDRILS)) tendrils += placeLoc(t, cox_locs.vespula_tendrils)
        nextSting = now + 4
        nextPortal = now + 2
    }

    override fun onTick() {
        driveMonsters()
        if (now >= nextPortal) {
            nextPortal = now + PORTAL_TICKS
            fightPlayers().randomOrNull()?.let { target ->
                strike(
                    portal,
                    target,
                    NpcAttack(
                        NpcAttackStyle.Magic,
                        projectile = cox_spots.portal_attack,
                        projanim = ToaProjAnims.orb,
                        maxHit = 8,
                        alwaysHits = false,
                    ),
                    playAnim = false,
                )
            }
        }
        if (landedUntil in 1..now) {
            landedUntil = -1
            transform(vespula, if (enraged) cox_npcs.vespula_enraged else cox_npcs.vespula_flying)
        }
        if (now < nextSting || !vespula.isSlotAssigned) return
        nextSting = now + if (enraged) 3 else 5
        val target = if (enraged || chance(2)) fightPlayers().randomOrNull() else null
        if (target != null) {
            vespula.facePlayer(target)
            strike(
                vespula,
                target,
                NpcAttack(
                    NpcAttackStyle.Melee,
                    anim = cox_seqs.vespula_sting,
                    maxHit = scaledMaxHit(CoxScaling.Bases.vespula, 14),
                ),
            )
            return
        }
        val grub =
            grubs.entries.filter { it.key.isSlotAssigned && it.value > 0 }.randomOrNull() ?: return
        vespula.faceNpc(grub.key)
        vespula.anim(cox_seqs.vespula_sting)
        val left = grub.value - DRAIN
        grubs[grub.key] = left
        transform(
            grub.key,
            when {
                left <= 0 -> cox_npcs.grub_dead
                left * 3 < GRUB_HP -> cox_npcs.grub_infected
                left * 3 < GRUB_HP * 2 -> cox_npcs.grub_sickly
                else -> cox_npcs.grub_healthy
            },
        )
        if (left <= 0) hatch(grub.key)
    }

    private fun hatch(grub: Npc) {
        graphicAt(cox_spots.vespine_hatch, grub.coords)
        val soldier =
            spawnAt(cox_npcs.soldier_walking, grub.coords, CoxScaling.Bases.vespine_soldier)
        monster(
            soldier,
            NpcAttack(
                NpcAttackStyle.Melee,
                maxHit = scaledMaxHit(CoxScaling.Bases.vespine_soldier, 14),
            ),
            range = 1,
            speed = 4,
        )
        later(REGROW_TICKS) {
            if (completed || !grub.isSlotAssigned) return@later
            grubs[grub] = GRUB_HP
            transform(grub, cox_npcs.grub_healthy)
        }
    }

    fun feed(player: Player, grub: Npc) {
        val hp = grubs[grub] ?: return
        if (hp <= 0) {
            player.mes("This grub is beyond help.")
            return
        }
        grubs[grub] = GRUB_HP
        transform(grub, cox_npcs.grub_healthy)
        player.mes("You feed the grub the blossom.")
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc === portal && !enraged) {
            enraged = true
            transform(vespula, cox_npcs.vespula_enraged)
            for (t in tendrils) Unit
        }
        if (npc === vespula && landedUntil < 0 && hit.type == org.rsmod.game.hit.HitType.Melee) {
            if (source == null || !CoxCombat.wieldsHalberd(source)) hit.damage = 0
        }
        if (npc in grubs) hit.damage = 0
    }

    override fun hitpointsGone(npc: Npc) {
        when {
            npc === portal && !completed -> {
                for (p in roomPlayers()) p.mes(
                    "As the link to the Abyss disintegrates, the abyssal creatures perish."
                )
                for (other in npcs.toList()) despawn(other)
                tendrils.forEach(::removeLoc)
                complete()
            }
            npc === vespula -> {
                // She can't die before the portal; she lands to recover instead.
                heal(vespula, vespula.baseHitpointsLvl / 5)
                landedUntil = now + LANDED_TICKS
                transform(vespula, cox_npcs.vespula_walking)
            }
        }
    }

    override fun onIdle() {}

    companion object {
        const val GRUB_HP = 100
        const val DRAIN = 25
        const val PORTAL_TICKS = 2
        const val LANDED_TICKS = 33
        const val REGROW_TICKS = 37
        /** The grubs' south-west tiles (TSPS gives their middles; they are 3 tiles across). */
        val GRUBS =
            listOf(
                listOf(
                    CoordGrid(3286, 5288, 0),
                    CoordGrid(3284, 5293, 0),
                    CoordGrid(3276, 5285, 0),
                    CoordGrid(3277, 5293, 0),
                ),
                listOf(
                    CoordGrid(3311, 5288, 0),
                    CoordGrid(3303, 5290, 0),
                    CoordGrid(3311, 5296, 0),
                    CoordGrid(3303, 5296, 0),
                ),
                listOf(
                    CoordGrid(3343, 5293, 0),
                    CoordGrid(3336, 5285, 0),
                    CoordGrid(3336, 5293, 0),
                    CoordGrid(3333, 5289, 0),
                ),
            )
        val BOILS =
            listOf(
                listOf(CoordGrid(3267, 5295, 0), CoordGrid(3267, 5296, 0)),
                listOf(CoordGrid(3311, 5308, 0), CoordGrid(3312, 5308, 0)),
                listOf(CoordGrid(3356, 5296, 0), CoordGrid(3356, 5295, 0)),
            )
        val TENDRILS =
            listOf(
                listOf(CoordGrid(3279, 5280, 0), CoordGrid(3280, 5280, 0)),
                listOf(CoordGrid(3311, 5280, 0), CoordGrid(3312, 5280, 0)),
                listOf(CoordGrid(3343, 5280, 0), CoordGrid(3344, 5280, 0)),
            )
        val PORTAL =
            listOf(CoordGrid(3281, 5300, 0), CoordGrid(3318, 5291, 0), CoordGrid(3339, 5300, 0))
        val VESPULA =
            listOf(CoordGrid(3279, 5288, 0), CoordGrid(3306, 5290, 0), CoordGrid(3339, 5288, 0))
    }
}

// Guardians
// -------------------------------------------------------------------------------------------------

/**
 * The Guardians (Wiki, TSPS's captures): two stand in the gap; only pickaxes hurt them, scaled by
 * the Mining level; base hitpoints 151 + the party's average Mining. They swing (20 at the base
 * scale) and stomp (15, a rock falling on the target's tile). Both down: they crumble and the gap
 * opens.
 */
class CoxGuardians(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private val guardians = ArrayList<Npc>()
    private val blockers = ArrayList<LocInfo>()

    override fun build() {
        val tiles = pick3(GUARDIANS)
        val hp =
            CoxScaling.scale(
                    CoxScaling.Bases.guardian.copy(hitpoints = 151 + scaling.averageMining),
                    scaling,
                )
                .hitpoints
        guardians +=
            spawn(cox_npcs.guardian_left, tiles[0], CoxScaling.Bases.guardian, hitpoints = hp)
        guardians +=
            spawn(cox_npcs.guardian_right, tiles[1], CoxScaling.Bases.guardian, hitpoints = hp)
        for (t in pick3(BLOCKED)) blockers += placeLoc(t, cox_locs.invisible_blocker)
    }

    override fun onEntered(player: Player) {
        if (raid.started && !started) start()
    }

    override fun onStart() {
        // Hitpoints are set once the raid's scaling (taken at the start) is known.
        val hp =
            CoxScaling.scale(
                    CoxScaling.Bases.guardian.copy(hitpoints = 151 + scaling.averageMining),
                    scaling,
                )
                .hitpoints
        for (g in guardians) {
            CoxScaling.apply(g, CoxScaling.scale(CoxScaling.Bases.guardian, scaling))
            g.baseHitpointsLvl = hp
            g.hitpoints = hp
            monster(
                    g,
                    NpcAttack(
                        NpcAttackStyle.Melee,
                        anim = cox_seqs.guardian_attack,
                        maxHit = scaledMaxHit(CoxScaling.Bases.guardian, 20),
                    ),
                    range = 1,
                    speed = 5,
                )
                .stationary = true
        }
    }

    override fun swing(monster: CoxMonster, target: Player) {
        if (chance(4)) {
            monster.npc.anim(cox_seqs.guardian_stomp)
            val at = target.coords
            val land =
                projectileBy(
                    monster.npc.coords,
                    at,
                    cox_spots.rockfall,
                    delay = 0,
                    base = 30,
                    perTile = 0,
                    type = ToaProjAnims.lob,
                )
            later(land) {
                graphicAt(cox_spots.rock_splash, at)
                if (target.coords == at)
                    hurt(target, roll(0, scaledMaxHit(CoxScaling.Bases.guardian, 15)))
            }
            return
        }
        super.swing(monster, target)
    }

    override fun onTick() {
        driveMonsters()
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc !in guardians) return
        if (source == null || !CoxCombat.wieldsPickaxe(source)) {
            hit.damage = 0
            return
        }
        // Scaled by Mining (the Wiki's "Mining multiplier"): full at 99, a little over half at 1.
        val mining = source.stat(stats.mining).coerceIn(1, 99)
        hit.damage = hit.damage * (50 + mining / 2) / 100
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc !in guardians) return
        guardians -= npc
        npc.anim(cox_seqs.guardian_crumble)
        transform(
            npc,
            if (npc.type.id == env.npcTypes[cox_npcs.guardian_left].id) cox_npcs.guardian_left_dead
            else cox_npcs.guardian_right_dead,
        )
        monsters.remove(npc)
        if (guardians.isEmpty()) {
            later(3) {
                blockers.forEach(::removeLoc)
                complete()
            }
        }
    }

    companion object {
        val GUARDIANS =
            listOf(
                listOf(CoordGrid(3276, 5265, 0), CoordGrid(3276, 5269, 0)),
                listOf(CoordGrid(3312, 5271, 0), CoordGrid(3308, 5271, 0)),
                listOf(CoordGrid(3344, 5268, 0), CoordGrid(3344, 5272, 0)),
            )
        val BLOCKED =
            listOf(
                listOf(CoordGrid(3275, 5267, 0), CoordGrid(3275, 5268, 0)),
                listOf(CoordGrid(3310, 5273, 0), CoordGrid(3311, 5273, 0)),
                listOf(CoordGrid(3346, 5271, 0), CoordGrid(3346, 5270, 0)),
            )
    }
}

// Lizardman shamans
// -------------------------------------------------------------------------------------------------

/**
 * Lizardman shamans (Wiki, TSPS's captures): four, aggressive; they spit (ranged 24 at the base
 * scale), swipe (melee 24), spit acid that poisons, jump onto a raider's tile and summon spawn that
 * explode. All dead: the blockers by the way on burst.
 */
class CoxShamans(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private val shamans = ArrayList<Npc>()
    private val blockers = ArrayList<Npc>()
    private val tendrils = ArrayList<LocInfo>()

    override fun build() {
        for (t in pick3(SHAMANS)) {
            val npc =
                spawn(
                    if (chance(2)) cox_npcs.shaman_a else cox_npcs.shaman_b,
                    t,
                    CoxScaling.Bases.shaman,
                )
            shamans += npc
        }
        for (pair in pick3(BLOCKERS)) for (t in pair) blockers += spawn(cox_npcs.shaman_spawn, t)
    }

    override fun onEntered(player: Player) {
        if (raid.started && !started) start()
    }

    override fun onStart() {
        for (t in pick3(TENDRILS)) tendrils += placeLoc(t, cox_locs.shaman_tendrils)
        for (npc in shamans) {
            CoxScaling.apply(npc, CoxScaling.scale(CoxScaling.Bases.shaman, scaling))
            monster(
                npc,
                NpcAttack(
                    NpcAttackStyle.Ranged,
                    anim = cox_seqs.shaman_ranged,
                    projectile = cox_spots.spit,
                    projanim = ToaProjAnims.lob,
                    maxHit = scaledMaxHit(CoxScaling.Bases.shaman, 24),
                ),
                range = 8,
                speed = 4,
            )
        }
    }

    override fun swing(monster: CoxMonster, target: Player) {
        val npc = monster.npc
        when (roll(1, 8)) {
            1 -> {
                npc.anim(cox_seqs.shaman_ranged)
                val at = target.coords
                val land =
                    projectileBy(
                        npc.coords,
                        at,
                        cox_spots.acid,
                        delay = 30,
                        base = 120,
                        perTile = 0,
                        type = ToaProjAnims.lob,
                    )
                later(land) {
                    graphicAt(cox_spots.acid_splash, at)
                    if (target.coords == at) {
                        hurtPoison(target, roll(10, 20))
                        env.poison.poison(target, POISON)
                    }
                }
            }
            2 -> {
                npc.anim(cox_seqs.shaman_jump)
                val at = target.coords
                later(JUMP_TICKS) {
                    if (!npc.isSlotAssigned) return@later
                    org.rsmod.game.entity.util.PathingEntityCommon.telejump(
                        npc,
                        env.collision,
                        open(at),
                    )
                    npc.anim(cox_seqs.shaman_land)
                    for (p in fightPlayers()) if (gap(npc, p.coords) <= 1) hurt(p, roll(15, 25))
                }
            }
            3 -> {
                repeat(roll(1, 3)) {
                    val at = open(target.coords.translate(roll(-2, 2), roll(-2, 2)))
                    val spawn = spawnAt(cox_npcs.shaman_spawn, at)
                    later(SPAWN_TICKS) {
                        if (!spawn.isSlotAssigned) return@later
                        graphicAt(cox_spots.spawn_explode, spawn.coords)
                        for (p in fightPlayers()) if (gap(spawn, p.coords) <= 1)
                            hurt(p, roll(5, 15))
                        despawn(spawn)
                    }
                }
            }
            else ->
                if (gap(npc, target.coords) <= 1) {
                    strike(
                        npc,
                        target,
                        NpcAttack(
                            NpcAttackStyle.Melee,
                            anim = cox_seqs.shaman_melee,
                            maxHit = scaledMaxHit(CoxScaling.Bases.shaman, 24),
                        ),
                    )
                } else {
                    super.swing(monster, target)
                }
        }
    }

    override fun onTick() {
        driveMonsters()
    }

    override fun monsterDied(npc: Npc) {
        if (npc !in shamans) return
        shamans -= npc
        if (shamans.isEmpty()) {
            later(5) { blockers.forEach { it.anim(cox_seqs.shaman_spawn_explode) } }
            later(8) {
                blockers.forEach {
                    graphicAt(cox_spots.spawn_explode, it.coords)
                    despawn(it)
                }
                tendrils.forEach(::removeLoc)
                complete()
            }
        }
    }

    override fun pointsPerHitpoint(npc: Npc): Int = if (npc in blockers) 0 else POINTS_PER_HP

    companion object {
        const val JUMP_TICKS = 3
        const val SPAWN_TICKS = 6
        const val POISON = 6
        val SHAMANS =
            listOf(
                listOf(
                    CoordGrid(3273, 5270, 0),
                    CoordGrid(3281, 5269, 0),
                    CoordGrid(3278, 5262, 0),
                    CoordGrid(3273, 5255, 0),
                ),
                listOf(
                    CoordGrid(3306, 5260, 0),
                    CoordGrid(3306, 5267, 0),
                    CoordGrid(3315, 5267, 0),
                    CoordGrid(3316, 5259, 0),
                ),
                listOf(
                    CoordGrid(3340, 5258, 0),
                    CoordGrid(3346, 5259, 0),
                    CoordGrid(3341, 5268, 0),
                    CoordGrid(3347, 5270, 0),
                ),
            )
        val TENDRILS =
            listOf(
                listOf(CoordGrid(3279, 5249, 0), CoordGrid(3280, 5249, 0)),
                listOf(CoordGrid(3311, 5251, 0), CoordGrid(3310, 5251, 0)),
                listOf(CoordGrid(3342, 5251, 0), CoordGrid(3343, 5251, 0)),
            )
        val BLOCKERS =
            listOf(
                    listOf(
                        CoordGrid(3268, 5263, 0),
                        CoordGrid(3269, 5263, 0),
                        CoordGrid(3268, 5264, 0),
                        CoordGrid(3269, 5264, 0),
                    ),
                    listOf(
                        CoordGrid(3311, 5275, 0),
                        CoordGrid(3311, 5274, 0),
                        CoordGrid(3312, 5275, 0),
                        CoordGrid(3312, 5274, 0),
                    ),
                    listOf(
                        CoordGrid(3353, 5265, 0),
                        CoordGrid(3352, 5265, 0),
                        CoordGrid(3353, 5264, 0),
                        CoordGrid(3352, 5264, 0),
                    ),
                )
                .map { listOf(it) }
    }
}

// Skeletal mystics
// -------------------------------------------------------------------------------------------------

/**
 * Skeletal mystics (Wiki, TSPS's captures): three to six (by the party), casting fire (25 at the
 * base scale) or Vulnerability at range and switching to melee (25) when a raider prays against
 * magic up close. All dead: the way on opens.
 */
class CoxMystics(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private val mystics = ArrayList<Npc>()

    override fun onEntered(player: Player) {
        if (!raid.started || started) return
        val count = (3 + raid.scaling.size / 2).coerceAtMost(6)
        val types = listOf(cox_npcs.mystic_a, cox_npcs.mystic_b, cox_npcs.mystic_c)
        for (t in pick3(SPAWNS).take(count)) {
            val npc = spawn(pick(types), t, CoxScaling.Bases.mystic)
            mystics += npc
            monster(
                npc,
                NpcAttack(
                    NpcAttackStyle.Magic,
                    anim = cox_seqs.mystic_cast,
                    castSpot = cox_spots.mystic_red,
                    projectile = cox_spots.fire_travel,
                    projanim = ToaProjAnims.orb,
                    impactSpot = cox_spots.fire_impact,
                    maxHit = scaledMaxHit(CoxScaling.Bases.mystic, 25),
                ),
                range = 8,
                speed = 4,
            )
        }
        start()
    }

    override fun swing(monster: CoxMonster, target: Player) {
        val npc = monster.npc
        if (gap(npc, target.coords) <= 1 && prays(target, NpcAttackStyle.Magic)) {
            strike(
                npc,
                target,
                NpcAttack(
                    NpcAttackStyle.Melee,
                    anim = cox_seqs.mystic_melee,
                    maxHit = scaledMaxHit(CoxScaling.Bases.mystic, 25),
                ),
            )
            return
        }
        super.swing(monster, target)
    }

    override fun onTick() {
        driveMonsters()
    }

    override fun monsterDied(npc: Npc) {
        mystics -= npc
        if (mystics.isEmpty()) complete()
    }

    companion object {
        val SPAWNS =
            listOf(
                listOf(
                    CoordGrid(3278, 5261, 0),
                    CoordGrid(3286, 5265, 0),
                    CoordGrid(3284, 5271, 0),
                    CoordGrid(3277, 5273, 0),
                    CoordGrid(3273, 5265, 0),
                    CoordGrid(3278, 5270, 0),
                ),
                listOf(
                    CoordGrid(3315, 5262, 0),
                    CoordGrid(3318, 5259, 0),
                    CoordGrid(3309, 5258, 0),
                    CoordGrid(3301, 5256, 0),
                    CoordGrid(3306, 5264, 0),
                    CoordGrid(3312, 5259, 0),
                ),
                listOf(
                    CoordGrid(3342, 5262, 0),
                    CoordGrid(3342, 5266, 0),
                    CoordGrid(3340, 5268, 0),
                    CoordGrid(3338, 5274, 0),
                    CoordGrid(3337, 5268, 0),
                    CoordGrid(3338, 5260, 0),
                ),
            )
    }
}
