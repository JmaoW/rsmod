package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.configs.Creature
import org.rsmod.content.skills.hunter.configs.Falconry
import org.rsmod.content.skills.hunter.configs.Kebbit
import org.rsmod.content.skills.hunter.configs.hunter_objs
import org.rsmod.content.skills.hunter.configs.hunter_seqs
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - Matthias, who lends the falconer's glove for coins, and the falcon itself, which flies out to
// the
//   kebbit and has to be fetched back (here the catch comes straight to the hunter's hand)
// - the spotted capes that kebbit fur makes
//
// Falconry: with a falconer's glove on, "Catch" sends the falcon after a kebbit. The catch chance,
// level and experience are the Wiki's for each of the three kebbits; the hunter gets bones and the
// kebbit's fur.
class Falconer
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in npcTypes.values) {
            val kebbit = Falconry.forNpcName(type.name) ?: continue
            if (type.op.getOrNull(0) == "Catch") {
                onOpNpc1(type) { hunt(it.npc, kebbit) }
            }
        }
    }

    private suspend fun ProtectedAccess.hunt(npc: org.rsmod.game.entity.Npc, kebbit: Kebbit) {
        if (!hasGlove()) {
            mes("You need a falconer's glove to hunt with a falcon.")
            return
        }
        if (stat(stats.hunter) < kebbit.level) {
            mes("You need a Hunter level of ${kebbit.level} to hunt ${kebbit.name.lowercase()}s.")
            return
        }
        anim(hunter_seqs.falcon_attack)
        delay(FLIGHT_TICKS)
        if (npc.isInvisible) {
            return
        }
        val chance = min(1.0, Creature.chance(kebbit.low, kebbit.high, stat(stats.hunter)))
        if (random.randomDouble() >= chance) {
            mes("The kebbit evades your falcon.")
            return
        }
        npcRepo.despawn(npc, npcRepo.respawnSpeed.scale(KEBBIT_RESPAWN_TICKS))
        for (obj in listOf(hunter_objs.bones, kebbit.fur)) {
            if (!invAdd(inv, obj, 1).success) {
                invAddOrDrop(objRepo, obj, 1)
            }
        }
        statAdvance(stats.hunter, kebbit.xp * xpMods.get(player, stats.hunter))
        mes("Your falcon catches the ${kebbit.name.lowercase()} and brings it back.")
        publish(Hunted(player, kebbit))
    }

    private fun ProtectedAccess.hasGlove(): Boolean =
        player.righthand.isType(hunter_objs.falconers_glove) ||
            player.righthand.isType(hunter_objs.falconers_glove_bird)

    /** Published when a falcon brings back a kebbit. */
    class Hunted(val player: Player, val kebbit: Kebbit) : UnboundEvent

    companion object {
        /** Ticks the falcon is out for. */
        const val FLIGHT_TICKS = 3

        const val KEBBIT_RESPAWN_TICKS = 40
    }
}
