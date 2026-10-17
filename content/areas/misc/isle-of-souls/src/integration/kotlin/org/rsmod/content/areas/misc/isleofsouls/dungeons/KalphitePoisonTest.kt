package org.rsmod.content.areas.misc.isleofsouls.dungeons

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid

internal object KalphiteTestNpcs : NpcReferences() {
    val worker = find("kalphite_worker")
}

class KalphitePoisonTest {
    class Deps
    @Inject
    constructor(val poison: PlayerPoison, val interactions: AiPlayerInteractions)

    private object PoisonModule : AbstractModule() {
        override fun configure() {
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            bind(PlayerPoison::class.java).`in`(Scopes.SINGLETON)
            bind(KalphitePoisonScript::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private fun GameTestState.runPoisonTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            PoisonModule,
            KalphitePoisonScript::class,
            PlayerHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
        ) { deps ->
            deps.poison.cure(player)
            try {
                body(deps)
            } finally {
                deps.poison.cure(player)
            }
        }

    /** The lair's main cave on the top level, where its soldiers and guardians stand. */
    private val at = CoordGrid(3484, 9510, 2)

    /**
     * Fights [type] until the player is poisoned or [ticks] have gone; returns the poison damage.
     */
    private fun GameTestScope.fightUntilPoisoned(
        deps: Deps,
        type: NpcType,
        ticks: Int = 3_000,
    ): Int {
        allocZoneCollision(at)
        val npc = spawnNpc(at, npcTypes[type])
        val stand = at.translateX(npcTypes[type].size)
        allocZoneCollision(stand)
        player.placeAt(stand)
        advance(ticks = 3)
        player.stats[stats.hitpoints] = 99
        npc.opPlayer2(player, deps.interactions)
        var tick = 0
        while (!deps.poison.isPoisoned(player) && tick++ < ticks) {
            advance()
            player.stats[stats.hitpoints] = 99
        }
        val damage = deps.poison.damageOf(player)
        npcRepo.del(npc, Int.MAX_VALUE)
        return damage
    }

    @Test
    fun GameTestState.`a kalphite soldier's bite can poison for four`() = runPoisonTest { deps ->
        assertEquals(4, fightUntilPoisoned(deps, KalphiteNpcTypes.soldier))
        assertEquals(4, KalphitePoisonScript.SOLDIER_POISON)
    }

    @Test
    fun GameTestState.`a kalphite guardian's claws can poison for six`() = runPoisonTest { deps ->
        assertEquals(6, fightUntilPoisoned(deps, KalphiteNpcTypes.guardian))
        assertEquals(6, KalphitePoisonScript.GUARDIAN_POISON)
    }

    @Test
    fun GameTestState.`a worker never poisons`() = runPoisonTest { deps ->
        val damage = fightUntilPoisoned(deps, KalphiteTestNpcs.worker, ticks = 600)
        assertEquals(0, damage)
        assertFalse(deps.poison.isPoisoned(player))
    }
}
