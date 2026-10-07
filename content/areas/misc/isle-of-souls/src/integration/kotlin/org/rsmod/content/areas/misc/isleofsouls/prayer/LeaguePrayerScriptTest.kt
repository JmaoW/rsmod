package org.rsmod.content.areas.misc.isleofsouls.prayer

import com.google.inject.AbstractModule
import com.google.inject.Provides
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.interfaces.prayer.tab.PrayerRepository
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.enums.EnumTypeMapResolver
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjTypeList

/** The test harness does not install the prayer tab's module, so this provides its repository. */
private object PrayerRepositoryModule : AbstractModule() {
    @Provides
    fun repository(enumResolver: EnumTypeMapResolver, objTypes: ObjTypeList): PrayerRepository {
        val repo = PrayerRepository(enumResolver, objTypes)
        repo.load()
        return repo
    }
}

class LeaguePrayerScriptTest {
    private fun GameTestScope.locType(internal: String): UnpackedLocType =
        locTypes.values.first { it.internalName == internal }

    private fun GameTestState.runPrayerTest(body: GameTestScope.() -> Unit) =
        runInjectedGameTest(
            PrayerRepository::class,
            PrayerRepositoryModule,
            LeaguePrayerScript::class,
        ) { _ ->
            body()
        }

    @Test
    fun GameTestState.`every prayer with an unlock varbit is unlocked on login`() = runPrayerTest {
        player.setVarBit(varbits.rigour_unlocked, 0)
        player.setVarBit(varbits.augury_unlocked, 0)
        player.setVarBit(varbits.preserve_unlocked, 0)
        player.setVarBit(varbits.kr_knightwaves_state, 0)

        eventBus.publish(SessionStateEvent.Login(player))

        assertEquals(1, player.vars[varbits.rigour_unlocked])
        assertEquals(1, player.vars[varbits.augury_unlocked])
        assertEquals(1, player.vars[varbits.preserve_unlocked])
        assertEquals(8, player.vars[varbits.kr_knightwaves_state])
    }

    @Test
    fun GameTestState.`unlocking never lowers a varbit that is already higher`() = runPrayerTest {
        player.setVarBit(varbits.kr_knightwaves_state, 10)

        eventBus.publish(SessionStateEvent.Login(player))

        assertEquals(10, player.vars[varbits.kr_knightwaves_state])
    }

    @Test
    fun GameTestState.`both altars are placed beside the bank when the game starts`() =
        runPrayerTest {
            eventBus.publish(GameLifecycle.Startup)

            assertNotNull(findLoc(LeaguePrayerScript.ALTAR, locType("altar")))
            assertNotNull(findLoc(LeaguePrayerScript.CHAOS_ALTAR, locType("chaosaltar")))
        }
}
