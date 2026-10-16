# Isle of Souls: Runecraft plan

Written 2026-10-07, built the same day (see **Status** at the end). Every level, XP value and multiplier here
comes from the official wiki (oldschool.runescape.wiki: "Runecraft", "Abyss", "Mage of Zamorak",
"Essence pouch", "Rune essence mine", "Wrath Altar", "Astral Altar", "Sunfire rune"), not RuneScape 3.

## User decisions (2026-10-07)

- **Altar access**: the Mage of Zamorak teleports you into the Abyss, and from there you can reach
  every altar. No mysterious ruins needed to get to the altars.
- **Runes**: everything (core, combination, astral, wrath, blood, soul, sunfire; see the open
  questions for how the awkward ones work).
- **Essence**: mined at a Rune Essence Mine (rune and pure essence rocks added to Mining).
- **Extras**: essence pouches, tiaras and talismans, league tasks and XP milestones, a debug kit.

## Final decisions (2026-10-07, after the open questions)

1. The Mage drops players **straight into the middle** of the Abyss: no monsters, no obstacles.
2. **All rifts are open**, no quest locks.
3. **Rifts for blood, astral and wrath are added** (the cache has none).
4. Blood is built at the true Blood Altar with pure essence. Soul, sunfire and aether are phase 2.
5. The spells (Lvl-2 Enchant, Magic Imbue) will be added later, so combination runes are wired the
   normal way: talisman route, 50% success, binding necklace for 100%. **No necklace shop.**
6. Talismans come from monster drops; blank tiaras are crafted from silver (the Crafting module
   already makes them: silver bar + tiara mould at a furnace).
7. Pouches come from the Dark Mage by level.
8. The Mage of Zamorak **sells runes** (his real Battle Runes stock), for early magic combat.
9. **No prayer drain and no skull** on entering the Abyss. **Pouches never degrade.**

## What exists already

| Piece | State |
| --- | --- |
| Rune items (air to wrath, all combos, soul, sunfire), `blankrune` (rune essence), `blankrune_high` (pure), `blankrune_daeyalt` | In the cache. `BaseObjs` has refs for the runes; essence has no ref yet. |
| Talismans (air to soul, plus `elemental_talisman`), tiaras (`tiara`, `tiara_air` .. `tiara_soul`, `tiara_astral`) | In the cache |
| Altar locs `air_altar` .. `wrath_altar` (34760-34772), `abyss_exit_to_*` rifts, `rcu_abyssal_*` obstacles, `essencemine_portal_1..5`, `big_essence_rock` | In the cache (names to be checked in the build) |
| Mage of Zamorak npcs (`rcu_zammy_mage1`, `1a`, `1b`, `1_edge`, `2`), `aubury`, abyssal leech/walker npcs | In the cache, no scripts |
| Pouch items `rcu_pouch_small/medium/large/giant/colossal` (and `_degrade` forms) with varps `rcu_pouch_degradation_*` and varbits `small_essence_pouch` .. `giant_essence_pouch` | In the cache |
| Skillcape (`runecrafting_skillcape`), skill guide interface, stat refs | Done |
| Mining | **No essence rock.** The plan table says "runes (essence)" but nothing mines it. |
| Combat spells | Rune consumption already works (`MagicRuneManager`), so crafted runes feed spells straight away |

Found along the way (not touched): `BaseObjs.death_rune = find("chaosrune")`
([BaseObjs.kt:454](../api/config/src/main/kotlin/org/rsmod/api/config/refs/BaseObjs.kt)). The cache
has `deathrune`, so death-rune spells probably check the wrong rune. Verify and fix before the
runecraft death altar is tested with spells.

## What gets built (module `content/skills/runecraft`)

