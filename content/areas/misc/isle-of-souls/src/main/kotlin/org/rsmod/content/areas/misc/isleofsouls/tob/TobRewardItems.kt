package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varobjs
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.obj.charges.ObjChargeManager.Companion.isFailure
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.weapons.MagicWeapon
import org.rsmod.api.weapons.MeleeWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object TobItemObjs : ObjReferences() {
    val scythe = find("scythe_of_vitur")
    val scythe_uncharged = find("scythe_of_vitur_uncharged")
    val scythe_holy = find("scythe_of_vitur_or")
    val scythe_holy_uncharged = find("scythe_of_vitur_uncharged_or")
    val scythe_sanguine = find("scythe_of_vitur_bl")
    val scythe_sanguine_uncharged = find("scythe_of_vitur_uncharged_bl")
    val staff = find("sanguinesti_staff")
    val staff_uncharged = find("sanguinesti_staff_uncharged")
    val staff_holy = find("sanguinesti_staff_or")
    val staff_holy_uncharged = find("sanguinesti_staff_uncharged_or")
    val rapier = find("ghrazi_rapier")
    val rapier_holy = find("ghrazi_rapier_or")
    val avernic = find("infernal_defender")
    val hilt = find("infernal_defender_hilt")
    val dragon_defender = find("dragon_parryingdagger")
    val holy_kit = find("tob_hardmode_kit")
    val sanguine_kit = find("tob_hardmode_kit_blood")
    val vial_of_blood = find("vial_blood")
    val blood_rune = find("bloodrune")
}

object TobItemSeqs : SeqReferences() {
    val scythe_attack = find("scythe_of_vitur_attack")
    val staff_cast = find("human_castwave_staff")
}

object TobItemSpots : SpotanimReferences() {
    val staff_cast = find("sanguinesti_staff_casting")
    val staff_travel = find("sanguinesti_staff_travel")
    val staff_impact = find("sanguinesti_staff_impact")
    val staff_heal = find("sanguinesti_staff_heal")
}

/** Each charged weapon's uncharged form and back, so charges can run out and be put in. */
internal object TobItemEdits : ObjEditor() {
    init {
        pair(TobItemObjs.scythe, TobItemObjs.scythe_uncharged)
        pair(TobItemObjs.scythe_holy, TobItemObjs.scythe_holy_uncharged)
        pair(TobItemObjs.scythe_sanguine, TobItemObjs.scythe_sanguine_uncharged)
        pair(TobItemObjs.staff, TobItemObjs.staff_uncharged)
        pair(TobItemObjs.staff_holy, TobItemObjs.staff_holy_uncharged)
    }

    private fun pair(charged: ObjType, uncharged: ObjType) {
        edit(charged) { param[params.uncharged_variant] = uncharged }
        edit(uncharged) { param[params.charged_variant] = charged }
    }
}

/**
 * How the theatre's weapons fight (wiki):
 * - The Scythe of Vitur hits a monster two tiles wide twice and a larger one three times, for full,
 *   half and a quarter of a hit, each rolled on its own; one charge an attack.
 * - The Sanguinesti staff casts its own spell (max hit a third of the Magic level, less one), and
 *   one hit in six heals the caster for half the damage; one charge a cast. Uncharged, it does
 *   nothing.
 */
