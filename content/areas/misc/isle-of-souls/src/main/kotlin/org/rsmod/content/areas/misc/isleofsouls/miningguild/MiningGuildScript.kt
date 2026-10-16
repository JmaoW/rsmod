package org.rsmod.content.areas.misc.isleofsouls.miningguild

import jakarta.inject.Inject
import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.invs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.content.generic.locs.ladders.DungeonLadderClimb
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.events.EventBus
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object MiningGuildLocs : LocReferences() {
    /** One of the four ladders round the hole that is Falador's way down into the guild. */
    val ladder_down = find("mguild_ladder")

    /** One of the four ladders round the guild's way back up. Also Lumbridge's cellar ladder. */
    val ladder_up = find("ladder_from_cellar")

    /** The guild's bank chest, which is the Castle Wars one and is used from its east side. */
    val bank_chest = find("castlewars_bankchest")

    val door_outer = find("mguild_door")
    val door_inner_1 = find("mguild_door_inner1")
    val door_inner_2 = find("mguild_door_inner2")

    /** The plain wooden door's open state, which has the same model as the guild's doors. */
    val door_opened = find("poordooropen")

    /** The plain wooden door's closed state, which closing a swung guild door puts back. */
    val door_plain = find("poordoor")
}

/** Published each time the guild gives [player] a golden nugget from mined ore. */
class GoldenNuggetFound(val player: Player) : UnboundEvent

/** Published when [player] climbs down into the guild. */
class MiningGuildEntered(val player: Player) : UnboundEvent

/**
 * Makes the guild's three doors work like every other single door. They have an "Open" option but
 * no open version of their own in the cache, and share their model with the plain wooden door, so
 * they are given that door's open state as their next stage and the generic door script does the
 * rest: opening swings the door aside, and the swung door's "Close" puts a plain wooden door back
 * (which looks the same). Needs `packCache`.
 */
internal object MiningGuildDoorEdits : LocEditor() {
    init {
        for (door in
            listOf(
                MiningGuildLocs.door_outer,
                MiningGuildLocs.door_inner_1,
                MiningGuildLocs.door_inner_2,
            )) {
            edit(door) {
                param[params.next_loc_stage] = MiningGuildLocs.door_opened
                contentGroup = content.closed_single_door
            }
        }
    }
}

/**
 * A ladder on the isle's mine that leads down into the Mining Guild, which is Falador's guild left
 * where it is in the cache. Falador's entrance is four ladders round a hole; the isle has a single
 * one, and the guild's four up-ladders all lead back to the isle's.
 *
 * The guild's up-ladder is the same loc as Lumbridge's cellar ladder, which the generic
 * dungeon-ladder script sends exactly 6400 tiles north. That script publishes [DungeonLadderClimb]
 * first, and this one rewrites the destination for the guild's ladders.
 */
class MiningGuildScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val invTypes: InvTypeList,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { placeLadder() }

        onOpLoc1(MiningGuildLocs.ladder_down) { climbDown() }
        // The generic dungeon-ladder script climbs 6400 tiles; the guild's ladders are redirected.
        onEvent<DungeonLadderClimb> {
            if (up && loc.coords.isGuildLadder()) {
                dest = LADDER_ARRIVAL
            }
        }

        onEvent<Mining.MineOre> {
            if (rock.coords.isInGuild()) {
                player.giveNugget()
            }
        }
    }

    /** Puts the isle's ladder on [LADDER]. */
    fun placeLadder() {
        locRepo.add(
            LADDER,
            locTypes[MiningGuildLocs.ladder_down],
            duration = Int.MAX_VALUE,
            angle = LADDER_ANGLE,
            shape = LocShape.CentrepieceStraight,
        )
    }

    private suspend fun ProtectedAccess.climbDown() {
        arriveDelay()
        if (player.miningLvl < GUILD_MINING_LEVEL) {
            mes("You need a Mining level of $GUILD_MINING_LEVEL to enter the Mining Guild.")
            return
        }
        anim(seqs.human_pickupfloor)
        delay(1)
        telejump(GUILD_ARRIVAL)
        eventBus.publish(MiningGuildEntered(player))
    }

    /**
     * Every ore mined in the guild comes with a golden nugget. It goes in the inventory, or in the
     * bank when the inventory has no room (as it does when ore is banked as it is mined).
     */
    private fun Player.giveNugget() {
        val nugget = MiningGuildObjs.golden_nugget
        val bank = invMap.getOrPut(invTypes[invs.bank])
        if (!invAdd(inv, nugget).success && !invAdd(bank, nugget).success) {
            return
        }
        spam("You find a golden nugget.")
        eventBus.publish(GoldenNuggetFound(this))
    }

    private fun CoordGrid.isGuildLadder(): Boolean =
        level == GUILD_HOLE.level &&
            x in GUILD_HOLE.x - 1..GUILD_HOLE.x + 1 &&
            z in GUILD_HOLE.z - 1..GUILD_HOLE.z + 1

    companion object {
        /**
         * Where the user asked for it, on the tile they were standing on in the isle's mine. The
         * ground south of it is cliff (terrain-blocked), so the ladder is reached from the north.
         */
        val LADDER = CoordGrid(2204, 2786, 0)

        /** Turn it round here if the ladder faces the wrong way. */
        val LADDER_ANGLE = LocAngle.South

        /** The tile north of the ladder, where a player climbing up out of the guild lands. */
        val LADDER_ARRIVAL = LADDER.translateZ(1)

        /** The middle of the guild's four up-ladders. */
        val GUILD_HOLE = CoordGrid(3019, 9739, 0)

        /** The tile in front of the guild's south up-ladder, where a player climbing down lands. */
        val GUILD_ARRIVAL = GUILD_HOLE.translateZ(-2)

        /**
         * The guild's tiles, as a box. The Dwarven Mine, whose rocks are not the guild's, starts to
         * its north from about z 9780.
         */
        val GUILD_WEST = CoordGrid(2990, 9695, 0)
        val GUILD_EAST = CoordGrid(3075, 9770, 0)

        fun CoordGrid.isInGuild(): Boolean =
            level == 0 && x in GUILD_WEST.x..GUILD_EAST.x && z in GUILD_WEST.z..GUILD_EAST.z

        /** Falador asks for this level at the ladder. */
        const val GUILD_MINING_LEVEL = 60
    }
}
