package org.rsmod.content.areas.misc.isleofsouls.event

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.invs
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.type.inv.InvTypeList

class LeagueSkillcapesTest {
    class Deps @Inject constructor(val skillcapes: LeagueSkillcapes, val invTypes: InvTypeList)

    @Test
    fun GameTestState.`level 99 puts the trimmed cape and hood in the bank`() =
        runInjectedGameTest(Deps::class) { deps ->
            val bank = player.invMap.getOrPut(deps.invTypes[invs.bank])

            deps.skillcapes.onLevelUp(player, stats.mining, 99)

            assertEquals(1, bank.count(objs.mining_skillcape_t))
            assertEquals(1, bank.count(objs.mining_hood))
            assertEquals(0, bank.count(objs.mining_skillcape))
            assertEquals(0, bank.count(objs.fishing_skillcape_t))
            assertEquals(0, bank.count(objs.max_cape_inv))
        }

    @Test
    fun GameTestState.`levels below 99 give nothing`() =
        runInjectedGameTest(Deps::class) { deps ->
            val bank = player.invMap.getOrPut(deps.invTypes[invs.bank])

            deps.skillcapes.onLevelUp(player, stats.mining, 98)

            assertEquals(0, bank.count(objs.mining_skillcape_t))
            assertEquals(0, bank.count(objs.mining_hood))
        }

    @Test
    fun GameTestState.`the last 99 also gives the max cape and hood`() =
        runInjectedGameTest(Deps::class) { deps ->
            val bank = player.invMap.getOrPut(deps.invTypes[invs.bank])
            val all =
                listOf(
                    stats.attack,
                    stats.defence,
                    stats.strength,
                    stats.hitpoints,
                    stats.ranged,
                    stats.prayer,
                    stats.magic,
                    stats.cooking,
                    stats.woodcutting,
                    stats.fletching,
                    stats.fishing,
                    stats.firemaking,
                    stats.crafting,
                    stats.smithing,
                    stats.mining,
                    stats.herblore,
                    stats.agility,
                    stats.thieving,
                    stats.slayer,
                    stats.farming,
                    stats.runecrafting,
                    stats.hunter,
                    stats.construction,
                )
            for (stat in all) {
                player.stats[stat] = 99
            }
            player.stats[stats.construction] = 98

            deps.skillcapes.onLevelUp(player, stats.mining, 99)
            assertEquals(0, bank.count(objs.max_cape_inv))

            player.stats[stats.construction] = 99
            deps.skillcapes.onLevelUp(player, stats.construction, 99)
            assertEquals(1, bank.count(objs.construction_skillcape_t))
            assertEquals(1, bank.count(objs.max_cape_inv))
            assertEquals(1, bank.count(objs.max_hood))
        }
}
