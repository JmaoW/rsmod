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
import org.rsmod.content.skills.prayer.configs.PrayerQueues
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
    /** The bone each player is currently offering, so the loop knows what to keep offering. */
    private val offering = WeakHashMap<Player, PrayerBone>()

    override fun ScriptContext.startup() {
        for (type in locTypes.values) {
            val kind = altarKind(type) ?: continue
            onOpLoc1(type) { pray() }
            onOpLocU(type) { startOffering(it.loc, it.objType) }
            onOpLoc4(type) { offerNext(it.loc, kind) }
        }
        onPlayerQueue(PrayerQueues.recharge) { recharge() }
    }

    /**
     * The player kneels for [PRAY_TICKS] and the points come back from a weak queue, not a delay,
     * so walking away stops the prayer instead of the player being held in place.
     */
    private fun ProtectedAccess.pray() {
        if (player.prayerLvl >= player.basePrayerLvl) {
            mes("You already have full Prayer points.")
            return
        }

        anim(PrayerSeqs.pray)
        weakQueue(PrayerQueues.recharge, PRAY_TICKS)
    }

    private fun ProtectedAccess.recharge() {
        val restored = player.basePrayerLvl - player.prayerLvl
        statRestore(stats.prayer)
        mes("You recharge your Prayer points.")
        if (restored > 0) {
            publish(PrayerRestored(player, restored))
        }
    }

    private fun ProtectedAccess.startOffering(loc: BoundLocInfo, obj: ObjType) {
        val bone = PrayerBones.forObj(obj)
        if (bone == null) {
            mes("Nothing interesting happens.")
            return
        }

        offering[player] = bone
        actionDelay = mapClock + OFFER_DELAY
        skillAnimDelay = mapClock + ANIM_INTERVAL
        anim(PrayerSeqs.offer)
        opLoc4(loc)
    }

    private fun ProtectedAccess.offerNext(loc: BoundLocInfo, kind: AltarKind) {
        val bone = offering[player] ?: return
        if (bone.obj !in inv) {
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
            offerOne(bone, kind, loc)
            if (bone.obj !in inv) {
                stop()
                return
            }
        }

        opLoc4(loc)
    }

    private fun ProtectedAccess.offerOne(bone: PrayerBone, kind: AltarKind, loc: BoundLocInfo) {
        val saved = kind == AltarKind.Chaos && random.randomBoolean()
        if (!saved && invDel(inv, bone.obj, 1).failure) {
            return
        }

        val xp = bone.xp * OFFER_MULTIPLIER * xpMods.get(player, stats.prayer)
        statAdvance(stats.prayer, xp)
        spotanimMap(world, PrayerSpotanims.offer, loc.coords)
        if (saved) {
            spam(SPARED_MESSAGE)
        } else {
            spam(OFFERED_MESSAGE)
        }
        publish(BoneOffered(player, bone.obj, kind, saved))
    }

    private fun ProtectedAccess.stop() {
        offering.remove(player)
        resetAnim()
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

        /** Ticks the player kneels before the points come back. */
        private const val PRAY_TICKS = 2

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
