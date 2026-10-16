package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerInit
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object ZamorakSeqs : SeqReferences() {
    val kril_magic = find("godwars_zamorak_magic_attack")
    val zakln_throw = find("godwars_zamorak_bdygrd_ranged")
    val balfrug_cast = find("demon_update_fireball_cast")
}

object ZamorakSpotanims : SpotanimReferences() {
    val kril_cast = find("godwars_zamorak_magic_attack_spot")
    val kril_travel = find("godwars_zamorak_magic_attack_proj")
    val zakln_cast = find("godwars_zamarok_bdygrd_ranged_spot")
    val zakln_travel = find("godwars_zamorak_bdygrd_ranged_proj")
    val balfrug_cast = find("godwars_black_demon_fireball_spot")
    val balfrug_travel = find("godwars_black_demon_fireball_proj")
}

/**
 * K'ril Tsutsaroth and his three demon bodyguards (OSRS Wiki: K'ril Tsutsaroth, its Strategies
 * page, and the bodyguards' pages).
 *
 * **K'ril** attacks every 6 ticks, only from next to his target and only one player at a time:
 * - 2 in 3: a **melee** swipe, up to 46 (slash, Protect from Melee). A swipe that lands can poison
 *   the target, even through Protect from Melee ([PlayerPoison], starting at 16);
 * - 1 in 3: a **magic** hit, 10 to 30 when it lands (Protect from Magic);
 * - when the target is praying Protect from Melee, 1 melee swipe in 9 is the **prayer smash**
 *   instead: he yells "YARRRRRRR!", it always hits for 35 to 49 whatever the prayer, and it drains
 *   half the target's Prayer points (a quarter with a spectral spirit shield), rounded down.
 *
 * **The bodyguards** attack every 5 ticks: Tstanon Karlak in melee (crush, up to 15), Zakl'n Gritch
 * with ranged snowballs (up to 21) and Balfrug Kreeyath with magic fireballs (up to 16). When K'ril
 * dies each of them goes for a random player in the chamber, whoever struck the last blow.
 *
 * When the chamber empties all four heal to full and their stats are restored.
 *
 * The animations, graphics, projectile flights and sounds are the captured ones (rsprox), via
 * [GodWarsProjAnims] and [GodWarsSynths]. Guesses where the Wiki is silent: how often a landed
 * swipe poisons (1 in 4).
 */
