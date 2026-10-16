package org.rsmod.content.skills.prayer.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.basePrayerLvl
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.prayer.configs.AltarKind
import org.rsmod.content.skills.prayer.configs.PrayerBone
import org.rsmod.content.skills.prayer.configs.PrayerBones
import org.rsmod.content.skills.prayer.configs.PrayerSeqs
import org.rsmod.content.skills.prayer.configs.PrayerSpotanims
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the altar and offering sounds (there are no named synths for them in the symbols yet)
// - gilded altars, incense burners, the Ectofuntus and player-owned house altars
// - praying at an altar also resets the prayer drain timer in the real game
//
// Every loc named "Altar" or "Chaos altar" that has a "Pray-at" option is handled here, wherever it
// stands. Bones are offered by using them on the altar: every bone gives 3.5 times its burying
// experience, and the chaos altar saves half of the bones. Offering continues through the otherwise
// unused op4 of the altar once the first bone has been started, the same way cooking keeps going.
class Altars
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val xpMods: XpModifiers,
    private val world: WorldRepository,
) : PluginScript() {
    /** What each player is currently offering, so the loop knows what to keep offering. */
    private val offering = WeakHashMap<Player, Offering>()

    override fun ScriptContext.startup() {
        for (type in locTypes.values) {
            val kind = altarKind(type) ?: continue
            onOpLoc1(type) { pray() }
            onOpLocU(type) { startOffering(it.loc, it.objType) }
            onOpLoc4(type) { offerNext(it.loc, kind) }
        }
    }

    /**
     * The points come back as soon as the prayer animation starts, so the player is never held
     * in place waiting for it to finish.
     */
    private fun ProtectedAccess.pray() {
        if (player.prayerLvl >= player.basePrayerLvl) {
            mes("You already have full Prayer points.")
            return
        }

        val restored = player.basePrayerLvl - player.prayerLvl
        anim(PrayerSeqs.pray)
        statRestore(stats.prayer)
        mes("You recharge your Prayer points.")
        if (restored > 0) {
            publish(PrayerRestored(player, restored))
        }
    }

    private fun ProtectedAccess.startOffering(loc: BoundLocInfo, obj: ObjType) {
        val bone = PrayerBones.forObj(obj)
        val current =
            if (bone != null) Offering(bone.obj, 1, bone.xp, bone) else customOffering(obj)
        if (current == null) {
            mes("Nothing interesting happens.")
            return
        }

        offering[player] = current
        actionDelay = mapClock + OFFER_DELAY
        skillAnimDelay = mapClock + ANIM_INTERVAL
        anim(PrayerSeqs.offer)
        opLoc4(loc)
    }

    /**
     * What [obj], which is not a bone, can be offered as, if anything says so by changing the
     * [OfferRule] (the Golden God relic does this for coins).
     */
    private fun ProtectedAccess.customOffering(obj: ObjType): Offering? {
        val rule = OfferRule(player, obj)
        publish(rule)
        val like = rule.experienceLike ?: return null
        return Offering(obj, rule.count, like.xp, bone = null)
    }

    private fun ProtectedAccess.offerNext(loc: BoundLocInfo, kind: AltarKind) {
        val current = offering[player] ?: return
        if (invTotal(inv, current.obj) < current.count) {
            stop()
            return
        }

        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + ANIM_INTERVAL
            anim(PrayerSeqs.offer)
        }

        if (actionDelay < mapClock) {
            actionDelay = mapClock + OFFER_DELAY
        } else if (actionDelay == mapClock) {
            offerOne(current, kind, loc)
            if (invTotal(inv, current.obj) < current.count) {
                stop()
                return
            }
        }

        opLoc4(loc)
    }

    private fun ProtectedAccess.offerOne(current: Offering, kind: AltarKind, loc: BoundLocInfo) {
        val saved = kind == AltarKind.Chaos && random.randomBoolean()
        if (!saved && invDel(inv, current.obj, current.count).failure) {
            return
        }

        val xp = current.xp * OFFER_MULTIPLIER * xpMods.get(player, stats.prayer)
        statAdvance(stats.prayer, xp)
        spotanimMap(world, PrayerSpotanims.offer, loc.coords)
        if (saved) {
            spam(SPARED_MESSAGE)
        } else {
            spam(OFFERED_MESSAGE)
        }
        if (current.bone != null) {
            publish(BoneOffered(player, current.bone.obj, kind, saved))
        }
    }

    private fun ProtectedAccess.stop() {
        offering.remove(player)
        resetAnim()
    }

    /**
     * What is being offered: [count] of [obj] for each offering, worth the experience of a bone
     * with [xp] experience when buried. [bone] is the bone itself, or `null` for something that
     * only counts as one.
     */
    private class Offering(val obj: ObjType, val count: Int, val xp: Double, val bone: PrayerBone?)

    /**
     * Published when something that is not a bone is used on an altar, so that a relic can allow it
     * to be offered. Nothing is by default. To allow it, set [experienceLike] to the bone whose
     * experience each offering gives, and [count] to how many are used up by each offering.
     */
    class OfferRule(val player: Player, val obj: ObjType) : UnboundEvent {
        var count: Int = 1
        var experienceLike: PrayerBone? = null
    }

    /** Published once a bone has been offered on an altar, whether or not the bone was saved. */
    class BoneOffered(
        val player: Player,
        val bone: ObjType,
        val altar: AltarKind,
        val saved: Boolean,
    ) : UnboundEvent

    /** Published when praying at an altar gave back [points] Prayer points. */
    class PrayerRestored(val player: Player, val points: Int) : UnboundEvent

    companion object {
        /** The experience each offered bone gives, as a multiple of its burying experience. */
        const val OFFER_MULTIPLIER = 3.5

        private const val OFFERED_MESSAGE = "The gods are pleased with your offering."
        private const val SPARED_MESSAGE =
            "The Dark Lord spares your sacrifice but still rewards you for your efforts."

        /**
         * Ticks to wait before each bone is offered, minus one. The loop spends one extra tick
         * between bones, so this gives a three tick interval between offered bones.
         */
        private const val OFFER_DELAY = 2
        private const val ANIM_INTERVAL = 4

        internal fun altarKind(type: UnpackedLocType): AltarKind? {
            if (type.op.getOrNull(0) != "Pray-at") {
                return null
            }
            return when {
                type.name.equals("Chaos altar", ignoreCase = true) -> AltarKind.Chaos
                type.name.equals("Altar", ignoreCase = true) -> AltarKind.Standard
                else -> null
            }
        }
    }
}
