package org.rsmod.content.areas.misc.isleofsouls.tasks

import com.google.inject.AbstractModule
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.events.SpellCast
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.magic.utility.scripts.ArceuusUtilities
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

class LeagueMagicTasksTest {
    private companion object {
        /** The isle's own magic tasks, in the order of `IsleOfSoulsMagicTasks`. */
        val KEYS =
            listOf(
                "wind_strike",
                "low_alchemy",
                "bones_to_bananas",
                "varrock_teleport",
                "enchant_sapphire",
                "telekinetic_grab",
                "superheat",
                "smoke_rush",
                "thrall",
                "mark_of_darkness",
                "kills_25",
                "high_alchemy_100",
                "charge",
                "magic_imbue",
                "tele_group",
                "death_charge",
                "vile_vigour",
                "demonic_offering",
                "blood_barrage",
                "vengeance",
                "plank_make",
                "sinister_offering",
                "kills_250",
                "casts_1000",
                "xp_25m",
                "xp_35m",
            )

        fun taskId(key: String): Int = 1846 + KEYS.indexOf(key)
    }

    class Deps @Inject constructor(val spells: MagicSpellRegistry)

    object SpellModule : AbstractModule() {
        override fun configure() {
            install(MagicSpellModule())
        }
    }

    private fun GameTestState.runMagic(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            SpellModule,
            LeagueMagicTasks::class,
            LeagueSkillTasks::class,
            LeagueGearTasks::class,
        ) { deps ->
            body(deps)
        }

    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    private fun GameTestScope.mine(key: String) = done(taskId(key))

    private fun GameTestScope.spell(deps: Deps, name: String): MagicSpell =
        objTypes.values.firstNotNullOf { obj ->
            deps.spells.getObjSpell(obj)?.takeIf { it.name == name }
        }

    private fun GameTestScope.cast(deps: Deps, name: String) {
        eventBus.publish(SpellCast(player, spell(deps, name)))
    }

    private fun GameTestScope.obj(internal: String): ObjType =
        objTypes.values.first { it.internalName == internal }

    @Test
    fun GameTestState.`casting a spell completes the task named after it`() = runMagic { deps ->
        assertFalse(mine("wind_strike"))
        cast(deps, "Wind Strike")
        assertTrue(mine("wind_strike"))
        assertFalse(mine("low_alchemy"))

        for ((spell, key) in
            listOf(
                "Low Level Alchemy" to "low_alchemy",
                "Bones to Bananas" to "bones_to_bananas",
                "Varrock Teleport" to "varrock_teleport",
                "Lvl-1 Enchant" to "enchant_sapphire",
                "Telekinetic Grab" to "telekinetic_grab",
                "Superheat Item" to "superheat",
                "Charge" to "charge",
                "Magic Imbue" to "magic_imbue",
                "Vengeance" to "vengeance",
                "Plank Make" to "plank_make",
            )) {
            assertFalse(mine(key), key)
            cast(deps, spell)
            assertTrue(mine(key), key)
        }
    }

    @Test
    fun GameTestState.`the first spell of a kind does not complete the harder one`() =
        runMagic { deps ->
            cast(deps, "Charge Water Orb")
            assertFalse(mine("charge"))
            cast(deps, "Vengeance Other")
            assertFalse(mine("vengeance"))
        }

    @Test
    fun GameTestState.`blast wave and surge spells complete their tasks`() = runMagic { deps ->
        cast(deps, "Iban Blast")
        assertFalse(done(434))

        cast(deps, "Wind Blast")
        assertTrue(done(434))
        assertFalse(done(437))

        cast(deps, "Fire Wave")
        assertTrue(done(437))
        assertFalse(done(438))

        cast(deps, "Earth Surge")
        assertTrue(done(438))
    }

    @Test
    fun GameTestState.`ancient spells complete their tasks`() = runMagic { deps ->
        cast(deps, "Smoke Rush")
        assertTrue(mine("smoke_rush"))
        cast(deps, "Ice Rush")
        assertTrue(done(868))
        assertFalse(done(866))
        cast(deps, "Ice Burst")
        cast(deps, "Ice Blitz")
        cast(deps, "Ice Barrage")
        assertTrue(done(866))
        assertTrue(done(869))
        assertTrue(done(872))
        cast(deps, "Blood Barrage")
        assertTrue(mine("blood_barrage"))
    }

    @Test
    fun GameTestState.`teleports with law runes and tele group spells complete their tasks`() =
        runMagic { deps ->
            cast(deps, "Home Teleport")
            assertFalse(done(432))

            cast(deps, "Teleother Lumbridge")
            assertFalse(done(432))

            cast(deps, "Varrock Teleport")
            assertTrue(done(432))
            assertFalse(mine("tele_group"))

            cast(deps, "Tele Group Moonclan")
            assertTrue(mine("tele_group"))
        }

    @Test
    fun GameTestState.`moonclan teleport and the standard book teleports complete`() =
        runMagic { deps ->
            cast(deps, "Moonclan Teleport")
            assertTrue(done(566))
            cast(deps, "Kourend Castle Teleport")
            assertTrue(done(178))
            cast(deps, "Civitas illa Fortis Teleport")
            assertTrue(done(1654))
        }

