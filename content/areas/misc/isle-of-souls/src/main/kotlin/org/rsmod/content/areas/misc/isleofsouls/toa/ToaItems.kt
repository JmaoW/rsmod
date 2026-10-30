package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object ToaItemObjs : ObjReferences() {
    val plate = find("armadylean_component")
    val masori_mask = find("masori_mask")
    val masori_body = find("masori_body")
    val masori_chaps = find("masori_chaps")
    val masori_mask_f = find("masori_mask_fortified")
    val masori_body_f = find("masori_body_fortified")
    val masori_chaps_f = find("masori_chaps_fortified")
    val armadyl_helmet = find("armadyl_helmet")
    val armadyl_chestplate = find("armadyl_chestplate")
    val armadyl_chainskirt = find("armadyl_skirt")
    val ward = find("elidinis_ward")
    val ward_f = find("elidinis_ward_fortified")
    val ward_or = find("elidinis_ward_ornament")
    val ward_kit = find("elidinis_ward_ornament_kit")
    val arcane_sigil = find("arcane_sigil")
    val soul_rune = find("soulrune")
    val fang = find("osmumtens_fang")
    val fang_or = find("osmumtens_fang_ornament")
    val fang_kit = find("osmumtens_fang_ornament_kit")
    val keris = find("keris_partisan")
    val keris_corruption = find("keris_partisan_corruption")
    val keris_breach = find("keris_partisan_breach")
    val keris_sun = find("keris_partisan_sun")
    val eye_of_the_corruptor = find("eye_of_the_corruptor")
    val breach_of_the_scarab = find("breach_of_the_scarab")
    val jewel_of_the_sun = find("jewel_of_the_sun")
    val thread = find("thread_of_elidinis")
    val rune_pouch = find("bh_rune_pouch")
    val divine_rune_pouch = find("divine_rune_pouch")
    val needle = find("needle")
}

object ToaItemSeqs : SeqReferences() {
    val fortify = find("human_poh_build")
}

/**
 * What the raid's rewards are made into (wiki): Masori fortified with Armadylean plates (90
 * Crafting and a hammer; 1, 4 and 3 plates for 830, 3,320 and 2,490 xp) and the plates chiselled
 * out of Armadyl armour (210, 840, 630 xp); the arcane sigil on Elidinis' ward (90 Prayer, 90
 * Smithing, 10,000 soul runes, 260 xp in each) and the Menaphite kit on the fortified ward; the
 * cursed phalanx on Osmumten's fang; the three jewels on the keris partisan; the Thread of Elidinis
 * on a rune pouch (75 Crafting and a needle). Each upgrade that can be undone has its Dismantle.
 */
class ToaItemsScript @Inject constructor() : PluginScript() {
    private class Fortify(
        val piece: ObjType,
        val fortified: ObjType,
        val plates: Int,
        val xp: Double,
    )

    private class Chisel(val piece: ObjType, val plates: Int, val xp: Double)

    private class Kit(val base: ObjType, val kit: ObjType, val result: ObjType)

    override fun ScriptContext.startup() {
        val fortify =
            listOf(
                Fortify(ToaItemObjs.masori_mask, ToaItemObjs.masori_mask_f, 1, 830.0),
                Fortify(ToaItemObjs.masori_body, ToaItemObjs.masori_body_f, 4, 3320.0),
                Fortify(ToaItemObjs.masori_chaps, ToaItemObjs.masori_chaps_f, 3, 2490.0),
            )
        for (entry in fortify) {
            onOpHeldU(ToaItemObjs.plate, entry.piece) { fortifyMasori(entry) }
        }
        val chisel =
            listOf(
                Chisel(ToaItemObjs.armadyl_helmet, 1, 210.0),
                Chisel(ToaItemObjs.armadyl_chestplate, 4, 840.0),
                Chisel(ToaItemObjs.armadyl_chainskirt, 3, 630.0),
            )
        for (entry in chisel) {
            onOpHeldU(objs.chisel, entry.piece) { breakArmadyl(entry) }
        }

        onOpHeldU(ToaItemObjs.arcane_sigil, ToaItemObjs.ward) { fortifyWard() }
        // Dismantling the fortified ward gives back the ward and the sigil, not the runes (wiki).
        onOpHeld4(ToaItemObjs.ward_f) {
            dismantle(ToaItemObjs.ward_f, ToaItemObjs.ward, ToaItemObjs.arcane_sigil)
        }

        val kits =
            listOf(
                Kit(ToaItemObjs.ward_f, ToaItemObjs.ward_kit, ToaItemObjs.ward_or),
                Kit(ToaItemObjs.fang, ToaItemObjs.fang_kit, ToaItemObjs.fang_or),
                Kit(
                    ToaItemObjs.keris,
                    ToaItemObjs.eye_of_the_corruptor,
                    ToaItemObjs.keris_corruption,
                ),
                Kit(ToaItemObjs.keris, ToaItemObjs.breach_of_the_scarab, ToaItemObjs.keris_breach),
                Kit(ToaItemObjs.keris, ToaItemObjs.jewel_of_the_sun, ToaItemObjs.keris_sun),
            )
        for (kit in kits) {
            onOpHeldU(kit.kit, kit.base) { attach(kit) }
            onOpHeld3(kit.result) { dismantle(kit.result, kit.base, kit.kit) }
        }

        onOpHeldU(ToaItemObjs.thread, ToaItemObjs.rune_pouch) { threadPouch() }
    }