class TobWeapons
@Inject
constructor(private val charges: CombatChargeManager, private val random: GameRandom) : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        val scythe = Scythe(manager, charges)
        register(TobItemObjs.scythe, scythe)
        register(TobItemObjs.scythe_holy, scythe)
        register(TobItemObjs.scythe_sanguine, scythe)
        val staff = Staff(manager, charges, random)
        register(TobItemObjs.staff, staff)
        register(TobItemObjs.staff_holy, staff)
        val uncharged = UnchargedStaff(manager)
        register(TobItemObjs.staff_uncharged, uncharged)
        register(TobItemObjs.staff_holy_uncharged, uncharged)
    }

    private class Scythe(
        private val manager: WeaponAttackManager,
        private val charges: CombatChargeManager,
    ) : MeleeWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            swing(target, attack, hitsFor(target.size))
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            swing(target, attack, 1)
            return true
        }

        private fun hitsFor(size: Int): Int =
            when {
                size >= 3 -> 3
                size == 2 -> 2
                else -> 1
            }

        private fun ProtectedAccess.swing(
            target: PathingEntity,
            attack: CombatAttack.Melee,
            hits: Int,
        ) {
            val result = charges.attemptDetractWeapon(player, varobjs.tob_charges)
            if (result.isFailure()) {
                manager.stopCombat(this)
                mes("Your Scythe of Vitur has no charges.")
                return
            }
            manager.playWeaponFx(this, attack)
            for (index in 0 until hits) {
                val damage = manager.rollMeleeDamage(this, target, attack, 1.0, SPLASH[index])
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMeleeHit(this, target, damage)
            }
            if (result.fullyUncharged) {
                manager.stopCombat(this)
                mes("Your Scythe of Vitur has run out of charges.")
                return
            }
            manager.continueCombat(this, target)
        }

        private companion object {
            val SPLASH = doubleArrayOf(1.0, 0.5, 0.25)
        }
    }

    private class Staff(
        private val manager: WeaponAttackManager,
        private val charges: CombatChargeManager,
        private val random: GameRandom,
    ) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            cast(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            cast(target, attack)
            return true
        }

        private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Staff) {
            val result = charges.attemptDetractWeapon(player, varobjs.tob_charges)
            if (result.isFailure()) {
                manager.stopCombat(this)
                mes("Your Sanguinesti staff has no charges.")
                return
            }
            anim(TobItemSeqs.staff_cast)
            spotanim(TobItemSpots.staff_cast, height = 92)
            val proj =
                manager.spawnProjectile(
                    this,
                    target,
                    TobItemSpots.staff_travel,
                    projanims.magic_spell,
                )
            val (serverDelay, clientDelay) = proj.durations
            if (manager.rollStaffSplash(this, target, attack)) {
                manager.playSplashFx(this, target, clientDelay, null, soundRadius = 10)
                manager.queueSplashHit(this, target, clientDelay, serverDelay)
            } else {
                val damage =
                    manager.rollStaffMaxHit(this, target, maxOf(1, player.magicLvl / 3 - 1))
                manager.playMagicHitFx(
                    source = this,
                    target = target,
                    clientDelay = clientDelay,
                    castSound = null,
                    soundRadius = 10,
                    hitSpot = TobItemSpots.staff_impact,
                    hitSpotHeight = 124,
                    hitSound = null,
                )
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMagicHit(this, target, damage, clientDelay, serverDelay)
                if (damage > 0 && random.of(HEAL_ONE_IN) == 0) {
                    player.statHeal(stats.hitpoints, damage / 2, percent = 0)
                    spotanim(TobItemSpots.staff_heal, delay = clientDelay)
                }
            }
            if (result.fullyUncharged) {
                manager.stopCombat(this)
                mes("Your Sanguinesti staff has run out of charges.")
                return
            }
            manager.continueCombat(this, target)
        }

        private companion object {
            const val HEAL_ONE_IN = 6
        }
    }

    private class UnchargedStaff(private val manager: WeaponAttackManager) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            refuse()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            refuse()
            return true
        }

        private fun ProtectedAccess.refuse() {
            mes("Your Sanguinesti staff has no charges! Charge it with blood runes.")
            manager.stopCombat(this)
        }
    }
}

/**
 * Charging, checking and uncharging the scythe and the staff, making the Avernic defender, and the
 * theatre's ornament kits.
 *
 * Wiki: the scythe takes 100 charges for a vial of blood and 300 blood runes (the real game fills
 * the Vyre well in Ver Sinhaza with them first; here they are used on the scythe), the staff one
 * charge for three blood runes; each holds 20,000. The Avernic defender is the hilt on a dragon
 * defender. The Holy ornament kit goes on the scythe, the staff and the rapier, the Sanguine
 * ornament kit on the scythe; each can be taken off again (Dismantle), and the scythe's charges go
 * with it either way.
 */
