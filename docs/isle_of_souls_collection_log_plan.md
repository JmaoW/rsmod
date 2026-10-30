# Isle of Souls collection log: implementation plan

Status: **built 2026-10-19, not yet run in a client** (all phases). See section 10 for what was
built, what differs from this plan and what still has to be checked.

Decision taken: the log is **item-based**. A slot fills when the player *receives* that item from a
drop or a reward, whatever the source. There is no per-source check. That keeps the log to one
small store and one function, and it fits a one-day event.

## 1. What the client already does (decoded from the rev 233 cache)

Nothing about the screen has to be built; the server only feeds it.

| Thing | Where it lives |
| --- | --- |
| Tabs, categories, items, names | Cache. Tab structs 471-475 (Bosses, Raids, Clues, Minigames, Other), param 683 = enum of category structs. Category struct: param 689 name, param 690 = enum (index to obj). **114 categories, 1,810 slots, 1,613 unique objs.** |
| "Is this slot obtained, and how many" | Client, from inventory **620 `collection_transmit`**: `inv_total(620, obj) > 0`. The "Obtained x/y" line and the "You have received N x item" click message are client scripts too. |
| Frame title "Collection Log - x/y" | Varps `collection_count` (2943) and `collection_count_max` (2944). |
| Tab totals (also the journal summary panel) | Varps `collection_count_{bosses,raids,clues,minigames,other}` and `_max` (4612-4622). `prepareSummaryTab` already resyncs them. |
| Kill-count lines under a category | Varps 2048 / 2941 / 2942 (`collection_category_count`, `_count2`, `_count3`), **reused for every category**. The label text is in cs2 2735, a switch on the category struct id: **78 of the 114 categories** have 1 to 3 lines, **97 counters in all** (plus 2 Gnome Restaurant lines that read varbits `collection_aluft_*_deliveries`). A value of 2,147,483,647 or more is shown as "Lots!". |
| Overview screen | Interface 908 (`collection_overview`). Shows a rank and progress bar from `collection_count` (falling back to `collection_count_highscores`, 4612, when that is 0), per-tab progress, and "Latest Collections": the 12 most recent items in varps `collection_overview_last_item0..11` (an **obj id**, -1 for none) with `_date` (4623-4646). The date is a **runeday** (days since the RS epoch); the click message works out "today / yesterday / N days ago" against varbit `current_runeday` (9535), and the server does not set that varbit anywhere today. |
| Moving between log and overview | The burger menu in each screen has "View Collection Log" and "View Collection Overview" ops (built in cs2 7814). Their client scripts only colour the hover; the switch itself must be done by the server. |
| New-item notification option | Varbit `option_collection_new_item` (11959). |

Existing repo state: `interfaces.collection` and the count varps are already declared
(`BaseInterfaces`, `BaseVarps`), and `SummarySideScript.clickCollectionLog` opens interface 621 as
an overlay. Nothing stores or sends any log data, so today the screen opens empty.

Memory note with the full contract: `reference-collection-log-screen.md`.

## 2. Design

### 2.1 Store: inventory 620 itself, made persistent

Edit inv 620 with `InvEditor` (`InvEdits.kt`): `scope = Perm`, `stack = Always`, `size = 2048`,
`protect = false`. One slot per distinct obj, the count is how many times it was received.