| Area | What |
| --- | --- |
| Essence mine | Rune and pure essence rocks in Mining (never deplete, every attempt succeeds, 5 XP, pure at Mining 30), a mine area, a teleport NPC on the isle (Aubury), and the way back |
| Mage of Zamorak | NPC on the isle with Talk-to, Teleport (into the Abyss), and optionally a rune shop. Prayer is drained and the skull is skipped (no Wilderness) |
| The Abyss | The real Abyss map, rifts to the altars, a Dark Mage in the centre who repairs pouches. See open question 1 for the outer ring |
| Altars | Craft-rune on every altar: all essence in the inventory (and pouches via Empty) becomes runes, with the multiple-rune table, XP per essence, one action |
| Combination runes | Elemental rune + matching talisman + pure essence on the altar. 50% success per rune, binding necklace 100%. XP differs per altar |
| Tiaras and talismans | Tiara + talisman on the altar makes the talisman tiara (XP per tiara). Locate on a talisman. Tiara/talisman on a ruin or rift where it applies |
| Essence pouches | Fill, Empty, Check, capacity per level, degradation and Dark Mage repair, colossal pouch from the other four |
| League | Tasks, 99 and XP milestones, counters (see below) |
| Debug | `::runekit` gives essence, talismans, tiaras, pouches, binding necklace |

## Data (from the wiki)

### Rune crafting

| Level | Rune | XP per essence | Essence |
| --- | --- | --- | --- |
| 1 | Air | 5 | rune or pure |
| 2 | Mind | 5.5 | rune or pure |
| 5 | Water | 6 | rune or pure |
| 9 | Earth | 6.5 | rune or pure |
| 14 | Fire | 7 | rune or pure |
| 20 | Body | 7.5 | rune or pure |
| 27 | Cosmic | 8 | pure |
| 33 | Sunfire | 9 | pure (+ fire rune + sunfire splinter, Shrine of Ralos) |
| 35 | Chaos | 8.5 | pure |
| 40 | Astral | 8.7 | pure |
| 44 | Nature | 9 | pure |
| 54 | Law | 9.5 | pure |
| 65 | Death | 10 | pure |
| 77 | Blood | 10.5 | pure (true altar) |
| 77 | Blood (Kourend) | 23.8 | dark essence fragments |
| 90 | Soul | 29.7 | dark essence fragments (Kourend altar) |
| 90 | Aether | 20 | pure + soul runes + aether catalyst (see open question 4) |
| 95 | Wrath | 8 | pure |

### Combination runes (per rune made, XP is low at the first altar / high at the second)

| Level | Rune | Made from | XP |
| --- | --- | --- | --- |
| 6 | Mist | air + water | 8 / 8.5 |
| 10 | Dust | air + earth | 8.3 / 9 |
| 13 | Mud | water + earth | 9.3 / 9.5 |
| 15 | Smoke | air + fire | 8.5 / 9.5 |
| 19 | Steam | water + fire | 9.3 / 10 |
| 23 | Lava | earth + fire | 10 / 10.5 |

### Multiple runes per essence (level needed for 1x, 2x, 3x ...)

Air 1, 11, 22, 33, 44, 55, 66, 77, 88, 99. Mind 2, 14, 28, 42, 56, 70, 84, 98. Water 5, 19, 38, 57,
76, 95. Earth 9, 26, 52, 78 (104 for 5x). Fire 14, 35, 70. Body 20, 46, 92. Cosmic 27, 59. Sunfire
33, 49, 98. Chaos 35, 74. Astral 40, 82. Nature 44, 91. Law 54, 95. Death 65, 99. The multiplier
uses the level **before** the action; XP only counts essence, not runes. (Blood, soul, wrath: 1x.)

### Pouches

| Pouch | Level | Capacity | Uses before it degrades |
| --- | --- | --- | --- |
| Small | 1 | 3 | never |
| Medium | 25 | 6 | about 45 |
| Large | 50 | 9 | about 29 |
| Giant | 75 | 12 | about 10 |
| Colossal | 25 to 85 | 8 at 25 up to 40 at 85 | 8 uses at 85 (320 essence stored) |

A runecraft cape (or max cape) stops degradation. Degraded pouches shrink and then vanish unless the
Dark Mage repairs them (free, also works on banked pouches).

## Open questions (my recommendations first)

