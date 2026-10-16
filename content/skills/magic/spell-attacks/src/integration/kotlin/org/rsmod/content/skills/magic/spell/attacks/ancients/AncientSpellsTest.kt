package org.rsmod.content.skills.magic.spell.attacks.ancients

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.magic.MagicSpellType
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.isValidTarget
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
import org.rsmod.map.CoordGrid

class AncientSpellsTest {
    class Deps
    @Inject
    constructor(
        val ancients: AncientSpells,
        val repo: SpellAttackRepository,
        val registry: SpellAttackRegistry,
        val manager: SpellAttackManager,
        val spells: MagicSpellRegistry,
        val poison: NpcPoison,
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
                objs.spell_smoke_rush,
                objs.spell_shadow_rush,
                objs.spell_blood_rush,
                objs.spell_ice_rush,
                objs.spell_smoke_burst,
                objs.spell_shadow_burst,
                objs.spell_blood_burst,
                objs.spell_ice_burst,
                objs.spell_smoke_blitz,
                objs.spell_shadow_blitz,
                objs.spell_blood_blitz,
                objs.spell_ice_blitz,
                objs.spell_smoke_barrage,
                objs.spell_shadow_barrage,
                objs.spell_blood_barrage,
                objs.spell_ice_barrage,
            )

        /** An npc with a lot of hitpoints and an Attack option. */
        fun npcType(attackable: Boolean = true) =
            npcTypeFactory.create {
                name = "Test target"
                vislevel = 50
                attack = 100
                strength = 100
                defence = 1
                hitpoints = 500
                defaultMode = NpcMode.None
                if (attackable) {
                    op[1] = "Attack"
                }
            }
    }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(vararg items: Pair<String, Int>) {
        player.teleport(CoordGrid(0, 50, 50, 34, 31))
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            player.inv[slot++] = InvObj(obj(name), count)
        }
        player.stats[stats.magic] = 99
        player.statMap.setXP(stats.magic, 0)
        player.withProtectedAccess { vars[varbits.spellbook] = Spellbook.Ancients.varValue }
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
            with(deps.ancients) { deps.repo.register(deps.manager) }
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
    fun GameTestState.`all sixteen Ancient combat spells have an attack`() =
        runInjectedGameTest(Deps::class, SpellModule) { deps ->
            with(deps.ancients) { deps.repo.register(deps.manager) }
            for (spell in SPELLS) {
                check(deps.registry[spell] != null) { "No attack registered for $spell." }
                val magic = checkNotNull(deps.spells.getObjSpell(spell)) { "$spell is no spell." }
                check(magic.type == MagicSpellType.Combat) {
                    "${magic.name} is not a combat spell."
                }
                check(magic.spellbook == Spellbook.Ancients) { "${magic.name} is not Ancient." }
            }
        }

    @Test
    fun GameTestState.`ice spells freeze for the length of the spell`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val cases =
                listOf(
                    objs.spell_ice_rush to 8,
                    objs.spell_ice_burst to 16,
                    objs.spell_ice_blitz to 24,
                    objs.spell_ice_barrage to 32,
                )
            for ((spell, ticks) in cases) {
                val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
                prepareAllRunes()

                cast(deps, spell, target)

                check(target.isFrozen) { "$spell should hold the npc." }
                check(target.frozenUntil - target.currentMapClock <= ticks) { "Held too long." }
                check(target.frozenUntil - target.currentMapClock >= ticks - 2) {
                    "Held too short."
                }
                check(deps.freezes.isHeld(target, target.currentMapClock))
            }
        }

    @Test
    fun GameTestState.`a frozen npc is not frozen again until it has thawed`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            prepareAllRunes()
            cast(deps, objs.spell_ice_rush, target)
            val firstFreeze = target.frozenUntil

            advance(2)
            cast(deps, objs.spell_ice_barrage, target)

            check(target.frozenUntil == firstFreeze) { "A held npc should not be held again." }
        }

    @Test
    fun GameTestState.`a frozen npc can be targeted and takes the freezing hit while frozen`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            prepareAllRunes()

            cast(deps, objs.spell_ice_barrage, target, roll = 20)

            check(target.isFrozen)
            check(!target.isDelayed) { "A freeze should only stop movement." }
            check(target.isValidTarget()) { "A frozen npc should still be attackable." }
            advance(6)
            check(target.isFrozen) { "The npc should still be frozen when the hit lands." }
            check(target.hitpoints < 500) { "The hit should land while the npc is frozen." }
        }

    @Test
    fun GameTestState.`a frozen npc cannot move until it thaws`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val start = CoordGrid(0, 50, 50, 36, 31)
            val target = spawnNpc(start, npcType())
            deps.freezes.hold(target, 4)

            target.walk(start.translateX(3))
            advance(3)
            check(target.coords == start) { "A frozen npc should not move, was ${target.coords}." }

            advance(3)
            target.walk(start.translateX(3))
            advance(2)
            check(target.coords != start) { "A thawed npc should move." }
        }

    @Test
    fun GameTestState.`shadow spells lower attack by ten or fifteen percent and do not stack`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val rush = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            prepareAllRunes()
            cast(deps, objs.spell_shadow_rush, rush)
            check(rush.attackLvl == 90) { "Expected 90, was ${rush.attackLvl}." }
            cast(deps, objs.spell_shadow_rush, rush)
            check(rush.attackLvl == 90) { "A second Shadow Rush should not lower it more." }

            val blitz = spawnNpc(CoordGrid(0, 50, 50, 37, 31), npcType())
            prepareAllRunes()
            cast(deps, objs.spell_shadow_blitz, blitz)
            check(blitz.attackLvl == 85) { "Expected 85, was ${blitz.attackLvl}." }
        }

    @Test
    fun GameTestState.`blood spells heal a quarter of the damage dealt`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            prepareAllRunes()
            player.stats[stats.hitpoints] = 99
            player.statMap.setCurrentLevel(stats.hitpoints, 40.toByte())

            cast(deps, objs.spell_blood_barrage, target, roll = 20)
            val healed = player.statMap.getCurrentLevel(stats.hitpoints).toInt() - 40

            check(healed in 1..8) { "A quarter of at most 30 damage was expected, healed $healed." }
        }

    @Test
    fun GameTestState.`poison hits every thirty ticks and weakens every fifth hit`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            prepareAllRunes()
            check(deps.poison.poison(target, player, 2)) { "Expected the npc to be poisoned." }
            check(deps.poison.nextDamage(target) == 2)

            val start = target.hitpoints
            var ticks = 0
            var weakenedAt = -1
            while (deps.poison.isPoisoned(target) && ticks < 400) {
                advance()
                ticks++
                if (weakenedAt == -1 && deps.poison.nextDamage(target) == 1) {
                    weakenedAt = ticks
                }
            }

            // Ten hits thirty ticks apart; the damage drops from 2 to 1 after the fifth.
            check(ticks in 295..310) { "The poison should last ten hits, lasted $ticks ticks." }
            check(weakenedAt in 148..160) {
                "The damage should drop after five hits ($weakenedAt)."
            }
            // The npc heals a point now and then, so the total is only checked loosely.
            check(start - target.hitpoints >= 10) { "Expected at least 10 damage in all." }
        }

    @Test
    fun GameTestState.`a weaker poison does not replace a stronger one`() =
        runInjectedGameTest(Deps::class, SpellModule, *RUNE_SCRIPTS) { deps ->
            val target = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcType())
            check(deps.poison.poison(target, player, 4))
            check(!deps.poison.poison(target, player, 2)) { "A weaker poison should be refused." }
            check(deps.poison.nextDamage(target) == 4)
        }

    @Test
    fun GameTestState.`barrages hit the attackable npcs around the target and no more than nine`() =
        runInjectedGameTest(Deps::class, SpellModule) { deps ->
            val centre = CoordGrid(0, 50, 50, 36, 31)
            val primary = spawnNpc(centre, npcType())
            val near = spawnNpc(centre.translate(1, 1), npcType())
            val friendly = spawnNpc(centre.translate(-1, 0), npcType(attackable = false))
            val far = spawnNpc(centre.translate(2, 0), npcType())

            val around = deps.ancients.targetsAround(primary)

            check(near in around) { "A neighbour should be hit." }
            check(friendly !in around) { "An npc with no Attack option should not be hit." }
            check(far !in around) { "An npc two tiles away should not be hit." }
            check(primary !in around) { "The target is hit separately." }

            // Eight neighbours is everything the 3x3 can hold; more on a tile still stops at nine.
            repeat(12) { spawnNpc(centre.translate(0, 1), npcType()) }
            check(deps.ancients.targetsAround(primary).size == AncientSpells.MAX_TARGETS - 1)
        }
}
