package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.skills.magic.utility.configs.utility_objs
import org.rsmod.content.skills.magic.utility.configs.utility_seqs
import org.rsmod.content.skills.magic.utility.configs.utility_spotanims
import org.rsmod.content.skills.magic.utility.configs.utility_timers
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the warning prompt for items worth a lot; the wiki says it is a setting, off by default here
// - the switch to the inventory tab that the client makes after an alchemy cast
//
// Low Level Alchemy turns one item into 40% of its value in coins, High Level Alchemy 60%. Noted
// items work and are worth what the item is. Either spell takes 5 ticks.
//
// [AlchemyModifiers] (the Golden God relic) can make the spells free, pay more, sometimes keep the
// item, and carry on casting by themselves on a stack. That repeat is a timer rather than a loop
// inside one cast, so the player stays free to walk and fight while it runs; it ends when the stack
// is gone, the item is moved, or the player casts on another item.
class Alchemy
@Inject
constructor(
    private val caster: SpellCaster,
    private val objTypes: ObjTypeList,
    private val modifiers: AlchemyModifiers,
    private val random: GameRandom,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    /** The cast a player's timer repeats: [slot] is where the stack was, [obj] what it holds. */
    private class Repeat(val slot: Int, val obj: UnpackedObjType, val low: Boolean)

    private val repeats = WeakHashMap<Player, Repeat>()

    override fun ScriptContext.startup() {
        with(caster) {
            onItemSpell(objs.spell_low_alchemy) { alchemise(it, low = true) }
            onItemSpell(objs.spell_high_alchemy) { alchemise(it, low = false) }
        }
        onPlayerSoftTimer(utility_timers.alchemy_repeat) { repeat(player) }
        onPlayerLogout { stopRepeating(player) }
    }

    private fun ProtectedAccess.alchemise(target: SpellTarget, low: Boolean): Boolean {
        val obj = target.obj
        val spell = if (low) objs.spell_low_alchemy else objs.spell_high_alchemy
        val coins = value(obj, low, modifiers.coinPercent(player))
        if (obj.id == utility_objs.coins.id) {
            mes("You cannot cast this spell on coins.")
            return false
        }
        if (coins <= 0) {
            mes("You cannot cast this spell on that item.")
            return false
        }
        if (player.inv[target.slot]?.id != obj.id) {
            return false
        }

        val free = modifiers.isFree(player)
        with(caster) { cast(spell, COOLDOWN, free) } ?: return false
        val kept = random.of(PERCENT) < modifiers.keepChance(player)
        if (!kept) {
            invDel(inv, obj, 1, slot = target.slot)
        }
        invAdd(inv, utility_objs.coins, coins)

        anim(if (low) utility_seqs.cast_low_alchemy else utility_seqs.cast_high_alchemy)
        spotanim(
            if (low) utility_spotanims.low_alchemy else utility_spotanims.high_alchemy,
            height = SPOTANIM_HEIGHT,
        )
        soundSynth(if (low) synths.low_alchemy else synths.high_alchemy)
        publish(Alchemised(player, obj.id, coins, low))

        if (obj.isStackable && modifiers.autoRecast(player)) {
            startRepeating(player, Repeat(target.slot, obj, low))
        } else {
            stopRepeating(player)
        }
        return true
    }

    private fun startRepeating(player: Player, repeat: Repeat) {
        val running = player in repeats
        repeats[player] = repeat
        if (!running) {
            player.softTimer(utility_timers.alchemy_repeat, COOLDOWN)
        }
    }

    private fun stopRepeating(player: Player) {
        if (repeats.remove(player) != null) {
            player.clearSoftTimer(utility_timers.alchemy_repeat)
        }
    }

    private fun repeat(player: Player) {
        val repeat = repeats[player] ?: return stopRepeating(player)
        val stillThere = player.inv[repeat.slot]?.id == repeat.obj.id
        if (!stillThere || !modifiers.autoRecast(player)) {
            return stopRepeating(player)
        }
        if (caster.isCooling(player, player.currentMapClock)) {
            return
        }
        // A busy player (a dialogue, an open shop) misses a turn and is tried again next time.
        protectedAccess.launch(player) {
            val target = SpellTarget(repeat.slot, repeat.obj)
            if (!alchemise(target, repeat.low)) {
                stopRepeating(player)
            }
        }
    }

    /** What the spell pays for [obj]. A noted item is worth what the item it stands for is. */
    fun value(obj: UnpackedObjType, low: Boolean, coinPercent: Int = PERCENT): Int {
        val base = (if (obj.isCert) objTypes[obj.certlink] else null) ?: obj
        val percent = if (low) LOW_PERCENT else HIGH_PERCENT
        return base.cost * percent * coinPercent / (PERCENT * PERCENT)
    }

    /** Published once [obj] has been turned into [coins]. */
    class Alchemised(val player: Player, val obj: Int, val coins: Int, val low: Boolean) :
        UnboundEvent

    private companion object {
        const val COOLDOWN = 5
        const val LOW_PERCENT = 40
        const val HIGH_PERCENT = 60
        const val PERCENT = 100
        const val SPOTANIM_HEIGHT = 92
    }
}
