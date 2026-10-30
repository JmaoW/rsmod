package org.rsmod.content.other.dragonfire

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.varobjs
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.enumVarp
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn1
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.SpecialAttackType
import org.rsmod.api.specials.shield.ShieldSpecialAttack
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.utils.bits.getBits
import org.rsmod.utils.bits.withBits

object ShieldObjs : ObjReferences() {
    val dragonfire_shield = find("dragonfire_shield")
    val dragonfire_shield_uncharged = find("dragonfire_shield_uncharged")
    val dragonfire_ward = find("dragonfire_ward")
    val dragonfire_ward_uncharged = find("dragonfire_ward_uncharged")
    val wyvern_shield = find("wyvern_shield")
    val wyvern_shield_uncharged = find("wyvern_shield_uncharged")
}

object ShieldSeqs : SeqReferences() {
    val dragonfire_shield = find("qip_dragon_slayer_player_unleashing_fire")
    val dragonfire_ward = find("dragonfire_ward_releasing_charge")
    val wyvern_shield = find("fossil_wyvern_shield_release_charge")
}

object ShieldSpotanims : SpotanimReferences() {
    val launch = find("qip_dragon_slayer_shield_attack_spotanim")
    val travel = find("qip_dragon_slayer_player_projanim")
    val impact = find("qip_dragon_slayer_player_impact")
}

/** The three shields that store dragonfire and can breathe it back out. */
enum class ChargedShield(
    val charged: ObjType,
    val uncharged: ObjType,
    val seq: org.rsmod.game.type.seq.SeqType,
    val title: String,
) {
    DragonfireShield(
        ShieldObjs.dragonfire_shield,
        ShieldObjs.dragonfire_shield_uncharged,
        ShieldSeqs.dragonfire_shield,
        "dragonfire shield",
    ),
    DragonfireWard(
        ShieldObjs.dragonfire_ward,
        ShieldObjs.dragonfire_ward_uncharged,
        ShieldSeqs.dragonfire_ward,
        "dragonfire ward",
    ),
    AncientWyvernShield(
        ShieldObjs.wyvern_shield,
        ShieldObjs.wyvern_shield_uncharged,
        ShieldSeqs.wyvern_shield,
        "ancient wyvern shield",
    );

    companion object {
        /** The shield [obj] is, charged or not, or `null` if it is neither. */
        fun of(obj: InvObj?): ChargedShield? =
            if (obj == null) null
            else entries.firstOrNull { it.charged.id == obj.id || it.uncharged.id == obj.id }
    }
}

/**
 * The charges of the dragonfire shield, the dragonfire ward and the ancient wyvern shield: up to
 * [MAX_CHARGES], stored on the shield itself. A shield takes a charge each time it absorbs a breath
 * of dragonfire and gives one up when it breathes. It is the uncharged shield with none.
 */
@Singleton
class ShieldCharges @Inject constructor() {
    fun charges(shield: InvObj?): Int = shield?.vars?.getBits(varobjs.charges_16383.bits) ?: 0

    /** Adds [amount] charges to the shield [player] wears, up to the most it holds. */
    fun charge(player: Player, amount: Int = 1): Boolean {
        val worn = player.lefthand ?: return false
        val shield = ChargedShield.of(worn) ?: return false
        val current = charges(worn)
        val total = minOf(MAX_CHARGES, current + amount)
        if (total == current) {
            return false
        }
        val vars = worn.vars.withBits(varobjs.charges_16383.bits, total)
        player.worn[Wearpos.LeftHand.slot] = InvObj(shield.charged, vars = vars)
        return true
    }

    /** Takes [amount] charges from the shield [player] wears, if it has that many. */
    fun spend(player: Player, amount: Int = 1): Boolean {
        val worn = player.lefthand ?: return false
        val shield = ChargedShield.of(worn) ?: return false
        val current = charges(worn)
        if (current < amount) {
            return false
        }
        val left = current - amount
        val vars = worn.vars.withBits(varobjs.charges_16383.bits, left)
        val type = if (left == 0) shield.uncharged else shield.charged
        player.worn[Wearpos.LeftHand.slot] = InvObj(type, vars = vars)
        return true
    }

    companion object {
        const val MAX_CHARGES = 50
    }
}

