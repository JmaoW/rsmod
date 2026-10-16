package org.rsmod.content.skills.slayer.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.varns
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * A monster that cannot be killed by damage alone. Once it is down to [threshold] hitpoints or
 * fewer, a [finisher] used on it kills it. [consumed] says whether the item is used up.
 *
 * With the Slayer unlock at [autoBit], a finisher in the inventory is used by itself on the hit
 * that would take the monster to [threshold].
 */
class Finishing(
    val names: Set<String>,
    val threshold: Int,
    val finishers: List<ObjType>,
    val consumed: Set<ObjType>,
    val autoBit: Int?,
    val weakMessage: String,
)

/**
 * Gargoyles, rockslugs and desert lizards. A hit never takes them below one hitpoint; at or below
 * the threshold the player must use the hammer, salt or ice cooler on them, or buy the perk that
 * does it for them.
 */
class SlayerFinishing
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val engine: SlayerEngine,
    private val modifier: NpcHitModifier,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcHitEvents.ModifyAny> { cap(npc, hit.type, source, this) }
        registerItemUses()
    }

    private fun ScriptContext.registerItemUses() {
        for (rule in RULES) {
            for (type in npcTypes.values) {
                if (type.name.lowercase() !in rule.names) continue
                for (item in rule.finishers) {
                    onOpNpcU(type, item) { use(it.npc, item, rule) }
                }
            }
        }
    }

    private fun ruleFor(npc: Npc): Finishing? {
        val name = npc.visType.name.lowercase()
        return RULES.firstOrNull { name in it.names }
    }

    /** Keeps [npc] above zero until it is finished, or finishes it for a player with the perk. */
    private fun cap(npc: Npc, type: HitType, source: Player?, event: NpcHitEvents.ModifyAny) {
        val rule = ruleFor(npc) ?: return
        if (npc.uid.packed in finishing) {
            return
        }
        val hit = event.hit
        val after = npc.hitpoints - hit.damage
        if (after > rule.threshold) {
            return
        }
        if (source != null && autoFinish(source, rule)) {
            // Flat armour would take some of the final blow, so it goes.
            npc.vars[varns.flat_armour] = 0
            hit.damage = npc.hitpoints
            return
        }
        hit.damage = minOf(hit.damage, maxOf(0, npc.hitpoints - 1))
    }

    private fun autoFinish(player: Player, rule: Finishing): Boolean {
        val bit = rule.autoBit ?: return false
        if (!engine.isUnlocked(player, bit)) {
            return false
        }
        val item = rule.finishers.firstOrNull { player.inv.contains(it) } ?: return false
        if (item in rule.consumed) {
            player.invDel(player.inv, item, 1)
        }
        return true
    }

    /** [player] used [item] on [npc]. */
    fun ProtectedAccess.use(npc: Npc, item: ObjType, rule: Finishing) {
        if (npc.hitpoints > rule.threshold) {
            if (item in rule.consumed) {
                invDel(player.inv, item, 1)
            }
            mes(rule.weakMessage)
            return
        }
        if (item in rule.consumed) {
            invDel(player.inv, item, 1)
        }
        val uid = npc.uid.packed
        val armour = npc.vars[varns.flat_armour]
        finishing += uid
        npc.vars[varns.flat_armour] = 0
        try {
            // More than it has left: the hit processor takes only what remains, so nothing a
            // modifier takes off can leave it standing.
            npc.queueHit(player, 1, HitType.Typeless, npc.hitpoints + OVERKILL, modifier)
        } finally {
            finishing -= uid
            npc.vars[varns.flat_armour] = armour
        }
    }

    companion object {
        private const val OVERKILL = 100

        /** Monsters being finished at this moment: their hit may take them to zero. */
        private val finishing = HashSet<Int>()

        val RULES: List<Finishing> =
            listOf(
                Finishing(
                    names = setOf("gargoyle", "marble gargoyle"),
                    threshold = 8,
                    finishers = listOf(SlayerObjs.rock_hammer, SlayerObjs.rock_thrownhammer),
                    consumed = setOf(SlayerObjs.rock_thrownhammer),
                    autoBit = 0,
                    weakMessage = "The gargoyle isn't weak enough to be smashed yet.",
                ),
                Finishing(
                    names = setOf("rockslug", "giant rockslug"),
                    threshold = 4,
                    finishers = listOf(SlayerObjs.bag_of_salt),
                    consumed = setOf(SlayerObjs.bag_of_salt),
                    autoBit = 1,
                    weakMessage = "The rockslug isn't weak enough for the salt to work yet.",
                ),
                Finishing(
                    names = setOf("desert lizard", "lizard", "small lizard"),
                    threshold = 4,
                    finishers = listOf(SlayerObjs.ice_cooler),
                    consumed = setOf(SlayerObjs.ice_cooler),
                    autoBit = 2,
                    weakMessage = "The lizard isn't weak enough for the ice to finish it yet.",
                ),
            )
    }
}
