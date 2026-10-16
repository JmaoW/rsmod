package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.objs
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.events.interact.PlayerTEvents
import org.rsmod.api.player.hit.events.PlayerHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApPlayerT
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.skills.magic.utility.configs.lunar_seqs
import org.rsmod.content.skills.magic.utility.configs.lunar_spotanims
import org.rsmod.events.EventBus
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO: players hitting players. The isle has no player fighting, so only npcs are rebounded on.
//
// Vengeance (for the caster) and Vengeance Other (for a teammate) make the next hit the player
// takes rebound on whoever dealt it, for 75% of the damage taken (at least 1). A caster can cast
// either only once every 50 ticks, and the effect ends at logout. The spells themselves take no
// time. Poison and the like do not rebound; nothing on the isle poisons a player.
class Vengeance
@Inject
constructor(
    private val caster: SpellCaster,
    private val teams: SpellTeams,
    private val modifier: NpcHitModifier,
    private val eventBus: EventBus,
) : PluginScript() {
    /** The players who will rebound their next hit. */
    private val active = WeakHashMap<Player, Boolean>()

    /** The cycle each caster can next cast a vengeance spell. */
    private val ready = WeakHashMap<Player, Int>()

    fun isActive(player: Player): Boolean = active[player] == true

    override fun ScriptContext.startup() {
        with(caster) {
            onSpell(objs.spell_vengeance) { castOnSelf() }
            onApPlayerT(spell(objs.spell_vengeance_other).component) { castOnOther(it) }
        }
        onEvent<PlayerHitEvents.Impact> { rebound(this) }
        onPlayerLogout {
            active.remove(player)
            ready.remove(player)
        }
    }

    private fun ProtectedAccess.cast(spell: ObjType): Boolean {
        if (mapClock < (ready[player] ?: Int.MIN_VALUE)) {
            mes("You can only cast vengeance spells once every 30 seconds.")
            return false
        }
        if (with(caster) { cast(spell, 0) } == null) {
            return false
        }
        ready[player] = mapClock + COOLDOWN
        return true
    }

    private fun ProtectedAccess.castOnSelf() {
        if (!cast(objs.spell_vengeance)) {
            return
        }
        active[player] = true
        anim(lunar_seqs.cast_self)
        spotanim(lunar_spotanims.vengeance, height = SPOTANIM_HEIGHT)
        publish(Granted(player, player))
    }

    private fun ProtectedAccess.castOnOther(event: PlayerTEvents.Ap) {
        val target = event.target
        if (!teams.areTeammates(player, target)) {
            mes("You can only cast that on a member of your own team.")
            return
        }
        if (!cast(objs.spell_vengeance_other)) {
            return
        }
        active[target] = true
        target.mes("You have the power of vengeance!")
        anim(lunar_seqs.cast_self)
        target.spotanim(lunar_spotanims.vengeance_other, height = SPOTANIM_HEIGHT)
        publish(Granted(player, target))
    }

    private fun rebound(hit: PlayerHitEvents.Impact) {
        val player = hit.player
        val source = hit.npcSource ?: return
        if (active[player] != true || hit.damage <= 0) {
            return
        }
        active.remove(player)
        val damage = maxOf(1, hit.damage * REBOUND_PERCENT / PERCENT)
        player.say("Taste vengeance!")
        source.queueHit(
            player,
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            modifier = modifier,
        )
        eventBus.publish(Rebounded(player, source, damage))
    }

    /** Published once [player]'s vengeance has rebounded [damage] on [npc]. */
    class Rebounded(val player: Player, val npc: Npc, val damage: Int) : UnboundEvent

    /**
     * Published once [caster] has given [target] (who may be the caster) the power of vengeance.
     */
    class Granted(val caster: Player, val target: Player) : UnboundEvent

    private companion object {
        const val COOLDOWN = 50
        const val REBOUND_PERCENT = 75
        const val PERCENT = 100
        const val SPOTANIM_HEIGHT = 92
    }
}
