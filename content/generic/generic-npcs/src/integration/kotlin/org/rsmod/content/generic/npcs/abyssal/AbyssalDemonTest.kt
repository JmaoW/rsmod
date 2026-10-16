package org.rsmod.content.generic.npcs.abyssal

import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.map.CoordGrid

class AbyssalDemonTest {
    class Deps
    @Inject
    constructor(val random: GameTestScope.VariableGameRandom, val modifier: NpcHitModifier)

    private val here = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestState.runDemon(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            null,
            AbyssalDemon::class,
            NpcHitScript::class,
            PlayerHitScript::class,
        ) { deps ->
            body(deps)
        }

    private fun GameTestScope.demonType(): UnpackedNpcType =
        npcTypes.values.first { it.internalName == "slayer_abyssal" }

    private fun GameTestScope.spawnDemon(): Npc {
        player.teleport(here)
        val demon = spawnNpc(here.translate(2, 0), demonType()) { mode = NpcMode.None }
        advance()
        return demon
    }

    private fun GameTestScope.hit(deps: Deps, demon: Npc, damage: Int) {
        demon.queueHit(player, delay = 1, HitType.Melee, damage, deps.modifier)
        repeat(4) { advance() }
    }

    @Test
    fun GameTestState.`the demon teleports next to the player on a 1 in 6 roll`() =
        runDemon { deps ->
            val demon = spawnDemon()
            repeat(10) { deps.random.next = 0 }
            hit(deps, demon, 5)

            check(demon.coords != here.translate(2, 0)) { "The demon should have moved." }
            check(demon.coords.chebyshevDistance(player.coords) == 1) {
                "Expected the demon next to the player, got ${demon.coords} for ${player.coords}."
            }
            check(player.coords == here) { "The player should not move." }
        }

    @Test
    fun GameTestState.`the demon teleports the player next to it when its own roll fails`() =
        runDemon { deps ->
            val demon = spawnDemon()
            deps.random.next = 1
            repeat(10) { deps.random.next = 0 }
            hit(deps, demon, 5)

            check(demon.coords == here.translate(2, 0)) { "The demon should stay put." }
            check(player.coords != here) { "The player should have been moved." }
            check(player.coords.chebyshevDistance(demon.coords) == 1) {
                "Expected the player next to the demon, got ${player.coords} for ${demon.coords}."
            }
        }

    @Test
    fun GameTestState.`nobody teleports when both rolls fail`() = runDemon { deps ->
        val demon = spawnDemon()
        repeat(10) { deps.random.next = 1 }
        hit(deps, demon, 5)

        check(demon.coords == here.translate(2, 0))
        check(player.coords == here)
    }

    @Test
    fun GameTestState.`a hit that does no damage never teleports`() = runDemon { deps ->
        val demon = spawnDemon()
        repeat(10) { deps.random.next = 0 }
        hit(deps, demon, 0)

        check(demon.coords == here.translate(2, 0))
        check(player.coords == here)
    }
}
