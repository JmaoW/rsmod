package org.rsmod.content.other.special.attacks

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.commons.styles.MagicAttackStyle
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.PvpRules
import org.rsmod.api.combat.manager.AttackBuffs
import org.rsmod.api.combat.manager.PlayerAttackEvents
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varps
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.specials.SpecialAttack
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRegistry
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.specials.weapon.SpecialAttackWeapons
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.other.special.attacks.support.DamageGuardScript
import org.rsmod.content.other.special.attacks.support.DamageGuards
import org.rsmod.content.other.special.attacks.support.PrayerLockScript
import org.rsmod.content.other.special.attacks.support.PrayerLocks
import org.rsmod.content.other.special.attacks.support.SoulStacks
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.content.other.special.attacks.support.SoulStackScript
import org.rsmod.content.other.special.attacks.support.WeaponPoison
import org.rsmod.content.other.special.attacks.support.WeaponPoisonScript
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

/**
 * Sets up every special attack in the game test the way the server does at startup, and lets a
 * test make one as the engine would: the energy is checked before and taken after.
 */
object SpecialAttackHarness {
    class Deps
    @Inject
    constructor(
        val registry: SpecialAttackRegistry,
        val repo: SpecialAttackRepository,
        val manager: SpecialAttackManager,
        val weapons: SpecialAttackWeapons,
        val energy: SpecialAttackEnergy,
        val guards: DamageGuards,
        val dots: SpecialDots,
        val poison: WeaponPoison,
        val prayerLocks: PrayerLocks,
        val buffs: AttackBuffs,
        val stacks: SoulStacks,
        val pvp: PvpRules,
        val maps: Set<SpecialAttackMap>,
    ) {
        fun registerAll() {
            weapons.startup()
            for (map in maps) {
                with(map) { repo.register(manager) }
            }
        }
    }

    private object SpecialsModule : AbstractModule() {
        override fun configure() {
            install(org.rsmod.api.specials.SpecialAttackModule())
            install(SpecialAttackModule())
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            // The game cycle and the special attacks must queue on the same world queue.
            bind(WorldQueueList::class.java).`in`(Scopes.SINGLETON)
        }
    }

    fun <T> GameTestState.runSpecialTest(body: GameTestScope.(Deps) -> T) =
        runInjectedGameTest(
            Deps::class,
            SpecialsModule,
            WeaponAttackStylesScript::class,
            NpcHitScript::class,
            PlayerHitScript::class,
            PrayerLockScript::class,
            DamageGuardScript::class,
            SoulStackScript::class,
            WeaponPoisonScript::class,
        ) { deps ->
            deps.registerAll()
            body(deps)
        }

    /** A target with plenty of hitpoints and no defence to speak of. */
    val dummy =
        npcTypeFactory.create {
            name = "Special attack dummy"
            vislevel = 50
            attack = 50
            strength = 50
            defence = 1
            hitpoints = 10_000
            defaultMode = NpcMode.None
        }

