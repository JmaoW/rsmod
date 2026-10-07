package org.rsmod.api.combat

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.map.CoordGrid

class NpcAttacksTest {
    class Deps @Inject constructor(val attacks: NpcAttacks, val targeting: NpcTargeting)

    private class Setup(val npc: Npc, val first: Player, val second: Player, val far: Player)

    private fun GameTestState.runAttackTest(body: GameTestScope.(Deps, Setup) -> Unit) =
        runInjectedGameTest(Deps::class, null, PlayerHitScript::class) { deps ->
            // Every damage roll is 3, so a hit is easy to recognise.
            repeat(8) { random.next = 3 }
            val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), jad)
            val first = player
            first.placeAt(npc.coords.translateX(4))
            val second = registerPlayer(coords = npc.coords.translateX(-4).translateZ(2))
            // Standing in another instance, or just far away: out of range for the attack.
            val far = registerPlayer(coords = CoordGrid(0, 50, 50, 0, 0).translateX(40))
            for (each in listOf(first, second, far)) {
                each.stats[stats.hitpoints] = 99
            }
            body(deps, Setup(npc, first, second, far))
        }

    private val melee =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            anim = seqs.human_unarmedpunch,
            maxHit = 5,
            alwaysHits = true,
        )

    private val ranged =
        NpcAttack(
            style = NpcAttackStyle.Ranged,
            anim = seqs.human_bow,
            projectile = spotanims.ballista_special,
            projanim = projanims.arrow,
            maxHit = 5,
            alwaysHits = true,
        )

    private fun Player.damageTaken(before: Int) = before - hitpoints

    @Test
    fun GameTestState.`a melee attack hits the target on the next cycle`() =
        runAttackTest { deps, s ->
            val before = s.first.hitpoints

            assertTrue(deps.attacks.attack(s.npc, s.first, melee))
            assertEquals(0, s.first.damageTaken(before))

            advance(ticks = 2)
            assertEquals(3, s.first.damageTaken(before))
        }

    @Test
    fun GameTestState.`a ranged attack lands when its projectile does`() =
        runAttackTest { deps, s ->
            val before = s.first.hitpoints

            deps.attacks.attack(s.npc, s.first, ranged)
            advance(ticks = 1)
            assertEquals(0, s.first.damageTaken(before))

            advance(ticks = 6)
            assertEquals(3, s.first.damageTaken(before))
        }

    @Test
    fun GameTestState.`a hit is capped to the hitpoints the target had when it was queued`() =
        runAttackTest { deps, s ->
            s.first.stats[stats.hitpoints] = 2

            deps.attacks.attack(s.npc, s.first, melee)
            // Tick-eating: the target heals while the hit is in flight.
            s.first.stats[stats.hitpoints] = 50
            advance(ticks = 2)

            assertEquals(48, s.first.hitpoints)
        }

    @Test
    fun GameTestState.`one target style hits only the player it is fighting`() =
        runAttackTest { deps, s ->
            val before = s.second.hitpoints

            val hit = deps.attacks.perform(s.npc, s.first, ranged)
            advance(ticks = 8)

            assertEquals(listOf(s.first), hit)
            assertEquals(0, s.second.damageTaken(before))
        }

    @Test
    fun GameTestState.`all targets style attacks every player in range, each with its own hit`() =
        runAttackTest { deps, s ->
            deps.targeting.setStyle(jad, TargetingStyle.All)
            val firstBefore = s.first.hitpoints
            val secondBefore = s.second.hitpoints
            val farBefore = s.far.hitpoints

            val hit = deps.attacks.perform(s.npc, s.first, ranged)
            advance(ticks = 8)

            assertEquals(listOf(s.first, s.second), hit)
            assertEquals(3, s.first.damageTaken(firstBefore))
            assertEquals(3, s.second.damageTaken(secondBefore))
            assertEquals(0, s.far.damageTaken(farBefore))
        }

    @Test
    fun GameTestState.`alternating style takes turns between the players in range`() =
        runAttackTest { deps, s ->
            deps.targeting.setStyle(jad, TargetingStyle.Alternating)

            val turns = List(4) { deps.attacks.perform(s.npc, s.first, melee).single() }

            assertEquals(listOf(s.first, s.second, s.first, s.second), turns)
        }

    @Test
    fun GameTestState.`a scope can keep a player out of range`() = runAttackTest { deps, s ->
        deps.targeting.setStyle(jad, TargetingStyle.All)
        deps.targeting.addScope { _, player -> if (player === s.second) false else null }

        val hit = deps.attacks.perform(s.npc, s.first, melee)

        assertEquals(listOf(s.first), hit)
    }

    @Test
    fun GameTestState.`a target that cannot be attacked is skipped`() = runAttackTest { deps, s ->
        deps.targeting.setStyle(jad, TargetingStyle.All)
        s.second.statSub(stats.hitpoints, 100, 0)

        val hit = deps.attacks.perform(s.npc, s.first, melee)

        assertEquals(listOf(s.first), hit)
    }

    private companion object {
        private val jad =
            npcTypeFactory.create {
                name = "Test jad"
                size = 3
                hitpoints = 250
                defaultMode = NpcMode.None
            }
    }
}
