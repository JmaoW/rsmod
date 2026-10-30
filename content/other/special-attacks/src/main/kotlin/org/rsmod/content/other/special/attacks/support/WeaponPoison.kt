package org.rsmod.content.other.special.attacks.support

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.manager.PlayerAttackEvents
import org.rsmod.api.player.righthand
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Poisoned weapons and ammunition. A melee or ranged hit that does damage while a poisoned weapon
 * (or the poisoned ammunition it shoots) is in use has a one in four chance to poison the target:
 * for 4, 5 or 6 damage with a (p), (p+) or (p++) dagger, spear, knife or javelin, and 2, 3 or 4 with
 * poisoned arrows, bolts and darts. The abyssal tentacle poisons for 4 in the same way. How the
 * poison works is [SpecialDots.poison].
 */
@Singleton
class WeaponPoison
@Inject
constructor(
    private val dots: SpecialDots,
    private val objTypes: ObjTypeList,
    private val random: GameRandom,
) {
    /** The damage a poison from [weapon] and [ammo] starts at, or `null` if neither is poisoned. */
    fun poisonOf(weapon: InvObj?, ammo: ObjType?): Int? {
        val fromAmmo = ammo?.internalName?.let(::parse)
        if (fromAmmo != null) {
            return fromAmmo
        }
        if (weapon == null) {
            return null
        }
        if (TENTACLES.any { it.id == weapon.id }) {
            return TENTACLE_POISON
        }
        return objTypes[weapon].internalName?.let(::parse)
    }

    internal fun onHit(event: PlayerAttackEvents.Hit) {
        if (event.damage <= 0 || event.type == HitType.Magic || event.type == HitType.Typeless) {
            return
        }
        val damage = poisonOf(event.source.righthand, event.ammo) ?: return
        if (random.of(CHANCE) == 0) {
            dots.poison(event.target, event.source, damage)
        }
    }

    private fun parse(name: String): Int? {
        val match = POISONED.matchEntire(name) ?: return null
        val kind = match.groupValues[1]
        val tier = match.groupValues[2].length
        val base = if (kind in LIGHT_KINDS) LIGHT_BASE else HEAVY_BASE
        return base + tier
    }

    private companion object {
        val POISONED = Regex("^.*_(dagger|spear|knife|javelin|dart|arrow|bolts)_p(\\+{0,2})$")
        val LIGHT_KINDS = setOf("dart", "arrow", "bolts")
        const val LIGHT_BASE = 2
        const val HEAVY_BASE = 4
        const val TENTACLE_POISON = 4

        /** A one in four chance. */
        const val CHANCE = 4

        val TENTACLES = listOf(sobjs.abyssal_tentacle, sobjs.league_3_whip_tentacle)
    }
}

class WeaponPoisonScript @Inject constructor(private val poison: WeaponPoison) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<PlayerAttackEvents.Hit> { poison.onHit(this) }
    }
}
