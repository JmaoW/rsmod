package org.rsmod.content.skills.magic.utility

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.config.refs.varps
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
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
import org.rsmod.content.skills.magic.utility.scripts.ArceuusUtilities
import org.rsmod.content.skills.magic.utility.scripts.DisabledSpells
import org.rsmod.content.skills.magic.utility.scripts.Thralls
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class ArceuusSpellsTest {
    class Deps
    @Inject
    constructor(
        val spells: MagicSpellRegistry,
        val thralls: Thralls,
        val random: GameTestScope.VariableGameRandom,
        val npcList: NpcList,
        val npcRegistry: NpcRegistry,
    )

    object SpellModule : AbstractModule() {
        override fun configure() {
            install(MagicSpellModule())
            bind(WorldQueueList::class.java).`in`(Scopes.SINGLETON)
            bind(Thralls::class.java).`in`(Scopes.SINGLETON)
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

        fun npcType() =
            npcTypeFactory.create {
                name = "Test target"
                vislevel = 50
                attack = 100
                strength = 100
                defence = 1
                hitpoints = 500
                defaultMode = NpcMode.None
            }
    }

    private fun GameTestState.runArceuus(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            SpellModule,
            ArceuusUtilities::class,
            Thralls::class,
            DisabledSpells::class,
            *RUNE_SCRIPTS,
        ) { deps ->
            // Npcs left in the world keep their collision in the map the next test shares, and
            // would stand in a walking thrall's way: delete them when the test ends.
            try {
                body(deps)
            } finally {
                clearLeftoverNpcs(deps.npcList, deps.npcRegistry)
            }
        }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private val here = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.prepare(magic: Int, vararg items: Pair<String, Int>) {
        player.teleport(here)
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            val type = obj(name)
            if (type.isStackable) {
                player.inv[slot++] = InvObj(type, count)
            } else {
                repeat(count) { player.inv[slot++] = InvObj(type) }
            }
        }
        player.stats[stats.magic] = magic
        player.statMap.setXP(stats.magic, 0)
        player.stats[stats.hitpoints] = 99
        player.stats[stats.prayer] = 99
        player.withProtectedAccess { vars[varbits.spellbook] = Spellbook.Arceuus.varValue }
    }

    private fun GameTestScope.cast(deps: Deps, spell: ObjType, message: String? = null) {
        val button = deps.spells.getObjSpell(spell)!!.component
        eventBus.publish(IfOverlayButton(player, button, -1, null, IfButtonOp.Op1))
        settle(message)
    }

    private fun GameTestScope.settle(message: String?) {
        repeat(4) {
            if (message != null && runCatching { assertMessageSent(message) }.isSuccess) {
                return
            }
            advance()
        }
        if (message != null) {
            assertMessageSent(message)
        }
    }

    private fun GameTestScope.xp(stat: org.rsmod.game.type.stat.StatType): Double =
        player.statMap.getFineXP(stat) / 10.0

    @Test
    fun GameTestState.`mark of darkness lasts three ticks a level and then fades`() =
        runArceuus { deps ->
            prepare(59, "cosmicrune" to 1, "soulrune" to 1)

            cast(deps, objs.spell_mark_of_darkness)
            check(player.vars[varbits.buff_mark_of_darkness_disabled] == 1) { "Expected the mark." }

            repeat(160) { advance() }
            check(player.vars[varbits.buff_mark_of_darkness_disabled] == 1) { "Faded too early." }
            repeat(40) { advance() }
            check(player.vars[varbits.buff_mark_of_darkness_disabled] == 0) {
                "Expected it to fade."
            }
        }

    @Test
    fun GameTestState.`death charge gives back special attack for the next kill only`() =
        runArceuus { deps ->
            prepare(80, "deathrune" to 2, "bloodrune" to 2, "soulrune" to 2)
            player.withProtectedAccess { vars[varps.sa_energy] = 0 }
            val npc = spawnNpc(here.translate(2, 0), npcType())

            cast(deps, objs.spell_death_charge)
            eventBus.publish(NpcDeathEvents.Killed(npc, player))
            check(player.vars[varps.sa_energy] == 150) {
                "Expected 15%, was ${player.vars[varps.sa_energy]}."
            }

            eventBus.publish(NpcDeathEvents.Killed(npc, player))
            check(player.vars[varps.sa_energy] == 150) { "Only the first kill counts." }

            cast(deps, objs.spell_death_charge, "You can only cast Death Charge once every minute.")
            check(player.count(obj("deathrune")) == 1) { "Only one cast should be paid for." }
        }

    @Test
    fun GameTestState.`vile vigour turns prayer points into run energy`() = runArceuus { deps ->
        prepare(66, "soulrune" to 2, "airrune" to 6)
        player.runEnergy = 5000

        cast(deps, objs.spell_vile_vigour)

        check(player.runEnergy >= 10_000) { "Run energy was ${player.runEnergy}." }
        check(player.prayerLvl == 49) {
            "Fifty points should have been used, left ${player.prayerLvl}."
        }
    }

    @Test
    fun GameTestState.`vile vigour uses what prayer there is`() = runArceuus { deps ->
        prepare(66, "soulrune" to 2, "airrune" to 6)
        player.runEnergy = 5000
        player.statMap.setCurrentLevel(stats.prayer, 20.toByte())

        cast(deps, objs.spell_vile_vigour)

        check(player.runEnergy in 7000..7100) { "Run energy was ${player.runEnergy}." }
        check(player.prayerLvl == 0)
    }

    @Test
    fun GameTestState.`degrime cleans the herbs the level allows for half the experience`() =
        runArceuus { deps ->
            prepare(
                70,
                "unidentified_guam" to 3,
                "unidentified_ranarr" to 1,
                "earthrune" to 4,
                "naturerune" to 2,
            )
            player.stats[stats.herblore] = 3
            player.statMap.setXP(stats.herblore, 0)

            cast(deps, objs.spell_degrime)

            check(player.count(obj("guam_leaf")) == 3) { "Expected three clean guam." }
            check(player.count(obj("unidentified_ranarr")) == 1) { "Ranarr needs level 25." }
            check(xp(stats.herblore) in 3.5..3.8) {
                "Expected 3.75 Herblore experience, was ${xp(stats.herblore)}."
            }
            check(player.statMap.getFineXP(stats.magic) / 10.0 == 83.0)
        }

    @Test
    fun GameTestState.`demonic offering offers three ashes for triple experience`() =
        runArceuus { deps ->
            prepare(
                84,
                "fiendish_ashes" to 2,
                "infernal_ashes" to 2,
                "soulrune" to 1,
                "wrathrune" to 1,
            )
            player.statMap.setCurrentLevel(stats.prayer, 0.toByte())
            player.statMap.setXP(stats.prayer, 0)

            cast(deps, objs.spell_demonic_offering)

            check(player.count(obj("fiendish_ashes")) == 0) { "Both fiendish ashes come first." }
            check(player.count(obj("infernal_ashes")) == 1) {
                "Only one infernal ash fits in three."
            }
            check(xp(stats.prayer) == 3 * (10.0 + 10.0 + 110.0)) {
                "Prayer experience was ${xp(stats.prayer)}."
            }
            check(player.prayerLvl == 4) { "1 + 1 + 2 points, got ${player.prayerLvl}." }
        }

    @Test
    fun GameTestState.`sinister offering offers three bones for triple experience`() =
        runArceuus { deps ->
            prepare(92, "bones" to 5, "bloodrune" to 1, "wrathrune" to 1)
            player.statMap.setCurrentLevel(stats.prayer, 0.toByte())
            player.statMap.setXP(stats.prayer, 0)

            cast(deps, objs.spell_sinister_offering)

            check(player.count(obj("bones")) == 2)
            check(xp(stats.prayer) in 40.4..40.6) { "Prayer experience was ${xp(stats.prayer)}." }
            check(player.prayerLvl == 3)
        }

    private fun GameTestScope.thrallTarget(): Npc {
        val target = spawnNpc(here.translate(2, 0), npcType())
        player.withProtectedAccess { vars[varps.aggressive_npc] = target.uid.packed }
        return target
    }

    @Test
    fun GameTestState.`a thrall hits what the player is fighting until it expires`() =
        runArceuus { deps ->
            prepare(38, "airrune" to 10, "cosmicrune" to 1, "mindrune" to 5)
            val target = thrallTarget()
            repeat(60) { deps.random.next = 1 }

            cast(deps, objs.spell_resurrect_lesser_ghost)

            val thrall = deps.thralls.thrallOf(player)
            check(thrall != null) { "Expected a thrall." }
            check(player.prayerLvl == 97) {
                "A lesser thrall costs 2 Prayer points, left ${player.prayerLvl}."
            }

            check(thrall.coords != player.coords) { "It should appear beside its owner." }
            repeat(20) { advance() }
            check(thrall.mode == NpcMode.None) { "Its walk is the follow walker's, not a mode." }
            check(thrall.faceEntity.isNpc && thrall.faceEntity.npcSlot == target.slotId) {
                "It should look at what its owner is fighting."
            }
            check(target.hitpoints <= 498) {
                "The thrall should have hit the target, it has ${target.hitpoints}."
            }

            // 38 Magic is 38 ticks.
            var ticks = 0
            while (deps.thralls.thrallOf(player) != null && ticks++ < 60) {
                advance()
                if (deps.thralls.thrallOf(player) == null) {
                    assertMessageSent("Your thrall returns to the grave.")
                }
            }
            check(deps.thralls.thrallOf(player) == null) { "The thrall should be gone." }
        }

    @Test
    fun GameTestState.`a zombie thrall walks up to what the player is fighting`() =
        runArceuus { deps ->
            prepare(38, "airrune" to 10, "cosmicrune" to 1, "mindrune" to 5)
            // An open strip of the test map (around `here` some tiles are walled off).
            val open = CoordGrid(0, 50, 50, 20, 35)
            player.teleport(open)
            advance()
            val target = spawnNpc(open.translate(6, 0), npcType())
            player.withProtectedAccess { vars[varps.aggressive_npc] = target.uid.packed }
            repeat(60) { deps.random.next = 1 }

            cast(deps, objs.spell_resurrect_lesser_zombie)
            val thrall = checkNotNull(deps.thralls.thrallOf(player)) { "Expected a thrall." }

            repeat(16) { advance() }
            val at = thrall.coords
            val to = target.coords
            val beside =
                (at.z == to.z && kotlin.math.abs(at.x - to.x) == 1) ||
                    (at.x == to.x && kotlin.math.abs(at.z - to.z) == 1)
            check(beside) { "The zombie should stand beside its target: $at, target $to." }
            check(target.hitpoints < 500) {
                "It should strike once beside it: ${target.hitpoints}."
            }
            check(thrall.faceEntity.isNpc && thrall.faceEntity.npcSlot == target.slotId)

            // The fight is over: it walks back to its owner.
            player.withProtectedAccess { vars[varps.aggressive_npc] = -1 }
            repeat(8) { advance() }
            check(thrall.isWithinDistance(player, 1)) { "Back beside its owner: ${thrall.coords}." }
        }

    @Test
    fun GameTestState.`a new thrall replaces the old one`() = runArceuus { deps ->
        prepare(
            57,
            "airrune" to 10,
            "cosmicrune" to 2,
            "mindrune" to 5,
            "earthrune" to 10,
            "deathrune" to 5,
        )

        cast(deps, objs.spell_resurrect_lesser_ghost)
        val first = deps.thralls.thrallOf(player)
        repeat(20) { advance() }
        cast(deps, objs.spell_resurrect_superior_zombie)
        val second = deps.thralls.thrallOf(player)

        check(first != null && second != null && first !== second)
        check(first.slotId == org.rsmod.game.entity.PathingEntity.INVALID_SLOT) {
            "The first should be gone."
        }
    }

    @Test
    fun GameTestState.`a thrall needs the prayer points`() = runArceuus { deps ->
        prepare(38, "airrune" to 10, "cosmicrune" to 1, "mindrune" to 5)
        player.statMap.setCurrentLevel(stats.prayer, 1.toByte())

        cast(
            deps,
            objs.spell_resurrect_lesser_ghost,
            "You need at least 2 Prayer points to summon that thrall.",
        )

        check(deps.thralls.thrallOf(player) == null)
        check(player.count(obj("cosmicrune")) == 1) { "The runes should be kept." }
    }

    @Test
    fun GameTestState.`the spells the isle has no use for only say so`() = runArceuus { deps ->
        prepare(73, "soulrune" to 4, "naturerune" to 2, "cosmicrune" to 1)

        cast(deps, objs.spell_ward_of_arceuus, DisabledSpells.UNAVAILABLE)

        check(player.count(obj("soulrune")) == 4) { "Nothing should be spent." }
    }
}

/** Deletes every npc in the test world, so its collision is gone before the next test. */
internal fun clearLeftoverNpcs(npcList: NpcList, npcRegistry: NpcRegistry) {
    val all = buildList { for (npc in npcList) add(npc) }
    for (npc in all) {
        npcRegistry.del(npc)
    }
}
