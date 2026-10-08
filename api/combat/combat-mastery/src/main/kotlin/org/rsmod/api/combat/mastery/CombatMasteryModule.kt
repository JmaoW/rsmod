package org.rsmod.api.combat.mastery

import com.google.inject.multibindings.Multibinder
import org.rsmod.module.ExtendedModule

/**
 * Declares the (possibly empty) set of [CombatMasteryProvider]s, so [CombatMasteries] can be
 * injected whether or not any content registers one.
 */
public object CombatMasteryModule : ExtendedModule() {
    override fun bind() {
        Multibinder.newSetBinder(binder(), CombatMasteryProvider::class.java)
        bindInstance<CombatMasteries>()
        bindInstance<CombatMasteryEffects>()
    }
}
