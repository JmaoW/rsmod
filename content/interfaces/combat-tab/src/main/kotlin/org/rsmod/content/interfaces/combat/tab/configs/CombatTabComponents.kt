package org.rsmod.content.interfaces.combat.tab.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences

typealias combat_components = CombatTabComponents

typealias combat_interfaces = CombatTabInterfaces

object CombatTabComponents : ComponentReferences() {
    val stance1 = find("combat_interface:0", 2858390886771847763)
    val stance2 = find("combat_interface:1", 2437616626352755401)
    val stance3 = find("combat_interface:2", 4786378263585440964)
    val stance4 = find("combat_interface:3", 4365604003166348602)
    val auto_retaliate = find("combat_interface:retaliate", 6232502627085836658)
    val special_attack = find("combat_interface:special_attack", 5418117387536405546)

    val special_attack_orb = find("orbs:specbutton", 4900391869574997453)

    /** The "Choose spell" buttons of the combat tab; the defensive one only shows with a staff. */
    val autocast_normal = find("combat_interface:autocast_normal")
    val autocast_defensive = find("combat_interface:autocast_defensive")

    /** Container of the spell picker; its children 1.. are the autocast ids, child 0 is Cancel. */
    val autocast_spells = find("autocast:spells")
}

object CombatTabInterfaces : InterfaceReferences() {
    val autocast = find("autocast")
}
