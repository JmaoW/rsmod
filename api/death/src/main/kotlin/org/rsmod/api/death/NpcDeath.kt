package org.rsmod.api.death

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.varns
import org.rsmod.api.config.refs.varps
import org.rsmod.api.drops.DropResult
import org.rsmod.api.drops.DropRoller
import org.rsmod.api.drops.DropTables
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.map.CoordGrid

@Singleton
public class NpcDeath
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val seqTypes: SeqTypeList,
    private val players: PlayerList,
    private val objRepo: ObjRepository,
    private val dropTables: DropTables,
    private val dropRoller: DropRoller,
    private val eventBus: EventBus,
) {
    private var lootEventId: Int = 0

    public suspend fun deathNoDrops(access: StandardNpcAccess) {
        access.death(npcRepo, seqTypes, players)
    }

    public suspend fun deathWithDrops(
        access: StandardNpcAccess,
        dropCoords: CoordGrid = access.coords,
    ) {
        access.death(npcRepo, seqTypes, players)
        access.npc.spawnDeathDrops(dropCoords)
    }

    private fun Npc.spawnDeathDrops(dropCoords: CoordGrid) {
        val hero = findHero(players)
        eventBus.publish(NpcDeathEvents.Killed(this, hero))
        if (hero == null) {
            return
        }

        val duration = hero.lootDropDuration ?: constants.lootdrop_duration
        val table = dropTables[type]
        if (table == null) {
            // Monsters without a drop table keep dropping bones only. Subscribers may still change
            // what is dropped (a minigame that replaces the drops of its own monsters).
            val bones = listOf(DropResult(objs.bones, 1))
            val claim = NpcDropEvents.Claim(this, hero, bones.toMutableList())
            eventBus.publish(claim)
            for (drop in claim.drops) {
                objRepo.add(drop.obj, dropCoords, duration, hero, count = drop.count)
            }
            sendLootTracker(hero, this, claim.drops)
            return
        }

        val modify = NpcDropEvents.Modify(this, hero)
        eventBus.publish(modify)

        val rolled = modify.variant?.let { dropTables.variantOf(type, it) } ?: table
        val roll =
            dropRoller.roll(rolled, modify.multiplier, modify.chanceMultipliers).map {
                it.copy(count = modify.countOf(it))
            }
        val drops =
            roll + modify.guaranteed.filter { extra -> roll.none { it.obj.id == extra.obj.id } }
        val claim = NpcDropEvents.Claim(this, hero, drops.toMutableList())
        eventBus.publish(claim)
        for (drop in claim.drops) {
            objRepo.add(drop.obj, dropCoords, duration, hero, count = drop.count)
        }
        sendLootTracker(hero, this, drops)
        eventBus.publish(NpcDropEvents.Rolled(this, hero, drops))
    }

    /**
     * RuneLite's loot tracker ignores ground items for npc kills. It listens for the game's own
     * loot tracker script (`LOOTTRACKER_ADD_LOOT`, 7192), which the game server runs once per item
     * with `(npc id, kill id, item id, count)`. Entries that share a kill id are grouped into one
     * kill.
     */
    /**
     * Sends [drops] that [hero] received from [npc] to the loot tracker. Any drop path that does
     * not go through [spawnDrops] or [deathWithDrops] (custom drop rolls) must call this itself.
     */
    public fun sendLootTracker(hero: Player, npc: Npc, drops: List<DropResult>) {
        if (drops.isEmpty()) {
            return
        }
        lootEventId = (lootEventId + 1) and Int.MAX_VALUE
        for (drop in drops) {
            hero.runClientScript(
                LOOT_TRACKER_ADD_LOOT,
                npc.type.id,
                lootEventId,
                drop.obj.id,
                drop.count,
            )
        }
    }

    // Note: We may be able to have `Npc` as the arg instead of `StandardNpcAccess`, however we
    // will need to wait and see how [spawnDeathDrops] ends up once it handles everything it needs
    // to.
    public fun spawnDrops(access: StandardNpcAccess, dropCoords: CoordGrid = access.coords) {
        access.npc.spawnDeathDrops(dropCoords)
    }
}

private const val LOOT_TRACKER_ADD_LOOT: Int = 7192

private const val UNKNOWN_DEATH_ANIM_TICKS: Int = 4

private var Player.lastCombat: Int by intVarp(varps.lastcombat)
private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp(varps.aggressive_npc)
private var Npc.aggressivePlayer by typePlayerUidVarn(varns.aggressive_player)

/**
 * Handles the death sequence of this [StandardNpcAccess.npc], including clearing interactions and
 * removing (or hiding, if it respawns) the npc from the world.
 *
 * **Notes:**
 * - This is **not** the way to "kill" a npc. This "death sequence" occurs after the npc has already
 *   been deemed dead and its death queue is being processed.
 * - To queue a npc's death, use [StandardNpcAccess.queueDeath] or [org.rsmod.api.npc.queueDeath]
 *   instead.
 * - This function **does not** spawn any drop table objs for the npc.
 * - Drop table spawns are handled via [NpcDeath.deathWithDrops], which is **automatically called**
 *   for queued deaths by default. However, if you override death queues for specific npc types
 *   (`onNpcQueue(npc_type, queues.death)`), you must explicitly handle drop spawns in the script by
 *   injecting `NpcDeath` and calling either [NpcDeath.deathWithDrops] or [NpcDeath.spawnDrops].
 */
public suspend fun StandardNpcAccess.death(
    npcRepo: NpcRepository,
    seqTypes: SeqTypeList,
    players: PlayerList,
) {
    walk(coords)
    noneMode()
    hideAllOps()
    arriveDelay()

    val aggressivePlayer = npc.aggressivePlayer
    if (aggressivePlayer != null) {
        val player = aggressivePlayer.resolve(players)

        val deathSound = paramOrNull(params.death_sound)
        if (deathSound != null && player != null) {
            player.soundSynth(deathSound)
        }

        // TODO(combat): Should we assert that npc.uid will always match player.aggressiveNpc at
        // this point?

        if (player != null && player.aggressiveNpc == npc.uid) {
            player.lastCombat = 0
        }
    }

    val deathAnim = param(params.death_anim)
    anim(deathAnim)
    // A keyframe death anim with no range in the cache has no length the server can read.
    delay(seqTypes[deathAnim].tickDuration.takeIf { it > 0 } ?: UNKNOWN_DEATH_ANIM_TICKS)

    if (npc.respawns) {
        npcRepo.despawn(npc, npcRepo.respawnSpeed.scale(npc.type.respawnRate))
        return
    }

    npcRepo.del(npc, Int.MAX_VALUE)
}
