package org.rsmod.content.areas.misc.isleofsouls.instances

import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifSetColour
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.comp.ComponentType

/**
 * The client's boss health bar at the top of the screen (interface 303), kept for a group of
 * players: each one is shown the bar of the npc given to [show], and [hide] takes it away. One
 * instance per room or run, since it remembers what each player was last sent.
 *
 * The client only opens the overlay by itself when the player hits the npc, so the first time a
 * player is shown a bar the server runs the overlay's open and fade-in scripts (as captured by
 * TSPS): an npc nobody has hit yet, or one that cannot be hit, otherwise shows no bar.
 */
class BossHealthBar {
    private val shownTo = HashMap<Int, Long>()

    /** The colours each player's bar was last drawn in. */
    private val coloursOf = HashMap<Int, List<Int>>()

    /** The players (by account id) who are shown a bar now. */
    val viewers: Set<Int>
        get() = shownTo.keys

    /**
     * Shows [player] [npc]'s bar with [hp] out of its maximum, in [colours]. Cheap to call every
     * tick: nothing is sent unless something changed.
     */
    fun show(
        player: Player,
        npc: Npc,
        hp: Int = npc.hitpoints,
        colours: List<Int> = BossBarScripts.COLOURS,
    ) {
        val shownHp = hp.coerceIn(0, 0xFFFF)
        val base = npc.baseHitpointsLvl.coerceIn(1, 0xFFFF)
        val key = (npc.visType.id.toLong() shl 32) or (shownHp.toLong() shl 16) or base.toLong()
        val previous = shownTo[player.accountId]
        if (previous != null && coloursOf[player.accountId] != colours) {
            coloursOf[player.accountId] = colours
            setColours(player, colours)
            player.runClientScript(BossBarScripts.REDRAW, BossBarScripts.redrawArgs)
        }
        if (previous == key) {
            return
        }
        shownTo[player.accountId] = key
        VarPlayerIntMapSetter.set(player, BossBarBits.base, base)
        VarPlayerIntMapSetter.set(player, BossBarBits.hp, shownHp)
        VarPlayerIntMapSetter.set(player, BossBarVars.npc, npc.visType.id)
        if (previous == null) {
            VarPlayerIntMapSetter.set(player, BossBarBits.boss, 1)
            coloursOf[player.accountId] = colours
            setColours(player, colours)
            // The open script only resets the bar and fades it in when its `hp` layer is hidden. A
            // bar faded out earlier is still "shown" (just fully transparent), and the fade in
            // below does nothing unless it starts from the bar's transparency, so without this
            // only the first bar after logging in ever appeared.
            player.ifSetHide(BossBarComponents.hp, true)
            player.runClientScript(BossBarScripts.OPEN, BossBarScripts.openArgs)
            player.runClientScript(BossBarScripts.FADE_IN, BossBarScripts.fadeInArgs)
        }
    }

    /** Fades [player]'s bar out, if they are shown one. */
    fun hide(player: Player) {
        coloursOf.remove(player.accountId)
        if (shownTo.remove(player.accountId) != null) {
            player.runClientScript(BossBarScripts.FADE_OUT, BossBarScripts.fadeOutArgs)
            VarPlayerIntMapSetter.set(player, BossBarVars.npc, -1)
            VarPlayerIntMapSetter.set(player, BossBarBits.hp, 0)
            VarPlayerIntMapSetter.set(player, BossBarBits.base, 0)
            VarPlayerIntMapSetter.set(player, BossBarBits.boss, 0)
        }
    }

    /** Forgets a player without sending anything (they logged out, and the bar went with them). */
    fun forget(accountId: Int) {
        shownTo.remove(accountId)
        coloursOf.remove(accountId)
    }

    private fun setColours(player: Player, colours: List<Int>) {
        for ((component, colour) in BossBarComponents.coloured.zip(colours)) {
            player.ifSetColour(component, colour)
        }
    }
}

/**
 * The client's boss health overlay: the npc type whose name and health it shows (-1 for none), and
 * the health and maximum it draws.
 */
object BossBarVars : VarpReferences() {
    val npc = find("hpbar_hud_npc")
}

object BossBarBits : VarBitReferences() {
    val hp = find("hpbar_hud_hp")
    val base = find("hpbar_hud_basehp")
    val boss = find("hpbar_hud_boss")
}

/**
 * The overlay's (interface 303) client scripts and their component arguments, as TSPS captured
 * them: 2376 opens it (the 19 components its own onload passes), 2887 and 2889 fade it in and out
 * (14 components and the transparency to start from). A fade step only runs when told the
 * transparency the bar has now (that is how its timer carries a fade on), so the fade in that
 * matters is the one 2376 starts itself, which it does only while the `hp` layer is hidden; the
 * captured 2887 from 254 after it is then a no-op.
 */
object BossBarScripts {
    const val OPEN = 2376
    const val FADE_IN = 2887
    const val FADE_OUT = 2889

    private fun components(vararg children: Int): List<Int> = children.map { (303 shl 16) or it }

    val openArgs: List<Any> =
        components(0, 2, 4, 5, 8, 10, 20, 13, 14, 15, 9, 6, 7, 11, 18, 19, 16, 17, 3)
    private val fadeComponents = components(5, 8, 6, 7, 9, 11, 13, 14, 15, 20, 18, 19, 16, 17)
    val fadeInArgs: List<Any> = fadeComponents + 254
    val fadeOutArgs: List<Any> = fadeComponents + 0

    /**
     * The colours of the bar's back, sliding and remaining parts (15-bit RGB, captured by TSPS):
     * the usual red and green, and the blue a shield is drawn in (the Doom of Mokhaiotl's).
     */
    val COLOURS = listOf(25600, 576, 800)
    val SHIELD_COLOURS = listOf(132, 623, 853)

    /** Redraws the bar in new colours: 11 components and a flag. */
    const val REDRAW = 2102
    val redrawArgs: List<Any> = components(5, 20, 13, 14, 15, 8, 9, 18, 19, 16, 17) + 1
}

object BossBarComponents : ComponentReferences() {
    val hp = find("hpbar_hud:hp")
    val back = find("hpbar_hud:health_bar_back")
    val sliding = find("hpbar_hud:health_bar_sliding")
    val remaining = find("hpbar_hud:health_bar_remaining")

    val coloured: List<ComponentType>
        get() = listOf(back, sliding, remaining)
}