1. **The Abyss outer ring.** The real Abyss has an outer ring of monsters and skill obstacles
   (woodcutting, mining, thieving, agility, firemaking, 25 XP each) and an inner ring with the rifts.
   *Recommend:* the Mage drops you **straight into the inner ring**, no monsters, no obstacles.
   You said "teleport to all the altars", and the obstacles would need five other skills tuned for a
   one-day event. The "Use the Abyss" task still works. The alternative is the full ring.
2. **Rift quest locks.** Real rifts for cosmic, law, death and blood need quests. *Recommend:* all
   rifts open (the league has no quests).
3. **Astral and Wrath altars.** In the real game neither is reachable from the Abyss (Astral is on
   the surface of Lunar Isle, Wrath is behind a ruin in the Myths' Guild). *Recommend:* add rifts
   for both so "everything" works, flagged as a deliberate deviation. The Astral altar is not an
   instance, so its rift teleports to the altar's coordinates on Lunar Isle; I need to confirm the
   map is in the cache.
4. **Blood, soul, sunfire, aether.** Each needs a material chain with no source on the isle:
   - *Blood*: the true Blood Altar uses pure essence (Abyss blood rift), so it is cheap. Include.
   - *Soul*: only Kourend's Soul Altar, using dark essence fragments (dense essence mine, Mining 38
     and Crafting 38, the Dark Altar, then a chisel). *Recommend:* **phase 2**, with a short chain:
     dense runestone in the mine, block, Dark Altar, chisel.
   - *Sunfire*: fire runes + sunfire splinters + pure essence at a Shrine of Ralos (Varlamore).
     *Recommend:* phase 2, only if splinters get a source.
   - *Aether* (level 90, 20 XP): needs aether catalysts. *Recommend:* leave out.
5. **Combination runes without Magic Imbue or enchanting.** The binding necklace needs an enchant
   spell the isle does not have, and Magic Imbue is a Lunar spell. *Recommend:* combination runes use
   the real talisman route at 50% success, and the binding necklace is obtainable from the Mage's shop
   (a league-only source), so the 100% route exists.
6. **Source of talismans and tiara blanks.** The wiki gives drops and trades only. *Recommend:* the
   same approach as crafting: a debug kit now, real sources later when you supply the monster list.
   Tiaras are only needed for tiara tasks and combination runes.
7. **Source of pouches.** Real pouches drop from Abyss monsters, which option 1 removes.
   *Recommend:* the Dark Mage hands out each pouch when you reach its level (small at 1, medium 25,
   large 50, giant 75; colossal by combining them with an abyssal needle that the Dark Mage also gives).
8. **Rune shop.** The real Mage of Zamorak sells basic runes (Battle Runes). *Recommend:* no shop at
   first: runecraft should be the way to get runes for magic. Reconsider if magic stalls early.

## Gotchas to handle in the build

- Rune essence and pure essence are both in the cache but pure essence is the one used by most
  runes; rune essence only crafts air, mind, water, earth, fire and body. An air altar with a pure
  essence works, a cosmic altar with a rune essence does not.
- Multiple-rune levels are decided **before** the action, and the cap is the level table above.
- The Abyss drains prayer to 0. In a league with no PvP that is the only penalty kept (the skull is
  skipped).
- Pouches degrade on fill; the degradation varps already exist. The runecraft cape check needs the
  skillcape's worn state.
- The rift loc names (`abyss_exit_to_*`) are the exits *from* the Abyss in some cache dumps; I need
  to confirm in the build whether they are the ring-side rifts or the altar-side exits.
- The harness limits seen in the other skill modules still apply: weak queues, no `delay`,
  integration tests cannot see `internal` members, `packCache` needed after adding varps.

## League tasks (Raging Echoes list)

Can be supported: Craft an Air Rune, Craft a Fire Rune, Craft a Body Rune, Craft 50 Water, Cosmic,
Astral, Nature, Death and Law Runes, Craft 100 Blood Runes, Craft 200 and 2,500 essence into runes,
Craft 4 runes with 1 essence, Craft any combination rune, Craft a Lava Rune at the Fire Altar, Locate
an altar with a talisman, Use the Abyss, Visit the Rune Essence Mine, plus 99 Runecraft and the 25M,
35M and 50M XP milestones. That is about 22 tasks.

