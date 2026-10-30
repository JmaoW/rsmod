package org.rsmod.content.areas.misc.isleofsouls.revenant

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.worn.RevenantEther
import org.rsmod.api.script.onEvent
import org.rsmod.api.spells.events.SpellCast
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.weapons.MagicWeapon
import org.rsmod.api.weapons.MeleeWeapon
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object RevenantWeaponSpotanims : SpotanimReferences() {
    val arrow_launch = find("wild_cave_bow_arrow_launch")
    val arrow_travel = find("wild_cave_bow_arrow_travel")
    val arrow_launch_webweaver = find("wild_cave_bow_arrow_launch02")
    val arrow_travel_webweaver = find("wild_cave_bow_arrow_travel02")
    val sceptre_travel = find("revenant_magic_travel")
    val sceptre_impact = find("revenant_magic_impact")
}

/**
 * How the revenant weapons spend ether when they attack (OSRS Wiki): every attack takes one ether,
 * whether or not it hits, and the weapon's passive (+50% accuracy and damage) only works while it
 * has ether to spend.
 * - The chainmaces fight as usual: the attack spends the ether, and it is spent before the hit is
 *   rolled, so the very last ether is spent by an attack that no longer has the bonus.
 * - The bows need no ammunition, only ether, and cannot fire without it.
 * - The sceptres' built-in spell (the powered staff) needs ether too. Its max hit is a third of the
 *   Magic level less 8 (Thammaron's) or 6 (Accursed). The Wiki does not describe its look, so it
 *   uses the revenants' own magic graphics, and the cast animation of the other powered staves
 *   (guesses).
 * - A spell cast with a sceptre in hand (the autocast form, or any combat spell) spends one ether,
 *   see [RevenantSpellEther].
 */
class RevenantWeapons : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        val melee = RevenantMelee()
        register(ether_objs.viggora, melee)
        register(ether_objs.ursine, melee)

        val craw =
            RevenantBow(
                manager,
                RevenantWeaponSpotanims.arrow_launch,
                RevenantWeaponSpotanims.arrow_travel,
            )
        register(ether_objs.craw_uncharged, craw)
        register(ether_objs.craw, craw)
        val webweaver =
            RevenantBow(
                manager,
                RevenantWeaponSpotanims.arrow_launch_webweaver,
                RevenantWeaponSpotanims.arrow_travel_webweaver,
            )
        register(ether_objs.webweaver_uncharged, webweaver)
        register(ether_objs.webweaver, webweaver)

        val thammaron = RevenantSceptre(manager, EtherItems.thammaron.maxHitOffset)
        register(ether_objs.thammaron_uncharged, thammaron)
        register(ether_objs.thammaron, thammaron)
        val accursed = RevenantSceptre(manager, EtherItems.accursed.maxHitOffset)
        register(ether_objs.accursed_uncharged, accursed)
        register(ether_objs.accursed, accursed)
    }

    private class RevenantMelee : MeleeWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Melee,
        ): Boolean {
            EtherCharges.spendWeaponEther(player)
            return false
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Melee,
        ): Boolean {
            EtherCharges.spendWeaponEther(player)
            return false
        }
    }

    private class RevenantBow(
        private val manager: WeaponAttackManager,
        private val launch: SpotanimType,
        private val travel: SpotanimType,
    ) : RangedWeapon {
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
            if (RevenantEther.usable(player.righthand) <= 0) {
                manager.stopCombat(this)
                mes("Your bow has no ether to fire with.")
                return
            }
            manager.playWeaponFx(this, attack)
            spotanim(launch, height = 96, slot = constants.spotanim_slot_combat)
            val projanim = manager.spawnProjectile(this, target, travel, projanims.arrow)
            val (serverDelay, clientDelay) = projanim.durations

            // The shot is rolled with the ether still in the bow, then paid for.
            val damage = manager.rollRangedDamage(this, target, attack)
            EtherCharges.spendWeaponEther(player)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, null, damage, clientDelay, serverDelay)
            manager.continueCombat(this, target)
        }
    }

    private class RevenantSceptre(
        private val manager: WeaponAttackManager,
        private val maxHitOffset: Int,
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
            if (RevenantEther.usable(player.righthand) <= 0) {
                manager.stopCombat(this)
                mes("Your sceptre has no ether to cast with.")
                return
            }
            anim(seqs.human_castwave_staff)
            val proj =
                manager.spawnProjectile(
                    source = this,
                    target = target,
                    spotanim = RevenantWeaponSpotanims.sceptre_travel,
                    projanim = projanims.magic_spell,
                )
            val (serverDelay, clientDelay) = proj.durations

            val splash = manager.rollStaffSplash(this, target, attack)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay, castSound = null, soundRadius = 10)
                manager.queueSplashHit(this, target, clientDelay, serverDelay)
            } else {
                val baseMaxHit = maxOf(0, player.magicLvl / 3 - maxHitOffset)
                val damage = manager.rollStaffMaxHit(this, target, baseMaxHit)
                manager.playMagicHitFx(
                    source = this,
                    target = target,
                    clientDelay = clientDelay,
                    castSound = null,
                    soundRadius = 10,
                    hitSpot = RevenantWeaponSpotanims.sceptre_impact,
                    hitSpotHeight = 100,
                    hitSound = null,
                )
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMagicHit(this, target, damage, clientDelay, serverDelay)
            }
            EtherCharges.spendWeaponEther(player)
            manager.continueCombat(this, target)
        }
    }
}

/**
 * Spends one ether of the sceptre a player casts a combat spell with (Wiki: Thammaron's sceptre). A
 * bow or a chainmace in hand pays nothing for a spell cast with it.
 */
class RevenantSpellEther : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SpellCast> {
            val style = EtherItems.chargedOf(player.righthand)?.style
            if (style == EtherStyle.Powered || style == EtherStyle.Autocast) {
                EtherCharges.spendWeaponEther(player)
            }
        }
    }
}
