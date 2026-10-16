# Isle of Souls: Magic plan

Written 2026-10-07. **Status: scoping, nothing built yet.** Levels, XP and rune costs will come from the
official wiki (oldschool.runescape.wiki, never RuneScape 3) when each phase is built. The spell lists
below are from memory and only show the shape of the work; exact numbers are checked at build time.

## User decisions (2026-10-07)

- **Scope: everything.** Standard, Ancient Magicks, Lunar and Arceuus books, plus spellbook swap.
- **Teleports: island destinations.** Keep each spell's name, level, runes and XP, but send it to a
  point on the isle.
- **Item sources: build the spell, hand out inputs with `::magickit`.** Sources (drops, shops) arrive
  with the NPC drop work.
- **League tasks: yes**, like the other skills, with a plan first (this document).
- **Team spells built for real** (Tele Group, Heal Group/Other, Vengeance Other, Boost/Restore Potion
  Share, Energy Transfer target nearby teammates).
- **Spells that need missing skills**: build the ones that work on existing items (Bake Pie, Plank Make,
  Degrime, Humidify, String Jewellery, Superglass, Magic Imbue...). Farming, house, hunter and
  Reanimation spells are **left out of the book**.
- **Teleports keep their rune costs and are allowed everywhere**, including inside Fight Caves and the
  Inferno (a deliberate change from the real game; players can bail out of boss runs).
- **Build order starts with M0 core + M1 standard book.** Ancients come next.

- **PvP-only spells disabled** (Tele Block, Teleport to Target): cast shows a message.
- **Teleport destinations**: each teleport gets a placeholder isle location for now; the user edits them
  later as isle content grows, so destinations live in one config file.
- **Teleports can be cast out of Fight Caves and the Inferno** (the real game allows it too).
- **Magic mastery IV heal is done in M1.**
- **Autocast rows are added for every new combat spell.**

## What exists already

| Piece | State |
| --- | --- |
| Standard Strike/Bolt/Blast/Wave/Surge (20 spells) | Done in `content/skills/magic/spell-attacks` (`ElementalSpells.kt`) |
| Spell registry, autocast table, rune validation, rune pouch, staff and wear requirements | Done in `api/spells` and `MagicRuneManager` |
| Magic accuracy and max hit formulas (PvN, PvP), spell attack manager | Done |
| Combat mastery magic tiers I, II, VI, 95% rune save (passive) | Done. Tier IV (heal on high roll) waits on a hook |
| Runes, runecraft, Mage of Zamorak rune shop | Done (runecraft plan) |
| Spellbook interface in the gameframe, Home Teleport on all four books | Done (redirects to the Soul Wars lobby) |
| `Spellbook` enum, `spellbook` varbit, spellbook enums in the cache | Done |
| Spell-on-item, spell-on-loc, spell-on-npc handlers (`onOpHeldT`, `onOpLocT`, `onOpNpcT`) | Engine events exist; no content uses them for magic yet |
| `BaseObjs` refs for most standard utility spells (alchemy, superheat, enchant 1-7, teleports, charge, tele other, bones to bananas/peaches) | Refs exist, **no scripts** |

## Things to fix or verify first

1. `BaseObjs.death_rune = find("chaosrune")`. The cache has `deathrune`. Death-rune spells
   (Wind/Fire Surge, Ancient Burst and up) may be checking the wrong rune. Already flagged in the
   runecraft plan.
2. Some teleport refs point at placeholder objs (`spell_kourendcastle_teleport` is
   `cert_deadman_level99_lamp`, `spell_civitas_fortis_teleport` is a placeholder). Those cache rows
   need checking against the real spell objs before use.
3. NPC freeze, poison, stun and stat-drain: the engine has hit and poison hitmarks and freeze-immunity
   params, but I have not confirmed a working "freeze this npc" or "drain this npc's stat" effect.
   **Fight Caves also showed that npcs have no pathfinding**, so a bind or freeze mostly stops
   attacks and movement already. Verify before Ancients.
4. Spellbook swap and Arceuus/Lunar books need their gameframe components checked in the client
   (the same decode step as skillmulti and anvil).

## Phases

Each phase ends with unit and integration tests, a plan-doc status update, and a memory note.

### M0. Magic core (the part everything else uses)

