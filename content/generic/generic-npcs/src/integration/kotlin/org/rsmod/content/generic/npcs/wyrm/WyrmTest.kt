package org.rsmod.content.generic.npcs.wyrm

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
import org.rsmod.map.CoordGrid

class WyrmTest {
    class Deps @Inject constructor(val modifier: NpcHitModifier)

    private val here = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestState.runWyrm(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            null,
            Wyrm::class,
            NpcHitScript::class,
            PlayerHitScript::class,
        ) { deps ->
            body(deps)
        }

    private fun GameTestScope.spawnWyrm(): Npc {
        player.teleport(here)
        val type = npcTypes.values.first { it.internalName == "wyrm_dark" }
        val wyrm = spawnNpc(here.translate(2, 0), type) { mode = NpcMode.None }
        advance()
        return wyrm
    }

    private fun GameTestScope.hit(deps: Deps, wyrm: Npc, damage: Int) {
        wyrm.queueHit(player, delay = 1, HitType.Melee, damage, deps.modifier)
        repeat(3) { advance() }
    }

    @Test
    fun GameTestState.`a wyrm stays burrowed until it is hit`() = runWyrm { _ ->
        val wyrm = spawnWyrm()
        repeat(5) { advance() }
        check(wyrm.visType.internalName == "wyrm_dark")
    }

    @Test
    fun GameTestState.`a hit makes the wyrm rise into its standing form`() = runWyrm { deps ->
        val wyrm = spawnWyrm()
        val uid = wyrm.uid
        hit(deps, wyrm, 3)
        check(wyrm.visType.internalName == "wyrm_light") { "Got ${wyrm.visType.internalName}." }
        check(wyrm.uid == uid) { "Rising must not drop the fight in progress." }
    }

    @Test
    fun GameTestState.`a hit that does no damage still makes it rise`() = runWyrm { deps ->
        val wyrm = spawnWyrm()
        hit(deps, wyrm, 0)
        check(wyrm.visType.internalName == "wyrm_light")
    }

    @Test
    fun GameTestState.`a wyrm burrows again once the fight is over`() = runWyrm { deps ->
        val wyrm = spawnWyrm()
        hit(deps, wyrm, 3)
        check(wyrm.visType.internalName == "wyrm_light")
        repeat(60) { advance() }
        check(wyrm.visType.internalName == "wyrm_dark") { "Got ${wyrm.visType.internalName}." }
    }
}
