// Isle of Souls content tracker data.
//
// Edit this file to update the tracker. Each item has:
//   name      what it is
//   priority  "P0" (the event can't run without it), "P1" (much better with it), "P2" (only if time)
//   built     0-100, see the scale on the tracker page (0 / 25 / 50 / 75 / 90 / 100)
//   tested    "no", "issues" or "passed"  (set by Jamie only)
//   issue     what went wrong when tested (only when tested is "issues")
//   about     one or two lines for players: what it is and what's in it
//   left      what's still to build (empty when nothing)
//   doc       build notes in the repo's docs/ folder (optional)
//
// This is a .js file rather than .json so the page also works when opened straight from disk.

window.ISLE_CONTENT = {
  updated: "2026-11-03",
  sections: [
    {
      id: "skills",
      name: "Skills",
      blurb: "Every skill can be trained on the isle and is useful for something else.",
      items: [
        { name: "Attack", priority: "P0", built: 100, tested: "no", about: "Trained on the monsters spawned across the isle and its dungeons.", left: "" },
        { name: "Strength", priority: "P0", built: 100, tested: "no", about: "Trained on the monsters spawned across the isle and its dungeons.", left: "" },
        { name: "Defence", priority: "P0", built: 100, tested: "no", about: "Trained on the monsters spawned across the isle and its dungeons.", left: "" },
        { name: "Hitpoints", priority: "P0", built: 100, tested: "no", about: "Trained alongside every combat style.", left: "" },
        { name: "Ranged", priority: "P0", built: 100, tested: "no", about: "Bows, crossbows, thrown weapons and chinchompas. Ammo comes from Fletching and Hunter.", left: "" },
        { name: "Magic", priority: "P0", built: 90, tested: "no", about: "All four spellbooks: standard, Ancient, Lunar and Arceuus, with combat spells, alchemy, teleports to places on the isle and team spells that target your teammate.", left: "Farming, house and hunter spells are left out on purpose. Tele Block and Teleport to Target are switched off.", doc: "isle_of_souls_magic_plan.md" },
        { name: "Prayer", priority: "P0", built: 100, tested: "no", about: "Bury bones, normal and chaos altars, every prayer unlocked from the start.", left: "", doc: "isle_of_souls_prayer_plan.md" },
        { name: "Mining", priority: "P0", built: 100, tested: "no", about: "Ores, gems and essence, plus the Mining Guild.", left: "" },
        { name: "Smithing", priority: "P0", built: 100, tested: "no", about: "Smelting at furnaces and smithing at anvils on the real interfaces.", left: "" },
        { name: "Woodcutting", priority: "P0", built: 100, tested: "no", about: "Trees across the isle; logs feed Firemaking and Fletching.", left: "" },
        { name: "Firemaking", priority: "P0", built: 100, tested: "no", about: "Light fires anywhere; cook on them.", left: "" },
        { name: "Fishing", priority: "P0", built: 90, tested: "no", about: "Fishing spots around the isle supplying Cooking.", left: "Dark crabs and anglerfish are not in yet." },
        { name: "Cooking", priority: "P0", built: 100, tested: "no", about: "Every cooked food on the wiki list, pies, pizzas, cakes, stews, potato toppings and wine.", left: "", doc: "isle_of_souls_food_plan.md" },
        { name: "Fletching", priority: "P0", built: 90, tested: "no", about: "Every wiki recipe: shafts, bows, crossbows, arrows, bolts, darts, javelins and tips.", left: "The ogre bow is left out. No place to get bowstrings or feathers yet." },
        { name: "Crafting", priority: "P0", built: 90, tested: "no", about: "Gems, leather, dragonhide, jewellery, battlestaves, pottery, glass and more, on the real interfaces.", left: "Golem crafting is left out. Some materials have no source on the isle yet.", doc: "isle_of_souls_crafting_plan.md" },
        { name: "Herblore", priority: "P0", built: 90, tested: "no", about: "Cleaning herbs and making 33 potions, from attack potions to super combats, brews and staminas.", left: "No place to get some secondary ingredients yet.", doc: "isle_of_souls_herblore_plan.md" },
        { name: "Runecraft", priority: "P0", built: 100, tested: "no", about: "Essence mine, 14 altars, combination runes, tiaras, pouches and the Abyss through the Mage of Zamorak.", left: "", doc: "isle_of_souls_runecraft_plan.md" },
        { name: "Thieving", priority: "P0", built: 100, tested: "no", about: "23 pickpocket targets, 19 stalls, coin pouches, rogue gear and the dodgy necklace.", left: "", doc: "isle_of_souls_thieving_plan.md" },
        { name: "Agility", priority: "P0", built: 90, tested: "no", about: "The Brimhaven Agility Arena, marks of grace and Grace's graceful shop.", left: "The arena hut on the beach has no floor or roof. Only one place to train.", doc: "isle_of_souls_agility_plan.md" },
        { name: "Hunter", priority: "P0", built: 100, tested: "no", about: "Snares, box traps, deadfalls, net traps, pitfalls, butterflies, implings, falconry and birdhouses.", left: "", doc: "isle_of_souls_hunter_plan.md" },
        { name: "Slayer", priority: "P0", built: 75, tested: "no", about: "Turael, Vannaka and Duradel, real task lists, the rewards shop, superiors and slayer gear rules.", left: "35 of the 82 task types have no monster you can reach on the isle. We still need to choose: add spawns, or remove those tasks.", doc: "isle_of_souls_slayer_coverage.md" },
        { name: "Farming", priority: "P0", built: 0, tested: "no", about: "Planned: herb and food patches with faster growth.", left: "Needs a design talk first, then the whole skill." },
        { name: "Construction", priority: "P0", built: 0, tested: "no", about: "Planned: build sites on the isle instead of houses (restore pool, range, bank chest, teleport pad).", left: "Needs a design talk first, then the whole skill." }
      ]
    },
    {
      id: "bosses",
      name: "Bosses and minigames",
      blurb: "Bosses are fought as a team. Most are in a private instance for your team.",
      items: [
        { name: "Fight Caves", priority: "P1", built: 100, tested: "no", about: "Full 63 waves or a short 8 waves plus Jad, set by the event settings. Fire cape and a pet roll.", left: "", doc: "isle_of_souls_fight_caves_plan.md" },
        { name: "Inferno", priority: "P1", built: 90, tested: "no", about: "Full 69 waves or a short 9-wave version with both Jads and TzKal-Zuk. Pillars, glyph and enrage.", left: "Some numbers are guesses and may need tuning.", doc: "isle_of_souls_inferno_plan.md" },
        { name: "Zulrah", priority: "P1", built: 90, tested: "no", about: "All four rotations, venom, clouds and snakelings.", left: "Some numbers are guesses and may need tuning.", doc: "isle_of_souls_zulrah_plan.md" },
        { name: "Scurrius", priority: "P1", built: 90, tested: "no", about: "The rat boss in its lair, with phases and rat minions.", left: "Some numbers are guesses and may need tuning.", doc: "isle_of_souls_scurrius_plan.md" },
        { name: "Kalphite Queen", priority: "P1", built: 100, tested: "no", about: "Two forms with the molt between, workers, soldiers and guardians.", left: "", doc: "isle_of_souls_kalphite_queen_plan.md" },
        { name: "Obor and Bryophyta", priority: "P1", built: 100, tested: "no", about: "Team lairs opened with giant and mossy keys, chests, growthlings and Bryophyta's staff.", left: "", doc: "isle_of_souls_giants_plan.md" },
        { name: "Wilderness bosses", priority: "P1", built: 100, tested: "no", about: "Callisto, Artio, Venenatis, Spindel, Vet'ion and Calvar'ion in their lairs, reached through the Escape Caves. Loot goes by damage dealt.", left: "", doc: "isle_of_souls_wilderness_bosses_plan.md" },
        { name: "King Black Dragon", priority: "P1", built: 100, tested: "no", about: "Team lair through a lever in the Revenant Caves, with all four breath attacks.", left: "", doc: "isle_of_souls_king_black_dragon.md" },
        { name: "Dagannoth Kings", priority: "P1", built: 100, tested: "no", about: "All three kings and the spinolyps in a team lair, reached by the ladder on the isle.", left: "", doc: "isle_of_souls_dagannoth_kings.md" },
        { name: "Vorkath", priority: "P1", built: 100, tested: "no", about: "The full fight in a team crater, reached by boat from the isle.", left: "", doc: "isle_of_souls_vorkath.md" },
        { name: "God Wars Dungeon", priority: "P1", built: 100, tested: "no", about: "Kree'arra, General Graardor, Commander Zilyana and K'ril Tsutsaroth with their bodyguards, altars and the ecumenical key. 40 kills to enter a room the first time.", left: "Nex is not included.", doc: "isle_of_souls_godwars_plan.md" },
        { name: "Tombs of Amascut", priority: "P1", built: 90, tested: "no", about: "The full raid for your team: all four paths, the Wardens, invocations and loot points.", left: "Still being polished after playtests.", doc: "isle_of_souls_toa_plan.md" },
        { name: "The Gauntlet", priority: "P1", built: 75, tested: "no", about: "Normal and Corrupted, as a duo: gathering, crystal tools, cooking, potions and the Hunllef.", left: "A list of open questions to work through (looks, scoreboard text, overlays).", doc: "isle_of_souls_gauntlet_issues.md" },
        { name: "Fortis Colosseum", priority: "P1", built: 90, tested: "no", about: "As a duo, with all 14 modifiers and Sol Heredit at the end.", left: "Some numbers are guesses and may need tuning.", doc: "isle_of_souls_colosseum_plan.md" },
        { name: "Cerberus", priority: "P1", built: 0, tested: "no", about: "Planned. Needed first for the Cerberus echo boss.", left: "The whole boss." },
        { name: "Grotesque Guardians", priority: "P1", built: 0, tested: "no", about: "Planned. Needed first for the Grotesque Guardians echo boss.", left: "The whole boss." },
        { name: "Thermonuclear smoke devil", priority: "P1", built: 0, tested: "no", about: "Planned. Needed first for its echo boss.", left: "The whole boss." },
        { name: "Hespori", priority: "P2", built: 0, tested: "no", about: "Planned. Needs Farming first.", left: "The whole boss, after Farming." }
      ]
    },
    {
      id: "echoes",
      name: "Echo bosses",
      blurb: "Harder versions of normal bosses, reached with the echo orb the normal boss drops.",
      items: [
        { name: "Kalphite Queen (echo)", priority: "P1", built: 0, tested: "no", about: "Drops the drygore blowpipe.", left: "Needs a design talk first, then the fight." },
        { name: "King Black Dragon (echo)", priority: "P1", built: 0, tested: "no", about: "Drops the thunder khopesh and thousand-bone door.", left: "Needs a design talk first, then the fight." },
        { name: "Dagannoth Kings (echo)", priority: "P1", built: 0, tested: "no", about: "Drops the amulet and ring of kings.", left: "Needs a design talk first, then the fight." },
        { name: "Corrupted Hunllef (echo)", priority: "P1", built: 0, tested: "no", about: "Drops the crystal blessing. Skips the gathering part of the Gauntlet.", left: "Needs a design talk first, then the fight." },
        { name: "Sol Heredit (echo)", priority: "P1", built: 0, tested: "no", about: "Drops the weapon of Sol and sunlight cuffs. Skips straight to the last wave.", left: "Needs a design talk first, then the fight." },
        { name: "Cerberus (echo)", priority: "P2", built: 0, tested: "no", about: "Drops the echo godsword. Needs Cerberus first.", left: "Normal Cerberus first, then the echo." },
        { name: "Grotesque Guardians (echo)", priority: "P2", built: 0, tested: "no", about: "Drops the damned gloves. Needs the Grotesque Guardians first.", left: "Normal boss first, then the echo." },
        { name: "Thermonuclear smoke devil (echo)", priority: "P2", built: 0, tested: "no", about: "Drops the devil's element. Needs the normal boss first.", left: "Normal boss first, then the echo." },
        { name: "Hespori (echo)", priority: "P2", built: 0, tested: "no", about: "Drops the tangled lizard. Needs Farming and Hespori first.", left: "Farming and Hespori first, then the echo." }
      ]
    },
    {
      id: "relics",
      name: "Relics",
      blurb: "Pick one relic per tier as your team's points grow. All 8 tiers can be unlocked.",
      items: [
        { name: "Tier passives", priority: "P0", built: 100, tested: "no", about: "XP multipliers, boosted drop rates, no run drain at tier 1, double combat XP and more as tiers unlock.", left: "" },
        { name: "Power Miner (tier 1)", priority: "P0", built: 100, tested: "no", about: "Extra mining success, ore sent to the bank, rocks last longer. Comes with the echo pickaxe.", left: "" },
        { name: "Lumberjack (tier 1)", priority: "P0", built: 100, tested: "no", about: "Extra woodcutting success and logs sent to the bank. Comes with the echo axe.", left: "" },
        { name: "Animal Wrangler (tier 1)", priority: "P0", built: 100, tested: "no", about: "Traps never fail, faster box traps with double chinchompas, jars never break.", left: "" },
        { name: "Corner Cutter (tier 2)", priority: "P0", built: 100, tested: "no", about: "Agility always succeeds, double laps, bonus XP, and Sage's greaves give XP while running.", left: "" },
        { name: "Friendly Forager (tier 2)", priority: "P0", built: 100, tested: "no", about: "Find herbs while mining, woodcutting, fishing and hunting.", left: "" },
        { name: "Dodgy Deals (tier 2)", priority: "P0", built: 100, tested: "no", about: "Pickpocket everyone nearby at once, never fail, noted loot, triple coin pouches.", left: "" },
        { name: "Clue Compass (tier 3)", priority: "P0", built: 0, tested: "no", about: "Planned: teleport to boss entrances and other named places on the isle.", left: "The teleport list and the relic." },
        { name: "Bank Heist (tier 3)", priority: "P0", built: 0, tested: "no", about: "Planned: teleport to the banks on the isle.", left: "The teleport list and the relic." },
        { name: "Fairy's Flight (tier 3)", priority: "P0", built: 0, tested: "no", about: "Planned: teleport to named places on the isle.", left: "The teleport list and the relic." },
        { name: "Golden God (tier 4)", priority: "P0", built: 100, tested: "no", about: "Free, stronger alchemy, kept items, offering coins for prayer XP and noted shop buys.", left: "" },
        { name: "Reloaded (tier 4)", priority: "P0", built: 100, tested: "no", about: "Pick one extra relic from tiers 1 to 3.", left: "" },
        { name: "Equilibrium (tier 4)", priority: "P0", built: 100, tested: "no", about: "Bonus XP the more even your skills are.", left: "" },
        { name: "Production Master (tier 5)", priority: "P0", built: 100, tested: "no", about: "Every production job finishes in one go.", left: "" },
        { name: "Treasure Arbiter (tier 5)", priority: "P0", built: 0, tested: "no", about: "Planned: clues drop more often and caskets always roll the most rewards.", left: "Needs clue scrolls and caskets first." },
        { name: "Slayer Master (tier 5)", priority: "P0", built: 100, tested: "no", about: "Always counts as on task, free slayer perks and shop, bonus XP on every 100th kill.", left: "" },
        { name: "Total Recall (tier 6)", priority: "P0", built: 100, tested: "no", about: "A crystal that saves a spot and takes you back to it. Doesn't work inside instances.", left: "" },
        { name: "Banker's Note (tier 6)", priority: "P0", built: 90, tested: "no", about: "Note and un-note items anywhere.", left: "How the toggle option works is a guess." },
        { name: "Pocket Workshop (tier 7)", priority: "P0", built: 0, tested: "no", about: "Planned: a portable furnace, anvil, range and loom. Replaces Pocket Kingdom.", left: "The whole relic." },
        { name: "Grimoire (tier 7)", priority: "P0", built: 100, tested: "no", about: "The arcane grimoire swaps your spellbook any time.", left: "" },
        { name: "Overgrown (tier 7)", priority: "P0", built: 0, tested: "no", about: "Planned: Farming boosts.", left: "Needs Farming first." },
        { name: "Specialist (tier 8)", priority: "P0", built: 100, tested: "no", about: "Special attacks cost 20%, hit twice as accurately and refund energy.", left: "" },
        { name: "Guardian (tier 8)", priority: "P0", built: 90, tested: "no", about: "A guardian follower that fights beside you, using the style your target is weakest to.", left: "It looks like a royal guard, because the real guardian has no model in this game version." },
        { name: "Last Stand (tier 8)", priority: "P0", built: 100, tested: "no", about: "Heal from the damage you deal.", left: "" }
      ]
    },
    {
      id: "combat",
      name: "Combat",
      blurb: "How fighting works: monster attacks, special attacks, masteries and gear effects.",
      items: [
        { name: "Monster combat", priority: "P0", built: 100, tested: "no", about: "Monsters use melee, ranged and magic, can hit both teammates, and the whole world is multi-combat.", left: "", doc: "isle_of_souls_npc_combat.md" },
        { name: "Drop tables", priority: "P0", built: 100, tested: "no", about: "Real drop tables for 1,645 monsters, with boosted rates for wiki-listed drops and rare-drop announcements.", left: "", doc: "isle_of_souls_drops.md" },
        { name: "Combat masteries", priority: "P0", built: 100, tested: "no", about: "All 18 melee, ranged and magic masteries plus the 6 passives. Each mastery task gives 2 points.", left: "" },
        { name: "Special attacks", priority: "P1", built: 90, tested: "no", about: "Every weapon with a special attack in this game version (268).", left: "A few numbers are guesses. The full blowpipe and one Hunllef effect are left.", doc: "isle_of_souls_special_attacks.md" },
        { name: "Autocast", priority: "P1", built: 100, tested: "no", about: "Choose an autocast spell from the combat tab.", left: "" },
        { name: "Dragonfire", priority: "P1", built: 90, tested: "no", about: "Wiki protection rules for shields, potions and prayer.", left: "Some numbers are guesses.", doc: "isle_of_souls_dragonfire.md" },
        { name: "Enchanted bolts and poison", priority: "P1", built: 90, tested: "no", about: "All 10 gem bolt effects, enchanting bolts with magic, and poison against players.", left: "The bolt enchanting menu hasn't been seen in the game yet.", doc: "isle_of_souls_enchanted_bolts.md" },
        { name: "Revenant weapons", priority: "P1", built: 100, tested: "no", about: "Craw's bow, Viggora's mace, Thammaron's sceptre, their upgrades and the bracelet of ethereum, charged with ether.", left: "", doc: "isle_of_souls_revenant_weapons_plan.md" },
        { name: "PvP zones", priority: "P1", built: 100, tested: "no", about: "Areas where players can fight each other, switched on or off per area.", left: "", doc: "isle_of_souls_pvp_plan.md" },
        { name: "Monster animations", priority: "P1", built: 75, tested: "no", about: "Monsters use their own block animations instead of a human one.", left: "Some monsters still use the wrong attack or death animation." }
      ]
    },
    {
      id: "world",
      name: "World",
      blurb: "The isle itself: monsters, dungeons, shops, banks and places.",
      items: [
        { name: "Monster spawns", priority: "P0", built: 100, tested: "no", about: "22,700 monsters placed across the world as in the real game.", left: "", doc: "isle_of_souls_npc_spawns.md" },
        { name: "Monster types", priority: "P1", built: 100, tested: "no", about: "Undead, demon, dragon and other types set, so slayer gear and salve amulets work.", left: "", doc: "isle_of_souls_npc_attributes.md" },
        { name: "Dungeons", priority: "P1", built: 75, tested: "no", about: "21 dungeons reached from the isle, including Taverley, Brimhaven, the Catacombs, Kalphite Lair and Karuulm.", left: "Some doors, gates and dungeon obstacles still don't work.", doc: "isle_of_souls_dungeons_plan.md" },
        { name: "Revenant Caves", priority: "P1", built: 100, tested: "no", about: "All 30 revenants placed from the wiki map.", left: "" },
        { name: "Shops", priority: "P0", built: 60, tested: "no", about: "General store, Aubury's rune shop, the Mage of Zamorak, Mining Guild shops and Grace's shop.", left: "Skill supply shops and a final money plan." },
        { name: "Banks, bank chests and deposit boxes", priority: "P0", built: 100, tested: "no", about: "Bank chests and 19 deposit boxes across the isle, all using your team's shared bank.", left: "" },
        { name: "Starter tools", priority: "P0", built: 100, tested: "no", about: "Places on the isle to get a pickaxe, axe and hammer, a general store and a furnace.", left: "", doc: "isle_of_souls_starter_objects.md" },
        { name: "Mining Guild", priority: "P1", built: 100, tested: "no", about: "Ladder entrance, golden nuggets, prospector gear and the guild shops.", left: "" },
        { name: "Crumbling Tower", priority: "P2", built: 100, tested: "no", about: "Stairs, trapdoor and basement ladder work.", left: "" },
        { name: "Soul Wars lobby", priority: "P0", built: 100, tested: "no", about: "New players, respawns and home teleport land in the lobby graveyards. Barriers and portals work.", left: "" },
        { name: "Guide NPC or sign", priority: "P1", built: 0, tested: "no", about: "Planned: something on the isle that explains the league and teams.", left: "All of it." },
        { name: "Hide the Areas button", priority: "P2", built: 0, tested: "no", about: "The whole isle is open from the start, so the Leagues Areas screen isn't needed.", left: "Try hiding it; leave it if that's too much work." }
      ]
    },
    {
      id: "progress",
      name: "Tasks and progression",
      blurb: "Tasks earn your team points, points unlock relic tiers, and the most points wins.",
      items: [
        { name: "Leagues screens", priority: "P0", built: 100, tested: "passed", about: "The Leagues tab, task list, relic picker, task tracker, points and pop-ups.", left: "" },
        { name: "League task list", priority: "P0", built: 80, tested: "no", about: "Raging Echoes tasks that the isle can support, plus new ones for isle content. Grows as content is added.", left: "Check every task can be completed, and add tasks for content still to come." },
        { name: "Relic tier thresholds", priority: "P0", built: 0, tested: "no", about: "How many points unlock each tier.", left: "Set last, from the finished task list and XP rates." },
        { name: "Collection log", priority: "P1", built: 100, tested: "no", about: "The real collection log with kill counts and an overview page.", left: "", doc: "isle_of_souls_collection_log_plan.md" },
        { name: "Clue scrolls and caskets", priority: "P1", built: 25, tested: "no", about: "Planned: clues drop from monsters and skilling, and reading one gives the casket straight away. No treasure trails.", left: "Scroll and casket items, real reward tables, sources and tasks." }
      ]
    },
    {
      id: "ops",
      name: "Event running",
      blurb: "What it takes to run the day itself.",
      items: [
        { name: "Teams", priority: "P0", built: 100, tested: "no", about: "Teams of two sharing tasks, points, loot and rankings, with freeze and announcements.", left: "", doc: "isle_of_souls_teams.md" },
        { name: "Team instances", priority: "P0", built: 100, tested: "no", about: "A private copy of a boss or minigame area for your team.", left: "" },
        { name: "Shared team bank", priority: "P0", built: 100, tested: "no", about: "One bank per team, one teammate in it at a time.", left: "" },
        { name: "Leaderboard page", priority: "P0", built: 100, tested: "no", about: "A live web page of team rankings, written by the server.", left: "" },
        { name: "Autosave", priority: "P0", built: 100, tested: "no", about: "Saves every 2 minutes, on key events and on disconnect.", left: "" },
        { name: "Event settings", priority: "P0", built: 100, tested: "no", about: "One file controls cave lengths, respawn speed, PvP areas and more.", left: "" },
        { name: "Respawn speed", priority: "P1", built: 100, tested: "no", about: "Monsters and resources come back faster for the short event.", left: "" },
        { name: "F-key hotkeys", priority: "P1", built: 100, tested: "no", about: "Default F-keys at login, and rebinding them works and is remembered.", left: "" },
        { name: "Game settings remembered", priority: "P1", built: 75, tested: "no", about: "Some client settings reset at login. Shift-drop is forced on for now.", left: "A real fix so every setting is remembered." },
        { name: "Final XP rates", priority: "P0", built: 0, tested: "no", about: "XP rates for the day, tuned so the best players can max.", left: "Tune down from the testing rates." },
        { name: "Hide dev commands", priority: "P0", built: 0, tested: "no", about: "Players can't use the testing commands.", left: "All of it." },
        { name: "Reset checklist", priority: "P0", built: 50, tested: "no", about: "Steps to wipe test progress before the event.", left: "Bring the checklist up to date with everything built since.", doc: "isle_of_souls_teams.md" },
        { name: "Dress rehearsal", priority: "P0", built: 0, tested: "no", about: "A full run with at least four players before the day.", left: "Plan and run it." },
        { name: "10-hour soak test", priority: "P0", built: 0, tested: "no", about: "Server left running for 10 hours with players, watching memory and saves.", left: "Plan and run it." },
        { name: "Backups and restart plan", priority: "P1", built: 0, tested: "no", about: "What happens if the server goes down mid-event.", left: "All of it." },
        { name: "Player wiki", priority: "P1", built: 10, tested: "no", about: "This wiki: the tracker now, guides for skills, bosses and relics later.", left: "Player guide pages." },
        { name: "Connection guide", priority: "P2", built: 25, tested: "no", about: "A one-page guide for players to connect to the server.", left: "Write the page; the setup script exists." }
      ]
    }
  ]
};
