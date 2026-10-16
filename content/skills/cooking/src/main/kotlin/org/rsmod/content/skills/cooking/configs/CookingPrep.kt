package org.rsmod.content.skills.cooking.configs

import org.rsmod.api.type.refs.queue.QueueReferences
import org.rsmod.api.type.refs.timer.TimerReferences

internal object CookingQueues : QueueReferences() {
    /** Fires when the next item of a preparation job is ready to be made. */
    val prepare = find("cooking_prepare")
}

internal object CookingTimers : TimerReferences() {
    /** Ticks every cycle for a player who has unfermented wine to wait for. */
    val wine = find("cooking_wine")
}

/**
 * An item by the name the cache gives it. When several objs share a name, `#2` is the second of
 * them by id and so on, which tells the stages of a part-made pie apart.
 */
internal class PrepItem(val name: String, val count: Int = 1)

/** Something made by using items on each other: dough, a pie shell, a pizza, a bowl of stew. */
internal class PrepRecipe(
    /** Used up. */
    val inputs: List<PrepItem>,
    /** The product first, then whatever the cook gets back (a pot, a bucket). */
    val outputs: List<PrepItem>,
    val level: Int,
    val xp: Double,
    /** Needed but not used up, such as a knife. */
    val tools: List<String> = emptyList(),
    /** `true` for unfermented wine, which turns into wine a little while later. */
    val ferments: Boolean = false,
) {
    val product: PrepItem
        get() = outputs.first()
}

/**
 * The recipes of the wiki's Cooking page that need no more than items: the doughs, pies, pizzas,
 * cakes, stews, curries, potatoes with toppings and the bowl dishes that go with them, and wine.
 * Making butter, cheese and flour needs a dairy churn or a windmill, which the isle does not have.
 */
internal object PrepRecipes {
    private val waters =
        listOf("Bucket of water" to "Bucket", "Bowl of water" to "Bowl", "Jug of water" to "Jug")

    private fun item(text: String): PrepItem {
        val count = text.substringBefore("x ", "").toIntOrNull()
        return if (count != null) PrepItem(text.substringAfter("x "), count) else PrepItem(text)
    }

    private fun recipe(
        product: String,
        level: Int,
        xp: Double,
        vararg inputs: String,
        returns: List<String> = emptyList(),
        tools: List<String> = emptyList(),
        ferments: Boolean = false,
    ) =
        PrepRecipe(
            inputs = inputs.map(::item),
            outputs = listOf(item(product)) + returns.map(::item),
            level = level,
            xp = xp,
            tools = tools,
            ferments = ferments,
        )

    /** A pie that is built in steps on a pie shell: each step uses the item and the last stage. */
    private fun pie(level: Int, vararg steps: Pair<String, String>): List<PrepRecipe> {
        var last = "Pie shell"
        return steps.map { (ingredient, made) ->
            recipe(made, level, 0.0, last, ingredient).also { last = made }
        }
    }

    private val doughs =
        waters.flatMap { (water, container) ->
            listOf("Bread dough" to 1, "Pastry dough" to 1, "Pizza base" to 35).map { (dough, level)
                ->
                recipe(dough, level, 0.0, "Pot of flour", water, returns = listOf("Pot", container))
            }
        }

    private val pies =
        listOf(
            recipe("Pie shell", 1, 0.0, "Pastry dough", "Pie dish"),
            recipe("Uncooked berry pie", 10, 0.0, "Redberries", "Pie shell"),
            recipe("Uncooked meat pie", 20, 0.0, "Cooked meat", "Pie shell"),
            recipe("Uncooked meat pie", 20, 0.0, "Cooked chicken", "Pie shell"),
            recipe("Uncooked apple pie", 30, 0.0, "Cooking apple", "Pie shell"),
            recipe("Uncooked mushroom pie", 60, 0.0, "Sulliuscep cap", "Pie shell"),
            recipe("Uncooked botanical pie", 52, 0.0, "Golovanova fruit top", "Pie shell"),
            recipe("Uncooked dragonfruit pie", 73, 0.0, "Dragonfruit", "Pie shell"),
        ) +
            pie(
                34,
                "Tomato" to "Part garden pie#1",
                "Onion" to "Part garden pie#2",
                "Cabbage" to "Raw garden pie",
            ) +
            pie(
                47,
                "Trout" to "Part fish pie#1",
                "Cod" to "Part fish pie#2",
                "Potato" to "Raw fish pie",
            ) +
            pie(
                70,
                "Salmon" to "Part admiral pie#1",
                "Tuna" to "Part admiral pie#2",
                "Potato" to "Raw admiral pie",
            ) +
            pie(
                85,
                "Raw bear meat" to "Part wild pie#1",
                "Raw chompy" to "Part wild pie#2",
                "Raw rabbit" to "Raw wild pie",
            ) +
            pie(
                95,
                "Strawberry" to "Part summer pie#1",
                "Watermelon" to "Part summer pie#2",
                "Cooking apple" to "Raw summer pie",
            )

