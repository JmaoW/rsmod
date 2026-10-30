package org.rsmod.content.other.drops

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.drops.DropChance
import org.rsmod.api.drops.DropEntry
import org.rsmod.api.drops.DropOutcome
import org.rsmod.api.drops.DropStack
import org.rsmod.api.drops.DropTable
import org.rsmod.api.drops.DropVariant
import org.rsmod.api.parsers.toml.Toml
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType

/** The raw text of one drop data file, and the name to blame in error messages. */
internal class DropSource(val name: String, val bytes: ByteArray)

/**
 * The drop tables built from a set of files, keyed by npc type id. [warnings] lists everything that
 * was skipped because it could not be found in the cache (an item or monster the data names that
 * does not exist). Malformed data is not a warning: it makes loading fail.
 */
internal class DropLoadResult(
    val tables: Map<Int, DropTable>,
    val warnings: List<String>,
    /** The variant tables (skulled, on task) of the monsters that have them, by npc type id. */
    val variants: Map<Int, Map<DropVariant, DropTable>> = emptyMap(),
    /** `[[boost]]` lines (as `source: item`) that matched no drop in any monster's table. */
    val unmatchedBoosts: List<String> = emptyList(),
    val boostedEntries: Int = 0,
)

/**
 * Builds drop tables from the data files, in order: a monster in a later file adds to the same
 * monster from an earlier file (or replaces it). This is what keeps the imported OSRS data and the
 * isle's own changes in separate files.
 */
