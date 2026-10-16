package org.rsmod.content.skills.slayer.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.feet
import org.rsmod.api.player.hands
import org.rsmod.api.player.hat
import org.rsmod.api.player.hit.events.PlayerHitEvents
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.statDrain
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.content.skills.slayer.configs.SlayerParams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.stat.StatType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** What a hit from a Slayer monster does to a player who is not wearing what protects from it. */
class Unprotected(
    /** The most extra damage, rolled from 0, which the hit adds. */
    val extraMax: Int = 0,
    /** Extra damage as a percentage of the player's Hitpoints level, always dealt. */
    val extraHpPercent: Int = 0,
    /** Stats the hit drains, by [drainLevels] and [drainPercent] of their base level. */
    val drains: List<StatType> = emptyList(),
    val drainLevels: Int = 0,
    val drainPercent: Int = 0,
)

/** One monster's protection: [wears] says whether the player has it on. */
class Protection(val task: Int, val effect: Unprotected, val wears: (Player) -> Boolean)

/**
 * The effects of Slayer monsters on a player without the gear that protects from them. A hit that
 * lands from one of these monsters adds the extra damage and drains described by its [Unprotected],
 * unless the player wears the protection (a Slayer helmet counts for the nose peg, earmuffs,
 * facemask, spiny helmet and reinforced goggles).
 *
 * Damage and drain sizes are the Wiki's where it gives them; where it only says "greatly reduced"
 * the drain is [HEAVY].
 */
class SlayerProtection
@Inject
constructor(private val random: GameRandom, private val objTypes: ObjTypeList) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<PlayerHitEvents.Impact> {
            val npc = npcSource ?: return@onEvent
            apply(player, npc)
        }
    }

    private fun apply(player: Player, npc: Npc) {
        val task = npc.visType.param(SlayerParams.task_id)
        val rule = rules[task] ?: return
        if (rule.wears(player)) {
            return
        }
        val effect = rule.effect
        var extra = 0
        if (effect.extraMax > 0) {
            extra += random.of(0, effect.extraMax)
        }
        if (effect.extraHpPercent > 0) {
            extra += player.baseHitpointsLvl * effect.extraHpPercent / 100
        }
        if (extra > 0) {
            player.queueHit(1, HitType.Typeless, extra)
        }
        for (stat in effect.drains) {
            player.statDrain(stat, effect.drainLevels, effect.drainPercent)
        }
    }

    private val combatStats =
        listOf(stats.attack, stats.strength, stats.defence, stats.ranged, stats.magic)

    private fun Player.wearsSlayerHelm(): Boolean {
        val hat = hat ?: return false
        val type = objTypes[hat]
        return type.param(params.slayer_helm) != 0 || type.param(params.slayer_helm_imbued) != 0
    }

    private fun Player.headWears(obj: ObjType): Boolean =
        wearsSlayerHelm() || hat?.isType(obj) == true

    private fun InvObj?.worn(vararg types: ObjType): Boolean =
        this != null && types.any { isType(it) }

    private val rules: Map<Int, Protection> =
        listOf(
                // Nose peg: stats drain extremely quickly and the hit grows from 8 to 14.
                Protection(
                    ABERRANT_SPECTRE,
                    Unprotected(extraMax = 6, drains = combatStats, drainLevels = HEAVY),
                ) {
                    it.headWears(SlayerObjs.nose_peg)
                },
                // Earmuffs: a scream for 6 (not 2) that drains the combat stats, Prayer and
                // Agility.
                Protection(
                    BANSHEE,
                    Unprotected(
                        extraMax = 4,
                        drains = combatStats + listOf(stats.prayer, stats.agility),
                        drainLevels = HEAVY,
                    ),
                ) {
                    it.headWears(SlayerObjs.earmuffs)
                },
                // Facemask: always 16, and the combat stats are drained completely.
                Protection(
                    DUST_DEVIL,
                    Unprotected(extraMax = 8, drains = combatStats, drainPercent = 100),
                ) {
                    it.headWears(SlayerObjs.facemask)
                },
                // Mirror shield or V's shield: the combat stats drop with every hit.
                Protection(BASILISK, Unprotected(drains = combatStats, drainLevels = HEAVY)) {
                    it.lefthand.worn(SlayerObjs.mirror_shield, SlayerObjs.v_shield)
                },
                Protection(COCKATRICE, Unprotected(drains = combatStats, drainLevels = HEAVY)) {
                    it.lefthand.worn(SlayerObjs.mirror_shield, SlayerObjs.v_shield)
                },
                // Insulated boots: the hit goes from 8 or 6 to 15, and no prayer stops the extra.
                Protection(KILLERWATT, Unprotected(extraMax = 7)) {
                    it.feet.worn(SlayerObjs.insulated_boots)
                },
                // Spiny helmet: a wall beast grabs the head and hits for 18 instead of 4.
                Protection(WALL_BEAST, Unprotected(extraMax = 14)) {
                    it.headWears(SlayerObjs.spiny_helmet)
                },
                // Slayer gloves: 12.5% of the Hitpoints level (and a disease, which is left
                // out).
                Protection(FEVER_SPIDER, Unprotected(extraHpPercent = 12)) {
                    it.hands.worn(SlayerObjs.slayer_gloves)
                },
                // Reinforced goggles: 20 to 30 damage and Attack and Defence drained by 90%.
                Protection(
                    SOURHOG,
                    Unprotected(
                        extraMax = 30,
                        drains = listOf(stats.attack, stats.defence),
                        drainPercent = 90,
                    ),
                ) {
                    it.headWears(SlayerObjs.reinforced_goggles)
                },
            )
            .associateBy { it.task }

    companion object {
        const val ABERRANT_SPECTRE = 41
        const val BANSHEE = 38
        const val DUST_DEVIL = 49
        const val BASILISK = 43
        const val COCKATRICE = 44
        const val KILLERWATT = 73
        const val WALL_BEAST = 61
        const val FEVER_SPIDER = 69
        const val SOURHOG = 121

        /** "Greatly reduced": the wiki gives no figure, so each hit takes this many levels. */
        const val HEAVY = 5
    }
}
