package org.rsmod.content.areas.misc.isleofsouls.dungeons

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import kotlin.reflect.KClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.death.plugin.NpcDeathScript
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.SpellAttackRepository
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
import org.rsmod.content.skills.magic.spell.attacks.standard.ElementalSpells
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript

private val RUNE_REPOSITORIES =
    listOf(
        ComboRuneRepository::class,
        CompactRuneRepository::class,
        FakeRuneRepository::class,
        RuneSubstituteRepository::class,
        StaffSubstituteRepository::class,
        UnlimitedRuneRepository::class,
    )

@OptIn(InternalApi::class)
class DungeonMechanicsTest {
    private object MechanicsModule : AbstractModule() {
        override fun configure() {
            // The accuracy formulas read weapon styles that WeaponAttackStylesScript loads.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            // The test and the loaded script must be the same instance to share their state.
            bind(DungeonKrakenScript::class.java).`in`(Scopes.SINGLETON)
            bind(DungeonRevenantScript::class.java).`in`(Scopes.SINGLETON)
            bind(DungeonSmokeScript::class.java).`in`(Scopes.SINGLETON)
            // The combat script reads the spell registry and the rune repositories.
            install(MagicSpellModule())
            bind(SpellAttackManager::class.java).`in`(Scopes.SINGLETON)
            bind(SpellAttackRegistry::class.java).`in`(Scopes.SINGLETON)
            bind(SpellAttackRepository::class.java).`in`(Scopes.SINGLETON)
            Multibinder.newSetBinder(binder(), SpellAttackMap::class.java)
                .addBinding()
                .to(ElementalSpells::class.java)
            for (repository in RUNE_REPOSITORIES) {
                bind(repository.java).`in`(Scopes.SINGLETON)
            }
        }
    }

    class Deps
    @Inject
    constructor(
        val smoke: DungeonSmokeScript,
        val kraken: DungeonKrakenScript,
        val revenants: DungeonRevenantScript,
        val npcRepo: NpcRepository,
        val interactions: AiPlayerInteractions,
    )

