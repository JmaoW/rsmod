package org.rsmod.content.skills.slayer

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.combat.NpcAttackChecks
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.hat
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.stat
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.content.skills.slayer.configs.SlayerParams
import org.rsmod.content.skills.slayer.configs.SlayerTables
import org.rsmod.content.skills.slayer.scripts.SlayerEngine
import org.rsmod.content.skills.slayer.scripts.SlayerFinishing
import org.rsmod.content.skills.slayer.scripts.SlayerGear
import org.rsmod.content.skills.slayer.scripts.SlayerProtection
import org.rsmod.content.skills.slayer.scripts.SlayerRequirements
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SlayerRulesHolder
@Inject
constructor(
    engine: SlayerEngine,
    checks: NpcAttackChecks,
    finishing: SlayerFinishing,
    gear: SlayerGear,
) : PluginScript() {
    init {
        heldEngine = engine
        heldChecks = checks
        heldFinishing = finishing
        heldGear = gear
    }

    override fun ScriptContext.startup() {}

    companion object {
        lateinit var heldEngine: SlayerEngine
        lateinit var heldChecks: NpcAttackChecks
        lateinit var heldFinishing: SlayerFinishing
        lateinit var heldGear: SlayerGear
    }
}

// The holders above are static, so the tests in a class must not run side by side.
@Execution(ExecutionMode.SAME_THREAD)
class SlayerRulesTest {
    private val engine
        get() = SlayerRulesHolder.heldEngine

    private val scripts =
        arrayOf(
            SlayerRulesHolder::class,
            SlayerRequirements::class,
            SlayerProtection::class,
            SlayerFinishing::class,
            PlayerHitScript::class,
            NpcHitScript::class,
        )

    private val spot = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.prepare(slayer: Int = 99) {
        player.stats[stats.slayer] = slayer
        for (stat in
            listOf(stats.attack, stats.strength, stats.defence, stats.ranged, stats.magic)) {
            player.stats[stat] = 80
        }
        player.stats[stats.hitpoints] = 99
        player.stats[stats.crafting] = 99
        player.stats[stats.firemaking] = 99
        player.clearInv()
        player.hat = null
        player.lefthand = null
        player.righthand = null
        player.teleport(spot)
        advance(12)
    }

    private fun GameTestScope.monster(taskId: Int, name: String? = null): Npc {
        val type =
            npcTypes.values.first {
                it.param(SlayerParams.task_id) == taskId &&
                    it.vislevel > 0 &&
                    (name == null || it.name.equals(name, ignoreCase = true))
            }
        return spawnNpc(spot.translateX(1), type)
    }

    private fun GameTestScope.obj(type: ObjType) = InvObj(objTypes[type])

    /** A hit from [npc] on the player, run through the game's own hit queue. */
    private fun GameTestScope.hitPlayerFrom(
        npc: Npc,
        type: HitType = HitType.Melee,
        damage: Int = 1,
    ) {
        player.queueHit(npc, 1, type, damage)
        advance(4)
    }

    private fun GameTestScope.attackLevel() = player.stat(stats.attack)

    // --- Slayer level ------------------------------------------------------------------------

    @Test
    fun GameTestState.`a monster needs its Slayer level to be attacked`() =
        runGameTest(*scripts) {
            prepare(slayer = 54)
            val turoth = monster(36)
            var allowed = true
            player.withProtectedAccess {
                allowed = SlayerRulesHolder.heldChecks.allows(this, turoth)
            }
            assertFalse(allowed, "Turoth need 55 Slayer")

            player.stats[stats.slayer] = 55
            player.withProtectedAccess {
                allowed = SlayerRulesHolder.heldChecks.allows(this, turoth)
            }
            assertTrue(allowed)

            val rat = monster(3)
            player.stats[stats.slayer] = 1
            player.withProtectedAccess { allowed = SlayerRulesHolder.heldChecks.allows(this, rat) }
            assertTrue(allowed, "A rat needs no Slayer level")
        }

    // --- Weapons -----------------------------------------------------------------------------

    @Test
    fun GameTestState.`turoth only take damage from leaf-bladed weapons and broad ammunition`() =
        runGameTest(*scripts) {
            prepare()
            val turoth = monster(36)
            fun damageWith(type: HitType, weapon: ObjType?, secondary: ObjType? = null): Int {
                val builder = hitBuilder(type, 10, weapon, secondary)
                eventBus.publish(NpcHitEvents.ModifyAny(turoth, builder, player))
                return builder.damage
            }
            assertEquals(0, damageWith(HitType.Melee, objs.coins))
            assertEquals(10, damageWith(HitType.Melee, SlayerObjs.leaf_bladed_spear))
            assertEquals(10, damageWith(HitType.Melee, SlayerObjs.leaf_bladed_battleaxe))
            assertEquals(0, damageWith(HitType.Ranged, objs.coins, objs.coins))
            assertEquals(10, damageWith(HitType.Ranged, objs.coins, SlayerObjs.broad_bolts))
            assertEquals(0, damageWith(HitType.Magic, SlayerObjs.staff, objs.coins))
            assertEquals(10, damageWith(HitType.Magic, SlayerObjs.staff, objs.spell_magic_dart))
            assertEquals(0, damageWith(HitType.Magic, objs.air_staff, objs.spell_magic_dart))
        }