/**
 * The special attack of the three shields: it breathes the shield's stored dragonfire at the target
 * in place of the player's next swing. It takes a charge and deals up to 25 damage that cannot
 * miss, and the shield then needs two minutes before it can breathe again.
 */
class DragonfireShieldSpecials @Inject constructor(private val charges: ShieldCharges) :
    SpecialAttackMap {
    private val ready = WeakHashMap<Player, Int>()

    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val breath = Breath(manager, charges, ready)
        registerShield(ShieldObjs.dragonfire_shield, breath)
        registerShield(ShieldObjs.dragonfire_ward, breath)
        registerShield(ShieldObjs.wyvern_shield, breath)
    }

    private class Breath(
        private val manager: SpecialAttackManager,
        private val charges: ShieldCharges,
        private val ready: MutableMap<Player, Int>,
    ) : ShieldSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc): Boolean = breathe(target)

        override suspend fun ProtectedAccess.attack(target: Player): Boolean = breathe(target)

        private fun ProtectedAccess.breathe(target: PathingEntity): Boolean {
            val shield = ChargedShield.of(player.lefthand) ?: return false
            if (charges.charges(player.lefthand) <= 0) {
                mes("Your ${shield.title} has no charges.")
                return false
            }
            if ((ready[player] ?: Int.MIN_VALUE) > mapClock) {
                mes("Your ${shield.title} is still recharging.")
                return false
            }
            charges.spend(player)
            ready[player] = mapClock + RECHARGE_TICKS

            anim(shield.seq)
            spotanim(ShieldSpotanims.launch, height = 96, slot = constants.spotanim_slot_combat)
            val proj =
                manager.spawnProjectile(this, target, ShieldSpotanims.travel, projanims.magic_spell)
            target.spotanim(ShieldSpotanims.impact, height = 96, delay = proj.clientCycles)
            val damage = random.of(0..MAX_HIT)
            manager.queueTypelessHit(this, target, damage, proj.serverCycles)
            manager.continueCombat(this, target)
            return true
        }

        private companion object {
            const val MAX_HIT = 25

            /** Two minutes. */
            const val RECHARGE_TICKS = 200
        }
    }
}

/**
 * The options of the charged shields: Activate (or Operate) readies the special attack for the next
 * swing and Check, Inspect and Empty tell the charges or give them up.
 */
class DragonfireShieldScript @Inject constructor(private val charges: ShieldCharges) :
    PluginScript() {
    private var Player.specialType by enumVarp<SpecialAttackType>(varps.sa_attack)

    override fun ScriptContext.startup() {
        for (shield in ChargedShield.entries) {
            onOpWorn1(shield.charged) { activate(shield) }
            onOpWorn2(shield.charged) { check(shield) }
            onOpWorn2(shield.uncharged) { check(shield) }
            onOpHeld3(shield.charged) { check(shield, it.inventory[it.slot]) }
            onOpHeld3(shield.uncharged) { check(shield, it.inventory[it.slot]) }
            onOpHeld4(shield.charged) { empty(shield, it.inventory, it.slot) }
        }
    }

    /** Readies the special attack of the shield for the next swing, or puts it away again. */
    fun ProtectedAccess.activate(shield: ChargedShield) {
        if (player.specialType == SpecialAttackType.Shield) {
            player.specialType = SpecialAttackType.None
            mes("You put your ${shield.title}'s breath away.")
            return
        }
        if (charges.charges(player.lefthand) <= 0) {
            mes("Your ${shield.title} has no charges.")
            return
        }
        player.specialType = SpecialAttackType.Shield
        mes("Your ${shield.title} is ready to breathe on your next attack.")
    }

    fun ProtectedAccess.check(shield: ChargedShield, obj: InvObj? = player.lefthand) {
        val count = charges.charges(obj)
        mes("Your ${shield.title} has $count charge${if (count == 1) "" else "s"}.")
    }

    private fun ProtectedAccess.empty(
        shield: ChargedShield,
        inventory: org.rsmod.game.inv.Inventory,
        slot: Int,
    ) {
        inventory[slot] = InvObj(shield.uncharged)
        mes("You empty the ${shield.title} of its charges.")
    }
}
