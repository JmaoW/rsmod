package org.rsmod.content.interfaces.combat.tab

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.autocast.scripts.AutocastScript
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
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.interfaces.combat.tab.configs.combat_components
import org.rsmod.content.interfaces.combat.tab.configs.combat_interfaces
import org.rsmod.content.interfaces.combat.tab.configs.combat_varps
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.obj.Wearpos

class AutocastPickerTest {
    class Deps
    @Inject
    constructor(val spells: MagicSpellRegistry, val launcher: ProtectedAccessLauncher)

    object SpellModule : AbstractModule() {
        override fun configure() {
            install(MagicSpellModule())
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

        val SCRIPTS =
            arrayOf(
                CombatTabScript::class,
                AutocastScript::class,
                ComboRuneScript::class,
                CompactRuneScript::class,
                FakeRuneScript::class,
                RuneSubstituteScript::class,
                StaffSubstituteScript::class,
                UnlimitedRuneScript::class,
            )

        val RUNES =
            listOf(
                "airrune",
                "waterrune",
                "earthrune",
                "firerune",
                "mindrune",
                "bodyrune",
                "chaosrune",
                "deathrune",
                "bloodrune",
                "soulrune",
                "lawrune",
                "naturerune",
                "cosmicrune",
                "wrathrune",
            )
    }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(book: Spellbook = Spellbook.Standard) {
        player.clearInv()
        for ((slot, rune) in RUNES.withIndex()) {
            player.inv[slot] = InvObj(obj(rune), 10_000)
        }
        player.worn[Wearpos.RightHand.slot] = InvObj(objs.air_staff)
        player.stats[stats.magic] = 99
        player.withProtectedAccess { vars[varbits.spellbook] = book.varValue }
    }

    @OptIn(InternalApi::class)
    private fun GameTestScope.choose(deps: Deps, defensive: Boolean, autocastId: Int) {
        val button =
            if (defensive) combat_components.autocast_defensive
            else combat_components.autocast_normal
        eventBus.publish(IfOverlayButton(player, button, -1, null, IfButtonOp.Op1))
        advance()
        assertModalOpen(combat_interfaces.autocast)
        val click =
            IfModalButton(combat_components.autocast_spells, autocastId, null, IfButtonOp.Op1)
        deps.launcher.launchLenient(player) { eventBus.publish(this, click) }
        advance()
    }

    private val org.rsmod.game.entity.Player.chosen
        get() = vars[varbits.autocast_spell]

    private val org.rsmod.game.entity.Player.enabled
        get() = vars[varbits.autocast_set]

    private val org.rsmod.game.entity.Player.defensive
        get() = vars[varbits.autocast_defmode]

    @Test
    fun GameTestState.`choosing a spell sets it as autocast and closes the picker`() =
        runInjectedGameTest(Deps::class, SpellModule, *SCRIPTS) { deps ->
            prepare()
            choose(deps, defensive = false, autocastId = 1)
            assertModalNotOpen(combat_interfaces.autocast)
            assertEquals(1, player.chosen)
            assertEquals(1, player.enabled)
            assertEquals(0, player.defensive)
        }

    @Test
    fun GameTestState.`the defensive button sets defensive casting`() =
        runInjectedGameTest(Deps::class, SpellModule, *SCRIPTS) { deps ->
            prepare()
            choose(deps, defensive = true, autocastId = 2)
            assertEquals(2, player.chosen)
            assertEquals(1, player.defensive)
            // The next pick from the normal button is not defensive.
            choose(deps, defensive = false, autocastId = 3)
            assertEquals(3, player.chosen)
            assertEquals(0, player.defensive)
        }

    @Test
    fun GameTestState.`cancel turns autocast off`() =
        runInjectedGameTest(Deps::class, SpellModule, *SCRIPTS) { deps ->
            prepare()
            choose(deps, defensive = false, autocastId = 1)
            choose(deps, defensive = false, autocastId = 0)
            assertEquals(0, player.chosen)
            assertEquals(0, player.enabled)
        }

    @Test
    fun GameTestState.`a spell of another spellbook is refused`() =
        runInjectedGameTest(Deps::class, SpellModule, *SCRIPTS) { deps ->
            prepare(Spellbook.Standard)
            // 31 is the first Ancient Magicks spell.
            choose(deps, defensive = false, autocastId = 31)
            assertEquals(0, player.enabled)
        }

    @Test
    fun GameTestState.`every autocast spell of the three combat books can be chosen`() =
        runInjectedGameTest(Deps::class, SpellModule, *SCRIPTS) { deps ->
            val failed = mutableListOf<String>()
            for (id in 1..58) {
                val spell = deps.spells.getAutocastSpell(id) ?: continue
                prepare(checkNotNull(spell.spellbook))
                // Spells that need a special staff cannot be picked with a staff of air.
                val staff = obj("staff_of_air")
                player.worn[Wearpos.RightHand.slot] = InvObj(staff)
                choose(deps, defensive = false, autocastId = id)
                if (player.chosen != id) {
                    failed += "${spell.name} ($id)"
                }
            }
            // Only the staff restricted spells are allowed to fail with a plain staff.
            assertEquals(
                emptyList<String>(),
                failed.filterNot { name ->
                    listOf(
                            "Crumble Undead",
                            "Iban Blast",
                            "Magic Dart",
                            "Flames of Zamorak",
                            "Saradomin Strike",
                            "Claws of Guthix",
                        )
                        .any { name.startsWith(it) }
                },
            )
        }

    @Test
    fun GameTestState.`the staff restricted spells work with their staff`() =
        runInjectedGameTest(Deps::class, SpellModule, *SCRIPTS) { deps ->
            val cases =
                listOf(
                    Triple(objs.ibans_staff, 47, "Iban Blast"),
                    Triple(objs.slayers_staff, 17, "Crumble Undead"),
                    Triple(objs.slayers_staff, 18, "Magic Dart"),
                    Triple(objs.staff_of_light, 52, "Saradomin Strike"),
                    Triple(objs.staff_of_balance, 19, "Claws of Guthix"),
                    Triple(objs.staff_of_the_dead, 20, "Flames of Zamorak"),
                )
            for ((staff, id, name) in cases) {
                prepare()
                player.worn[Wearpos.RightHand.slot] = InvObj(staff)
                choose(deps, defensive = false, autocastId = id)
                assertEquals(id, player.chosen, "$name was not set with its staff")
            }
        }

    private fun GameTestScope.openedLayout(
        book: Spellbook,
        staff: org.rsmod.game.type.obj.ObjType,
    ): Int {
        player.ifClose(eventBus)
        prepare(book)
        advance()
        player.worn[Wearpos.RightHand.slot] = InvObj(staff)
        eventBus.publish(
            IfOverlayButton(player, combat_components.autocast_normal, -1, null, IfButtonOp.Op1)
        )
        advance()
        return player.vars[combat_varps.autocast_layout]
    }

    @Test
    fun GameTestState.`the picker layout varp tells the client which spells to draw`() =
        runInjectedGameTest(Deps::class, SpellModule, *SCRIPTS) {
            assertEquals(-1, openedLayout(Spellbook.Standard, objs.air_staff))
            assertEquals(4675, openedLayout(Spellbook.Ancients, objs.air_staff))
            assertEquals(9013, openedLayout(Spellbook.Arceuus, objs.air_staff))
            assertEquals(1409, openedLayout(Spellbook.Standard, objs.ibans_staff))
            assertEquals(11791, openedLayout(Spellbook.Standard, objs.staff_of_light))
        }
}
