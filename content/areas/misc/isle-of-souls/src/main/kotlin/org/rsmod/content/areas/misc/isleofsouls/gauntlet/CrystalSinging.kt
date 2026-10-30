package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object CrystalLocs : LocReferences() {
    /** Prifddinas's singing bowl ("Sing-crystal"); the isle has a copy of it. */
    val bowl = find("prif_singing_bowl")
}

object CrystalObjs : ObjReferences() {
    val shard = find("prif_crystal_shard")
    val armour_seed = find("prif_armour_seed")
    val weapon_seed = find("crystal_seed_old")
    val enhanced_seed = find("prif_weapon_seed_enhanced")
    val tool_seed = find("prif_tool_seed")

    val helmet = find("crystal_helmet")
    val chestplate = find("crystal_chestplate")
    val platelegs = find("crystal_platelegs")
    val bow = find("crystal_bow")
    val halberd = find("crystal_halberd")
    val shield = find("crystal_shield")
    val axe = find("crystal_axe")
    val pickaxe = find("crystal_pickaxe")
    val harpoon = find("crystal_harpoon")
    val blade_of_saeldor = find("blade_of_saeldor")
    val bow_of_faerdhinen = find("bow_of_faerdhinen")

    val dragon_axe = find("dragon_axe")
    val dragon_pickaxe = find("dragon_pickaxe")
    val dragon_harpoon = find("dragon_harpoon")
}

/**
 * A thing the singing bowl in Prifddinas makes (wiki, Singing bowl). [level] is both the Crafting
 * and the Smithing level it needs (boosts count) and [xp] what each skill gets.
 *
 * @param key the item that decides whether the recipe is on offer at all (the seed).
 */
class CrystalRecipe(
    val product: ObjType,
    val level: Int,
    val xp: Double,
    val key: ObjType,
    val inputs: List<Pair<ObjType, Int>>,
    val armour: Boolean = false,
)

/**
 * The crystal recipes the isle supports: the armour, the crystal bow, halberd and shield, the
 * crystal tools, and the two enhanced weapons. Their seeds come from the Gauntlet's chest (the tool
 * seed is Zalcano's drop in the real game, which the isle does not have). Not built: charging, the
 * degrading of the weapons and armour, or the crystal key and teleport crystal recipes.
 */
object CrystalRecipes {
    val all: List<CrystalRecipe> by lazy {
        val shard = CrystalObjs.shard
        val armourSeed = CrystalObjs.armour_seed
        val weaponSeed = CrystalObjs.weapon_seed
        listOf(
            CrystalRecipe(
                CrystalObjs.helmet,
                70,
                2_500.0,
                armourSeed,
                listOf(armourSeed to 1, shard to 50),
                true,
            ),
            CrystalRecipe(
                CrystalObjs.platelegs,
                72,
                5_000.0,
                armourSeed,
                listOf(armourSeed to 2, shard to 100),
                true,
            ),
            CrystalRecipe(
                CrystalObjs.chestplate,
                74,
                7_500.0,
                armourSeed,
                listOf(armourSeed to 3, shard to 150),
                true,
            ),
            CrystalRecipe(
                CrystalObjs.bow,
                78,
                2_000.0,
                weaponSeed,
                listOf(weaponSeed to 1, shard to 40),
            ),
            CrystalRecipe(
                CrystalObjs.halberd,
                78,
                2_000.0,
                weaponSeed,
                listOf(weaponSeed to 1, shard to 40),
            ),
            CrystalRecipe(
                CrystalObjs.shield,
                78,
                2_000.0,
                weaponSeed,
                listOf(weaponSeed to 1, shard to 40),
            ),
            CrystalRecipe(
                CrystalObjs.axe,
                76,
                6_000.0,
                CrystalObjs.tool_seed,
                listOf(CrystalObjs.dragon_axe to 1, CrystalObjs.tool_seed to 1, shard to 120),
            ),
            CrystalRecipe(
                CrystalObjs.pickaxe,
                76,
                6_000.0,
                CrystalObjs.tool_seed,
                listOf(CrystalObjs.dragon_pickaxe to 1, CrystalObjs.tool_seed to 1, shard to 120),
            ),
            CrystalRecipe(
                CrystalObjs.harpoon,
                76,
                6_000.0,
                CrystalObjs.tool_seed,
                listOf(CrystalObjs.dragon_harpoon to 1, CrystalObjs.tool_seed to 1, shard to 120),
            ),
            CrystalRecipe(
                CrystalObjs.blade_of_saeldor,
                82,
                5_000.0,
                CrystalObjs.enhanced_seed,
                listOf(CrystalObjs.enhanced_seed to 1, shard to 100),
            ),
            CrystalRecipe(
                CrystalObjs.bow_of_faerdhinen,
                82,
                5_000.0,
                CrystalObjs.enhanced_seed,
                listOf(CrystalObjs.enhanced_seed to 1, shard to 100),
            ),
        )
    }
}

