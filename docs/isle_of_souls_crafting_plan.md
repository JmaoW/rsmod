# Isle of Souls: Crafting plan

Written 2026-10-06. Scope is option C that the user picked: everything the Old School RuneScape
wiki lists under the Crafting skill, using the real client interfaces. Every level, XP value and
recipe here comes from the official wiki (oldschool.runescape.wiki, the "Crafting" page and the
individual item pages), not from RuneScape 3. (An earlier draft of the scope listed royal
dragonhide: that is RS3 only and is NOT included. OSRS has green, blue, red and black only.)

User decisions (2026-10-06):
- Scope C, minus the two minigames below.
- A tanner NPC with the real tanner interface (324); a test tanner stands in the lobby area.
- Sources for every material come later from the user. A debug command hands out test materials.
- Wiki XP rates, no changes.

## What gets built (module `content/skills/crafting`)

| Area | What | Interface |
| --- | --- | --- |
| Gem cutting | Chisel on every uncut gem: opal, jade, red topaz (can crush), sapphire, emerald, ruby, diamond, dragonstone, onyx, zenyte. Crush chance and XP from the wiki. | none (menu-free) |
| Leather | Gloves, boots, cowl, vambraces, body, chaps (leather); hard leather body; coif; studded body and chaps; spiky vambraces (kebbit claws, leather and every d'hide); hard leather shield | `skillmulti` chatbox |
| Dragonhide | Green, blue, red, black: vambraces, chaps, body, shield | `skillmulti` |
| Other armour | Snakeskin set + shield + Broodoo shields, yak-hide armour, snelms (all 9), crab helmet and claw, Xerician robes, splitbark, mixed hide, Hueycoatl hide | `skillmulti` / item on item |
| Staves | All four elemental battlestaves (orb on staff) | item on item |
| Amethyst | Bolt tips, arrowtips, javelin tips, dart tips (chisel on amethyst) | item on item |
| Spinning | Spinning wheel: ball of wool, bow string, crossbow string, linen/hemp/cotton yarn, magic string, rope | `skillmulti` |
| Weaving | Loom: strip of cloth, bolts of linen/canvas/cotton, sacks, drift nets, baskets | `skillmulti` |
| Pottery | Soft clay (clay + water container), potter's wheel (shape), pottery oven (fire) | `skillmulti` |
| Glass | Molten glass at a furnace (sand + soda ash, 20 XP), glassblowing pipe: beer glass, candle lantern, oil lamp, vial, fishbowl, unpowered orb, lantern lens, empty light orb; oil lantern, light orb | `skillmulti` |
| Jewellery | Gold bar on a furnace opens the real gold interface (446): rings, necklaces, amulets, bracelets for gold, sapphire, emerald, ruby, diamond, dragonstone, onyx, zenyte, plus Slayer rings. Silver bar opens the real silver interface (6): opal/jade/topaz jewellery, holy and unholy symbols, sickle, bolts, tiara. Stringing an amulet (ball of wool) is 4 XP. | real client interfaces |
| Birdhouses | Logs + clockwork with a hammer and chisel (9 kinds) | item on item |
| Tanning | Tanner NPC (interface 324): cowhide to leather (1 coin) / hard leather (3), snake hide to snakeskin (15), the four dragonhides (20 coins). Coins taken from the player. | real client interface |
| Debug | `::craftkit` hands out test materials | n/a |
| League | Crafting tasks + 99/XP milestones + a few counters; see the module docs | n/a |

## Status (2026-10-06): built

Module `content/skills/crafting`, 18 unit and 32 integration tests, 5 league task tests. Needs a
server restart and `packCache` (new queue, three counter varps, changed task enum). Not yet seen in
a real client. `::craftkit <kit>` (admin) gives test materials. The test tanner (Ellis) and the test
stations that stood outside the Soul Wars lobby (`CraftingTestLocs`) were removed on 2026-10-10;
the crafting methods now work on the stations of the map.

League: 20 tasks, 1,650 points (331, 332, 333, 913, 1350, 335, 336, 1351, 1572, 334, 337, 338, 981,
1352, 1353, 339 and the 99 / 25M / 35M / 50M XP milestones 250, 251, 1472, 1495). Left locked:
773 Enter the Crafting Guild, 1291 yak-hide set (needs equip tracking), 1330 "Scrape some blue
dragonhide" (unknown meaning).

## Left out on purpose
- **Crystal singing and Golem crafting**: both are minigames that need their own areas, objects and
  materials (crystal shards, sunstone, plinths). Revisit when the isle has those.
- Gold tiara (the wiki lists it, but the client's gold interface has no tiara entry), the Pre-nature
  amulet, and quest-only silver items (lightning rod, Ivandis, Silvthrill, Demonic sigil).
- Hemp, cotton and linen yarn and their cloth, drift nets and the Jeweller's chisel: the wiki lists
  them but this cache has none of those items.
- Soda ash (burn seaweed on a fire or range, no XP) and cured yak-hide (Thakkrad Sigmundson in
  Neitiznot): not Crafting actions, add with the sources.
- Anything that is not Crafting skill (zenyte "shard" upgrades are Smithing).

## Things to check in a real client
- The gold and silver jewellery interfaces (they open as a main modal, like the anvil).
- The tanner interface: names, prices and pictures were confirmed in the client. The old-style tan
  buttons send a different packet (`If1Button`) that the server ignored; `If1ButtonHandler` in
  `api/net` now turns it into a normal modal button click (op1).
- The potter's wheel, oven, spinning wheel and loom approach and animations.

## Decisions I made that are not in the wiki
- Thread: each spool lasts 5 uses ("a use is an action, not a hide"), counted in memory per player.
- Quantity: the make-X buttons on the real interfaces set varp `makexcrafting`; the server keeps
  that varp in step. An unset value means "all".
- Speeds (ticks per action): gems 2, leather/sewing 3, glass 3, spinning 3, weaving 3, pottery 3,
  jewellery 3, staves 3.
- Messages are my wording.

See the module `content/skills/crafting` and the memory file `project-crafting-skill`.
