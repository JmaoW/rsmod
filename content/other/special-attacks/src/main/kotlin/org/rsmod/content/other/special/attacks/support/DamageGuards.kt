package org.rsmod.content.other.special.attacks.support

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifiers
import org.rsmod.api.player.righthand
import org.rsmod.api.script.onEvent
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The damage a player takes less of for a while because of a special attack they made: Power of
 * Death halves melee damage for a minute for as long as the staff stays wielded, and Spear Wall
 * makes the player immune to melee for a few seconds.
 */
@Singleton
class DamageGuards @Inject constructor() {
    private val meleeImmuneUntil = WeakHashMap<Player, Int>()
    private val powerOfDeathUntil = WeakHashMap<Player, Int>()
    private val vulnerable = WeakHashMap<PathingEntity, Vulnerability>()

    private class Vulnerability(val until: Int, val percent: Int)

    /** Makes [player] immune to melee damage for [ticks]. */
    fun immuneToMelee(player: Player, ticks: Int) {
        meleeImmuneUntil[player] = player.currentMapClock + ticks
    }

    /** Halves the melee damage [player] takes for [ticks], while a Power of Death staff is held. */
    fun powerOfDeath(player: Player, ticks: Int) {
        powerOfDeathUntil[player] = player.currentMapClock + ticks
    }

    /** Makes [target] take [percent] percent more damage from every hit for [ticks]. */
    fun makeVulnerable(target: PathingEntity, ticks: Int, percent: Int) {
        vulnerable[target] = Vulnerability(target.currentMapClock + ticks, percent)
    }

    /** How many percent more damage [target] takes from each hit right now. */
    fun vulnerabilityOf(target: PathingEntity): Int {
        val mark = vulnerable[target] ?: return 0
        return if (mark.until > target.currentMapClock) mark.percent else 0
    }

    fun isImmuneToMelee(player: Player): Boolean =
        (meleeImmuneUntil[player] ?: Int.MIN_VALUE) > player.currentMapClock

    fun hasPowerOfDeath(player: Player): Boolean =
        (powerOfDeathUntil[player] ?: Int.MIN_VALUE) > player.currentMapClock &&
            player.righthand?.let { weapon -> POWER_OF_DEATH_STAVES.any { it.id == weapon.id } } ==
                true

    internal val modifier = PlayerHitModifier { target -> modify(target) }

    private fun HitBuilder.modify(target: Player) {
        if (type == HitType.Typeless) {
            return
        }
        val extra = vulnerabilityOf(target)
        if (extra > 0) {
            damage += damage * extra / 100
        }
        if (type != HitType.Melee) {
            return
        }
        if (isImmuneToMelee(target)) {
            damage = 0
        } else if (hasPowerOfDeath(target)) {
            damage /= 2
        }
    }

    internal fun modifyNpcHit(npc: Npc, hit: HitBuilder) {
        val extra = vulnerabilityOf(npc)
        if (extra > 0 && hit.type != HitType.Typeless) {
            hit.damage += hit.damage * extra / 100
        }
    }

    companion object {
        /** The staves whose special attack is Power of Death. */
        val POWER_OF_DEATH_STAVES =
            listOf(
                sobjs.sotd,
                sobjs.toxic_sotd,
                sobjs.toxic_sotd_charged,
                sobjs.staff_of_light,
                sobjs.br_sotd,
                sobjs.staff_of_balance,
            )
    }
}

class DamageGuardScript @Inject constructor(private val guards: DamageGuards) :
    PluginScript() {
    override fun ScriptContext.startup() {
        PlayerHitModifiers.register("special_attack_damage_guards", guards.modifier)
        onEvent<NpcHitEvents.ModifyAny> { guards.modifyNpcHit(npc, hit) }
    }
}