Cannot be supported yet: Brimstail teleport, Xeric's talisman (two tasks), Teleport using Law Runes
(needs teleport spells), the Ourania salamander and altar, the cat trade, the silver stall tiara
(needs thieving), Craft 100 Soul runes and 1,000 Sunfire Runes (phase 2).

Points follow the same tiers as the other skills (check `LeagueSkillTasks`).

## Build order

1. Mining: essence rocks, essence mine area, Aubury teleport. Tests: mining gives essence, every
   attempt succeeds, pure at Mining 30.
2. Crafting runes: the altar action with the multiple-rune table and one test per altar.
3. Abyss: Mage of Zamorak, Dark Mage, rifts. Real client check needed for the map.
4. Combination runes, talismans, tiaras.
5. Pouches and the Dark Mage repair.
6. League tasks, `::runekit`, docs, memory.
7. Phase 2 (soul, sunfire) only if sources are decided.

## Size

Medium, a touch larger than crafting because of the Abyss map and the pouch system. Roughly 12
unit-testable data tables and 25 to 30 integration tests.

## Status (2026-10-07): built

Module `content/skills/runecraft`: 7 unit tests, 27 integration tests, 8 league task tests in the
isle module, and the 72 existing task tests still pass. Spotless clean. A sandbox server boots with
it (174 scripts). **Not seen in a real client.** Needs a server restart **and `packCache`** (the
essence rock becomes an ore, three portal types become rifts, the rune shop gets stock, and ten
league counter varps and thirteen task structs were added to `.data/symbols`).

What is in:
- **Essence mine**: Aubury (`aubury_3op`, option Teleport) takes players to the mine; the large rocks
  are Mining ores that always succeed, never run out, give 5 XP and pure essence from Mining 30. A
  portal beside the arrival point leads back.
- **The Mage of Zamorak** (`rcu_zammy_mage1b`): Talk-to, Trade (Battle Runes) and Teleport. The Dark
  Mage (`rcu_zammy_mage2`) hands out pouches and the abyssal needle, or takes you back.
- **The Abyss**: the real map. The 12 cache rifts lead to the real altar rooms (soul says it is
  silent); three added rifts lead to blood, astral and wrath. Every altar room's portal leads back to
  the Mage; the Astral Altar and the mine get an added exit portal.
- **Altars**: Craft-rune on all 14 (air to wrath bar soul), the wiki's multiple-rune table, XP per
  essence. Rune essence works only at the six lowest.
- **Combination runes**, **tiaras** (talisman + tiara on the altar), **Locate** on every talisman.
- **Pouches**: Fill, Empty, Check on small to colossal, kept in the cache's own varbits; the colossal
  pouch is made with the abyssal needle (Runecraft 25 and Crafting 56).
- **League**: 25 tasks (see below) and `::runekit <pure|rune|tiara|combo|pouches>`.

League tasks wired (ids): 364, 366, 774, 854, 1801 (easy); 367, 368, 369, 462, 463, 573, 648, 775,
855, 982, 983, 1613 (medium); 370, 525, 776, 984, 1603 (hard); 238, 239, 1466, 1489 (99 and XP
milestones). 1603 is now "Craft 100 Blood Runes" (the real task names Kourend's altar). Left locked:
747, 120, 177, 432, 694, 915, 1689, 1602, 1769 (see the list above).

Deviations from the wiki:
- Colossal capacity runs linearly from 8 at level 25 to 40 at level 85 (the wiki only gives the ends).
- Talismans point towards the Mage of Zamorak when you use Locate (there are no ruins on the isle).
- The abyssal needle is used up when the colossal pouch is made.

Places picked at run time (so they follow the real map): the Mage and Aubury stand just outside the
Soul Wars lobby graveyards (the free tiles nearest 2210,2862 and 2214,2862); the Dark Mage in the
middle of the Abyss.

## Left to check in a real client
- The three renamed portal types look like portals, not rifts.
- The Mage's Trade option opens the shop with the stock listed here, and the prices are right.
- Mining the essence rock keeps going until the inventory is full.
- Standing spots: nobody lands inside a wall in an altar room; the Astral Altar exit is reachable.
