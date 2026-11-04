package org.rsmod.content.areas.misc.isleofsouls.colosseum

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.npc.combatDefaultRetaliateAp
import org.rsmod.api.combat.manager.AttackRangeModifier
import org.rsmod.api.combat.manager.AttackRangeModifiers
import org.rsmod.api.combat.manager.ForcedMaxHit
import org.rsmod.api.combat.manager.ForcedMaxHits
import org.rsmod.api.combat.manager.PlayerAttackEvents
import org.rsmod.api.combat.manager.PlayerAttackKind
import org.rsmod.api.config.refs.queues
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.events.PlayerHitEvents
import org.rsmod.api.player.interact.WornClickHook
import org.rsmod.api.player.interact.WornClickHooks
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The arena's handlers: the tunnel in, Minimus's Start-wave and Leave, the intermission screen and
 * the rewards chest, the monsters' attacks and deaths, and the hooks the modifiers and Sol Heredit
 * need (Myopia's range, the warband's weaknesses and the perfect parry, the grapple's clicks, the
 * damage players take). The lobby is [ColosseumLobbyScript]; the run itself is [Colosseum].
 */
class ColosseumScript
@Inject
constructor(
    private val colosseum: Colosseum,
    private val instances: TeamInstances,
    private val enemies: ColosseumEnemies,
    private val sol: SolHeredit,
    private val effects: ColosseumEffects,
    private val screens: ColosseumScreens,
    private val world: ColosseumWorld,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
    private val seqTypes: SeqTypeList,
    private val interactions: AiPlayerInteractions,
    private val forcedMaxHits: ForcedMaxHits,
    private val rangeModifiers: AttackRangeModifiers,
    private val wornClicks: WornClickHooks,
    private val collectionLog: CollectionLog,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        instances.register(ColosseumInstance.type)
        colosseum.settings = EventSettings.load().colosseum
        logger.info { "Colosseum mode: ${colosseum.settings.mode.key}" }

        onEvent<GameLifecycle.StartCycle> { colosseum.tick() }
        onEvent<PlayerDeathEvents.Died> { colosseum.died(player) }
        onEvent<PlayerHitEvents.Impact> { colosseum.impact(player, damage, npcSource) }
        onEvent<PlayerAttackEvents.Hit> { attacked(source, target) }
        onPlayerLogout { colosseum.loggedOut(player) }

        onOpLoc1(colosseum_locs.tunnel) { tunnel() }
        onOpNpc1(colosseum_npcs.minimus) { colosseum.startWaveRequested(player) }
        onOpNpc3(colosseum_npcs.minimus) { askToLeave() }
        onOpLoc1(colosseum_locs.reward_chest) { searchChest() }
        onPlayerQueue(colosseum_queues.sol_greeting) { solGreeting() }

        val c = colosseum_components
        onIfModalButton(c.mod_button_1) { colosseum.selectModifier(player, 1) }
        onIfModalButton(c.mod_button_2) { colosseum.selectModifier(player, 2) }
        onIfModalButton(c.mod_button_3) { colosseum.selectModifier(player, 3) }
        onIfModalButton(c.confirm) { colosseum.confirm(player) }
        onIfModalButton(c.chest_bank) { takeAll(toBank = true) }
        onIfModalButton(c.chest_take) { takeAll(toBank = false) }
        onIfModalButton(c.chest_discard) { discardAll() }
        onIfModalButton(c.chest_items) { takeOne(it.comsub) }

        monsters()
        hooks()
    }

    // The monsters ------------------------------------------------------------------------------

    private fun ScriptContext.monsters() {
        val n = colosseum_npcs
        for (type in listOf(n.shaman, n.jaguar, n.javelin, n.manticore, n.shockwave, n.minotaur)) {
            onAiOpPlayer2(type) { fight(it.target) }
            onAiApPlayer2(type) { fight(it.target) }
        }
        for (type in listOf(n.shaman, n.javelin, n.manticore, n.shockwave)) {
            // They fight back by shooting, not by walking up to melee the player.
            onNpcQueue(type, queues.com_retaliate_player) {
                npc.combatDefaultRetaliateAp(interactions)
            }
        }
        // The warband, Red Flag's Minotaur and Sol Heredit are moved by the run, not by
        // retaliation.
        for (type in listOf(n.berserker, n.seer, n.archer, n.minotaur_routefind, n.sol)) {
            onNpcQueue(type, queues.com_retaliate_player) {}
        }
        onAiOpPlayer2(n.sol) { solAttack(it.target) }

        val deaths: List<Pair<org.rsmod.game.type.npc.NpcType, SeqType?>> =
            listOf(
                n.berserker to colosseum_seqs.berserker_death,
                n.seer to colosseum_seqs.seer_death,
                n.archer to colosseum_seqs.archer_death,
                n.shaman to colosseum_seqs.shaman_death,
                n.jaguar to colosseum_seqs.jaguar_death,
                n.javelin to colosseum_seqs.javelin_death,
                n.manticore to colosseum_seqs.manticore_death,
                n.shockwave to colosseum_seqs.shockwave_death,
                n.minotaur to colosseum_seqs.minotaur_death,
                n.minotaur_routefind to colosseum_seqs.minotaur_death,
                n.sol to colosseum_seqs.sol_death,
            )
        for ((type, anim) in deaths) {
            onNpcQueue(type, queues.death) { die(anim) }
        }
        // The modifiers' bees break up; totems are gone at once. Either may come back later.
        onNpcQueue(n.bees, queues.death) { die(colosseum_seqs.bees_despawn) }
        onNpcQueue(n.totem, queues.death) { vanish() }
    }

    private fun StandardNpcAccess.fight(target: Player) {
        val run = colosseum.runOf(npc) ?: return
        enemies.fight(run, npc, target)
    }

    private fun StandardNpcAccess.solAttack(target: Player) {
        val run = colosseum.runOf(npc) ?: return
        sol.attack(run, target)
    }

    private suspend fun StandardNpcAccess.die(anim: SeqType?) {
        colosseum.monsterDied(npc)
        walk(coords)
        noneMode()
        hideAllOps()
        arriveDelay()
        if (anim != null) {
            anim(anim)
            delay(anim, seqTypes)
        }
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun StandardNpcAccess.vanish() {
        noneMode()
        hideAllOps()
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    // The hooks ---------------------------------------------------------------------------------

    private fun hooks() {
        forcedMaxHits.register(
            ForcedMaxHit { source, target, kind ->
                val npc = target as? Npc ?: return@ForcedMaxHit false
                val run = colosseum.runOf(npc) ?: return@ForcedMaxHit false
                weakTo(run, npc) == kind || sol.parried(run, source, npc)
            }
        )
        rangeModifiers.register(
            AttackRangeModifier { player, _, range ->
                val run = colosseum.runOf(player)
                if (run == null) range else range - effects.rangeLoss(run)
            }
        )
        wornClicks.register(
            WornClickHook { player, slot ->
                val run = colosseum.runOf(player)
                run != null && sol.defend(run, player, slot)
            }
        )
    }

    /** The style each of the trio is weak to: it always hits it, for the attacker's max hit. */
    private fun weakTo(run: ColosseumRun, npc: Npc): PlayerAttackKind? =
        when (run.stateOf(npc)?.kind) {
            ColosseumMonster.Berserker -> PlayerAttackKind.Magic
            ColosseumMonster.Seer -> PlayerAttackKind.Ranged
            ColosseumMonster.Archer -> PlayerAttackKind.Melee
            else -> null
        }

    /** A perfect parry empowers one blow: the hit on Sol Heredit uses it up. */
    private fun attacked(source: Player, target: org.rsmod.game.entity.PathingEntity) {
        val npc = target as? Npc ?: return
        val run = colosseum.runOf(npc) ?: return
        if (npc === run.sol?.npc) {
            run.memberOf(source)?.maxHitUntil = -1
        }
    }

    // The tunnel and Minimus --------------------------------------------------------------------

    private suspend fun ProtectedAccess.tunnel() {
        if (player.vars[colosseum_varbits.master_intro] == 0) {
            minimusSays(
                "Oi! Where d'you think you're going? Come and speak to me before you go " +
                    "wondering through there. You'll get yourself killed!"
            )
            return
        }
        if (player.vars[colosseum_varbits.entry_warning] == 0) {
            mesbox(ENTRY_WARNING)
            val choice =
                choice3(
                    "Yes, I understand the risks.",
                    1,
                    "Yes, and don't ask again.",
                    2,
                    "No.",
                    3,
                    title = "Are you sure you wish to enter the Fortis Colosseum?",
                )
            when (choice) {
                1 -> {}
                2 -> vars[colosseum_varbits.entry_warning] = 1
                else -> return
            }
        }
        when (colosseum.enter(player)) {
            ColosseumEntry.Entered,
            ColosseumEntry.Rejoined -> {}
            ColosseumEntry.AlreadyInside -> mes("You are already in the Colosseum.")
            ColosseumEntry.WaveUnderway ->
                mes("Your team is fighting a wave. You can rejoin them when it is over.")
            ColosseumEntry.Barred -> mes("You have already left your team's run.")
            ColosseumEntry.NoSpace ->
                mes("There is no room for another Colosseum run right now. Try again.")
            ColosseumEntry.InvalidEntry -> mes("The Colosseum is not open.")
        }
    }

    private suspend fun ProtectedAccess.minimusSays(text: String) {
        startDialogue {
            chatNpcSpecific("Minimus", npcTypes[colosseum_npcs.minimus_lobby], neutral, text)
        }
    }

    private suspend fun ProtectedAccess.askToLeave() {
        when (colosseum.leaveKind(player)) {
            Colosseum.LeaveKind.None -> return
            Colosseum.LeaveKind.Leave -> {
                val yes =
                    choice2("Yes.", true, "No.", false, title = "Are you sure you wish to leave?")
                if (yes) {
                    colosseum.leave(player)
                }
            }
            Colosseum.LeaveKind.Forfeit -> {
                startDialogue {
                    chatNpcSpecific(
                        "Minimus",
                        npcTypes[colosseum_npcs.minimus],
                        neutral,
                        "Leaving now will forfeit your run, with you only being rewarded the " +
                            "items you have earned so far.",
                    )
                    val yes =
                        choice2(
                            "Yes.",
                            true,
                            "No.",
                            false,
                            title = "Are you sure you wish to leave early?",
                        )
                    if (!yes) {
                        chatPlayer(neutral, "On second thoughts, I'd better stay.")
                        return@startDialogue
                    }
                    colosseum.forfeit(player)
                    chatNpcSpecific(
                        "Minimus",
                        npcTypes[colosseum_npcs.minimus],
                        neutral,
                        "It was a valiant effort, ${colosseum.rankOf(player)}. I've popped all " +
                            "your earned rewards into that chest over there. You can retrieve " +
                            "them from it when ready.",
                    )
                }
            }
        }
    }

    private suspend fun ProtectedAccess.solGreeting() {
        val lines = colosseum.takeGreeting(player)
        if (lines.isEmpty()) {
            return
        }
        startDialogue {
            for (line in lines) {
                chatNpcSpecific("Sol Heredit", npcTypes[colosseum_npcs.sol_seated], neutral, line)
            }
        }
    }

    // The chest -----------------------------------------------------------------------------------

    private fun ProtectedAccess.chestMember(): ColosseumMember? {
        val run = colosseum.runOf(player) ?: return null
        if (run.chest == null) {
            return null
        }
        val member = run.memberOf(player) ?: return null
        val claiming = member.forfeited || run.stage == ColosseumStage.Finished
        return member.takeIf { claiming }
    }

    private fun ProtectedAccess.searchChest() {
        val member = chestMember()
        if (member == null) {
            mes("There is nothing in the chest for you.")
            return
        }
        if (member.rewards.isEmpty()) {
            mes("Your reward chest is empty.")
            return
        }
        screens.openChest(member)
    }

    private fun ProtectedAccess.takeAll(toBank: Boolean) {
        val member = chestMember() ?: return
        for (item in member.rewards.toList()) {
            if (!take(member, item, toBank)) {
                break
            }
        }
        screens.refreshChest(member)
    }

    private fun ProtectedAccess.takeOne(slot: Int) {
        val member = chestMember() ?: return
        val item = member.rewards.getOrNull(slot) ?: return
        take(member, item, toBank = false)
        screens.refreshChest(member)
    }

    /** Moves [item] out of the chest into the inventory or the bank. Returns `false` if full. */
    private fun ProtectedAccess.take(
        member: ColosseumMember,
        item: ColosseumItem,
        toBank: Boolean,
    ): Boolean {
        val into = if (toBank) bank else inv
        val added = invAdd(into, item.obj, item.count, strict = true).success
        if (!added) {
            mes(
                if (toBank) "You need more space in your bank."
                else "You don't have enough inventory space."
            )
            return false
        }
        member.rewards.remove(item)
        collectionLog.receive(player, item.obj.id, item.count)
        return true
    }

    private suspend fun ProtectedAccess.discardAll() {
        val member = chestMember() ?: return
        if (member.rewards.isEmpty()) {
            return
        }
        val yes =
            choice2(
                "Yes.",
                true,
                "Cancel.",
                false,
                title = "Are you sure you want to destroy the items?",
            )
        if (yes) {
            member.rewards.clear()
            screens.refreshChest(member)
        }
    }

    companion object {
        /**
         * The real warning, with its unsafe-death line changed: on the isle a death keeps your
         * items, but it does cost the rewards you have not claimed.
         */
        const val ENTRY_WARNING =
            "You are about to enter the Fortis Colosseum. Within, you will face multiple waves " +
                "of <col=b30000>deadly foes</col>. Dying, teleporting or logging out " +
                "<col=b30000>loses the rewards you have not claimed</col>. Apart from between " +
                "waves, there is no exit within the Colosseum, but teleporting out is allowed."
    }
}