    @Test
    fun GameTestState.`a harpie bug swarm needs a lit bug lantern`() =
        runGameTest(*scripts) {
            prepare()
            val swarm = monster(70)
            val unlit = hitBuilder(HitType.Melee, 6, null, null)
            eventBus.publish(NpcHitEvents.ModifyAny(swarm, unlit, player))
            assertEquals(0, unlit.damage)

            player.lefthand = obj(SlayerObjs.bug_lantern)
            val stillUnlit = hitBuilder(HitType.Melee, 6, null, null)
            eventBus.publish(NpcHitEvents.ModifyAny(swarm, stillUnlit, player))
            assertEquals(0, stillUnlit.damage)

            player.lefthand = obj(SlayerObjs.bug_lantern_lit)
            val lit = hitBuilder(HitType.Melee, 6, null, null)
            eventBus.publish(NpcHitEvents.ModifyAny(swarm, lit, player))
            assertEquals(6, lit.damage)
        }

    @Test
    fun GameTestState.`a bug lantern lights with a tinderbox and Firemaking 33`() =
        runGameTest(*scripts) {
            prepare()
            player.stats[stats.firemaking] = 32
            player.invAdd(player.inv, SlayerObjs.bug_lantern, 1)
            player.invAdd(player.inv, SlayerObjs.tinderbox, 1)
            player.withProtectedAccess { with(SlayerRulesHolder.heldGear) { lightLantern() } }
            assertTrue(SlayerObjs.bug_lantern in player.inv, "Not lit at 32")

            player.stats[stats.firemaking] = 33
            player.withProtectedAccess { with(SlayerRulesHolder.heldGear) { lightLantern() } }
            assertTrue(SlayerObjs.bug_lantern_lit in player.inv)
            assertFalse(SlayerObjs.bug_lantern in player.inv)
        }

    // --- Protection --------------------------------------------------------------------------

    @Test
    fun GameTestState.`an aberrant spectre drains an unprotected player`() =
        runGameTest(*scripts) {
            prepare()
            val spectre = monster(41, "Aberrant spectre")
            hitPlayerFrom(spectre, HitType.Magic, 5)
            assertTrue(attackLevel() < 80, "Attack should be drained, was ${attackLevel()}")
        }

    @Test
    fun GameTestState.`a nose peg or a Slayer helmet protects from aberrant spectres`() =
        runGameTest(*scripts) {
            prepare()
            val spectre = monster(41, "Aberrant spectre")
            player.hat = obj(SlayerObjs.nose_peg)
            hitPlayerFrom(spectre, HitType.Magic, 5)
            assertEquals(80, attackLevel())

            player.hat = obj(SlayerObjs.helm)
            hitPlayerFrom(spectre, HitType.Magic, 5)
            assertEquals(80, attackLevel(), "The Slayer helmet has a nose peg in it")
        }

    @Test
    fun GameTestState.`a mirror shield protects from basilisks and cockatrice`() =
        runGameTest(*scripts) {
            prepare()
            val basilisk = monster(43, "Basilisk")
            hitPlayerFrom(basilisk, HitType.Ranged, 1)
            assertTrue(attackLevel() < 80, "Unshielded")

            prepare()
            player.lefthand = obj(SlayerObjs.mirror_shield)
            hitPlayerFrom(basilisk, HitType.Ranged, 1)
            assertEquals(80, attackLevel())

            prepare()
            player.lefthand = obj(SlayerObjs.v_shield)
            hitPlayerFrom(monster(44), HitType.Ranged, 1)
            assertEquals(80, attackLevel(), "V's shield works too")
        }

    @Test
    fun GameTestState.`banshees need earmuffs`() =
        runGameTest(*scripts) {
            prepare()
            val banshee = monster(38)
            hitPlayerFrom(banshee, HitType.Magic, 1)
            assertTrue(attackLevel() < 80)

            prepare()
            player.hat = obj(SlayerObjs.earmuffs)
            hitPlayerFrom(banshee, HitType.Magic, 1)
            assertEquals(80, attackLevel())
        }

    @Test
    fun GameTestState.`a dust devil without a facemask drains the combat stats completely`() =
        runGameTest(*scripts) {
            prepare()
            hitPlayerFrom(monster(49), HitType.Melee, 1)
            assertEquals(0, attackLevel())

            prepare()
            player.hat = obj(SlayerObjs.facemask)
            hitPlayerFrom(monster(49), HitType.Melee, 1)
            assertEquals(80, attackLevel())
        }

    @Test
    fun GameTestState.`killerwatts hit harder without insulated boots`() =
        runGameTest(*scripts) {
            prepare()
            random.next = 7 // The extra damage roll.
            val before = player.stat(stats.hitpoints)
            hitPlayerFrom(monster(73), HitType.Ranged, 1)
            assertEquals(before - 8, player.stat(stats.hitpoints))
        }

    // --- Finishing ---------------------------------------------------------------------------