    @Test
    fun GameTestState.`the lunar book and spellbook swap complete their tasks`() =
        runMagic { deps ->
            cast(deps, "Wind Strike")
            assertFalse(done(565))

            cast(deps, "Spellbook Swap")
            assertTrue(done(565))
            assertTrue(done(568))
        }

    @Test
    fun GameTestState.`logging in with the lunar book completes its task`() = runMagic {
        player.withProtectedAccess { vars[varbits.spellbook] = Spellbook.Lunars.varValue }
        eventBus.publish(SessionStateEvent.Login(player))
        assertTrue(done(565))
    }

    @Test
    fun GameTestState.`the god spells complete their tasks`() = runMagic { deps ->
        cast(deps, "Saradomin Strike")
        cast(deps, "Claws of Guthix")
        assertFalse(done(1025))
        cast(deps, "Flames of Zamorak")
        assertTrue(done(1023))
        assertTrue(done(1024))
        assertTrue(done(1025))
    }

    @Test
    fun GameTestState.`arceuus spells and thralls complete their tasks`() = runMagic { deps ->
        cast(deps, "Resurrect Lesser Ghost")
        assertTrue(mine("thrall"))
        cast(deps, "Mark of Darkness")
        cast(deps, "Death Charge")
        cast(deps, "Vile Vigour")
        cast(deps, "Demonic Offering")
        cast(deps, "Sinister Offering")
        for (key in
            listOf(
                "mark_of_darkness",
                "death_charge",
                "vile_vigour",
                "demonic_offering",
                "sinister_offering",
            )) {
            assertTrue(mine(key), key)
        }
    }

    @Test
    fun GameTestState.`a full degrime needs twenty six herbs`() = runMagic {
        eventBus.publish(ArceuusUtilities.HerbsCleaned(player, 25))
        assertFalse(done(1124))
        eventBus.publish(ArceuusUtilities.HerbsCleaned(player, 26))
        assertTrue(done(1124))
    }

    @Test
    fun GameTestState.`high level alchemy counts a hundred casts`() = runMagic { deps ->
        cast(deps, "High Level Alchemy")
        assertTrue(done(435))
        repeat(98) { cast(deps, "High Level Alchemy") }
        assertFalse(mine("high_alchemy_100"))
        cast(deps, "High Level Alchemy")
        assertTrue(mine("high_alchemy_100"))
    }

    @Test
    fun GameTestState.`a thousand casts of any spell complete the task`() = runMagic { deps ->
        val spell = spell(deps, "Wind Strike")
        repeat(999) { eventBus.publish(SpellCast(player, spell)) }
        assertFalse(mine("casts_1000"))
        eventBus.publish(SpellCast(player, spell))
        assertTrue(mine("casts_1000"))
    }

    private fun GameTestScope.kill() {
        val npc =
            spawnNpc(
                CoordGrid(0, 50, 50, 0, 0),
                npcTypeFactory.create {
                    name = "Test target"
                    vislevel = 10
                    hitpoints = 10
                    defaultMode = NpcMode.None
                },
            )
        eventBus.publish(NpcDeathEvents.Killed(npc, player))
    }

    @Test
    fun GameTestState.`kills only count shortly after a combat spell`() = runMagic { deps ->
        kill()
        assertEquals(0, player.vars[LeagueProgressVarps.magic_kills_25])

        cast(deps, "Fire Strike")
        kill()
        assertEquals(1, player.vars[LeagueProgressVarps.magic_kills_25])

        repeat(12) { advance() }
        kill()
        assertEquals(1, player.vars[LeagueProgressVarps.magic_kills_25])
    }

    @Test
    fun GameTestState.`lunar and utility spells do not make a kill a magic kill`() =
        runMagic { deps ->
            cast(deps, "Low Level Alchemy")
            cast(deps, "Heal Other")
            kill()
            assertEquals(0, player.vars[LeagueProgressVarps.magic_kills_25])
        }

    @Test
    fun GameTestState.`twenty five and two hundred and fifty kills complete their tasks`() =
        runMagic { deps ->
            cast(deps, "Wind Strike")
            repeat(24) { kill() }
            assertFalse(mine("kills_25"))
            kill()
            assertTrue(mine("kills_25"))
            assertFalse(mine("kills_250"))

            repeat(225) { kill() }
            assertTrue(mine("kills_250"))
        }

    @Test
    fun GameTestState.`magic level and experience milestones complete`() = runMagic { deps ->
        player.stats[stats.magic] = 99
        cast(deps, "Wind Strike")
        assertTrue(done(236))
        assertFalse(mine("xp_25m"))

        player.statMap.setXP(stats.magic, 25_000_000)
        cast(deps, "Wind Strike")
        assertTrue(mine("xp_25m"))
        assertFalse(mine("xp_35m"))

        player.statMap.setXP(stats.magic, 50_000_000)
        cast(deps, "Wind Strike")
        assertTrue(mine("xp_35m"))
        assertTrue(done(1488))
    }

    @Test
    fun GameTestState.`elemental staffs complete the equip tasks`() = runMagic {
        fun wear(internal: String) {
            val staff = obj(internal)
            player.worn[Wearpos.RightHand.slot] = InvObj(staff)
            eventBus.publish(
                HeldEquipEvents.WearposChange(player, Wearpos.RightHand, objTypes[staff])
            )
        }

        wear("staff_of_air")
        assertTrue(done(430))
        assertFalse(done(433))

        wear("mystic_fire_staff")
        assertTrue(done(433))
    }
}
