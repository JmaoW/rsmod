package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onIfOverlayButtonT
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.events.SpellCast
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.plugin.scripts.ScriptContext

/** The inventory item a spell was cast on. */
class SpellTarget(val slot: Int, val obj: UnpackedObjType)

/**
 * What every utility spell shares: finding the spell, listening for it being cast on an inventory
 * item, and paying for the cast (level, spellbook, runes, cast experience).
 */
class SpellCaster
@Inject
constructor(
    private val registry: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val xpMods: XpModifiers,
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
) {
    /** The cycle each player can next cast a spell on an item. */
    private val ready = WeakHashMap<Player, Int>()

    fun spell(obj: ObjType): MagicSpell =
        registry.getObjSpell(obj) ?: error("No spell is registered for $obj.")

    /** Whether [player] is still recovering from the last spell cast. */
    fun isCooling(player: Player, clock: Int): Boolean = clock < (ready[player] ?: Int.MIN_VALUE)

    /**
     * Listens for [spell] being cast on an inventory item. The cast is dropped, silently, while the
     * player is in the cooldown of an earlier cast.
     */
    fun ScriptContext.onItemSpell(
        spell: ObjType,
        action: suspend ProtectedAccess.(SpellTarget) -> Unit,
    ) {
        val component = spell(spell).component
        onIfOverlayButtonT(component, components.inv_items) { launch(action) }
    }

    /** Listens for [spell] being cast from its spellbook button, without a target. */
    fun ScriptContext.onSpell(spell: ObjType, action: suspend ProtectedAccess.() -> Unit) {
        val component = spell(spell).component
        onIfOverlayButton(component) {
            // The button's other op toggles the cast animation; only "Cast" casts the spell.
            if (op != IfButtonOp.Op1) {
                return@onIfOverlayButton
            }
            player.clearPendingAction(eventBus)
            player.resetFaceEntity()
            if (player.isAccessProtected) {
                return@onIfOverlayButton
            }
            protectedAccess.launch(player) {
                if (!isCooling(player, mapClock)) {
                    action()
                }
            }
        }
    }

    private fun IfOverlayButtonT.launch(action: suspend ProtectedAccess.(SpellTarget) -> Unit) {
        val target = targetObj ?: return
        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        if (player.isAccessProtected) {
            return
        }
        val slot = targetSlot
        protectedAccess.launch(player) {
            if (!isCooling(player, mapClock)) {
                action(SpellTarget(slot, target))
            }
        }
    }

    /**
     * Pays for casting [obj]: checks the level and spellbook, takes the runes and gives the cast
     * experience. The player cannot cast another item spell for [cooldown] ticks.
     *
     * With [free] set, the level, spellbook and runes are not asked for or taken, but the
     * experience is still given.
     *
     * @return the spell, or `null` if it cannot be cast (the player has been told why).
     */
    fun ProtectedAccess.cast(obj: ObjType, cooldown: Int, free: Boolean = false): MagicSpell? {
        val spell = spell(obj)
        if (!free && runes.attemptCast(player, spell).isFailure()) {
            return null
        }
        statAdvance(stats.magic, spell.castXp * xpMods.get(player, stats.magic))
        ready[player] = mapClock + cooldown
        publish(SpellCast(player, spell))
        return spell
    }
}
