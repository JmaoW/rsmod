package org.rsmod.game.client

import org.rsmod.game.entity.Player

public interface Client<S, T> {
    public fun close()

    public fun write(message: T)

    public fun read(player: Player)

    public fun flush()

    public fun flushHighPriority()

    public fun unregister(service: S, player: Player)

    /**
     * Sets how far away (in tiles) the player's client is told about npcs; the default is 15. Large
     * arenas raise it so that far-off bosses do not vanish.
     */
    public fun setNpcViewDistance(distance: Int) {}

    /** Puts the npc view distance back to the default. */
    public fun resetNpcViewDistance() {}
}

public interface ClientCycle {
    public fun update(player: Player)

    public fun flush(player: Player)

    public fun release()
}
