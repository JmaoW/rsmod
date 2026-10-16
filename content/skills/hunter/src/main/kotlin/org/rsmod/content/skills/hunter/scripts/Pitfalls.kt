package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.hat
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.legs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.torso
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.configs.Creature
import org.rsmod.content.skills.hunter.configs.HunterCreatures
import org.rsmod.content.skills.hunter.configs.HunterLoot
import org.rsmod.content.skills.hunter.configs.HunterPitBits
import org.rsmod.content.skills.hunter.configs.hunter_locs
import org.rsmod.content.skills.hunter.configs.hunter_objs
import org.rsmod.content.skills.hunter.configs.hunter_seqs
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

// The pits are the one trap that looks different to each hunter: a pit is a loc whose picture comes
// from one of the player's varbits (0 empty, 1 set, 2 falling in, 3 holding a catch), so setting a
// pit sets that varbit and nothing is placed in the world. Which creature a pit takes depends on
// which of the 25 pits it is: 1 to 6 sabre-toothed kyatt, 7 to 11 spined larupia, 12 to 16 horned
// graahk, 17 to 21 sunlight antelope and 22 to 25 moonlight antelope.
//
// To catch something the hunter teases the creature with a teasing stick so that it follows them,
// leads it to a pit, and jumps across: the creature falls in or leaps over, on its catch chance.
class Pitfalls
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val locTypes: LocTypeList,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val mapClock: MapClock,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val eventBus: EventBus,
) : PluginScript() {
    private enum class Phase {
        Set,
        Falling,
        Full,
    }

    private class Pit(
        val number: Int,
        val coords: CoordGrid,
        val creature: Creature,
        val bit: VarBitType,
    ) {
        var phase = Phase.Set
        var expires = 0
        var settleAt = 0
    }

    private class Teased(val npc: Npc, val creature: Creature) {
        var until = 0
        var nextAttack = 0
        var lastPit: CoordGrid? = null
    }

    private val pits = WeakHashMap<Player, MutableMap<CoordGrid, Pit>>()

    private val teased = WeakHashMap<Player, Teased>()

    override fun ScriptContext.startup() {
        onOpLoc3(hunter_locs.pit_empty) { layPit(it.loc) }
        onOpLoc1(hunter_locs.pit_set) { jump(it.loc) }
        onOpLoc2(hunter_locs.pit_set) { dismantle(it.loc) }
        for (full in
            listOf(
                hunter_locs.pit_full_kyatt,
                hunter_locs.pit_full_larupia,
                hunter_locs.pit_full_graahk,
                hunter_locs.pit_full_any,
            )) {
            onOpLoc2(full) { collect(it.loc) }
        }

        for (type in npcTypes.values) {
            val creature = HunterCreatures.forNpcName(type.name) ?: continue
            if (creature.kind == org.rsmod.content.skills.hunter.configs.TrapKind.Pit) {
                onOpNpc1(type) { tease(it.npc, creature) }
            }
        }

        onEvent<HunterTraps.Tick> { tick(this) }
        onPlayerLogin { clearPits(player) }
    }

    // region pits

    private suspend fun ProtectedAccess.layPit(loc: BoundLocInfo) {
        val number = pitNumber(loc) ?: return
        val creature = creatureOf(number)
        if (pits[player]?.containsKey(loc.coords) == true) {
            return
        }
        if (stat(stats.hunter) < creature.level) {
            mes("You need a Hunter level of ${creature.level} to set a pit here.")
            return
        }
        val log = HunterTraps.logs().firstOrNull { invTotal(inv, it) > 0 }
        if (invTotal(inv, hunter_objs.knife) < 1 || log == null) {
            mes("You need a knife and logs to build this trap.")
            return
        }
        anim(hunter_seqs.lay_small)
        delay(HunterTraps.LAY_TICKS)
        if (!invDel(inv, log, 1).success) {
            return
        }
        val bit = HunterPitBits.all[number - 1]
        val pit = Pit(number, loc.coords, creature, bit)
        pit.expires = mapClock + HunterTraps.LIFETIME
        pits.getOrPut(player) { HashMap() }[loc.coords] = pit
        vars[bit] = PIT_SET
        player.softTimer(org.rsmod.content.skills.hunter.configs.hunter_timers.tick, 1)
        mes("You set up the pit.")
    }

    private suspend fun ProtectedAccess.dismantle(loc: BoundLocInfo) {
        val pit = pits[player]?.get(loc.coords) ?: return
        anim(hunter_seqs.take)
        delay(HunterTraps.TAKE_TICKS)
        if (pits[player]?.get(loc.coords) !== pit) {
            return
        }
        remove(player, pit)
        mes("You dismantle the pit.")
    }

    private suspend fun ProtectedAccess.collect(loc: BoundLocInfo) {
        val pit = pits[player]?.get(loc.coords) ?: return
        if (pit.phase != Phase.Full) {
            return
        }
        val creature = pit.creature
        val collecting = HunterTraps.Collecting(player, creature)
        eventBus.publish(collecting)
        anim(hunter_seqs.take)
        delay(HunterTraps.TAKE_TICKS)
        if (pits[player]?.get(loc.coords) !== pit || pit.phase != Phase.Full) {
            return
        }
        val loot = ArrayList<HunterLoot.Item>()
        repeat(collecting.catches) { loot += HunterLoot.roll(creature, stat(stats.hunter), random) }
        remove(player, pit)
        for (item in loot) {
            if (!invAdd(inv, item.obj, item.count).success) {
                invAddOrDrop(objRepo, item.obj, item.count)
            }
        }
        val xp = creature.xp * collecting.catches * collecting.xpMultiplier
        statAdvance(stats.hunter, xp * xpMods.get(player, stats.hunter))
        mes("You've caught a ${creature.npcName.lowercase()}.")
        publish(HunterTraps.Trapped(player, creature, collecting.catches, loot.map { it.obj }))
    }

    private fun remove(player: Player, pit: Pit) {
        pits[player]?.remove(pit.coords)
        VarPlayerIntMapSetter.set(player, pit.bit, PIT_EMPTY)
    }

    private fun clearPits(player: Player) {
        for (bit in HunterPitBits.all) {
            if (player.vars[bit] != PIT_EMPTY) {
                VarPlayerIntMapSetter.set(player, bit, PIT_EMPTY)
            }
        }
        pits.remove(player)
    }

    // endregion

    // region teasing and jumping

    private suspend fun ProtectedAccess.tease(npc: Npc, creature: Creature) {
        val spear = invTotal(inv, hunter_objs.hunters_spear) > 0
        if (invTotal(inv, hunter_objs.teasing_stick) < 1 && !spear) {
            mes("You need a teasing stick or a hunter's spear.")
            return
        }
        if (stat(stats.hunter) < creature.level) {
            mes("You need a Hunter level of ${creature.level} to tease this creature.")
            return
        }
        if (teased.containsKey(player) || npc.isInvisible) {
            return
        }
        anim(hunter_seqs.tease)
        delay(1)
        val state = Teased(npc, creature)
        val now = mapClock
        state.until = now + TEASE_TICKS
        state.nextAttack = now + 4
        teased[player] = state
        npc.noneMode()
        player.softTimer(org.rsmod.content.skills.hunter.configs.hunter_timers.tick, 1)
        mes("The creature follows you. Lead it to your pit and jump across.")
    }

    private suspend fun ProtectedAccess.jump(loc: BoundLocInfo) {
        val pit = pits[player]?.get(loc.coords) ?: return
        if (pit.phase != Phase.Set) {
            return
        }
        val from = player.coords
        val dest = landing(pit, from)
        if (mapBlocked(dest)) {
            mes("You cannot land on the other side of this pit.")
            return
        }
        anim(hunter_seqs.jump)
        delay(2)
        telejump(dest)

        val state = teased[player] ?: return
        val creature = state.creature
        if (creature !== pit.creature) {
            mes("The creature ignores this pit.")
            return
        }
        if (distance(state.npc.coords, pit.coords) > 5 || state.lastPit == pit.coords) {
            return
        }
        state.lastPit = pit.coords
        val spear = if (invTotal(inv, hunter_objs.hunters_spear) > 0) SPEAR_BONUS else 0.0
        val chance = min(1.0, creature.chance(stat(stats.hunter)) + spear)
        if (random.randomDouble() < chance) {
            val npc = state.npc
            clearTease(player, release = false)
            npcRepo.despawn(npc, npcRepo.respawnSpeed.scale(HunterTraps.RESPAWN_TICKS))
            pit.phase = Phase.Falling
            pit.settleAt = mapClock + 2
            vars[pit.bit] = PIT_FALLING
            mes("The creature falls into your pit!")
        } else {
            state.npc.telejump(collision, dest)
            mes("The creature leaps over your trap.")
        }
    }

    /** Where a jump from [from] over [pit] lands: straight across and three tiles on. */
    private fun landing(pit: Pit, from: CoordGrid): CoordGrid {
        val dx = from.x - pit.coords.x
        val dz = from.z - pit.coords.z
        // The pit is two tiles square, so the middle is half a tile past its corner.
        val acrossX = abs(dx * 2 - 1) >= abs(dz * 2 - 1)
        return if (acrossX) {
            from.translate(if (dx <= 0) 3 else -3, 0)
        } else {
            from.translate(0, if (dz <= 0) 3 else -3)
        }
    }

    private fun clearTease(player: Player, release: Boolean) {
        val state = teased.remove(player) ?: return
        if (release && !state.npc.isInvisible) {
            state.npc.defaultMode()
        }
    }

    // endregion

    private fun tick(event: HunterTraps.Tick) {
        val player = event.player
        val now = mapClock.cycle
        val mine = pits[player]
        if (mine != null) {
            for (pit in mine.values.toList()) {
                if (pit.phase == Phase.Falling && now >= pit.settleAt) {
                    pit.phase = Phase.Full
                    pit.expires = now + HunterTraps.LIFETIME
                    VarPlayerIntMapSetter.set(player, pit.bit, PIT_FULL)
                    player.mes("Something has been caught in your pit!")
                } else if (pit.phase != Phase.Falling && now >= pit.expires) {
                    remove(player, pit)
                    player.mes("Your pit has collapsed.")
                }
            }
            if (mine.isNotEmpty()) {
                event.keep = true
            }
        }
        val state = teased[player] ?: return
        val npc = state.npc
        if (now >= state.until || npc.isInvisible || distance(npc.coords, player.coords) > 16) {
            clearTease(player, release = true)
            return
        }
        event.keep = true
        if (now % 2 == 0) {
            npc.walk(player.coords)
        }
        if (now >= state.nextAttack && distance(npc.coords, player.coords) <= 1) {
            state.nextAttack = now + 4
            val max =
                if (
                    state.creature.key == "sabre_toothed_kyatt" ||
                        state.creature.key == "horned_graahk"
                )
                    7
                else 5
            val damage = (random.of(max + 1) * clothingFactor(player)).toInt()
            player.queueHit(npc, 1, HitType.Melee, damage)
        }
    }

    /** Big cat clothing: the full set of one cat takes some of its claws' bite. */
    private fun clothingFactor(player: Player): Double {
        val o = hunter_objs
        fun wears(hat: ObjType, top: ObjType, legs: ObjType) =
            player.hat.isType(hat) && player.torso.isType(top) && player.legs.isType(legs)
        return when {
            wears(o.kyatt_hat, o.kyatt_top, o.kyatt_legs) -> 0.4
            wears(o.graahk_headdress, o.graahk_top, o.graahk_legs) -> 0.6
            wears(o.larupia_hat, o.larupia_top, o.larupia_legs) -> 0.8
            else -> 1.0
        }
    }

    private fun distance(a: CoordGrid, b: CoordGrid): Int = max(abs(a.x - b.x), abs(a.z - b.z))

    private fun pitNumber(loc: BoundLocInfo): Int? {
        val name = locTypes[loc.id]?.internalName ?: return null
        return PIT_NAME.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
    }

    companion object {
        const val PIT_EMPTY = 0
        const val PIT_SET = 1
        const val PIT_FALLING = 2
        const val PIT_FULL = 3

        /** Ticks a teased creature follows its hunter. */
        const val TEASE_TICKS = 50

        /** The extra chance, out of one, that a hunter's spear gives. */
        const val SPEAR_BONUS = 0.05

        private val PIT_NAME = Regex("hunting_pitfall_(\\d+)")

        /** The varbit that holds the state of each of the 25 pits. */
        val PIT_BIT: Map<Int, VarBitType> = (1..25).associateWith { HunterPitBits.all[it - 1] }

        /** The creature that pit [number] takes. */
        fun creatureOf(number: Int): Creature =
            when (number) {
                in 1..6 -> HunterCreatures["sabre_toothed_kyatt"]
                in 7..11 -> HunterCreatures["spined_larupia"]
                in 12..16 -> HunterCreatures["horned_graahk"]
                in 17..21 -> HunterCreatures["sunlight_antelope"]
                else -> HunterCreatures["moonlight_antelope"]
            }
    }
}