/**
 * What singing does: needs the recipe's Crafting and Smithing levels, takes the seeds, tools and
 * shards, gives the product and the experience in both skills.
 */
class CrystalSinging
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
    private val tracker: LeagueTaskTracker,
) {
    /** The recipes the player holds the seed (and any tool to improve) for. */
    fun ProtectedAccess.offered(): List<CrystalRecipe> =
        CrystalRecipes.all.filter { available(it) }.take(MAX_OPTIONS)

    /** Whether the player holds what the recipe is built on (the seed, and the tool to improve). */
    private fun ProtectedAccess.available(recipe: CrystalRecipe): Boolean =
        recipe.inputs
            .filter { it.first.id != CrystalObjs.shard.id }
            .all { (obj, count) -> held(obj) >= count }

    private fun ProtectedAccess.held(obj: ObjType): Int = inv.count(objTypes[obj])

    /** Sings [recipe] once. Returns `true` if it was made. */
    fun ProtectedAccess.make(recipe: CrystalRecipe): Boolean {
        if (player.craftingLvl < recipe.level || player.smithingLvl < recipe.level) {
            mes("You need level ${recipe.level} Crafting and Smithing to sing that.")
            return false
        }
        val short = recipe.inputs.firstOrNull { (obj, count) -> held(obj) < count }
        if (short != null) {
            mes("You do not have the materials required to sing that.")
            return false
        }
        val freed =
            recipe.inputs.sumOf { (obj, count) -> if (objTypes[obj].stackable) 0 else count }
        if (inv.freeSpace() + freed < 1) {
            mes("You don't have enough inventory space.")
            return false
        }
        for ((obj, count) in recipe.inputs) {
            invDel(inv, obj, count)
        }
        invAdd(inv, recipe.product)
        for (stat in listOf(stats.crafting, stats.smithing)) {
            statAdvance(stat, recipe.xp * xpMods.get(player, stat))
        }
        mes("You sing the crystal into shape.")
        if (recipe.armour) {
            tracker.complete(player, TASK_CRAFT_ARMOUR)
        }
        return true
    }

    companion object {
        /** The chat menu holds ten products. */
        const val MAX_OPTIONS = 10

        const val TASK_CRAFT_ARMOUR = 530
    }
}

/**
 * The singing bowl: a copy of Prifddinas's stands in front of the Gauntlet's portal. "Sing-crystal"
 * offers what the player holds the seed for (see [CrystalSinging]).
 */
class CrystalSingingScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val singing: CrystalSinging,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { placeBowl() }
        onOpLoc1(CrystalLocs.bowl) { sing() }
    }

    private fun placeBowl() {
        locRepo.add(
            BOWL,
            locTypes[CrystalLocs.bowl],
            Int.MAX_VALUE,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
    }

    private suspend fun ProtectedAccess.sing() {
        val offered = with(singing) { offered() }
        if (offered.isEmpty()) {
            mes("You have no crystal seeds to sing. Seeds come from the Gauntlet's reward chest.")
            return
        }
        val choice =
            skillMulti(
                title = "What would you like to make?",
                options = offered.map { it.product },
                maxQuantity = 1,
            )
        with(singing) { make(offered[choice.index]) }
    }

    companion object {
        /** In front of the portal on the isle, beside the way down. */
        val BOWL = CoordGrid(2127, 2888, 0)
    }
}

/** The league's "Equip ..." tasks for crystal gear. */
class CrystalGearTasks @Inject constructor(private val tracker: LeagueTaskTracker) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onWearposChange { check(player) }
        onPlayerLogin { check(player) }
    }

    private fun Player.wears(obj: ObjType): Boolean = worn.objs.any { it != null && it.isType(obj) }

    private fun check(player: Player) {
        val helmet = player.wears(CrystalObjs.helmet)
        val body = player.wears(CrystalObjs.chestplate)
        val legs = player.wears(CrystalObjs.platelegs)
        if (helmet || body || legs) tracker.complete(player, TASK_ANY_ARMOUR)
        if (helmet && body && legs) tracker.complete(player, TASK_FULL_SET)
        if (player.wears(CrystalObjs.bow)) tracker.complete(player, TASK_BOW)
        if (player.wears(CrystalObjs.shield)) tracker.complete(player, TASK_SHIELD)
        if (
            player.wears(CrystalObjs.blade_of_saeldor) ||
                player.wears(CrystalObjs.bow_of_faerdhinen)
        ) {
            tracker.complete(player, TASK_ENHANCED)
        }
    }

    companion object {
        const val TASK_FULL_SET = 531
        const val TASK_BOW = 546
        const val TASK_SHIELD = 549
        const val TASK_ANY_ARMOUR = 1512
        const val TASK_ENHANCED = 1396
    }
}
