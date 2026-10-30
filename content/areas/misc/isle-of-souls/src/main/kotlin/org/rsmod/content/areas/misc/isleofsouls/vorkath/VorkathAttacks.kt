package org.rsmod.content.areas.misc.isleofsouls.vorkath

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.mastery.CombatMasteryEffects
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.areas.misc.isleofsouls.zulrah.PlayerVenom
import org.rsmod.content.other.dragonfire.Breath
import org.rsmod.content.other.dragonfire.Dragonfire
import org.rsmod.content.other.dragonfire.DragonfireProtection
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.proj.ProjAnimType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.map.CoordGrid

/** Vorkath's standard attacks (OSRS Wiki, "Vorkath/Strategies"). */
enum class VorkathAttack {
    Melee,
    Ranged,
    Magic,
    Dragonfire,
    Venom,
    Corrupting,
    Fireball,
}

/**
 * How Vorkath's attacks look and what they do. [Vorkath] decides when it attacks and which attack,
 * and times the ones that land on a tile (the fireball, the acid special's rapid fire and pools,
 * the spawn's explosion); this class sends the animations and projectiles and deals the damage.
 * - **Melee** (slash, up to 32) only next to it; **ranged** (up to 32) and **magic** (up to 30)
 *   roll against the player's defence, and the protection prayer stops them.
 * - **Dragonfire**: the fiery, venomous and corrupting breaths all use [Breath.Vorkath]. The
 *   venomous one envenoms whatever the protection; the corrupting one turns every prayer off.
 * - **Fireball**: aimed at the tile the player stands on. Whoever is on it when it lands takes up
 *   to [FIREBALL_MAX], whoever is next to it half that, whatever their protection.
 *
 * The mix of attacks is tsps' share of 3,734 captured attacks (rsprox): ranged 30, magic 24,
 * dragonfire 15, venom 11, fireball 11, corrupting 8, and next to it melee 19 on top.
 */
