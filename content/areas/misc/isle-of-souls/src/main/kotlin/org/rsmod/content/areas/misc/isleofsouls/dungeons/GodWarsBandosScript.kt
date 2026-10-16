package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcTargeting
import org.rsmod.api.combat.TargetingStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object BandosSeqs : SeqReferences() {
    val graardor_slam = find("godwars_bandos_ranged")
    val steelwill_sonic = find("godwars_sergeant_goblin2_sonic")
    val grimspike_throw = find("godwars_sergeant_goblin3_ranged")
}

object BandosSpotanims : SpotanimReferences() {
    val graardor_slam = find("godwars_bandos_spot")
    val graardor_travel = find("godwars_bandos_proj")
    val steelwill_cast = find("godwars_goblin2_sonic_attk_spot")
    val steelwill_travel = find("godwars_goblin2_sonic_attk_proj")
    val grimspike_cast = find("godwars_sergeant_goblin3_ranged")
    val grimspike_travel = find("godwars_goblin3_handaxe_proj")
}

/**
 * General Graardor and his three sergeants (OSRS Wiki: General Graardor, its Strategies page, and
 * the sergeants' pages).
 *
 * **Graardor** attacks every 6 ticks with one of two attacks:
 * - 2 in 3: a **melee** punch, up to 60 (crush; Protect from Melee);
 * - 1 in 3: a **ground slam** that hits every player in the chamber at once, each with their own
 *   roll, for 15 to 35 on a hit, equal odds for each number (ranged; Protect from Missiles). He
 *   only slams while he is next to the player he is fighting.
 *
 * **The sergeants** attack every 5 ticks: Sergeant Strongstack in melee (crush, up to 15), Sergeant
 * Steelwill with magic (up to 15) and Sergeant Grimspike with ranged axes (up to 21). On the tick
 * they are due to attack they go for the player who last hurt Graardor, and once he is dead for
 * whoever dealt the killing blow; otherwise they keep their own target.
 *
 * When the chamber empties Graardor and the sergeants heal to full and their stats are restored.
 *
 * The animations, graphics, projectile flights and sounds are the captured ones (rsprox), via
 * [GodWarsProjAnims] and [GodWarsSynths]: the slam is a projectile to each player that lands when
 * it does.
 */
