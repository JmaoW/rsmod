package org.rsmod.content.areas.misc.isleofsouls.magic

import com.google.inject.AbstractModule
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.params
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.skills.magic.utility.scripts.TeleportDestinations
import org.rsmod.game.type.obj.ObjTypeList

class IsleTeleportTest {
    class Deps
    @Inject
    constructor(
        val spells: MagicSpellRegistry,
        val isle: IsleTeleportDestinations,
        val objTypes: ObjTypeList,
    )

    object SpellModule : AbstractModule() {
        override fun configure() {
            install(MagicSpellModule())
        }
    }

    @Test
    fun GameTestState.`every teleport with a real world destination has an isle destination`() =
        runInjectedGameTest(Deps::class, SpellModule) { deps ->
            val unmapped =
                deps.spells
                    .teleportSpells()
                    .filter { deps.objTypes[it.obj].paramOrNull(params.spell_telecoord) != null }
                    .filter { it.obj !in deps.isle.spells }
                    .map { it.name }
                    .sorted()

            // The house needs a house, which the isle does not have.
            val expected = setOf("Teleport to House")
            val missing = unmapped.filterNot { it in expected }
            check(missing.isEmpty()) { "Teleports with no isle destination: $missing" }
        }

    @Test
    fun GameTestState.`the isle's destinations replace the cache's and are not the real world`() =
        runInjectedGameTest(Deps::class, SpellModule) { deps ->
            val destinations = TeleportDestinations(deps.objTypes)
            deps.isle.apply(destinations)
            check(destinations.overridesOnly) { "Teleports should only use the isle's places." }

            for (spell in deps.spells.teleportSpells().filter { it.obj in deps.isle.spells }) {
                val isle = destinations.destination(spell.obj)
                val real = deps.objTypes[spell.obj].paramOrNull(params.spell_telecoord)
                check(isle != null) { "${spell.name} has no destination." }
                check(isle != real) { "${spell.name} still goes to the real world: $real." }
            }
        }
}
