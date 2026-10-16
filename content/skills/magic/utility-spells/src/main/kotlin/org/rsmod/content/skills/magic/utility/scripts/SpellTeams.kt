package org.rsmod.content.skills.magic.utility.scripts

import com.google.inject.ImplementedBy
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

/**
 * Who a caster's group spells and spells on other players may reach. A world with teams (the Isle
 * of Souls league) binds its own; by default everybody counts.
 */
@ImplementedBy(OpenSpellTeams::class)
interface SpellTeams {
    /** Whether [caster]'s spells may be cast on [other]. */
    fun areTeammates(caster: Player, other: Player): Boolean

    /** The players [caster]'s group spells reach: not [caster], on the same level, close by. */
    fun nearby(caster: Player, distance: Int): List<Player>
}

@Singleton
class OpenSpellTeams @Inject constructor(private val players: PlayerList) : SpellTeams {
    override fun areTeammates(caster: Player, other: Player): Boolean = caster !== other

    override fun nearby(caster: Player, distance: Int): List<Player> =
        players.filter { it !== caster && caster.isWithinDistance(it, distance) }
}
