package org.rsmod.content.skills.thieving.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import kotlin.math.max
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.back
import org.rsmod.api.player.events.SkillingPet
import org.rsmod.api.player.events.SkillingPetRoll
import org.rsmod.api.player.feet
import org.rsmod.api.player.front
import org.rsmod.api.player.hands
import org.rsmod.api.player.hat
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.legs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.player.torso
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.configs.Drop
import org.rsmod.content.skills.thieving.configs.PickpocketTarget
import org.rsmod.content.skills.thieving.configs.PickpocketTargets
import org.rsmod.content.skills.thieving.configs.ThievingObjs
import org.rsmod.content.skills.thieving.configs.ThievingPets
import org.rsmod.content.skills.thieving.configs.ThievingSeqs
import org.rsmod.content.skills.thieving.configs.ThievingSpotanims
import org.rsmod.content.skills.thieving.configs.ThievingSynths
import org.rsmod.content.skills.thieving.configs.ThievingVarps
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType
import org.rsmod.game.type.hunt.HuntVis
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - blackjacking (lure and knock out) and the wealthy citizen's street urchin
// - the Ardougne diary bonuses, the Shadow Veil spell, the Rocky pet and clue scroll rolls
// - the in-game loot tracker
//
// Every cache npc with the "Pickpocket" option (or the Digsite workman's "Steal-from") and one of
// the names in [PickpocketTargets] can be robbed. The timing follows the tsps notes (a capture of a
// hero): the attempt message and a two tick cooldown come at the click, the outcome one tick later.
// A caught thief is stunned for nine ticks, can pickpocket again after eight of them, and takes the
// target's stun damage; the strike, the graphic and the damage all come on the tick the thief is
// caught here (the capture has them a tick later), so walking away cannot dodge them.
class Pickpocketing
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val search: NpcSearch,
    private val xpMods: XpModifiers,
    private val objRepo: ObjRepository,
) : PluginScript() {
    /** The map clock tick from which each thief can attempt another pickpocket. */
    private val nextAttempt = WeakHashMap<Player, Int>()

    /** The map clock tick until which each stunned thief cannot pickpocket. */
    private val stunnedUntil = WeakHashMap<Player, Int>()

    /** The attempt each repeating thief has begun, which lands on [Attempt.lands]. */
    private val attempting = WeakHashMap<Player, Attempt>()

    override fun ScriptContext.startup() {
        for (type in npcTypes.values) {
            val target = PickpocketTargets.forNpc(type) ?: continue
            when (val op = target.opIndex(type) + 1) {
                1 -> onOpNpc1(type) { pickpocket(it.npc, target, op) }
                2 -> onOpNpc2(type) { pickpocket(it.npc, target, op) }
                3 -> onOpNpc3(type) { pickpocket(it.npc, target, op) }
                4 -> onOpNpc4(type) { pickpocket(it.npc, target, op) }
                5 -> onOpNpc5(type) { pickpocket(it.npc, target, op) }
            }
        }
    }

    /** One attempt to rob [npc], whose robbing option is [op]. */
    private suspend fun ProtectedAccess.pickpocket(npc: Npc, target: PickpocketTarget, op: Int) {
        val mods = Modifiers(player)
        publish(mods)

        if (player.thievingLvl < target.level) {
            mes("You need a Thieving level of ${target.level} to pickpocket ${target.article()}.")
            return
        }
        if (target.needsFireCape && !hasFireCape()) {
            mes("You need a fire cape to pickpocket ${target.article()}.")
            return
        }
        if ((stunnedUntil[player] ?: 0) > mapClock) {
            return
        }
        if (isInCombat()) {
            mes("You can't pickpocket while you are in combat.")
            return
        }
        val pouch = target.pouch
        if (pouch != null && invTotal(inv, pouch.obj) >= POUCH_LIMIT * mods.pouchMultiplier) {
            mes("You have too many coin pouches. Open them before pickpocketing again.")
            return
        }
        if (!hasRoom(target)) {
            mes("You don't have enough inventory space to do that.")
            return
        }

        if (mods.guaranteed && mods.repeat) {
            pickpocketWithoutHolding(npc, target, op, mods)
            return
        }

        mes("You attempt to pick ${target.possessive(npc.name)} pocket.")
        val wait = max(0, (nextAttempt[player] ?: 0) - mapClock)
        nextAttempt[player] = mapClock + wait + COOLDOWN
        delay(1 + wait)

        val success = mods.guaranteed || random.randomDouble() < successChance(target)
        if (success) {
            succeed(npc, target, mods)
            if (mods.repeat) {
                repeatOp(npc, op)
            }
        } else {
            fail(npc, target)
        }
    }

    /**
     * A repeating pickpocket that cannot fail, so there is no stun to hold the thief for. The thief
     * is not delayed (a delayed player ignores every click, and the repeat would never let them
     * walk away): on the same schedule as [pickpocket], the interaction is set again each tick and
     * clicking anywhere else ends it.
     */
    private suspend fun ProtectedAccess.pickpocketWithoutHolding(
        npc: Npc,
        target: PickpocketTarget,
        op: Int,
        mods: Modifiers,
    ) {
        val pending = attempting[player]?.takeIf { it.npc === npc && it.lands >= mapClock }
        if (pending == null) {
            if (mapClock >= (nextAttempt[player] ?: 0)) {
                mes("You attempt to pick ${target.possessive(npc.name)} pocket.")
                nextAttempt[player] = mapClock + COOLDOWN
                attempting[player] = Attempt(npc, mapClock + 1)
            }
        } else if (pending.lands == mapClock) {
            attempting.remove(player)
            succeed(npc, target, mods)
        }
        repeatOp(npc, op)
    }

    private suspend fun ProtectedAccess.succeed(
        npc: Npc,
        target: PickpocketTarget,
        mods: Modifiers,
    ) {
        anim(ThievingSeqs.pickpocket)
        soundSynth(ThievingSynths.pickpocket)

        val doubled = rogueDoubles()
        val loot = rollLoot(target, doubled).toMutableList()
        var victims = 1
        if (mods.area) {
            for (other in sameKindNearby(npc, target)) {
                loot += rollLoot(target, doubled)
                victims++
            }
        }
        // The pet roll comes first: a pet takes the last free slot ahead of the loot.
        publish(
            SkillingPetRoll(player, SkillingPet.Rocky, ThievingPets.pickpocketChance(target.key))
        )
        for (item in loot) {
            give(item, mods.noted)
        }
        mes("You pick ${target.possessive(npc.name)} pocket.")
        statAdvance(stats.thieving, target.xp * xpMods.get(player, stats.thieving))
        publish(Pickpocketed(player, target, npc, loot, victims))
    }

    private suspend fun ProtectedAccess.fail(npc: Npc, target: PickpocketTarget) {
        mes("You fail to pick ${target.possessive(npc.name)} pocket.")
        npc.say(target.catchLine)
        npc.facePlayer(player)
        if (npc.visType.hasParam(params.attack_anim)) {
            npc.anim(npc.visType.param(params.attack_anim))
        }

        wearGloves()
        if (necklaceSaves()) {
            return
        }
        player.spotanim(ThievingSpotanims.stunned, height = STUN_GRAPHIC_HEIGHT)
        player.freeze(STUN_TICKS)
        stunnedUntil[player] = mapClock + STUN_TICKS - 1
        nextAttempt[player] = mapClock + STUN_TICKS
        player.queueHit(1, HitType.Typeless, random.of(target.stun))
        delay(1)
        mes("You've been stunned!")
    }

    /** The Wiki's chance of success: the target's chart, with the gloves and cape bonuses. */
    private fun ProtectedAccess.successChance(target: PickpocketTarget): Double {
        val multipliers = ArrayList<Double>(2)
        if (player.hands.isType(ThievingObjs.hunting_silent_gloves)) {
            multipliers += GLOVES_BONUS
        }
        if (wearsThievingCape()) {
            multipliers += CAPE_BONUS
        }
        return target.chance(player.thievingLvl, multipliers)
    }

    /**
     * Whether the rogue equipment doubles this pickpocket's loot: 15% for each piece, all five
     * always.
     */
    private fun ProtectedAccess.rogueDoubles(): Boolean {
        val pieces = rogueEquipment(player)
        return when {
            pieces >= ROGUE_PIECES -> true
            pieces == 0 -> false
            else -> random.of(100) < pieces * ROGUE_CHANCE_PER_PIECE
        }
    }

    /** The loot of one pickpocket of [target]: the always items, then the table. */
    fun ProtectedAccess.rollLoot(target: PickpocketTarget, doubled: Boolean): List<Loot> {
        val loot = ArrayList<Loot>()
        for (drop in target.always) {
            loot += itemsOf(target, drop, doubled)
        }
        val firstHit = target.first.firstOrNull { random.randomDouble() < it.weight }
        if (firstHit != null) {
            loot += itemsOf(target, firstHit, doubled)
        } else if (target.table.isNotEmpty()) {
            loot += itemsOf(target, pickWeighted(target.table), doubled)
        }
        return loot
    }

    private fun ProtectedAccess.pickWeighted(table: List<Drop>): Drop {
        val farming = stat(stats.farming)
        val total = table.sumOf { it.weightAt(farming) }
        var roll = random.randomDouble() * total
        for (drop in table) {
            roll -= drop.weightAt(farming)
            if (roll < 0) {
                return drop
            }
        }
        return table.last()
    }

    /**
     * What one [drop] gives: a coin drop becomes a coin pouch, and a doubled drop twice as many.
     */
    private fun ProtectedAccess.itemsOf(
        target: PickpocketTarget,
        drop: Drop,
        doubled: Boolean,
    ): List<Loot> {
        val pouch = target.pouch
        if (pouch != null && drop.obj.id == ThievingObjs.coins.id) {
            val items = mutableListOf(Loot(pouch.obj, 1))
            if (doubled) {
                // A doubled pouch is the pouch and the coins in it, not two pouches.
                items += Loot(ThievingObjs.coins, pouch.coins.first)
            }
            return items
        }
        val count = random.of(drop.amount)
        return listOf(Loot(drop.obj, if (doubled) count * 2 else count))
    }

    private fun ProtectedAccess.give(item: Loot, noted: Boolean) {
        val added = invAdd(inv, item.obj, item.count, cert = noted).success
        if (!added) {
            invAddOrDrop(objRepo, item.obj, item.count)
        }
    }

    /** The other npcs of [target]'s kind within five tiles (an 11 by 11 square) of [npc]. */
    private fun sameKindNearby(npc: Npc, target: PickpocketTarget): List<Npc> =
        search
            .findAllAny(npc.coords, DODGY_DEALS_RADIUS, HuntVis.Off)
            .filter { it !== npc && PickpocketTargets.forNpc(it.type) === target }
            .toList()

    /**
     * Whether the thief can hold what [target] gives: a free slot does, and so does a pouch of the
     * target's kind already in the inventory when the target only ever gives coins.
     */
    private fun ProtectedAccess.hasRoom(target: PickpocketTarget): Boolean {
        if (inv.hasFreeSpace()) {
            return true
        }
        val pouch = target.pouch ?: return false
        val coinsOnly = (target.always + target.first + target.table).all { it.obj.id == COINS_ID }
        return coinsOnly && invTotal(inv, pouch.obj) > 0
    }

    private fun ProtectedAccess.repeatOp(npc: Npc, op: Int) {
        when (op) {
            1 -> opNpc1(npc)
            2 -> opNpc2(npc)
            3 -> opNpc3(npc)
            4 -> opNpc4(npc)
        }
    }

    /** Counts a caught pickpocket against the gloves of silence, and wears them out. */
    private fun ProtectedAccess.wearGloves() {
        if (!player.hands.isType(ThievingObjs.hunting_silent_gloves)) {
            return
        }
        val fails = player.vars[ThievingVarps.gloves_fails] + 1
        if (fails >= GLOVES_LIFE) {
            VarPlayerIntMapSetter.set(player, ThievingVarps.gloves_fails, 0)
            invDel(worn, ThievingObjs.hunting_silent_gloves, 1)
            mes("Your gloves of silence have worn out.")
        } else {
            VarPlayerIntMapSetter.set(player, ThievingVarps.gloves_fails, fails)
        }
    }

    /**
     * Rolls the dodgy necklace's chance to stop a stun and the damage, and uses a charge when it
     * does. The tenth charge turns the necklace to dust.
     */
    private fun ProtectedAccess.necklaceSaves(): Boolean {
        if (!player.front.isType(ThievingObjs.dodgy_necklace)) {
            return false
        }
        if (random.of(100) >= NECKLACE_CHANCE) {
            return false
        }
        val saves = player.vars[ThievingVarps.necklace_saves] + 1
        if (saves >= NECKLACE_CHARGES) {
            VarPlayerIntMapSetter.set(player, ThievingVarps.necklace_saves, 0)
            invDel(worn, ThievingObjs.dodgy_necklace, 1)
            mes("Your dodgy necklace protects you. It then crumbles to dust.")
        } else {
            VarPlayerIntMapSetter.set(player, ThievingVarps.necklace_saves, saves)
            mes(
                "Your dodgy necklace protects you. It has ${NECKLACE_CHARGES - saves} charges left."
            )
        }
        return true
    }

    private fun ProtectedAccess.hasFireCape(): Boolean =
        FIRE_CAPES.any { player.back.isType(it) || invTotal(inv, it) > 0 }

    private fun ProtectedAccess.wearsThievingCape(): Boolean =
        THIEVING_CAPES.any { player.back.isType(it) }

    /** "the man", or a named npc's name, as in "pickpocket ___". */
    private fun PickpocketTarget.article(): String = if (properNames) name else "the $name"

    private class Attempt(val npc: Npc, val lands: Int)

    /** One item of loot: [count] of [obj]. */
    class Loot(val obj: ObjType, val count: Int)

    /**
     * Published before each pickpocket attempt so that a relic can change it: [guaranteed] makes
     * every roll a success, [area] also robs every other npc of the same kind within five tiles,
     * [repeat] robs again until something stops it, [noted] gives the loot noted, and
     * [pouchMultiplier] raises how many coin pouches can be carried.
     */
    class Modifiers(val player: Player) : UnboundEvent {
        var guaranteed: Boolean = false
        var area: Boolean = false
        var repeat: Boolean = false
        var noted: Boolean = false
        var pouchMultiplier: Int = 1
    }

    /**
     * Published after a successful pickpocket. [loot] is everything given, from all [victims] (the
     * npc and, with the area relic, those around it).
     */
    class Pickpocketed(
        val player: Player,
        val target: PickpocketTarget,
        val npc: Npc,
        val loot: List<Loot>,
        val victims: Int,
    ) : UnboundEvent

    companion object {
        /** Ticks between one attempt and the next. */
        const val COOLDOWN = 2

        /** Ticks a caught thief cannot move. */
        const val STUN_TICKS = 9

        /** The height of the stunned graphic above the thief. */
        private const val STUN_GRAPHIC_HEIGHT = 124

        /** Coin pouches of one kind that can be carried before they must be opened. */
        const val POUCH_LIMIT = 28

        const val GLOVES_BONUS = 1.05
        const val CAPE_BONUS = 1.1

        /** Failed pickpockets before the gloves of silence wear out. */
        const val GLOVES_LIFE = 62

        /** Percent chance that the dodgy necklace stops a stun, and the stuns it can stop. */
        const val NECKLACE_CHANCE = 25
        const val NECKLACE_CHARGES = 10

        /** Percent chance each piece of rogue equipment doubles the loot, and the full set. */
        const val ROGUE_CHANCE_PER_PIECE = 15
        const val ROGUE_PIECES = 5

        /** The side, in tiles, from the npc, of the square the area relic robs. */
        const val DODGY_DEALS_RADIUS = 5

        private val COINS_ID
            get() = ThievingObjs.coins.id

        private val FIRE_CAPES =
            listOf(
                ThievingObjs.tzhaar_cape_fire,
                ThievingObjs.tzhaar_cape_fire_dummy,
                ThievingObjs.infernal_cape,
                ThievingObjs.infernal_cape_dummy,
                ThievingObjs.skillcape_max,
                ThievingObjs.skillcape_max_worn,
            )

        private val THIEVING_CAPES =
            listOf(
                ThievingObjs.skillcape_thieving,
                ThievingObjs.skillcape_thieving_trimmed,
                ThievingObjs.skillcape_max,
                ThievingObjs.skillcape_max_worn,
            )

        /** How many pieces of the rogue equipment [player] is wearing. */
        fun rogueEquipment(player: Player): Int {
            var pieces = 0
            if (player.hat.isType(ThievingObjs.roguesden_helm)) pieces++
            if (player.torso.isType(ThievingObjs.roguesden_body)) pieces++
            if (player.legs.isType(ThievingObjs.roguesden_legs)) pieces++
            if (player.hands.isType(ThievingObjs.roguesden_gloves)) pieces++
            if (player.feet.isType(ThievingObjs.roguesden_boots)) pieces++
            return pieces
        }
    }
}
