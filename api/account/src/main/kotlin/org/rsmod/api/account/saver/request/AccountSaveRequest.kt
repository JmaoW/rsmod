package org.rsmod.api.account.saver.request

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.game.entity.Player

/**
 * A request to write a [snapshot] of [player]'s state to the database.
 *
 * The snapshot is a detached copy taken on the game thread, so the saving thread never reads the
 * live [player] (it is only kept for logging and the [callback] response).
 *
 * @param sequence orders requests for the same character. A request is skipped if a newer one has
 *   already been written, so a retried older save can never overwrite a newer one.
 */
public data class AccountSaveRequest(
    val accountId: Int,
    val characterId: Int,
    val player: Player,
    val snapshot: List<CharacterDataStage.Snapshot>,
    val sequence: Long,
    val callback: AccountSaveCallback,
    var attempts: Int = 0,
)
