package org.rsmod.api.drops

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.type.TypeResolver
import org.rsmod.game.type.npc.NpcType

/** The drop table of every monster, keyed by npc type id. Filled in at server start. */
@Singleton
public class DropTables @Inject constructor() {
    private var tables: Map<Int, DropTable> = emptyMap()

    public val size: Int
        get() = tables.size

    public operator fun get(type: NpcType): DropTable? = tables[TypeResolver[type]]

    public fun forNpc(typeId: Int): DropTable? = tables[typeId]

    /** Replaces every table. Meant to be called once, when the drop data files are loaded. */
    public fun replaceAll(tables: Map<Int, DropTable>) {
        this.tables = tables.toMap()
    }
}
