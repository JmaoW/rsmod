package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.configs.Boost
import org.rsmod.content.skills.hunter.configs.Butterfly
import org.rsmod.content.skills.hunter.configs.Creature
import org.rsmod.content.skills.hunter.configs.HunterCatchables
import org.rsmod.content.skills.hunter.configs.HunterLootTables
import org.rsmod.content.skills.hunter.configs.Impling
import org.rsmod.content.skills.hunter.configs.hunter_objs
import org.rsmod.content.skills.hunter.configs.hunter_seqs
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.stat.StatType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - Puro-Puro, where implings can be caught barehanded without a jar for another kind of
//   experience, and the impling jars the Wiki's clue scroll rolls would have added to
// - the butterfly mixes (herblore) and the mix-making
//
// Butterflies, moths and implings. A hunter catches one with a butterfly net (or barehanded, at a
// higher level), into an empty jar if there is one in the inventory. A caught butterfly can be
// released from its jar for its boost; a caught impling can be looted from its jar, which most of
// the time is left as an empty jar.
class HunterCatching
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val tables: HunterLootTables,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in npcTypes.values) {
            if (type.op.getOrNull(0) != "Catch") {
                continue
            }
            val butterfly = HunterCatchables.butterfly(type.name)
            val impling = HunterCatchables.impling(type.name)
            if (butterfly != null) {
                onOpNpc1(type) { catchButterfly(it.npc, butterfly) }
            } else if (impling != null) {
                onOpNpc1(type) { catchImpling(it.npc, impling) }
            }
        }
        for (butterfly in HunterCatchables.butterflies) {
            onOpHeld4(butterfly.jar) { release(butterfly) }
        }
        for (impling in HunterCatchables.implings) {
            onOpHeld3(impling.jar) { loot(impling) }
        }
    }

    // region butterflies

    private suspend fun ProtectedAccess.catchButterfly(npc: Npc, butterfly: Butterfly) {
        val net = holdsNet()
        val required = if (net) butterfly.level else butterfly.hands
        if (stat(stats.hunter) < required) {
            mes("You need a Hunter level of $required to catch ${butterfly.name.lowercase()}s.")
            return
        }
        val jarred = invTotal(inv, hunter_objs.butterfly_jar) > 0
        anim(swing(net))
        delay(2)
        if (npc.isInvisible) {
            return
        }
        val low = if (net) butterfly.low else butterfly.handsLow
        val high = if (net) butterfly.high else butterfly.handsHigh
        if (random.randomDouble() >= min(1.0, Creature.chance(low, high, stat(stats.hunter)))) {
            mes("The ${butterfly.name.lowercase()} flutters away.")
            return
        }
        if (jarred) {
            invDel(inv, hunter_objs.butterfly_jar, 1)
            give(butterfly.jar)
            mes("You catch the ${butterfly.name.lowercase()} in a jar.")
        } else {
            mes("You catch the ${butterfly.name.lowercase()} and let it go.")
            boost(butterfly.boost)
        }
        val xp = if (net) butterfly.xp else butterfly.handsXp
        statAdvance(stats.hunter, xp * xpMods.get(player, stats.hunter))
        publish(Caught(player, butterfly.name, jarred))
    }

    private suspend fun ProtectedAccess.release(butterfly: Butterfly) {
        if (!invDel(inv, butterfly.jar, 1).success) {
            return
        }
        give(hunter_objs.butterfly_jar)
        mes("You release the ${butterfly.name.lowercase()}.")
        boost(butterfly.boost)
    }

    private fun ProtectedAccess.boost(boost: Boost) {
        when (boost) {
            is Boost.Stat -> statBoost(boost.stat, BOOST_CONSTANT, BOOST_PERCENT)
            Boost.Hitpoints -> statHeal(stats.hitpoints, 8, 0)
            Boost.Restore -> {
                for (stat in RESTORED) {
                    statHeal(stat, 6, 20)
                }
                statHeal(stats.hitpoints, 8, 0)
            }
            Boost.Prayer -> statHeal(stats.prayer, 22, 0)
        }
    }

    // endregion

    // region implings

    private suspend fun ProtectedAccess.catchImpling(npc: Npc, impling: Impling) {
        val net = holdsNet()
        val required = if (net) impling.level else impling.hands
        if (stat(stats.hunter) < required) {
            mes("You need a Hunter level of $required to catch ${impling.name.lowercase()}s.")
            return
        }
        val jarred = invTotal(inv, hunter_objs.impling_jar) > 0
        if (net && !jarred) {
            mes("You need an empty impling jar.")
            return
        }
        anim(swing(net))
        delay(2)
        if (npc.isInvisible) {
            return
        }
        if (
            random.randomDouble() >=
                min(1.0, Creature.chance(impling.low, impling.high, stat(stats.hunter)))
        ) {
            mes("The ${impling.name.lowercase()} slips away.")
            return
        }
        if (jarred) {
            invDel(inv, hunter_objs.impling_jar, 1)
            give(impling.jar)
            mes("You catch the ${impling.name.lowercase()} in a jar.")
        } else {
            // With no jar to put it in, a barehanded impling is looted at once.
            mes(
                "You catch the ${impling.name.lowercase()} barehanded and it gives up what it carries."
            )
            open(impling)
        }
        npcRepo.despawn(npc, npcRepo.respawnSpeed.scale(IMPLING_RESPAWN_TICKS))
        statAdvance(stats.hunter, impling.xp * xpMods.get(player, stats.hunter))
        publish(Caught(player, impling.name, jarred))
    }

    private suspend fun ProtectedAccess.loot(impling: Impling) {
        if (!invDel(inv, impling.jar, 1).success) {
            return
        }
        val opening = Opening(player, impling)
        opening.breaks = random.of(JAR_BREAK_ODDS) == 0
        publish(opening)
        if (opening.breaks) {
            mes("The jar breaks as you open it.")
        } else {
            give(hunter_objs.impling_jar)
        }
        open(impling)
    }

    /** Gives what one impling holds. */
    private fun ProtectedAccess.open(impling: Impling) {
        val drop =
            if (impling.key == "Lucky") tables.luckyLoot(random)
            else tables.implingLoot(impling.key, random)
        if (drop == null) {
            mes("The ${impling.name.lowercase()} jar was empty.")
            return
        }
        val added = invAdd(inv, drop.obj, drop.count, cert = drop.noted).success
        if (!added) {
            invAddOrDrop(objRepo, drop.obj, drop.count)
        }
        publish(Looted(player, impling.name, drop.obj))
    }

    // endregion

    private fun ProtectedAccess.give(obj: ObjType) {
        if (!invAdd(inv, obj, 1).success) {
            invAddOrDrop(objRepo, obj, 1)
        }
    }

    private fun ProtectedAccess.holdsNet(): Boolean =
        player.righthand.isType(hunter_objs.butterfly_net) ||
            player.righthand.isType(hunter_objs.magic_butterfly_net)

    private fun ProtectedAccess.swing(net: Boolean) =
        if (!net) hunter_seqs.hands
        else if (player.righthand.isType(hunter_objs.magic_butterfly_net)) hunter_seqs.net
        else hunter_seqs.butterfly_net

    /** Published when a butterfly, moth or impling is caught ([jarred] when put in a jar). */
    class Caught(val player: Player, val name: String, val jarred: Boolean) : UnboundEvent

    /** Published when an impling jar is opened: a relic can make sure it does not break. */
    class Opening(val player: Player, val impling: Impling) : UnboundEvent {
        var breaks: Boolean = false
    }

    /** Published when an impling gives up its loot. */
    class Looted(val player: Player, val name: String, val obj: ObjType) : UnboundEvent

    companion object {
        /** One in this many impling jars breaks when it is opened. */
        const val JAR_BREAK_ODDS = 10

        /** Ticks before a caught impling is back. */
        const val IMPLING_RESPAWN_TICKS = 100

        const val BOOST_CONSTANT = 4
        const val BOOST_PERCENT = 15

        /** Every stat a sunlight moth tops up. */
        private val RESTORED: List<StatType> =
            listOf(
                stats.attack,
                stats.defence,
                stats.strength,
                stats.ranged,
                stats.magic,
                stats.cooking,
                stats.woodcutting,
                stats.fletching,
                stats.fishing,
                stats.firemaking,
                stats.crafting,
                stats.smithing,
                stats.mining,
                stats.herblore,
                stats.agility,
                stats.thieving,
                stats.slayer,
                stats.farming,
                stats.runecrafting,
                stats.hunter,
                stats.construction,
            )
    }
}
