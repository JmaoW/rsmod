package org.rsmod.content.skills.magic.utility

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.events.interact.ObjTEvents
import org.rsmod.api.player.events.interact.PlayerTEvents
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.IfOverlayButtonT
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
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.magic.utility.scripts.Alchemy
import org.rsmod.content.skills.magic.utility.scripts.BonesToFruit
import org.rsmod.content.skills.magic.utility.scripts.ChargeSpell
import org.rsmod.content.skills.magic.utility.scripts.DisabledSpells
import org.rsmod.content.skills.magic.utility.scripts.Enchanting
import org.rsmod.content.skills.magic.utility.scripts.Superheat
import org.rsmod.content.skills.magic.utility.scripts.TeleOther
import org.rsmod.content.skills.magic.utility.scripts.TelekineticGrab
import org.rsmod.content.skills.magic.utility.scripts.TeleportDestinations
import org.rsmod.content.skills.magic.utility.scripts.Teleports
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class UtilitySpellsTest {
    class Deps
    @Inject
    constructor(val spells: MagicSpellRegistry, val destinations: TeleportDestinations)

    /** Tests do not install plugin modules, and the registry must be initialised by its module. */
    object SpellModule : AbstractModule() {
        override fun configure() {
            install(MagicSpellModule())
            // The rune repositories are filled by their scripts and read by the rune manager, so
            // both must see the same instance.
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

        /** The scripts that fill the rune repositories. */
        val RUNE_SCRIPTS =
            arrayOf(
                ComboRuneScript::class,
                CompactRuneScript::class,
                FakeRuneScript::class,
                RuneSubstituteScript::class,
                StaffSubstituteScript::class,
                UnlimitedRuneScript::class,
            )
    }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(magic: Int, vararg items: Pair<String, Int>) {
        player.teleport(CoordGrid(0, 50, 50, 34, 31))
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
    }

    /**
     * Button events run their script at once, and messages only live until the next tick, so the
     * message is looked for before any tick and then after each one.
     */
    private fun GameTestScope.settle(message: String? = null) {
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

    private fun GameTestScope.castOn(
        deps: Deps,
        spell: ObjType,
        item: String,
        message: String? = null,
    ) {
        val component = Component(deps.spells.getObjSpell(spell)!!.component.packed)
        val inv = Component(components.inv_items.packed)
        val slot = player.inv.indexOfFirst { it?.id == obj(item).id }
        check(slot >= 0) { "Expected $item in the inventory." }
        eventBus.publish(IfOverlayButtonT(player, -1, null, slot, obj(item), component, inv))
        settle(message)
    }

    private fun GameTestScope.cast(deps: Deps, spell: ObjType, message: String? = null) {
        val button = deps.spells.getObjSpell(spell)!!.component
        eventBus.publish(IfOverlayButton(player, button, -1, null, IfButtonOp.Op1))
        settle(message)
    }

    private fun GameTestScope.magicXp(): Double = player.statMap.getFineXP(stats.magic) / 10.0

    @Test
    fun GameTestState.`high alchemy pays sixty percent of the value and uses the runes`() =
        runInjectedGameTest(Deps::class, SpellModule, Alchemy::class, *RUNE_SCRIPTS) { deps ->
            prepare(55, "firerune" to 5, "naturerune" to 1, "gold_ring" to 1)

            castOn(deps, objs.spell_high_alchemy, "gold_ring")

            val expected = obj("gold_ring").cost * 60 / 100
            check(expected > 0) { "The test item needs a value." }
            check(player.count(obj("coins")) == expected) {
                "Expected $expected coins, had ${player.count(obj("coins"))}."
            }
            check(player.count(obj("gold_ring")) == 0) { "Expected the ring to be gone." }
            check(player.count(obj("firerune")) == 0) { "Expected the fire runes to be used." }
            check(player.count(obj("naturerune")) == 0) { "Expected the nature rune to be used." }
            check(magicXp() == 65.0) { "Expected 65 experience, was ${magicXp()}." }
        }

    @Test
    fun GameTestState.`low alchemy pays forty percent`() =
        runInjectedGameTest(Deps::class, SpellModule, Alchemy::class, *RUNE_SCRIPTS) { deps ->
            prepare(21, "firerune" to 3, "naturerune" to 1, "gold_ring" to 1)

            castOn(deps, objs.spell_low_alchemy, "gold_ring")

            val expected = obj("gold_ring").cost * 40 / 100
            check(player.count(obj("coins")) == expected) { "Expected $expected coins." }
            check(magicXp() == 31.0) { "Expected 31 experience, was ${magicXp()}." }
        }

    @Test
    fun GameTestState.`alchemy needs the magic level and takes nothing without it`() =
        runInjectedGameTest(Deps::class, SpellModule, Alchemy::class, *RUNE_SCRIPTS) { deps ->
            prepare(54, "firerune" to 5, "naturerune" to 1, "gold_ring" to 1)

            castOn(
                deps,
                objs.spell_high_alchemy,
                "gold_ring",
                "Your Magic level is not high enough for this spell.",
            )

            check(player.count(obj("gold_ring")) == 1) { "The ring should be kept." }
            check(player.count(obj("firerune")) == 5) { "The runes should be kept." }
        }

    @Test
    fun GameTestState.`alchemy refuses coins`() =
        runInjectedGameTest(Deps::class, SpellModule, Alchemy::class, *RUNE_SCRIPTS) { deps ->
            prepare(55, "firerune" to 5, "naturerune" to 1, "coins" to 100)

            castOn(deps, objs.spell_high_alchemy, "coins", "You cannot cast this spell on coins.")

            check(player.count(obj("coins")) == 100)
            check(player.count(obj("firerune")) == 5)
        }

    @Test
    fun GameTestState.`bones to bananas converts every bone`() =
        runInjectedGameTest(Deps::class, SpellModule, BonesToFruit::class, *RUNE_SCRIPTS) { deps ->
            prepare(
                15,
                "earthrune" to 2,
                "waterrune" to 2,
                "naturerune" to 1,
                "bones" to 3,
                "big_bones" to 2,
            )

            cast(deps, objs.spell_bones_to_bananas)

            check(player.count(obj("banana")) == 5) { "Expected 5 bananas." }
            check(player.count(obj("bones")) == 0 && player.count(obj("big_bones")) == 0)
            check(magicXp() == 25.0) { "Expected 25 experience, was ${magicXp()}." }
        }

    @Test
    fun GameTestState.`bones to bananas with no bones keeps the runes`() =
        runInjectedGameTest(Deps::class, SpellModule, BonesToFruit::class, *RUNE_SCRIPTS) { deps ->
            prepare(15, "earthrune" to 2, "waterrune" to 2, "naturerune" to 1)

            cast(deps, objs.spell_bones_to_bananas, "You do not have any bones to convert.")

            check(player.count(obj("earthrune")) == 2)
        }

    @Test
    fun GameTestState.`superheat makes steel from iron ore and coal`() =
        runInjectedGameTest(Deps::class, SpellModule, Superheat::class, *RUNE_SCRIPTS) { deps ->
            prepare(43, "firerune" to 4, "naturerune" to 1, "iron_ore" to 1, "coal" to 2)
            player.stats[stats.smithing] = 30
            player.statMap.setXP(stats.smithing, 0)

            castOn(deps, objs.spell_superheat, "iron_ore")

            check(player.count(obj("steel_bar")) == 1) { "Expected a steel bar." }
            check(player.count(obj("coal")) == 0 && player.count(obj("iron_ore")) == 0)
            check(magicXp() == 53.0) { "Expected 53 magic experience, was ${magicXp()}." }
            val smithing = player.statMap.getFineXP(stats.smithing) / 10.0
            check(smithing == 17.5) { "Expected 17.5 smithing experience, was $smithing." }
        }

    @Test
    fun GameTestState.`superheat makes an iron bar when there is no coal`() =
        runInjectedGameTest(Deps::class, SpellModule, Superheat::class, *RUNE_SCRIPTS) { deps ->
            prepare(43, "firerune" to 4, "naturerune" to 1, "iron_ore" to 1)
            player.stats[stats.smithing] = 15

            castOn(deps, objs.spell_superheat, "iron_ore")

            check(player.count(obj("iron_bar")) == 1) { "Expected an iron bar." }
        }

    @Test
    fun GameTestState.`superheat needs the smithing level`() =
        runInjectedGameTest(Deps::class, SpellModule, Superheat::class, *RUNE_SCRIPTS) { deps ->
            prepare(43, "firerune" to 4, "naturerune" to 1, "iron_ore" to 1)
            player.stats[stats.smithing] = 14

            castOn(
                deps,
                objs.spell_superheat,
                "iron_ore",
                "You need a Smithing level of 15 to make a iron bar.",
            )

            check(player.count(obj("iron_ore")) == 1 && player.count(obj("firerune")) == 4)
        }

    @Test
    fun GameTestState.`level one enchant turns a sapphire ring into a ring of recoil`() =
        runInjectedGameTest(Deps::class, SpellModule, Enchanting::class, *RUNE_SCRIPTS) { deps ->
            prepare(7, "waterrune" to 1, "cosmicrune" to 1, "sapphire_ring" to 1)

            castOn(deps, objs.spell_enchant_lvl_1, "sapphire_ring")

            check(player.count(obj("ring_of_recoil")) == 1) { "Expected a ring of recoil." }
            check(magicXp() == 17.5) { "Expected 17.5 experience, was ${magicXp()}." }
        }

    @Test
    fun GameTestState.`an enchant spell refuses jewellery of another gem`() =
        runInjectedGameTest(Deps::class, SpellModule, Enchanting::class, *RUNE_SCRIPTS) { deps ->
            prepare(27, "airrune" to 3, "cosmicrune" to 1, "sapphire_ring" to 1)

            castOn(deps, objs.spell_enchant_lvl_2, "sapphire_ring")

            check(player.count(obj("sapphire_ring")) == 1) { "The ring should be kept." }
            check(player.count(obj("cosmicrune")) == 1) { "The runes should be kept." }
        }

    @Test
    fun GameTestState.`a teleport spell takes the caster to its destination`() =
        runInjectedGameTest(Deps::class, SpellModule, Teleports::class, *RUNE_SCRIPTS) { deps ->
            prepare(25, "lawrune" to 1, "airrune" to 3, "firerune" to 1)
            val spell = deps.spells.getObjSpell(objs.spell_varrock_teleport)!!
            val destination = objTypes[objs.spell_varrock_teleport].param(params.spell_telecoord)

            cast(deps, objs.spell_varrock_teleport)
            repeat(6) { advance() }

            check(player.coords == destination) { "Expected $destination, was ${player.coords}." }
            check(player.count(obj("lawrune")) == 0) { "Expected the law rune to be used." }
            check(magicXp() == spell.castXp) {
                "Expected ${spell.castXp} experience, was ${magicXp()}."
            }
        }

    @Test
    fun GameTestState.`a teleport spell without the runes stays put`() =
        runInjectedGameTest(Deps::class, SpellModule, Teleports::class, *RUNE_SCRIPTS) { deps ->
            prepare(25, "airrune" to 3, "firerune" to 1)
            val start = player.coords

            cast(deps, objs.spell_varrock_teleport)
            repeat(6) { advance() }

            check(player.coords == start) { "The caster should not have moved." }
        }

    @Test
    fun GameTestState.`disabled spells only say so and cost nothing`() =
        runInjectedGameTest(Deps::class, SpellModule, DisabledSpells::class, *RUNE_SCRIPTS) { deps
            ->
            prepare(45, "lawrune" to 1)

            cast(deps, objs.spell_teleport_to_target, DisabledSpells.UNAVAILABLE)

            check(player.count(obj("lawrune")) == 1)
        }

    @Test
    fun GameTestState.`charge lasts seven minutes and then fades`() =
        runInjectedGameTest(Deps::class, SpellModule, ChargeSpell::class, *RUNE_SCRIPTS) { deps ->
            prepare(80, "airrune" to 3, "firerune" to 3, "bloodrune" to 3)

            cast(deps, objs.spell_charge)

            check(player.vars[varbits.buff_charge_spell_disabled] == 1) { "Expected a charge." }
            check(magicXp() == 180.0) { "Expected 180 experience, was ${magicXp()}." }

            // The cast has already spent a few ticks settling.
            repeat(680) { advance() }
            check(player.vars[varbits.buff_charge_spell_disabled] == 1) { "Faded too early." }
            repeat(40) { if (player.vars[varbits.buff_charge_spell_disabled] != 0) advance() }
            check(player.vars[varbits.buff_charge_spell_disabled] == 0) { "Expected it to end." }
        }

    private fun GameTestScope.grab(
        deps: Deps,
        item: String,
        distance: Int,
        message: String? = null,
    ) {
        val spell = deps.spells.getObjSpell(objs.spell_tele_grab)!!
        val type = obj(item)
        val at = player.coords.translateX(distance)
        val ground = objRepo.add(type, at, duration = 100)
        player.withProtectedAccess {
            eventBus.publish(this, ObjTEvents.Cast(ground, type, -1, spell.component))
        }
        settle(message)
    }

    @Test
    fun GameTestState.`telekinetic grab takes a ground item into the inventory`() =
        runInjectedGameTest(Deps::class, SpellModule, TelekineticGrab::class, *RUNE_SCRIPTS) { deps
            ->
            prepare(33, "airrune" to 1, "lawrune" to 1)

            grab(deps, "bronze_bar", distance = 5)

            check(player.count(obj("bronze_bar")) == 1) { "Expected the bar in the inventory." }
            check(player.count(obj("lawrune")) == 0) { "Expected the law rune to be used." }
            check(magicXp() == 43.0) { "Expected 43 experience, was ${magicXp()}." }
        }

    @Test
    fun GameTestState.`telekinetic grab cannot reach past ten tiles`() =
        runInjectedGameTest(Deps::class, SpellModule, TelekineticGrab::class, *RUNE_SCRIPTS) { deps
            ->
            prepare(33, "airrune" to 1, "lawrune" to 1)

            grab(deps, "bronze_bar", distance = 11, message = "That's too far away.")

            check(player.count(obj("bronze_bar")) == 0)
            check(player.count(obj("lawrune")) == 1) { "The runes should be kept." }
        }

    @Test
    fun GameTestState.`telekinetic grab with a full inventory keeps the runes`() =
        runInjectedGameTest(Deps::class, SpellModule, TelekineticGrab::class, *RUNE_SCRIPTS) { deps
            ->
            prepare(33, "airrune" to 1, "lawrune" to 1)
            player.fillInv()
            player.inv[0] = InvObj(obj("airrune"), 1)
            player.inv[1] = InvObj(obj("lawrune"), 1)

            grab(
                deps,
                "bronze_bar",
                distance = 3,
                message = "You don't have enough inventory space to hold that item.",
            )

            check(player.count(obj("lawrune")) == 1) { "The runes should be kept." }
        }

    @Test
    fun GameTestState.`teleother sends the target to the spell's destination`() =
        runInjectedGameTest(Deps::class, SpellModule, TeleOther::class, *RUNE_SCRIPTS) { deps ->
            prepare(74, "lawrune" to 1, "earthrune" to 1, "soulrune" to 1)
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            bob.teleport(player.coords.translateX(2))
            val destination = CoordGrid(0, 50, 51, 20, 20)
            deps.destinations.override(objs.spell_teleother_lumbridge) { destination }
            val spell = deps.spells.getObjSpell(objs.spell_teleother_lumbridge)!!

            player.withProtectedAccess {
                eventBus.publish(this, PlayerTEvents.Ap(bob, -1, null, spell.component))
            }
            repeat(8) { advance() }

            check(bob.coords == destination) { "Expected $destination, was ${bob.coords}." }
            check(player.coords != destination) { "The caster should stay where they were." }
            check(player.count(obj("lawrune")) == 0) { "Expected the law rune to be used." }
        }
}
