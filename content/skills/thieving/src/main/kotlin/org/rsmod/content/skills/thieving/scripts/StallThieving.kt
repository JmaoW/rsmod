package org.rsmod.content.skills.thieving.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.events.SkillingPet
import org.rsmod.api.player.events.SkillingPetRoll
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.configs.Drop
import org.rsmod.content.skills.thieving.configs.Stall
import org.rsmod.content.skills.thieving.configs.Stalls
import org.rsmod.content.skills.thieving.configs.ThievingPets
import org.rsmod.content.skills.thieving.configs.ThievingSeqs
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the guards that come for a thief seen by a stall owner, and the owner refusing a second theft
//   for ten minutes (a stall here is simply empty for its respawn time)
// - the Rocky pet
//
// Stealing from a stall never fails. The thief reaches for it, and two ticks later has one item of
// the stall's loot and the experience, and the stall is left empty until it refills.
class StallThieving
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
) : PluginScript() {
    /** The theft each repeating thief has begun, which lands on [Reach.lands]. */
    private val reaching = WeakHashMap<Player, Reach>()

    override fun ScriptContext.startup() {
        for (stall in Stalls.all) {
            for (entry in stall.locs) {
                onOpLoc2(entry.loc) { steal(it.loc, stall) }
            }
        }
    }

    private suspend fun ProtectedAccess.steal(loc: BoundLocInfo, stall: Stall) {
        val mods = Modifiers(player)
        publish(mods)

        if (player.thievingLvl < stall.level) {
            mes("You need a Thieving level of ${stall.level} to steal from the ${stall.name}.")
            return
        }
        if (!inv.hasFreeSpace()) {
            mes("You don't have enough inventory space to do that.")
            return
        }

        if (mods.keepStall && mods.repeat) {
            stealWithoutHolding(loc, stall)
            return
        }

        anim(ThievingSeqs.steal)
        delay(STEAL_TICKS)
        award(stall)

        if (!mods.keepStall) {
            val entry = stall.locs.first { it.loc.id == loc.id }
            locRepo.change(loc, entry.empty, locRepo.respawnSpeed.scale(stall.respawn))
        } else if (mods.repeat) {
            opLoc2(loc)
        }
    }

    /**
     * A repeating theft that cannot fail or empty the stall. The thief is not held for the two
     * ticks (a delayed player ignores every click, and the repeat would never let them walk away):
     * the interaction is set again each tick instead, and clicking anywhere else ends it.
     */
    private fun ProtectedAccess.stealWithoutHolding(loc: BoundLocInfo, stall: Stall) {
        val reach = reaching[player]
        if (reach == null || reach.loc != loc.coords || reach.lands < mapClock) {
            anim(ThievingSeqs.steal)
            reaching[player] = Reach(loc.coords, mapClock + STEAL_TICKS)
        } else if (reach.lands == mapClock) {
            reaching.remove(player)
            award(stall)
        }
        opLoc2(loc)
    }

    private fun ProtectedAccess.award(stall: Stall) {
        // The pet roll comes first: a pet takes the last free slot ahead of the loot.
        publish(SkillingPetRoll(player, SkillingPet.Rocky, ThievingPets.stallChance(stall.key)))
        val item = roll(stall)
        val added = invAdd(inv, item.obj, item.count).success
        if (!added) {
            invAddOrDrop(objRepo, item.obj, item.count)
        }
        mes("You steal ${withArticle(item.obj, item.count)} from the ${stall.name}.")
        statAdvance(stats.thieving, stall.xp * xpMods.get(player, stats.thieving))
        publish(Stolen(player, stall, item.obj, item.count))
    }

    private fun ProtectedAccess.roll(stall: Stall): Item {
        val total = stall.loot.sumOf { it.weight }
        var roll = random.randomDouble() * total
        var pick: Drop = stall.loot.last()
        for (drop in stall.loot) {
            roll -= drop.weight
            if (roll < 0) {
                pick = drop
                break
            }
        }
        return Item(pick.obj, random.of(pick.amount))
    }

    private fun ProtectedAccess.withArticle(obj: ObjType, count: Int): String {
        val name = objTypes[obj].name.lowercase()
        return if (count > 1) "$count $name"
        else "${if (name.first() in VOWELS) "an" else "a"} $name"
    }

    private class Reach(val loc: CoordGrid, val lands: Int)

    /** What one theft gave. */
    class Item(val obj: ObjType, val count: Int)

    /**
     * Published before each theft so that a relic can change it: [keepStall] stops the stall
     * emptying and, with [repeat], steals again until something stops it.
     */
    class Modifiers(val player: Player) : UnboundEvent {
        var keepStall: Boolean = false
        var repeat: Boolean = false
    }

    /** Published after every theft from a stall. */
    class Stolen(val player: Player, val stall: Stall, val obj: ObjType, val count: Int) :
        UnboundEvent

    companion object {
        /** Ticks between reaching for a stall and having the item. */
        const val STEAL_TICKS = 2

        private const val VOWELS = "aeiou"
    }
}
