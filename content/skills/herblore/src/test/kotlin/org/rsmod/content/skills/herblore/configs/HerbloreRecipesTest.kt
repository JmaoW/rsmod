package org.rsmod.content.skills.herblore.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.type.obj.ObjType

class HerbloreRecipesTest {
    private val o = HerbloreObjs

    private fun ObjType.name() = internalNameValue

    private fun recipe(product: ObjType, vararg inputs: ObjType): HerbloreRecipe =
        HerbloreRecipes.all.first { recipe ->
            recipe.product.name() == product.name() &&
                inputs.all { input -> recipe.inputs.any { it.obj.name() == input.name() } }
        }

    @Test
    fun `herbs match the wiki`() {
        val expected =
            listOf(
                Triple(Herbs.guam, 3, 2.5),
                Triple(Herbs.marrentill, 5, 3.8),
                Triple(Herbs.tarromin, 11, 5.0),
                Triple(Herbs.harralander, 20, 6.3),
                Triple(Herbs.ranarr, 25, 7.5),
                Triple(Herbs.toadflax, 30, 8.0),
                Triple(Herbs.irit, 40, 8.8),
                Triple(Herbs.avantoe, 48, 10.0),
                Triple(Herbs.kwuarm, 54, 11.3),
                Triple(Herbs.huasca, 58, 11.8),
                Triple(Herbs.snapdragon, 59, 11.8),
                Triple(Herbs.cadantine, 65, 12.5),
                Triple(Herbs.lantadyme, 67, 13.1),
                Triple(Herbs.dwarf_weed, 70, 13.8),
                Triple(Herbs.torstol, 75, 15.0),
            )
        for ((herb, level, xp) in expected) {
            assertEquals(level, herb.cleanLevel, herb.name)
            assertEquals(xp, herb.cleanXp, herb.name)
        }
        assertEquals(expected.size, Herbs.all.size)
    }

    @Test
    fun `unfinished potions need the wiki level and give no experience`() {
        val levels = listOf(3, 5, 12, 22, 30, 34, 45, 50, 55, 58, 63, 66, 69, 72, 78)
        assertEquals(levels, Herbs.all.map { it.unfinishedLevel })
        for (herb in Herbs.all) {
            val made = recipe(herb.unfinished, o.vial_water, herb.clean)
            assertEquals(herb.unfinishedLevel, made.level)
            assertEquals(0.0, made.xp)
        }
    }

    @Test
    fun `potions match the wiki`() {
        val expected =
            listOf(
                Triple(Potions.attack, 3, 25.0),
                Triple(Potions.antipoison, 5, 37.5),
                Triple(Potions.strength, 12, 50.0),
                Triple(Potions.restore, 22, 62.5),
                Triple(Potions.energy, 26, 67.5),
                Triple(Potions.defence, 30, 75.0),
                Triple(Potions.agility, 34, 80.0),
                Triple(Potions.combat, 36, 84.0),
                Triple(Potions.prayer, 38, 87.5),
                Triple(Potions.super_attack, 45, 100.0),
                Triple(Potions.super_antipoison, 48, 106.3),
                Triple(Potions.fishing, 50, 112.5),
                Triple(Potions.super_energy, 52, 117.5),
                Triple(Potions.hunter, 53, 120.0),
                Triple(Potions.goading, 54, 132.0),
                Triple(Potions.super_strength, 55, 125.0),
                Triple(Potions.prayer_regeneration, 58, 132.0),
                Triple(Potions.super_restore, 63, 142.5),
                Triple(Potions.super_defence, 66, 150.0),
                Triple(Potions.antifire, 69, 157.5),
                Triple(Potions.ranging, 72, 162.5),
                Triple(Potions.magic, 76, 172.5),
                Triple(Potions.zamorak_brew, 78, 175.0),
                Triple(Potions.saradomin_brew, 81, 180.0),
            )
        for ((potion, level, xp) in expected) {
            val made = recipe(potion.dose(3))
            assertEquals(level, made.level, potion.name)
            assertEquals(xp, made.xp, potion.name)
            assertEquals(HerbloreMethod.Potion, made.method)
            assertTrue(made.potion === potion)
        }
    }

