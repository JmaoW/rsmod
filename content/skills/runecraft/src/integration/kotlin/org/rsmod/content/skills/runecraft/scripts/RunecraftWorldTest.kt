package org.rsmod.content.skills.runecraft.scripts

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.shops.ShopScript
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.config.ShopComponents
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.runecraft.configs.Pouches
import org.rsmod.content.skills.runecraft.configs.RuneAltars
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.interf.IfButtonOp.Op2
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class RunecraftWorldTest {
    /** The real server shares one of each of these between every script, so tests must too. */
    private object ShopModule : AbstractModule() {
        override fun configure() {
            bind(ShopOperationMap::class.java).`in`(Scopes.SINGLETON)
            bind(Shops::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(val sites: RunecraftSites, val abyss: Abyss, val spawns: RunecraftSpawns)

    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestState.runWorldTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            ShopModule,
            Abyss::class,
            RunecraftSpawns::class,
            EssenceMine::class,
            Mining::class,
            ShopScript::class,
        ) { deps ->
            body(deps)
        }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.npc(internal: String): Npc {
        val type = npcTypes.values.first { it.internalName == internal }
        val npc = Npc(type, coords)
        npcRepo.add(npc, Int.MAX_VALUE)
        player.placeAt(coords.translateZ(1))
        return npc
    }

    private fun GameTestScope.loc(internal: String): BoundLocInfo {
        val type = locTypes.values.first { it.internalName == internal }
        val loc = placeMapLoc(coords, type)
        player.placeAt(loc.coords.translateX(-1))
        return loc
    }

    private fun GameTestScope.prepare(vararg items: Pair<String, Int>) {
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            repeat(count) { player.inv[slot++] = InvObj(obj(name)) }
        }
    }

    @Test
    fun GameTestState.`the mage teleports a player into the abyss`() = runWorldTest { deps ->
        val mage = npc("rcu_zammy_mage1b")

        player.opNpc4(mage)
        advance(ticks = 3)

        check(player.coords == deps.sites.abyssArrival) {
            "Expected ${deps.sites.abyssArrival}, was ${player.coords}."
        }
    }

    @Test
    fun GameTestState.`the mage sells runes`() = runWorldTest {
        val mage = npc("rcu_zammy_mage1b")

        player.opNpc3(mage)
        advance(ticks = 3)

        val shop = checkNotNull(player.openedShop) { "The mage should open a shop." }
        val fire = obj("firerune")
        val death = obj("deathrune")
        check(shop.inv.any { it?.id == fire.id }) { "Expected fire runes for sale." }
        check(shop.inv.any { it?.id == death.id }) { "Expected death runes for sale." }
        val stock =
            listOf(
                "firerune" to 1000,
                "waterrune" to 1000,
                "airrune" to 1000,
                "earthrune" to 1000,
                "mindrune" to 1000,
                "chaosrune" to 500,
                "deathrune" to 500,
                "bloodrune" to 500,
                "pack_firerune" to 50,
                "pack_waterrune" to 50,
                "pack_airrune" to 50,
                "pack_earthrune" to 50,
                "pack_mindrune" to 35,
                "pack_chaosrune" to 35,
            )
        for ((name, count) in stock) {
            val held = shop.inv.firstOrNull { it?.id == obj(name).id }
            check(held != null) { "Expected $name for sale." }
            check(held.count == count) { "Expected $count of $name, was ${held.count}." }
        }
        check(shop.inv.count { it != null } == stock.size) { "The mage sells nothing else." }
    }

    @Test
    fun GameTestState.`aubury sells the wiki's runes and rune packs`() = runWorldTest {
        val aubury = npc("aubury_3op")

        player.opNpc3(aubury)
        advance(ticks = 3)

        val shop = checkNotNull(player.openedShop) { "Aubury should open a shop." }
        val stock =
            listOf(
                "airrune" to 5000,
                "firerune" to 5000,
                "waterrune" to 5000,
                "earthrune" to 5000,
                "mindrune" to 5000,
                "bodyrune" to 5000,
                "chaosrune" to 250,
                "deathrune" to 250,
                "pack_firerune" to 80,
                "pack_waterrune" to 80,
                "pack_airrune" to 80,
                "pack_earthrune" to 80,
                "pack_mindrune" to 40,
                "pack_chaosrune" to 35,
            )
        for ((name, count) in stock) {
            val held = shop.inv.firstOrNull { it?.id == obj(name).id }
            check(held != null) { "Expected $name for sale." }
            check(held.count == count) { "Expected $count of $name, was ${held.count}." }
        }
        check(shop.inv.count { it != null } == stock.size) { "Aubury sells nothing else." }
    }

    @Test
    fun GameTestState.`aubury sells a rune pack at its listed price`() = runWorldTest {
        val aubury = npc("aubury_3op")
        val pack = obj("pack_airrune")
        player.opNpc3(aubury)
        advance(ticks = 3)
        val shop = checkNotNull(player.openedShop) { "Aubury should open a shop." }
        val slot = shop.inv.indexOfFirst { it?.id == pack.id }

        player.clearInv()
        player.inv[0] = InvObj(objs.coins, 430)
        player.ifButton(ShopComponents.shop_inv, comsub = slot + 1, obj = pack.id, op = Op2)
        advance(ticks = 1)

        check(player.inv.count { it?.id == pack.id } == 1) { "Expected the pack to be bought." }
        check(player.inv.none { it?.id == objs.coins.id }) { "Expected 430 coins to be spent." }
    }

    @Test
    fun GameTestState.`a rift leads to its altar and the portal leads home`() =
        runWorldTest { deps ->
            val rift = loc("abyss_exit_to_air")

            player.opLoc1(rift)
            advance(ticks = 3)

            val landing = deps.sites.landing(RuneAltars.air.inside)
            check(player.coords == landing) { "Expected $landing, was ${player.coords}." }

            val portal = loc("airtemple_exit_portal")
            player.opLoc1(portal)
            advance(ticks = 3)

            check(player.coords == deps.sites.home) {
                "Expected ${deps.sites.home}, was ${player.coords}."
            }
        }

    @Test
    fun GameTestState.`the rifts the cache lacks are added for blood astral and wrath`() =
        runWorldTest { deps ->
            for ((rift, altar) in
                listOf(
                    "essencemine_portal_3" to RuneAltars.blood,
                    "essencemine_portal_4" to RuneAltars.astral,
                    "essencemine_portal_5" to RuneAltars.wrath,
                )) {
                val loc = loc(rift)
                player.opLoc1(loc)
                advance(ticks = 3)

                val landing = deps.sites.landing(altar.inside)
                check(player.coords == landing) {
                    "Expected $landing for the ${altar.name} rift, was ${player.coords}."
                }
            }
        }

    @Test
    fun GameTestState.`aubury teleports a player to the essence mine`() = runWorldTest { deps ->
        val aubury = npc("aubury_3op")

        player.opNpc4(aubury)
        advance(ticks = 3)

        check(player.coords == deps.sites.mineArrival) {
            "Expected ${deps.sites.mineArrival}, was ${player.coords}."
        }
    }

    @Test
    fun GameTestState.`the essence rock always gives rune essence and never runs out`() =
        runWorldTest {
            val rock = loc("blankrunestone")
            prepare("bronze_pickaxe" to 1)
            player.stats[stats.mining] = 1
            player.statMap.setXP(stats.mining, 0)

            player.opLoc1(rock)
            advanceUntil({ player.count(obj("blankrune")) >= 3 }, timeoutTicks = 80)

            check(player.count(obj("blankrune_high")) == 0) { "Level 1 should mine rune essence." }
            check(player.statMap.getFineXP(stats.mining) >= 150) { "Expected 5 experience each." }
        }

    @Test
    fun GameTestState.`mining level 30 gives pure essence`() = runWorldTest {
        val rock = loc("blankrunestone")
        prepare("bronze_pickaxe" to 1)
        player.stats[stats.mining] = 30

        player.opLoc1(rock)
        advanceUntil({ player.count(obj("blankrune_high")) >= 2 }, timeoutTicks = 80)

        check(player.count(obj("blankrune")) == 0) { "Level 30 should mine pure essence." }
    }

    @Test
    fun GameTestState.`the dark mage hands out the pouches a player has the level for`() =
        runWorldTest { deps ->
            prepare()
            player.stats[stats.runecrafting] = 50
            player.stats[stats.crafting] = 99

            with(deps.abyss) { player.withProtectedAccess { givePouches() } }
            advance()

            check(player.count(obj("rcu_pouch_small")) == 1) { "Expected a small pouch." }
            check(player.count(obj("rcu_pouch_medium")) == 1) { "Expected a medium pouch." }
            check(player.count(obj("rcu_pouch_large")) == 1) { "Expected a large pouch." }
            check(player.count(obj("rcu_pouch_giant")) == 0) { "Level 50 is too low for giant." }

            with(deps.abyss) { player.withProtectedAccess { givePouches() } }
            advance()
            check(player.count(obj("rcu_pouch_small")) == 1) { "No duplicate pouches." }
            check(Pouches.all.size == 5) { "Five pouches." }
        }

    @Test
    fun GameTestState.`the spawner places the mages and the portals`() = runWorldTest { deps ->
        deps.spawns.spawn()
        val sites = deps.sites

        for (at in listOf(sites.home, sites.aubury, sites.darkMage)) {
            check(npcRepo.findAll(at).any()) { "Expected an npc at $at." }
        }
        check(sites.home != sites.aubury) { "The mages should stand apart." }
        check(sites.abyssArrival != sites.darkMage) { "A player must not land on the Dark Mage." }
        check(sites.mineArrival != sites.mineExit) {
            "The mine exit must not be where players land."
        }
        java.io
            .File(System.getProperty("user.dir"), "rc-sites.txt")
            .writeText(
                "home=${sites.home} aubury=${sites.aubury} dark=${sites.darkMage} " +
                    "arrival=${sites.abyssArrival} mine=${sites.mineArrival} exit=${sites.mineExit} " +
                    "astralExit=${sites.astralExit} blood=${sites.bloodRift} astral=${sites.astralRift} " +
                    "wrath=${sites.wrathRift} landings=" +
                    RuneAltars.all.joinToString { it.name + ":" + sites.landing(it.inside) }
            )
    }
}
