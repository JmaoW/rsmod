package org.rsmod.content.skills.magic.utility

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.IfOverlayButtonT
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
import org.rsmod.content.skills.magic.utility.scripts.LunarConversions
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class LunarSpellsTest {
    class Deps
    @Inject
    constructor(val spells: MagicSpellRegistry, val random: GameTestScope.VariableGameRandom)

    object SpellModule : AbstractModule() {
        override fun configure() {
            install(MagicSpellModule())
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

    private fun GameTestState.runLunar(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, SpellModule, LunarConversions::class, *RUNE_SCRIPTS) { deps
            ->
            body(deps)
        }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(magic: Int, vararg items: Pair<String, Int>) {
        player.teleport(CoordGrid(0, 50, 50, 34, 31))
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            val type = obj(name)
            if (type.isStackable) {
                player.inv[slot++] = InvObj(type, count)
            } else {
                repeat(count) { player.inv[slot++] = InvObj(type) }
            }
        }
        player.stats[stats.magic] = magic
        player.statMap.setXP(stats.magic, 0)
        // The Lunar spells can only be cast from the Lunar spellbook.
        player.withProtectedAccess { vars[varbits.spellbook] = Spellbook.Lunars.varValue }
    }

    private fun GameTestScope.cast(deps: Deps, spell: ObjType, message: String? = null) {
        val button = deps.spells.getObjSpell(spell)!!.component
        eventBus.publish(IfOverlayButton(player, button, -1, null, IfButtonOp.Op1))
        settle(message)
    }

    private fun GameTestScope.castOn(
        deps: Deps,
        spell: ObjType,
        item: String,
        message: String? = null,
    ) {
        val component = Component(deps.spells.getObjSpell(spell)!!.component.packed)
        val inv = Component(components.inv_items.packed)
        val slot = player.inv.indexOfFirst { it?.id == obj(item).id }
        check(slot >= 0) { "Expected $item in the inventory." }
        eventBus.publish(IfOverlayButtonT(player, -1, null, slot, obj(item), component, inv))
        settle(message)
    }

    private fun GameTestScope.castOnSlot(
        deps: Deps,
        spell: ObjType,
        slot: Int,
        message: String? = null,
    ) {
        val component = Component(deps.spells.getObjSpell(spell)!!.component.packed)
        val inv = Component(components.inv_items.packed)
        val held = objTypes[player.inv[slot]!!]
        eventBus.publish(IfOverlayButtonT(player, -1, null, slot, held, component, inv))
        settle(message)
    }

    /** Button events run their script at once, so a message is looked for before any tick. */
    private fun GameTestScope.settle(message: String?) {
        repeat(4) {
            if (message != null && runCatching { assertMessageSent(message) }.isSuccess) {
                return
            }
            advance()
        }
        if (message != null) {
            assertMessageSent(message)
        }
    }

    private fun GameTestScope.magicXp(): Double = player.statMap.getFineXP(stats.magic) / 10.0

    private fun GameTestScope.xp(stat: org.rsmod.game.type.stat.StatType): Double =
        player.statMap.getFineXP(stat) / 10.0

    @Test
    fun GameTestState.`bake pie bakes every pie it has runes for`() = runLunar { deps ->
        prepare(
            65,
            "uncooked_redberry_pie" to 3,
            "firerune" to 10,
            "waterrune" to 8,
            "astralrune" to 2,
        )
        player.stats[stats.cooking] = 10
        player.statMap.setXP(stats.cooking, 0)

        cast(deps, objs.spell_bake_pie)

        check(player.count(obj("redberry_pie")) == 2) { "Runes for two pies means two pies." }
        check(player.count(obj("uncooked_redberry_pie")) == 1)
        check(magicXp() == 120.0) { "Expected 120 magic experience, was ${magicXp()}." }
        check(xp(stats.cooking) == 156.0) { "Expected 156 cooking experience." }
    }

    @Test
    fun GameTestState.`bake pie needs the cooking level of the pie`() = runLunar { deps ->
        prepare(65, "uncooked_meat_pie" to 1, "firerune" to 5, "waterrune" to 4, "astralrune" to 1)
        player.stats[stats.cooking] = 19

        cast(deps, objs.spell_bake_pie, "You need a Cooking level of 20 to bake that.")

        check(player.count(obj("uncooked_meat_pie")) == 1)
        check(player.count(obj("firerune")) == 5) { "The runes should be kept." }
    }

    @Test
    fun GameTestState.`plank make saws a log for coins`() = runLunar { deps ->
        prepare(
            86,
            "oak_logs" to 2,
            "coins" to 175,
            "earthrune" to 15,
            "astralrune" to 2,
            "naturerune" to 1,
        )

        castOn(deps, objs.spell_plank_make, "oak_logs")

        check(player.count(obj("plank_oak")) == 1) { "Expected an oak plank." }
        check(player.count(obj("oak_logs")) == 1)
        check(player.count(obj("coins")) == 0) { "Expected 175 coins to be taken." }
        check(magicXp() == 90.0)
    }

    @Test
    fun GameTestState.`plank make works on noted logs and needs the coins`() = runLunar { deps ->
        val noted = objTypes.cert(obj("logs"))
        prepare(86, "coins" to 69, "earthrune" to 15, "astralrune" to 2, "naturerune" to 1)
        player.inv[10] = InvObj(noted, 5)

        castOnSlot(deps, objs.spell_plank_make, 10, "You need 70 coins to do that.")
        check(player.inv[10]?.count == 5)

        player.inv[0] = InvObj(obj("coins"), 70)
        repeat(4) { advance() }
        castOnSlot(deps, objs.spell_plank_make, 10)
        check(player.count(obj("woodplank")) == 1)
        check(player.inv[10]?.count == 4) { "One note should be used." }
    }

    @Test
    fun GameTestState.`string jewellery strings every amulet`() = runLunar { deps ->
        prepare(
            80,
            "unstrung_sapphire_amulet" to 2,
            "astralrune" to 4,
            "waterrune" to 10,
            "earthrune" to 20,
        )
        player.stats[stats.crafting] = 1
        player.statMap.setXP(stats.crafting, 0)

        cast(deps, objs.spell_string_jewelry)

        check(player.count(obj("strung_sapphire_amulet")) == 2)
        check(xp(stats.crafting) == 8.0) { "Expected 8 crafting experience." }
        check(magicXp() == 166.0) { "Expected 166 magic experience, was ${magicXp()}." }
    }

    @Test
    fun GameTestState.`superglass turns sand and seaweed into molten glass`() = runLunar { deps ->
        prepare(
            77,
            "bucket_sand" to 4,
            "seaweed" to 2,
            "soda_ash" to 1,
            "airrune" to 10,
            "firerune" to 6,
            "astralrune" to 2,
        )
        // No extra glass: every roll is too high.
        repeat(10) { deps.random.next = 99 }
        player.stats[stats.crafting] = 1
        player.statMap.setXP(stats.crafting, 0)

        cast(deps, objs.spell_superglass)

        check(player.count(obj("molten_glass")) == 3) { "Three pairs make three glass." }
        check(player.count(obj("bucket_sand")) == 1)
        check(player.count(obj("seaweed")) == 0 && player.count(obj("soda_ash")) == 0)
        check(xp(stats.crafting) == 30.0) { "Expected 30 crafting experience." }
        check(magicXp() == 78.0)
    }

    @Test
    fun GameTestState.`tan leather tans five hides`() = runLunar { deps ->
        prepare(78, "cow_hide" to 7, "firerune" to 5, "astralrune" to 2, "naturerune" to 1)

        cast(deps, objs.spell_tan_leather)

        check(player.count(obj("leather")) == 5)
        check(player.count(obj("cow_hide")) == 2)
        check(magicXp() == 81.0)
    }

    @Test
    fun GameTestState.`spin flax spins five flax`() = runLunar { deps ->
        prepare(76, "flax" to 8, "airrune" to 5, "astralrune" to 1, "naturerune" to 2)
        player.stats[stats.crafting] = 1
        player.statMap.setXP(stats.crafting, 0)

        cast(deps, objs.spell_spin_flax)

        check(player.count(obj("bow_string")) == 5)
        check(player.count(obj("flax")) == 3)
        check(xp(stats.crafting) == 75.0) { "Expected 75 crafting experience." }
    }

    @Test
    fun GameTestState.`humidify fills every empty container`() = runLunar { deps ->
        prepare(
            68,
            "vial_empty" to 2,
            "bucket_empty" to 1,
            "clay" to 3,
            "astralrune" to 1,
            "firerune" to 1,
            "waterrune" to 3,
        )

        cast(deps, objs.spell_humidify)

        check(player.count(obj("vial_water")) == 2)
        check(player.count(obj("bucket_water")) == 1)
        check(player.count(obj("softclay")) == 3)
        check(magicXp() == 65.0)
    }

    @Test
    fun GameTestState.`recharge dragonstone fills jewellery to four charges`() = runLunar { deps ->
        prepare(89, "amulet_of_glory_1" to 1, "waterrune" to 4, "astralrune" to 1, "soulrune" to 1)

        castOn(deps, objs.spell_recharge_dragonstone, "amulet_of_glory_1")

        check(player.count(obj("amulet_of_glory_4")) == 1)
        check(player.count(obj("amulet_of_glory_1")) == 0)
    }

    @Test
    fun GameTestState.`recharge dragonstone refuses full jewellery`() = runLunar { deps ->
        prepare(89, "amulet_of_glory_4" to 1, "waterrune" to 4, "astralrune" to 1, "soulrune" to 1)

        castOn(
            deps,
            objs.spell_recharge_dragonstone,
            "amulet_of_glory_4",
            "That is already fully charged.",
        )

        check(player.count(obj("waterrune")) == 4) { "The runes should be kept." }
    }
}