    @Test
    fun `a potion is made with three doses and the bonus makes it four`() {
        val made = recipe(Potions.attack.dose(3))
        assertEquals(Potions.attack.dose(3).name(), made.productWith(0).name())
        assertEquals(Potions.attack.dose(4).name(), made.productWith(1).name())
        // Never more than four doses.
        assertEquals(Potions.attack.dose(4).name(), made.productWith(5).name())
    }

    @Test
    fun `the upgrade recipes go up with the doses`() {
        for (doses in 1..4) {
            val stamina = recipe(Potions.stamina.dose(doses))
            assertEquals(77, stamina.level)
            assertEquals(25.5 * doses, stamina.xp)
            assertEquals(doses, stamina.inputs.first { it.obj.name() == o.amylase.name() }.count)
            assertEquals(Potions.super_energy.dose(doses).name(), stamina.inputs.first().obj.name())

            val antifire = recipe(Potions.extended_antifire.dose(doses))
            assertEquals(84, antifire.level)
            assertEquals(27.5 * doses, antifire.xp)

            val combat = recipe(Potions.super_combat.dose(doses))
            assertEquals(90, combat.level)
            assertEquals(150.0, combat.xp)
            assertEquals(4, combat.inputs.size)

            val divine = recipe(Potions.divine_super_attack.dose(doses))
            assertEquals(70, divine.level)
            assertEquals(
                doses,
                divine.inputs.first { it.obj.name() == o.crystal_dust.name() }.count,
            )
        }
        assertEquals(97, recipe(Potions.divine_super_combat.dose(4)).level)
        assertEquals(74, recipe(Potions.divine_ranging.dose(4)).level)
        assertEquals(78, recipe(Potions.divine_magic.dose(4)).level)
    }

    @Test
    fun `super antifire is an antifire potion and crushed superior dragon bones`() {
        val made = recipe(Potions.super_antifire.dose(4))
        assertEquals(92, made.level)
        assertEquals(130.0, made.xp)
        assertEquals(
            listOf(Potions.antifire.dose(4).name(), o.crushed_superior_dragon_bones.name()),
            made.inputs.map { it.obj.name() },
        )
    }

    @Test
    fun `extended super antifire needs a lava shard a dose`() {
        for (doses in 1..4) {
            val made = recipe(Potions.extended_super_antifire.dose(doses))
            assertEquals(98, made.level)
            assertEquals(40.0 * doses, made.xp)
            assertEquals(doses, made.inputs.first { it.obj.name() == o.lava_shard.name() }.count)
            assertEquals(Potions.super_antifire.dose(doses).name(), made.inputs.first().obj.name())
        }
    }

    @Test
    fun `every pair of objs offers its recipes in either order`() {
        val pairs = HerbloreRecipes.triggers()
        val keys = pairs.map { listOf(it.first.name(), it.second.name()).sorted() }
        assertEquals(keys.size, keys.toSet().size, "A pair must only be listed once.")

        val offered = HerbloreRecipes.offered(o.vial_water, Herbs.guam.clean)
        assertEquals(listOf(Herbs.guam.unfinished.name()), offered.map { it.product.name() })
        val reversed = HerbloreRecipes.offered(Herbs.guam.clean, o.vial_water)
        assertEquals(offered.map { it.product.name() }, reversed.map { it.product.name() })

        val combat = HerbloreRecipes.offered(o.torstol, Potions.super_attack.dose(4))
        assertEquals(listOf(Potions.super_combat.dose(4).name()), combat.map { it.product.name() })
    }

    @Test
    fun `grinding is started with the tool on the ingredient`() {
        val grind = recipe(o.unicorn_horn_dust, o.unicorn_horn)
        assertEquals(HerbloreMethod.Grind, grind.method)
        assertEquals(1, grind.level)
        assertEquals(0.0, grind.xp)
        assertEquals(1, HerbloreRecipes.offered(o.pestle_and_mortar, o.unicorn_horn).size)

        val shards = recipe(o.lava_shard, o.lava_scale)
        assertTrue(shards.outputMax > shards.output)
    }

    @Test
    fun `every potion has four doses and a unique obj for each`() {
        val names = HashSet<String>()
        for (potion in Potions.all) {
            assertEquals(4, potion.doses.size, potion.name)
            for (doses in 1..4) {
                assertTrue(names.add(potion.dose(doses).name()), "Duplicate obj in ${potion.name}")
            }
        }
    }
}
