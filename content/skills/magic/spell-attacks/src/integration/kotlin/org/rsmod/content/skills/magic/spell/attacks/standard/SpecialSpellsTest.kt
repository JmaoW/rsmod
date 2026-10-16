package org.rsmod.content.skills.magic.spell.attacks.standard

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.SpellAttackRepository
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
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class SpecialSpellsTest {
    class Deps
    @Inject
    constructor(
        val special: SpecialSpells,
        val repo: SpellAttackRepository,
        val registry: SpellAttackRegistry,
        val manager: SpellAttackManager,
        val spells: MagicSpellRegistry,
        val runes: MagicRuneManager,
        val random: GameTestScope.VariableGameRandom,
    )

    object SpellModule : AbstractModule() {
        override fun configure() {
            install(MagicSpellModule())
            bind(SpellAttackRegistry::class.java).`in`(Scopes.SINGLETON)
            // The game cycle and the spells must queue on the same world queue.
            bind(WorldQueueList::class.java).`in`(Scopes.SINGLETON)
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
                NpcHitScript::class,
                PlayerHitScript::class,
            )

        val SPELLS: List<ObjType> =
            listOf(
                objs.spell_confuse,
                objs.spell_weaken,
                objs.spell_curse,
                objs.spell_vulnerability,
                objs.spell_enfeeble,
                objs.spell_stun,
                objs.spell_bind,
                objs.spell_snare,
                objs.spell_entangle,
                objs.spell_crumble_undead,
                objs.spell_iban_blast,
                objs.spell_magic_dart,
                objs.spell_saradomin_strike,
                objs.spell_claws_of_guthix,
                objs.spell_flames_of_zamorak,
            )

        fun npcType() =
            npcTypeFactory.create {
                name = "Test target"
                vislevel = 50
                attack = 100
                strength = 100
                defence = 100
                hitpoints = 200
                defaultMode = NpcMode.None
            }
    }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(vararg items: Pair<String, Int>) {
        player.teleport(CoordGrid(0, 50, 50, 34, 31))
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            val type = obj(name)
            player.inv[slot++] = InvObj(type, count)
        }
        player.stats[stats.magic] = 99
        player.statMap.setXP(stats.magic, 0)
    }

    private fun GameTestScope.cast(deps: Deps, spell: ObjType, target: Npc) {
        if (deps.registry[spell] == null) {
            with(deps.special) { deps.repo.register(deps.manager) }
        }
        val attack = checkNotNull(deps.registry[spell]) { "No attack registered for $spell." }
        val magic = checkNotNull(deps.spells.getObjSpell(spell))
        // Every roll is zero, which is a hit: the accuracy roll is below the chance to hit.
        repeat(16) { deps.random.next = 0 }
        player.withProtectedAccess {
            with(attack) { attack(target, CombatAttack.Spell(null, magic, false)) }
        }
    }

    @Test
    fun GameTestState.`every non elemental combat spell has an attack`() =
        runInjectedGameTest(Deps::class, SpellModule) { deps ->
            with(deps.special) { deps.repo.register(deps.manager) }
            for (spell in SPELLS) {
                check(deps.registry[spell] != null) { "No attack registered for $spell." }
                val magic = checkNotNull(deps.spells.getObjSpell(spell)) { "$spell is no spell." }
                check(magic.type == org.rsmod.api.combat.commons.magic.MagicSpellType.Combat) {
                    "${magic.name} is not a combat spell."
                }
            }
        }

    @Test
    fun GameTestState.`debuffs lower the stat of the spell by its percent`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val cases =
                listOf(
                    Triple(objs.spell_confuse, "attack", 5),
                    Triple(objs.spell_weaken, "strength", 5),
                    Triple(objs.spell_curse, "defence", 5),
                    Triple(objs.spell_stun, "attack", 10),
                    Triple(objs.spell_enfeeble, "strength", 10),
                    Triple(objs.spell_vulnerability, "defence", 10),
                )
            for ((spell, stat, percent) in cases) {
                val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
                prepare(
                    "waterrune" to 1000,
                    "earthrune" to 1000,
                    "bodyrune" to 1000,
                    "soulrune" to 1000,
                )

                cast(deps, spell, target)

                val level =
                    when (stat) {
                        "attack" -> target.attackLvl
                        "strength" -> target.strengthLvl
                        else -> target.defenceLvl
                    }
                check(level == 100 - percent) {
                    "$spell should lower $stat by $percent%, level is $level."
                }
            }
        }

    @Test
    fun GameTestState.`a lowered target refuses another debuff and keeps the runes`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            target.attackLvl = 90
            prepare("waterrune" to 1000, "earthrune" to 1000, "bodyrune" to 1000)

            cast(deps, objs.spell_confuse, target)

            check(player.count(obj("waterrune")) == 1000) { "The runes should be kept." }
            assertMessageSent("The target's stats have already been lowered.")
        }

    @Test
    fun GameTestState.`crumble undead refuses anything that is not undead`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            prepare("airrune" to 1000, "earthrune" to 1000, "chaosrune" to 1000)

            cast(deps, objs.spell_crumble_undead, target)

            check(player.count(obj("chaosrune")) == 1000) { "The runes should be kept." }
            assertMessageSent("This spell only affects skeletons, zombies, ghosts and shades.")
        }

    @Test
    fun GameTestState.`bind holds an npc and then it is immune for a moment`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            prepare("waterrune" to 1000, "earthrune" to 1000, "naturerune" to 1000)

            cast(deps, objs.spell_bind, target)

            check(target.isFrozen) { "Expected the npc to be held." }
            val before = player.count(obj("waterrune"))
            cast(deps, objs.spell_bind, target)
            check(player.count(obj("waterrune")) == before) { "The second cast should be refused." }
            assertMessageSent("The target is currently immune to that spell.")
        }
}
