package org.rsmod.content.interfaces.collectionlog

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.type.TypeListMap

/** One category of the log (a boss, a clue tier, a minigame). */
class CollectionCategory(
    /** The category struct id the client's scripts switch on. */
    val struct: Int,
    val tab: Int,
    /** The position in the tab's list, which is the child index of its row. */
    val row: Int,
    val name: String,
    /** The objs the category lists, as the client lists them. */
    val objs: IntArray,
)

/** What a logged obj is stored as, and where it is listed. */
class CollectionEntry(
    /** The obj as listed by the log, shown in the overview and used for names. */
    val listed: Int,
    /** The obj the client looks for in the inventory (a few items have an alias, see below). */
    val key: Int,
    val categories: List<CollectionCategory>,
)

/** What the client keeps for each tab: its struct and the components it draws the rows into. */
class CollectionTab(
    val index: Int,
    val struct: Int,
    val name: String,
    val categories: List<CollectionCategory>,
    /** Packed components of the tab's container, row background, row text and scrollbar. */
    val container: Int,
    val background: Int,
    val text: Int,
    val scrollbar: Int,
)

/**
 * The structure of the collection log, read from the same cache enums and structs the client draws
 * it from: tab structs 471-475 (param 683 the enum of its category structs, 685-688 its
 * components), category structs (689 name, 690 the enum of its objs) and enum 3721, which maps a
 * few listed objs to the obj the client counts in the log inventory.
 */
class CollectionCatalog(
    val tabs: List<CollectionTab>,
    private val entries: Map<Int, CollectionEntry>,
) {
    val categories: List<CollectionCategory> = tabs.flatMap { it.categories }

    private val byStruct: Map<Int, CollectionCategory> = categories.associateBy { it.struct }

    /** The number of slots the log has in total, and in each tab. */
    val slots: Int = categories.sumOf { it.objs.size }
    val tabSlots: IntArray =
        IntArray(tabs.size) { tab -> tabs[tab].categories.sumOf { it.objs.size } }

    /** The entry for [obj] if the log lists it. */
    fun entryOf(obj: Int): CollectionEntry? = entries[obj]

    fun category(struct: Int): CollectionCategory? = byStruct[struct]

    /** Every obj the log lists. */
    val listedObjs: Set<Int>
        get() = entries.keys

    companion object {
        const val TAB_STRUCT_FIRST = 471
        const val TAB_COUNT = 5

        private const val PARAM_TAB_NAME = 682
        private const val PARAM_TAB_CATEGORIES = 683
        private const val PARAM_TAB_CONTAINER = 685
        private const val PARAM_TAB_BACKGROUND = 686
        private const val PARAM_TAB_TEXT = 687
        private const val PARAM_TAB_SCROLLBAR = 688
        private const val PARAM_CATEGORY_NAME = 689
        private const val PARAM_CATEGORY_OBJS = 690
        private const val ENUM_KEY_ALIAS = 3721

        /**
         * Builds the catalog from raw cache data: [structs] maps a struct id to its params, [enums]
         * maps an enum id to its entries.
         */
        fun build(
            structs: (Int) -> Map<Int, Any>?,
            enums: (Int) -> Map<Any, Any?>?,
        ): CollectionCatalog {
            val alias =
                enums(ENUM_KEY_ALIAS)
                    ?.entries
                    ?.mapNotNull { (k, v) ->
                        (k as? Number)?.toInt()?.let { it to (v as Number).toInt() }
                    }
                    ?.toMap() ?: emptyMap()

            val tabs = ArrayList<CollectionTab>(TAB_COUNT)
            val grouped = HashMap<Int, MutableList<CollectionCategory>>()
            for (tabIndex in 0 until TAB_COUNT) {
                val tabStruct = TAB_STRUCT_FIRST + tabIndex
                val params =
                    structs(tabStruct) ?: error("Collection log tab struct missing: $tabStruct")
                val categoryEnum =
                    enums((params[PARAM_TAB_CATEGORIES] as Number).toInt())
                        ?: error("Collection log category enum missing for tab $tabStruct")
                val categories = ArrayList<CollectionCategory>()
                for (row in 0 until categoryEnum.size) {
                    val struct = (categoryEnum[row] as Number).toInt()
                    val category = structs(struct) ?: error("Category struct missing: $struct")
                    val objEnum =
                        enums((category[PARAM_CATEGORY_OBJS] as Number).toInt())
                            ?: error("Category enum missing for struct $struct")
                    val objs = IntArray(objEnum.size) { (objEnum[it] as Number).toInt() }
                    categories +=
                        CollectionCategory(
                            struct = struct,
                            tab = tabIndex,
                            row = row,
                            name = category[PARAM_CATEGORY_NAME] as String,
                            objs = objs,
                        )
                }
                tabs +=
                    CollectionTab(
                        index = tabIndex,
                        struct = tabStruct,
                        name = params[PARAM_TAB_NAME] as String,
                        categories = categories,
                        container = (params[PARAM_TAB_CONTAINER] as Number).toInt(),
                        background = (params[PARAM_TAB_BACKGROUND] as Number).toInt(),
                        text = (params[PARAM_TAB_TEXT] as Number).toInt(),
                        scrollbar = (params[PARAM_TAB_SCROLLBAR] as Number).toInt(),
                    )
                for (category in categories) {
                    for (obj in category.objs) {
                        grouped.getOrPut(obj) { ArrayList() } += category
                    }
                }
            }

            val entries =
                grouped.mapValues { (obj, categories) ->
                    CollectionEntry(obj, alias[obj] ?: obj, categories)
                }
            return CollectionCatalog(tabs, entries)
        }
    }
}

/** Reads the [CollectionCatalog] from the loaded cache once, the first time it is used. */
@Singleton
class CollectionCatalogProvider @Inject constructor(private val types: TypeListMap) {
    val catalog: CollectionCatalog by lazy {
        CollectionCatalog.build(
            structs = { id -> types.structs.types[id]?.paramMap?.primitiveMap },
            enums = { id -> types.enums.types[id]?.primitiveMap },
        )
    }
}
