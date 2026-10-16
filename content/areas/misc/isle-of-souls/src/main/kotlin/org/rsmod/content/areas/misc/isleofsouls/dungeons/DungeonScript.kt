package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.ladders.DungeonLadderClimb
import org.rsmod.events.EventBus
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Published when [player] has just landed in [dungeon], by its entrance or by `::dungeon`. A
 * dungeon with state to set up (the God Wars ropes, kill counts) subscribes.
 */
class DungeonEntered(val player: Player, val dungeon: Dungeon) : UnboundEvent

/**
 * The dungeons' doors: puts each entrance on the isle, takes a player from it into the real dungeon
 * map, and sends every way out back to the entrance. See [Dungeon].
 *
 * The generic `ladder_from_cellar` (one object shared by many dungeons and by Lumbridge's cellar)
 * has no handler of its own here: the generic dungeon-ladder script asks first, through
 * [DungeonLadderClimb], and any such ladder inside a dungeon's area is redirected.
 */
class DungeonScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val protectedAccess: ProtectedAccessLauncher,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { placeEntrances() }

        for (dungeon in Dungeon.entries) {
            // An entrance that is part of the map may share its type with others elsewhere in the
            // cache (the Isle of Souls cave has a twin in the tutorial area), so go by where it
            // stands.
            onOpLoc1(dungeon.entranceType) {
                if (!dungeon.onMap || it.loc.coords == dungeon.entrance) enter(dungeon)
            }
            for (type in dungeon.alsoEnter) {
                onOpLoc1(type) { enter(dungeon) }
            }
        }

        for ((type, exits) in Dungeon.entries.flatMap { it.exits }.groupBy { it.type }) {
            onOpLoc1(type) { leave(exits, it.loc) }
        }

        for ((type, links) in Dungeon.links.groupBy { it.type }) {
            onOpLoc1(type) { follow(links, it.loc) }
        }

        onEvent<DungeonLadderClimb> {
            if (up) {
                Dungeon.containing(loc.coords)?.let { dest = it.front }
            }
        }

        onCommand("dungeon") {
            modLevel = modlevels.admin
            desc = "Teleport into one of the isle's dungeons"
            invalidArgs = "Use as ::dungeon key (ex: ::dungeon taverley); ::dungeon lists the keys"
            cheat {
                val dungeon = args.getOrNull(0)?.let { Dungeon.byKey(it) }
                if (dungeon == null) {
                    player.mes(Dungeon.entries.joinToString(", ") { it.key })
                    return@cheat
                }
                protectedAccess.launch(player) {
                    mes("Teleported to the ${dungeon.title}.")
                    telejump(dungeon.arrival)
                    eventBus.publish(DungeonEntered(player, dungeon))
                }
            }
        }
    }

    /** Puts every entrance on the isle. */
    fun placeEntrances() {
        for (dungeon in Dungeon.entries.filterNot { it.onMap }) {
            locRepo.add(
                dungeon.entrance,
                locTypes[dungeon.entranceType],
                duration = Int.MAX_VALUE,
                angle = dungeon.angle,
                shape = dungeon.shape,
            )
            for (prop in dungeon.props) {
                locRepo.add(
                    dungeon.entrance.translate(prop.dx, prop.dz),
                    locTypes[prop.type],
                    duration = Int.MAX_VALUE,
                    angle = prop.angle,
                    shape = prop.shape,
                )
            }
        }
    }

    private suspend fun ProtectedAccess.enter(dungeon: Dungeon) {
        arriveDelay()
        if (dungeon.climbIn) {
            anim(seqs.human_pickupfloor)
            delay(1)
        }
        telejump(dungeon.arrival)
        eventBus.publish(DungeonEntered(player, dungeon))
    }

    private suspend fun ProtectedAccess.leave(exits: List<DungeonExit>, loc: BoundLocInfo) {
        arriveDelay()
        val exit = exits.firstOrNull { it.coords == loc.coords }
        val dungeon = exit?.let { e -> Dungeon.entries.first { e in it.exits } }
        if (exit == null || dungeon == null) {
            mes("Nothing interesting happens.")
            return
        }
        if (exit.climb) {
            anim(seqs.human_pickupfloor)
            delay(1)
        }
        telejump(dungeon.front)
    }

    private suspend fun ProtectedAccess.follow(links: List<DungeonLink>, loc: BoundLocInfo) {
        arriveDelay()
        val link = links.firstOrNull { it.coords == loc.coords }
        if (link == null) {
            mes("Nothing interesting happens.")
            return
        }
        if (link.agility > 0 && player.agilityLvl < link.agility) {
            mes("You need an Agility level of at least ${link.agility} to climb this.")
            return
        }
        if (link.climb) {
            anim(seqs.human_pickupfloor)
            delay(1)
        }
        telejump(link.dest)
    }
}
