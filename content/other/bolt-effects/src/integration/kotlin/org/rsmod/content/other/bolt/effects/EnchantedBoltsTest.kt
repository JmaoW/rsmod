package org.rsmod.content.other.bolt.effects

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.manager.BoltBoost
import org.rsmod.api.combat.manager.BoltEffectRegistry
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.PlayerPoisonEvents
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.stat.stat
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.other.special.attacks.support.DotKind
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.util.ParamMapBuilder
import org.rsmod.map.CoordGrid

class EnchantedBoltsTest {
    class Deps
    @Inject
    constructor(
        val manager: PlayerAttackManager,
        val registry: BoltEffectRegistry,
        val bolts: EnchantedBolts,
        val dots: SpecialDots,
    )

    private object BoltModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            // The game cycle and the damage over time must queue on the same world queue.
            bind(WorldQueueList::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private fun GameTestState.runBoltTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            BoltModule,
            WeaponAttackStylesScript::class,
            NpcHitScript::class,
            PlayerHitScript::class,
            EnchantedBoltsScript::class,
        ) { deps ->
            body(deps)
        }

    private val dummy =
        npcTypeFactory.create {
            name = "Bolt dummy"
            vislevel = 50
            attack = 50
            strength = 50
            defence = 1
            ranged = 90
            hitpoints = 10_000
            defaultMode = NpcMode.None
        }

