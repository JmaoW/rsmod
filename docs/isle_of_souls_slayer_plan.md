# Isle of Souls: Slayer plan and status

Scoped with you 2026-10-11. **Everything in the build order is done (steps 0-6), tested but not seen in a client.** On
2026-10-12 the rewards store was moved from a dialogue onto the cache's real screen (see "Step 4b").
This is the
scope you approved, the build order and the open items. Task, weight, amount and store data come from
the client cache itself (see "S0 result"); points tables and gear requirements come from the OSRS
Wiki (fetched through the wiki API, as with thieving and hunter); nothing from RuneScape 3.

## S0 result (2026-10-11): the data is in the client cache

The plan assumed the wiki tables would have to be transcribed. They do not: **the rev 233 cache
holds the real Slayer data as database tables**, so the server can use exactly what the client's own
Slayer UI expects. Tools in `tools/slayer/` (run in this order):

| Tool | Output | What it does |
| --- | --- | --- |
| `dbdump.py` | `cache_tables.json` | Decodes dbtables 113 `slayer_task` (158 rows), 114 `slayer_master_task` (322, nine masters), 115 `slayer_area` (52), 116 `slayer_task_sublist` (31, boss tasks only), 117 `slayer_unlock` (58 store entries) |
| `npcdump.py` | `cache_npcs.json` | Reads all 14,793 npc configs: name, combat level, category, params. **npc param 50 is the Slayer task id** (it matches `slayer_task:id`), e.g. aberrant spectre 41, banshee 38 |
| `parse_masters.py`, `wiki.py` | `masters.json` | Wiki master pages (Turael, Nieve, Duradel): points per task interval, block cost. Used as a cross-check and for the points tables, which the cache does not hold |
| `monsters.py` | `monsters.json` | osrsreboxed Slayer XP / level per spawned npc (the file is cached in `tools/drops/.cache`, so the earlier "not on this machine" was wrong) |
| `build_data.py` | `slayer_data.json`, `report.txt` | Joins it all with the spawn list: per master the tasks with weights, amounts, requirements, extension, unlock row, npc ids and spawn counts; the 29 superiors; the store |

`enums.py` reads cache enums: 834 unlock names, 836 costs, 840-842 item rewards (slayer ring 75,
broad bolts 35, broad arrows 35, herb sack 750, rune pouch 750, looting bag 10), 273 extend table.

Findings:
- **Cache and wiki agree.** For the three masters only four requirement differences show (the cache is
  rev 233, so Sailing tasks such as aquanites, gryphons and frost dragons are wiki-only and absent).
  The cache wins, since it is what the client displays.
- **Group tasks.** Metal dragons (task 127) is an umbrella over bronze 58, iron 59, steel 60, mithril
  93, adamant 108 and rune 109 (each npc carries its own id). Boss tasks (task 98) use sublist 1 and
  are out of scope.
- **Dropped for no spawn on the isle:** araxytes, brine rats, warped creatures, mutated zygomites,
  cave kraken (spawned only as a whirlpool disguise, see the dungeon plan), custodian stalkers, boss.
- **Superiors:** all 29 superior npc types exist in the cache (ids 7388-7410, 9258, 9287/9288,
  12451, 12459, 14031) and carry the base monster's task id.
- **Leagues columns.** 60 tasks have `leagues_max_comlevel` and 11 have `leagues_min_comlevel`
  (rats 50, aberrant spectres 110, tzhaar min 60): a Leagues-specific combat window. Not applied yet
  (applied in S1, open item 6).
- **Gear requirements are not in the cache** (no `equipment_required` values), so S3 still comes from
  the wiki.

**Open item 1 (masters) now has data behind it.** Counting tasks that have a spawn on the isle:

| Masters | Tasks reachable (of 82 any master could give) | Superiors reachable (of 18) |
| --- | --- | --- |
| Turael, Nieve, Duradel (as scoped) | 60 | 13 |
| **Turael, Vannaka, Duradel** | **81** | **17** |
| Turael, Chaeldar, Duradel | 64 | 14 |

