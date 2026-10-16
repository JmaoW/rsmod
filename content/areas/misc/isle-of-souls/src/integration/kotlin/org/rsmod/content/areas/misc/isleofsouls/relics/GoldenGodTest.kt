package org.rsmod.content.areas.misc.isleofsouls.relics

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.LocUOpScript
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.shops.ShopNoting
import org.rsmod.api.shops.ShopScript
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.config.ShopComponents
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.runes.combo.ComboRuneRepository
import org.rsmod.api.spells.runes.combo.ComboRuneScript
import org.rsmod.api.spells.runes.compact.CompactRuneRepository
import org.rsmod.api.spells.runes.compact.CompactRuneScript
import org.rsmod.api.spells.runes.fake.FakeRuneRepository
import org.rsmod.api.spells.runes.fake.FakeRuneScript
import org.rsmod.api.spells.runes.staves.StaffSubstituteRepository
import org.rsmod.api.spells.runes.staves.StaffSubstituteScript
import org.rsmod.api.spells.runes.subs.RuneSubstituteRepository
import org.rsmod.api.spells.runes.subs.RuneSubstituteScript
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.miningguild.MiningGuildScript
import org.rsmod.content.areas.misc.isleofsouls.miningguild.MiningGuildShopScript
import org.rsmod.content.skills.magic.utility.scripts.Alchemy
import org.rsmod.content.skills.magic.utility.scripts.AlchemyModifiers
import org.rsmod.content.skills.prayer.scripts.Altars
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.shop.Shop
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class GoldenGodTest {
    class Deps
    @Inject
    constructor(
        val golden: LeagueGoldenGod,
        val spells: MagicSpellRegistry,
        val random: GameTestScope.VariableGameRandom,
        val shops: MiningGuildShopScript,
        val objTypes: ObjTypeList,
    )

    /** The real server shares these between every script, so the tests must too. */
    private object RelicModule : AbstractModule() {
        override fun configure() {
            install(MagicSpellModule())
            bind(LeagueGoldenGod::class.java).`in`(Scopes.SINGLETON)
            bind(AlchemyModifiers::class.java).to(LeagueGoldenGod::class.java)
            bind(ShopNoting::class.java).to(LeagueGoldenGod::class.java)
            bind(ShopOperationMap::class.java).`in`(Scopes.SINGLETON)
            bind(Shops::class.java).`in`(Scopes.SINGLETON)
            for (repository in RUNE_REPOSITORIES) {
                bind(repository.java).`in`(Scopes.SINGLETON)
            }
        }
    }

    private companion object {
        val RUNE_REPOSITORIES =
            listOf(
                ComboRuneRepository::class,
                CompactRuneRepository::class,
                FakeRuneRepository::class,
                RuneSubstituteRepository::class,
                StaffSubstituteRepository::class,
                UnlimitedRuneRepository::class,
            )

        val RUNE_SCRIPTS =
            arrayOf(
                ComboRuneScript::class,
                CompactRuneScript::class,
                FakeRuneScript::class,
                RuneSubstituteScript::class,
                StaffSubstituteScript::class,
                UnlimitedRuneScript::class,
            )
    }

    private fun GameTestState.runGolden(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            RelicModule,
            Alchemy::class,
            Altars::class,
            LeagueGoldenGodScript::class,
            LocUOpScript::class,
            MiningGuildScript::class,
            MiningGuildShopScript::class,
            ShopScript::class,
            *RUNE_SCRIPTS,
        ) { deps ->
            body(deps)
        }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.choose(golden: Boolean) {
        val option = if (golden) 1 else 0
        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[LeagueGoldenGod.TIER], option)
    }

    private fun GameTestScope.fillInv(vararg items: Pair<ObjType, Int>) {
        player.teleport(CoordGrid(0, 50, 50, 34, 31))
        player.clearInv()
        for ((index, item) in items.withIndex()) {
            player.inv[index] = InvObj(item.first, item.second)
        }
        player.stats[stats.magic] = 1
        player.statMap.setXP(stats.magic, 0)
    }

    private fun GameTestScope.alch(deps: Deps, spell: ObjType, slot: Int) {
        val button = Component(deps.spells.getObjSpell(spell)!!.component.packed)
        val inv = Component(components.inv_items.packed)
        val item = player.inv[slot]!!
        eventBus.publish(IfOverlayButtonT(player, -1, null, slot, objTypes[item], button, inv))
    }

    private fun GameTestScope.coins(): Int = player.count(obj("coins"))

    private fun GameTestScope.magicXp(): Double = player.statMap.getFineXP(stats.magic) / 10.0

    @Test
    fun GameTestState.`alchemy needs no runes or level and pays fifteen percent more`() =
        runGolden { deps ->
            choose(true)
            val ring = obj("gold_ring")
            fillInv(ring to 1)
            repeat(8) { deps.random.next = 99 }

            alch(deps, objs.spell_high_alchemy, slot = 0)

            val expected = ring.cost * 60 * 115 / 10_000
            assertTrue(expected > 0)
            assertEquals(expected, coins())
            assertEquals(0, player.count(ring), "The item should be used up.")
            assertEquals(65.0, magicXp(), "Casting still gives experience.")
        }

    @Test
    fun GameTestState.`alchemy keeps the item sixty five percent of the time`() =
        runGolden { deps ->
            choose(true)
            val ring = obj("gold_ring")
            fillInv(ring to 1)
            deps.random.next = 64

            alch(deps, objs.spell_low_alchemy, slot = 0)

            assertEquals(1, player.count(ring), "A roll of 64 keeps the item.")
            assertTrue(coins() > 0, "The coins are paid either way.")

            fillInv(ring to 1)
            deps.random.next = 65
            player.clearInv()
            player.inv[0] = InvObj(ring, 1)
            // The spell is still cooling down from the first cast.
            repeat(6) { advance() }
            alch(deps, objs.spell_low_alchemy, slot = 0)
            assertEquals(0, player.count(ring), "A roll of 65 uses the item up.")
        }

    @Test
    fun GameTestState.`without the relic alchemy still needs runes and the level`() =
        runGolden { deps ->
            choose(false)
            val ring = obj("gold_ring")
            fillInv(ring to 1)

            alch(deps, objs.spell_high_alchemy, slot = 0)

            assertEquals(1, player.count(ring), "The item should be kept.")
            assertEquals(0, coins())
        }

    @Test
    fun GameTestState.`alchemy on a stack carries on by itself until the stack is gone`() =
        runGolden { deps ->
            choose(true)
            val noted = deps.objTypes.cert(obj("gold_ring"))
            fillInv(noted to 4)
            repeat(30) { deps.random.next = 99 }

            alch(deps, objs.spell_low_alchemy, slot = 0)
            assertEquals(3, player.inv[0]?.count, "The first cast uses one.")

            repeat(5) { advance() }
            assertEquals(2, player.inv[0]?.count, "It casts again five ticks later.")

            repeat(20) { advance() }
            assertEquals(null, player.inv[0], "The whole stack should have been alchemised.")
            val each = deps.objTypes.uncert(noted).cost * 40 * 115 / 10_000
            assertEquals(4 * each, coins())

            val before = coins()
            repeat(20) { advance() }
            assertEquals(before, coins(), "It should have stopped.")
        }

    @Test
    fun GameTestState.`moving the stack stops the repeat`() = runGolden { deps ->
        choose(true)
        val noted = deps.objTypes.cert(obj("gold_ring"))
        fillInv(noted to 10)
        repeat(30) { deps.random.next = 99 }

        alch(deps, objs.spell_low_alchemy, slot = 0)
        player.inv[5] = player.inv[0]
        player.inv[0] = null
        val coinsBefore = coins()

        repeat(30) { advance() }

        assertEquals(coinsBefore, coins(), "The stack is no longer where it was cast on.")
        assertEquals(9, player.inv[5]?.count)
    }

    private fun GameTestScope.altarAt(internal: String): org.rsmod.game.loc.BoundLocInfo {
        val type: UnpackedLocType = locTypes.values.first { it.internalName == internal }
        val loc = placeMapLoc(CoordGrid(0, 50, 50, 36, 31), type)
        player.teleport(loc.coords.translateX(-1))
        return loc
    }

    private fun GameTestScope.prayerXp(): Double = player.statMap.getFineXP(stats.prayer) / 10.0

    private val offering = LeagueGoldenGod.COINS_PER_OFFERING

    @Test
    fun GameTestState.`twenty thousand coins on an altar give the experience of a dragon bone`() =
        runGolden { _ ->
            choose(true)
            val coins = obj("coins")
            fillInv(coins to offering)
            player.statMap.setXP(stats.prayer, 0)
            val altar = altarAt("altar")

            player.opLocT(altar, coins, 0)
            repeat(10) { advance() }

            assertEquals(null, player.inv[0], "The coins should have been offered.")
            assertEquals(72.0 * Altars.OFFER_MULTIPLIER, prayerXp())
        }

    @Test
    fun GameTestState.`each twenty thousand is another offering and the rest is kept`() =
        runGolden { _ ->
            choose(true)
            val coins = obj("coins")
            fillInv(coins to 3 * offering + 5_000)
            player.statMap.setXP(stats.prayer, 0)
            val altar = altarAt("altar")

            player.opLocT(altar, coins, 0)
            repeat(40) { advance() }

            assertEquals(5_000, player.inv[0]?.count)
            assertEquals(3 * 72.0 * Altars.OFFER_MULTIPLIER, prayerXp())
        }

    @Test
    fun GameTestState.`fewer than twenty thousand coins are not enough`() = runGolden { _ ->
        choose(true)
        val coins = obj("coins")
        fillInv(coins to offering - 1)
        player.statMap.setXP(stats.prayer, 0)
        val altar = altarAt("altar")

        player.opLocT(altar, coins, 0)
        repeat(10) { advance() }

        assertEquals(offering - 1, player.inv[0]?.count)
        assertEquals(0.0, prayerXp())
    }

    @Test
    fun GameTestState.`coins do nothing on an altar without the relic`() = runGolden { _ ->
        choose(false)
        val coins = obj("coins")
        fillInv(coins to 2 * offering)
        val altar = altarAt("altar")

        player.opLocT(altar, coins, 0)
        repeat(10) { advance() }

        assertEquals(2 * offering, player.inv[0]?.count)
    }

    @Test
    fun GameTestState.`the chaos altar can spare the coins as it spares bones`() =
        runGolden { deps ->
            choose(true)
            val coins = obj("coins")
            fillInv(coins to offering)
            player.statMap.setXP(stats.prayer, 0)
            // Every roll comes up "spared".
            repeat(60) { deps.random.next = 0 }
            val altar = altarAt("chaosaltar")

            player.opLocT(altar, coins, 0)
            repeat(8) { advance() }

            assertEquals(offering, player.inv[0]?.count, "All the coins were spared.")
            assertTrue(
                prayerXp() >= 72.0 * Altars.OFFER_MULTIPLIER,
                "Sparing still gives experience.",
            )
        }

    @Test
    fun GameTestState.`bones are still offered one at a time`() = runGolden { _ ->
        choose(true)
        fillInv(obj("bones") to 3)
        player.statMap.setXP(stats.prayer, 0)
        val altar = altarAt("altar")

        player.opLocT(altar, obj("bones"), 0)
        repeat(30) { advance() }

        assertEquals(3 * 4.5 * Altars.OFFER_MULTIPLIER, prayerXp(), 0.5)
    }

    private fun GameTestScope.yarsul(deps: Deps): Shop {
        deps.shops.placeNpcs()
        val npc: Npc = npcRepo.findAll(MiningGuildShopScript.YARSUL).first()
        player.placeAt(npc.coords.translateZ(1))
        player.opNpc3(npc)
        advance(ticks = 3)
        return checkNotNull(player.openedShop) { "no shop opened" }
    }

    private fun GameTestScope.buy(shop: Shop, obj: ObjType, op: IfButtonOp) {
        val slot = shop.inv.indexOfFirst { it?.id == obj.id }
        check(slot != -1) { "not in stock: $obj" }
        player.ifButton(ShopComponents.shop_inv, comsub = slot + 1, obj = obj.id, op = op)
        advance(ticks = 1)
    }

    @Test
    fun GameTestState.`what the relic holder buys from the pickaxe shop comes noted`() =
        runGolden { deps ->
            choose(true)
            val shop = yarsul(deps)
            player.clearInv()
            player.inv[0] = InvObj(obj("coins"), 1_000_000)

            buy(shop, objs.bronze_pickaxe, IfButtonOp.Op3)

            val noted = deps.objTypes.cert(obj("bronze_pickaxe"))
            assertTrue(player.count(noted) > 0, "Expected noted pickaxes.")
            assertEquals(0, player.count(obj("bronze_pickaxe")), "No loose pickaxes.")
        }

    @Test
    fun GameTestState.`switching shop noting off hands over the items as they are`() =
        runGolden { deps ->
            choose(true)
            VarPlayerIntMapSetter.set(player, RelicVarBits.shop_noted_disabled, 1)
            val shop = yarsul(deps)
            player.clearInv()
            player.inv[0] = InvObj(obj("coins"), 1_000_000)

            buy(shop, objs.bronze_pickaxe, IfButtonOp.Op2)

            assertEquals(1, player.count(obj("bronze_pickaxe")))
        }

    @Test
    fun GameTestState.`nothing is noted without the relic`() = runGolden { deps ->
        choose(false)
        val shop = yarsul(deps)
        player.clearInv()
        player.inv[0] = InvObj(obj("coins"), 1_000_000)

        buy(shop, objs.bronze_pickaxe, IfButtonOp.Op2)

        assertEquals(1, player.count(obj("bronze_pickaxe")))
    }

    @Test
    fun GameTestState.`the noted items button in the shop switches shop noting off and on`() =
        runGolden { deps ->
            choose(true)
            yarsul(deps)
            assertEquals(0, player.vars[RelicVarBits.shop_noted_disabled])

            player.ifButton(
                ShopComponents.note_button,
                comsub = Shops.NOTE_BUTTON_CHILD,
                op = IfButtonOp.Op1,
            )
            advance()
            assertEquals(1, player.vars[RelicVarBits.shop_noted_disabled])

            player.ifButton(
                ShopComponents.note_button,
                comsub = Shops.NOTE_BUTTON_CHILD,
                op = IfButtonOp.Op1,
            )
            advance()
            assertEquals(0, player.vars[RelicVarBits.shop_noted_disabled])
        }

    @Test
    fun GameTestState.`the noted items button does nothing without the relic`() =
        runGolden { deps ->
            choose(false)
            yarsul(deps)

            player.ifButton(
                ShopComponents.note_button,
                comsub = Shops.NOTE_BUTTON_CHILD,
                op = IfButtonOp.Op1,
            )
            advance()

            assertEquals(0, player.vars[RelicVarBits.shop_noted_disabled])
        }
}
