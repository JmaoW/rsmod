package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.rsmod.api.player.righthand
import org.rsmod.content.skills.hunter.configs.Creature
import org.rsmod.content.skills.hunter.configs.TrapKind
import org.rsmod.content.skills.hunter.configs.hunter_objs
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.varp.VarpType

/**
 * Completes the league's Hunter tasks from the events the Hunter skill publishes (traps, impling
 * and butterfly catches, birdhouses and what a hunter holds). The level and experience tasks are
 * with the other skills' in [LeagueSkillTasks].
 *
 * Tasks that named a place (Puro-Puro, Fossil Island, the Wilderness) complete anywhere now, and
 * tasks for content the isle does not have (Rumours and their loot sacks, herbiboar, the Guild
 * Hunter outfit, aerial fishing, the dragon hunter weapons, big cat clothing) are not in the list.
 */
class LeagueHunterTasks @Inject constructor(private val tracker: LeagueTaskTracker) {
    /** A hunter collected [catches] of [creature] from a trap or a pit. */
    fun onTrapped(player: Player, creature: Creature, catches: Int) {
        when (creature.key) {
            "wild_kebbit" -> tracker.complete(player, TASK_WILD_KEBBIT)
            "sabre_toothed_kebbit" -> tracker.complete(player, TASK_SABRE_KEBBIT)
            "chinchompa" -> {
                tracker.complete(player, TASK_GREY_CHINCHOMPA)
                count(player, catches, TASK_GREY_50, league_progress.hunter_grey_50, 50)
            }
            "carnivorous_chinchompa" -> {
                count(player, catches, TASK_RED_150, league_progress.hunter_red_150, 150)
                count(player, catches, TASK_RED_300, league_progress.hunter_red_300, 300)
            }
            "black_chinchompa" -> tracker.complete(player, TASK_BLACK_CHINCHOMPA)
            "swamp_lizard" -> {
                tracker.complete(player, TASK_SWAMP_LIZARD)
                tracker.complete(player, TASK_LIZARD_OR_SALAMANDER)
            }
            "orange_salamander" -> {
                tracker.complete(player, TASK_LIZARD_OR_SALAMANDER)
                count(player, catches, TASK_ORANGE_30, league_progress.hunter_orange_30, 30)
            }
            "red_salamander" -> {
                tracker.complete(player, TASK_LIZARD_OR_SALAMANDER)
                tracker.complete(player, TASK_RED_SALAMANDER)
            }
            "black_salamander" -> {
                tracker.complete(player, TASK_LIZARD_OR_SALAMANDER)
                tracker.complete(player, TASK_BLACK_SALAMANDER)
            }
            "tecu_salamander" -> {
                tracker.complete(player, TASK_LIZARD_OR_SALAMANDER)
                count(player, catches, TASK_TECU_100, league_progress.hunter_tecu_100, 100)
            }
            "spined_larupia" -> tracker.complete(player, TASK_LARUPIA)
            "sabre_toothed_kyatt" -> tracker.complete(player, TASK_KYATT)
            "sunlight_antelope" ->
                count(player, catches, TASK_SUNLIGHT_25, league_progress.hunter_sunlight_25, 25)
            "moonlight_antelope" ->
                count(player, catches, TASK_MOONLIGHT_50, league_progress.hunter_moonlight_50, 50)
        }
        if (creature.kind == TrapKind.Bird) {
            tracker.complete(player, TASK_SNARE_BIRD)
        }
        if (creature.key.endsWith("chinchompa")) {
            count(player, catches, TASK_CHINCHOMPAS_500, league_progress.hunter_chinchompa_500, 500)
        }
    }

    /**
     * A butterfly, moth or impling called [name] was caught, [jarred] when in a jar (a barehanded
     * catch with no jar is looted at once).
     */
    fun onCaught(player: Player, name: String, jarred: Boolean) {
        if (!name.endsWith("impling", ignoreCase = true)) {
            return
        }
        tracker.addProgress(player, TASK_IMPLINGS_50, league_progress.hunter_implings_50, 50)
        when (name.lowercase()) {
            "baby impling" -> tracker.complete(player, TASK_BABY_IMPLING)
            "dragon impling" -> tracker.complete(player, TASK_DRAGON_IMPLING)
            "crystal impling" -> tracker.complete(player, TASK_CRYSTAL_IMPLING)
            "lucky impling" -> if (!jarred) tracker.complete(player, TASK_LUCKY_BAREHANDED)
        }
    }

    /** An impling called [name] gave up [obj]. */
    fun onLooted(player: Player, name: String, obj: ObjType) {
        val amulet = hunter_objs.strung_dragonstone_amulet
        if (name.equals("Crystal impling", true) && obj.id == amulet.id) {
            tracker.complete(player, TASK_DRAGONSTONE_AMULET)
        }
    }

    fun onBirdhouseCollected(player: Player) {
        tracker.addProgress(player, TASK_BIRDHOUSES_20, league_progress.hunter_birdhouse_20, 20)
    }

    fun onBirdhouseBuilt(player: Player, house: String) {
        if (house == "magic") {
            tracker.complete(player, TASK_MAGIC_BIRDHOUSE)
        }
    }

    /** Wielded stacks of black chinchompas and the adult tecu salamander. */
    fun checkGear(player: Player) {
        val held = player.righthand ?: return
        if (held.isType(hunter_objs.chinchompa_black)) {
            for ((taskId, goal) in BLACK_CHINCHOMPA_STACKS) {
                if (held.count >= goal) {
                    tracker.complete(player, taskId)
                }
            }
        }
        if (held.isType(hunter_objs.tecu_salamander)) {
            tracker.complete(player, TASK_TECU_EQUIPPED)
        }
    }

    private fun count(player: Player, catches: Int, taskId: Int, counter: VarpType, goal: Int) {
        repeat(catches) { tracker.addProgress(player, taskId, counter, goal) }
    }

    private companion object {
        const val TASK_BABY_IMPLING = 379
        const val TASK_SNARE_BIRD = 380
        const val TASK_WILD_KEBBIT = 1369
        const val TASK_LIZARD_OR_SALAMANDER = 382
        const val TASK_BIRDHOUSES_20 = 439
        const val TASK_IMPLINGS_50 = 445
        const val TASK_CRYSTAL_IMPLING = 519
        const val TASK_LARUPIA = 691
        const val TASK_RED_SALAMANDER = 694
        const val TASK_ORANGE_30 = 859
        const val TASK_SWAMP_LIZARD = 912
        const val TASK_BLACK_SALAMANDER = 976
        const val TASK_BLACK_CHINCHOMPA = 977
        const val TASK_GREY_CHINCHOMPA = 1614
        const val TASK_SABRE_KEBBIT = 1833
        const val TASK_DRAGON_IMPLING = 384
        const val TASK_MAGIC_BIRDHOUSE = 440
        const val TASK_KYATT = 578
        const val TASK_GREY_50 = 1615
        const val TASK_SUNLIGHT_25 = 1697
        const val TASK_DRAGONSTONE_AMULET = 1513
        const val TASK_LUCKY_BAREHANDED = 385
        const val TASK_RED_150 = 692
        const val TASK_RED_300 = 1377
        const val TASK_CHINCHOMPAS_500 = 1616
        const val TASK_MOONLIGHT_50 = 1696
        const val TASK_TECU_100 = 1720
        const val TASK_TECU_EQUIPPED = 1722

        /** Task id to the size of the black chinchompa stack it needs wielded. */
        val BLACK_CHINCHOMPA_STACKS = mapOf(978 to 100, 979 to 250, 980 to 500)
    }
}