    private fun GameTestState.runMechanicsTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            MechanicsModule,
            DungeonSmokeScript::class,
            DungeonKrakenScript::class,
            DungeonRevenantScript::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
            playerCombatScript,
            NpcDeathScript::class,
            spellAttackScript,
            AutocastScript::class,
            ComboRuneScript::class,
            CompactRuneScript::class,
            FakeRuneScript::class,
            RuneSubstituteScript::class,
            StaffSubstituteScript::class,
            UnlimitedRuneScript::class,
        ) { deps ->
            body(deps)
        }

    /** The player-versus-npc combat script is internal to the combat module. */
    @Suppress("UNCHECKED_CAST")
    private val playerCombatScript =
        Class.forName("org.rsmod.api.combat.scripts.PvNCombatScript").kotlin
            as KClass<out PluginScript>

    @Suppress("UNCHECKED_CAST")
    private val spellAttackScript =
        Class.forName("org.rsmod.api.spells.attack.scripts.SpellAttackScript").kotlin
            as KClass<out PluginScript>

    private val inSmoke = CoordGrid(2404, 9415, 0)

    private fun GameTestScope.setHitpoints(hp: Int) {
        player.stats[stats.hitpoints] = hp
    }

    private fun GameTestScope.hp(): Int = player.stats[stats.hitpoints]

    @Test
    fun GameTestState.`the smoke hurts a player with nothing over their face`() =
        runMechanicsTest { _ ->
            allocZoneCollision(inSmoke)
            player.placeAt(inSmoke)
            setHitpoints(99)

            advance(ticks = 20)

            assertTrue(hp() < 99, "coughing costs hitpoints")
        }

    @Test
    fun GameTestState.`a face mask or a slayer helmet keeps the smoke off`() =
        runMechanicsTest { deps ->
            allocZoneCollision(inSmoke)
            player.placeAt(inSmoke)
            setHitpoints(99)
            player.worn[Wearpos.Hat.slot] = InvObj(SmokeObjs.face_mask)
            advance(ticks = 20)
            assertEquals(99, hp(), "a face mask")

            val helm = locTypesHelm()
            player.worn[Wearpos.Hat.slot] = InvObj(helm)
            advance(ticks = 20)
            assertEquals(99, hp(), "a slayer helmet")
            assertTrue(deps.smoke.isProtected(player))
        }

    private fun GameTestScope.locTypesHelm() =
        objTypes.values.first { it.internalName == "slayer_helm_i_red" }

    @Test
    fun GameTestState.`the smoke never takes the last hitpoint`() = runMechanicsTest { _ ->
        allocZoneCollision(inSmoke)
        player.placeAt(inSmoke)
        setHitpoints(1)

        advance(ticks = 20)

        assertEquals(1, hp())
    }

    @Test
    fun GameTestState.`the smoke does nothing outside the dungeon`() = runMechanicsTest { _ ->
        val outside = CoordGrid(2200, 2815, 0)
        allocZoneCollision(outside)
        player.placeAt(outside)
        setHitpoints(99)

        advance(ticks = 20)

        assertEquals(99, hp())
    }

    private fun GameTestScope.whirlpoolAt(coords: CoordGrid): Npc {
        allocZoneCollision(coords)
        return spawnNpc(coords, npcTypes[KrakenNpcs.whirlpool])
    }

    @Test
    fun GameTestState.`disturbing a whirlpool puts a cave kraken in its place`() =
        runMechanicsTest { deps ->
            val coords = CoordGrid(2262, 10020, 0)
            val whirlpool = whirlpoolAt(coords)
            player.placeAt(coords.translateX(2))

            val kraken = deps.kraken.reveal(whirlpool, player)

            assertEquals(KrakenNpcs.cave_kraken.id, kraken.type.id)
            assertEquals(coords, kraken.coords)
            assertTrue(whirlpool.hidden, "the whirlpool is gone while the kraken is up")
        }

    private fun GameTestScope.krakenAt(coords: CoordGrid): Npc? =
        npcRepo.findAll(coords).firstOrNull { it.type.id == KrakenNpcs.cave_kraken.id }

    private fun GameTestScope.equipBow() {
        val bow = objTypes.values.first { it.internalName == "shortbow" }
        val arrows = objTypes.values.first { it.internalName == "bronze_arrow" }
        player.worn[Wearpos.RightHand.slot] = InvObj(bow)
        player.worn[Wearpos.Quiver.slot] = InvObj(arrows, 100)
    }

    @Test
    fun GameTestState.`a ranged attack on a whirlpool brings up the kraken when it lands`() =
        runMechanicsTest { _ ->
            val coords = CoordGrid(2262, 10020, 0)
            val whirlpool = whirlpoolAt(coords)
            val far = coords.translateX(5)
            allocZoneCollision(far)
            player.placeAt(far)
            equipBow()
            advance(ticks = 3)
            val hitpoints = whirlpool.hitpoints

            player.opNpc2(whirlpool)
            advance(ticks = 1)
            assertFalse(whirlpool.hidden, "the arrow is still in the air")
            advance(ticks = 6)

            assertEquals(far, player.coords, "the player does not walk up to it")
            assertTrue(whirlpool.hidden, "the whirlpool is gone")
            assertEquals(hitpoints, whirlpool.hitpoints, "the arrow does it no damage")
            assertTrue(krakenAt(coords) != null, "a kraken is up")
        }

    @Test
    fun GameTestState.`the player keeps shooting the kraken without clicking it`() =
        runMechanicsTest { _ ->
            val coords = CoordGrid(2262, 10020, 0)
            val whirlpool = whirlpoolAt(coords)
            val far = coords.translateX(5)
            allocZoneCollision(far)
            player.placeAt(far)
            equipBow()
            advance(ticks = 3)

            player.opNpc2(whirlpool)
            advance(ticks = 8)
            val kraken = checkNotNull(krakenAt(coords)) { "a kraken is up" }
            val target = (player.interaction as? InteractionNpcOp)?.target
            assertTrue(target === kraken, "the player is attacking the kraken: $target")

            val arrows = player.worn[Wearpos.Quiver.slot]!!.count
            advance(ticks = 12)
            assertTrue(
                player.worn[Wearpos.Quiver.slot]!!.count < arrows,
                "the player keeps firing at the kraken",
            )
        }

    @Test
    fun GameTestState.`an autocast spell on a whirlpool brings up the kraken when it lands`() =
        runMechanicsTest { _ ->
            val coords = CoordGrid(2262, 10020, 0)
            val whirlpool = whirlpoolAt(coords)
            val far = coords.translateX(5)
            allocZoneCollision(far)
            player.placeAt(far)
            player.stats[stats.magic] = 99
            player.worn[Wearpos.RightHand.slot] = InvObj(objs.air_staff)
            player.inv[0] = InvObj(objTypes.values.first { it.internalName == "mindrune" }, 100)
            player.setVarBit(varbits.autocast_spell, 1)
            player.setVarBit(varbits.autocast_set, 1)
            advance(ticks = 3)
            val hitpoints = whirlpool.hitpoints

            player.opNpc2(whirlpool)
            advance(ticks = 8)

            assertEquals(far, player.coords, "the player does not walk up to it")
            assertTrue(whirlpool.hidden, "the whirlpool is gone")
            assertEquals(hitpoints, whirlpool.hitpoints, "the spell does it no damage")
            assertTrue(krakenAt(coords) != null, "a kraken is up")
        }

    @Test
    fun GameTestState.`the damage of hits on a whirlpool goes to the kraken`() =
        runMechanicsTest { _ ->
            val coords = CoordGrid(2262, 10020, 0)
            val whirlpool = whirlpoolAt(coords)
            player.placeAt(coords.translateX(4))
            advance(ticks = 3)
            val whirlpoolHitpoints = whirlpool.hitpoints

            // The second hit is already in the air when the first one brings the kraken up.
            whirlpool.queueHit(player, delay = 2, type = HitType.Ranged, damage = 17)
            whirlpool.queueHit(player, delay = 4, type = HitType.Ranged, damage = 5)
            advance(ticks = 8)

            val kraken = checkNotNull(krakenAt(coords)) { "a kraken is up" }
            assertEquals(whirlpoolHitpoints, whirlpool.hitpoints, "the whirlpool is unharmed")
            assertEquals(npcTypes[KrakenNpcs.cave_kraken].hitpoints - 22, kraken.hitpoints)
        }

    @Test
    fun GameTestState.`a revealed kraken casts at a player who stays back`() =
        runMechanicsTest { deps ->
            val coords = CoordGrid(2262, 10020, 0)
            val whirlpool = whirlpoolAt(coords)
            val back = coords.translateX(6)
            allocZoneCollision(back)
            player.placeAt(back)
            advance(ticks = 3)
            setHitpoints(99)
            val kraken = deps.kraken.reveal(whirlpool, player)

            var hurt = false
            repeat(60) {
                advance()
                if (hp() < 99) hurt = true
                setHitpoints(99)
            }
            assertTrue(hurt, "the kraken casts from where it is")
            assertEquals(coords, kraken.coords, "and stays in the water")
        }

    @Test
    fun GameTestState.`melee cannot disturb a whirlpool`() = runMechanicsTest { _ ->
        val coords = CoordGrid(2262, 10020, 0)
        val whirlpool = whirlpoolAt(coords)
        allocZoneCollision(coords.translateX(1))
        player.placeAt(coords.translateX(1))
        advance(ticks = 3)

        player.opNpc2(whirlpool)
        advance(ticks = 1)
        assertMessageSent(constants.dm_reach)
        advance(ticks = 5)
        assertFalse(whirlpool.hidden, "the whirlpool stays")
        assertTrue(krakenAt(coords) == null, "no kraken")
    }

    @Test
    fun GameTestState.`a killed kraken dies, drops under the killer and its whirlpool returns`() =
        runMechanicsTest { deps ->
            val coords = CoordGrid(2262, 10020, 0)
            val whirlpool = whirlpoolAt(coords)
            val back = coords.translateX(6)
            allocZoneCollision(back)
            player.placeAt(back)
            player.observerUUID = player.uuid
            advance(ticks = 3)
            setHitpoints(99)
            val kraken = deps.kraken.reveal(whirlpool, player)
            advance(ticks = 4)
            val death = npcTypes[KrakenNpcs.cave_kraken].param(params.death_anim)
            assertEquals(3993, death.id, "the kraken's own death anim")

            kraken.heroPoints(player, 10)
            kraken.queueHit(player, delay = 1, type = HitType.Ranged, damage = kraken.hitpoints)
            advance(ticks = 20)

            assertTrue(objRepo.findAll(back).any(), "the drops are under the player")
            assertTrue(objRepo.findAll(coords).none(), "and not where the kraken died")
            assertTrue(whirlpool.hidden, "the whirlpool is not back yet")
            advance(ticks = DungeonKrakenScript.WHIRLPOOL_RETURN_TICKS + 5)
            assertFalse(whirlpool.hidden, "the whirlpool comes back")
        }

    @Test
    fun GameTestState.`a killed kraken's whirlpool comes back`() = runMechanicsTest { deps ->
        val coords = CoordGrid(2262, 10020, 0)
        val whirlpool = whirlpoolAt(coords)
        player.placeAt(coords.translateX(2))
        advance(ticks = 5)
        val kraken = deps.kraken.reveal(whirlpool, player)

        eventBus.publish(NpcDeathEvents.Killed(kraken, player))
        advance(ticks = 3)
        assertTrue(whirlpool.hidden, "still gone just after the kill")

        advance(ticks = DungeonKrakenScript.WHIRLPOOL_RETURN_TICKS + 5)
        assertFalse(whirlpool.hidden, "back after a while")
    }

    @Test
    fun GameTestState.`a revealed kraken fights back`() = runMechanicsTest { deps ->
        val coords = CoordGrid(2262, 10020, 0)
        val whirlpool = whirlpoolAt(coords)
        val stand = coords.translateX(npcTypes[KrakenNpcs.cave_kraken].size)
        allocZoneCollision(stand)
        player.placeAt(stand)
        advance(ticks = 3)
        setHitpoints(99)
        deps.kraken.reveal(whirlpool, player)

        var hurt = false
        repeat(60) {
            advance()
            if (hp() < 99) hurt = true
            setHitpoints(99)
        }
        assertTrue(hurt, "the kraken's magic hits the player")
    }

    @Test
    fun GameTestState.`revenants never choose a style the player is protected from`() =
        runMechanicsTest { deps ->
            player.setVarBit(varbits.protect_from_magic, 1)
            repeat(200) { assertNotEquals(NpcAttackStyle.Magic, deps.revenants.pickStyle(player)) }
            player.setVarBit(varbits.protect_from_missiles, 1)
            repeat(200) { assertEquals(NpcAttackStyle.Melee, deps.revenants.pickStyle(player)) }
        }

    @Test
    fun GameTestState.`revenants use every style on a player with no protection`() =
        runMechanicsTest { deps ->
            val seen = HashSet<NpcAttackStyle>()
            repeat(500) { seen += deps.revenants.pickStyle(player) }
            assertTrue(seen.size >= 2, "more than one style is used: $seen")
        }

    @Test
    fun GameTestState.`the wiki's max hits are on the revenants`() = runMechanicsTest { _ ->
        val hits = DungeonRevenantScript.KINDS.map { it.maxHit }
        assertEquals(listOf(2, 3, 5, 8, 16, 14, 18, 20, 23, 27, 30), hits)
    }

    @Test
    fun GameTestState.`a revenant hurts a player and heals itself once it is hurt`() =
        runMechanicsTest { deps ->
            val coords = CoordGrid(3217, 10062, 0)
            allocZoneCollision(coords)
            val stand = coords.translateX(npcTypes[RevenantNpcs.dragon].size)
            allocZoneCollision(stand)
            player.placeAt(stand)
            advance(ticks = 3)
            val revenant = spawnNpc(coords, npcTypes[RevenantNpcs.dragon])
            val full = revenant.baseHitpointsLvl
            revenant.hitpoints = full / 4
            setHitpoints(99)
            revenant.opPlayer2(player, deps.interactions)

            var hurt = false
            var healed = false
            repeat(300) {
                advance()
                if (hp() < 99) hurt = true
                setHitpoints(99)
                if (revenant.hitpoints > full / 4) healed = true
            }
            assertTrue(hurt, "the revenant hits the player")
            assertTrue(healed, "the revenant heals itself below half its hitpoints")
        }
}
