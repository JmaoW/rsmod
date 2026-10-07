package org.rsmod.content.areas.misc.isleofsouls.miningguild

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.invs
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.shops.ShopScript
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.config.ShopComponents
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.mining.configs.ProspectorObjs
import org.rsmod.content.skills.mining.configs.ProspectorXpMod
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.game.shop.Shop
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

class MiningGuildShopsTest {
    /** The real server shares one of each of these between every script, so tests must too. */
    private object ShopModule : AbstractModule() {
        override fun configure() {
            bind(ShopOperationMap::class.java).`in`(Scopes.SINGLETON)
            bind(Shops::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val guild: MiningGuildScript,
        val shops: MiningGuildShopScript,
        val validator: BoundValidator,
        val invTypes: InvTypeList,
    )

    private val nugget = MiningGuildObjs.golden_nugget

    private fun GameTestState.runShopTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            ShopModule,
            MiningGuildScript::class,
            MiningGuildShopScript::class,
            ShopScript::class,
        ) { deps ->
            body(deps)
        }

    private fun rockAt(coords: CoordGrid) =
        BoundLocInfo(
            coords = coords,
            entity = LocEntity(1, LocShape.CentrepieceStraight.id, LocAngle.West.id),
            layer = 0,
            width = 1,
            length = 1,
            forceApproachFlags = 0,
        )

    private val guildRock = rockAt(CoordGrid(3016, 9717, 0))
    private val dwarvenMineRock = rockAt(CoordGrid(3039, 9783, 0))

    private fun GameTestScope.npcAt(coords: CoordGrid): Npc = npcRepo.findAll(coords).first()

    private fun GameTestScope.openShopOf(npc: Npc): Shop {
        player.placeAt(npc.coords.translateZ(1))
        player.opNpc3(npc)
        advance(ticks = 3)
        return checkNotNull(player.openedShop) { "no shop opened" }
    }

    private fun GameTestScope.buy(shop: Shop, obj: ObjType, op: IfButtonOp = IfButtonOp.Op2) {
        val slot = shop.inv.indexOfFirst { it?.id == obj.id }
        check(slot != -1) { "not in stock: $obj" }
        player.ifButton(ShopComponents.shop_inv, comsub = slot + 1, obj = obj.id, op = op)
        advance(ticks = 1)
    }

    private fun GameTestScope.sell(obj: ObjType, op: IfButtonOp = IfButtonOp.Op2) {
        val slot = player.inv.indexOfFirst { it?.id == obj.id }
        check(slot != -1) { "not in inventory: $obj" }
        player.ifButton(ShopComponents.shop_side_inv, comsub = slot, obj = obj.id, op = op)
        advance(ticks = 1)
    }

    @Test
    fun GameTestState.`percy and yarsul are placed in the guild`() = runShopTest { deps ->
        deps.shops.placeNpcs()

        assertEquals(MiningGuildNpcs.percy.id, npcAt(MiningGuildShopScript.PERCY).type.id)
        assertEquals(MiningGuildNpcs.yarsul.id, npcAt(MiningGuildShopScript.YARSUL).type.id)

        for (coords in listOf(MiningGuildShopScript.PERCY, MiningGuildShopScript.YARSUL)) {
            val npc = npcAt(coords)
            assertEquals(NpcMode.Wander, npc.mode, "${npc.type.name} should wander")
            assertEquals(MiningGuildNpcs.WANDER_RANGE, npc.wanderRange)
        }
    }

    @Test
    fun GameTestState.`percy sells the prospector pieces for nuggets`() = runShopTest { deps ->
        deps.shops.registerCurrency()
        deps.shops.placeNpcs()
        val shop = openShopOf(npcAt(MiningGuildShopScript.PERCY))
        assertEquals(MiningGuildCurrencies.golden_nuggets.id, shop.currency.id)

        val prices =
            listOf(
                ProspectorObjs.boots to 30,
                ProspectorObjs.helmet to 40,
                ProspectorObjs.legs to 50,
                ProspectorObjs.jacket to 60,
            )
        for ((piece, price) in prices) {
            player.clearInv()
            player.inv[0] = InvObj(nugget, price - 1)
            buy(shop, piece)
            assertEquals(0, player.count(piece), "bought a piece with ${price - 1} nuggets")
            assertMessageSent("You don't have enough golden nuggets.")

            player.inv[0] = InvObj(nugget, price)
            buy(shop, piece)
            assertEquals(1, player.count(piece), "could not buy for $price nuggets")
            assertEquals(0, player.count(nugget))
        }
    }