* The existing inventory persistence (`inventories` / `inventory_objs`, the bank's path) saves it
  with the character. Up to 1,613 rows per player; fine for SQLite and the snapshot saves.
* The engine already keeps a transmitted inv in step: queue it once at login
  (`transmittedInvAddQueue`) and `PlayerInvUpdateProcessor` sends partial updates when a slot
  changes. Opening the log needs no extra send for items.
* `UpdateInvFull` sends the occupied slot count itself, so the cache's size of 500 should not limit
  it. **This is the one assumption to confirm in a real client.** Fallback: keep the store as a
  second Perm inv (new inv name in `inv.sym`, so a packCache) and send only the clicked
  category (at most 134 items) through a Temp inv 620, which is the vanilla way.
* A single obj in several categories (1,810 slots vs 1,613 unique) fills all of them for free,
  because the client looks items up by obj.

### 2.2 One entry point

```kotlin
class CollectionLog @Inject constructor(...) {
    fun receive(player: Player, obj: ObjType, count: Int = 1)   // the only writer
    fun owned(player: Player, obj: ObjType): Int
    fun slotsFilled(player: Player, category: CollectionCategory): Int
}
```

`receive` does, in order: ignore objs that are in no category (after resolving the noted and
placeholder forms to the base obj); add to inv 620; if it was new: bump the counts, push it onto
the "latest" ring, message the player, publish a `CollectionLogSlotFilled` event for the tasks.

### 2.3 Category data at startup

A `CollectionCatalog` singleton reads the same enums the client does, from the loaded cache types
(tab structs 471-475, then each category's enum), and builds: `obj -> categories`, `category ->
objs`, `tab -> total slots`. The max varps are set from it, never hard-coded. No data file in the
repo, so it cannot drift from the client.

### 2.4 Counting

* Tab and total counts are **slots**, not unique objs (an item in two categories counts twice, like
  the real log).
* Recompute all count varps from the inventory on login (cheap), then update incrementally in
  `receive`. Keeps the summary panel right for existing characters and after any edit.

### 2.5 Screen handling (new module `content/interfaces/collection-log`)

1. On open (`onIfOpen(interfaces.collection)`): `ifSetEvents` for the tab components (4-8, op1) and
   the category row ops (dynamic children, so events must be enabled by the server, as for the
   relic and mastery screens).
2. Tab click: run clientscript `collection_draw_tabs_all` (2388) with the tab, then redraw the list.
3. Category row click: copy that category's kill counters into varps 2048 / 2941 / 2942 (2.6),
   then run `collection_draw_list` (2730) with the selected row.
4. Close button already goes through the overlay handling.
5. Summary panel: opens the **overview** (908), see 2.7. The burger menu in the log switches back
   to it.

Exact component indices and script arguments are to be taken from the disassembly
(`collection_init` 2240, `collection_draw` 7798, `collection_draw_list` 2731); the contract note
lists them. They are **unverified in a real client**, like the other decoded screens.

### 2.6 Kill counts (in scope)

The client shows what the server puts in three shared varps when a category is clicked, so the
server keeps its own counters and copies the right ones across.

**Counters.** One server-only, saved varp per line the client can show: **97 counters**, named
`clog_kc_<category>_<n>` (for example `clog_kc_zulrah_0`, `clog_kc_dagannoth_kings_2`). The
list is generated from the cache by `tools/clog/gen_kc.py` (the category struct, the label and
which of the three varps it uses, decoded from cs2 2735), which writes the `varp.sym` lines and a
Kotlin table `CollectionKillCountData`. All 97 are created up front, even those with no source in
the event, so a source added later needs no new symbol. **New varp names mean one packCache.** The
two Gnome Restaurant lines use the client's own varbits and are set directly.

**Showing them.** On a category click, `CollectionKillCounts.show(player, category)` writes the
category's counters into varps 2048 / 2941 / 2942 (in the order the label table says; a category
with one line uses only the first), then the draw script runs. Categories with no counters just
leave the lines blank.

**Feeding them.**

* Kills: a `NpcDeathEvents.Killed` listener matches the npc's name against a table of
  `counter -> npc names` (like `LeagueKillTasks`), and counts for the hero and, for a team
  instance, every member who was in the fight. The name table lives in
  `collection_kc.toml` beside the generator output because forms need listing by hand (Grotesque
  Guardians = Dusk and Dawn, the Dagannoth Kings by name, the Nightmare and Phosani, Callisto /
  Artio, Venenatis / Spindel, Vet'ion / Calvar'ion, any revenant, Tortured / Demonic Gorilla, and
  the like). The generator seeds it from the labels ("Zulrah kills:" -> "Zulrah") and a person
  fixes the rest.
* Completions and openings (Barrows chests, Fight Caves, Inferno, Gauntlet, Wintertodt and
  Tempoross rewards, Hallowed Sepulchre, clue caskets, Guardians of the Rift, Mixology orders and
  so on): each reward path calls `CollectionKillCounts.add(player, counter)`. These are found by the
  same audit as the item sources (section 3).
* The existing kill-task counters (`isle_count_*`, capped at each task's goal) are **not** reused,
  because they stop counting at the goal.
* Counts are per player and never reset by the log. Fights that publish `Rolled` without `Killed`
  (the team-instance bosses) are covered by listing the counter feed next to their reward code.

**Order of lines.** The client prints the lines in its own order (for example the Theatre of
Blood shows Hard, Entry, then normal). The generator keeps the label-to-varp pairing exactly as the
script has it, so the numbers stay on the right line.

### 2.7 Overview screen (in scope)

* `ifOpenOverlay(interfaces.collection_overview)` from `SummarySideScript.clickCollectionLog`
  (replacing the direct open of 621). It needs a `collection_overview` interface reference in
  `BaseInterfaces` (the interface exists in `interface.sym` as 908).
* **Latest ring.** A `CollectionLatest` helper keeps the 12 most recent new slots in varps
  `collection_overview_last_item0..11` and their `_date`, newest first (shift down on each new
  item). Obj id in the item varp, `RuneTime.runeday()` in the date varp. These are saved with the
  player, so they come back after a relog.
* **Day number.** Set varbit `current_runeday` (9535) at login and again when the day changes
  (the existing map-clock / daily timer if there is one), or "obtained today" comes out wrong.
* **Counts.** Already driven by the count varps of 2.4; `collection_count_highscores` stays 0.
* **Clicks.** Latest-item "Check-obtained" and "Show Menu" are client scripts. The server
  handles the burger menu: "View Collection Log" closes 908 and opens 621; the same menu inside 621
  ("View Collection Overview") does the reverse. Also the per-tab sub-section buttons in 908, which
  presumably open the log on that tab. As for 621, the dynamic ops need `ifSetEvents`, and the
  exact components are to be read from cs2 7814 and 7811 during the build and checked in a client.
* Close button and the draggable position varcs (`collection_overview_x/y`) are client-only.

## 3. Where items come from

The only requirement is that each reward path calls `CollectionLog.receive`.

| Source | Hook | Covers | State |
| --- | --- | --- | --- |
| Monster drops | `NpcDropEvents.Rolled(npc, hero, drops)` (rolled, before pickup, so it also counts drops the hero never picks up, and drops absorbed by a bracelet) | most of Bosses and Other | exists; also fired by Giants, Scurrius and Wilderness rewards |
| Custom reward code that does not publish `Rolled` | call `receive` directly | Fight Caves / Inferno capes, God Wars, Zulrah, Kalphite Queen if they bypass it | audit in phase 3 |
| Clue caskets | casket reward code | Clues (674 slots) | casket system not built (content plan 3.5); the log needs nothing special once it exists |
| Pets | pet roll code | All Pets, Skilling Pets, boss pets | pet system state to audit |
| Skilling finds, minigame rewards, Barrows, Slayer rewards, random events | each calls `receive` | Other, Minigames | per feature |

Coverage today (item name appears in some drop table): Bosses 205 of 363, Raids 0 of 67, Clues
55 of 674, Minigames 1 of 252, Other 178 of 454, so **about 439 of 1,810 slots are reachable
through drops right now**. The rest come with the features above, or never in this event.

Not counted: shop buys, trades, ground pickups of other players' items, bank withdrawals.

### 3.1 Unobtainable slots (decision needed)

Raids, most Minigames and most Clues can never fill in the event as built. Options:

* A. Leave the full log. Max shown is 1,810 and the grid has many permanent grey slots. Zero work.
* B. Set the `_max` varps to the reachable slots only, so the title reads honestly. Small.
* C. Override the category enums (as `IsleOfSoulsTaskEnums` does for tasks) to drop dead
  categories. Cleanest for players, but a client cache change, so a packCache.

Default: A for v1, B once phase 3 shows what is reachable.

## 4. League tasks

The task list already names 26 collection log tasks (ids 1448-1456 for 5/15/30/50/100/200/350/500/750
slots, and 8-10, 27-40 for the clue-section fills). None is in `LeagueTaskList` yet.

* Listener on `CollectionLogSlotFilled`; check thresholds against the player's total and per-section
  counts; complete through `LeagueTaskTracker.complete`.
* The log is **per player**, tasks are **per team**. A task completes when *any* member reaches the
  threshold (tasks are shared, so one member getting 50 slots is enough). Say so in the teams doc.
* "Clue section" tiers: the tier's own category plus its "(Rare)" category (Hard, Elite, Master).
  The wiki wording is ambiguous; this is a guess to confirm.
* The 1000-slot task (and 750) may be out of reach; keep the thresholds, they are the wiki's.
* Add them in the same change as the log, per the content plan's rule that a feature ships with
  its tasks.

## 5. Notification and announcements

* Chat: `New item added to your collection log: <item>` for the player (and honour
  `option_collection_new_item` for the popup, if the popup is a client script; to decode).
* Optional: a team message for rare items, through the existing `LeagueAnnouncer` hooks. Not
  needed for v1.

## 6. Phases

| # | Work | Size | Needs |
| --- | --- | --- | --- |
| 1 | `CollectionCatalog`, inv 620 edit (Perm, Always, 2048), `CollectionLog.receive`, login transmit + count recompute, count varps, unit tests | M | nothing new in the cache |
| 2 | Kill-count counters: `tools/clog/gen_kc.py` (97 counters, `varp.sym` lines, Kotlin table, seeded `collection_kc.toml`), `CollectionKillCounts` (add / show), the `Killed` listener | M | **packCache** (new varp names) |
| 3 | Collection log module: events on open, tab click, category click (copies the KC varps, sends the draw script), chat message | M | real client to confirm the click flow and the large inv |
| 4 | Overview: `collection_overview` interface ref, `CollectionLatest` ring, `current_runeday` set at login and at midnight, summary panel opens 908, burger menu switching both ways, sub-section buttons | M | real client to confirm the burger menu and button components |
| 5 | Hook every reward path for items and completions, with an audit script `tools/clog/coverage.py` (logged items vs. drop tables vs. code that grants them, KC lines vs. their feeds) and a doc table of what is reachable | M | none |
| 6 | League tasks (26), `::clog` debug commands (`give`, `clear`, `fill`, `kc`) | M | none |

Phases 1-5 give a working log, counts and overview; 6 is what the league needs for points. Phases
2 and 4 can run in either order after 1; the packCache from phase 2 can be done together with the
first client check.

## 7. Tests

* Unit: catalog build (every category resolves, no obj missing from the cache), `receive` (first
  time, repeat, noted form, obj in two categories), counts after login recompute.
* Unit: the generated kill-count table (every counter has a varp, label order matches the cs2
  switch, no category without a label), the latest ring (shift, wrap at 12, runeday stored),
  `show` writes the right varp per line.
* Integration (sandbox, per the testing notes): a `Killed` event for a named boss raises its
  counter; a category click leaves the three varps holding that category's counts;
* Integration (sandbox, per the testing notes): receive via a fake `Rolled` event, check inv 620
  contents and the varps; save and reload a character and check the log persists; task completes at
  a threshold.
* Real client checklist: screen opens, tabs switch, a category shows owned items lit and the rest
  grey, "Obtained x/y" is right, a new item lights up live, the summary panel counts match, the log
  survives relog, and an inv of several hundred slots does not disconnect the client.

## 8. Risks

* Click flow and script arguments are decoded, not observed. Budget one client session.
* Inv 620 larger than the cache's 500 slots (see 2.1) has a defined fallback.
* A reward path that grants items without going through `receive` leaves a hole. The audit script
  in phase 3 is the guard.
* The 97 kill-count varps are new names, so phase 2 needs the packCache before any test can run
  against a real cache. Kill and completion feeds are only as complete as the audit makes them:
  a boss whose death does not publish `Killed` (instance fights) shows 0 until its reward code
  calls `CollectionKillCounts.add`.
* `current_runeday` has never been set by the server. If a client script elsewhere reads it, setting
  it changes that too (expected to be harmless; check on the first client run).
* Existing characters have no log: nothing to migrate, they start empty. Items obtained before the
  feature shipped are not credited. `::clog` can backfill for testing.

## 9. Decisions (settled 2026-10-19)

1. **Per-player log**, with team-wide task completion (any member reaching a threshold completes it).
2. **Kill counts are in** (changed from "left at 0"): 97 saved counters, shown on every category
   that has them (section 2.6).
3. **Full log**, all 1,810 slots shown, max varps not trimmed and enums not overridden.
4. **Overview screen is in** (changed from "not yet"): the summary panel opens it first, with the
   latest-collections ring (section 2.7).

Still a guess to confirm during the build: the clue-section tasks count the tier's "(Rare)" category
too (section 4).

## 10. What was built (2026-10-19)

New module `content/interfaces/collection-log` (package `org.rsmod.content.interfaces.collectionlog`):

| File | What it does |
| --- | --- |
| `CollectionLogConfigs.kt` | References (interfaces, components, varps, runeday varbit) and the `InvEditor` that makes inv 620 `collection_transmit` Perm, stack Always, 2048 slots |
| `CollectionCatalog.kt` | Reads tabs, categories, objs and the enum 3721 aliases from the loaded cache (no data file) |
| `CollectionLog.kt` | `receive` (the only way in), `recount`, `owned`, `slotCounts`, `filled`, `clear`; publishes `CollectionLogSlotFilled` |
| `CollectionLatest.kt` | The overview's 12 latest slots (obj id + runeday varps) |
| `CollectionKillCounts.kt`, `CollectionKillSources.kt` | Saved kill counters, copy into the client varps, name-matched kill feed |
| `CollectionKillCountData.kt` | **Generated** by `tools/clog/gen_kc.py`: 94 counters, 77 categories |
| `CollectionLogScreens.kt` | Open / switch / redraw (client scripts 2388 and 2730) and the events each screen needs |
| `CollectionLogScript.kt`, `CollectionOverviewScript.kt` | Login transmit, `Rolled` and `Killed` feeds, clicks on tabs, rows, menus and section buttons |
| `CollectionLogCommands.kt` | `::clog give / fill / clear / open / kc` (admin) |

Elsewhere: the summary tab opens the overview (`SummarySideScript`); the 26 tasks are in
`LeagueTaskList` and completed by `LeagueCollectionLogTasks`; Fight Caves and Inferno rewards, and
the Scurrius team share, feed the log and the kill counts; `tools/clog/coverage.py` writes
`tools/clog/coverage.txt`.

Differences from the plan above:

* **94 counters, not 97.** Two categories share the normal and "(Rare)" clue counters, and the two
  Gnome Restaurant lines (client varbits) are not tracked.
* **New varp names** (`clog_kc_*`, `varp.sym` ids 65280 to 65187) need a **packCache** before the
  server starts.
* Reachable slots measured by `coverage.py`: **575 of 1,810** (Bosses 217/363, Raids 4/67,
  Clues 129/674, Minigames 22/252, Other 203/454). The clue-tier league tasks stay uncompletable
  until caskets exist.
* The burger-menu and section-button component indices are enabled on the whole child range of
  the menu frame and both section components; they follow the decoded scripts and are **unverified
  in a real client**.

Tests: 15 unit tests in the module (catalog build, generated kill-count table, latest ring) and 10
integration tests in the isle module (`collectionlog/CollectionLogTest`: the catalog read from the
real cache gives 5 tabs, 114 categories and 1,810 slots; receive, counts, drop and kill events,
category and tab clicks, overview to log and back, the inventory being Perm with 2,048 slots, the
five-slot task). They ran green against a throwaway copy of `.data` after a sandbox `packCache`.
The real `.data/cache` has not been repacked: **run `packCache` before starting the server.**

Admin command: `::clog give objId [count] | fill [tab] | clear | open | kc name amount`.

**Search (2026-10-19):** the log's search is entirely client-side and draws every result grey
(count `-1`), expecting the server to run `collection_delayed_transmit` (4100) once per obtained
item, which it cannot do without knowing the search text. `CollectionLogScripts` packs a patched
`[proc,collection_search_display_item]` (made by `tools/clog/gen_search_patch.py`) that reads the
count from inv 620 itself, as the category view does. Needs a packCache; verified byte for byte on
a sandbox pack, not yet seen in a client.

Still to check in a client: the log opens and fills as items arrive, the 2,048-slot inv transmits,
tab and row clicks redraw, kill-count lines show, the overview opens first, the menu and section
buttons switch screens, the latest list and dates look right, and a relog keeps everything.
