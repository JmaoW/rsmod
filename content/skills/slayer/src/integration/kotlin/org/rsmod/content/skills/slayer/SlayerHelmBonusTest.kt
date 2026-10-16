package org.rsmod.content.skills.slayer

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.maxhit.melee.PvNMeleeMaxHit
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.hat
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.slayer.configs.SlayerHeadgear
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.content.skills.slayer.configs.SlayerParams
import org.rsmod.content.skills.slayer.scripts.SlayerEngine
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.ObjType

class HelmTestDependencies
@Inject
constructor(val maxHit: PvNMeleeMaxHit, val engine: SlayerEngine)

object HelmTestModule : AbstractModule() {
    override fun configure() {
        bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
    }
}

/** The black mask and Slayer helmet add to what a player hits on their task, and only there. */
class SlayerHelmBonusTest {
    private fun GameTestScope.maxHit(
        deps: HelmTestDependencies,
        wearing: ObjType?,
        onTask: Boolean,
    ): Int {
        val engine = deps.engine
        val master = checkNotNull(engine.tables.master("vannaka"))
        val spectres = master.tasks.first { it.task.id == 41 }
        player.stats[stats.attack] = 99
        player.stats[stats.strength] = 99
        player.appearance.combatLevel = 100
        player.hat = wearing?.let { InvObj(objTypes[it]) }
        engine.cancel(player)
        if (onTask) {
            random.next = 50
            engine.assign(player, master, spectres)
        }
        val npcType =
            npcTypes.values.first { it.param(SlayerParams.task_id) == 41 && it.vislevel > 0 }
        return deps.maxHit.computeMaxHit(
            source = player,
            target = npcType,
            targetCurrHp = 90,
            targetMaxHp = 90,
            attackType = MeleeAttackType.Slash,
            attackStyle = null,
            specialMultiplier = 1.0,
        )
    }

    @Test
    fun GameTestState.`the Slayer helmet and black mask are marked for the combat code`() =
        runInjectedGameTest(
            HelmTestDependencies::class,
            HelmTestModule,
            WeaponAttackStylesScript::class,
        ) {
            assertTrue(objTypes[SlayerObjs.helm].param(params.slayer_helm) != 0)
            assertTrue(objTypes[SlayerObjs.helm_imbued].param(params.slayer_helm_imbued) != 0)
            assertTrue(objTypes[SlayerObjs.black_mask].param(params.blackmask) != 0)
            assertTrue(SlayerHeadgear.helms.all { objTypes[it].param(params.slayer_helm) != 0 })
        }

    @Test
    fun GameTestState.`a Slayer helmet raises the max hit on the task monster only`() =
        runInjectedGameTest(
            HelmTestDependencies::class,
            HelmTestModule,
            WeaponAttackStylesScript::class,
        ) {
            val bare = maxHit(it, null, onTask = true)
            val offTask = maxHit(it, SlayerObjs.helm, onTask = false)
            val onTask = maxHit(it, SlayerObjs.helm, onTask = true)
            assertEquals(bare, offTask, "No bonus off the task")
            assertTrue(onTask > bare, "On the task $onTask should beat $bare")
        }

    @Test
    fun GameTestState.`a black mask does the same`() =
        runInjectedGameTest(
            HelmTestDependencies::class,
            HelmTestModule,
            WeaponAttackStylesScript::class,
        ) {
            val bare = maxHit(it, null, onTask = true)
            val masked = maxHit(it, SlayerObjs.black_mask, onTask = true)
            assertTrue(masked > bare, "Black mask $masked should beat $bare")
        }
}