class VorkathAttacks
@Inject
constructor(
    private val attacks: NpcAttacks,
    private val accuracy: AccuracyFormulae,
    private val dragonfire: Dragonfire,
    private val venom: PlayerVenom,
    private val masteries: CombatMasteryEffects,
    private val worldRepo: WorldRepository,
    private val random: GameRandom,
) {
    /** Picks the next standard attack. [adjacent] is whether the target stands next to it. */
    fun choose(adjacent: Boolean): VorkathAttack {
        val table = if (adjacent) listOf(VorkathAttack.Melee to MELEE_WEIGHT) + WEIGHTS else WEIGHTS
        var pick = random.of(table.sumOf { it.second })
        for ((attack, weight) in table) {
            pick -= weight
            if (pick < 0) {
                return attack
            }
        }
        return VorkathAttack.Magic
    }

    /** [npc] attacks [target] with [attack], any of them but the [VorkathAttack.Fireball]. */
    fun standard(npc: Npc, target: Player, attack: VorkathAttack) {
        when (attack) {
            VorkathAttack.Melee -> attacks.attack(npc, target, MELEE)
            VorkathAttack.Ranged -> attacks.attack(npc, target, RANGED)
            VorkathAttack.Magic -> attacks.attack(npc, target, MAGIC)
            VorkathAttack.Dragonfire -> breathe(npc, target, FIERY, null)
            VorkathAttack.Venom -> breathe(npc, target, VENOMOUS) { venom.envenom(it) }
            VorkathAttack.Corrupting ->
                breathe(npc, target, CORRUPTING) {
                    it.disablePrayers()
                    it.mes(PRAYERS_DISABLED)
                }
            VorkathAttack.Fireball -> error("The fireball is launched with `launchFireball`")
        }
    }

    /**
     * A breath of dragonfire: the damage is worked out when it lands, from the protection the
     * player has on then ([Dragonfire.resolve]); [effect] is what the breath does besides burn.
     */
    private fun breathe(npc: Npc, target: Player, attack: NpcAttack, effect: ((Player) -> Unit)?) {
        val burnt = accuracy.rollMagicAccuracy(npc, target, random)
        attacks.attack(
            npc,
            target,
            attack,
            onImpact =
                NpcImpactListener { player, hit ->
                    dragonfire.resolve(player, hit, Breath.Vorkath, burnt)
                    effect?.invoke(player)
                },
        )
    }

    /** Launches the fireball at [tile]. Returns the ticks until it lands. */
    fun launchFireball(npc: Npc, tile: CoordGrid): Int {
        npc.anim(vorkath_seqs.lob)
        worldRepo.spotanimMap(vorkath_spotanims.fireball_shadow, tile)
        return flight(npc, tile, vorkath_spotanims.fireball_travel, vorkath_projanims.fireball)
    }

    /** What the fireball's [roll] does to a player [distance] tiles from where it landed. */
    fun fireballDamage(roll: Int, distance: Int): Int =
        when (distance) {
            0 -> roll
            1 -> roll / 2
            else -> 0
        }

    fun rollFireball(): Int = random.of(0..FIREBALL_MAX)

    /** The fireball lands on [tile]. */
    fun fireballLands(tile: CoordGrid) {
        worldRepo.spotanimMap(vorkath_spotanims.fire_impact, tile, height = FIREBALL_IMPACT_HEIGHT)
    }

    /** Spits acid at every one of [tiles]. */
    fun spitAcid(npc: Npc, tiles: List<CoordGrid>) {
        npc.anim(vorkath_seqs.acid)
        for (tile in tiles) {
            worldRepo.projAnim(npc, tile, vorkath_spotanims.acid_travel, vorkath_projanims.acid)
        }
    }

    /** One shot of the rapid fire at [tile]. */
    fun rapidShot(npc: Npc, tile: CoordGrid) {
        worldRepo.projAnim(npc, tile, vorkath_spotanims.rapid_travel, vorkath_projanims.rapid)
    }

    fun rapidLands(tile: CoordGrid) {
        worldRepo.spotanimMap(vorkath_spotanims.rapid_impact, tile, height = RAPID_IMPACT_HEIGHT)
    }

    fun rollRapid(): Int = random.of(RAPID_MIN..RAPID_MAX)

    fun rollPool(): Int = random.of(POOL_MIN..POOL_MAX)

    /** The icy breath at [target]. Returns the ticks until it lands and freezes them. */
    fun iceBreath(npc: Npc, target: Player): Int {
        npc.anim(vorkath_seqs.breathe)
        val flight =
            worldRepo.projAnim(npc, target, vorkath_spotanims.ice_travel, vorkath_projanims.shot)
        target.spotanim(vorkath_spotanims.freeze_impact, delay = flight.durations.clientDelay)
        return maxOf(1, flight.durations.serverDelay)
    }

    /** Throws the spawn high into the air to [tile]. Returns the ticks until it lands. */
    fun throwSpawn(npc: Npc, tile: CoordGrid): Int {
        npc.anim(vorkath_seqs.lob)
        return flight(npc, tile, vorkath_spotanims.spawn_travel, vorkath_projanims.spawn)
    }

    /** What the spawn's explosion does with [hitpoints] left: up to 60 at its full 38 (wiki). */
    fun explosionDamage(hitpoints: Int): Int =
        hitpoints.coerceIn(0, Vorkath.SPAWN_HITPOINTS) * EXPLOSION_MAX / Vorkath.SPAWN_HITPOINTS

    fun explode(tile: CoordGrid) {
        worldRepo.spotanimMap(vorkath_spotanims.spawn_explosion, tile, height = EXPLOSION_HEIGHT)
    }

    /**
     * Hits [player] for [damage] that nothing protects against, a tick from now, from [npc].
     * Returns the damage after the combat mastery that lowers damage taken.
     */
    fun hurt(npc: Npc, player: Player, damage: Int): Int {
        if (damage <= 0 || !player.isValidTarget()) {
            return 0
        }
        val reduced = minOf(masteries.reduceDamageTaken(player, damage), player.hitpoints)
        player.queueImpactHit(npc, 1, HitType.Typeless, reduced)
        return reduced
    }

    private fun flight(npc: Npc, tile: CoordGrid, spot: SpotanimType, type: ProjAnimType): Int =
        maxOf(1, worldRepo.projAnim(npc, tile, spot, type).durations.serverDelay)

    companion object {
        const val MELEE_MAX = 32
        const val RANGED_MAX = 32
        const val MAGIC_MAX = 30
        const val FIREBALL_MAX = 121

        /** The rapid fire hits for 25-41 whatever the player's protection (wiki). */
        const val RAPID_MIN = 25
        const val RAPID_MAX = 41

        /** Standing in acid: up to 10 a tick, which Vorkath heals (wiki). 1 at least: tsps. */
        const val POOL_MIN = 1
        const val POOL_MAX = 10
        const val EXPLOSION_MAX = 60

        const val MELEE_WEIGHT = 19
        val WEIGHTS =
            listOf(
                VorkathAttack.Ranged to 30,
                VorkathAttack.Magic to 24,
                VorkathAttack.Dragonfire to 15,
                VorkathAttack.Venom to 11,
                VorkathAttack.Fireball to 11,
                VorkathAttack.Corrupting to 8,
            )

        const val PRAYERS_DISABLED = "<col=ff3045>Your prayers have been disabled!</col>"

        /** Graphic heights on the tile, as captured. */
        const val FIREBALL_IMPACT_HEIGHT = 38
        const val RAPID_IMPACT_HEIGHT = 30
        const val EXPLOSION_HEIGHT = 92

        val MELEE =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                anim = vorkath_seqs.melee,
                meleeType = MeleeAttackType.Slash,
                maxHit = MELEE_MAX,
            )

        val RANGED =
            NpcAttack(
                style = NpcAttackStyle.Ranged,
                anim = vorkath_seqs.breathe,
                projectile = vorkath_spotanims.ranged_travel,
                projanim = vorkath_projanims.shot,
                impactSpot = vorkath_spotanims.ranged_impact,
                maxHit = RANGED_MAX,
            )

        val MAGIC =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = vorkath_seqs.breathe,
                projectile = vorkath_spotanims.magic_travel,
                projanim = vorkath_projanims.shot,
                impactSpot = vorkath_spotanims.magic_impact,
                maxHit = MAGIC_MAX,
            )

        private fun breath(travel: SpotanimType, impact: SpotanimType): NpcAttack =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = vorkath_seqs.breathe,
                projectile = travel,
                projanim = vorkath_projanims.shot,
                impactSpot = impact,
                // A placeholder: the real damage is worked out when the breath lands.
                maxHit = Breath.Vorkath.maxHit(DragonfireProtection.NONE, burntRoll = true),
                alwaysHits = true,
                typeless = true,
            )

        val FIERY = breath(vorkath_spotanims.fire_travel, vorkath_spotanims.fire_impact)
        val VENOMOUS = breath(vorkath_spotanims.venom_travel, vorkath_spotanims.venom_impact)
        val CORRUPTING = breath(vorkath_spotanims.corrupt_travel, vorkath_spotanims.corrupt_impact)
    }
}