Nieve (combat 85) and Duradel (combat 100, Slayer 50) hand out almost the same tasks, while the
mid-level ones (cockatrice, jellies, pyrefiends, infernal mages, rockslugs, and more) sit with
Vannaka (combat 40). Recommendation: **swap Nieve for Vannaka**. Applied 2026-10-11.

## S1 result (2026-10-11): the task engine

Module `content/skills/slayer`, 22 tests (5 unit, 17 integration), spotless clean, a sandbox boot
loads 227 scripts. Not seen in a real client.

| Piece | Where | What it does |
| --- | --- | --- |
| Data | `resources/.../slayer/*.tsv`, `configs/SlayerData.kt` | `tools/slayer/build_data.py` writes tasks, master tasks, masters (points, block cost), npc XP fallback and superiors; `SlayerTables` loads them. Re-run the tool to change the data |
| State | `configs/SlayerRefs.kt` | The cache's own varps/varbits: `slayer_target`, `slayer_count`, `slayer_count_original`, `slayer_rewards_unlocks(1)`, `slayer_rewards_blocked(_2..)`, varbits `slayer_master`, `slayer_points`, `slayer_tasks_completed` (the streak). Npc param 50 is named `slayer_task_id` (one line in `param.sym`, default 0 to match the cache) |
| Engine | `scripts/SlayerEngine.kt` | Eligibility (combat, Slayer, stat requirements, unlock bit, blocked, Leagues window), weighted roll, amounts (extended once the bit is set), kill counting, XP, completion with the points table, cancel (streak reset). Events `SlayerEvents.Assigned/Kill/Completed` for relics and tasks |
| Kill hook, commands | `scripts/SlayerScript.kt` | Counts `NpcDeathEvents.Killed` for the top damager. `::slayertask <master> [task]`, `::slayerstatus`, `::slayerpoints n`, `::slayercancel` |

Rules built (all from the cache or the wiki):
- A master needs the player's combat level (Vannaka 40, Duradel 100) and, for Duradel, 50 Slayer.
  Turael's tasks still count toward the streak but give 0 points.
- A task needs the cache's combat level and stat requirements, its unlock bit where it has one
  (vampyres, basilisks, red dragons, lizardmen, aviansies, TzHaar), and must not be blocked.
- **The Leagues window is applied**: a task with `leagues_max_comlevel` stops being offered above
  that combat level, and `leagues_min_comlevel` holds it back below. If the window leaves nothing,
  the whole open list is used so a strong player is never left without a task.
- Points follow the largest interval the streak ends on (1/10/50/100/250/1000); Vannaka 8/40/120/
  200/280/400, Duradel 15/75/225/375/525/750.
- Slayer XP per kill: the npc's `slayer_experience`, else the osrsreboxed value, else its
  hitpoints (which is what Slayer XP is for almost every monster). A test checks every monster of
  an offered task gets some.
- **Cave krakens are on** (Duradel gives them): the cove's whirlpools are their spawn, and the kill is
  counted when the revealed kraken dies. Needs Magic 50 and Slayer 87 as in the cache.
- Metal dragons count all six metal dragon task ids.

Guesses to confirm in step 4 (the rewards screen is the only reader): the unlock bits 0-31 / 32+
split across `slayer_rewards_unlocks` and `slayer_rewards_unlocks1`, and six blocked slots using the
first six `slayer_rewards_blocked*` varps. The varbit widths are known from the cache: master 4 bits, points 17 (max 131,071), streak 14 (max
16,383); the engine clamps to them.

Not in S1: the master NPCs and their dialogue (S2), gear requirements and the can't-hurt-it Slayer
level rule (S3), the rewards store (S4), superiors (S5), relics and league tasks (S6).

## S2 result (2026-10-11): the masters

`scripts/SlayerMasters.kt`, `configs/SlayerMasterConfigs.kt`; 31 slayer tests in all, spotless clean,
a sandbox boot loads 228 scripts. Needs `./gradlew packCache` (shop stock and npc edits) and a
restart. Not seen in a client.

