package org.rsmod.api.player.input

import org.rsmod.game.type.obj.ObjType

/**
 * The result of the `skillmulti` chatbox interface.
 *
 * @property index the position of the clicked button, matching the order of the offered options.
 * @property obj the clicked option.
 * @property quantity how many the player asked for, from `1` to the offered maximum.
 */
public data class SkillMultiChoice(
    public val index: Int,
    public val obj: ObjType,
    public val quantity: Int,
)