    private val pizzas =
        listOf(
            recipe("Incomplete pizza", 35, 0.0, "Pizza base", "Tomato"),
            recipe("Uncooked pizza", 35, 0.0, "Incomplete pizza", "Cheese"),
            recipe("Meat pizza", 45, 26.0, "Plain pizza", "Cooked meat"),
            recipe("Meat pizza", 45, 26.0, "Plain pizza", "Cooked chicken"),
            recipe("Anchovy pizza", 55, 39.0, "Plain pizza", "Anchovies"),
            recipe("Pineapple pizza", 65, 45.0, "Plain pizza", "Pineapple ring"),
            recipe("Pineapple pizza", 65, 45.0, "Plain pizza", "Pineapple chunks"),
        )

    private val cakes =
        listOf(
            recipe(
                "Uncooked cake",
                40,
                0.0,
                "Cake tin",
                "Egg",
                "Pot of flour",
                "Bucket of milk",
                returns = listOf("Bucket", "Pot"),
            ),
            recipe("Chocolate cake", 50, 30.0, "Cake", "Chocolate bar"),
            recipe("Chocolate cake", 50, 30.0, "Cake", "Chocolate dust"),
            recipe("Chocolate dust", 1, 0.0, "Chocolate bar", tools = listOf("Knife")),
        )

    private val stews =
        listOf(
            recipe("Incomplete stew#1", 25, 0.0, "Bowl of water", "Potato"),
            recipe("Incomplete stew#2", 25, 0.0, "Bowl of water", "Cooked meat"),
            recipe("Incomplete stew#2", 25, 0.0, "Bowl of water", "Cooked chicken"),
            recipe("Uncooked stew", 25, 0.0, "Incomplete stew#1", "Cooked meat"),
            recipe("Uncooked stew", 25, 0.0, "Incomplete stew#1", "Cooked chicken"),
            recipe("Uncooked stew", 25, 0.0, "Incomplete stew#2", "Potato"),
            recipe("Uncooked curry", 60, 0.0, "Uncooked stew", "3x Curry leaf"),
            recipe("Uncooked curry", 60, 0.0, "Uncooked stew", "Spice"),
        )

    private val potatoes =
        listOf(
            recipe("Potato with butter", 39, 40.0, "Baked potato", "Pat of butter"),
            recipe("Potato with cheese", 47, 40.0, "Potato with butter", "Cheese"),
            recipe(
                "Chilli potato",
                41,
                15.0,
                "Chilli con carne",
                "Potato with butter",
                returns = listOf("Bowl"),
            ),
            recipe(
                "Egg potato",
                51,
                45.0,
                "Egg and tomato",
                "Potato with butter",
                returns = listOf("Bowl"),
            ),
            recipe(
                "Mushroom potato",
                64,
                55.0,
                "Mushroom & onion",
                "Potato with butter",
                returns = listOf("Bowl"),
            ),
            recipe(
                "Tuna potato",
                68,
                10.0,
                "Tuna and corn",
                "Potato with butter",
                returns = listOf("Bowl"),
            ),
        )

    private val bowls =
        listOf(
            recipe("Chopped onion", 1, 0.0, "Bowl", "Onion", tools = listOf("Knife")),
            recipe("Chopped tomato", 1, 0.0, "Bowl", "Tomato", tools = listOf("Knife")),
            recipe("Onion & tomato", 1, 0.0, "Chopped onion", "Tomato"),
            recipe("Onion & tomato", 1, 0.0, "Chopped tomato", "Onion"),
            recipe("Chopped tuna", 1, 0.0, "Bowl", "Tuna", tools = listOf("Knife")),
            recipe("Minced meat", 1, 0.0, "Bowl", "Cooked meat", tools = listOf("Knife")),
            recipe("Sliced mushrooms", 1, 0.0, "Bowl", "Mushroom", tools = listOf("Knife")),
            recipe("Uncooked egg", 13, 0.0, "Egg", "Bowl"),
            recipe("Spicy sauce", 9, 25.0, "Chopped garlic", "Gnome spice"),
            recipe("Chilli con carne", 11, 0.0, "Spicy sauce", "Cooked meat"),
            recipe(
                "Chilli con carne",
                11,
                0.0,
                "Spicy sauce",
                "Minced meat",
                returns = listOf("Bowl"),
            ),
            recipe("Egg and tomato", 23, 0.0, "Scrambled egg", "Tomato"),
            recipe("Mushroom & onion", 57, 0.0, "Fried mushrooms", "Fried onions"),
            recipe("Tuna and corn", 67, 0.0, "Cooked sweetcorn", "Chopped tuna"),
        )

    /** Grapes in a jug of water, which turn into wine (or bad wine) after a short wait. */
    private val wine =
        listOf(recipe("Unfermented wine", 35, 0.0, "Grapes", "Jug of water", ferments = true))

    val all: List<PrepRecipe> = doughs + pies + pizzas + cakes + stews + potatoes + bowls + wine
}
