package org.rsmod.content.skills.magic.spell.attacks.arceuus

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.magic.MagicSpellType
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
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
import org.rsmod.content.skills.magic.spell.attacks.standard.NpcFreezes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.util.ParamMapBuilder
import org.rsmod.map.CoordGrid

class ArceuusSpellsTest {
    class Deps
    @Inject
    constructor(
        val arceuus: ArceuusSpells,
        val repo: SpellAttackRepository,
        val registry: SpellAttackRegistry,
        val manager: SpellAttackManager,
        val spells: MagicSpellRegistry,
        val freezes: NpcFreezes,
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
                objs.spell_ghostly_grasp,
                objs.spell_skeletal_grasp,
                objs.spell_undead_grasp,
                objs.spell_inferior_demonbane,
                objs.spell_superior_demonbane,
                objs.spell_dark_demonbane,
            )

        /** An npc with a lot of hitpoints and an Attack option. */
        fun npcType(demon: Boolean = false) =
            npcTypeFactory.create {
                name = "Test target"
                vislevel = 50
                attack = 100
                strength = 100
                defence = 1
                hitpoints = 500
                defaultMode = NpcMode.None
                op[1] = "Attack"
                if (demon) {
                    paramMap = ParamMapBuilder().apply { this[params.demon] = 1 }.toParamMap()
                }
            }
    }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(vararg items: Pair<String, Int>) {
        @Suppress("NAME_SHADOWING")
        val items = items.map { (name, count) -> "${name}rune" to count }.toTypedArray()
        player.teleport(CoordGrid(0, 50, 50, 34, 31))
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            player.inv[slot++] = InvObj(obj(name), count)
        }
        player.stats[stats.magic] = 99
        player.statMap.setXP(stats.magic, 0)
        player.withProtectedAccess { vars[varbits.spellbook] = Spellbook.Arceuus.varValue }
    }

    /** Every Ancient spell's runes, plenty of each. */
    private fun GameTestScope.prepareAllRunes() =
        prepare(
            "airrune" to 5000,
            "waterrune" to 5000,
            "earthrune" to 5000,
            "firerune" to 5000,
            "chaosrune" to 5000,
            "deathrune" to 5000,
            "bloodrune" to 5000,
            "soulrune" to 5000,
        )

    private fun GameTestScope.cast(deps: Deps, spell: ObjType, target: Npc, roll: Int = 0) {
        if (deps.registry[spell] == null) {
            with(deps.arceuus) { deps.repo.register(deps.manager) }
        }
        val attack = checkNotNull(deps.registry[spell]) { "No attack registered for $spell." }
        val magic = checkNotNull(deps.spells.getObjSpell(spell))
        // The first roll is the accuracy roll, and a zero is a hit; the rest are damage rolls.
        deps.random.next = 0
        repeat(24) { deps.random.next = roll }
        player.withProtectedAccess {
            with(attack) { attack(target, CombatAttack.Spell(null, magic, false)) }
        }
    }

    @Test
    fun GameTestState.`all six Arceuus attack spells have an attack`() =
        runInjectedGameTest(Deps::class, SpellModule) { deps ->
            with(deps.arceuus) { deps.repo.register(deps.manager) }
            for (spell in SPELLS) {
                check(deps.registry[spell] != null) { "No attack registered for $spell." }
                val magic = checkNotNull(deps.spells.getObjSpell(spell)) { "$spell is no spell." }
                check(magic.type == MagicSpellType.Combat) {
                    "${magic.name} is not a combat spell."
                }
                check(magic.spellbook == Spellbook.Arceuus) { "${magic.name} is not Arceuus." }
            }
        }

    @Test
    fun GameTestState.`undead grasp always binds with a hit and mark of darkness`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            prepare("fire" to 12, "blood" to 1)
            player.withProtectedAccess { vars[varbits.buff_mark_of_darkness_disabled] = 1 }

            cast(deps, objs.spell_undead_grasp, target)

            check(target.isFrozen) { "A bind chance of 100% should have held the npc." }
        }

    @Test
    fun GameTestState.`a grasp without a bind roll does not hold the npc`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            prepare("air" to 4, "chaos" to 1)

            // The accuracy roll hits (zero); the bind roll of 99 is above the 10% chance.
            cast(deps, objs.spell_ghostly_grasp, target, roll = 99)

            check(!target.isFrozen) { "A roll of 99 should not bind." }
        }

    @Test
    fun GameTestState.`demonbane refuses anything that is not a demon and keeps the runes`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            prepare("fire" to 3, "chaos" to 1)

            cast(deps, objs.spell_inferior_demonbane, target)

            check(player.count(obj("chaosrune")) == 1) { "The runes should be kept." }
            assertMessageSent("This spell can only be cast on demonic creatures.")
        }

    @Test
    fun GameTestState.`demonbane hits a demon`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType(demon = true))
            prepare("fire" to 3, "chaos" to 1)

            cast(deps, objs.spell_inferior_demonbane, target, roll = 10)
            repeat(4) { advance() }

            check(target.hitpoints < 500) { "The spell should have hurt the demon." }
            check(player.count(obj("chaosrune")) == 0) { "The runes should be used." }
        }
}