    private fun GameTestScope.obj(internal: String): ObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.spawnDummy(): Npc {
        val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), dummy)
        player.placeAt(npc.coords.translateX(-3))
        return npc
    }

    /** Fires from a crossbow with [ammo] in the quiver, 99 in Ranged and a full health bar. */
    private fun GameTestScope.arm(
        ammo: String,
        crossbow: String = "xbows_crossbow_runite",
        shooter: Player = player,
    ) {
        shooter.stats[stats.ranged] = 99
        shooter.stats[stats.hitpoints] = 99
        shooter.stats[stats.prayer] = 99
        shooter.worn[Wearpos.RightHand.slot] = InvObj(obj(crossbow))
        shooter.worn[Wearpos.Quiver.slot] = InvObj(obj(ammo), 1_000)
    }

    private fun enchanted(metal: String, gem: String): String =
        "xbows_crossbow_bolts_${metal}_tipped_${gem}_enchanted"

    private fun ruby() = enchanted("adamantite", "ruby")

    private fun GameTestScope.attack() =
        CombatAttack.Ranged(
            checkNotNull(player.worn[Wearpos.RightHand.slot]),
            RangedAttackType.Standard,
            RangedAttackStyle.Accurate,
        )

    /** A shot at [target] with [boost], rolled and queued as the combat script does. */
    private fun GameTestScope.shoot(
        deps: Deps,
        target: PathingEntity,
        boost: BoltBoost? = null,
    ): Int {
        if (boost != null) {
            deps.registry.boost(player, boost)
        }
        val ammo = objTypes.getOrNull(player.worn[Wearpos.Quiver.slot])
        val damage = deps.manager.rollRangedDamage(player, target, attack())
        deps.manager.queueRangedHit(player, target, ammo, damage, 2, 3)
        return damage
    }

    /** Shoots [boost] shots at [target] until [done], at most 600 times. */
    private fun GameTestScope.shootUntil(
        deps: Deps,
        target: PathingEntity,
        boost: BoltBoost? = BoltBoost.Guaranteed,
        done: (Int) -> Boolean,
    ): Boolean {
        repeat(600) {
            if (done(shoot(deps, target, boost))) {
                return true
            }
            advance(ticks = 1)
        }
        return false
    }

    @Test
    fun GameTestState.`plain bolts and other ammunition are no enchanted shot`() =
        runBoltTest { deps ->
            val npc = spawnDummy()
            arm("xbows_crossbow_bolts_runite")
            assertNull(deps.bolts.begin(player, npc, null))
            arm(ruby(), crossbow = "xbows_crossbow_runite")
            assertNotNull(deps.bolts.begin(player, npc, null))
            // Not a crossbow.
            player.worn[Wearpos.RightHand.slot] = InvObj(obj("dragon_scimitar"))
            assertNull(deps.bolts.begin(player, npc, null))
        }

    @Test
    fun GameTestState.`opal and pearl add a tenth and a twentieth of the Ranged level`() =
        runBoltTest { deps ->
            val npc = spawnDummy()
            arm(enchanted("bronze", "opal"))
            val opal = deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!
            assertEquals(9, opal.extraDamage)
            assertTrue(opal.alwaysHit)

            arm(enchanted("iron", "pearl"))
            val pearl = deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!
            assertEquals(4, pearl.extraDamage)
            assertTrue(pearl.alwaysHit)
        }

    @Test
    fun GameTestState.`the Zaryte crossbow makes every effect a tenth stronger`() =
        runBoltTest { deps ->
            val npc = spawnDummy()
            arm(enchanted("bronze", "opal"), crossbow = "zaryte_xbow")
            assertEquals(
                11,
                deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!.extraDamage,
            )
            arm(enchanted("iron", "pearl"), crossbow = "zaryte_xbow")
            assertEquals(
                5,
                deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!.extraDamage,
            )
            arm(enchanted("adamantite", "diamond"), crossbow = "zaryte_xbow")
            assertEquals(
                1.26,
                deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!.maxHitMultiplier,
            )
            arm(enchanted("runite", "onyx"), crossbow = "zaryte_xbow")
            assertEquals(
                1.32,
                deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!.maxHitMultiplier,
            )
            arm(enchanted("runite", "dragonstone"), crossbow = "zaryte_xbow")
            assertEquals(
                21,
                deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!.extraDamage,
            )
            arm(ruby(), crossbow = "zaryte_xbow")
            assertEquals(
                110,
                deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!.fixedDamage,
            )
        }

    @Test
    fun GameTestState.`diamond never misses and onyx and dragonstone hit harder`() =
        runBoltTest { deps ->
            val npc = spawnDummy()
            arm(enchanted("adamantite", "diamond"))
            val diamond = deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!
            assertTrue(diamond.alwaysHit)
            assertEquals(1.15, diamond.maxHitMultiplier)

            arm(enchanted("runite", "onyx"))
            assertEquals(
                1.2,
                deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!.maxHitMultiplier,
            )

            arm(enchanted("runite", "dragonstone"))
            assertEquals(
                19,
                deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!.extraDamage,
            )
        }

    @Test
    fun GameTestState
        .`ruby takes a fifth of the target's hitpoints for a tenth of the shooter's`() =
        runBoltTest { deps ->
            val npc = spawnDummy()
            arm(ruby())
            val adjust = deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!
            assertEquals(100, adjust.fixedDamage, "a fifth of 10,000 is capped at 100")
            npc.hitpoints = 200
            assertEquals(
                40,
                deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit()!!.fixedDamage,
            )

            npc.hitpoints = 10_000
            val before = player.stat(stats.hitpoints)
            val hit = shootUntil(deps, npc) { it == 100 }
            assertTrue(hit, "a ruby bolt hit for 100")
            advance(ticks = 3)
            assertEquals(before - before / 10, player.stat(stats.hitpoints))
        }

    @Test
    fun GameTestState.`ruby does nothing for a shooter with less than ten hitpoints`() =
        runBoltTest { deps ->
            val npc = spawnDummy()
            arm(ruby())
            player.stats[stats.hitpoints] = 9
            assertNull(deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit())
        }

    @Test
    fun GameTestState.`emerald poisons for five and six with the Zaryte crossbow`() =
        runBoltTest { deps ->
            val npc = spawnDummy()
            arm(enchanted("mithril", "emerald"))
            assertTrue(shootUntil(deps, npc) { deps.dots.has(npc, DotKind.Poison) })
            assertEquals(5, deps.dots.nextHit(npc, DotKind.Poison))

            val other = spawnNpc(CoordGrid(0, 50, 50, 12, 10), dummy)
            arm(enchanted("mithril", "emerald"), crossbow = "zaryte_xbow")
            assertTrue(shootUntil(deps, other) { deps.dots.has(other, DotKind.Poison) })
            assertEquals(6, deps.dots.nextHit(other, DotKind.Poison))
        }

    @Test
    fun GameTestState.`sapphire gives the shooter prayer back from an npc`() = runBoltTest { deps ->
        val npc = spawnDummy()
        arm(enchanted("mithril", "sapphire"))
        player.setCurrentLevel(stats.prayer, 50)
        // The dummy has 90 Ranged: a tenth drained, an eighth of it given.
        assertTrue(shootUntil(deps, npc) { player.stat(stats.prayer) > 50 })
        assertEquals(51, player.stat(stats.prayer))
    }

    @Test
    fun GameTestState.`onyx heals a quarter of the damage`() = runBoltTest { deps ->
        val npc = spawnDummy()
        arm(enchanted("runite", "onyx"))
        player.setCurrentLevel(stats.hitpoints, 40)
        var damage = 0
        assertTrue(
            shootUntil(deps, npc) {
                damage = it
                it >= 8
            }
        )
        // Healed that much for each of the hits that landed, but the hits rolled before this one
        // may also have healed, so only a lower bound is certain.
        assertTrue(player.stat(stats.hitpoints) >= 40 + damage / 4)
    }

    @Test
    fun GameTestState.`jade holds an npc still for eight ticks`() = runBoltTest { deps ->
        val npc = spawnDummy()
        arm(enchanted("blurite", "jade"))
        assertFalse(npc.isFrozen)
        assertTrue(shootUntil(deps, npc) { npc.isFrozen })
    }

    @Test
    fun GameTestState
        .`topaz and sapphire drain a player and nothing happens to an npc with topaz`() =
        runBoltTest { deps ->
            val npc = spawnDummy()
            arm(enchanted("steel", "redtopaz"))
            // Topaz works on players only.
            val shot = deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!
            assertNull(shot.rollBypass())
            assertNull(shot.rollOnHit())

            val victim = registerPlayer(player = Player().apply { username = "victim" })
            victim.placeAt(player.coords.translateX(1))
            victim.stats[stats.magic] = 80
            assertTrue(shootUntil(deps, victim, boost = null) { victim.stat(stats.magic) < 80 })
            assertEquals(79, victim.stat(stats.magic))
        }

    @Test
    fun GameTestState.`sapphire drains a player's prayer by a twentieth of the Ranged level`() =
        runBoltTest { deps ->
            spawnDummy()
            arm(enchanted("mithril", "sapphire"))
            val victim = registerPlayer(player = Player().apply { username = "victim" })
            victim.placeAt(player.coords.translateX(1))
            victim.stats[stats.prayer] = 70
            player.setCurrentLevel(stats.prayer, 50)
            assertTrue(shootUntil(deps, victim) { victim.stat(stats.prayer) < 70 })
            // 99 / 20 = 4 drained, 2 given.
            assertEquals(66, victim.stat(stats.prayer))
            assertEquals(52, player.stat(stats.prayer))
        }

    @Test
    fun GameTestState.`emerald poisons a player through the player's own poison`() =
        runBoltTest { deps ->
            spawnDummy()
            arm(enchanted("mithril", "emerald"))
            val victim = registerPlayer(player = Player().apply { username = "victim" })
            victim.placeAt(player.coords.translateX(1))
            val poisoned = mutableListOf<PlayerPoisonEvents.Apply>()
            eventBus.subscribeUnbound(PlayerPoisonEvents.Apply::class.java) {
                applied = true
                poisoned += this
            }
            assertTrue(shootUntil(deps, victim) { poisoned.isNotEmpty() })
            assertEquals(victim, poisoned.first().target)
            assertEquals(player, poisoned.first().source)
            assertEquals(5, poisoned.first().damage)
            assertFalse(deps.dots.has(victim, DotKind.Poison), "no special attack dot on a player")
        }

    @Test
    fun GameTestState.`pearls do nothing to a player with a water staff and more to one in fire`() =
        runBoltTest { deps ->
            val npc = spawnDummy()
            arm(enchanted("iron", "pearl"))
            val victim = registerPlayer(player = Player().apply { username = "victim" })
            victim.worn[Wearpos.RightHand.slot] = InvObj(obj("staff_of_water"))
            assertNull(deps.bolts.begin(player, victim, BoltBoost.Guaranteed)!!.rollOnHit())
            victim.worn[Wearpos.RightHand.slot] = InvObj(obj("staff_of_fire"))
            assertEquals(
                6,
                deps.bolts.begin(player, victim, BoltBoost.Guaranteed)!!.rollOnHit()!!.extraDamage,
                "a fifteenth of 99",
            )
        }

    @Test
    fun GameTestState.`dragonstone does nothing to a fiery target`() = runBoltTest { deps ->
        spawnDummy()
        arm(enchanted("runite", "dragonstone"))
        val victim = registerPlayer(player = Player().apply { username = "victim" })
        victim.worn[Wearpos.RightHand.slot] = InvObj(obj("staff_of_fire"))
        assertNull(deps.bolts.begin(player, victim, BoltBoost.Guaranteed)!!.rollOnHit())
    }

    @Test
    fun GameTestState.`onyx does nothing to undead`() = runBoltTest { deps ->
        val undead =
            npcTypeFactory.create {
                name = "Bolt undead"
                vislevel = 50
                hitpoints = 100
                defaultMode = NpcMode.None
                paramMap = ParamMapBuilder().apply { this[params.undead] = 1 }.toParamMap()
            }
        val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), undead)
        arm(enchanted("runite", "onyx"))
        assertNull(deps.bolts.begin(player, npc, BoltBoost.Guaranteed)!!.rollOnHit())
    }

    @Test
    fun GameTestState.`chances are the wiki's`() = runBoltTest { deps ->
        val npc = spawnDummy()
        val shots = 20_000

        fun rate(
            gem: String,
            metal: String,
            boost: BoltBoost? = null,
            bypass: Boolean = true,
        ): Int {
            arm(enchanted(metal, gem))
            var worked = 0
            repeat(shots) {
                val shot = deps.bolts.begin(player, npc, boost)!!
                val adjust = if (bypass) shot.rollBypass() else shot.rollOnHit()
                if (adjust != null) worked++
            }
            return worked * 1000 / shots
        }

        // Tenths of a percent, within a margin of a percent.
        assertEquals(50.0, rate("opal", "bronze").toDouble(), 12.0)
        assertEquals(100.0, rate("opal", "bronze", BoltBoost.Doubled).toDouble(), 15.0)
        assertEquals(100.0, rate("diamond", "adamantite").toDouble(), 15.0)
        assertEquals(550.0, rate("emerald", "mithril", bypass = false).toDouble(), 25.0)
        assertEquals(250.0, rate("sapphire", "mithril", bypass = false).toDouble(), 20.0)
        assertEquals(60.0, rate("ruby", "adamantite").toDouble(), 12.0)
    }

    @Test
    fun GameTestState.`a boost is used up by one shot`() = runBoltTest { deps ->
        val npc = spawnDummy()
        arm("xbows_crossbow_bolts_runite")
        deps.registry.boost(player, BoltBoost.Guaranteed)
        shoot(deps, npc)
        assertNull(deps.registry.takeBoost(player), "a plain shot still uses the boost up")
    }
}
