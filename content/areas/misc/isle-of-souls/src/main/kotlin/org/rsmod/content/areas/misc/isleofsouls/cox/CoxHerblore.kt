package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.content.skills.cooking.scripts.Cooking
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The raid's Herblore and the points for food and potions.
 *
 * Wiki: golpar, buchu leaf and noxifer clean at 47, 52 and 60 (1, 2, 3 XP). A potion is a clean
 * herb, a secondary and a water-filled gourd vial, and comes out at the best strength the level
 * allows (levels and XP in [CoxPotion]); an overload is noxifer and an elder, twisted and kodai
 * potion (4) of one strength. The game has no unfinished stage: the secondary or herb is used on
 * the gourd. Cooks get 4 + 8 x tier points a piece (soft cap 20 x raiders pieces, hard cap 40 x)
 * and potion makers 100 points a dose (Xeric's aid 200, overloads 400; x0.25 weak, x0.5 normal), up
 * to 5 doses a raider and 5 more at half (point sizes are TSPS's guesses). Items carry no maker
 * here, so the points are given when the food is cooked or the potion made, not when used.
 */
class CoxHerblore
@Inject
constructor(private val raids: CoxRaids, private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        for (herb in CLEANING) {
            onOpHeld1(herb.grimy) { clean(herb) }
        }
        val registered = HashSet<Pair<Int, Int>>()
        for (potion in CoxPotion.entries.filter { it.herb != null }) {
            for (used in listOf(potion.herb!!, potion.secondary!!)) {
                if (registered.add(used.id to cox_objs.gourd_water.id)) {
                    onOpHeldU(used, cox_objs.gourd_water) { mix(used) }
                }
            }
        }
        for (strength in 0..2) {
            for (part in listOf(CoxPotion.Elder, CoxPotion.Twisted, CoxPotion.Kodai)) {
                onOpHeldU(cox_objs.noxifer, part.doses(strength).first()) { overload() }
            }
        }
        onEvent<Cooking.Cooked> { cooked(player, cooked) }
    }

    private fun ProtectedAccess.advance(xp: Double) {
        statAdvance(stats.herblore, xp * xpMods.get(player, stats.herblore))
    }

    private class Cleaning(val grimy: ObjType, val clean: ObjType, val level: Int, val xp: Double)

    private fun ProtectedAccess.clean(herb: Cleaning) {
        if (player.stat(stats.herblore) < herb.level) {
            mes("You need a Herblore level of ${herb.level} to clean this herb.")
            return
        }
        invDel(inv, herb.grimy, 1)
        invAdd(inv, herb.clean, 1)
        advance(herb.xp)
    }

    /** [used] (a herb or secondary) on a water-filled gourd: the potion it and its partner make. */
    private fun ProtectedAccess.mix(used: ObjType) {
        val level = player.stat(stats.herblore)
        val potion =
            CoxPotion.entries.firstOrNull { p ->
                p.herb != null &&
                    (p.herb.id == used.id || p.secondary!!.id == used.id) &&
                    p.herb in inv &&
                    p.secondary!! in inv
            }
        if (potion == null) {
            mes("You need a herb and a secondary ingredient to make a potion.")
            return
        }
        val strength = potion.levels.indexOfLast { level >= it }
        if (strength < 0) {
            mes("You need a Herblore level of ${potion.levels[0]} to make this potion.")
            return
        }
        anim(cox_seqs.herbing)
        invDel(inv, cox_objs.gourd_water, 1)
        invDel(inv, potion.herb!!, 1)
        invDel(inv, potion.secondary!!, 1)
        invAdd(inv, potion.doses(strength).first(), 1)
        advance(potion.xp[strength])
        made(player, potion, strength)
    }

    private fun ProtectedAccess.overload() {
        val level = player.stat(stats.herblore)
        val parts = listOf(CoxPotion.Elder, CoxPotion.Twisted, CoxPotion.Kodai)
        // The strongest strength of which all three (4) are carried, and the level allows.
        val strength =
            (2 downTo 0).firstOrNull { s ->
                level >= CoxPotion.Overload.levels[s] && parts.all { it.doses(s).first() in inv }
            }
        if (strength == null) {
            val least = CoxPotion.Overload.levels[0]
            if (level < least) {
                mes("You need a Herblore level of $least to make an overload.")
            } else {
                mes("You need a full elder, twisted and kodai potion of the same strength.")
            }
            return
        }
        anim(cox_seqs.herbing)
        invDel(inv, cox_objs.noxifer, 1)
        for (part in parts) invDel(inv, part.doses(strength).first(), 1)
        invAdd(inv, CoxPotion.Overload.doses(strength).first(), 1)
        advance(CoxPotion.Overload.xp[strength])
        made(player, CoxPotion.Overload, strength)
    }

    private fun made(player: Player, potion: CoxPotion, strength: Int) {
        val raid = raids.raidOf(player) ?: return
        val member = raid.member(player) ?: return
        val perDose =
            when (potion) {
                CoxPotion.Overload -> 400
                CoxPotion.XericsAid -> 200
                else -> 100
            } * STRENGTH_FACTOR[strength] / 100
        val full = 5 * raid.scaling.size
        var points = 0
        repeat(DOSES) {
            val dose = member.potionPoints
            member.potionPoints++
            points +=
                when {
                    dose < full -> perDose
                    dose < full + 5 -> perDose / 2
                    else -> 0
                }
        }
        raids.addPoints(raid, player, points)
    }

    private fun cooked(player: Player, cooked: ObjType) {
        val raid = raids.raidOf(player) ?: return
        val tier =
            cox_objs.cooked_fish.indexOfFirst { it.id == cooked.id }.takeIf { it >= 0 }
                ?: cox_objs.cooked_bat.indexOfFirst { it.id == cooked.id }.takeIf { it >= 0 }
                ?: return
        val member = raid.member(player) ?: return
        val raiders = raid.scaling.size
        val piece = member.foodPoints
        member.foodPoints++
        val base = 4 + 8 * tier
        val points =
            when {
                piece < 20 * raiders -> base
                piece < 40 * raiders -> base / 2
                else -> 0
            }
        raids.addPoints(raid, player, points)
    }

    private companion object {
        const val DOSES = 4
        val STRENGTH_FACTOR = listOf(25, 50, 100)
        val CLEANING =
            listOf(
                Cleaning(cox_objs.grimy_golpar, cox_objs.golpar, 47, 1.0),
                Cleaning(cox_objs.grimy_buchu, cox_objs.buchu, 52, 2.0),
                Cleaning(cox_objs.grimy_noxifer, cox_objs.noxifer, 60, 3.0),
            )
    }
}

/** The raid's potions (Wiki levels and XP for the weak, normal and strong strengths). */
enum class CoxPotion(
    val herb: ObjType?,
    val secondary: ObjType?,
    val levels: List<Int>,
    val xp: List<Double>,
    private val prefix: String,
) {
    Elder(
        cox_objs.golpar,
        cox_objs.stinkhorn,
        listOf(47, 59, 70),
        listOf(6.5, 10.0, 13.0),
        "elder",
    ),
    Twisted(
        cox_objs.golpar,
        cox_objs.cicely,
        listOf(47, 59, 70),
        listOf(6.5, 10.0, 13.0),
        "twisted",
    ),
    Kodai(
        cox_objs.golpar,
        cox_objs.endarkened_juice,
        listOf(47, 59, 70),
        listOf(6.5, 10.0, 13.0),
        "kodai",
    ),
    Revitalisation(
        cox_objs.buchu,
        cox_objs.stinkhorn,
        listOf(52, 65, 78),
        listOf(13.5, 20.0, 26.5),
        "revitalisation",
    ),
    PrayerEnhance(
        cox_objs.buchu,
        cox_objs.cicely,
        listOf(52, 65, 78),
        listOf(13.5, 20.0, 26.5),
        "prayer",
    ),
    XericsAid(
        cox_objs.buchu,
        cox_objs.endarkened_juice,
        listOf(52, 65, 78),
        listOf(13.5, 20.0, 26.5),
        "xericaid",
    ),
    Antipoison(
        cox_objs.noxifer,
        cox_objs.cicely,
        listOf(60, 75, 90),
        listOf(13.5, 20.0, 26.5),
        "antipoison",
    ),
    Overload(null, null, listOf(60, 75, 90), listOf(33.5, 50.0, 66.5), "overload");

    /** The (4)..(1) doses of [strength] (0 weak, 1 normal, 2 strong). */
    fun doses(strength: Int): List<ObjType> = CoxPotionObjs.doses(prefix, strength)
}

internal object CoxPotionObjs : ObjReferences() {
    private val all = HashMap<String, List<ObjType>>()

    init {
        for (prefix in
            listOf(
                "elder",
                "twisted",
                "kodai",
                "revitalisation",
                "prayer",
                "xericaid",
                "antipoison",
                "overload",
            )) {
            for ((strength, suffix) in listOf("_weak", "", "_strong").withIndex()) {
                all["$prefix$strength"] =
                    (4 downTo 1).map { find("raids_vial_$prefix${suffix}_$it") }
            }
        }
    }

    fun doses(prefix: String, strength: Int): List<ObjType> = checkNotNull(all["$prefix$strength"])
}
