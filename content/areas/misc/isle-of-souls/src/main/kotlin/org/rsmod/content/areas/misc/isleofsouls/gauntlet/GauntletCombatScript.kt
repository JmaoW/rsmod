package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * How the maze's monsters fight and what they drop. The weak and strong monsters and the bear fight
 * in melee with the engine's own melee, from their cache stats; the dragon casts magic and the dark
 * beast shoots, with the projectiles the cache has for them. [GauntletMonsters] aims them; once
 * aimed, the engine calls the handlers below.
 *
 * The wiki gives every demi-boss a max hit of 28 (48 in the Corrupted Gauntlet) and every monster
 * an attack every 4 ticks.
 */
class GauntletCombatScript
@Inject
constructor(private val gauntlet: Gauntlet, private val attacks: NpcAttacks) : PluginScript() {
    private val dragon =
        mapOf(
            GauntletMode.Regular to
                NpcAttack(
                    style = NpcAttackStyle.Magic,
                    projectile = gauntlet_spotanims.dragon_travel,
                    projanim = projanims.magic_spell,
                    impactSpot = gauntlet_spotanims.dragon_impact,
                    maxHit = DEMI_MAX_HIT,
                ),
            GauntletMode.Corrupted to
                NpcAttack(
                    style = NpcAttackStyle.Magic,
                    projectile = gauntlet_spotanims.dragon_travel_hm,
                    projanim = projanims.magic_spell,
                    impactSpot = gauntlet_spotanims.dragon_impact_hm,
                    maxHit = CORRUPTED_DEMI_MAX_HIT,
                ),
        )

    private val darkBeast =
        mapOf(
            GauntletMode.Regular to
                NpcAttack(
                    style = NpcAttackStyle.Ranged,
                    projectile = gauntlet_spotanims.dark_beast_travel,
                    projanim = projanims.arrow,
                    maxHit = DEMI_MAX_HIT,
                ),
            GauntletMode.Corrupted to
                NpcAttack(
                    style = NpcAttackStyle.Ranged,
                    projectile = gauntlet_spotanims.dark_beast_travel_hm,
                    projanim = projanims.arrow,
                    maxHit = CORRUPTED_DEMI_MAX_HIT,
                ),
        )

    override fun ScriptContext.startup() {
        for (mode in GauntletMode.entries) {
            val dragonType = GauntletMonster.Dragon.type(mode)
            val beastType = GauntletMonster.DarkBeast.type(mode)
            onAiOpPlayer2(dragonType) { shoot(it.target, dragon.getValue(mode)) }
            onAiApPlayer2(dragonType) { shoot(it.target, dragon.getValue(mode)) }
            onAiOpPlayer2(beastType) { shoot(it.target, darkBeast.getValue(mode)) }
            onAiApPlayer2(beastType) { shoot(it.target, darkBeast.getValue(mode)) }
        }

        // A monster of a run drops by the run's rules, not by a table.
        onEvent<NpcDropEvents.Claim> {
            val run = gauntlet.runOf(npc) ?: return@onEvent
            drops.clear()
            drops += gauntlet.monsterKilled(run, npc, hero)
        }
    }

    private fun StandardNpcAccess.shoot(target: Player, attack: NpcAttack) {
        if (!target.isValidTarget() || actionDelay > mapClock) {
            return
        }
        attacks.attack(npc, target, attack)
        actionDelay = mapClock + ATTACK_RATE
    }

    private companion object {
        const val DEMI_MAX_HIT = 28
        const val CORRUPTED_DEMI_MAX_HIT = 48
        const val ATTACK_RATE = 4
    }
}

/**
 * What the monsters need to chase a player across a room and, for the two that shoot, to stop and
 * shoot from a distance. The engine keeps a chase going only while the target is within `maxRange +
 * attackRange` of where the monster spawned (the maze moves that point along with the target), and
 * reads the distance a monster attacks from out of its type. The shooters range is a guess: the
 * wiki gives none. Needs `packCache`.
 */
internal object GauntletNpcEdits : NpcEditor() {
    init {
        for (monster in GauntletMonster.entries) {
            for (mode in GauntletMode.entries) {
                edit(monster.type(mode), monster.shoots, monster.anims())
            }
        }
    }

    private fun edit(type: NpcType, shoots: Boolean, anims: MonsterAnims) {
        edit(type) {
            maxRange = LEASH
            giveChase = true
            defaultMode = NpcMode.None
            if (shoots) {
                attackRange = SHOOT_RANGE
            }
            param[params.attack_anim] = anims.attack
            param[params.defend_anim] = anims.ready
            param[params.death_anim] = anims.death
        }
    }

    private class MonsterAnims(val ready: SeqType, val attack: SeqType, val death: SeqType)

    /** The animations each monster has in the cache and in the real game (see [GauntletSeqs]). */
    private fun GauntletMonster.anims(): MonsterAnims =
        when (this) {
            GauntletMonster.Rat ->
                MonsterAnims(
                    gauntlet_seqs.rat_ready,
                    gauntlet_seqs.rat_attack,
                    gauntlet_seqs.rat_death,
                )
            GauntletMonster.Spider ->
                MonsterAnims(
                    gauntlet_seqs.spider_ready,
                    gauntlet_seqs.spider_attack,
                    gauntlet_seqs.spider_death,
                )
            GauntletMonster.Bat ->
                MonsterAnims(
                    gauntlet_seqs.bat_ready,
                    gauntlet_seqs.bat_attack,
                    gauntlet_seqs.bat_death,
                )
            GauntletMonster.Unicorn ->
                MonsterAnims(
                    gauntlet_seqs.unicorn_ready,
                    gauntlet_seqs.unicorn_attack,
                    gauntlet_seqs.unicorn_death,
                )
            GauntletMonster.Scorpion ->
                MonsterAnims(
                    gauntlet_seqs.scorpion_ready,
                    gauntlet_seqs.scorpion_attack,
                    gauntlet_seqs.scorpion_death,
                )
            GauntletMonster.Wolf ->
                MonsterAnims(
                    gauntlet_seqs.wolf_ready,
                    gauntlet_seqs.wolf_attack,
                    gauntlet_seqs.wolf_death,
                )
            GauntletMonster.Bear ->
                MonsterAnims(
                    gauntlet_seqs.bear_ready,
                    gauntlet_seqs.bear_attack,
                    gauntlet_seqs.bear_death,
                )
            GauntletMonster.Dragon ->
                MonsterAnims(
                    gauntlet_seqs.dragon_ready,
                    gauntlet_seqs.dragon_attack,
                    gauntlet_seqs.dragon_death,
                )
            GauntletMonster.DarkBeast ->
                MonsterAnims(
                    gauntlet_seqs.dark_beast_ready,
                    gauntlet_seqs.dark_beast_attack,
                    gauntlet_seqs.dark_beast_death,
                )
        }

    /** Further than any two tiles of a room are apart. */
    const val LEASH = 30

    /** The wiki gives no range for the dragon or the dark beast; this is a guess. */
    const val SHOOT_RANGE = 8
}