class TobRewardItems
@Inject
constructor(private val charges: ObjChargeManager, private val tracker: LeagueTaskTracker) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (charged in
            listOf(TobItemObjs.scythe, TobItemObjs.scythe_holy, TobItemObjs.scythe_sanguine)) {
            onOpHeld3(charged) { check(it.inventory[it.slot], "Scythe of Vitur") }
            onOpHeld4(charged) { unchargeScythe(it.inventory, it.slot) }
            onOpHeldU(charged, TobItemObjs.vial_of_blood) { chargeScythe(inv, it.firstSlot) }
            onOpHeldU(charged, TobItemObjs.blood_rune) { chargeScythe(inv, it.firstSlot) }
        }
        for (uncharged in
            listOf(
                TobItemObjs.scythe_uncharged,
                TobItemObjs.scythe_holy_uncharged,
                TobItemObjs.scythe_sanguine_uncharged,
            )) {
            onOpHeld4(uncharged) { chargeScythe(it.inventory, it.slot) }
            onOpHeldU(uncharged, TobItemObjs.vial_of_blood) { chargeScythe(inv, it.firstSlot) }
            onOpHeldU(uncharged, TobItemObjs.blood_rune) { chargeScythe(inv, it.firstSlot) }
        }
        for (charged in listOf(TobItemObjs.staff, TobItemObjs.staff_holy)) {
            onOpHeld3(charged) { check(it.inventory[it.slot], "Sanguinesti staff") }
            onOpHeld4(charged) { chargeStaff(it.inventory, it.slot) }
            onOpHeld5(charged) { unchargeStaff(it.inventory, it.slot) }
            onOpHeldU(charged, TobItemObjs.blood_rune) { chargeStaff(inv, it.firstSlot) }
        }
        for (uncharged in listOf(TobItemObjs.staff_uncharged, TobItemObjs.staff_holy_uncharged)) {
            onOpHeld3(uncharged) { chargeStaff(it.inventory, it.slot) }
            onOpHeldU(uncharged, TobItemObjs.blood_rune) { chargeStaff(inv, it.firstSlot) }
        }

        onOpHeldU(TobItemObjs.hilt, TobItemObjs.dragon_defender) { makeAvernic() }

        for ((from, into) in HOLY) {
            onOpHeldU(TobItemObjs.holy_kit, from) {
                attach(TobItemObjs.holy_kit, from, into, TobGearTasks.TASK_HOLY_KIT)
            }
        }
        for ((from, into) in SANGUINE) {
            onOpHeldU(TobItemObjs.sanguine_kit, from) {
                attach(TobItemObjs.sanguine_kit, from, into, TobGearTasks.TASK_SANGUINE_KIT)
            }
        }
        // Only these forms have "Dismantle" (op 5); the charged ones are uncharged first.
        for ((ornamented, plain, kit) in DISMANTLE) {
            onOpHeld5(ornamented) { dismantle(it.inventory, it.slot, plain, kit) }
        }
    }

    private fun ProtectedAccess.check(obj: InvObj?, name: String) {
        val left = charges.getCharges(obj, varobjs.tob_charges)
        mes("Your $name has $left charges remaining.")
    }

    private suspend fun ProtectedAccess.chargeScythe(inventory: Inventory, slot: Int) {
        if (TobItemObjs.vial_of_blood !in inv || TobItemObjs.blood_rune !in inv) {
            mes("You need vials of blood and blood runes to charge the Scythe of Vitur.")
            return
        }
        val current = charges.getCharges(inventory[slot], varobjs.tob_charges)
        val room = (MAX_CHARGES - current) / SCYTHE_CHARGES_PER
        val affordable =
            min(
                invTotal(inv, TobItemObjs.vial_of_blood),
                invTotal(inv, TobItemObjs.blood_rune) / SCYTHE_RUNES_PER,
            )
        val most = min(room, affordable)
        if (most <= 0) {
            mes(
                if (room <= 0) "Your Scythe of Vitur is fully charged."
                else "You need 300 blood runes for each vial of blood."
            )
            return
        }
        val sets =
            min(
                countDialog("How many sets of 100 charges do you wish to apply? (Up to $most)"),
                most,
            )
        if (sets <= 0) {
            return
        }
        val paid =
            invDel(
                inv,
                TobItemObjs.vial_of_blood,
                sets,
                TobItemObjs.blood_rune,
                sets * SCYTHE_RUNES_PER,
            )
        if (paid.failure) {
            return
        }
        charges.addCharges(
            inventory,
            slot,
            sets * SCYTHE_CHARGES_PER,
            varobjs.tob_charges,
            MAX_CHARGES,
        )
        mes("You apply ${sets * SCYTHE_CHARGES_PER} charges to your Scythe of Vitur.")
    }

    private suspend fun ProtectedAccess.unchargeScythe(inventory: Inventory, slot: Int) {
        val current = charges.getCharges(inventory[slot], varobjs.tob_charges)
        val sets = current / SCYTHE_CHARGES_PER
        val sure =
            choice2(
                "Yes.",
                true,
                "No.",
                false,
                title = "Remove all charges from your Scythe of Vitur?",
            )
        if (!sure) {
            return
        }
        if (sets > 0 && inv.freeSpace() < 2) {
            mes("You need two free inventory spaces to uncharge the scythe.")
            return
        }
        charges.removeAllCharges(inventory, slot, varobjs.tob_charges)
        if (sets > 0) {
            invAdd(inv, TobItemObjs.vial_of_blood, sets)
            invAdd(inv, TobItemObjs.blood_rune, sets * SCYTHE_RUNES_PER)
        }
        mes("You remove the charges from your Scythe of Vitur.")
    }

    private suspend fun ProtectedAccess.chargeStaff(inventory: Inventory, slot: Int) {
        if (TobItemObjs.blood_rune !in inv) {
            mes("You need blood runes to charge the Sanguinesti staff.")
            return
        }
        val current = charges.getCharges(inventory[slot], varobjs.tob_charges)
        val most =
            min(MAX_CHARGES - current, invTotal(inv, TobItemObjs.blood_rune) / STAFF_RUNES_PER)
        if (most <= 0) {
            mes(
                if (current >= MAX_CHARGES) "Your Sanguinesti staff is fully charged."
                else "You need three blood runes for each charge."
            )
            return
        }
        val wanted = min(countDialog("How many charges do you wish to apply? (Up to $most)"), most)
        if (wanted <= 0) {
            return
        }
        if (invDel(inv, TobItemObjs.blood_rune, wanted * STAFF_RUNES_PER).failure) {
            return
        }
        charges.addCharges(inventory, slot, wanted, varobjs.tob_charges, MAX_CHARGES)
        mes("You apply $wanted charges to your Sanguinesti staff.")
    }

    private suspend fun ProtectedAccess.unchargeStaff(inventory: Inventory, slot: Int) {
        val sure =
            choice2(
                "Yes.",
                true,
                "No.",
                false,
                title = "Remove all charges from your Sanguinesti staff?",
            )
        if (!sure) {
            return
        }
        val removed = charges.removeAllCharges(inventory, slot, varobjs.tob_charges)
        if (removed > 0) {
            invAdd(inv, TobItemObjs.blood_rune, removed * STAFF_RUNES_PER)
        }
        mes("You remove the charges from your Sanguinesti staff.")
    }

    private fun ProtectedAccess.makeAvernic() {
        if (invDel(inv, TobItemObjs.hilt, 1, TobItemObjs.dragon_defender, 1).failure) {
            return
        }
        invAdd(inv, TobItemObjs.avernic, 1)
        mes("You attach the hilt to the dragon defender, creating an Avernic defender.")
    }

    /** Puts [kit] on the [from] item, keeping its charges; on a scythe it completes [task]. */
    private fun ProtectedAccess.attach(kit: ObjType, from: ObjType, into: ObjType, task: Int) {
        val slot = inv.indices.firstOrNull { inv[it]?.id == from.id } ?: return
        val obj = inv[slot] ?: return
        if (invDel(inv, kit, 1).failure) {
            return
        }
        inv[slot] = InvObj(into, 1, obj.vars)
        mes("You attach the ornament kit.")
        if (from.isScythe()) {
            tracker.complete(player, task)
        }
    }

    private fun ProtectedAccess.dismantle(
        inventory: Inventory,
        slot: Int,
        into: ObjType,
        kit: ObjType,
    ) {
        val obj = inventory[slot] ?: return
        if (inv.freeSpace() <= 0) {
            mes("You need a free inventory space to remove the ornament kit.")
            return
        }
        inventory[slot] = InvObj(into, 1, obj.vars)
        invAdd(inv, kit, 1)
        mes("You remove the ornament kit.")
    }

    private fun ObjType.isScythe(): Boolean =
        this.id == TobItemObjs.scythe.id || this.id == TobItemObjs.scythe_uncharged.id

    private companion object {
        const val MAX_CHARGES = 20_000
        const val SCYTHE_CHARGES_PER = 100
        const val SCYTHE_RUNES_PER = 300
        const val STAFF_RUNES_PER = 3

        /** The Holy ornament kit: the plain form and the ornamented one. */
        val HOLY =
            listOf(
                TobItemObjs.scythe to TobItemObjs.scythe_holy,
                TobItemObjs.scythe_uncharged to TobItemObjs.scythe_holy_uncharged,
                TobItemObjs.staff to TobItemObjs.staff_holy,
                TobItemObjs.staff_uncharged to TobItemObjs.staff_holy_uncharged,
                TobItemObjs.rapier to TobItemObjs.rapier_holy,
            )

        val SANGUINE =
            listOf(
                TobItemObjs.scythe to TobItemObjs.scythe_sanguine,
                TobItemObjs.scythe_uncharged to TobItemObjs.scythe_sanguine_uncharged,
            )

        val DISMANTLE =
            listOf(
                Triple(
                    TobItemObjs.scythe_holy_uncharged,
                    TobItemObjs.scythe_uncharged,
                    TobItemObjs.holy_kit,
                ),
                Triple(
                    TobItemObjs.staff_holy_uncharged,
                    TobItemObjs.staff_uncharged,
                    TobItemObjs.holy_kit,
                ),
                Triple(TobItemObjs.rapier_holy, TobItemObjs.rapier, TobItemObjs.holy_kit),
                Triple(
                    TobItemObjs.scythe_sanguine_uncharged,
                    TobItemObjs.scythe_uncharged,
                    TobItemObjs.sanguine_kit,
                ),
            )
    }
}
