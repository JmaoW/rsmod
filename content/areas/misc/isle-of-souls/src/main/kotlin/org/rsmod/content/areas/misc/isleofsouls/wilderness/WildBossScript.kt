package org.rsmod.content.areas.misc.isleofsouls.wilderness

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.queues
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onNpcTimer
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.spells.attack.SpellAccuracyOverrides
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Wilderness bosses' doors and lives: the seven cave mouths in the Revenant Caves, the lairs'
 * exits, the Escape Caves hub, and the events that run a boss (its timer, its hits, its death and
 * its respawn). The fights themselves are [WildBosses]; the loot is [WildRewards].
 *
 * The real game asks for a 50,000 coin fee, a Wilderness diary or a Slayer task and drops the fee
 * on a death in the Wilderness. The isle has no Wilderness rules, so none of that is here.
 */
class WildBossScript
@Inject
constructor(
    private val bosses: WildBosses,
    private val rewards: WildRewards,
    private val npcDeath: NpcDeath,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
    private val seqTypes: SeqTypeList,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val players: PlayerList,
    private val attacks: NpcAttacks,
    private val protectedAccess: ProtectedAccessLauncher,
    private val spellOverrides: SpellAccuracyOverrides,
    private val wornBonuses: WornBonuses,
) : PluginScript() {
    private val bossTypes: List<NpcType>
        get() =
            listOf(
                wild_npcs.callisto,
                wild_npcs.artio,
                wild_npcs.venenatis,
                wild_npcs.spindel,
                wild_npcs.vetion,
                wild_npcs.vetion_enraged,
                wild_npcs.calvarion,
                wild_npcs.calvarion_enraged,
            )

    private val minionTypes: List<NpcType>
        get() =
            listOf(
                wild_npcs.vetion_hound,
                wild_npcs.vetion_hound_greater,
                wild_npcs.calvarion_hound,
                wild_npcs.calvarion_hound_greater,
                wild_npcs.venenatis_spiderling,
                wild_npcs.spindel_spiderling,
            )

    override fun ScriptContext.startup() {
        onGameStartup {
            placeEntrances()
            bosses.spawnAll()
            spawnCaveCreatures()
        }

        // The first spell that would freeze Callisto or Artio always lands, if the caster's
        // magic attack bonus is 0 or more (wiki); the ones after it have to hit.
        val freezing =
            listOf(
                    objs.spell_bind,
                    objs.spell_snare,
                    objs.spell_entangle,
                    objs.spell_ice_rush,
                    objs.spell_ice_burst,
                    objs.spell_ice_blitz,
                    objs.spell_ice_barrage,
                )
                .map { it.id }
                .toSet()
        spellOverrides.register { player, target, spell ->
            target is Npc &&
                (target.visType.id == wild_npcs.callisto.id ||
                    target.visType.id == wild_npcs.artio.id) &&
                spell.id in freezing &&
                !target.isFrozen &&
                !bosses.freezeImmunity.isImmune(target, bosses.now) &&
                wornBonuses.offensiveMagicBonus(player) >= 0
        }

        // The bosses ----------------------------------------------------------------------
        for (type in bossTypes) {
            onNpcTimer(type, wild_timers.tick) { bosses.tick(npc) }
            // They never turn on, or walk towards, a player who hits them: the fight moves them.
            onNpcQueue(type, queues.com_retaliate_player) {}
            onNpcQueue(type, queues.death) { die() }
            onNpcHit(type) {
                val fight = bosses.fightOf(npc) ?: return@onNpcHit
                val attacker = if (hit.isFromPlayer) hit.resolvePlayerSource(players) else null
                fight.engaged = true
                bosses.logicOf(fight).hurt(fight, attacker, hit.damage)
            }
            onModifyNpcHit(type) {
                val fight = bosses.fightOf(npc) ?: return@onModifyNpcHit
                bosses.logicOf(fight).modifyHit(fight, hit)
            }
        }
        onEvent<NpcStateEvents.Respawn> { bosses.respawned(npc) }

        // Their minions die without drops and bite with the fight's rules.
        for (type in minionTypes) {
            onNpcQueue(type, queues.death) { npcDeath.deathNoDrops(this) }
        }
        for (type in listOf(wild_npcs.venenatis_spiderling, wild_npcs.spindel_spiderling)) {
            onAiOpPlayer2(type) { spiderlingBite(it.target) }
        }

        // The Escape Caves' creatures ------------------------------------------------------
        for (type in listOf(wild_npcs.cave_bear, wild_npcs.cave_spider)) {
            onNpcTimer(type, wild_timers.tick) { caveTick(npc) }
            onNpcQueue(type, queues.death) { npcDeath.deathNoDrops(this) }
        }
        onAiOpPlayer2(wild_npcs.cave_bear) { caveAttack(it.target, BEAR_RATE) }
        onAiOpPlayer2(wild_npcs.cave_spider) { caveAttack(it.target, SPIDER_RATE) }

        // The doors ------------------------------------------------------------------------
        for (lair in WildLair.entries) {
            val type = lair.entrance.type
            onOpLoc1(type) { enter(lair) }
            onOpLoc2(type) { peek(lair) }
            onOpLoc3(type) { mes("There is no entry fee here.") }
        }
        for (type in
            listOf(wild_locs.callisto_exit, wild_locs.venenatis_exit, wild_locs.vetion_exit)) {
            onOpLoc1(type) { leave() }
        }
        onOpLoc1(wild_locs.escape_entrance) { enterCaves() }
        onOpLoc2(wild_locs.escape_entrance) { mes("There is no entry fee here.") }
        for (type in
            listOf(
                wild_locs.escape_exit_south,
                wild_locs.escape_exit_side,
                wild_locs.escape_exit_centre,
            )) {
            onOpLoc1(type) { leaveCaves() }
        }
        onPlayerLogout { clearSinglesPlus(player) }

        onCommand("bashfx") {
            modLevel = modlevels.admin
            desc = "Try graphics for Vet'ion's bash"
            invalidArgs = "Use as ::bashfx shadow|dust spotanimId, or ::bashfx reset"
            cheat {
                val what = args.getOrNull(0)
                val id = args.getOrNull(1)?.toIntOrNull()
                when {
                    what == "reset" -> {
                        bosses.bashShadowId = -1
                        bosses.bashDustId = -1
                        player.mes("Bash graphics back to the configured ones")
                    }
                    id == null ->
                        player.mes("Use as ::bashfx shadow|dust spotanimId, or ::bashfx reset")
                    what == "shadow" -> bosses.bashShadowId = id
                    what == "dust" -> bosses.bashDustId = id
                    else -> player.mes("Use as ::bashfx shadow|dust spotanimId, or ::bashfx reset")
                }
            }
        }

        onCommand("bossanim") {
            modLevel = modlevels.admin
            desc = "Play a seq on the Wilderness boss whose lair you are in"
            invalidArgs = "Use as ::bossanim seqId (e.g. 9969 magic, 9971 sword, 9973 parry)"
            cheat {
                val id = args.getOrNull(0)?.toIntOrNull()
                val lair = WildLair.entries.firstOrNull { it.contains(player.coords) }
                val boss = lair?.let { bosses.bossOf(it) }
                val seq = id?.let { seqTypes[it] }
                if (seq == null || boss == null) {
                    player.mes("Stand in a boss lair: ::bossanim seqId")
                } else {
                    boss.anim(seq)
                    player.mes("${boss.visType.name} plays seq $id")
                }
            }
        }

        onCommand("wildlair") {
            modLevel = modlevels.admin
            desc = "Teleport into a Wilderness boss lair"
            invalidArgs =
                "Use as ::wildlair key (callisto, hunters, silk, web, vetion, tomb, caves)"
            cheat {
                val key = args.getOrNull(0)
                val lair = key?.let { WildLair.byKey(it) }
                if (key == "caves") {
                    protectedAccess.launch(player) { telejump(EscapeCaves.fronts.first()) }
                } else if (lair == null) {
                    player.mes(WildLair.entries.joinToString(", ") { it.key } + ", caves")
                } else {
                    protectedAccess.launch(player) {
                        telejump(lair.arrival)
                        afterEntering(lair)
                    }
                }
            }
        }
    }

    // The doors -------------------------------------------------------------------------------

    /** Puts the seven cave mouths in the Revenant Caves. */
    fun placeEntrances() {
        val entrances = WildLair.entries.map { it.entrance } + EscapeCaves.entrance
        for (entrance in entrances) {
            locRepo.add(
                entrance.sw,
                locTypes[entrance.type],
                duration = Int.MAX_VALUE,
                angle = entrance.angle,
                shape = LocShape.CentrepieceStraight,
            )
        }
    }

    private suspend fun ProtectedAccess.enter(lair: WildLair) {
        arriveDelay()
        telejump(lair.arrival)
        afterEntering(lair)
    }

    private suspend fun ProtectedAccess.afterEntering(lair: WildLair) {
        mes("You enter ${lair.title}.")
        if (!lair.multi) {
            // The singles lairs are multi-combat to the engine (so everyone can hit the boss) but
            // show the singles-plus icon.
            delay(2)
            setIndicators(player, singlesPlus = true)
        } else {
            setIndicators(player, singlesPlus = false)
        }
    }

    private fun ProtectedAccess.peek(lair: WildLair) {
        val count = bosses.countIn(lair)
        if (count == 0) {
            mes("You peek inside ${lair.title}. Nobody is in there.")
        } else {
            mes(
                "You peek inside ${lair.title}. There ${if (count == 1) "is 1 adventurer" else "are $count adventurers"} in there."
            )
        }
    }

    private suspend fun ProtectedAccess.leave() {
        arriveDelay()
        val lair = WildLair.containing(player.coords)
        if (lair == null) {
            mes("Nothing interesting happens.")
            return
        }
        clearSinglesPlus(player)
        // A big lair opens into its own mouth in the Escape Caves; a small one onto the cave
        // mouth in the Revenant Caves it was entered by.
        telejump(EscapeCaves.arrivalOf(lair) ?: lair.entrance.front)
    }

    private suspend fun ProtectedAccess.enterCaves() {
        arriveDelay()
        telejump(EscapeCaves.fronts[bosses.random.of(EscapeCaves.fronts.size)])
        mes("You enter the Escape Caves.")
    }

    private suspend fun ProtectedAccess.leaveCaves() {
        arriveDelay()
        telejump(EscapeCaves.entrance.front)
    }

    private fun setIndicators(player: Player, singlesPlus: Boolean) {
        VarPlayerIntMapSetter.set(
            player,
            varbits.singleway_plus_indicator,
            if (singlesPlus) 1 else 0,
        )
        if (singlesPlus) {
            VarPlayerIntMapSetter.set(player, varbits.multiway_indicator, 0)
        }
    }

    private fun clearSinglesPlus(player: Player) {
        VarPlayerIntMapSetter.set(player, varbits.singleway_plus_indicator, 0)
    }

    // Death -----------------------------------------------------------------------------------

    /** The boss fell: a form ends, or it dies, leaves its loot by damage and comes back later. */
    private suspend fun StandardNpcAccess.die() {
        // A second death queued by a hit that was already flying lands after a form change.
        if (npc.hitpoints > 0) {
            return
        }
        val fight = bosses.fightOf(npc)
        if (fight == null) {
            npcDeath.deathWithDrops(this)
            return
        }
        val logic = bosses.logicOf(fight)
        if (logic.interceptDeath(fight)) {
            return
        }
        fight.dead = true
        logic.died(fight)
        val dropAt = bosses.centre(npc)
        walk(coords)
        noneMode()
        hideAllOps()
        arriveDelay()
        val seq = deathSeq(npc.visType.id)
        anim(seq)
        delay(seqTypes[seq].tickDuration.takeIf { it > 0 } ?: DEATH_TICKS)
        rewards.award(npc, dropAt)
        npcRepo.despawn(npc, npcRepo.respawnSpeed.scale(npc.type.respawnRate))
    }

    private fun deathSeq(visTypeId: Int): SeqType =
        when (visTypeId) {
            wild_npcs.callisto.id,
            wild_npcs.artio.id -> wild_seqs.callisto_death
            wild_npcs.venenatis.id,
            wild_npcs.spindel.id -> wild_seqs.venenatis_death
            wild_npcs.vetion_enraged.id,
            wild_npcs.calvarion_enraged.id -> wild_seqs.vetion_death_enraged
            else -> wild_seqs.vetion_death
        }

    // The minions and the cave creatures ------------------------------------------------------

    /** A spiderling's bite: a tiny stab, and a prayer point gone whether it hits or not. */
    private fun StandardNpcAccess.spiderlingBite(target: Player) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        attacks.attack(
            npc,
            target,
            NpcAttack(style = NpcAttackStyle.Melee, maxHit = SPIDERLING_MAX_HIT),
        )
        bosses.hits.drainPrayer(target, 1)
        actionDelay = mapClock + SPIDERLING_RATE
    }

    fun spawnCaveCreatures() {
        for ((index, spot) in CAVE_SPAWNS.withIndex()) {
            val type = if (index % 2 == 0) wild_npcs.cave_bear else wild_npcs.cave_spider
            val npc = Npc(npcTypes[type], spot)
            npcRepo.add(npc, Int.MAX_VALUE)
            npc.timer(wild_timers.tick, 1)
        }
    }

    /** A cave creature goes for the nearest player in the caves and never gives up. */
    private fun caveTick(npc: Npc) {
        if (!npc.isValidTarget() || npc.hitpoints <= 0) {
            return
        }
        val nearest =
            players
                .filter { it.isValidTarget() && EscapeCaves.area.contains(it.coords) }
                .minByOrNull { npc.distanceTo(it) } ?: return
        if (npc.distanceTo(nearest) <= CAVE_AGGRO) {
            bosses.attackTarget(npc, nearest)
        }
    }

    /** Their hits are typeless: no prayer helps against them (wiki). */
    private fun StandardNpcAccess.caveAttack(target: Player, rate: Int) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        npc.anim(npc.visType.param(params.attack_anim))
        bosses.hits.typeless(npc, target, bosses.random.of(0..CAVE_MAX_HIT), 1)
        actionDelay = mapClock + rate
    }

    private companion object {
        const val DEATH_TICKS = 6
        const val SPIDERLING_MAX_HIT = 3
        const val SPIDERLING_RATE = 4

        /** The wiki: bears hit up to 8 every 4 ticks, spiders up to 8 every 5. */
        const val CAVE_MAX_HIT = 8
        const val BEAR_RATE = 4
        const val SPIDER_RATE = 5
        const val CAVE_AGGRO = 10

        /**
         * Spread over the caves' floor (found with the collision tools); bears and spiders
         * alternate.
         */
        val CAVE_SPAWNS =
            listOf(
                CoordGrid(3349, 10268, 0),
                CoordGrid(3381, 10279, 0),
                CoordGrid(3346, 10291, 0),
                CoordGrid(3373, 10256, 0),
                CoordGrid(3376, 10291, 0),
                CoordGrid(3359, 10286, 0),
                CoordGrid(3334, 10268, 0),
                CoordGrid(3362, 10276, 0),
                CoordGrid(3362, 10295, 0),
                CoordGrid(3338, 10259, 0),
                CoordGrid(3382, 10267, 0),
                CoordGrid(3338, 10282, 0),
                CoordGrid(3357, 10250, 0),
                CoordGrid(3365, 10267, 0),
                CoordGrid(3347, 10250, 0),
            )
    }
}
