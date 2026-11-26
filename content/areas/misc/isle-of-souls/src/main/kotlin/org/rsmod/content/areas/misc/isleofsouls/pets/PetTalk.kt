package org.rsmod.content.areas.misc.isleofsouls.pets

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.obj.ObjTypeList

/**
 * A pet's "Talk-to": the wiki transcript of its current look (`pets/dialogue.json`, made by
 * `tools/pets/gen_dialogue.py`), played as chat boxes. Option menus show the transcript's options,
 * random conversations pick one at random, and "[player name]" becomes the player's name.
 *
 * The transcripts do not give facial expressions, so every line uses the neutral one. Conditions
 * the isle can check are checked (the rift guardian's lock, a worn item); the rest count as not
 * met. Picking the rift guardian's "stay this colour" / "adopt the altar's colour" also sets its
 * lock, as in the game.
 */
class PetTalk
@Inject
constructor(private val random: GameRandom, private val objTypes: ObjTypeList) {
    sealed interface Node

    data class Line(val player: Boolean, val text: String) : Node

    data class Box(val text: String) : Node

    data class Menu(
        val title: String?,
        val random: Boolean,
        val opts: List<Pair<String, List<Node>>>,
    ) : Node

    data class Cond(val branches: List<Pair<String, List<Node>>>) : Node

    data object End : Node

    data object Back : Node

    data object Restart : Node

    private enum class Flow {
        Continue,
        Back,
        End,
        Restart,
    }

    val conversations: Map<String, List<Node>> by lazy { load() }

    fun conversation(pet: Pet, form: PetForm): List<Node>? =
        conversations["${pet.key}/${form.label}"]

    suspend fun talk(access: ProtectedAccess, npc: Npc, pet: Pet, form: PetForm) {
        val nodes = conversation(pet, form) ?: return
        access.startDialogue(npc) {
            var restarts = 0
            while (run(nodes) == Flow.Restart && restarts++ < MAX_RESTARTS) {
                // "Back to the start" in the transcript.
            }
        }
    }

    private suspend fun Dialogue.run(nodes: List<Node>): Flow {
        for (node in nodes) {
            val flow =
                when (node) {
                    is Line -> {
                        val text = name(node.text)
                        if (node.player) chatPlayer(neutral, text) else chatNpc(neutral, text)
                        Flow.Continue
                    }
                    is Box -> {
                        mesbox(name(node.text))
                        Flow.Continue
                    }
                    is Menu -> menu(node)
                    is Cond -> cond(node)
                    End -> Flow.End
                    Back -> Flow.Back
                    Restart -> Flow.Restart
                }
            if (flow != Flow.Continue) {
                return flow
            }
        }
        return Flow.Continue
    }

    private suspend fun Dialogue.menu(menu: Menu): Flow {
        val opts =
            menu.opts.filterNot { (label, _) -> label.contains("(only if", ignoreCase = true) }
        if (opts.isEmpty()) {
            return Flow.Continue
        }
        if (menu.random) {
            val (_, body) = opts[random.of(0, opts.size - 1)]
            return run(body).let { if (it == Flow.Back) Flow.End else it }
        }
        while (true) {
            val (label, body) = choose(opts, menu.title)
            onPicked(label)
            when (val flow = run(body)) {
                Flow.Back -> continue
                Flow.Continue -> return Flow.End
                else -> return flow
            }
        }
    }

    /** Shows [opts] (more than five get a "More..." page) and returns the one picked. */
    private suspend fun Dialogue.choose(
        opts: List<Pair<String, List<Node>>>,
        title: String?,
    ): Pair<String, List<Node>> {
        var page = 0
        while (true) {
            val more = opts.size > 5
            val shown = if (more) opts.drop(page * 4).take(4) else opts
            val labels = shown.map { name(it.first) } + if (more) listOf("More...") else emptyList()
            val pick = pickOne(labels, title ?: "Select an option")
            if (more && pick == labels.size - 1) {
                page = if ((page + 1) * 4 >= opts.size) 0 else page + 1
                continue
            }
            return shown[pick]
        }
    }

    private suspend fun Dialogue.pickOne(labels: List<String>, title: String): Int =
        when (labels.size) {
            1 -> 0
            2 -> choice2(labels[0], 0, labels[1], 1, title = title)
            3 -> choice3(labels[0], 0, labels[1], 1, labels[2], 2, title = title)
            4 -> choice4(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, title = title)
            else ->
                choice5(
                    labels[0],
                    0,
                    labels[1],
                    1,
                    labels[2],
                    2,
                    labels[3],
                    3,
                    labels[4],
                    4,
                    title = title,
                )
        }

    private suspend fun Dialogue.cond(cond: Cond): Flow {
        var previous: Boolean? = null
        for ((text, body) in cond.branches) {
            val met = met(text, previous)
            previous = met
            if (met) {
                return run(body)
            }
        }
        return Flow.Continue
    }

    /** Whether a transcript condition holds. "...is not:" is the opposite of the one before. */
    private fun Dialogue.met(text: String, previous: Boolean?): Boolean {
        val lower = text.lowercase()
        if (
            previous != null &&
                (lower.startsWith("if the player is not") || lower.startsWith("otherwise"))
        ) {
            return !previous
        }
        val locked = player.vars[pet_varps.rift_unlocks] and PetScript.RIFT_LOCK_BIT != 0
        return when {
            "set to changing colours" in lower -> !locked
            "keeping its colour" in lower -> locked
            "wielding " in lower || "wearing " in lower -> {
                val item = lower.substringAfter("wielding ").substringAfter("wearing ").trimEnd(':')
                player.worn.any { it != null && objTypes[it].name.equals(item, ignoreCase = true) }
            }
            else -> false
        }
    }

    /** The rift guardian's colour lock follows what the player tells it. */
    private fun Dialogue.onPicked(label: String) {
        val lower = label.lowercase()
        val vars = player.vars
        when {
            "stay this colour" in lower || "stay locked" in lower ->
                VarPlayerIntMapSetter.set(
                    player,
                    pet_varps.rift_unlocks,
                    vars[pet_varps.rift_unlocks] or PetScript.RIFT_LOCK_BIT,
                )
            "adopt the altar" in lower || "carry on adopting" in lower ->
                VarPlayerIntMapSetter.set(
                    player,
                    pet_varps.rift_unlocks,
                    vars[pet_varps.rift_unlocks] and PetScript.RIFT_LOCK_BIT.inv(),
                )
        }
    }

    private fun Dialogue.name(text: String): String =
        text
            .replace("[player name]", player.displayName, ignoreCase = true)
            .replace("[Player name]", player.displayName)
            .replace("[name]", player.displayName, ignoreCase = true)

    private fun load(): Map<String, List<Node>> {
        val stream =
            PetTalk::class.java.getResourceAsStream(RESOURCE)
                ?: error("Missing pet dialogue resource: $RESOURCE")
        val root = stream.use { ObjectMapper().readTree(it) }
        return buildMap { root.fields().forEach { (key, nodes) -> put(key, parseNodes(nodes)) } }
    }

    private fun parseNodes(array: JsonNode): List<Node> = array.map(::parseNode)

    private fun parseNode(node: JsonNode): Node =
        when (val type = node["t"].asText()) {
            "line" -> Line(node["who"].asText() == "player", node["text"].asText())
            "box" -> Box(node["text"].asText())
            "menu" ->
                Menu(
                    title = node["title"]?.takeUnless { it.isNull }?.asText(),
                    random = node["random"].asBoolean(),
                    opts = node["opts"].map { it["label"].asText() to parseNodes(it["nodes"]) },
                )
            "cond" -> Cond(node["branches"].map { it["cond"].asText() to parseNodes(it["nodes"]) })
            "end" -> End
            "back" -> Back
            "restart" -> Restart
            else -> error("Unknown pet dialogue node `$type`.")
        }

    private companion object {
        const val RESOURCE = "/org/rsmod/content/areas/misc/isleofsouls/pets/dialogue.json"
        const val MAX_RESTARTS = 3
    }
}