    private suspend fun ProtectedAccess.confirm(question: String): Boolean {
        var yes = false
        startDialogue { yes = choice2("Yes.", 1, "No.", 2, title = question) == 1 }
        return yes
    }

    private suspend fun ProtectedAccess.fortifyMasori(entry: Fortify) {
        val name = objName(entry.piece)
        when {
            player.stat(stats.crafting) < 90 -> mes("You need a Crafting level of 90 to do that.")
            objs.hammer !in inv -> mes("You need a hammer to fortify your armour.")
            invTotal(inv, ToaItemObjs.plate) < entry.plates ->
                mes("You need ${plates(entry.plates)} to fortify your $name.")
            !confirm("Fortify your $name with ${plates(entry.plates)}?") -> {}
            entry.piece !in inv || invTotal(inv, ToaItemObjs.plate) < entry.plates -> {}
            else -> {
                anim(ToaItemSeqs.fortify)
                invDel(inv, entry.piece, 1)
                invDel(inv, ToaItemObjs.plate, entry.plates)
                invAdd(inv, entry.fortified, 1)
                statAdvance(stats.crafting, entry.xp)
                mes("You use ${plates(entry.plates)} to fortify your $name.")
            }
        }
    }

    private suspend fun ProtectedAccess.breakArmadyl(entry: Chisel) {
        val name = objName(entry.piece)
        when {
            player.stat(stats.crafting) < 90 -> mes("You need a Crafting level of 90 to do that.")
            !confirm("Break apart your $name into ${plates(entry.plates)}?") -> {}
            entry.piece !in inv -> {}
            ToaItemObjs.plate !in inv && !inv.hasFreeSpace() ->
                mes("You don't have enough inventory space.")
            else -> {
                invDel(inv, entry.piece, 1)
                invAdd(inv, ToaItemObjs.plate, entry.plates)
                statAdvance(stats.crafting, entry.xp)
                mes("You use your chisel to break the armour down into its base components.")
            }
        }
    }

    private suspend fun ProtectedAccess.fortifyWard() {
        when {
            player.stat(stats.prayer) < 90 && player.statBase(stats.prayer) < 90 ->
                mes("You need a Prayer level of at least 90 to fortify Elidinis' ward.")
            player.stat(stats.smithing) < 90 ->
                mes("You need a Smithing level of at least 90 to fortify Elidinis' ward.")
            invTotal(inv, ToaItemObjs.soul_rune) < WARD_SOUL_RUNES ->
                mes("You need at least 10,000 soul runes to fortify Elidinis' ward.")
            !confirm("Consume 10,000 soul runes to fortify Elidinis' ward?") -> {}
            ToaItemObjs.ward !in inv || ToaItemObjs.arcane_sigil !in inv -> {}
            else -> {
                invDel(inv, ToaItemObjs.arcane_sigil, 1)
                invDel(inv, ToaItemObjs.ward, 1)
                invDel(inv, ToaItemObjs.soul_rune, WARD_SOUL_RUNES)
                invAdd(inv, ToaItemObjs.ward_f, 1)
                statAdvance(stats.prayer, WARD_XP)
                statAdvance(stats.smithing, WARD_XP)
                mes(
                    "You fuse the arcane sigil to Elidinis' ward, returning it to its former glory."
                )
            }
        }
    }

    private fun ProtectedAccess.attach(kit: Kit) {
        if (kit.kit !in inv || kit.base !in inv) {
            return
        }
        invDel(inv, kit.kit, 1)
        invDel(inv, kit.base, 1)
        invAdd(inv, kit.result, 1)
        mes("You attach the ${objName(kit.kit).lowercase()} to your ${objName(kit.base)}.")
    }

    private fun ProtectedAccess.dismantle(item: ObjType, first: ObjType, second: ObjType) {
        if (item !in inv) {
            return
        }
        if (!inv.hasFreeSpace()) {
            mes("You need a free inventory space to dismantle that.")
            return
        }
        invDel(inv, item, 1)
        invAdd(inv, first, 1)
        invAdd(inv, second, 1)
        mes("You dismantle the ${objName(item)}.")
    }

    private suspend fun ProtectedAccess.threadPouch() {
        when {
            player.stat(stats.crafting) < 75 ->
                mes("You need a Crafting level of at least 75 to augment your rune pouch.")
            ToaItemObjs.needle !in inv ->
                mes("You need a needle to work with the Thread of Elidinis.")
            !confirm("Augment your rune pouch?") -> {}
            ToaItemObjs.rune_pouch !in inv || ToaItemObjs.thread !in inv -> {}
            else -> {
                // The pouch's runes are kept in the player's varbits, so the new pouch has them.
                invDel(inv, ToaItemObjs.rune_pouch, 1)
                invDel(inv, ToaItemObjs.thread, 1)
                invAdd(inv, ToaItemObjs.divine_rune_pouch, 1)
                mes(
                    "You skillfully weave the Thread of Elidinis into the rune pouch and watch as it " +
                        "transforms into something more."
                )
            }
        }
    }

    private fun ProtectedAccess.objName(type: ObjType): String = ocName(type)

    private fun plates(count: Int): String =
        if (count == 1) "1 Armadylean plate" else "$count Armadylean plates"

    private companion object {
        const val WARD_SOUL_RUNES = 10_000
        const val WARD_XP = 260.0
    }
}