- **Turael and Duradel** are placed by `SlayerMasters.place()` at the tiles in `SlayerSpots`;
  **Vannaka** is not placed at all, the map's own spawn in the Varrock sewers is his only one (his
  handlers attach by npc type, so they work there). They use the cache's post-quest-free npc types (`slayer_master_1_tureal`,
  `slayer_master_3`, `slayer_master_5_duradel`), which have the four real options. They stand still.
- **Talk-to** greets in the master's own words, then a menu: an assignment, "How am I doing?"
  (task, streak, points), the shop, goodbye.
- **Assignment** gives a task, or says why not (combat level, Slayer level, nothing suitable), or
  talks about the task the player already has with an option:
  - Turael: "Can you give me an easier task?" takes a task he could not give himself (one he could is
    refused), ends the streak and gives one of his own, as on the Wiki.
  - Vannaka and Duradel: "Cancel my task" for 30 reward points, ending the streak.
- **Trade** opens the Slayer Equipment shop (`slayershop`), which all masters share: the Wiki's 27
  items and stock, sold at 100% and bought back at 60%, 1% price change per item. Reinforced goggles
  are in stock since the isle has no quests. The cache's shop had no stock, so it is set by an
  `InvEditor`.
- **Rewards** says the screen is not ready (step 4).
- **Enchanted gem**: "Activate" and "Check" print the current assignment and remaining kills (the
  gem's Partner and Log options do nothing). Fires `SlayerEvents.Checked` for the league task.
- **Dev commands** added: `::slayerkit` (gem, Slayer helmet and ring, 100,000 coins) and
  `::slayermasters` (teleports to the three).

Left for later: the enchanted gem costs 1 coin, which is what the Wiki's equipment page says too; the shop does not yet highlight an
item for a task that needs one, and the cancel cost and the Turael swap should become free with the
Slayer Master relic (S6).

## S3 result (2026-10-11): requirements and equipment

`scripts/SlayerRequirements.kt`, `SlayerProtection.kt`, `SlayerFinishing.kt`, `SlayerGear.kt`; 49
slayer tests in all, spotless clean, the isle (517), combat-scripts and combat-formulas suites still
pass, a sandbox boot loads 232 scripts. Needs `./gradlew packCache` (helmet params) and a restart.

**Engine changes outside the module** (all small, in files you had already modified):
- `isSlayerTask` in `combat-formulas/InternalHelpers.kt` was a stub that always said no; it now checks
  the player's `slayer_target` against the monster's task id (and the metal dragon umbrella). Nothing
  ever set the `slayer_helm`, `slayer_helm_imbued`, `blackmask` and `blackmask_imbued` params on the
  items either, so **the black mask and Slayer helmet bonus never worked**; `SlayerHeadgearEdits`
  now sets them on every helmet and mask. A test shows +max hit on task only.
- The npc param 50 (`slayer_task_id`) and the varps `slayer_target`/`slayer_count` moved into the
  shared config (`BaseParams`, `ParamBuilds`, `BaseVarps`) so combat can read them.
- `NpcHitEvents.ModifyAny` (api/npc): an unbound event for every hit a npc takes, after its own
  keyed `Modify`. Keyed events allow one handler per npc type, so content that covers many types
  needs this.
- `NpcAttackChecks` (combat-scripts), called from `PvNCombatScript.canAttack`: content registers
  conditions on attacking a npc and tells the player why it said no.

**Rules built**
- **Slayer level to harm a monster**: its own requirement (npc param, else the task's), against the
  current level, so a boost counts. Checked at the start of every attack, melee, ranged or magic.
- **Weapons**: turoth and kurask take damage only from leaf-bladed spear/sword/battleaxe, broad
  arrows and bolts, and Magic Dart cast with a Slayer's staff (plain or enchanted); anything else does
  0. Harpie bug swarms take damage only while a **lit** bug lantern is wielded (a tinderbox on the
  unlit one lights it, with 33 Firemaking).
- **Protection** (a hit from the monster on a player without the gear adds the extra damage and
  drains; the Slayer helmet covers the head items):
  - aberrant spectres: nose peg (+up to 6, combat stats -5)
  - banshees: earmuffs (+up to 4, combat stats, Prayer and Agility -5)
  - dust devils: facemask (+up to 8, combat stats drained to 0)
  - basilisks and cockatrice: mirror shield or V's shield (combat stats -5 a hit)
  - cave horrors: not a rider on their hits but their own attacks (`CaveHorrorScript`): a kick (magic
    melee vs Magic defence, max 9, every 4 ticks) and a scream (always 10% of base Hitpoints, every 5
    ticks). Without the witchwood icon they only scream; with it they pick at random. Both are
    melee hits, so Protect from Melee stops all of it. The scream's area effect and the cave
    abomination's own version are not built
  - killerwatts: insulated boots (+up to 7, not through the matching prayer)
  - wall beasts: spiny helmet (+up to 14)
  - fever spiders: slayer gloves (12% of Hitpoints; the disease is left out)
  - sourhogs: reinforced goggles (+up to 30, Attack and Defence -90%)
  The wiki says "greatly reduced" for most drains; the 5 levels are my figure (`HEAVY`).
- **Finishing**: gargoyles (8 hitpoints or fewer, rock hammer kept or rock thrownhammer used up),
  rockslugs (4, bag of salt), desert lizards (4, ice cooler). A hit never leaves them below 1;
  using the item on a weak one kills it, on a strong one salt and coolers are wasted. The perks
  (Gargoyle Smasher bit 0, Slug Salter 1, Reptile Freezer 2) finish them on the hit by themselves
  with the item in the inventory; the store (step 4) sets those bits.
- **Slayer helmet**: use the enchanted gem on a black mask with a facemask, earmuffs, nose peg and
  spiny helmet in the inventory, after the Malevolent Masquerade perk (bit 5) and with 55 Crafting.
  Imbuing it is not built (no Nightmare Zone or Soul Wars).
- `::slayerkit protect`, `finish` and `hunt` give the gear for each rule.

**Not built, because the monster or place is not on the isle**: mutated zygomites and the fungicide
spray, mogres and molanisks (explosive and bell lures), brine rats, skeletal and fossil wyverns'
shields, the Karuulm floor and its boots (drakes and wyrms spawn there but the place is not
reachable), gryphons' Tortugan shield, the Shayzien armour for lizardmen shamans. They are cheap to
add as rows now that the framework exists. The league tasks that mention gear are step 6.

## S4 to S6 result (2026-10-11, built while you were out)

66 slayer tests and 12 more in the isle module, all passing; the rest of the isle suite and the
combat suites still pass; spotless clean; a sandbox boot loads 235 scripts. **Not seen in a real
client.** Needs `./gradlew packCache` (new varps, item params, shop stock) and a restart.

### Step 4: the rewards store (first version, a dialogue; replaced by step 4b below)
**Superseded by Step 4b:** the dialogue is gone and the master's "Rewards" option opens the real screen.
The text below is kept for what it says about prices and what is left out.
`scripts/SlayerRewards.kt`. **Decision: a dialogue, not the cache's interface 426.** Decoding the
client's rewards screen means disassembling about twenty client scripts and guessing which button
the server must answer, with no client here to try it on, so I built the store as a dialogue every
master opens with "Rewards" (and from the Talk-to menu). It uses the cache's own costs, names and
descriptions (`unlocks.tsv`, made by `tools/slayer/build_data.py`) and the real varps, so the
interface can be wired later without changing the data. The disassembly notes: `slayer_rewards_init`
(client script 405) takes eight int arguments and runs on open; the purchase scripts are 321/322
(`buy_init`, `buy_op`), 411/414 (unlock) and 421-427 (tasks); none is wired.
- **Tasks**: cancel (30 points, ends the streak), block (the current master's price: Turael 40,
  Vannaka 60, Duradel 100; six slots; ends the streak, a guess), unblock (free).
- **Unlocks** and **Longer tasks**: every unlock that does something on the isle, in pages of three,
  with the cache's description and a confirm step. 33 are in the store; 25 are left out as they do
  nothing here (Task Storage, the helmet colourings, mithril/adamant/rune dragons, Wilderness,
  revenants, zygomite spraying, and so on). Longer tasks use the cache's extended amounts.
- **Items**: slayer ring (75), 250 broad bolts (35), 250 broad arrows (35), herb sack (750), rune
  pouch (750), looting bag (10).
- Not built: refunds of refundable unlocks, Task Storage. "Ring Bling" and "Broader Fletching" are
  checked by Crafting and Fletching (`SlayerUnlocks`, 2026-10-16).

### Step 4b (2026-10-12): the real rewards screen

`scripts/SlayerRewards.kt` (state and actions), `scripts/SlayerRewardsScript.kt` (click routing),
refs in `configs/SlayerRefs.kt`. 22 rewards tests, 73 in the module, spotless clean, the isle's Slayer
tests still pass, a sandbox boot loads 236 scripts. **No packCache needed** (it uses only names the
cache already has), a restart is. Not seen in a client. Open it in game with the masters' Rewards
option or `::slayerrewards [turael|vannaka|duradel]`.

**How the screen works** (decoded from interface 426 and clientscripts 405-427, 1089-1092, 1297, 8025-8056;
notes in the scratchpad). The client draws all four tabs from cache tables and does the browsing and
the "Are you sure?" panels itself. The server only has to
- set the state before it opens: varbit `slayer_master_in_focus` (the master whose prices and block
  list it shows; ids 1/3/5 as in the engine), the generic interface varps `if1`-`if6` (task amount,
  task id, boss id, then the same for a stored task; the Tasks tab redraws when they change), and the
  real unlock bits in `slayer_rewards_unlocks`/`unlocks1` (bit n of 0-31 / 32-63: confirmed by
  script 8048, so the earlier guess was right);
- enable the clicks (`ifSetEvents`) and answer them. Every Confirm draws one button into
  `slayer_rewards:confirm_button` whose **child index says what it is**: the unlock's bit (0-57),
  58 cancel, 59 block, 66 "Extend everything", or 3-8 a block slot to unblock. Unlock bits 3-8 clash with
  the unblock slots, so the script also watches the earlier "Unblock" click on a slot
  (`tasks_slot_1..6`) and the "Unlock" click on a row (`unlock_contents`, `extend_contents`);
- the Buy tab: item `n` of enum 840 (the same order as `reward_items.tsv`) is child `n` of
  `buy_items`; ops 2-5 are Buy 1/5/10/50 (sets for a stack). Pouch, sack and bag only have Buy 1.

**What changed in the engine because of the screen** (the cache says so):
- **Block lists are per master** (varbits `slayer_blocked_<master>_1..6`, `slayer_master_in_focus`
  chooses which the screen shows), not one shared list. `blocked/isBlocked/setBlocked` take the master.
- **Cancelling or blocking keeps the streak.** The cache's text on that screen: "Neither option will
  reset your current tally of completed Slayer tasks." (Reverses my earlier guess.) Turael's easier-task
  swap still ends it. New `SlayerEngine.drop`.
- Block prices are the cache's too (script 823: Turael 40, Vannaka 60, Duradel 100 - the same as
  before); cancel is 30; "Extend everything" is the sum of the extensions left at 95%, leaving out the
  Leagues-excluded bits (script 3690), or the plain price when one is left.
- "Disable" on a refundable perk clears its bit with no refund; Bigger and Badder (35), Stop the
  Wyvern (43) and Revenenenenenants (53) are toggles (varbits `slayer_toggleoff_*`) that stay bought.
  A switched-off Bigger and Badder stops superiors.
- The relic: the cache's Slayer Master relic (struct 1129) is the one we use, so the screen already
  shows "Free" by itself. On opening, anything a relic grants is written into the unlock bits so it
  shows as bought. The relic frees only the pouch, sack and bag in the Buy tab (its own text), not the
  ring or ammo as the dialogue did.
- Purchases of unlocks that do nothing on the isle (Like a Boss, helm colours and so on) are allowed, as the original plan said; the points are spent for no effect.

**Not wired:** *Task Storage* (bit 51) is refused with a message, because buying it makes the Tasks tab
show Store/Swap buttons that would do nothing (the relic does not grant it either). *View List* (the
Tasks tab's button, interface 924 `slayer_rewards_task_list`, clientscripts 8059-8064) does nothing;
it needs its open call decoded. Refunds, diary block slots and the hybrid wilderness unlocks are out.

### Step 5: superiors
`scripts/SlayerSuperiors.kt`. A kill of a task monster, with "Bigger and Badder" bought, has a 1 in
200 chance of bringing up its superior on the same tile; it attacks the player, stays up to five
minutes, is a kill for the task, gives its own XP and drops, and says "A superior foe has
appeared...". All 23 base-to-superior pairs are mapped by name (checked against the cache by a test).
- **Drops**: the superiors' own tables were imported from the Wiki into `osrs.toml` (23 pages added
  to `tools/drops/monsters.txt`; superiors roll the lesser monster's table three times), and the
  shared drops (eternal gem, imbued heart, mist and dust battlestaff) use the Wiki's formula for the
  superior's combat level.
- Their Slayer XP comes from the cache data; their combat is whatever the cache gives them.

### Step 6: relics and league tasks
- **Passives** (any tier 3 relic, `relics/LeagueSlayerRelics.kt`): 5x reward points, Bigger and
  Badder free, superiors at 1 in 50. The 2x combat XP was already built.
- **Slayer Master** (tier 5, option 3): always on task for every monster a master could assign and
  the player could harm (Slayer XP, the Slayer helmet bonus through `SlayerTaskOverrides`, and the
  superior roll without a task; the kill does not count for the task), every unlock theirs, cancel,
  block and the store free, and **bonus XP for the 100th kill of each kind of monster** (1,000 at
  level 1 rising to 15,000 at 99 by the monster's Slayer level, before multipliers). Kinds are the
  Slayer tasks (about 60), counted in 21 packed varps (`isle_slayer_unique_0..20`).
- **League tasks** (`tasks/LeagueSlayerTasks.kt`): 16 added, worth 2,020 points: check the task with
  a gem, complete a task, 250 kills on task, a superior, 10/20/50 superiors, 25/75/100 superior
  encounters on task, assemble a helm, an abyssal demon on its task, and Slayer level 99 and
  25/35/50 million XP (in `LeagueSkillTasks`). Dropped: Demon Slayer, Vampyre Slayer, drakes, the
  Grotesque Guardians (three), the enchanted staff (nothing on the isle makes them). The eternal ring task was added back
  2026-10-16 (`LeagueSlayerTasks`, task 126, worn ring via `onWearposChange`). Seven counters and 21 relic varps were added by hand to `varp.sym`; the helm task's area
  was made global in `struct.sym`.

### Decisions I made while you were out
1. Rewards as a dialogue (above; replaced by the real screen in step 4b).
2. Blocking ends the streak (my guess; the cache's own screen says it does not, see step 4b).
3. "Always on task" counts the monster only for XP, helmet and superiors, never for the counter.
4. The Slayer Master's 100th-kill count is per Slayer task kind, not per monster id.
5. Weapons and gear for tasks not on the isle were left out (see S3).

## Client checklist (for when you test in the real client)
1. Run `./gradlew packCache`, restart, `::slayermasters`.
2. Talk to each master: greeting, menu, assignment, "How am I doing?", shop, rewards (all five
   options), and the Assignment, Trade and Rewards right-click options.
3. Open the real rewards screen (Rewards option or `::slayerrewards`): all four tabs draw, the points
   show. Buy an unlock, a longer task and "Extend everything"; Disable a refundable perk; buy 1 and 5
   sets of an item; cancel, block and unblock a task (check the unblock does not buy an unlock of the same
   child number, bits 3-8). Check the game messages, that the Tasks tab redraws after each, and that the
   screen returns to its tab after confirming (the client waits ~1 second then resets).
4. Use the enchanted gem (Activate and Check); `::slayerkit protect` and fight an aberrant spectre
   with and without the nose peg and watch the stats.
5. `::slayerkit finish`: gargoyle, rockslug and lizard finishing with the item.
6. Turoth or kurask with and without a leaf-bladed weapon; harpie bug swarm with the lantern.
7. Superiors: unlock Bigger and Badder, kill a task monster until one appears (or lower the odds
   with Passives), kill it, see the drops.
8. Choose Passives and the Slayer Master relic and check the points, the free store and the 100th
   kill bonus (`::slayerstatus`).
9. Open the league tasks panel and check the 16 new tasks and their text (the list size changed).

## Decisions (yours)

| Question | Decision |
| --- | --- |
| Masters | **Turael, Vannaka, Duradel** (Vannaka replaced Nieve 2026-10-11 on the numbers in the S0 result) |
| Gear requirements | **All of them**: every real requirement, protective gear and finishing items |
| Superiors | **Yes, all of them** the cache has |
| Rewards store | **Everything** the real store sells |
| XP and points | **Real wiki values.** One multiplier setting for the event, tuned later (content plan 1.3) |
| Wilderness | **Out.** No Krystilia, no Wilderness tasks, no Wilderness-only unlocks |
| Boss tasks | Out, with the Wilderness and Konar / Mount Karuulm content (see "Out of scope") |

## What is already in the repo

- Stat `slayer` (`StatBuilds`), skillcape and hood (`ObjEdits`, `BaseObjs`).
- NPC params `slayer_levelrequire`, `slayer_experience` (read by `DefaultNpcCacheEnricher`, so each
  npc already carries its requirement and XP), `slayer_helm`, `slayer_helm_imbued`,
  `bonus_slayer_buff`, `bonus_slayer_meleeonly`.
- Kill hooks (`LeagueKillTasks`, `LeagueMasteryKills`), drop tables for 1,645 monster types and the
  22.7k world spawns (dungeons included).
- Dungeons done and playable: Stronghold Slayer Cave, Slayer Tower Basement, Smoke Devil Dungeon,
  Kraken Cove (cave only), Taverley, Brimhaven, Fremennik, Catacombs and others.
- Autosave (snapshot saves), so task state saves as soon as it is a varp or persisted field.

## What gets built (module `content/skills/slayer`)

### 1. Task engine
- Assign, check, cancel and reassign. A task is a monster group plus an amount, held in varps.
- Real weights, quantity ranges, Slayer and combat level requirements per master, **filtered to
  monsters that have a spawn on the isle**. A task with no spawns is dropped and the weights
  renormalised; the list of dropped tasks goes in this doc.
- Kill counting per task group (for example "banshees" includes twisted banshees), with
  non-damaging kills counted as the wiki says.
- Slayer XP per kill from `slayer_experience`. Points per task from the master's table, with the
  10th and 50th task bonus (and 100th, 250th, 1000th) as on the wiki.
- Task streak and totals kept per player; state saves with the player.
- Enchanted gem: "Check task" / "Teleport" is not wanted (no teleports); check only.

### 2. Masters (three, placed near the done dungeons)
Easy, mid and high. Dialogue for: get task, check, cancel, "rewards", "unlock". The exact master
set and placement is picked from the dungeon census once the tables are in (open item 1).

### 3. Requirements (all of them)
Enforced when the player attacks or gets near a monster of the group:
- **Protective gear**: nose peg or earmuffs (aberrant spectres, banshees), mirror shield or V's
  shield (cockatrice, basilisk), witchwood icon (cave horrors), facemask or any slayer helm
  (dust devils, smoke devils), insulated boots (killerwatts), reinforced goggles, leaf-bladed gear
  (turoth, kurask), and the rest of the wiki's list.
- **Finishing items**: rock hammer (gargoyles, rock slugs fall to a hammer), fungicide spray
  (mutated zygomites), bags of salt (desert lizards, sulphur lizards), ice coolers (lizards, wall
  beasts via spiny helm), and similar. The monster is left at 1 HP until the item is used.
- **Slayer helm**: covers everything the real one does, with an imbued variant for ranged and magic.
- Sources for each item on the isle (the Slayer equipment shop, dungeon drops, crafting) are listed
  as they are wired. Quest gates are treated as met.

### 4. Superiors
Every superior monster in the cache spawns from a task kill at the base rate (1/200), with the
wiki's own drops and bonus XP. Needs a census of superior npcs in the cache first (open item 2).

### 5. Rewards store (everything)
- Real client interface, decoded from the cache like the anvil and gold interfaces (open item 3).
- Task options: extend, unlock, block (list of 6), skip, cancel, and the unlock lists the wiki
  names (Bigger and Badder, Like a Boss, Malevolent Masquerade, and so on).
- Items: rune pouch, herb sack, looting bag, slayer ring and helm parts, the weapon and gear
  unlocks, and the rest the wiki lists. Items whose content the isle lacks are still sold, and each
  is flagged "no effect" in this doc.
- Points are a varp. Costs are the wiki's.

### 6. Relics
- **Passives (tier 3)**: 5x slayer points (no 5-task wait), Bigger and Badder free, superiors at
  1/50. Built through the existing relic hooks, not in the skill.
- **Slayer Master (tier 5)**: always on task for eligible monsters, all perks unlocked free, free
  skip and block, free pouches, and bonus XP the first time you kill the 100th of each unique
  slayer monster, scaled 1,000 to 15,000 by the monster's Slayer level.

### 7. League tasks (about 18 of 24)
Build: Check Your Slayer Task, Complete 1 Slayer Task, Slay 250 Creatures, Assemble a Slayer Helm,
Equip an Enchanted Slayer Staff, Equip an Eternal Slayer Ring, Slay an Abyssal Demon, Defeat a
Superior Slayer Creature, 10, 20 and 50 Superior Creatures, 25, 75 and 100 Superior Encounters,
Reach Level 99 Slayer, 25M, 35M and 50M Slayer XP.

Drop: Demon Slayer and Vampyre Slayer (quests), Defeat a Drake in the Mount Karuulm Slayer Dungeon
(no Karuulm), Grotesque Guardians x3 (belongs to Bosses, no task needed there). The XP tasks stay
until final rates are set (content plan 1.3).

### 8. Tooling and tests
`::slayerkit` (helm, ring, gem, protective gear, finishing items, a stack of points), tests in the
style of the other skills, the importer for task tables kept in `tools/slayer`.

## Build order

1. **S0 Data** (done 2026-10-11): from the cache, see "S0 result". Gear requirements still to come from the wiki in S3.
   Filter against the spawns. Write `tools/slayer`.
2. **S1 Engine**: tasks, kill counting, XP, points, varps, save. Tests.
3. **S2 Masters**: dialogue, placement, `::slayerkit`.
4. **S3 Requirements**: protective gear and finishing items, helm.
5. **S4 Store**: decode the interface, then the store itself.
6. **S5 Superiors**.
7. **S6 Relics and league tasks.** Then docs and a client checklist.

## Open items

1. Which three masters (Vannaka instead of Nieve recommended, see S0 result), and where on the isle.
2. ~~Which superiors exist~~ Answered: all 29 are in the cache. Still open: whether each has a drop
   table and combat data.
3. ~~The rewards interface contract~~ Decoded and wired 2026-10-12 (see step 4b).
4. Sources on the isle for each gear and finishing item.
5. How the 100-kill bonus XP is stored per monster (a varbit per unique monster, or one packed varp).
6. Whether to apply the cache's Leagues combat window (`leagues_min_comlevel`/`leagues_max_comlevel`).
   Applied 2026-10-11 (see S1).

## Out of scope

Wilderness (Krystilia, Wilderness-only tasks and unlocks), boss tasks, Konar and Mount Karuulm,
Slayer teleports, the slayer partner / group feature, the Slayer Tower upper floors (the basement
is already a dungeon), and the Grotesque Guardians (Bosses).

## Client checks needed at the end

Rewards interface, dialogue flow, helm and ring appearance, and the hint arrow if one is used.
Needs `./gradlew packCache` run once.
