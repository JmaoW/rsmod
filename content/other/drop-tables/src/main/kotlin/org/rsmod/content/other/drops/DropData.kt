package org.rsmod.content.other.drops

/**
 * The shape of a drop data file (`osrs.toml`, `isle.toml`). A file is a list of `[[shared]]` tables
 * (such as the rare drop table) and a list of `[[monster]]` tables.
 *
 * ```toml
 * [[shared]]
 * name = "rare_drop_table"
 * main = [{ item = "Nature rune", qty = "67", chance = "5/128" }]
 *
 * [[monster]]
 * name = "Goblin"
 * levels = [2, 5]
 * always = [{ item = "Bones" }]
 * main = [{ item = "Bronze spear", chance = "4/128" }, { table = "rare_drop_table", chance = "1/128" }]
 * tertiary = [{ item = "Clue scroll (easy)", chance = "1/128" }]
 * ```
 */
internal data class DropFile(
    val shared: List<SharedDef> = emptyList(),
    val monster: List<MonsterDef> = emptyList(),
    val boost: List<BoostDef> = emptyList(),
)

/**
 * Marks [item] in the drops of [source] as boosted: the league's relics make it more likely (see
 * `NpcDropEvents.Modify`). [source] is a monster's `source` (or `name`) as written in the data.
 */
internal data class BoostDef(val source: String, val item: String)

/** A table that is not tied to a monster and can be rolled from another table's entries. */
internal data class SharedDef(val name: String, val main: List<EntryDef> = emptyList())

/**
 * The drops of every npc called [name] (case does not matter). [levels] limits it to npcs with one
 * of those combat levels, [npcs] adds npcs by internal name and [ids] adds npcs by type id. A
 * monster in a later file adds to the table of an earlier file, unless [replace] is `true`, which
 * replaces it. [source] is a label that `[[boost]]` lines refer to (it defaults to [name]); the
 * wiki import sets it to the wiki page name and lists [ids] instead of a [name].
 *
 * [inherit] lists the `source`s of other monsters whose drops this monster also rolls (the echo
 * bosses roll their normal boss's drops as well as their own unique). A monster with no [name],
 * [ids] or [npcs] is a table that is not attached to any npc yet. Attach it later with a monster
 * that has the same `source`, an npc selector, and `attach = true`: it takes that earlier table as
 * it is and gives it to those npcs.
 */
internal data class MonsterDef(
    val name: String? = null,
    val source: String? = null,
    val ids: List<Int> = emptyList(),
    val inherit: List<String> = emptyList(),
    val attach: Boolean = false,
    val mainRolls: Int = 1,
    val levels: List<Int> = emptyList(),
    val npcs: List<String> = emptyList(),
    val replace: Boolean = false,
    val always: List<EntryDef> = emptyList(),
    val main: List<EntryDef> = emptyList(),
    val tertiary: List<EntryDef> = emptyList(),
)

/**
 * One drop line. Exactly one of [item] (the item's name), [obj] (an item's internal name) or
 * [table] (a `[[shared]]` table) is set. [qty] is `5` or `1-5` (default `1`), [noted] hands out the
 * noted version, and [chance] is `3/128`, `12.5%` or `0.25` (not used by `always` lines). A
 * [boosted] line is affected by the relics' drop rate boost; `[[boost]]` lines can set this too.
 */
internal data class EntryDef(
    val item: String? = null,
    val obj: String? = null,
    val table: String? = null,
    val qty: String? = null,
    val noted: Boolean = false,
    val chance: String? = null,
    val boosted: Boolean = false,
)