internal class DropTableLoader
@Inject
constructor(@Toml private val mapper: ObjectMapper, objTypes: ObjTypeList, npcTypes: NpcTypeList) {
    /**
     * Many names are shared by several objs (`fake_coins` is also called "Coins", and so is the
     * decorative `viking_beerkeg` "Keg of beer"). The one the wiki means is the one whose internal
     * name is the display name (`coins`), then [preferredObjs], then the lowest id.
     */
    private val objsByName: Map<String, UnpackedObjType> =
        objTypes.values
            .filter { !it.isCert && it.placeholdertemplate == 0 }
            .filter { it.name.isNotBlank() && !it.name.equals("null", ignoreCase = true) }
            .groupBy { it.name.lowercase() }
            .mapValues { (name, candidates) ->
                val wanted = preferredObjs[name] ?: internalNameOf(name)
                candidates.firstOrNull { it.internalName == wanted } ?: candidates.minBy { it.id }
            }

    private val objsByInternal: Map<String, UnpackedObjType> =
        objTypes.values.filter { it.internalName != null }.associateBy { it.internalNameValue }

    private val notedObjs: Map<Int, UnpackedObjType> =
        objTypes.values.filter { it.isCert }.associateBy { it.certlink }

    private val npcsByName: Map<String, List<UnpackedNpcType>> =
        npcTypes.values.filter { it.name.isNotBlank() }.groupBy { it.name.lowercase() }

    private val npcsById: Map<Int, UnpackedNpcType> = npcTypes

    private val npcsByInternal: Map<String, UnpackedNpcType> =
        npcTypes.values.filter { it.internalName != null }.associateBy { it.internalNameValue }

    fun load(sources: List<DropSource>): DropLoadResult {
        val warnings = ArrayList<String>()
        val files = sources.map { it to parse(it) }

        val sharedDefs = LinkedHashMap<String, SharedDef>()
        for ((_, file) in files) {
            for (shared in file.shared) {
                sharedDefs[shared.name.lowercase()] = shared
            }
        }
        val shared = SharedTables(sharedDefs, warnings)

        val boosts = Boosts(files.flatMap { it.second.boost })

        val drafts = HashMap<Int, Draft>()
        val variantDrafts = HashMap<Int, HashMap<DropVariant, Draft>>()
        val sourceDrafts = HashMap<String, Draft>()
        for ((source, file) in files) {
            for (def in file.monster) {
                val label = "${source.name}: `${def.label()}`"
                val npcs = resolveNpcs(def, label, warnings)
                val variant =
                    def.variant?.let { key ->
                        DropVariant.fromKey(key).also {
                            if (it == null) warnings += "$label: unknown variant `$key`."
                        }
                    }
                if (def.variant != null && variant == null) {
                    continue
                }
                val block =
                    if (def.attach) {
                        sourceDrafts[def.label().lowercase()].also {
                            if (it == null)
                                warnings += "$label: `attach` found no table with that source."
                        }
                    } else {
                        draft(def, label, shared, boosts, warnings).let { own ->
                            inherit(def, label, own, sourceDrafts, warnings)
                        }
                    }
                if (block == null) {
                    continue
                }
                if (variant != null) {
                    for (npc in npcs) {
                        val byVariant = variantDrafts.getOrPut(npc.id) { HashMap() }
                        val existing = byVariant[variant]
                        byVariant[variant] =
                            if (def.replace || existing == null) block else existing + block
                    }
                    continue
                }
                if (!def.attach) {
                    sourceDrafts[def.label().lowercase()] = block
                }
                for (npc in npcs) {
                    val existing = drafts[npc.id]
                    drafts[npc.id] =
                        if (def.replace || existing == null) block else existing + block
                }
            }
        }

        val built = IdentityHashMap<Draft, DropTable>()
        val tables = HashMap<Int, DropTable>()
        for ((npcId, draft) in drafts) {
            tables[npcId] = built.getOrPut(draft) { draft.toTable() }
        }
        val variants = HashMap<Int, Map<DropVariant, DropTable>>()
        for ((npcId, byVariant) in variantDrafts) {
            variants[npcId] =
                byVariant.mapValues { (_, draft) -> built.getOrPut(draft) { draft.toTable() } }
        }
        return DropLoadResult(
            tables = tables,
            warnings = warnings,
            unmatchedBoosts = boosts.unmatched(),
            boostedEntries = boosts.matchedEntries,
            variants = variants,
        )
    }

    private fun parse(source: DropSource): DropFile =
        try {
            mapper.readValue(source.bytes, DropFile::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Could not read drop data `${source.name}`: ${e.message}",
                e,
            )
        }

    private fun resolveNpcs(
        def: MonsterDef,
        label: String,
        warnings: MutableList<String>,
    ): List<UnpackedNpcType> {
        require(
            def.name != null || def.npcs.isNotEmpty() || def.ids.isNotEmpty() || def.source != null
        ) {
            "$label: needs a `name`, `ids`, `npcs` or `source`."
        }
        val found = LinkedHashMap<Int, UnpackedNpcType>()
        for (id in def.ids) {
            val npc = npcsById[id]
            if (npc == null) {
                warnings += "$label: no npc with id $id."
                continue
            }
            found[npc.id] = npc
        }

        def.name?.let { name ->
            val named = npcsByName[name.lowercase()].orEmpty()
            val matches =
                if (def.levels.isEmpty()) named else named.filter { it.vislevel in def.levels }
            matches.forEach { found[it.id] = it }
        }
        for (internal in def.npcs) {
            val npc = npcsByInternal[internal]
            if (npc == null) {
                warnings += "$label: no npc with internal name `$internal`."
                continue
            }
            found[npc.id] = npc
        }

        val selected = def.name != null || def.npcs.isNotEmpty() || def.ids.isNotEmpty()
        if (found.isEmpty() && selected) {
            warnings += "$label: no npc matched, so these drops are unused."
        }
        return found.values.toList()
    }

    private fun draft(
        def: MonsterDef,
        label: String,
        shared: SharedTables,
        boosts: Boosts,
        warnings: MutableList<String>,
    ): Draft {
        val source = def.label().lowercase()
        val always = def.always.mapNotNull { stack(it, label, warnings) }
        val main = def.main.mapNotNull { entry(it, label, shared, boosts, source, warnings) }
        val tertiary =
            def.tertiary.mapNotNull { entry(it, label, shared, boosts, source, warnings) }
        return Draft(def.label(), always, main, tertiary, def.mainRolls)
    }

    /** Adds the drops of every `inherit`ed source to [own]. */
    private fun inherit(
        def: MonsterDef,
        label: String,
        own: Draft,
        sourceDrafts: Map<String, Draft>,
        warnings: MutableList<String>,
    ): Draft {
        var combined = own
        for (inherited in def.inherit) {
            val base = sourceDrafts[inherited.lowercase()]
            if (base == null) {
                warnings += "$label: inherits `$inherited`, which has no table."
                continue
            }
            combined = (base + combined).named(own.name)
        }
        return combined
    }

    private fun stack(def: EntryDef, label: String, warnings: MutableList<String>): DropStack? {
        require(def.table == null) { "$label: `always` lines cannot be tables: $def" }
        val obj = resolveObj(def, label, warnings) ?: return null
        val qty = parseQuantity(def, label)
        return DropStack(obj, qty.first, qty.last)
    }

    private fun entry(
        def: EntryDef,
        label: String,
        shared: SharedTables,
        boosts: Boosts?,
        source: String?,
        warnings: MutableList<String>,
    ): DropEntry? {
        val chanceText = requireNotNull(def.chance) { "$label: this line needs a `chance`: $def" }
        val chance = parseChance(chanceText, label, def)

        val outcome =
            if (def.table != null) {
                val table = shared[def.table, label] ?: return null
                DropOutcome.Nested(table)
            } else {
                val obj = resolveObj(def, label, warnings) ?: return null
                val qty = parseQuantity(def, label)
                DropOutcome.Item(DropStack(obj, qty.first, qty.last))
            }
        val boosted =
            def.boosted || (boosts != null && source != null && boosts.matches(source, def))
        return DropEntry(chance, outcome, boosted)
    }

    private fun parseChance(text: String, label: String, def: EntryDef): Double =
        try {
            DropChance.parse(text)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$label: ${e.message} (in $def)", e)
        }

    private fun parseQuantity(def: EntryDef, label: String): IntRange =
        try {
            DropChance.parseQuantity(def.qty ?: "1")
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$label: ${e.message} (in $def)", e)
        }

    private fun resolveObj(
        def: EntryDef,
        label: String,
        warnings: MutableList<String>,
    ): UnpackedObjType? {
        val name = def.item ?: def.obj
        require(name != null) { "$label: this line needs an `item`, `obj` or `table`: $def" }
        if (def.item != null && def.item.equals("nothing", ignoreCase = true)) {
            return null
        }
        if (def.item != null && ExcludedDrops.isExcluded(def.item)) {
            return null
        }

        val base = if (def.item != null) objsByName[name.lowercase()] else objsByInternal[name]
        if (base == null) {
            warnings += "$label: no item called `$name`."
            return null
        }
        if (!def.noted) {
            return base
        }
        val noted = notedObjs[base.id]
        if (noted == null) {
            warnings += "$label: `$name` has no noted version."
        }
        return noted
    }

    private companion object {
        /**
         * Names whose internal name is not the display name, so the rule above would not find the
         * item the wiki means. Keyed by lowercase display name.
         */
        val preferredObjs =
            mapOf(
                // The unlit helmet is what cave goblins drop; the lit one is only ever made.
                "mining helmet" to "cave_goblin_mining_helmet_unlit",
                // The Slayer guide's display copy (4150) comes first; 4160 is the real ammunition.
                "broad arrows" to "slayer_broad_arrows",
            )
    }

    private fun internalNameOf(displayName: String): String =
        displayName.replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private fun MonsterDef.label(): String = source ?: name ?: npcs.joinToString().ifEmpty { "ids" }

    /**
     * The `[[boost]]` lines: which items of which monsters the league's drop rate boost applies to.
     * A boost line matches by the monster's `source` (or `name`) and the item's name.
     */
    private class Boosts(lines: List<BoostDef>) {
        private val all: Map<Pair<String, String>, BoostDef> =
            lines.associateBy { it.source.lowercase() to it.item.lowercase() }
        private val used = HashSet<Pair<String, String>>()

        var matchedEntries = 0
            private set

        fun matches(source: String, entry: EntryDef): Boolean {
            val item = entry.item?.lowercase() ?: return false
            val key = source to item
            if (key !in all) {
                return false
            }
            used += key
            matchedEntries++
            return true
        }

        fun unmatched(): List<String> =
            all.filterKeys { it !in used }.values.map { "${it.source}: ${it.item}" }
    }

    /** One monster's entries before they are turned into a [DropTable]. */
    private class Draft(
        val name: String,
        val always: List<DropStack>,
        val main: List<DropEntry>,
        val tertiary: List<DropEntry>,
        val mainRolls: Int = 1,
    ) {
        fun named(newName: String) = Draft(newName, always, main, tertiary, mainRolls)

        operator fun plus(other: Draft) =
            Draft(
                name,
                always + other.always,
                main + other.main,
                tertiary + other.tertiary,
                maxOf(mainRolls, other.mainRolls),
            )

        fun toTable(): DropTable =
            try {
                DropTable(name, always, main, tertiary, mainRolls)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Drop table for `$name`: ${e.message}", e)
            }
    }

    /** Builds each `[[shared]]` table the first time it is asked for. */
    private inner class SharedTables(
        private val defs: Map<String, SharedDef>,
        private val warnings: MutableList<String>,
    ) {
        private val built = HashMap<String, DropTable>()
        private val building = HashSet<String>()

        operator fun get(name: String, label: String): DropTable? {
            val key = name.lowercase()
            built[key]?.let {
                return it
            }
            val def = defs[key]
            if (def == null) {
                warnings += "$label: no shared table called `$name`."
                return null
            }
            check(building.add(key)) { "$label: shared table `$name` includes itself." }
            val sharedLabel = "shared table `${def.name}`"
            val main = def.main.mapNotNull { entry(it, sharedLabel, this, null, null, warnings) }
            building.remove(key)
            val table = DropTable(def.name, main = main)
            built[key] = table
            return table
        }
    }
}