class GodWarsZamorakScript
@Inject
constructor(
    private val attacks: NpcAttacks,
    private val accuracy: AccuracyFormulae,
    private val random: GameRandom,
    private val rooms: GodRooms,
    private val npcList: NpcList,
    private val npcTypes: NpcTypeList,
    private val objTypes: ObjTypeList,
    private val interactions: AiPlayerInteractions,
    private val poison: PlayerPoison,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val kril = GodWarsRoster.byName.getValue(GodWarsNames.KRIL)
        onAiOpPlayer2(kril) { fightKril(it.target) }
        val krilId = npcTypes[kril].id

        val tstanon = GodWarsRoster.byName.getValue(GodWarsNames.TSTANON)
        onAiOpPlayer2(tstanon) { bodyguard(it.target, TSTANON_MELEE) }
        val zakln = GodWarsRoster.byName.getValue(GodWarsNames.ZAKLN)
        onAiApPlayer2(zakln) { bodyguard(it.target, ZAKLN_RANGED) }
        onAiOpPlayer2(zakln) { bodyguard(it.target, ZAKLN_RANGED) }
        val balfrug = GodWarsRoster.byName.getValue(GodWarsNames.BALFRUG)
        onAiApPlayer2(balfrug) { bodyguard(it.target, BALFRUG_MAGIC) }
        onAiOpPlayer2(balfrug) { bodyguard(it.target, BALFRUG_MAGIC) }

        onEvent<NpcDeathEvents.Killed> {
            if (npc.type.id == krilId) {
                refocusBodyguards(npc)
            }
        }

        for (name in GodWarsNames.zamorakLeaders) {
            onAiTimer(GodWarsRoster.byName.getValue(name)) { healIfEmpty(npc) }
        }

        onPlayerSoftTimer(PoisonTimers.poison) {
            if (!poison.tick(player)) {
                player.clearSoftTimer(PoisonTimers.poison)
            }
        }
        onPlayerInit { poison.resume(player) }
    }

    private fun StandardNpcAccess.fightKril(target: Player) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        when {
            random.of(MAGIC_ODDS) == 0 -> attacks.attack(npc, target, KRIL_MAGIC)
            smashes(target) -> smash(target)
            else -> swipe(target)
        }
        actionDelay = mapClock + KRIL_RATE
    }

    /** Whether the melee swipe against [target] is the prayer smash this time. */
    fun smashes(target: Player): Boolean =
        target.vars[varbits.protect_from_melee] == 1 && random.of(SMASH_ODDS) == 0

    private fun StandardNpcAccess.smash(target: Player) {
        npc.say(SMASH_SHOUT)
        attacks.attack(npc, target, KRIL_SMASH)
        val drain = prayerDrain(target)
        if (drain > 0) {
            target.statSub(stats.prayer, drain, 0)
        }
    }

    /** The Prayer points the smash takes from [target]: half, or a quarter with the shield. */
    fun prayerDrain(target: Player): Int {
        val divisor = if (wearsSpectralShield(target)) 4 else 2
        return target.stat(stats.prayer) / divisor
    }

    private fun wearsSpectralShield(target: Player): Boolean =
        target.worn.objs.any { it != null && objTypes[it].internalName == SPECTRAL_SHIELD }

    /** The melee swipe: accuracy rolled once so that a landed swipe can poison even if prayed. */
    private fun StandardNpcAccess.swipe(target: Player) {
        val lands = accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Slash, random)
        val attack = KRIL_MELEE.copy(alwaysHits = true, maxHit = if (lands) KRIL_MELEE_MAX else 0)
        val onImpact =
            if (lands && random.of(POISON_ODDS) == 0) {
                NpcImpactListener { victim, _ -> poison.poison(victim, KRIL_POISON) }
            } else {
                null
            }
        attacks.attack(npc, target, attack, onImpact = onImpact)
    }

    private fun StandardNpcAccess.bodyguard(target: Player, attack: NpcAttack) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        attacks.attack(npc, target, attack)
        actionDelay = mapClock + BODYGUARD_RATE
    }

    /** Sends each bodyguard in [kril]'s room after a random player in that room. */
    fun refocusBodyguards(kril: Npc) {
        val room = rooms.roomOf(GodChambers.zamorak, kril.coords) ?: return
        val inside = rooms.playersIn(room)
        if (inside.isEmpty()) {
            return
        }
        val names = setOf(GodWarsNames.TSTANON, GodWarsNames.ZAKLN, GodWarsNames.BALFRUG)
        for (guard in npcList) {
            if (guard.visType.internalName !in names || guard.hitpoints <= 0) {
                continue
            }
            if (rooms.roomOf(GodChambers.zamorak, guard.coords) != room) {
                continue
            }
            val target = inside[random.of(inside.size)]
            if (guard.visType.internalName == GodWarsNames.TSTANON) {
                guard.opPlayer2(target, interactions)
            } else {
                guard.apPlayer2(target, interactions)
            }
        }
    }

    /** Heals [npc] to full when nobody is in its room any more. */
    fun healIfEmpty(npc: Npc) {
        val room = rooms.checkIn(npc, GodChambers.zamorak) ?: return
        if (rooms.isEmpty(room)) {
            npc.resetForEmptyRoom()
        }
    }

    companion object {
        const val KRIL_RATE = 6
        const val BODYGUARD_RATE = 5
        const val KRIL_MELEE_MAX = 46
        const val KRIL_MAGIC_MIN = 10
        const val KRIL_MAGIC_MAX = 30
        const val SMASH_MIN = 35
        const val SMASH_MAX = 49
        const val KRIL_POISON = 16

        /** One attack in this many is his magic; the rest are melee. */
        const val MAGIC_ODDS = 3

        /** One melee swipe in this many is the prayer smash, against a target praying melee. */
        const val SMASH_ODDS = 9

        /** One landed swipe in this many poisons (a guess; the Wiki gives no chance). */
        const val POISON_ODDS = 4

        const val SMASH_SHOUT = "YARRRRRRR!"

        /** The cache name of the spectral spirit shield. */
        const val SPECTRAL_SHIELD = "spectral"

        val KRIL_MELEE =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                meleeType = MeleeAttackType.Slash,
                sound = GodWarsSynths.kril_melee,
            )

        val KRIL_MAGIC =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = ZamorakSeqs.kril_magic,
                sound = GodWarsSynths.kril_magic,
                castSpot = ZamorakSpotanims.kril_cast,
                castHeight = 30,
                projectile = ZamorakSpotanims.kril_travel,
                projanim = GodWarsProjAnims.kril_flames,
                minHit = KRIL_MAGIC_MIN,
                maxHit = KRIL_MAGIC_MAX,
            )

        /** Typeless, so no protection prayer touches it, and it never misses. */
        val KRIL_SMASH =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                meleeType = MeleeAttackType.Slash,
                sound = GodWarsSynths.kril_melee,
                minHit = SMASH_MIN,
                maxHit = SMASH_MAX,
                alwaysHits = true,
                typeless = true,
            )

        val TSTANON_MELEE =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                meleeType = MeleeAttackType.Crush,
                sound = GodWarsSynths.tstanon_attack,
                maxHit = 15,
            )

        val ZAKLN_RANGED =
            NpcAttack(
                style = NpcAttackStyle.Ranged,
                anim = ZamorakSeqs.zakln_throw,
                sound = GodWarsSynths.kril_magic,
                castSpot = ZamorakSpotanims.zakln_cast,
                projectile = ZamorakSpotanims.zakln_travel,
                projanim = GodWarsProjAnims.goblin_axe,
                maxHit = 21,
            )

        val BALFRUG_MAGIC =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = ZamorakSeqs.balfrug_cast,
                sound = GodWarsSynths.kril_magic,
                castSpot = ZamorakSpotanims.balfrug_cast,
                castHeight = 30,
                projectile = ZamorakSpotanims.balfrug_travel,
                projanim = GodWarsProjAnims.balfrug_fireball,
                maxHit = 16,
            )
    }
}
