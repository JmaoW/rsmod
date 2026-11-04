package org.rsmod.api.combat.manager

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.ranged.RangedAmmunition
import org.rsmod.api.combat.mastery.CombatMasteryEffects
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

public class RangedAmmoManager
@Inject
constructor(
    private val random: GameRandom,
    private val eventBus: EventBus,
    private val objTypes: ObjTypeList,
    private val collision: CollisionFlagMap,
    private val worldQueues: WorldQueueList,
    private val objRepo: ObjRepository,
    private val masteries: CombatMasteryEffects,
    private val pouches: AmmoPouches,
) {
    /**
     * Lets any [AmmoPouch] put the ammunition [weapon] fires into [player]'s ammo slot. Call it
     * before the attack reads the slot.
     */
    public fun readyAmmo(player: Player, weapon: UnpackedObjType): Unit =
        pouches.ready(player, weapon)

    /**
     * Validates whether the given [weapon] and [ammo] combination are usable by [player].
     *
     * This function verifies that the provided ammunition is appropriate for the weapon type (e.g.,
     * bolts for crossbows, arrows for bows) and that [player] has the required level to use them.
     * If validation fails, an appropriate error message is sent to the player.
     *
     * **Note:** This function only performs validation and messaging. It does **not** remove
     * ammunition or handle ammo dropping - use [useQuiverAmmo] or [useThrownWeapon] for that
     * behavior.
     *
     * @return `true` if the weapon and ammo combination is valid or the weapon doesn't require
     *   specific ammo.
     * @see [RangedAmmunition.attemptAmmoUsage]
     */
    public fun attemptAmmoUsage(
        player: Player,
        weapon: UnpackedObjType,
        ammo: UnpackedObjType?,
    ): Boolean = RangedAmmunition.attemptAmmoUsage(player, weapon, ammo)

    /**
     * Consumes ammunition from the player's `quiver` slot, deducting it and handling possible ammo
     * drops.
     *
     * This function rolls once: [RangedAmmunition.recoveryRate] percent of shots (Ava's devices)
     * are recovered and use no ammunition, [RangedAmmunition.AMMO_BREAK_PERCENT] percent break, and
     * the rest are deducted from the `quiver` slot and dropped on the ground.
     *
     * @param quiverType The type of ammunition used from the `quiver` slot. If the `quiver` slot
     *   obj does **not** match this type, an [IllegalStateException] will be thrown.
     * @param dropCoord The coordinate where the ammo drop should land when applicable.
     * @param dropDelay The delay in cycles before the ammo appears on the ground.
     * @param dropDuration The duration in cycles that the dropped ammo will remain on the ground.
     * @param useCount The number of ammo units to consume from the `quiver` slot. If the `quiver`
     *   slot obj does not have at least this value as its `count`, an [IllegalStateException] will
     *   be thrown.
     */
    public fun useQuiverAmmo(
        player: Player,
        quiverType: UnpackedObjType,
        dropCoord: CoordGrid,
        dropDelay: Int,
        dropDuration: Int = RangedAmmunition.DEFAULT_AMMO_DROP_DURATION,
        useCount: Int = 1,
    ): Unit =
        useAmmo(
            player = player,
            ammoWearpos = Wearpos.Quiver,
            ammoType = quiverType,
            ammoCount = useCount,
            dropCoord = dropCoord,
            dropDelay = dropDelay,
            dropDuration = dropDuration,
        )

    /**
     * Consumes ammunition from the player's `righthand` slot (for thrown weapons), deducting it and
     * handling possible ammo drops.
     *
     * This function rolls once: [RangedAmmunition.recoveryRate] percent of throws (Ava's devices)
     * are recovered and use no ammunition, [RangedAmmunition.AMMO_BREAK_PERCENT] percent break, and
     * the rest are deducted from the `righthand` slot and dropped on the ground.
     *
     * @param weaponType The type of thrown weapon used from the `righthand` slot. If the
     *   `righthand` slot obj does **not** match this type, an [IllegalStateException] will be
     *   thrown.
     * @param dropCoord The coordinate where the ammo drop should land when applicable.
     * @param dropDelay The delay in cycles before the ammo appears on the ground.
     * @param dropDuration The duration in cycles that the dropped ammo will remain on the ground.
     * @param useCount The number of thrown weapon units to consume from the `righthand` slot. If
     *   the slot obj does not have at least this value as its `count`, an [IllegalStateException]
     *   will be thrown.
     */
    public fun useThrownWeapon(
        player: Player,
        weaponType: UnpackedObjType,
        dropCoord: CoordGrid,
        dropDelay: Int,
        dropDuration: Int = RangedAmmunition.DEFAULT_AMMO_DROP_DURATION,
        useCount: Int = 1,
    ): Unit =
        useAmmo(
            player = player,
            ammoWearpos = Wearpos.RightHand,
            ammoType = weaponType,
            ammoCount = useCount,
            dropCoord = dropCoord,
            dropDelay = dropDelay,
            dropDuration = dropDuration,
        )

    private fun useAmmo(
        player: Player,
        ammoWearpos: Wearpos,
        ammoType: UnpackedObjType,
        ammoCount: Int,
        dropCoord: CoordGrid,
        dropDelay: Int,
        dropDuration: Int,
    ) {
        if (masteries.savesResource(player)) {
            return
        }

        val roll = random.of(maxExclusive = 100)
        if (roll < RangedAmmunition.recoveryRate(player, objTypes)) {
            return
        }

        RangedAmmunition.detractAmmo(player, ammoWearpos, ammoType, ammoCount, eventBus)

        val broken = roll >= 100 - RangedAmmunition.AMMO_BREAK_PERCENT
        if (!broken) {
            RangedAmmunition.attemptAmmoDrop(
                player = player,
                delay = dropDelay,
                ammoType = ammoType,
                ammoCount = ammoCount,
                dropCoord = dropCoord,
                dropDuration = dropDuration,
                collision = collision,
                worldQueues = worldQueues,
                objRepo = objRepo,
            )
        }
    }
}
