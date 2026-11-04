package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import org.rsmod.api.combat.manager.AmmoPouches
import org.rsmod.api.combat.manager.PlayerAttackEvents
import org.rsmod.api.config.refs.categories
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.hit.events.PlayerHitEvents
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.script.onOpWorn4
import org.rsmod.api.script.onOpWorn5
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.content.other.special.attacks.ranged.TonalzticsOfRalos
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Colosseum's items and the way back to it:
 * - The ring of dueling's Fortis Colosseum teleport (Rub, or the worn option), to the isle's arch.
 *   Its Glory gate is [ColosseumSettings.ringGlory] (off for the event; the real game's is 12,000).
 *   The isle has none of the ring's other places.
 * - Dizana's quiver: sunfire splinters used on it charge it (up to 20,000, one charge a splinter),
 *   and each arrow or bolt fired with it worn uses a charge; Uncharge gives the splinters back. Its
 *   extra ammunition slot is [DizanasQuiverPouch]: Fill, Swap and Quiver-Remove in the worn
 *   equipment tab (and the equipment stats screen), Open and Empty on the quiver.
 * - The Tonalztics of Ralos: splinters charge it (up to 20,000), Check, Charge and Uncharge. Its
 *   attack is [ColosseumWeapons].
 * - Echo boots: an echo crystal on guardian boots makes them, and on echo boots adds charges, 6,000
 *   a crystal up to 60,000. While switched on they hit back for 1 whatever damages the wearer from
 *   within the 3x3 round them, a charge a time. Check, Toggle-activity, and Revert (back to
 *   guardian boots; the crystal is not given back).
 */
class ColosseumItemsScript
@Inject
constructor(
    private val colosseum: Colosseum,
    private val completions: ColosseumCompletions,
    private val pouch: DizanasQuiverPouch,
    private val ammoPouches: AmmoPouches,
    private val players: PlayerList,
    private val npcHitModifier: NpcHitModifier,
    private val objTypes: ObjTypeList,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val rings = colosseum_objs.rings_of_dueling
        for (ring in rings) {
            onOpHeld4(ring) { rub(ring) }
            onOpWorn5(ring) { teleport(ring, worn = true) }
            onOpWorn2(ring) { notOnIsle() }
            onOpWorn3(ring) { notOnIsle() }
            onOpWorn4(ring) { notOnIsle() }
        }

        val o = colosseum_objs
        onOpHeldU(o.sunfire_splinters, o.quiver_uncharged) { charge(o.quiver_uncharged) }
        onOpHeldU(o.sunfire_splinters, o.quiver_charged) { charge(o.quiver_charged) }
        // Charged: Wear, Open, Uncharge, Empty; uncharged: Wear, Open, Empty, Destroy.
        onOpHeld3(o.quiver_charged) { openPouch() }
        onOpHeld4(o.quiver_charged) { uncharge() }
        onOpHeld5(o.quiver_charged) { emptyPouch() }
        onOpHeld3(o.quiver_uncharged) { openPouch() }
        onOpHeld4(o.quiver_uncharged) { emptyPouch() }
        onEvent<PlayerAttackEvents.Hit> { shotFired(source, ammo) }

        ammoPouches.register(pouch)
        onPlayerLogin { pouch.syncOnLogin(player) }
        onIfOverlayButton(QuiverPouchComponents.worn_slot) { pouchSlot(player, op) }
        onIfModalButton(QuiverPouchComponents.stats_slot) { pouchSlot(player, it.op) }
        onIfModalButton(QuiverPouchComponents.screen_ammo) { removeFromPouch(player) }

        // Charged: Wield, Check, Charge, Uncharge.
        onOpHeldU(o.sunfire_splinters, o.tonalztics_uncharged) { chargeTonalztics() }
        onOpHeldU(o.sunfire_splinters, o.tonalztics_charged) { chargeTonalztics() }
        onOpHeld3(o.tonalztics_charged) { checkTonalztics() }
        onOpHeld4(o.tonalztics_charged) { chargeTonalztics() }
        onOpHeld5(o.tonalztics_charged) { unchargeTonalztics() }

        onOpHeldU(o.echo_crystal, o.guardian_boots) { makeEchoBoots() }
        onOpHeldU(o.echo_crystal, o.echo_boots) { addEchoCharges() }
        // Held: Wear, Check, Toggle-activity, Revert. Worn: Remove, Check, Toggle-activity.
        onOpHeld3(o.echo_boots) { checkEchoBoots() }
        onOpHeld4(o.echo_boots) { toggleEchoBoots() }
        onOpHeld5(o.echo_boots) { revertEchoBoots() }
        onOpWorn2(o.echo_boots) { checkEchoBoots() }
        onOpWorn3(o.echo_boots) { toggleEchoBoots() }
        onEvent<PlayerHitEvents.Impact> { echoRecoil(this) }
    }

    // The ring of dueling -------------------------------------------------------------------------

    private suspend fun ProtectedAccess.rub(ring: ObjType) {
        mes("You rub the ring...")
        val choice =
            choice2(
                "Fortis Colosseum.",
                1,
                "Nowhere.",
                2,
                title = "Where would you like to teleport to?",
            )
        if (choice == 1) {
            teleport(ring, worn = false)
        }
    }

    private fun ProtectedAccess.notOnIsle() {
        mes("That place is not on the Isle of Souls.")
    }

    private suspend fun ProtectedAccess.teleport(ring: ObjType, worn: Boolean) {
        val needed = colosseum.settings.ringGlory
        if (player.vars[colosseum_varps.glory] < needed) {
            mes("You need ${"%,d".format(needed)} Glory to teleport to the Fortis Colosseum.")
            return
        }
        if (colosseum.runOf(player) != null) {
            mes("You are already in the Fortis Colosseum.")
            return
        }
        anim(ColosseumItemSeqs.teleport)
        spotanim(ColosseumItemSpots.teleport, height = TELEPORT_HEIGHT)
        delay(TELEPORT_TICKS)
        telejump(ColosseumArena.isleArrival)
        resetAnim()
        useCharge(ring, worn)
        completions.teleported(player)
    }

    /** One charge of the ring goes: the next ring down takes its place, or it crumbles. */
    private fun ProtectedAccess.useCharge(ring: ObjType, worn: Boolean) {
        val rings = colosseum_objs.rings_of_dueling
        val index = rings.indexOfFirst { it.id == ring.id }
        val next = rings.getOrNull(index + 1)
        val left = rings.size - index - 1
        val inv = if (worn) player.worn else this.inv
        val slot =
            if (worn) Wearpos.Ring.slot
            else (0 until inv.size).firstOrNull { inv[it]?.id == ring.id }
        if (slot == null || inv[slot]?.id != ring.id) {
            return
        }
        if (next == null) {
            inv[slot] = null
            mes("<col=7f007f>Your ring of dueling crumbles to dust.</col>")
            if (worn) {
                WornUnequipOp.notifyWornUnequip(player, Wearpos.Ring, objTypes[ring], eventBus)
            }
            return
        }
        inv[slot] = InvObj(next, 1)
        val uses = if (left == 1) "one use" else "${WORDS.getOrElse(left) { "$left" }} uses"
        mes("<col=7f007f>Your ring of dueling has $uses left.</col>")
    }

    // Dizana's quiver -----------------------------------------------------------------------------

    private fun ProtectedAccess.charge(quiver: ObjType) {
        val charges = if (quiver.id == colosseum_objs.quiver_charged.id) quiverCharges() else 0
        val room = MAX_CHARGES - charges
        val have = inv.count(objTypes[colosseum_objs.sunfire_splinters])
        val added = minOf(room, have)
        if (added <= 0) {
            mes("Your quiver can't hold any more charges.")
            return
        }
        invDel(inv, colosseum_objs.sunfire_splinters, added)
        if (quiver.id != colosseum_objs.quiver_charged.id) {
            invReplace(inv, quiver, 1, colosseum_objs.quiver_charged)
        }
        setCharges(player, charges + added)
        mes("You charge your quiver. It now holds ${"%,d".format(charges + added)} charges.")
    }

    private fun ProtectedAccess.uncharge() {
        val charges = quiverCharges()
        if (charges > 0 && !invAdd(inv, colosseum_objs.sunfire_splinters, charges).success) {
            mes("You don't have enough inventory space.")
            return
        }
        invReplace(inv, colosseum_objs.quiver_charged, 1, colosseum_objs.quiver_uncharged)
        setCharges(player, 0)
        mes("You remove the sunfire splinters from your quiver.")
    }

    private fun ProtectedAccess.quiverCharges(): Int = player.vars[colosseum_varbits.quiver_charges]

    private fun setCharges(player: Player, charges: Int) {
        VarPlayerIntMapSetter.set(player, colosseum_varbits.quiver_charges, charges)
    }

    /** An arrow or bolt fired with a charged quiver worn uses one of its charges. */
    private fun shotFired(player: Player, ammo: ObjType?) {
        if (ammo == null) {
            return
        }
        val back = player.worn[Wearpos.Back.slot] ?: return
        if (back.id != colosseum_objs.quiver_charged.id) {
            return
        }
        val type = objTypes[ammo]
        val arrowOrBolt =
            type.isCategoryType(categories.arrows) ||
                type.isCategoryType(categories.dragon_arrow) ||
                type.isCategoryType(categories.crossbow_bolt)
        if (!arrowOrBolt) {
            return
        }
        val left = player.vars[colosseum_varbits.quiver_charges] - 1
        setCharges(player, maxOf(0, left))
        if (left <= 0) {
            player.worn[Wearpos.Back.slot] = InvObj(colosseum_objs.quiver_uncharged, 1)
            player.rebuildAppearance()
            player.mes("<col=ef1020>Your quiver has run out of charges.</col>")
        }
    }

    // The quiver's extra ammunition slot ---------------------------------------------------------

    /** The quiver's screen (592), which shows the pouch and lets its ammunition be taken out. */
    private fun ProtectedAccess.openPouch() {
        player.ifOpenMainModal(QuiverPouchInterfaces.quiver, eventBus)
        val charges = if (inv.contains(colosseum_objs.quiver_charged)) quiverCharges() else 0
        player.ifSetText(QuiverPouchComponents.screen_charges, "Charges: ${"%,d".format(charges)}")
    }

    private fun ProtectedAccess.emptyPouch() {
        if (pouch.isEmpty(player)) {
            mes("Your quiver's ammunition pouch is empty.")
            return
        }
        removeFromPouch(player)
    }

    /**
     * The extra slot in the worn equipment tab: empty, its only option is Fill; filled, it has
     * Quiver-Remove, Swap and Examine (client script 5026).
     */
    private fun pouchSlot(player: Player, op: IfButtonOp) {
        if (pouch.isEmpty(player)) {
            if (op == IfButtonOp.Op2) {
                fillPouch(player)
            }
            return
        }
        when (op) {
            IfButtonOp.Op1 -> removeFromPouch(player)
            IfButtonOp.Op2 -> swapPouch(player)
            IfButtonOp.Op10 -> pouch.obj(player)?.let { objTypes[it] }?.let { player.mes(it.desc) }
            else -> {}
        }
    }

    /** Fill: the ammo slot's arrows or bolts go into the pouch. */
    private fun fillPouch(player: Player) {
        if (!pouch.hasQuiver(player)) {
            return
        }
        val worn = player.worn[Wearpos.Quiver.slot]
        if (worn == null) {
            player.mes(
                "<col=ef1020>You have nothing in your worn quiver to fill your Dizana's Quiver with."
            )
            return
        }
        if (!pouch.fitsPouch(worn)) {
            player.mes("Only arrows and bolts can be stored in Dizana's quiver.")
            return
        }
        player.worn[Wearpos.Quiver.slot] = null
        pouch.set(player, worn.id, worn.count)
    }

    /** Swap: the ammo slot and the pouch change places (only arrows or bolts go in the pouch). */
    private fun swapPouch(player: Player) {
        val worn = player.worn[Wearpos.Quiver.slot]
        if (worn != null && !pouch.fitsPouch(worn)) {
            player.mes("Only arrows and bolts can be stored in Dizana's quiver.")
            return
        }
        pouch.swap(player)
    }

    /** The pouch's ammunition goes to the inventory. */
    private fun removeFromPouch(player: Player) {
        val obj = pouch.obj(player) ?: return
        val count = pouch.count(player)
        if (!player.invAdd(player.inv, obj, count).success) {
            player.mes("You don't have enough inventory space.")
            return
        }
        pouch.set(player, null, 0)
    }

    // The Tonalztics of Ralos ---------------------------------------------------------------------

    private fun ProtectedAccess.tonalzticsCharges(): Int =
        player.vars[colosseum_varbits.tonalztics_charges]

    private fun ProtectedAccess.chargeTonalztics() {
        val charged = inv.contains(colosseum_objs.tonalztics_charged)
        val charges = if (charged) tonalzticsCharges() else 0
        val room = TonalzticsOfRalos.MAX_CHARGES - charges
        val have = inv.count(objTypes[colosseum_objs.sunfire_splinters])
        val added = minOf(room, have)
        if (added <= 0) {
            mes(
                if (have <= 0) "You need sunfire splinters to charge the Tonalztics of Ralos."
                else "Your Tonalztics of Ralos can't hold any more charges."
            )
            return
        }
        invDel(inv, colosseum_objs.sunfire_splinters, added)
        if (!charged) {
            invReplace(
                inv,
                colosseum_objs.tonalztics_uncharged,
                1,
                colosseum_objs.tonalztics_charged,
            )
        }
        VarPlayerIntMapSetter.set(player, colosseum_varbits.tonalztics_charges, charges + added)
        mes(
            "You charge the Tonalztics of Ralos. It now has ${"%,d".format(charges + added)} " +
                "charges."
        )
    }

    private fun ProtectedAccess.checkTonalztics() {
        mes("Your Tonalztics of Ralos has ${"%,d".format(tonalzticsCharges())} charges left.")
    }

    private fun ProtectedAccess.unchargeTonalztics() {
        val charges = tonalzticsCharges()
        if (charges > 0 && !invAdd(inv, colosseum_objs.sunfire_splinters, charges).success) {
            mes("You don't have enough inventory space.")
            return
        }
        invReplace(inv, colosseum_objs.tonalztics_charged, 1, colosseum_objs.tonalztics_uncharged)
        VarPlayerIntMapSetter.set(player, colosseum_varbits.tonalztics_charges, 0)
        mes("You remove the sunfire splinters from the Tonalztics of Ralos.")
    }

    // Echo boots ----------------------------------------------------------------------------------

    private fun ProtectedAccess.echoCharges(): Int = player.vars[colosseum_varbits.echo_charges]

    private fun ProtectedAccess.makeEchoBoots() {
        invDel(inv, colosseum_objs.echo_crystal)
        invReplace(inv, colosseum_objs.guardian_boots, 1, colosseum_objs.echo_boots)
        VarPlayerIntMapSetter.set(player, colosseum_varbits.echo_charges, ECHO_PER_CRYSTAL)
        mes("You attach the echo crystal to your guardian boots.")
    }

    private fun ProtectedAccess.addEchoCharges() {
        val charges = echoCharges()
        if (charges + ECHO_PER_CRYSTAL > ECHO_MAX_CHARGES) {
            mes("Your echo boots can't hold any more charges.")
            return
        }
        invDel(inv, colosseum_objs.echo_crystal)
        VarPlayerIntMapSetter.set(
            player,
            colosseum_varbits.echo_charges,
            charges + ECHO_PER_CRYSTAL,
        )
        mes(
            "You add the echo crystal to your boots. They now have " +
                "${"%,d".format(charges + ECHO_PER_CRYSTAL)} charges."
        )
    }

    private fun ProtectedAccess.checkEchoBoots() {
        val state = if (player.vars[colosseum_varps.echo_off] == 0) "active" else "inactive"
        mes("Your echo boots have ${"%,d".format(echoCharges())} charges left and are $state.")
    }

    private fun ProtectedAccess.toggleEchoBoots() {
        val off = player.vars[colosseum_varps.echo_off] == 0
        VarPlayerIntMapSetter.set(player, colosseum_varps.echo_off, if (off) 1 else 0)
        mes(
            if (off) "You deactivate the recoil effect of your echo boots."
            else "You activate the recoil effect of your echo boots."
        )
    }

    private suspend fun ProtectedAccess.revertEchoBoots() {
        val choice =
            choice2("Yes.", 1, "No.", 2, title = "Revert the boots? The echo crystal will be lost.")
        if (choice != 1 || !inv.contains(colosseum_objs.echo_boots)) {
            return
        }
        invReplace(inv, colosseum_objs.echo_boots, 1, colosseum_objs.guardian_boots)
        VarPlayerIntMapSetter.set(player, colosseum_varbits.echo_charges, 0)
        mes("You revert your echo boots to guardian boots.")
    }

    /**
     * Echo boots' recoil (wiki): whatever damaged the wearer from within the 3x3 round them (in
     * melee reach, for an npc of any size) takes 1 back, and the boots use a charge. Typeless hits
     * from players are not answered, so two players in echo boots do not recoil each other's
     * recoil.
     */
    private fun echoRecoil(event: PlayerHitEvents.Impact) {
        val player = event.player
        if (player.worn[Wearpos.Feet.slot]?.id != colosseum_objs.echo_boots.id) {
            return
        }
        val charges = player.vars[colosseum_varbits.echo_charges]
        if (charges <= 0 || player.vars[colosseum_varps.echo_off] != 0) {
            return
        }
        val hit = event.hit
        val npc = event.npcSource
        val attacker =
            if (npc == null && hit.isFromPlayer && hit.type != HitType.Typeless) {
                hit.resolvePlayerSource(players)
            } else {
                null
            }
        val source: PathingEntity = npc ?: attacker ?: return
        if (!source.isSlotAssigned || source.distanceTo(player) > 1) {
            return
        }
        VarPlayerIntMapSetter.set(player, colosseum_varbits.echo_charges, charges - 1)
        when (source) {
            is Npc -> source.queueHit(player, 1, HitType.Typeless, 1, npcHitModifier)
            is Player -> source.queueHit(player, 1, HitType.Typeless, 1)
        }
    }

    companion object {
        const val MAX_CHARGES = 20_000
        const val ECHO_PER_CRYSTAL = 6_000
        const val ECHO_MAX_CHARGES = 60_000
        const val TELEPORT_TICKS = 3
        const val TELEPORT_HEIGHT = 92
        val WORDS = listOf("no", "one", "two", "three", "four", "five", "six", "seven")
    }
}

internal object ColosseumItemSeqs : SeqReferences() {
    val teleport = find("human_castteleport")
}

internal object ColosseumItemSpots : SpotanimReferences() {
    val teleport = find("teleport_casting")
}

internal object QuiverPouchInterfaces : InterfaceReferences() {
    val quiver = find("dizanas_quiver")
}

/**
 * The extra slot's ammunition in the worn equipment tab and on the equipment stats screen (ops 1, 2
 * and 10 are on in the cache), and the quiver screen's ammunition and charges.
 */
internal object QuiverPouchComponents : ComponentReferences() {
    val worn_slot = find("wornitems:extra_quiver_ammo")
    val stats_slot = find("equipment:extra_quiver_ammo")
    val screen_ammo = find("dizanas_quiver:ammo_obj")
    val screen_charges = find("dizanas_quiver:charges_text")
}
