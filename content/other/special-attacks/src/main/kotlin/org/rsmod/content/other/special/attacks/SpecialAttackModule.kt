package org.rsmod.content.other.special.attacks

import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.content.other.special.attacks.boost.CombatBoostSpecials
import org.rsmod.content.other.special.attacks.boost.StatBoostSpecialAttacks
import org.rsmod.content.other.special.attacks.magic.StaffSpecials
import org.rsmod.content.other.special.attacks.melee.ClawSpecials
import org.rsmod.content.other.special.attacks.melee.DaggerSpecials
import org.rsmod.content.other.special.attacks.melee.DragonLongswordSpecialAttack
import org.rsmod.content.other.special.attacks.melee.ExoticSpecials
import org.rsmod.content.other.special.attacks.melee.GodswordSpecials
import org.rsmod.content.other.special.attacks.melee.HeavySpecials
import org.rsmod.content.other.special.attacks.melee.MiscMeleeSpecials
import org.rsmod.content.other.special.attacks.melee.PolearmSpecials
import org.rsmod.content.other.special.attacks.melee.SwordSpecials
import org.rsmod.content.other.special.attacks.ranged.BowSpecials
import org.rsmod.content.other.special.attacks.ranged.CrossbowSpecials
import org.rsmod.content.other.special.attacks.ranged.DarkBowSpecialAttack
import org.rsmod.content.other.special.attacks.ranged.RareRangedSpecials
import org.rsmod.content.other.special.attacks.ranged.ThrownSpecials
import org.rsmod.plugin.module.PluginModule

class SpecialAttackModule : PluginModule() {
    override fun bind() {
        addSetBinding<SpecialAttackMap>(StatBoostSpecialAttacks::class.java)
        addSetBinding<SpecialAttackMap>(CombatBoostSpecials::class.java)
        addSetBinding<SpecialAttackMap>(DarkBowSpecialAttack::class.java)
        addSetBinding<SpecialAttackMap>(DragonLongswordSpecialAttack::class.java)
        addSetBinding<SpecialAttackMap>(DaggerSpecials::class.java)
        addSetBinding<SpecialAttackMap>(SwordSpecials::class.java)
        addSetBinding<SpecialAttackMap>(GodswordSpecials::class.java)
        addSetBinding<SpecialAttackMap>(HeavySpecials::class.java)
        addSetBinding<SpecialAttackMap>(PolearmSpecials::class.java)
        addSetBinding<SpecialAttackMap>(ClawSpecials::class.java)
        addSetBinding<SpecialAttackMap>(MiscMeleeSpecials::class.java)
        addSetBinding<SpecialAttackMap>(ExoticSpecials::class.java)
        addSetBinding<SpecialAttackMap>(BowSpecials::class.java)
        addSetBinding<SpecialAttackMap>(ThrownSpecials::class.java)
        addSetBinding<SpecialAttackMap>(CrossbowSpecials::class.java)
        addSetBinding<SpecialAttackMap>(StaffSpecials::class.java)
        addSetBinding<SpecialAttackMap>(RareRangedSpecials::class.java)
    }
}