    @Test
    fun GameTestState.`a gargoyle cannot be killed by damage alone`() =
        runGameTest(*scripts) {
            prepare()
            val gargoyle = monster(46, "Gargoyle")
            gargoyle.hitpoints = 10
            val builder = hitBuilder(HitType.Melee, 50, null, null)
            eventBus.publish(NpcHitEvents.ModifyAny(gargoyle, builder, player))
            assertEquals(9, builder.damage, "Left on one hitpoint")
        }

    @Test
    fun GameTestState.`a rock hammer finishes a weak gargoyle and a bag of salt a weak rockslug`() =
        runGameTest(*scripts) {
            prepare()
            val gargoyle = monster(46, "Gargoyle")
            gargoyle.hitpoints = 8
            player.invAdd(player.inv, SlayerObjs.rock_hammer, 1)
            val rule = SlayerFinishing.RULES[0]
            player.withProtectedAccess {
                with(SlayerRulesHolder.heldFinishing) {
                    use(gargoyle, SlayerObjs.rock_hammer, rule)
                }
            }
            advance(4)
            assertTrue(
                gargoyle.hitpoints <= 0,
                "The hammer should finish it, hp=${gargoyle.hitpoints}",
            )
            assertTrue(SlayerObjs.rock_hammer in player.inv, "The hammer is kept")

            val slug = monster(51, "Rockslug")
            slug.hitpoints = 5
            player.invAdd(player.inv, SlayerObjs.bag_of_salt, 2)
            val salt = SlayerFinishing.RULES[1]
            player.withProtectedAccess {
                with(SlayerRulesHolder.heldFinishing) { use(slug, SlayerObjs.bag_of_salt, salt) }
            }
            advance(4)
            assertTrue(slug.hitpoints > 0, "Salt on a slug above 4 hitpoints does nothing")
            slug.hitpoints = 4
            player.withProtectedAccess {
                with(SlayerRulesHolder.heldFinishing) { use(slug, SlayerObjs.bag_of_salt, salt) }
            }
            advance(4)
            assertTrue(slug.hitpoints <= 0, "Salt finishes it at 4")
            assertFalse(SlayerObjs.bag_of_salt in player.inv, "Both bags were used")
        }

    @Test
    fun GameTestState.`the Gargoyle Smasher perk finishes the gargoyle on the hit`() =
        runGameTest(*scripts) {
            prepare()
            val gargoyle = monster(46, "Gargoyle")
            gargoyle.hitpoints = 10
            player.invAdd(player.inv, SlayerObjs.rock_hammer, 1)
            engine.setUnlocked(player, 0, true)
            val builder = hitBuilder(HitType.Melee, 4, null, null)
            eventBus.publish(NpcHitEvents.ModifyAny(gargoyle, builder, player))
            assertEquals(10, builder.damage, "The perk takes it the rest of the way")
        }

    // --- Slayer helmet -----------------------------------------------------------------------

    @Test
    fun GameTestState.`a Slayer helmet is made once the perk is bought`() =
        runGameTest(*scripts) {
            prepare()
            for (part in SlayerGear.HELM_PARTS) {
                player.invAdd(player.inv, part, 1)
            }
            player.withProtectedAccess { with(SlayerRulesHolder.heldGear) { assembleHelm() } }
            assertFalse(SlayerObjs.helm in player.inv, "The perk is not bought")

            engine.setUnlocked(player, SlayerGear.HELM_UNLOCK, true)
            player.stats[stats.crafting] = 54
            player.withProtectedAccess { with(SlayerRulesHolder.heldGear) { assembleHelm() } }
            assertFalse(SlayerObjs.helm in player.inv, "55 Crafting is needed")

            player.stats[stats.crafting] = 55
            player.withProtectedAccess { with(SlayerRulesHolder.heldGear) { assembleHelm() } }
            assertTrue(SlayerObjs.helm in player.inv)
            for (part in SlayerGear.HELM_PARTS) {
                assertFalse(part in player.inv, "The parts are used up")
            }
        }

    // --- The black mask bonus ----------------------------------------------------------------

    @Test
    fun GameTestState.`the tables know which task ids the umbrella tasks cover`() =
        runGameTest(*scripts) {
            val metal = SlayerTables.default.tasks.getValue(127)
            assertTrue(metal.covers(109))
        }

    private fun GameTestScope.hitBuilder(
        type: HitType,
        damage: Int,
        weapon: ObjType?,
        secondary: ObjType?,
    ): HitBuilder =
        HitBuilder(
            type = type,
            damage = damage,
            sourceUid = null,
            sourceSlot = null,
            isFromNpc = false,
            isFromPlayer = true,
            clientDelay = 0,
            righthandType = weapon?.let { objTypes[it].id },
            secondaryType = secondary?.let { objTypes[it].id },
            targetHitmark = 0,
            sourceHitmark = 0,
            publicHitmark = null,
            zeroDamageHitmarkLit = null,
            zeroDamageHitmarkTint = null,
            maxDamageHitmarkLit = null,
            targetMaxDamageThreshold = 0,
            sourceMaxDamageThreshold = 0,
        )
}
