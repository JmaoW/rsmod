package org.rsmod.content.areas.misc.isleofsouls.relics

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.varps
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.util.ParamMap
import org.rsmod.map.CoordGrid

class LeagueGuardianTest {
    class Deps
    @Inject
    constructor(val guardian: LeagueGuardian, val npcList: NpcList, val npcRegistry: NpcRegistry)

    /** The script the server registers must be the one the test reads. */
    private object GuardianModule : AbstractModule() {
        override fun configure() {
            bind(WorldQueueList::class.java).`in`(Scopes.SINGLETON)
            bind(LeagueGuardian::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp(varps.aggressive_npc)

    private val spot = CoordGrid(0, 50, 50, 20, 20)

    private fun Npc.isFacing(player: Player): Boolean =
        faceEntity.isPlayer && faceEntity.playerSlot == player.slotId

    private fun GameTestState.runGuardianTest(body: GameTestScope.(LeagueGuardian) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            GuardianModule,
            LeagueGuardian::class,
            HeldOpScript::class,
            NpcHitScript::class,
        ) { deps ->
            player.clearInv()
            player.teleport(spot)
            advance(1)
            // Npcs left in the world keep their collision in the map the next test shares, and
            // would stand in a walking guardian's way: delete them when the test ends.
            try {
                body(deps.guardian)
            } finally {
                val leftover = buildList { for (npc in deps.npcList) add(npc) }
                leftover.forEach(deps.npcRegistry::del)
            }
        }

    private fun GameTestScope.choose(chosen: Boolean) {
        val tier = RelicItem.Guardian.tier
        val option = if (chosen) RelicItem.Guardian.option else 0
        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[tier], option)
    }

    /** Clicks option [op] (1 to 4) of the horn in slot 0. */
    private fun GameTestScope.horn(op: Int) {
        val obj = objTypes[player.inv[0]!!]
        val button =
            when (op) {
                1 -> IfButtonOp.Op2
                2 -> IfButtonOp.Op3
                3 -> IfButtonOp.Op4
                else -> IfButtonOp.Op6
            }
        eventBus.publish(IfOverlayButton(player, components.inv_items, 0, obj, button))
        advance(1)
    }

    private fun GameTestScope.monster(
        stab: Int = 0,
        ranged: Int = 0,
        magic: Int = 0,
        at: CoordGrid = spot.translateX(2),
    ): Npc {
        val bonuses =
            mapOf<Int, Any>(
                params.defence_stab.id to stab,
                params.defence_slash.id to stab,
                params.defence_crush.id to stab,
                params.defence_ranged.id to ranged,
                params.defence_magic.id to magic,
            )
        val type =
            npcTypeFactory.create {
                hitpoints = 500
                defaultMode = NpcMode.None
                paramMap = ParamMap(bonuses, bonuses)
                op[1] = "Attack"
            }
        return spawnNpc(at, type)
    }

    @Test
    fun GameTestState.`the horn summons a guardian that follows`() = runGuardianTest { guardian ->
        choose(chosen = true)
        player.inv[0] = InvObj(isle_objs.guardian_horn)
        horn(1)
        val npc = checkNotNull(guardian.guardianOf(player))
        assertEquals(NpcMode.None, npc.mode)
        assertTrue(npc.isFacing(player), "looks at its owner")

        horn(2)
        assertNull(guardian.guardianOf(player), "dismissed")
    }

    @Test
    fun GameTestState.`the horn does nothing without the relic`() = runGuardianTest { guardian ->
        choose(chosen = false)
        player.inv[0] = InvObj(isle_objs.guardian_horn)
        horn(1)
        assertNull(guardian.guardianOf(player))
    }

    @Test
    fun GameTestState.`the guardian hits what the player is fighting`() =
        runGuardianTest { guardian ->
            choose(chosen = true)
            player.inv[0] = InvObj(isle_objs.guardian_horn)
            horn(1)
            val target = monster()
            player.aggressiveNpc = target.uid
            advance(LeagueGuardian.ATTACK_INTERVAL * 4)
            assertTrue(target.hitpoints < 500, "hit points ${target.hitpoints}")
            assertTrue(500 - target.hitpoints <= 4 * LeagueGuardian.MAX_HIT * 2)
            assertNotNull(guardian.guardianOf(player))
        }

    @Test
    fun GameTestState.`it looks at what the player is fighting, and at the player otherwise`() =
        runGuardianTest { guardian ->
            choose(chosen = true)
            player.inv[0] = InvObj(isle_objs.guardian_horn)
            horn(1)
            val npc = checkNotNull(guardian.guardianOf(player))
            val target = monster(at = spot.translateX(3))
            player.aggressiveNpc = target.uid
            advance(2)
            assertTrue(npc.faceEntity.isNpc && npc.faceEntity.npcSlot == target.slotId, "target")
            assertEquals(NpcMode.None, npc.mode)

            player.aggressiveNpc = null
            advance(1)
            assertTrue(npc.isFacing(player), "back to its owner")
        }

    @Test
    fun GameTestState.`with melee it walks up to the target, then back to the player`() =
        runGuardianTest { guardian ->
            choose(chosen = true)
            player.inv[0] = InvObj(isle_objs.guardian_horn)
            horn(1)
            val npc = checkNotNull(guardian.guardianOf(player))
            val target = monster(stab = 0, ranged = 50, magic = 50, at = spot.translateX(5))
            player.aggressiveNpc = target.uid
            advance(LeagueGuardian.ATTACK_INTERVAL * 4)
            val at = npc.coords
            val to = target.coords
            val beside =
                (at.z == to.z && kotlin.math.abs(at.x - to.x) == 1) ||
                    (at.x == to.x && kotlin.math.abs(at.z - to.z) == 1)
            assertTrue(beside, "stands beside its target: $at, target $to")
            assertTrue(target.hitpoints < 500, "struck it: ${target.hitpoints}")

            player.aggressiveNpc = null
            advance(8)
            assertTrue(npc.isWithinDistance(player, 1), "back beside its owner: ${npc.coords}")
        }

    @Test
    fun GameTestState.`ranged and magic attack from where it stands`() =
        runGuardianTest { guardian ->
            choose(chosen = true)
            player.inv[0] = InvObj(isle_objs.guardian_horn)
            horn(1)
            val npc = checkNotNull(guardian.guardianOf(player))
            val target = monster(stab = 300, ranged = 300, magic = -60, at = spot.translateX(5))
            player.aggressiveNpc = target.uid
            advance(LeagueGuardian.ATTACK_INTERVAL * 4)
            assertTrue(npc.isWithinDistance(player, 1), "stayed with its owner: ${npc.coords}")
            assertTrue(target.hitpoints < 500, "hit it: ${target.hitpoints}")
        }

    @Test
    fun GameTestState.`changing the outfit keeps the same guardian where it is`() =
        runGuardianTest { guardian ->
            choose(chosen = true)
            player.inv[0] = InvObj(isle_objs.guardian_horn)
            horn(1)
            val npc = checkNotNull(guardian.guardianOf(player))
            val coords = npc.coords
            horn(4)
            assertTrue(npc === guardian.guardianOf(player), "the same npc, not a new one")
            assertEquals(coords, npc.coords)
            assertEquals(GuardianNpcs.outfits[1].id, npc.visType.id)
            assertTrue(npc.isFacing(player), "still looks at its owner")
        }

    @Test
    fun GameTestState.`it picks the style with the lowest defence`() = runGuardianTest { guardian ->
        assertEquals(
            HitType.Melee,
            guardian.weakestStyle(monster(stab = 0, ranged = 50, magic = 50)),
        )
        assertEquals(
            HitType.Ranged,
            guardian.weakestStyle(monster(stab = 80, ranged = 10, magic = 50)),
        )
        assertEquals(
            HitType.Magic,
            guardian.weakestStyle(monster(stab = 80, ranged = 50, magic = -10)),
        )
    }

    @Test
    fun GameTestState.`a ranged or magic attack also hits the npcs next to the target`() =
        runGuardianTest { _ ->
            choose(chosen = true)
            player.inv[0] = InvObj(isle_objs.guardian_horn)
            horn(1)
            val target = monster(stab = 300, ranged = 300, magic = -60, at = spot.translateX(3))
            val beside = monster(stab = 300, ranged = 300, magic = -60, at = spot.translateX(4))
            val far = monster(stab = 300, ranged = 300, magic = -60, at = spot.translateX(7))
            player.aggressiveNpc = target.uid
            advance(LeagueGuardian.ATTACK_INTERVAL * 4)
            assertTrue(target.hitpoints < 500)
            assertTrue(beside.hitpoints < 500, "the npc beside it is hit too")
            assertEquals(500, far.hitpoints)

            horn(3)
            val before = beside.hitpoints
            advance(LeagueGuardian.ATTACK_INTERVAL * 4)
            assertEquals(before, beside.hitpoints, "area attacks switched off")
        }

    @Test
    fun GameTestState.`the guardian leaves after thirty minutes`() = runGuardianTest { guardian ->
        choose(chosen = true)
        player.inv[0] = InvObj(isle_objs.guardian_horn)
        horn(1)
        assertNotNull(guardian.guardianOf(player))
        advance(LeagueGuardian.DURATION_TICKS)
        assertNull(guardian.guardianOf(player))
    }
}
