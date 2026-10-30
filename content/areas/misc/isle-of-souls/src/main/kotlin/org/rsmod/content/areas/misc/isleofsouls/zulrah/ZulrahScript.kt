package org.rsmod.content.areas.misc.isleofsouls.zulrah

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerInit
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Zulrah's boat and exit, the timers that run the fight and venom, and dismantling her uniques. The
 * fight itself is [Zulrah].
 */
class ZulrahScript
@Inject
constructor(
    private val zulrah: Zulrah,
    private val venom: PlayerVenom,
    private val completions: ZulrahCompletions,
    private val instances: TeamInstances,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val objRepo: ObjRepository,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        instances.register(ZulrahInstance.type)

        onGameStartup { placeBoat() }

        onOpLoc1(zulrah_locs.boat) { board() }
        onOpLoc1(zulrah_locs.exit) { leave() }

        onPlayerSoftTimer(zulrah_timers.tick) {
            if (!zulrah.tick(player)) {
                player.clearSoftTimer(zulrah_timers.tick)
            }
        }
        onPlayerSoftTimer(zulrah_timers.venom) {
            if (!venom.tick(player)) {
                player.clearSoftTimer(zulrah_timers.venom)
            }
        }
        onPlayerLogout { zulrah.sweep(except = player) }
        onPlayerInit { venom.resume(player) }
        onEvent<PlayerDeathEvents.Died> { venom.cure(player) }

        for (unique in DISMANTLED) {
            onOpHeld4(unique) { dismantle(it.inventory, it.slot, unique) }
        }
    }

    /** Puts the sacrificial boat on the lobby plaza. Runs when the server starts. */
    fun placeBoat() {
        locRepo.add(
            ZulrahArena.boat,
            locTypes[zulrah_locs.boat],
            duration = Int.MAX_VALUE,
            angle = LocAngle.West,
            shape = LocShape.CentrepieceStraight,
        )
        logger.info { "Zulrah's boat is at ${ZulrahArena.boat}" }
    }

    private fun ProtectedAccess.board() {
        when (zulrah.enter(player)) {
            EnterResult.Entered -> {}
            EnterResult.AlreadyInside -> mes("You are already in Zulrah's shrine.")
            EnterResult.NoSpace -> mes("There is no room for another shrine right now. Try again.")
            EnterResult.InvalidEntry -> mes("The boat will not sail.")
        }
    }

    private fun ProtectedAccess.leave() {
        zulrah.leave(player)
    }

    /** The wiki: a unique dismantles into 20,000 of Zulrah's scales. */
    private fun ProtectedAccess.dismantle(inventory: Inventory, slot: Int, type: ObjType) {
        if (invDel(inventory, type, 1, slot = slot).failure) {
            return
        }
        invAddOrDrop(objRepo, zulrah_objs.scales, SCALES_PER_UNIQUE)
        mes("You dismantle it and get $SCALES_PER_UNIQUE of Zulrah's scales.")
        completions.dismantled(player)
    }

    companion object {
        const val SCALES_PER_UNIQUE = 20_000

        /** The uniques that can be dismantled (their fourth option). */
        val DISMANTLED =
            listOf(
                zulrah_objs.tanzanite_fang,
                zulrah_objs.magic_fang,
                zulrah_objs.serpentine_visage,
            )
    }
}
