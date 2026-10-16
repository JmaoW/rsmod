package org.rsmod.content.skills.magic.utility

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Scopes
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.config.refs.varps
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.inv.LocUOpScript
import org.rsmod.api.player.events.interact.PlayerTEvents
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.events.SpellCast
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
import org.rsmod.content.skills.herblore.configs.PotionEffect
import org.rsmod.content.skills.herblore.configs.Potions
import org.rsmod.content.skills.magic.utility.scripts.LunarTeamSpells
import org.rsmod.content.skills.magic.utility.scripts.LunarUtilities
import org.rsmod.content.skills.magic.utility.scripts.TeleportDestinations
import org.rsmod.content.skills.magic.utility.scripts.Teleports
import org.rsmod.content.skills.magic.utility.scripts.Vengeance
import org.rsmod.content.skills.runecraft.scripts.Runecrafting
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class LunarTeamSpellsTest {
    class Deps
    @Inject
    constructor(
        val spells: MagicSpellRegistry,
        val destinations: TeleportDestinations,
        val utilities: LunarUtilities,
        val vengeance: Vengeance,
        val random: GameTestScope.VariableGameRandom,
    )

    object SpellModule : AbstractModule() {
        /**
         * The group teleport has no destination in the cache, so the tests give it one up front.
         */
        @Provides
        @Singleton
        fun destinations(objTypes: ObjTypeList): TeleportDestinations =
            TeleportDestinations(objTypes).also {
                it.override(objs.spell_catherby_telegroup) { GROUP_DESTINATION }
            }

        override fun configure() {
            install(MagicSpellModule())
            bind(WorldQueueList::class.java).`in`(Scopes.SINGLETON)
            // The tests ask the scripts about their state, so they must be the scripts that run.
            bind(Vengeance::class.java).`in`(Scopes.SINGLETON)
            bind(LunarUtilities::class.java).`in`(Scopes.SINGLETON)
            for (repository in RUNE_REPOSITORIES) {
                bind(repository.java).`in`(Scopes.SINGLETON)
            }
        }
    }

    private companion object {
        val GROUP_DESTINATION = CoordGrid(0, 50, 51, 20, 20)

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

    private fun GameTestState.runLunar(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            SpellModule,
            LunarTeamSpells::class,
            LunarUtilities::class,
            Vengeance::class,
            Teleports::class,
            Runecrafting::class,
            LocUOpScript::class,
            *RUNE_SCRIPTS,
        ) { deps ->
            body(deps)
        }

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private val here = CoordGrid(0, 50, 50, 34, 31)

    /** The player at [here] with [items], on the Lunar spellbook with Magic level [magic]. */
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
        player.withProtectedAccess { vars[varbits.spellbook] = Spellbook.Lunars.varValue }
    }

    private fun GameTestScope.teammate(name: String, dx: Int, dz: Int = 0): Player {
        val mate =
            registerPlayer(
                player =
                    Player().apply {
                        username = name
                        displayName = name
                    }
            )
        mate.teleport(here.translate(dx, dz))
        mate.stats[stats.hitpoints] = 99
        return mate
    }

    private fun GameTestScope.castOnPlayer(deps: Deps, spell: ObjType, target: Player) {
        val button = deps.spells.getObjSpell(spell)!!.component
        player.withProtectedAccess {
            eventBus.publish(this, PlayerTEvents.Ap(target, -1, null, button))
        }
        settle(null)
    }

    private fun GameTestScope.cast(deps: Deps, spell: ObjType, message: String? = null) {
        val button = deps.spells.getObjSpell(spell)!!.component
        eventBus.publish(IfOverlayButton(player, button, -1, null, IfButtonOp.Op1))
        settle(message)
    }

    private fun GameTestScope.castOnItem(
        deps: Deps,
        spell: ObjType,
        slot: Int,
        message: String? = null,
    ) {
        val component = Component(deps.spells.getObjSpell(spell)!!.component.packed)
        val inv = Component(org.rsmod.api.config.refs.components.inv_items.packed)
        val held = objTypes[player.inv[slot]!!]
        eventBus.publish(IfOverlayButtonT(player, -1, null, slot, held, component, inv))
        settle(message)
    }

    /** Button events run their script at once, so a message is looked for before any tick. */
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

    @Test
    fun GameTestState.`heal other gives a teammate hitpoints from the caster`() = runLunar { deps ->
        prepare(92, "astralrune" to 3, "lawrune" to 3, "bloodrune" to 1)
        val bob = teammate("bob", 1)
        bob.statMap.setCurrentLevel(stats.hitpoints, 40.toByte())

        castOnPlayer(deps, objs.spell_heal_other, bob)

        check(bob.hitpoints == 99) {
            "Bob was missing 59 and 74 is on offer, bob has ${bob.hitpoints}."
        }
        check(player.hitpoints == 40) {
            "The caster gives up what bob got, has ${player.hitpoints}."
        }
        check(player.statMap.getFineXP(stats.magic) / 10.0 == 101.0)
    }

    @Test
    fun GameTestState.`heal other needs eleven percent of the hitpoints left`() = runLunar { deps ->
        prepare(92, "astralrune" to 3, "lawrune" to 3, "bloodrune" to 1)
        val bob = teammate("bob", 1)
        bob.statMap.setCurrentLevel(stats.hitpoints, 40.toByte())
        player.statMap.setCurrentLevel(stats.hitpoints, 10.toByte())

        castOnPlayer(deps, objs.spell_heal_other, bob)

        check(bob.hitpoints == 40) { "Nothing should have been given." }
        check(player.count(obj("lawrune")) == 3) { "The runes should be kept." }
    }

    @Test
    fun GameTestState
        .`heal group splits the caster's hitpoints between nearby injured teammates`() =
        runLunar { deps ->
            prepare(95, "astralrune" to 4, "lawrune" to 6, "bloodrune" to 3)
            val near1 = teammate("bob", 1)
            val near2 = teammate("carol", 0, 1)
            val far = teammate("dave", 3)
            for (mate in listOf(near1, near2, far)) {
                mate.statMap.setCurrentLevel(stats.hitpoints, 10.toByte())
            }
            player.statMap.setCurrentLevel(stats.hitpoints, 80.toByte())

            cast(deps, objs.spell_heal_group)

            // 75% of 80 is 60; with 5 more that is 65 between two, 32 each.
            check(near1.hitpoints == 42 && near2.hitpoints == 42) {
                "Expected 42 each, got ${near1.hitpoints} and ${near2.hitpoints}."
            }
            check(far.hitpoints == 10) { "A teammate three tiles away is out of range." }
            check(player.hitpoints == 20) { "The caster gives up 60, has ${player.hitpoints}." }
        }

    @Test
    fun GameTestState.`energy transfer gives a teammate run energy and special attack`() =
        runLunar { deps ->
            prepare(91, "astralrune" to 3, "lawrune" to 2, "naturerune" to 1)
            val bob = teammate("bob", 1)
            bob.runEnergy = 100
            player.withProtectedAccess { vars[varps.sa_energy] = 1000 }
            bob.withProtectedAccess { vars[varps.sa_energy] = 0 }

            castOnPlayer(deps, objs.spell_energy_transfer, bob)

            check(bob.runEnergy == 10_000) { "Run energy was ${bob.runEnergy}." }
            check(bob.vars[varps.sa_energy] == 1000)
            check(player.vars[varps.sa_energy] == 0) { "The caster pays all their special attack." }
            check(player.hitpoints == 89) { "The caster pays 10 hitpoints." }
        }

    @Test
    fun GameTestState.`stat spy lists a teammate's levels`() = runLunar { deps ->
        prepare(75, "astralrune" to 2, "cosmicrune" to 2, "bodyrune" to 5)
        val bob = teammate("bob", 1)

        val button = deps.spells.getObjSpell(objs.spell_stat_spy)!!.component
        player.withProtectedAccess {
            eventBus.publish(this, PlayerTEvents.Ap(bob, -1, null, button))
        }
        assertMessageSent("bob's levels (current/base):")
    }

    @Test
    fun GameTestState.`stat restore pot share restores a teammate and uses a dose`() =
        runLunar { deps ->
            prepare(81, "astralrune" to 2, "waterrune" to 10, "earthrune" to 10)
            val potion = Potions.all.first { it.effect is PotionEffect.PrayerPoints }
            player.inv[10] = InvObj(potion.dose(4), 1)
            val bob = teammate("bob", 1)
            bob.stats[stats.prayer] = 99
            bob.statMap.setCurrentLevel(stats.prayer, 0.toByte())
            player.stats[stats.prayer] = 99
            player.statMap.setCurrentLevel(stats.prayer, 0.toByte())

            castOnItem(deps, objs.spell_res_pot_share, 10)
            repeat(3) { advance() }

            check(bob.prayerLvl > 0) { "Bob should have prayer points back." }
            check(player.prayerLvl > 0) { "The caster gets the effect too." }
            check(player.inv[10]?.id == potion.dose(3).id) { "One dose should be used for bob." }
        }

    @Test
    fun GameTestState.`boost potion share refuses a restoring potion`() = runLunar { deps ->
        prepare(84, "astralrune" to 3, "waterrune" to 10, "earthrune" to 12)
        val potion = Potions.all.first { it.effect is PotionEffect.PrayerPoints }
        player.inv[10] = InvObj(potion.dose(4), 1)
        teammate("bob", 1)

        castOnItem(
            deps,
            objs.spell_statboost_pot_share,
            10,
            "That potion does not boost anything. Try Stat Restore Pot Share.",
        )

        check(player.inv[10]?.id == potion.dose(4).id)
        check(player.count(obj("waterrune")) == 10) { "The runes should be kept." }
    }

    private fun GameTestScope.hitFor(target: Player, npc: Npc, damage: Int) {
        target.stats[stats.hitpoints] = 99
        target.queueHit(npc, delay = 1, HitType.Melee, damage)
        repeat(4) { advance() }
    }

    @Test
    fun GameTestState.`vengeance rebounds three quarters of the next hit`() = runLunar { deps ->
        prepare(94, "astralrune" to 8, "deathrune" to 4, "earthrune" to 20)
        val npc = spawnNpc(here.translate(2, 0), npcType())

        cast(deps, objs.spell_vengeance)
        check(deps.vengeance.isActive(player))

        hitFor(player, npc, 20)

        check(npc.hitpoints in 484..486) { "Expected 15 rebounded, npc has ${npc.hitpoints}/500." }
        check(!deps.vengeance.isActive(player)) { "Vengeance is used up by the hit." }

        // The next hit does not rebound.
        val before = npc.hitpoints
        hitFor(player, npc, 20)
        check(npc.hitpoints >= before) { "A second hit should not rebound." }
    }

    @Test
    fun GameTestState.`vengeance can only be cast every fifty ticks`() = runLunar { deps ->
        prepare(94, "astralrune" to 8, "deathrune" to 4, "earthrune" to 20)

        cast(deps, objs.spell_vengeance)
        cast(
            deps,
            objs.spell_vengeance,
            "You can only cast vengeance spells once every 30 seconds.",
        )

        check(player.count(obj("astralrune")) == 4) { "Only one cast should have been paid for." }
    }

    @Test
    fun GameTestState.`vengeance other gives a teammate the rebound`() = runLunar { deps ->
        prepare(93, "astralrune" to 3, "deathrune" to 2, "earthrune" to 10)
        val bob = teammate("bob", 1)
        val npc = spawnNpc(here.translate(2, 0), npcType())

        castOnPlayer(deps, objs.spell_vengeance_other, bob)
        check(deps.vengeance.isActive(bob))
        check(!deps.vengeance.isActive(player))

        hitFor(bob, npc, 40)

        check(npc.hitpoints in 469..471) { "Expected 30 rebounded, npc has ${npc.hitpoints}/500." }
    }

    @Test
    fun GameTestState.`magic imbue removes the need for the talisman for twenty one ticks`() =
        runLunar { deps ->
            prepare(82, "astralrune" to 2, "waterrune" to 7, "firerune" to 7)

            cast(deps, objs.spell_magic_imbue)

            val during = Runecrafting.ImbueCheck(player).also { eventBus.publish(it) }
            check(during.imbued) { "Expected the imbue to be on." }

            repeat(22) { advance() }
            val after = Runecrafting.ImbueCheck(player).also { eventBus.publish(it) }
            check(!after.imbued) { "Expected the imbue to be over." }
        }

    @Test
    fun GameTestState.`spellbook swap gives one spell from another book and then goes back`() =
        runLunar { deps ->
            prepare(96, "astralrune" to 3, "cosmicrune" to 2, "lawrune" to 1)

            player.withProtectedAccess { with(deps.utilities) { swapTo(Spellbook.Ancients) } }
            check(player.vars[varbits.spellbook] == Spellbook.Ancients.varValue) { "Not swapped." }

            // Casting an Ancient spell is the one spell the swap allows.
            val smokeRush = deps.spells.getObjSpell(objs.spell_smoke_rush)!!
            eventBus.publish(SpellCast(player, smokeRush))

            check(player.vars[varbits.spellbook] == Spellbook.Lunars.varValue) { "Not back." }
        }

    @Test
    fun GameTestState.`spellbook swap runs out after two minutes`() = runLunar { deps ->
        prepare(96, "astralrune" to 3, "cosmicrune" to 2, "lawrune" to 1)

        player.withProtectedAccess { with(deps.utilities) { swapTo(Spellbook.Arceuus) } }
        repeat(190) { advance() }
        check(player.vars[varbits.spellbook] == Spellbook.Arceuus.varValue) { "Too early." }

        repeat(20) { advance() }
        check(player.vars[varbits.spellbook] == Spellbook.Lunars.varValue) { "Not back." }
    }

    @Test
    fun GameTestState.`monster examine tells a monster's levels`() = runLunar { deps ->
        prepare(66, "mindrune" to 1, "cosmicrune" to 1, "astralrune" to 1)
        val npc = spawnNpc(here.translate(2, 0), npcType())
        val button = deps.spells.getObjSpell(objs.spell_monster_examine)!!.component

        player.withProtectedAccess {
            eventBus.publish(
                this,
                org.rsmod.api.player.events.interact.NpcTDefaultEvents.Ap(
                    npc,
                    -1,
                    null,
                    npc.visType,
                    button,
                ),
            )
        }

        assertMessageSent("Test target (level 50): 500/500 hitpoints.")
        assertMessageSent("Attack 100, Strength 100, Defence 1, Magic 1, Ranged 1.")
    }

    @Test
    fun GameTestState.`a group teleport takes the teammates standing close along`() =
        runLunar { deps ->
            prepare(88, "astralrune" to 3, "lawrune" to 3, "waterrune" to 15)
            val bob = teammate("bob", 1)
            val far = teammate("carol", 4)
            val destination = GROUP_DESTINATION

            cast(deps, objs.spell_catherby_telegroup)
            repeat(8) { advance() }

            check(player.coords == destination) { "The caster should be at the destination." }
            check(bob.coords == destination) {
                "Bob was next to the caster, he is at ${bob.coords}."
            }
            check(far.coords != destination) { "Carol was four tiles away." }
        }

    private fun GameTestScope.mistRunes(deps: Deps, imbued: Boolean): Int {
        prepare(82, "astralrune" to 2, "waterrune" to 12, "firerune" to 7, "blankrune_high" to 5)
        player.stats[stats.runecrafting] = 6
        repeat(40) { deps.random.next = 0 }
        if (imbued) {
            cast(deps, objs.spell_magic_imbue)
        }
        // Magic Imbue used up seven water runes; the rest are to be combined with air.
        val altarType = locTypes.values.first { it.internalName == "air_altar" }
        val altar = placeMapLoc(here.translate(3, 0), altarType)
        player.teleport(altar.coords.translateX(-1))
        val water = obj("waterrune")
        val slot = player.inv.indexOfFirst { it?.id == water.id }
        player.opLocT(altar, water, slot)
        repeat(6) { advance() }
        return player.count(obj("mistrune"))
    }

    @Test
    fun GameTestState.`with magic imbue runes combine at the altar without the talisman`() =
        runLunar { deps ->
            val made = mistRunes(deps, imbued = true)
            check(made > 0) { "Expected mist runes from the imbued combination." }
        }

    @Test
    fun GameTestState.`without magic imbue the talisman is needed`() = runLunar { deps ->
        val made = mistRunes(deps, imbued = false)
        check(made == 0) { "No talisman and no imbue should make nothing." }
    }
}