    @Test
    fun GameTestState.`percy will not buy the untradeable pieces back`() = runShopTest { deps ->
        deps.shops.registerCurrency()
        deps.shops.placeNpcs()
        openShopOf(npcAt(MiningGuildShopScript.PERCY))
        player.clearInv()
        player.inv[0] = InvObj(ProspectorObjs.helmet)

        sell(ProspectorObjs.helmet)

        assertEquals(1, player.count(ProspectorObjs.helmet))
        assertEquals(0, player.count(nugget))
        assertMessageSent("You can't sell this item.")
    }

    @Test
    fun GameTestState.`yarsul sells pickaxes for coins`() = runShopTest { deps ->
        deps.shops.placeNpcs()
        val shop = openShopOf(npcAt(MiningGuildShopScript.YARSUL))

        val prices =
            listOf(
                objs.bronze_pickaxe to 1,
                objs.iron_pickaxe to 140,
                objs.steel_pickaxe to 500,
                objs.mithril_pickaxe to 1300,
                objs.adamant_pickaxe to 3200,
                objs.rune_pickaxe to 32000,
            )
        for ((pickaxe, price) in prices) {
            player.clearInv()
            player.inv[0] = InvObj(objs.coins, price)
            buy(shop, pickaxe)
            assertEquals(1, player.count(pickaxe), "could not buy for $price coins")
            assertEquals(0, player.count(objs.coins), "price of $pickaxe")
        }
    }

    @Test
    fun GameTestState.`every ore mined in the guild comes with a golden nugget`() =
        runShopTest { _ ->
            player.clearInv()
            repeat(5) { eventBus.publish(Mining.MineOre(player, guildRock, objs.coal)) }
            assertEquals(5, player.count(nugget))
        }

    @Test
    fun GameTestState.`ore mined outside the guild gives no nugget`() = runShopTest { _ ->
        player.clearInv()
        eventBus.publish(Mining.MineOre(player, dwarvenMineRock, objs.copper_ore))
        eventBus.publish(Mining.MineOre(player, rockAt(CoordGrid(0, 50, 50, 0, 0)), objs.coal))
        assertEquals(0, player.count(nugget))
    }

    @Test
    fun GameTestState.`a nugget goes to the bank when the inventory is full`() =
        runShopTest { deps ->
            player.clearInv()
            player.fillInv()

            eventBus.publish(Mining.MineOre(player, guildRock, objs.coal))

            assertEquals(0, player.count(nugget))
            val bank = player.invMap.getOrPut(deps.invTypes[invs.bank])
            assertEquals(1, bank.count(nugget))
        }

    @Test
    fun GameTestState.`the prospector kit adds 2 and a half percent mining xp as a set`() =
        runShopTest { _ ->
            val mod = ProspectorXpMod()
            fun bonus() = with(mod) { player.modifier(stats.mining) }

            assertEquals(0.0, bonus(), 1e-9)
            player.worn[Wearpos.Feet.slot] = InvObj(ProspectorObjs.boots)
            assertEquals(0.002, bonus(), 1e-9)
            player.worn[Wearpos.Hat.slot] = InvObj(ProspectorObjs.helmet)
            assertEquals(0.006, bonus(), 1e-9)
            player.worn[Wearpos.Legs.slot] = InvObj(ProspectorObjs.legs)
            assertEquals(0.012, bonus(), 1e-9)
            player.worn[Wearpos.Torso.slot] = InvObj(ProspectorObjs.jacket)
            assertEquals(0.025, bonus(), 1e-9)

            // Only Mining benefits.
            assertEquals(0.0, with(mod) { player.modifier(stats.woodcutting) }, 1e-9)
        }

    @Test
    fun GameTestState.`the guild bank chest can be reached from its open side only`() =
        runShopTest { deps ->
            val chestTile = CoordGrid(3012, 9718, 0)
            val chest =
                placeMapLoc(
                    chestTile,
                    MiningGuildLocs.bank_chest,
                    LocShape.CentrepieceStraight,
                    LocAngle.North,
                )
            fun reaches(from: CoordGrid): Boolean {
                player.placeAt(from)
                return deps.validator.touches(player.avatar, chest)
            }

            assertTrue(reaches(chestTile.translateX(1)), "east is the chest's open side")
            assertFalse(reaches(chestTile.translateZ(1)))
            assertFalse(reaches(chestTile.translateZ(-1)))
            assertFalse(reaches(chestTile.translateX(-1)))
        }
}
