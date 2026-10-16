package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.magic.utility.configs.SuperheatRecipe
import org.rsmod.content.skills.magic.utility.configs.SuperheatRecipes
import org.rsmod.content.skills.magic.utility.configs.utility_seqs
import org.rsmod.content.skills.magic.utility.configs.utility_spotanims
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Superheat Item smelts the ore it is cast on, with the coal and second ore from the inventory,
// into a bar. Iron ore becomes steel when there are two coal to go with it. It gives Smithing
// experience like a furnace and never fails. Noted ore cannot be superheated. It takes 3 ticks.
class Superheat
@Inject
constructor(
    private val caster: SpellCaster,
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        with(caster) { onItemSpell(objs.spell_superheat) { superheat(it) } }
    }

    private fun ProtectedAccess.superheat(target: SpellTarget) {
        val recipes = SuperheatRecipes.using(target.obj)
        if (recipes.isEmpty()) {
            mes("You can only cast superheat item on ore.")
            return
        }

        val recipe = recipes.firstOrNull { hasIngredients(it) }
        if (recipe == null) {
            mes("You do not have the ores you need to make a bar.")
            return
        }
        if (player.smithingLvl < recipe.level) {
            val bar = objTypes[recipe.bar].name.lowercase()
            mes("You need a Smithing level of ${recipe.level} to make a $bar.")
            return
        }

        with(caster) { cast(objs.spell_superheat, COOLDOWN) } ?: return
        for (ingredient in recipe.ingredients) {
            invDel(inv, ingredient.ore, ingredient.count)
        }
        invAdd(inv, recipe.bar, 1)
        statAdvance(stats.smithing, recipe.xp * xpMods.get(player, stats.smithing))

        anim(utility_seqs.cast_superheat)
        spotanim(utility_spotanims.superheat, height = SPOTANIM_HEIGHT)
        soundSynth(synths.superheat_all)
        publish(Superheated(player, recipe))
    }

    private fun ProtectedAccess.hasIngredients(recipe: SuperheatRecipe): Boolean =
        recipe.ingredients.all { invTotal(inv, it.ore) >= it.count }

    /** Published once a bar has been made by the spell. */
    class Superheated(val player: Player, val recipe: SuperheatRecipe) : UnboundEvent

    private companion object {
        const val COOLDOWN = 3
        const val SPOTANIM_HEIGHT = 92
    }
}
