package org.rsmod.api.drops

/**
 * A different version of a monster's drop table, for a kill made in a certain state. The wiki's
 * revenant tables are the case: a skulled killer has other rates, and so does a killer on task.
 * Only the tables that have a variant use it; a monster without one drops its usual table.
 *
 * A variant is chosen by a [NpcDropEvents.Modify] subscriber, which sets the event's
 * [NpcDropEvents.Modify.variant].
 */
public enum class DropVariant {
    /** The killer is skulled. */
    SKULLED,

    /** The killer is on a Slayer task for the monster. */
    ON_TASK,

    /** The killer is skulled and on a Slayer task for the monster. */
    ON_TASK_SKULLED;

    public companion object {
        /** The variant the data files call [key], or `null` if there is no such variant. */
        public fun fromKey(key: String): DropVariant? =
            entries.firstOrNull { it.key == key.lowercase() }

        private val DropVariant.key: String
            get() =
                when (this) {
                    SKULLED -> "skulled"
                    ON_TASK -> "on-task"
                    ON_TASK_SKULLED -> "on-task skulled"
                }
    }
}
