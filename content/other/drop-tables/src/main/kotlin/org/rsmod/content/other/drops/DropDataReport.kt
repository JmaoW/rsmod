package org.rsmod.content.other.drops

/**
 * Everything the last load of the drop data files skipped because it could not be found in the
 * cache (an item or monster the data names that does not exist). The server logs the first few;
 * this keeps all of them for tests and tools.
 */
public object DropDataReport {
    @Volatile
    public var warnings: List<String> = emptyList()
        internal set

    /** Boosted drops (as `monster: item`) that matched no drop in any monster's table. */
    @Volatile
    public var unmatchedBoosts: List<String> = emptyList()
        internal set
}
