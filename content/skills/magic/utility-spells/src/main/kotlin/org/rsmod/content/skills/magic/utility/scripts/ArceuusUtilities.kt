package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.config.refs.varps
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseMagicLvl
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.herblore.configs.Herbs
import org.rsmod.content.skills.magic.utility.configs.arceuus_objs
import org.rsmod.content.skills.magic.utility.configs.arceuus_seqs
import org.rsmod.content.skills.magic.utility.configs.arceuus_spotanims
import org.rsmod.content.skills.magic.utility.configs.utility_timers
import org.rsmod.content.skills.prayer.configs.PrayerBones
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// The Arceuus spells that are not attacks, thralls or teleports:
// - Mark of Darkness makes the Arceuus attack spells stronger for three ticks of each base Magic
//   level (the damage formulas read the buff, see [ArceuusSpells] for the grasps)
// - Death Charge gives back 15% of the special attack when the caster lands a killing blow within a
//   minute of casting, once; it can be cast again after 100 ticks
// - Vile Vigour turns Prayer points into run energy, a point to each percent
// - Degrime cleans every grimy herb the caster has the Herblore level for, for half the experience
// - Demonic Offering offers up to three demonic ashes for three times their Prayer experience, and
//   Sinister Offering up to three bones, giving Prayer points back as well
//
// The Ward of Arceuus, Shadow Veil (pickpocketing), Dark Lure, the corruptions (they drain a
// player's Prayer), Resurrect Crops and the reanimation spells are in [DisabledSpells].
class ArceuusUtilities
@Inject
constructor(private val caster: SpellCaster, private val xpMods: XpModifiers) : PluginScript() {
    private var Player.markOfDarkness by intVarBit(varbits.buff_mark_of_darkness_disabled)
    private var Player.specialEnergy by intVarp(varps.sa_energy)

    /** The cycle each player's Death Charge runs out, and the cycle it can be cast again. */
    private val deathChargeUntil = WeakHashMap<Player, Int>()
    private val deathChargeReady = WeakHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        with(caster) {
            onSpell(objs.spell_mark_of_darkness) { markOfDarkness() }
            onSpell(objs.spell_death_charge) { deathCharge() }
            onSpell(objs.spell_vile_vigour) { vileVigour() }
            onSpell(objs.spell_degrime) { degrime() }
            onSpell(objs.spell_demonic_offering) { demonicOffering() }
            onSpell(objs.spell_sinister_offering) { sinisterOffering() }
        }
        onPlayerSoftTimer(utility_timers.mark_of_darkness) { fadeMark(player) }
        onEvent<NpcDeathEvents.Killed> { hero?.let { chargeKill(it, npc.coords.x, npc.coords.z) } }
        onPlayerLogout {
            fadeMark(player, quiet = true)
            deathChargeUntil.remove(player)
            deathChargeReady.remove(player)
        }
    }

    private fun ProtectedAccess.cast(spell: ObjType, cooldown: Int): Boolean =
        with(caster) { cast(spell, cooldown) } != null

    private fun ProtectedAccess.markOfDarkness() {
        if (!cast(objs.spell_mark_of_darkness, MARK_COOLDOWN)) {
            return
        }
        player.markOfDarkness = 1
        player.softTimer(
            utility_timers.mark_of_darkness,
            player.baseMagicLvl * MARK_TICKS_PER_LEVEL,
        )
        anim(arceuus_seqs.mark_of_darkness)
        spotanim(arceuus_spotanims.mark_of_darkness, height = SPOTANIM_HEIGHT)
        mes("You feel the dark mark settle on you.")
        publish(MarkCast(player))
    }

    private fun fadeMark(player: Player, quiet: Boolean = false) {
        player.clearSoftTimer(utility_timers.mark_of_darkness)
        if (player.markOfDarkness != 0) {
            player.markOfDarkness = 0
            if (!quiet) {
                player.mes("Your Mark of Darkness has faded away.")
            }
        }
    }

    private fun ProtectedAccess.deathCharge() {
        if (mapClock < (deathChargeReady[player] ?: Int.MIN_VALUE)) {
            mes("You can only cast Death Charge once every minute.")
            return
        }
        if (!cast(objs.spell_death_charge, 0)) {
            return
        }
        deathChargeReady[player] = mapClock + DEATH_CHARGE_COOLDOWN
        deathChargeUntil[player] = mapClock + DEATH_CHARGE_WINDOW
        anim(arceuus_seqs.death_charge)
        spotanim(arceuus_spotanims.death_charge, height = SPOTANIM_HEIGHT)
        publish(DeathChargeCast(player))
    }

    /** A kill by [hero] at the given tile: Death Charge, if it is on and the kill was near. */
    private fun chargeKill(hero: Player, x: Int, z: Int) {
        val until = deathChargeUntil[hero] ?: return
        if (hero.currentMapClock >= until) {
            deathChargeUntil.remove(hero)
            return
        }
        val near =
            maxOf(kotlin.math.abs(hero.coords.x - x), kotlin.math.abs(hero.coords.z - z)) <=
                KILL_RANGE
        if (!near) {
            return
        }
        deathChargeUntil.remove(hero)
        hero.specialEnergy = minOf(MAX_SPECIAL, hero.specialEnergy + DEATH_CHARGE_RESTORE)
        hero.mes("Death Charge restores some of your special attack energy.")
    }

    private fun ProtectedAccess.vileVigour() {
        val missing = MAX_RUN_ENERGY - player.runEnergy
        if (missing <= 0) {
            mes("You already have full run energy.")
            return
        }
        if (player.prayerLvl <= 0) {
            mes("You have no Prayer points to turn into run energy.")
            return
        }
        if (!cast(objs.spell_vile_vigour, VIGOUR_COOLDOWN)) {
            return
        }
        // A Prayer point is a percent of run energy, and run energy is kept in hundredths of one.
        val needed = (missing + RUN_PER_POINT - 1) / RUN_PER_POINT
        val used = minOf(needed, player.prayerLvl)
        statSub(stats.prayer, constant = used, percent = 0)
        player.runEnergy = minOf(MAX_RUN_ENERGY, player.runEnergy + used * RUN_PER_POINT)
        UpdateRun.energy(player, player.runEnergy)
        anim(arceuus_seqs.necromancy)
        publish(PrayerConverted(player, used))
    }

    private fun ProtectedAccess.degrime() {
        val herbs = Herbs.all.filter { invTotal(inv, it.grimy) > 0 }
        if (herbs.isEmpty()) {
            mes("You have no grimy herbs to clean.")
            return
        }
        val cleanable = herbs.filter { player.herbloreLvl >= it.cleanLevel }
        if (cleanable.isEmpty()) {
            mes("You do not have the Herblore level required to clean those herbs.")
            return
        }
        if (!cast(objs.spell_degrime, DEGRIME_COOLDOWN)) {
            return
        }
        var cleaned = 0
        for (herb in cleanable) {
            repeat(invTotal(inv, herb.grimy)) {
                val slot = inv.indexOfFirst { it?.id == herb.grimy.id }
                if (slot != -1 && invReplaceSlot(inv, slot, 1, herb.clean).success) {
                    statAdvance(
                        stats.herblore,
                        herb.cleanXp / 2 * xpMods.get(player, stats.herblore),
                    )
                    cleaned++
                }
            }
        }
        anim(arceuus_seqs.degrime)
        publish(HerbsCleaned(player, cleaned))
    }

    private fun ProtectedAccess.demonicOffering() {
        val ashes =
            inv.indices.filter { slot -> inv[slot]?.let { ASHES[it.id] } != null }.take(MAX_OFFERED)
        if (ashes.isEmpty()) {
            mes("You have no ashes to offer.")
            return
        }
        if (!cast(objs.spell_demonic_offering, OFFERING_COOLDOWN)) {
            return
        }
        var points = 0
        for (slot in ashes) {
            val ash = inv[slot] ?: continue
            val (xp, restore) = ASHES.getValue(ash.id)
            if (invDel(inv, objTypes(ash.id), 1, slot = slot).failure) {
                continue
            }
            statAdvance(stats.prayer, xp * OFFERING_MULTIPLIER * xpMods.get(player, stats.prayer))
            points += restore
        }
        statHeal(stats.prayer, constant = points, percent = 0)
        anim(arceuus_seqs.offering)
        publish(Offered(player, ashes.size))
    }

    private fun ProtectedAccess.sinisterOffering() {
        val bones =
            inv.indices
                .filter { slot -> inv[slot]?.let { PrayerBones.forObj(it.id) } != null }
                .take(MAX_OFFERED)
        if (bones.isEmpty()) {
            mes("You have no bones to offer.")
            return
        }
        if (!cast(objs.spell_sinister_offering, OFFERING_COOLDOWN)) {
            return
        }
        var points = 0
        for (slot in bones) {
            val held = inv[slot] ?: continue
            val bone = PrayerBones.forObj(held.id) ?: continue
            if (invDel(inv, bone.obj, 1, slot = slot).failure) {
                continue
            }
            statAdvance(
                stats.prayer,
                bone.xp * OFFERING_MULTIPLIER * xpMods.get(player, stats.prayer),
            )
            points += if (bone.xp >= BIG_BONE_XP) 2 else 1
        }
        statHeal(stats.prayer, constant = points, percent = 0)
        anim(arceuus_seqs.offering)
        publish(Offered(player, bones.size))
    }

    private fun objTypes(id: Int): ObjType = ASH_OBJS.first { it.id == id }

    /** Published once [player] has cast Mark of Darkness. */
    class MarkCast(val player: Player) : UnboundEvent

    /** Published once [player] has cast Death Charge. */
    class DeathChargeCast(val player: Player) : UnboundEvent

    /** Published once [player] has turned [points] Prayer points into run energy. */
    class PrayerConverted(val player: Player, val points: Int) : UnboundEvent

    /** Published once [player] has cleaned [count] herbs with Degrime. */
    class HerbsCleaned(val player: Player, val count: Int) : UnboundEvent

    /** Published once [player] has offered [count] ashes or bones. */
    class Offered(val player: Player, val count: Int) : UnboundEvent

    private companion object {
        const val SPOTANIM_HEIGHT = 92

        const val MARK_COOLDOWN = 10
        const val MARK_TICKS_PER_LEVEL = 3

        const val DEATH_CHARGE_COOLDOWN = 100
        const val DEATH_CHARGE_WINDOW = 100
        const val DEATH_CHARGE_RESTORE = 150
        const val MAX_SPECIAL = 1000
        const val KILL_RANGE = 16

        const val VIGOUR_COOLDOWN = 17
        const val MAX_RUN_ENERGY = 10_000
        const val RUN_PER_POINT = 100

        const val DEGRIME_COOLDOWN = 8

        const val MAX_OFFERED = 3
        const val OFFERING_COOLDOWN = 9
        const val OFFERING_MULTIPLIER = 3.0

        /** Bones worth this much burying experience give two Prayer points back, not one. */
        const val BIG_BONE_XP = 110.0

        val ASH_OBJS: List<ObjType> =
            listOf(
                arceuus_objs.fiendish_ashes,
                arceuus_objs.vile_ashes,
                arceuus_objs.malicious_ashes,
                arceuus_objs.abyssal_ashes,
                arceuus_objs.infernal_ashes,
            )

        /** Each ash's Prayer experience, and the Prayer points offering it gives back. */
        val ASHES: Map<Int, Pair<Double, Int>> by lazy {
            mapOf(
                arceuus_objs.fiendish_ashes.id to (10.0 to 1),
                arceuus_objs.vile_ashes.id to (25.0 to 1),
                arceuus_objs.malicious_ashes.id to (65.0 to 1),
                arceuus_objs.abyssal_ashes.id to (85.0 to 1),
                arceuus_objs.infernal_ashes.id to (110.0 to 2),
            )
        }
    }
}
