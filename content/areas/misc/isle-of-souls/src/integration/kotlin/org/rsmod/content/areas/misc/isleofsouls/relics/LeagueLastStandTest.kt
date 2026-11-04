package org.rsmod.content.areas.misc.isleofsouls.relics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

class LeagueLastStandTest {
    private fun GameTestScope.prepare(chosen: Boolean): Npc {
        player.stats[stats.hitpoints] = 99
        player.stats[stats.attack] = 70
        val option = if (chosen) LeagueLastStand.OPTION else 0
        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[LeagueLastStand.TIER], option)
        return spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())
    }

    private fun GameTestScope.hit(npc: Npc, damage: Int) {
        player.queueHit(npc, delay = 1, HitType.Typeless, damage = damage)
        advance(ticks = 1)
    }

    @Test
    fun GameTestState.`without the relic a lethal hit kills`() =
        runGameTest(PlayerHitScript::class, LeagueLastStand::class) {
            val npc = prepare(chosen = false)
            hit(npc, 150)
            assertEquals(0, player.hitpoints)
        }

    @Test
    fun GameTestState.`a lethal hit leaves 1 hitpoint and boosts combat stats`() =
        runGameTest(PlayerHitScript::class, LeagueLastStand::class) {
            val npc = prepare(chosen = true)
            hit(npc, 150)
            assertEquals(1, player.hitpoints)
            assertEquals(255, player.stat(stats.attack))

            advance(ticks = 1)
            assertEquals(255 - 16, player.stat(stats.attack), "drains 16 a tick")
        }

    @Test
    fun GameTestState.`hits during the effect cannot kill and the damage dealt heals at the end`() =
        runGameTest(PlayerHitScript::class, NpcHitScript::class, LeagueLastStand::class) {
            val npc = prepare(chosen = true)
            npc.baseHitpointsLvl = 200
            npc.hitpoints = 200
            hit(npc, 150)
            hit(npc, 40)
            assertEquals(1, player.hitpoints)

            npc.queueHit(player, delay = 1, HitType.Typeless, 30, NpcHitModifier {})
            npc.queueHit(player, delay = 1, HitType.Typeless, 25, NpcHitModifier {})
            advance(ticks = 2)
            assertEquals(1, player.hitpoints, "no healing until the effect ends")

            advance(ticks = LeagueLastStand.DURATION_TICKS)
            assertEquals(1 + 55, player.hitpoints, "healed by the 55 dealt")
            assertEquals(70 + 15, player.stat(stats.attack), "drained to 15 above base")
        }

    @Test
    fun GameTestState.`without damage dealt the player is left on 1 hitpoint`() =
        runGameTest(PlayerHitScript::class, LeagueLastStand::class) {
            val npc = prepare(chosen = true)
            hit(npc, 150)
            advance(ticks = LeagueLastStand.DURATION_TICKS)
            assertEquals(1, player.hitpoints)
        }

    @Test
    fun GameTestState.`it cannot trigger again until the cooldown ends`() =
        runGameTest(PlayerHitScript::class, LeagueLastStand::class) {
            val npc = prepare(chosen = true)
            hit(npc, 150)
            advance(ticks = LeagueLastStand.DURATION_TICKS)
            assertEquals(1, player.hitpoints)

            hit(npc, 150)
            assertEquals(0, player.hitpoints, "still on cooldown")
        }

    @Test
    fun GameTestState.`after the cooldown it triggers again`() =
        runGameTest(PlayerHitScript::class, LeagueLastStand::class) {
            val npc = prepare(chosen = true)
            hit(npc, 150)
            advance(ticks = LeagueLastStand.COOLDOWN_TICKS)
            player.stats[stats.hitpoints] = 99

            hit(npc, 150)
            assertEquals(1, player.hitpoints)
        }
}