| Piece | What |
| --- | --- |
| Cast framework | One `SpellCast` helper: level check, rune check and removal through `MagicRuneManager`, XP, 95% mastery rune save, animation and spotanim, "you do not have the runes" messages. Today only combat spells use this path |
| Targeting | Spell-on-item (alchemy, superheat, enchant, charge, string jewellery...), spell-on-loc (charge orbs on obelisks, Telekinetic Grab on ground items, fertile soil on patches), spell-on-npc/player (curses, vengeance other, heal other, tele other) |
| Teleport helper | `castTeleport(dest)` with the standard animation, delay, ring/rune cost, and the Soul Wars lobby rules (no teleport mid-fight caves, no teleport out of Caves/Inferno) |
| Spellbook state | Current book, swap rules, which spells are visible. All spells **unlocked** (no quest gates), like prayers |
| Death rune ref fix | Item 1 above |
| `::magickit` | Staves, runes, orbs, jewellery, unstrung items, ensouled heads, bones, planks, etc |

### M1. Finish the standard book

| Group | Spells |
| --- | --- |
| Combat debuffs | Confuse, Weaken, Curse, Vulnerability, Enfeeble, Stun (drain NPC attack/strength/defence), Crumble Undead, Magic Dart, Iban Blast |
| God spells | Saradomin Strike, Claws of Guthix, Flames of Zamorak (need the cape or staff, and the charge spell) |
| Binds | Bind, Snare, Entangle |
| Utility | Low and High Alchemy, Superheat Item, Telekinetic Grab, Bones to Bananas, Bones to Peaches, Charge, Charge Water/Earth/Fire/Air Orb, Enchant Lvl 1-7 (bolts too), Tele Block (no PvP: only messages), Teleport to Target (no target: disabled) |
| Teleports | Varrock, Lumbridge, Falador, Teleport to House, Camelot, Ardougne, Watchtower, Trollheim, Ape Atoll, Kourend Castle, Teleother (three) |

### M2. Ancient Magicks

16 combat spells (Smoke, Shadow, Blood, Ice; Rush, Burst, Blitz, Barrage) with freeze, poison, stat
drain, blood heal and multi-target area hits, plus the 8 teleports (Paddewwa to Ghorrock) and Home
Teleport. Ice Barrage is the Fight Caves and Inferno power spell, so this is where the boss
matches get decided. The multi-target part needs the `SpellAttack` interface to carry an area (the
current one hits a single target).

### M3. Lunar

Teleports and group teleports (Moonclan, Ourania, Waterbirth, Barbarian, Khazard, Fishing Guild,
Catherby, Ice Plateau), Vengeance, Vengeance Other, Heal Other, Heal Group, Energy Transfer, Cure
spells, Stat Spy, Stat Restore Pot Share, Boost Potion Share, Magic Imbue (this also unlocks the
deferred combination-rune route in runecraft), Superglass Make, Plank Make, String Jewellery,
Recharge Dragonstone, Bake Pie, Humidify, Hunter Kit, Fertile Soil, Cure Plant, Geomancy, Monster
Examine, NPC Contact, Dream, Spellbook Swap. Several are farming, hunter or construction
dependent, so those are built as far as the isle's content allows (see open questions).

### M4. Arceuus

Reanimation (four tiers), Ghostly/Skeletal/Undead Grasp (thralls, ties into the Guardian relic),
Demonbane (three), Corruption (two), Dark Lure, Mark of Darkness, Shadow Veil, Vile Vigour, Ward of
Arceuus, Degrime, Demonic and Sinister Offering, Resurrect Crops, Arceuus teleports (Battlefront,
Respawn, Salve Graveyard, Fenkenstrain, West Ardougne, Harmony Island, Cemetery, Barrows, Ape Atoll),
Arceuus Home Teleport. Reanimation needs ensouled heads and Arceuus favour mechanics we do not have;
likely trimmed (see open questions).

### M5. Island teleport destinations

Each teleport spell is mapped to an isle location. The list is in the open questions because it
depends on what the isle map offers. Group teleports and house teleport map to a smaller set.

### M6. League

Tasks (about 40-50 given the size of the skill), XP milestones, and the relic hooks:

| Relic | Hook |
| --- | --- |
| Golden God (tier 4) | Done 2026-10-08: see the relic plan |
| Grimoire (tier 7) | Spellbook swap and the books |
| Reloaded, Equilibrium | Unaffected |
| Specialist and Guardian (tier 8) | Guardian needs Arceuus thralls to be sensible |