class GodWarsBandosScript
@Inject
constructor(
    private val attacks: NpcAttacks,
    private val targeting: NpcTargeting,
    private val random: GameRandom,
    private val players: PlayerList,
    private val rooms: GodRooms,
    private val interactions: AiPlayerInteractions,
    private val npcTypes: NpcTypeList,
) : PluginScript() {
    /**
     * The player who last hurt Graardor in each room, the sergeants' favourite there. Cleared with
     * the room. The key is the private room's instance, or [PUBLIC] for the public chamber.
     */
    private val hitters = IdentityHashMap<Any, Player>()

    /** The favourite in the public chamber. */
    val lastHitter: Player?
        get() = hitters[PUBLIC]

    private fun keyOf(room: GodRoom): Any = room.instance ?: PUBLIC

    override fun ScriptContext.startup() {
        val graardor = GodWarsRoster.byName.getValue(GodWarsNames.GRAARDOR)
        val graardorId = npcTypes[graardor].id
        targeting.setStyle(graardor, TargetingStyle.All, range = ROOM_RANGE)
        targeting.addScope { npc, player ->
            if (npc.type.id == graardorId)
                rooms.sameRoom(GodChambers.bandos, npc.coords, player.coords)
            else null
        }

        onAiOpPlayer2(graardor) { fightGraardor(it.target) }
        onNpcHit(graardor) { noteHit() }

        val strongstack = GodWarsRoster.byName.getValue(GodWarsNames.STRONGSTACK)
        onAiOpPlayer2(strongstack) { sergeant(it.target, STRONGSTACK_MELEE, ranged = false) }
        val steelwill = GodWarsRoster.byName.getValue(GodWarsNames.STEELWILL)
        onAiApPlayer2(steelwill) { sergeant(it.target, STEELWILL_MAGIC, ranged = true) }
        onAiOpPlayer2(steelwill) { sergeant(it.target, STEELWILL_MAGIC, ranged = true) }
        val grimspike = GodWarsRoster.byName.getValue(GodWarsNames.GRIMSPIKE)
        onAiApPlayer2(grimspike) { sergeant(it.target, GRIMSPIKE_RANGED, ranged = true) }
        onAiOpPlayer2(grimspike) { sergeant(it.target, GRIMSPIKE_RANGED, ranged = true) }

        for (name in GodWarsNames.bandosLeaders) {
            onAiTimer(GodWarsRoster.byName.getValue(name)) { healIfEmpty(npc) }
        }
    }

    private fun NpcHitEvents.Impact.noteHit() {
        if (hit.damage <= 0 || !hit.isFromPlayer) {
            return
        }
        hit.resolvePlayerSource(players)?.let { recordHit(it) }
    }

    /** Forgets who hurt Graardor, in every room. */
    fun forgetHitter() {
        hitters.clear()
    }

    /** Records that [player] hurt Graardor (the Wiki's target for the sergeants). */
    fun recordHit(player: Player) {
        val room = rooms.roomOf(GodChambers.bandos, player.coords) ?: return
        hitters[keyOf(room)] = player
    }

    private fun StandardNpcAccess.fightGraardor(target: Player) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        if (random.of(SLAM_ODDS) == 0) {
            val victims = targeting.select(npc, target)
            for ((index, victim) in victims.withIndex()) {
                attacks.attack(npc, victim, GRAARDOR_SLAM, playAnim = index == 0)
            }
        } else {
            attacks.attack(npc, target, GRAARDOR_MELEE)
        }
        actionDelay = mapClock + GRAARDOR_RATE
    }

    /**
     * A sergeant's turn. If the player to go for is not the one it is on, it switches to them and
     * attacks next tick; the others keep their target.
     */
    private fun StandardNpcAccess.sergeant(target: Player, attack: NpcAttack, ranged: Boolean) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        val focus = focusOf(target)
        if (focus !== target) {
            if (ranged) npc.apPlayer2(focus, interactions) else opPlayer2(focus, interactions)
            return
        }
        attacks.attack(npc, target, attack)
        actionDelay = mapClock + SERGEANT_RATE
    }

    /** Who a sergeant that is currently on [current] should attack. */
    fun focusOf(current: Player): Player {
        val room = rooms.roomOf(GodChambers.bandos, current.coords) ?: return current
        val favourite = hitters[keyOf(room)] ?: return current
        return if (
            favourite.isValidTarget() && rooms.roomOf(GodChambers.bandos, favourite.coords) == room
        ) {
            favourite
        } else {
            current
        }
    }

    /** Heals [npc] to full when nobody is in its room any more, and forgets who hit Graardor. */
    fun healIfEmpty(npc: Npc) {
        val room = rooms.checkIn(npc, GodChambers.bandos) ?: return
        if (!rooms.isEmpty(room)) {
            return
        }
        hitters.remove(keyOf(room))
        npc.resetForEmptyRoom()
    }

    /** Who a sergeant is on right now, if anyone (for tests and the like). */
    fun targetOf(sergeant: Npc): Player? = (sergeant.interaction as? InteractionPlayer)?.target

    companion object {
        private val PUBLIC = Any()

        const val GRAARDOR_RATE = 6
        const val SERGEANT_RATE = 5
        const val GRAARDOR_MELEE_MAX = 60
        const val SLAM_MIN = 15
        const val SLAM_MAX = 35

        /** One attack in this many is the slam. */
        const val SLAM_ODDS = 3

        /** Players within this many tiles of him, and in the chamber, take the slam. */
        const val ROOM_RANGE = 24

        val GRAARDOR_MELEE =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                meleeType = MeleeAttackType.Crush,
                sound = GodWarsSynths.graardor_smash,
                maxHit = GRAARDOR_MELEE_MAX,
            )

        val GRAARDOR_SLAM =
            NpcAttack(
                style = NpcAttackStyle.Ranged,
                anim = BandosSeqs.graardor_slam,
                castSpot = BandosSpotanims.graardor_slam,
                castHeight = 30,
                projectile = BandosSpotanims.graardor_travel,
                projanim = GodWarsProjAnims.graardor_slam,
                minHit = SLAM_MIN,
                maxHit = SLAM_MAX,
            )

        val STRONGSTACK_MELEE =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                meleeType = MeleeAttackType.Crush,
                sound = GodWarsSynths.strongstack_attack,
                maxHit = 15,
            )

        val STEELWILL_MAGIC =
            NpcAttack(
                style = NpcAttackStyle.Magic,
                anim = BandosSeqs.steelwill_sonic,
                sound = GodWarsSynths.steelwill_attack,
                castSpot = BandosSpotanims.steelwill_cast,
                castHeight = 30,
                projectile = BandosSpotanims.steelwill_travel,
                projanim = GodWarsProjAnims.steelwill_sonic,
                maxHit = 15,
            )

        val GRIMSPIKE_RANGED =
            NpcAttack(
                style = NpcAttackStyle.Ranged,
                anim = BandosSeqs.grimspike_throw,
                sound = GodWarsSynths.grimspike_attack,
                castSpot = BandosSpotanims.grimspike_cast,
                projectile = BandosSpotanims.grimspike_travel,
                projanim = GodWarsProjAnims.goblin_axe,
                maxHit = 21,
            )
    }
}
