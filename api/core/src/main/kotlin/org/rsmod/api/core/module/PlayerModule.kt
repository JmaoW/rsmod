package org.rsmod.api.core.module

import com.google.inject.Provider
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import org.rsmod.api.game.process.player.RunDrainMod
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.ShuffledPlayerList
import org.rsmod.module.ExtendedModule

public object PlayerModule : ExtendedModule() {
    override fun bind() {
        Multibinder.newSetBinder(binder(), RunDrainMod::class.java)
        bindInstance<MusicPlayer>()
        bindInstance<ProtectedAccessContextFactory>()
        bindInstance<ProtectedAccessLauncher>()
        bindProvider(ShuffledPlayerListProvider::class.java)
    }

    private class ShuffledPlayerListProvider
    @Inject
    constructor(private val playerList: PlayerList) : Provider<ShuffledPlayerList> {
        override fun get(): ShuffledPlayerList = ShuffledPlayerList(playerList)
    }
}
