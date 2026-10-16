package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.NpcMeleeBlocks
import org.rsmod.api.combat.NpcTargeting
import org.rsmod.api.combat.TargetingStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onAiTimer
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Kree'arra and her three bodyguards (OSRS Wiki), and the rule that none of the Armadylean monsters
 * can be meleed.
 *
 * **Kree'arra** attacks every 3 ticks:
 * - her usual attacks hit every player in the chamber at once, each with their own roll, and push
 *   the player one tile away from her unless they stand in a corner: a **ranged** hit (grey
 *   tornado, up to 69, Protect from Missiles) or a **magic** hit (blue tornado, up to 21, Protect
 *   from Magic) whose accuracy is her Magic roll against the player's **Ranged** defence;
 * - if nobody in the chamber is attacking her she flies up to her target and swipes with a
 *   **magical melee** hit (up to 26, Protect from Melee, her melee roll against the player's Magic
 *   defence). Once she has come to melee range she stays there. When the chamber empties she and
 *   her bodyguards heal to full.
 *
 * **Her bodyguards** attack every 5 ticks: Flight Kilisa in melee (slash, up to 15), Wingman Skree
 * with magic (up to 16) and Flockleader Geerin with ranged (up to 25).
 *
 * **No melee.** Kree'arra, her bodyguards, the aviansies and the Armadyl spiritual warrior, ranger
 * and mage fly too high for a melee weapon ("The Aviansie is flying too high for you to attack
 * using melee."); spells, ranged weapons, halberds and salamanders work.
 *
 * The animations, graphics, projectile flights and sounds are the captured ones (rsprox), via
 * [GodWarsProjAnims] and [GodWarsSynths]. Guesses where the Wiki is silent: how often she picks
 * ranged over magic (7 in 10) and that the push happens on every attack that lands (a hit, a block
 * or a miss). When the chamber empties they heal and their stats are restored. Not built: her pet
 * and the hitting-through effects of scythes and Dinh's bulwark.
 */
class GodWarsArmadylScript
@Inject
constructor(
    private val attacks: NpcAttacks,
    private val accuracy: AccuracyFormulae,
    private val targeting: NpcTargeting,
    private val meleeBlocks: NpcMeleeBlocks,
    private val random: GameRandom,
    private val rooms: GodRooms,
    private val collision: CollisionFlagMap,
    private val interactions: AiPlayerInteractions,
    private val npcTypes: NpcTypeList,
) : PluginScript() {
    private val kreeRanged =
        NpcAttack(
            style = NpcAttackStyle.Ranged,
            anim = GodWarsSeqs.kree_wind,
            sound = GodWarsSynths.kree_attack,
            projectile = GodWarsSpotanims.kree_wind_travel,
            projanim = GodWarsProjAnims.kree_wind,
            maxHit = KREE_RANGED_MAX,
        )

    override fun ScriptContext.startup() {
        val kree = GodWarsRoster.byName.getValue(GodWarsNames.KREE)
        val kreeId = npcTypes[kree].id
        targeting.setStyle(kree, TargetingStyle.All, range = ROOM_RANGE)
        targeting.addScope { npc, player ->
            if (npc.type.id == kreeId)
                rooms.sameRoom(GodChambers.armadyl, npc.coords, player.coords)
            else null
        }

        for (name in GodWarsNames.armadyl + GodWarsNames.armadylLeaders) {
            meleeBlocks.block(
                GodWarsRoster.byName.getValue(name),
                FLYING_TOO_HIGH,
                reachable = true,
            )
        }

        onAiApPlayer2(kree) { fightKree(it.target) }
        onAiOpPlayer2(kree) { fightKree(it.target) }

        val kilisa = GodWarsRoster.byName.getValue(GodWarsNames.KILISA)
        onAiOpPlayer2(kilisa) { bodyguard(it.target, KILISA_MELEE) }
        val skree = GodWarsRoster.byName.getValue(GodWarsNames.SKREE)
        onAiApPlayer2(skree) { bodyguard(it.target, SKREE_MAGIC) }
        onAiOpPlayer2(skree) { bodyguard(it.target, SKREE_MAGIC) }
        val geerin = GodWarsRoster.byName.getValue(GodWarsNames.GEERIN)
        onAiApPlayer2(geerin) { bodyguard(it.target, GEERIN_RANGED) }
        onAiOpPlayer2(geerin) { bodyguard(it.target, GEERIN_RANGED) }

        for (name in GodWarsNames.armadylLeaders) {
            onAiTimer(GodWarsRoster.byName.getValue(name)) { healIfEmpty(npc) }
        }
    }

    private fun Player.isTargeting(npc: Npc): Boolean =
        (interaction as? InteractionNpc)?.uid == npc.uid

    private fun StandardNpcAccess.bodyguard(target: Player, attack: NpcAttack) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        attacks.attack(npc, target, attack)
        actionDelay = mapClock + BODYGUARD_RATE
    }

    private fun StandardNpcAccess.fightKree(primary: Player) {
        if (!primary.isValidTarget() || actionDelay > mapClock) {
            return
        }
        val targeted = targeting.inRange(npc, ROOM_RANGE).any { it.isTargeting(npc) }
        if (!targeted) {
            // Nobody is attacking her: fly up to the target and swipe.
            if (npc.distanceTo(primary) > 1) {
                opPlayer2(primary, interactions)
                return
            }
            attacks.attack(npc, primary, meleeOf(npc, primary))
            actionDelay = mapClock + KREE_RATE
            return
        }

        val magic = random.of(100) >= RANGED_ODDS
        val targets = targeting.select(npc, primary)
        for ((index, target) in targets.withIndex()) {
            val attack = if (magic) magicOf(npc, target) else kreeRanged
            attacks.attack(npc, target, attack, playAnim = index == 0, onImpact = pushBack(npc))
        }
        actionDelay = mapClock + KREE_RATE
    }

    /** Her swipe: a melee hit, but the player's Magic defence decides whether it lands. */
    fun meleeOf(npc: Npc, target: Player): NpcAttack {
        val lands = accuracy.rollMagicalMeleeAccuracy(npc, target, random)
        return NpcAttack(
            style = NpcAttackStyle.Melee,
            anim = GodWarsSeqs.kree_claw,
            meleeType = MeleeAttackType.Slash,
            maxHit = if (lands) KREE_MELEE_MAX else 0,
            alwaysHits = true,
        )
    }

    /** Her magic: a magic hit rolled with her Magic level against the player's Ranged defence. */
    fun magicOf(npc: Npc, target: Player): NpcAttack {
        val lands = accuracy.rollRangedMagicAccuracy(npc, target, random)
        return NpcAttack(
            style = NpcAttackStyle.Magic,
            anim = GodWarsSeqs.kree_wind,
            sound = GodWarsSynths.kree_attack,
            projectile = GodWarsSpotanims.kree_magic_travel,
            projanim = GodWarsProjAnims.kree_wind,
            maxHit = if (lands) KREE_MAGIC_MAX else 0,
            alwaysHits = true,
        )
    }

    private fun pushBack(npc: Npc) = NpcImpactListener { target, _ -> knockBack(npc, target) }

    /**
     * Pushes [target] one tile away from [npc], along each axis they are off-centre on, as long as
     * the tile stays inside the chamber. A player in a corner (or against the wall they would be
     * pushed into) stays where they are.
     */
    fun knockBack(npc: Npc, target: Player) {
        val chamber = GodChambers.armadyl
        val room = rooms.roomOf(chamber, target.coords) ?: return
        val start = rooms.toNormal(target.coords)
        val centre = rooms.toNormal(npc.coords)
        val centreX = centre.x + npc.size / 2
        val centreZ = centre.z + npc.size / 2
        var x = start.x + Integer.signum(start.x - centreX)
        var z = start.z + Integer.signum(start.z - centreZ)
        if (x !in chamber.minX..chamber.maxX) x = start.x
        if (z !in chamber.minZ..chamber.maxZ) z = start.z
        val dest = CoordGrid(x, z, start.level)
        if (dest == start) {
            return
        }
        // A telejump drops the player's interaction, which would stop them attacking her and let
        // auto-retaliate pick a new target (often a bodyguard). Keep it if it was aimed at her.
        val interaction = target.interaction
        PathingEntityCommon.telejump(target, collision, rooms.toReal(room, dest))
        if (interaction is InteractionNpc && interaction.uid == npc.uid) {
            target.interaction = interaction
        }
    }

    /** Heals [npc] to full when nobody is in the room any more. */
    fun healIfEmpty(npc: Npc) {
        val room = rooms.checkIn(npc, GodChambers.armadyl) ?: return
        if (rooms.isEmpty(room)) {
            npc.resetForEmptyRoom()
        }
    }

    companion object {
        const val KREE_RATE = 3
        const val BODYGUARD_RATE = 5
        const val KREE_RANGED_MAX = 69
        const val KREE_MAGIC_MAX = 21
        const val KREE_MELEE_MAX = 26

        /** The odds, out of 100, that she uses her ranged attack rather than her magic one. */
        const val RANGED_ODDS = 70

        /** Players within this many tiles of her, and in the chamber, take her area attacks. */
        const val ROOM_RANGE = 24

        const val FLYING_TOO_HIGH = "The Aviansie is flying too high for you to attack using melee."

        val KILISA_MELEE =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                anim = GodWarsSeqs.armadyl_sword,
                meleeType = MeleeAttackType.Slash,
                maxHit = 15,
            )

        val SKREE_MAGIC =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = GodWarsSeqs.armadyl_cannon,
                sound = GodWarsSynths.skree_attack,
                castSpot = GodWarsSpotanims.skree_bolt_cast,
                projectile = GodWarsSpotanims.skree_bolt_travel,
                projanim = GodWarsProjAnims.skree_bolt,
                impactSpot = GodWarsSpotanims.skree_bolt_hit,
                maxHit = 16,
            )

        val GEERIN_RANGED =
            NpcAttack(
                style = NpcAttackStyle.Ranged,
                anim = GodWarsSeqs.armadyl_spear,
                sound = GodWarsSynths.geerin_attack,
                projectile = GodWarsSpotanims.armadyl_spear_travel,
                projanim = GodWarsProjAnims.geerin_spear,
                maxHit = 25,
            )
    }
}
