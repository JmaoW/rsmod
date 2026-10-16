package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.axeWoodcuttingAnim
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal object DungeonSeqs : SeqReferences() {
    val sword_slash = find("human_sword_slash")
    val lockpick = find("human_pickupfloor")
}

/**
 * The dungeons' doors with no open version, the Forthos Dungeon's locked door, the spider webs and
 * Brimhaven's vines. Each opens a way for a while and then closes it again.
 * - **Gates and doors** with no open model in the cache swing open when used and close again a few
 *   seconds later: the closed model is turned and moved like the generic doors, a pair's halves
 *   swinging apart from the middle. The Wiki's keys (the dusty key, the temple key) are not asked
 *   for.
 * - **The Grubby Door** takes 57 Thieving, for 10 experience.
 * - **Webs** are slashed with a knife (50% to get through) or a weapon, by its slash bonus: 20% for
 *   a slash bonus of 1 or more, rising to 100% at +100 (the Wiki's chances).
 * - **Vines** are chopped with any axe the player can use: 10 Woodcutting for the two near the
 *   entrance and 34 for the three that lead on to the black demons.
 */
class DungeonPassageScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objTypes: ObjTypeList,
    private val bonuses: WornBonuses,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
) : PluginScript() {
    /** Doors that are standing open now, by where they swung to, and the tick they close again. */
    private val swung = HashMap<SwungKey, Int>()

    override fun ScriptContext.startup() {
        // Double gates: the two halves swing apart from the middle, like the cache's double doors.
        val doubles =
            listOf(
                DungeonDoorLocs.member_gate_left to DungeonDoorLocs.member_gate_right,
                DungeonDoorLocs.cauldron_door_left to DungeonDoorLocs.cauldron_door,
            )
        for ((left, right) in doubles) {
            onOpLoc1(left) { swingDouble(it.loc, left, right, clickedLeft = true) }
            onOpLoc1(right) { swingDouble(it.loc, left, right, clickedLeft = false) }
        }
        val singles =
            listOf(
                DungeonDoorLocs.deep_dungeon_gate,
                DungeonDoorLocs.dungeon_jail_door,
                DungeonDoorLocs.temple_door_west,
                DungeonDoorLocs.temple_door_east,
            )
        for (type in singles) {
            onOpLoc1(type) { swingSingle(it.loc, type) }
        }

        onOpLoc1(DungeonDoorLocs.grubby_door) { pickGrubbyDoor(it.loc) }
        onOpLoc1(DungeonDoorLocs.web) { slashWeb(it.loc) }

        val vines =
            mapOf(
                DungeonDoorLocs.vine_1 to VINE_EASY,
                DungeonDoorLocs.vine_2 to VINE_EASY,
                DungeonDoorLocs.vine_3 to VINE_HARD,
                DungeonDoorLocs.vine_4 to VINE_HARD,
                DungeonDoorLocs.vine_5 to VINE_HARD,
            )
        for ((type, level) in vines) {
            onOpLoc1(type) { chopVine(it.loc, level) }
        }
    }

    /**
     * With no open model in the cache, a swung door is the closed model turned and moved the way
     * the generic door script does it (a left half turns three quarters, a right half one quarter).
     */
    private suspend fun ProtectedAccess.swingDouble(
        clicked: BoundLocInfo,
        left: LocType,
        right: LocType,
        clickedLeft: Boolean,
    ) {
        arriveDelay()
        if (isSwung(clicked)) {
            mes("It is already open.")
            return
        }
        val leftInfo: LocInfo?
        val rightInfo: LocInfo?
        if (clickedLeft) {
            leftInfo = locRepo.findExact(clicked.coords, left)
            val rightCoords =
                DoorTranslations.translateClose(clicked.coords, clicked.shape, clicked.angle)
            rightInfo = locRepo.findExact(rightCoords, right)?.takeIf { it.angle == clicked.angle }
        } else {
            rightInfo = locRepo.findExact(clicked.coords, right)
            val leftCoords =
                DoorTranslations.translateCloseOpposite(
                    clicked.coords,
                    clicked.shape,
                    clicked.angle,
                )
            leftInfo = locRepo.findExact(leftCoords, left)?.takeIf { it.angle == clicked.angle }
        }
        leftInfo?.let { swing(it, left, rotations = 3) }
        rightInfo?.let { swing(it, right, rotations = 1) }
    }

    private suspend fun ProtectedAccess.swingSingle(clicked: BoundLocInfo, type: LocType) {
        arriveDelay()
        if (isSwung(clicked)) {
            mes("It is already open.")
            return
        }
        val info = locRepo.findExact(clicked.coords, type) ?: return
        swing(info, type, rotations = 1)
    }

    private fun ProtectedAccess.swing(info: LocInfo, type: LocType, rotations: Int) {
        val coords = DoorTranslations.translateOpen(info.coords, info.shape, info.angle)
        val angle = info.turnAngle(rotations)
        if (!locRepo.del(info, DOOR_TICKS)) {
            return
        }
        locRepo.add(coords, type, DOOR_TICKS, angle, info.shape)
        swung[SwungKey(coords, type.id)] = mapClock + DOOR_TICKS
    }

    private fun ProtectedAccess.isSwung(loc: BoundLocInfo): Boolean {
        val key = SwungKey(loc.coords, loc.id)
        val expiry = swung[key] ?: return false
        if (expiry <= mapClock) {
            swung.remove(key)
            return false
        }
        return true
    }

    private data class SwungKey(val coords: CoordGrid, val id: Int)

    private suspend fun ProtectedAccess.pickGrubbyDoor(loc: BoundLocInfo) {
        arriveDelay()
        if (player.thievingLvl < GRUBBY_DOOR_LEVEL) {
            mes("You need a Thieving level of $GRUBBY_DOOR_LEVEL to pick this lock.")
            return
        }
        anim(DungeonSeqs.lockpick)
        delay(2)
        mes("You pick the lock.")
        statAdvance(stats.thieving, GRUBBY_DOOR_XP * xpMods.get(player, stats.thieving))
        locRepo.change(loc, DungeonDoorLocs.grubby_door_open, OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.slashWeb(loc: BoundLocInfo) {
        arriveDelay()
        val chance = webChance()
        if (chance == 0) {
            mes("Only a sharp blade can cut through this sticky web.")
            return
        }
        anim(DungeonSeqs.sword_slash)
        delay(1)
        if (random.of(100) < chance) {
            mes("You slash the web apart.")
            locRepo.del(loc, WEB_TICKS)
        } else {
            mes("You fail to cut through it.")
        }
    }

    /** The percent chance a slash gets through: the better of a knife and the wielded weapon. */
    private fun ProtectedAccess.webChance(): Int {
        val knife =
            player.inv.any { obj ->
                obj != null &&
                    (objTypes[obj].internalName ?: "").let {
                        it == "knife" || it.endsWith("_knife")
                    }
            }
        val slash = bonuses.offensiveSlashBonus(player)
        val weapon =
            when {
                slash >= 100 -> 100
                slash >= 20 -> slash
                slash >= 1 -> 20
                else -> 0
            }
        return maxOf(weapon, if (knife) 50 else 0)
    }

    private suspend fun ProtectedAccess.chopVine(loc: BoundLocInfo, level: Int) {
        arriveDelay()
        if (player.woodcuttingLvl < level) {
            mes("You need a Woodcutting level of $level to chop down these vines.")
            return
        }
        val axe = Woodcutting.findAxe(player, objTypes)
        if (axe == null) {
            mes("You need an axe to chop down these vines.")
            return
        }
        val swing = objTypes[axe].axeWoodcuttingAnim
        mes("You swing your axe at the vines.")
        repeat(MAX_SWINGS) {
            anim(swing)
            delay(2)
            if (random.of(100) < VINE_CHANCE) {
                mes("You chop down the vines.")
                locRepo.del(loc, VINE_TICKS)
                return
            }
        }
    }

    private companion object {
        /** A minute, in game ticks. */
        const val OPEN_TICKS = 100

        /** Swinging doors close again soon after, behind whoever went through. */
        const val DOOR_TICKS = 10
        const val WEB_TICKS = 100
        const val VINE_TICKS = 100
        const val GRUBBY_DOOR_LEVEL = 57
        const val GRUBBY_DOOR_XP = 10.0
        const val VINE_EASY = 10
        const val VINE_HARD = 34
        const val VINE_CHANCE = 40
        const val MAX_SWINGS = 8
    }
}
