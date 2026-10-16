package org.rsmod.api.spells.events

import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/**
 * Published once a spell has been paid for (level, runes, cast experience) and is being cast,
 * whether it is a combat spell or any other.
 */
public class SpellCast(public val player: Player, public val spell: MagicSpell) : UnboundEvent