    fun GameTestScope.spawnDummy(dx: Int = 1): Npc {
        val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), dummy)
        player.placeAt(npc.coords.translateX(-dx))
        return npc
    }

    /** A player fit to fight: maxed combat stats, the [weapon] in hand and a full special bar. */
    fun GameTestScope.equip(weapon: ObjType) {
        for (stat in
            listOf(stats.attack, stats.strength, stats.defence, stats.ranged, stats.magic)) {
            player.stats[stat] = 99
        }
        player.stats[stats.prayer] = 99
        player.stats[stats.hitpoints] = 99
        player.worn[Wearpos.RightHand.slot] = InvObj(weapon)
        player.setVarp(varps.sa_energy, 1000)
        // Projectiles are sent to the players that can see them.
        player.observerUUID = player.uuid
    }

    /** Runs [attempt] until it returns `true`, [tries] times at the most. */
    inline fun repeatUntil(tries: Int, attempt: () -> Boolean): Boolean {
        repeat(tries) {
            if (attempt()) {
                return true
            }
        }
        return false
    }

    fun Player.weaponInv(): InvObj = checkNotNull(worn[Wearpos.RightHand.slot])

    /** Every melee and ranged hit the player queues from now on. */
    fun GameTestScope.recordHits(): MutableList<PlayerAttackEvents.Hit> {
        val hits = mutableListOf<PlayerAttackEvents.Hit>()
        eventBus.subscribeUnbound(PlayerAttackEvents.Hit::class.java) { hits += this }
        return hits
    }

    fun GameTestScope.obj(internal: String): ObjType =
        objTypes.values.first { it.internalName == internal }

    /** Puts [ammo] in the quiver, or leaves it empty for a weapon that is thrown. */
    fun GameTestScope.equipRanged(weapon: ObjType, ammo: String? = null, count: Int = 100) {
        equip(weapon)
        if (ammo != null) {
            player.worn[Wearpos.Quiver.slot] = InvObj(obj(ammo), count)
        }
    }

    /** Like [fireMelee], for the equipped weapon's ranged special attack. */
    fun GameTestScope.fireRanged(deps: Deps, target: PathingEntity): Boolean {
        val special = deps.registry[player.weaponInv()] as SpecialAttack.Ranged
        var energyTaken = false
        player.withProtectedAccess {
            if (!deps.energy.hasSpecialEnergy(player, special.energyInHundreds)) {
                return@withProtectedAccess
            }
            val attack =
                CombatAttack.Ranged(
                    player.weaponInv(),
                    RangedAttackType.Standard,
                    RangedAttackStyle.Accurate,
                )
            if (special.attack(this, target, attack)) {
                deps.energy.takeSpecialEnergy(player, special.energyInHundreds)
                energyTaken = true
            }
        }
        return energyTaken
    }

    /** Like [fireMelee], for the equipped staff's magic special attack. */
    fun GameTestScope.fireStaff(deps: Deps, target: PathingEntity): Boolean {
        val special = deps.registry[player.weaponInv()] as SpecialAttack.Magic
        var energyTaken = false
        player.withProtectedAccess {
            if (!deps.energy.hasSpecialEnergy(player, special.energyInHundreds)) {
                return@withProtectedAccess
            }
            val attack = CombatAttack.Staff(player.weaponInv(), MagicAttackStyle.Accurate)
            if (special.attack(this, target, attack)) {
                deps.energy.takeSpecialEnergy(player, special.energyInHundreds)
                energyTaken = true
            }
        }
        return energyTaken
    }

    /** Uses the equipped weapon's instant special attack. */
    fun GameTestScope.fireInstant(deps: Deps): Boolean {
        val special = deps.registry[player.weaponInv()] as SpecialAttack.Instant
        var energyTaken = false
        player.withProtectedAccess {
            if (!deps.energy.hasSpecialEnergy(player, special.energyInHundreds)) {
                return@withProtectedAccess
            }
            if (special.activate(this)) {
                deps.energy.takeSpecialEnergy(player, special.energyInHundreds)
                energyTaken = true
            }
        }
        return energyTaken
    }

    fun Player.energyLeft(): Int = vars[varps.sa_energy]

    fun melee(player: Player, type: MeleeAttackType = MeleeAttackType.Slash) =
        CombatAttack.Melee(
            player.weaponInv(),
            type,
            MeleeAttackStyle.Accurate,
            CombatStance.Stance1,
        )

    /**
     * Makes the melee special attack of the equipped weapon on [target] as the combat script does:
     * the engine checks the energy, the special runs, and the energy is taken if it says so.
     *
     * @return whether the special attack ran and took its energy.
     */
    fun GameTestScope.fireMelee(deps: Deps, target: PathingEntity): Boolean {
        val special = deps.registry[player.weaponInv()] as SpecialAttack.Melee
        var energyTaken = false
        player.withProtectedAccess {
            if (!deps.energy.hasSpecialEnergy(player, special.energyInHundreds)) {
                return@withProtectedAccess
            }
            val reduce = special.attack(this, target, melee(player))
            if (reduce) {
                deps.energy.takeSpecialEnergy(player, special.energyInHundreds)
                energyTaken = true
            }
        }
        return energyTaken
    }
}