## Status (2026-10-07, end of the first build session)

**Built and tested** (`content/skills/magic/utility-spells`, 20 integration tests; the isle's own
teleport test; sandbox server boot loads 185 scripts without a script error):

| Piece | Notes |
| --- | --- |
| `SpellCaster` | The one place that pays for a cast: level, spellbook, runes (`MagicRuneManager.attemptCast`, so rune pouches, staves, combo runes and the 95% mastery save all apply), cast experience, a per-player cooldown. Hooks `onItemSpell` (spell on an inventory item) and `onSpell` (spell button). Publishes `SpellCast` |
| Low and High Alchemy | 40% and 60% of the item value, noted items by their cert link, coins refused, 5 tick cooldown. Publishes `Alchemy.Alchemised` |
| Bones to Bananas and Peaches | Every bone in the wiki's list; long and curved bones, dragon bones and noted bones left alone |
| Superheat Item | Own recipe table (`SuperheatRecipes`), Smithing level and xp as a furnace, iron becomes steel with 2 coal, never fails |
| Lvl-1 to Lvl-7 Enchant | Tables in `Enchantments.kt` (all gems, charged items come out full). **Enchant Crossbow Bolt is not built.** The seven spells are not in the spellbook enum, so `MagicSpellRegistry` registers them itself |
| Telekinetic Grab | Needed a small engine addition: the `OpObjT` message (spell on a ground item) now has a handler (`api/net` `OpObjTHandler`, event `ObjTEvents.Cast`, script hook `onOpObjT`). Range 10, no line of sight check yet |
| Charge | Sets the `buff_charge_spell_disabled` varbit, which is what the damage formulas read for the +10 god spell maximum, for 700 ticks (soft timer `magic_charge`, added to `timer.sym`) |
| Charge Orb spells | Done, but they need an obelisk (`obelisk_*` locs); the isle has none, and I did not test spell-on-loc |
| Teleports | One handler for every teleport spell with a destination. Standard, Ancient and Lunar animations. Works in combat and out of boss areas, as decided |
| Teleother (3) | Targets a teammate through `SpellTeams` (the isle binds `LeagueSpellTeams`: same league team only) |
| Disabled spells | Teleport to Target, Teleport to House, Tele Block: a message, nothing spent |
| `::magickit <kit>`, `::spellbook <book>` | Kits: runes, alch, superheat, bones, jewellery, jewellery2, orbs, grab, staffs |
| Combat spells (`spell-attacks`, `SpecialSpells.kt`, 6 integration tests) | Confuse, Weaken, Curse, Vulnerability, Enfeeble, Stun (npc stat debuffs, restored by the engine's `NpcRegenProcessor`; cannot be cast on an already lowered npc), Bind, Snare, Entangle (npc `delay`, so a held npc cannot attack either; immunity after), Crumble Undead (undead only), Iban Blast, Magic Dart, the three god spells. Tests cover registration of all 15, each debuff's exact drop, the already-lowered refusal, Crumble Undead's refusal and Bind's hold plus immunity. The god spells' damage and Iban/Dart hits are not tested |
| Isle destinations | `IsleTeleportDestinations.kt`: one `Place` per spell, placeholders (altar interiors, graveyards, Fight Caves exit, mining guild, Aubury, Mage of Zamorak, Abyss). Spells with no entry cannot be cast on the isle (the cache's coordinates are the real world). Test: every teleport spell is mapped. Covers Standard, Ancients, Lunar (with group versions) and Arceuus |

**Found along the way**
- The Arceuus and a few other references in `BaseObjs` have odd names (`br_air_staff` for Barrows
  Teleport, `cert_deadman_level99_lamp` for Kourend Castle Teleport): the cache revision renamed
  the objs but the ids still point at the right spells. `docs/isle_of_souls_magic_spells.tsv` lists
  all 192 spell objs of the cache with the real spell name, level and book (column 6).
- The cache has no teleport coordinates for Teleother, Respawn Teleport or the group teleports, so
  those only work through the isle's destinations.
- Mastery tier IV's heal (item 7 in the open questions) was already built with the healing hooks.
- Autocast: the cache's `autocast_spells` enum is the real game's, so the debuffs (Confuse and so on)
  stay un-autocastable, as in the real game. Nothing was added; check in the client.

**Not done in this phase (M1)**
- League tasks and milestones (M6)
- Enchant Crossbow Bolt, Tome of water, swampbark, Iban's staff and Slayer staff (e) charges, line of
  sight for Telekinetic Grab, Mage Arena casts for the god spells
- Everything from M2 on (Ancient combat spells, Lunar, Arceuus)
- Group teleports (Lunar) are mapped to destinations and, until M3, move only the caster

## Status: M2 Ancient Magicks (2026-10-08)

**Built and tested** (`content/skills/magic/spell-attacks`, `ancients/`; 9 integration tests, 15 in the
module with the standard ones):

| Piece | Notes |
| --- | --- |
| The 16 combat spells | `AncientSpells.kt`: Smoke, Shadow, Blood and Ice, each Rush, Burst, Blitz and Barrage, with the wiki's maximum hits (13-30), animations, graphics and sounds. Levels, runes and cast xp come from the cache and `SpellObjEditor` |
| Smoke | 1 in 8 chance to poison on a hit, from 2 damage (Rush, Burst) or 4 (Blitz, Barrage). Immune npcs (`poison_immunity`) are skipped |
| Poison | New `NpcPoison`: a hit every 30 ticks from the poisoner (no experience), starting damage drops by 1 every five hits until it runs out; a weaker poison does not replace a stronger one. The engine had no poison before |
| Shadow | Attack down 10% (Rush, Burst) or 15% (Blitz, Barrage) of the base level on a hit; never lowered further than that |
| Blood | Heals the caster for 25% of the damage dealt (not more than the target had left) |
| Ice | Freezes 8, 16, 24 or 32 ticks on a hit; not on a splash, not on an npc already held or freeze-resistant |
| Burst and Barrage | Also hit the attackable npcs (those with an Attack option) in the 3x3 around the target, nine at most, each with its own accuracy roll and experience, **only in a multi-combat area**. Secondary targets come from `targetsAround`, tested on its own because the test world has no multi-combat area |
| Ancient teleports and Home Teleport | Already done in M1 (destinations are in `IsleTeleportDestinations.kt`) |

**Bug found and fixed on the way (also in M1's Bind, Snare and Entangle):** an npc's death is a queue,
and queues wait while the npc is delayed, so a frozen npc died only after its freeze ran out. The new
`NpcFreezes` lets go of the hold the moment the npc has no hitpoints left (tested).

**Not done:** Ancient sceptres (more freeze, more accuracy), freezing and poisoning other players
(no player fighting on the isle), a projectile for Shadow and Blood Burst/Barrage (the cache has
only the impact graphic, so they land two ticks after the cast). Nothing was seen in a real client.

## Status: M3 Lunar (2026-10-08)

**Built and tested** (`content/skills/magic/utility-spells`; `LunarSpellsTest` 11 and
`LunarTeamSpellsTest` 17 integration tests). Recipes are in `LunarRecipes.kt`; wiki numbers
throughout.

| Spell | What it does here |
| --- | --- |
| Bake Pie | Bakes every uncooked pie (the nine this cache has), one set of runes and 60 Magic xp each, normal Cooking xp, no burning, Cooking level needed |
| Plank Make | One log (noted too) into a plank for 70/175/350/1,050 coins (logs, oak, teak, mahogany) |
| String Jewellery | Every unstrung amulet, symbol and emblem, one set of runes each, 4 Crafting xp each |
| Superglass Make | Sand with seaweed, soda ash or giant seaweed (six units) into molten glass, with the wiki's extra-glass chances, Crafting xp for sand and giant seaweed. Spare glass drops to the floor |
| Tan Leather | Up to five hides (cow, green, blue, red, black dragon) |
| Spin Flax | Up to five flax into bowstrings, 15 Crafting xp each |
| Humidify | Every empty vial, bucket, jug, bowl, fishbowl and clay |
| Recharge Dragonstone | An amulet of glory, combat bracelet or skills necklace to four charges |
| Group teleports | The caster and the teammates within a tile go to the spell's isle destination (the single teleports were already done) |
| Heal Other, Heal Group | Give teammates 75% of the caster's hitpoints (the caster keeps 11%); Heal Group shares it plus 5 between up to five injured teammates within a tile |
| Energy Transfer | 10 hitpoints and all the caster's special attack for a teammate's full run energy and special attack |
| Stat Spy, Monster Examine | Levels in chat in place of the real game's interfaces |
| Stat Restore / Boost Potion Share | The effect of a dose of a restoring (boosting) potion for the caster free and up to four teammates within two tiles, a dose each (`Drinking.share`, `Potions.find`) |
| Vengeance, Vengeance Other | The next hit taken rebounds 75% on the npc that dealt it (at least 1), "Taste vengeance!", 50 tick cooldown for the caster. Needed a new engine event: `PlayerHitEvents.Impact` from the player hit processor |
| Magic Imbue | 21 ticks in which combination runes at an altar need no talisman (`Runecrafting.ImbueCheck`); the 50% rule and the binding necklace still apply |
| Spellbook Swap | Pick the standard, Ancient or Arceuus book for one cast, two minutes at most, then the Lunar book returns (needs a chat menu click, so the menu itself is untested) |

**Other changes along the way:** the cache calls many Lunar utility spells "combat" spells, so the
PvN/PvP combat scripts registered attack handlers on them and clashed with the new ones;
`MagicSpellRegistry.combatSpells()` now leaves out the Lunar book. All casts, combat or not, publish
`org.rsmod.api.spells.events.SpellCast` now (it used to be a utility-spells class).

**Left out on purpose** (answered earlier): Geomancy, Cure Plant, Fertile Soil, Hunter Kit, NPC
Contact, Dream (no farming, hunter or NPC Contact content), and Cure Me, Cure Other and Cure Group
(nothing poisons a player). They say so when cast. Not done: the hard leather option of Tan Leather,
snake hides, the Lunar quests and diaries (not enforced), cooldowns of Heal Group and the pot shares.

## Status: M4 Arceuus (2026-10-08)

Built, with tests (`ArceuusSpellsTest` in `spell-attacks` and in `utility-spells`):

- **Teleports** (Arceuus library, Draynor Manor, Mind Altar, Respawn, Salve Graveyard, Fenkenstrain's Castle, West Ardougne, Harmony Island, Cemetery, Barrows, Ape Atoll): go through the normal teleport script, placeholder destinations in `IsleTeleportDestinations.kt`.
- **Grasps** (Ghostly, Skeletal, Undead; 12/17/24 max hit): a hit can bind the npc for 2/3/4 ticks, 10/25/50% chance, doubled while Mark of Darkness is active (`NpcFreezes.hold`, a stun without freeze immunity).
- **Demonbanes** (Inferior, Superior, Dark; 16/23/30): refuse non-demons before taking runes; the damage bonus against demons and Mark of Darkness already lived in the damage formulas.
- **Thralls** (Resurrect Lesser/Superior/Greater Ghost/Skeleton/Zombie): follows the player, hits what the player is fighting every 4 ticks, max 1/2/3 (magic/ranged/melee), lasts one tick per Magic level, one at a time, costs 2/4/6 Prayer points, cooldown 16. The hits are the player's.
- **Mark of Darkness** (3 ticks per Magic level), **Death Charge** (next kill within 100 ticks gives 15% special attack), **Vile Vigour** (Prayer points to run energy), **Degrime** (half Herblore XP), **Demonic Offering** and **Sinister Offering** (up to 3 ashes or bones, triple XP, restores Prayer).

Left out (cast says the spell is unavailable): Basic, Adept, Expert and Master Reanimation, Resurrect Crops, Shadow Veil, Dark Lure, Lesser and Greater Corruption, Ward of Arceuus. Ward would need a source-aware player hit modifier to reduce damage.

Not done: the Book of the Dead requirement for thralls, a melee thrall walking up to its target, the combat-achievement thrall duration bonus.

## Status: M6 League tasks (2026-10-08)

49 Magic tasks, all with tests (`LeagueMagicTasksTest`, `IsleOfSoulsMagicTasksTest`). The server list is `LeagueTaskList`, the completing code `LeagueMagicTasks` (and `LeagueSkillTasks` for the level and XP milestones, `LeagueGearTasks` for staffs).

- **Vanilla tasks (23):** Equip an Elemental Staff, Equip an Elemental Battlestaff or Mystic Staff, Switch to the Lunar Spellbook, Teleport Using Law Runes, Cast a Blast / Wave / Surge Spell, Cast High Level Alchemy, Ice Rush / Burst / Blitz / Barrage, Saradomin Strike, Claws of Guthix, Flames of Zamorak, Degrime Full Clean (26 herbs), Teleport to Civitas illa Fortis, Kourend Castle Teleport, Moonclan Teleport, Spellbook Swap, Reach Level 99 Magic, 50 Million Magic XP. (Home Teleport was already in.) The ones that named a place (Kharidian Desert, Wilderness, Fremennik, Kourend, Varlamore) were made area-free through `IsleOfSoulsTaskEdits`; their struct names are in `struct.sym`.
- **New tasks (26, ids 1846-1871, structs 6246-6271, defined in `IsleOfSoulsMagicTasks`):** Easy: Wind Strike, Low Level Alchemy, Bones to Bananas, Varrock Teleport, Lvl-1 Enchant. Medium: Telekinetic Grab, Superheat Item, Smoke Rush, Summon a Thrall, Mark of Darkness, Kill 25 Monsters With Magic. Hard: High Level Alchemy x100, Charge, Magic Imbue, Tele Group, Death Charge, Vile Vigour, Demonic Offering. Elite: Blood Barrage, Vengeance, Plank Make, Sinister Offering, Kill 250 Monsters With Magic, Cast 1,000 Spells, 25 and 35 Million Magic XP.
- **Left out:** Inferior Demonbane kill (no demons on the isle), the teleport tablets, wizard robes / Mystic set / God capes / Ancient sceptre (nothing hands them out), the farming, hunter and house tasks.
- **Kills with magic** count when the player cast a combat spell in the 10 ticks before the kill (the killing blow is not tracked per spell). Thrall hits and non-Lunar combat-typed spells count as combat; Lunar and utility spells do not.
- "Switch to the Lunar Spellbook" completes on a Lunar-book cast or on login with the book; `::spellbook` alone does not complete it until the next cast.
- **Needs `./gradlew packCache`** (new task structs, the enum, edited texts) and a restart (new `isle_count_magic_*` varps in `varp.sym`).

## Open questions

1. **Group teleports and multi-player spells** (Tele Group, Heal Group, Boost Potion Share, Stat
   Restore Pot Share, Cure Group, Energy Transfer, Vengeance Other, Heal Other). Teams are 5 people
   on the isle. Build them for real (target nearby team members), or trim them?
2. **Farming, Construction, Hunter, Thieving and quest-gated Lunar/Arceuus spells** (Fertile Soil,
   Cure Plant, Resurrect Crops, Teleport to House, Hunter Kit, Dream, NPC Contact, Plank Make, Bake
   Pie, Humidify, Degrime, Reanimation, Spellbook Swap). Build all of them with a stub effect, or
   skip the ones whose skills are not on the isle?
3. **PvP-only spells** (Tele Block, Teleport to Target, Vengeance Other targeting, Mark of Darkness
   effects on players). Disable, or keep as no-ops with a message?
4. **Isle teleport destinations.** I need a list of the places a teleport can send you: Soul Wars
   lobby, mine, runecraft altars area, fishing, bank, Fight Caves, Inferno... Proposal: I read the
   isle's area files and propose a mapping for you to edit.
5. **Cost of teleports**: keep rune costs (so runecraft matters) or make them free? Proposal: keep.
6. **Can you teleport out of Fight Caves and the Inferno?** Proposal: no, same as the real game
   (Caves/Inferno block teleports).
7. **Magic mastery IV (heal on a high roll)** is still unhooked. Do it as part of M1 or leave?
8. **Autocast**: the combat tab already handles autocast for the 20 elemental spells. New combat
   spells need to be added to the autocast enum rows (cache) for the Ancient and standard debuffs.
   Proposal: add them all.

## Rough size

| Phase | Size |
| --- | --- |
| M0 core | M |
| M1 standard book | M-L |
| M2 Ancients | L (multi-target, freeze, drain) |
| M3 Lunar | L (about 40 spells, many odd) |
| M4 Arceuus | L |
| M5 destinations | S once the list exists |
| M6 league | M |

Suggested order: M0, M1, M5, M2, M6 (first half), M3, M4. That keeps the isle playable as soon as
possible: after M0 + M1 + M5 magic has combat, alchemy, superheat, teleports and enchanting. Ancients
come next because of the boss fights.
