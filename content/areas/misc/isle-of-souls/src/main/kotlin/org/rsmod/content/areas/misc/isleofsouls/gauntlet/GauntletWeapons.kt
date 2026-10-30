package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.weapons.MagicWeapon
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.spot.SpotanimType

object GauntletWeaponSpotanims : SpotanimReferences() {
    /** The crystal bow's glow and arrow (the cache has no graphics of the Gauntlet bows' own). */
    val arrow_launch = find("sp_attack_glow_arrow_launch")
    val arrow_travel = find("sp_attack_glow_arrow_travel")

    val staff_cast = find("crystal_staff_casting")
    val staff_travel = find("crystal_staff_projectile")
    val staff_impact = find("crystal_staff_impact")
    val staff_cast_hm = find("crystal_staff_casting_hm")
    val staff_travel_hm = find("crystal_staff_projectile_hm")
    val staff_impact_hm = find("crystal_staff_impact_hm")
}

/**
 * The Gauntlet's weapons in combat (wiki):
 * - the **halberd** (melee, range 2) fights as any melee weapon; its speed is set to 4 by
 *   [GauntletWeaponEdits], where the ordinary halberd's is 7;
 * - the **bow** (speed 5, range 10) needs no arrows, the strength is on the bow;
 * - the **staff** (speed 4, range 10) is a powered staff with a built-in spell whose max hit is 23
 *   / 31 / 39 for a basic / attuned / perfected staff, whatever the player's Magic level.
 *
 * Their look in the game is the cache's: the staff has its own graphics, the bow has none, so it
 * borrows the crystal bow's (a guess).
 */
class GauntletWeapons : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        for (items in listOf(GauntletRegularItems, GauntletCorruptedItems)) {
            val bow = GauntletBow(manager)
            items.bow.forEach { register(it, bow) }
            val corrupted = items === GauntletCorruptedItems
            items.staff.forEachIndexed { tier, staff ->
                register(staff, GauntletStaff(manager, STAFF_MAX_HIT[tier], corrupted))
            }
        }
    }

    private class GauntletBow(private val manager: WeaponAttackManager) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            shoot(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            shoot(target, attack)
            return true
        }

        private fun ProtectedAccess.shoot(target: PathingEntity, attack: CombatAttack.Ranged) {
            manager.playWeaponFx(this, attack)
            spotanim(
                GauntletWeaponSpotanims.arrow_launch,
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            val projanim =
                manager.spawnProjectile(
                    this,
                    target,
                    GauntletWeaponSpotanims.arrow_travel,
                    projanims.arrow,
                )
            val (serverDelay, clientDelay) = projanim.durations
            val damage = manager.rollRangedDamage(this, target, attack)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, null, damage, clientDelay, serverDelay)
            manager.continueCombat(this, target)
        }
    }

    private class GauntletStaff(
        private val manager: WeaponAttackManager,
        private val maxHit: Int,
        private val corrupted: Boolean,
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
            anim(seqs.human_castwave_staff)
            spotanim(castSpot(), height = 100)
            val proj = manager.spawnProjectile(this, target, travelSpot(), projanims.magic_spell)
            val (serverDelay, clientDelay) = proj.durations

            if (manager.rollStaffSplash(this, target, attack)) {
                manager.playSplashFx(this, target, clientDelay, castSound = null, soundRadius = 10)
                manager.queueSplashHit(this, target, clientDelay, serverDelay)
            } else {
                val damage = manager.rollStaffMaxHit(this, target, maxHit)
                manager.playMagicHitFx(
                    source = this,
                    target = target,
                    clientDelay = clientDelay,
                    castSound = null,
                    soundRadius = 10,
                    hitSpot = impactSpot(),
                    hitSpotHeight = 100,
                    hitSound = null,
                )
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMagicHit(this, target, damage, clientDelay, serverDelay)
            }
            manager.continueCombat(this, target)
        }

        private fun castSpot(): SpotanimType =
            if (corrupted) GauntletWeaponSpotanims.staff_cast_hm
            else GauntletWeaponSpotanims.staff_cast

        private fun travelSpot(): SpotanimType =
            if (corrupted) GauntletWeaponSpotanims.staff_travel_hm
            else GauntletWeaponSpotanims.staff_travel

        private fun impactSpot(): SpotanimType =
            if (corrupted) GauntletWeaponSpotanims.staff_impact_hm
            else GauntletWeaponSpotanims.staff_impact
    }

    companion object {
        /** Max hit of the staff by tier, whatever the player's Magic level (wiki). */
        val STAFF_MAX_HIT = listOf(23, 31, 39)

        /** The halberd attacks every 4 ticks, not 7 (wiki). */
        const val HALBERD_SPEED = 4
    }
}

/** The Gauntlet halberds attack every 4 ticks (the cache gives them the usual halberd's 7?). */
internal object GauntletWeaponEdits : ObjEditor() {
    init {
        for (items in listOf(GauntletRegularItems, GauntletCorruptedItems)) {
            for (halberd in items.halberd) {
                edit(halberd) { param[params.attackrate] = GauntletWeapons.HALBERD_SPEED }
            }
        }
    }
}
