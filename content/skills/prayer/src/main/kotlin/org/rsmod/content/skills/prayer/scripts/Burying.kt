package org.rsmod.content.skills.prayer.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.prayer.configs.PrayerBone
import org.rsmod.content.skills.prayer.configs.PrayerBones
import org.rsmod.content.skills.prayer.configs.PrayerQueues
import org.rsmod.content.skills.prayer.configs.PrayerSeqs
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the burying sound (there is no named synth for it in the symbols yet)
// - scattering ashes (fiendish, vile, malicious, abyssal and infernal) once something drops them
// - the bonecrusher and ensouled heads
//
// The bone is used up and the experience given when the option is clicked, and the second chat
// message follows a tick later from a weak queue. A bone cannot be buried again until
// [BURY_INTERVAL] ticks have passed, which is kept in a per-player cooldown rather than by holding
// the player with a delay, so they can walk away or click something else at any time. The wiki
// does not give the interval, so it is a choice, not a copy.
class Burying
@Inject
constructor(private val objTypes: ObjTypeList, private val xpMods: XpModifiers) : PluginScript() {
    /** The cycle each player can next bury a bone. */
    private val ready = WeakHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        for (bone in PrayerBones.all) {
            onOpHeld1(bone.obj) { bury(it, bone) }
        }
        onPlayerQueueWithArgs<String>(PrayerQueues.bury_message) { mes("You bury the ${it.args}.") }
    }

    private fun ProtectedAccess.bury(event: HeldObjEvents.Op1, bone: PrayerBone) {
        if (mapClock < (ready[player] ?: Int.MIN_VALUE)) {
            return
        }
        if (invDel(event.inventory, bone.obj, 1, slot = event.slot).failure) {
            return
        }
        ready[player] = mapClock + BURY_INTERVAL

        val xp = bone.xp * xpMods.get(player, stats.prayer)
        statAdvance(stats.prayer, xp)
        publish(BoneBuried(player, bone.obj))

        val name = objTypes[bone.obj].name.lowercase()
        anim(PrayerSeqs.bury)
        mes("You dig a hole in the ground...")
        weakQueue(PrayerQueues.bury_message, 1, name)
    }

    /** Published once a bone has been buried. */
    class BoneBuried(val player: Player, val bone: ObjType) : UnboundEvent

    private companion object {
        /** Ticks between one bone being buried and the next. */
        const val BURY_INTERVAL = 2
    }
}
