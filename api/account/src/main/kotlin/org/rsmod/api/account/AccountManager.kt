package org.rsmod.api.account

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.util.concurrent.atomic.AtomicLong
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.loader.AccountLoaderService
import org.rsmod.api.account.loader.request.AccountLoadAuth
import org.rsmod.api.account.loader.request.AccountLoadCallback
import org.rsmod.api.account.loader.request.AccountLoadRequest
import org.rsmod.api.account.saver.AccountSavingService
import org.rsmod.api.account.saver.request.AccountSaveCallback
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.game.entity.Player

public class AccountManager
@Inject
constructor(
    private val loader: AccountLoaderService,
    private val saver: AccountSavingService,
    private val repository: CharacterAccountRepository,
    private val pipelines: Set<CharacterDataStage.Pipeline>,
) {
    private val logger = InlineLogger()

    private val saveSequence = AtomicLong()

    /**
     * Copies the state of [player] and queues it to be saved; [callback] is invoked once the save
     * completes or gives up.
     *
     * The copy is taken right here, so this must be called on the game thread. That keeps the save
     * consistent while the player carries on playing: nothing after this call is part of the save.
     */
    public fun save(player: Player, callback: AccountSaveCallback) {
        val request = createRequest(player, callback)
        saver.queue(request)
    }

    /**
     * Saves [player] without logging them out. Failures are only logged: the next autosave, or the
     * save on logout, writes the same data again.
     */
    public fun autosave(player: Player) {
        save(player) { response ->
            if (response is AccountSaveResponse.Failure) {
                logger.warn { "Could not autosave player account: ${response.player} ($response)" }
            }
        }
    }

    private fun createRequest(player: Player, callback: AccountSaveCallback): AccountSaveRequest {
        val accountId = player.accountId
        val characterId = player.characterId
        val sequence = saveSequence.incrementAndGet()
        val snapshots = ArrayList<CharacterDataStage.Snapshot>(pipelines.size + 1)
        snapshots += repository.snapshot(player, accountId, characterId)
        for (pipeline in pipelines) {
            snapshots += pipeline.snapshot(player, characterId)
        }
        return AccountSaveRequest(accountId, characterId, player, snapshots, sequence, callback)
    }

    public fun load(
        auth: AccountLoadAuth,
        loginName: String,
        callback: AccountLoadCallback,
    ): Boolean {
        val loadRequest = AccountLoadRequest.StrictSearch(auth, loginName, callback)
        return loader.queue(loadRequest)
    }

    public fun loadOrCreate(
        auth: AccountLoadAuth,
        loginName: String,
        hashedPassword: () -> String,
        callback: AccountLoadCallback,
    ): Boolean {
        val loadRequest =
            AccountLoadRequest.SearchOrCreateWithPassword(hashedPassword, auth, loginName, callback)
        return loader.queue(loadRequest)
    }

    public fun isLoaderShuttingDown(): Boolean = loader.isShuttingDown()

    public fun isLoaderRejectingRequests(): Boolean = loader.isTemporarilyRejectingRequests()
}
