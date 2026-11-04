package org.rsmod.content.areas.misc.isleofsouls.colosseum

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.content.other.special.attacks.ranged.TonalzticsOfRalos
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * The Colosseum's weapon with an attack of its own: the Tonalztics of Ralos (wiki). It comes back
 * to the thrower, so it uses no ammunition (as a thrown weapon the engine would otherwise throw it
 * away). Each blade hits for up to 75% of the ranged max hit. Charged, it throws two blades rolled
 * one after the other (the second against any Defence the first took off) and uses a charge a
 * throw; see [TonalzticsOfRalos]. Its range is 6 uncharged and 7 charged (the obj data).
 *
 * Which graphics belong to which throw is not on the wiki: the charged throw shows the charged glow
 * and both blades, the uncharged one the first blade only (a guess from their names).
 */
class ColosseumWeapons : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        val tonalztics = Tonalztics(manager)
        register(colosseum_objs.tonalztics_uncharged, tonalztics)
        register(colosseum_objs.tonalztics_charged, tonalztics)
    }

    private class Tonalztics(private val manager: WeaponAttackManager) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Ranged,
        ): Boolean {
            throwBlades(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Ranged,
        ): Boolean {
            throwBlades(target, attack)
            return true
        }

        private fun ProtectedAccess.throwBlades(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ) {
            val spots = TonalzticsOfRalos.Spots
            manager.playWeaponFx(this, attack)
            val charged = TonalzticsOfRalos.spendCharge(player)
            if (charged) {
                spotanim(spots.launch_charged, height = 96, slot = constants.spotanim_slot_combat)
            }
            val blades = if (charged) 2 else 1
            var total = 0
            repeat(blades) { index ->
                val proj =
                    manager.spawnProjectile(
                        this,
                        target,
                        if (index == 0) spots.travel_1 else spots.travel_2,
                        if (blades == 1) projanims.thrown
                        else if (index == 0) projanims.doublearrow_one
                        else projanims.doublearrow_two,
                    )
                val damage =
                    manager.rollRangedDamage(
                        this,
                        target,
                        attack,
                        maxHitMultiplier = TonalzticsOfRalos.MAX_HIT_FRACTION,
                    )
                total += damage
                manager.queueRangedHit(
                    this,
                    target,
                    null,
                    damage,
                    proj.clientCycles,
                    proj.serverCycles,
                )
                if (index == 0) {
                    target.spotanim(spots.impact, height = 96, delay = proj.clientCycles)
                }
            }
            manager.giveCombatXp(this, target, attack, total)
            manager.continueCombat(this, target)
        }
    }
}
