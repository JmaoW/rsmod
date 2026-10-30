package org.rsmod.content.areas.misc.isleofsouls.revenant

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.formulas.attributes.CombatMeleeAttributes
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatMeleeAttributeCollector
import org.rsmod.api.combat.formulas.attributes.collector.CombatNpcAttributeCollector
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.objs
import org.rsmod.api.drops.DropResult
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.dungeons.RevenantNpcs
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

class AvariceTest {
    private object AvariceModule : AbstractModule() {
        override fun configure() {
            bind(AvariceScript::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val avarice: AvariceScript,
        val melee: CombatMeleeAttributeCollector,
        val npcs: CombatNpcAttributeCollector,
    )

    private fun GameTestState.runAvariceTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, AvariceModule, AvariceScript::class) { deps -> body(deps) }

    private fun GameTestScope.wear(amulet: Boolean) {
        player.worn[Wearpos.Front.slot] = if (amulet) InvObj(objs.amulet_of_avarice) else null
        eventBus.publish(
            HeldEquipEvents.WearposChange(player, Wearpos.Front, objTypes[objs.amulet_of_avarice])
        )
    }

    private fun GameTestScope.obj(internal: String) =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.revenant() =
        spawnNpc(CoordGrid(3217, 10062, 0), npcTypes[RevenantNpcs.imp])

    @Test
    fun GameTestState.`the amulet skulls its wearer and the skull fades 20 minutes after`() =
        runAvariceTest { deps ->
            assertNull(player.skullIcon)

            wear(true)
            assertEquals(constants.skullicon_default, player.skullIcon, "white, like a pvp skull")

            wear(false)
            assertEquals(constants.skullicon_default, player.skullIcon, "still skulled for a while")

            deps.avarice.expire(player)
            assertNull(player.skullIcon)
        }

    @Test
    fun GameTestState.`putting the amulet back on stops the skull fading`() =
        runAvariceTest { deps ->
            wear(true)
            wear(false)
            wear(true)

            deps.avarice.expire(player)

            assertEquals(constants.skullicon_default, player.skullIcon, "worn, so it stays")
        }

    @Test
    fun GameTestState.`the skull fades by itself when its timer runs out`() = runAvariceTest {
        wear(true)
        wear(false)

        advance(ticks = AvariceScript.FADE_TICKS + 2)

        assertNull(player.skullIcon)
    }

    @Test
    fun GameTestState.`a revenant's drops are noted for a wearer, uniques and supplies excepted`() =
        runAvariceTest { deps ->
            player.worn[Wearpos.Front.slot] = InvObj(objs.amulet_of_avarice)
            val platebody = obj("rune_platebody")
            val drops =
                mutableListOf(
                    DropResult(platebody, 1),
                    DropResult(platebody, 1, boosted = true),
                    DropResult(ether_objs.ether, 50),
                    DropResult(ether_objs.bracelet_uncharged, 1),
                    DropResult(obj("blighted_sack_bind"), 1),
                    DropResult(obj("blighted_mantaray"), 2),
                )

            val changed = deps.avarice.note(player, revenant(), drops)

            assertEquals(1, changed)
            assertTrue(objTypes[drops[0].obj].isCert, "the first is noted")
            assertEquals(platebody.id, objTypes[drops[0].obj].certlink)
            assertEquals(platebody.id, drops[1].obj.id, "a unique is left alone")
            assertEquals(ether_objs.ether.id, drops[2].obj.id, "a stack has no noted form")
            assertEquals(ether_objs.bracelet_uncharged.id, drops[3].obj.id, "the bracelet")
            assertEquals(obj("blighted_sack_bind").id, drops[4].obj.id, "a secondary supply")
            assertEquals(obj("blighted_mantaray").id, drops[5].obj.id, "a secondary supply")
        }

    @Test
    fun GameTestState.`nothing is noted without the amulet or off a revenant`() =
        runAvariceTest { deps ->
            val platebody = obj("rune_platebody")

            val bare = mutableListOf(DropResult(platebody, 1))
            assertEquals(0, deps.avarice.note(player, revenant(), bare))
            assertEquals(platebody.id, bare[0].obj.id)

            player.worn[Wearpos.Front.slot] = InvObj(objs.amulet_of_avarice)
            val man =
                spawnNpc(CoordGrid(3217, 10062, 0), npcTypes.values.first { it.name == "Man" })
            val other = mutableListOf(DropResult(platebody, 1))
            assertEquals(0, deps.avarice.note(player, man, other))
            assertFalse(objTypes[other[0].obj].isCert)
        }

    @Test
    fun GameTestState.`the amulet's bonus is on for revenants only`() = runAvariceTest { deps ->
        player.worn[Wearpos.Front.slot] = InvObj(objs.amulet_of_avarice)
        assertTrue(
            CombatMeleeAttributes.AmuletOfAvarice in deps.melee.collect(player, null),
            "the amulet attribute",
        )

        val revenant = npcTypes[RevenantNpcs.dragon]
        assertTrue(
            CombatNpcAttributes.Revenant in
                deps.npcs.collect(revenant, currHp = 10, maxHp = 10, slayerTask = false),
            "the revenant attribute",
        )
        val man = npcTypes.values.first { it.name == "Man" }
        assertFalse(
            CombatNpcAttributes.Revenant in
                deps.npcs.collect(man, currHp = 10, maxHp = 10, slayerTask = false)
        )
    }
}
