package org.rsmod.api.npc.hit.modifier

import jakarta.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.min
import org.rsmod.api.config.refs.varns
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitBuilder

public class StandardNpcHitModifier
@Inject
constructor(private val eventBus: EventBus, private val players: PlayerList) : NpcHitModifier {
    override fun HitBuilder.modify(target: Npc) {
        target.publishEvent(this)
        publishAny(target)
        target.applyFlatArmour(this)
    }

    private fun HitBuilder.publishAny(target: Npc) {
        val uid = sourceUid
        val source = if (isFromPlayer && uid != null) PlayerUid(uid).resolve(players) else null
        eventBus.publish(NpcHitEvents.ModifyAny(target, this, source))
    }

    private fun Npc.publishEvent(hit: HitBuilder) {
        val event = NpcHitEvents.Modify(this, hit)
        eventBus.publish(event)
    }

    private fun Npc.applyFlatArmour(hit: HitBuilder) {
        val armour = vars[varns.flat_armour]

        if (armour > 0) {
            val capped = min(armour.absoluteValue, hit.damage)
            vars[varns.flat_armour] -= capped
            hit.damage -= capped
        }

        if (armour < 0) {
            vars[varns.flat_armour] = 0
            hit.damage += armour.absoluteValue
        }
    }
}
