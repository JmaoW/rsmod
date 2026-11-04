// Isle of Souls skill pages: the hand-written half.
//
// The other half (level tables, where things are, league tasks) is generated from the game code into
// skills-generated.js by tools/wiki/gen_skills.py. Re-run that after changing a skill.
//
// Each skill has:
//   id        page address: skill.html?s=<id>
//   name      shown name
//   group     "combat", "gathering", "production" or "utility"
//   tracker   the content tracker rows it takes its build and test state from (data/content.js)
//   page      true once the page is written; false shows "Page coming" on the Skills page
//   summary   one line for the Skills page
//   notice    optional warning shown at the top of the page
//   intro     paragraphs: how the skill works here
//   reach     how to get to each area that has something for this skill, by area key (see
//             skills-generated.js "areas"). {dir} is replaced by where the entrance is, worked out
//             from the game code, so it stays right when an entrance moves.
//   placeNames  names for groups of rocks, targets etc. on the "Where to train" list. A group whose
//             middle lies inside box [x0, z0, x1, z1] in that area takes the name. Groups that get the
//             same name are shown as one.
//   gear      [{name, text}]
//   relics    [{name, tier, text}]
//   differences  lines on what is different from the main game or left out
//   osrs      the OSRS Wiki page for the basics
//
// Text may use <b>, <i> and <a> tags.

window.ISLE_SKILLS = {
  updated: "2026-11-04",
  groups: [
    { id: "combat", name: "Combat" },
    { id: "gathering", name: "Gathering" },
    { id: "production", name: "Production" },
    { id: "utility", name: "Utility" }
  ],
  // How to reach an area, used by every skill page unless the skill gives its own line.
  reach: {
    isle: "",
    essence: "Talk to Aubury, beside the Mage of Zamorak just north of the lobby, and choose <i>Teleport me to the essence mine</i>. The portal beside where you land takes you back.",
    guild: "Climb down the ladder at the south mine, {dir}. Needs 60 Mining (a boost counts).",
    morulrek: "Go down into Karamja Dungeon (entrance {dir}) and through the cave at the dungeon's east end.",
    karamja: "Entrance {dir}.",
    souls: "The cave entrance {dir}.",
    _dungeon: "Entrance {dir}."
  },
  skills: [
    {
      id: "melee", name: "Melee", group: "combat", tracker: ["Attack", "Strength", "Defence"], page: false,
      summary: "Attack, Strength and Defence, trained on the monsters across the isle and its dungeons."
    },
    {
      id: "ranged", name: "Ranged", group: "combat", tracker: ["Ranged"], page: false,
      summary: "Bows, crossbows, thrown weapons and chinchompas."
    },
    {
      id: "magic", name: "Magic", group: "combat", tracker: ["Magic"], page: false,
      summary: "All four spellbooks, alchemy, island teleports and team spells."
    },
    {
      id: "hitpoints", name: "Hitpoints", group: "combat", tracker: ["Hitpoints"], page: false,
      summary: "Trained alongside every combat style."
    },
    {
      id: "prayer", name: "Prayer", group: "combat", tracker: ["Prayer"], page: false,
      summary: "Bury bones, use the altars, every prayer unlocked from the start."
    },
    {
      id: "mining", name: "Mining", group: "gathering", tracker: ["Mining"], page: true,
      summary: "Ore, gems and essence for Smithing, Crafting and Runecraft.",
      intro: [
        "Mining works as in the main game: click a rock with a pickaxe in your inventory or worn, and you keep swinging until you get the ore. A higher level and a better pickaxe get it faster. Any ore can come with an uncut gem.",
        "Start at the <b>south mine</b>, south of the lobby, where a rock gives out free bronze pickaxes and a furnace and hammer stand close by. At 60 Mining the ladder there takes you down to the <b>Mining Guild</b>, which has every ore up to runite plus amethyst, and gives a golden nugget with every ore.",
        "Essence for Runecraft comes from the <b>rune essence mine</b>: rune essence below level 30 and pure essence from 30. The essence rocks never run out."
      ],
      placeNames: [
        { area: "isle", box: [2175, 2780, 2215, 2805], name: "South mine", text: "Take a bronze pickaxe from the pickaxe rock at the south end. The furnace and a hammer are just east of the mine, and the Mining Guild ladder stands in it." },
        { area: "guild", box: [2990, 9695, 3075, 9770], name: "Mining Guild", text: "Every ore mined here also gives a golden nugget. Percy and Yarsul have their shops here." },
        { area: "guild", box: [2990, 9771, 3075, 9840], name: "Dwarven Mine, north of the guild", text: "Out through the guild's doors. Ore here gives no nuggets." },
        { area: "essence", box: [2880, 4800, 2940, 4860], name: "The four essence rocks", text: "One in each corner of the mine." }
      ],
      gear: [
        { name: "Pickaxes", text: "Free bronze pickaxes from the pickaxe rock at the south mine. Yarsul in the Mining Guild sells bronze to rune." },
        { name: "Prospector kit", text: "Percy in the Mining Guild sells the prospector helmet (40 golden nuggets), jacket (60), legs (50) and boots (30). Worn, they give more Mining XP: 0.4%, 0.8%, 0.6% and 0.2%, plus 0.5% for the full set, 2.5% in all." },
        { name: "Echo pickaxe", text: "Comes with the Power Miner relic. No level needed to use it, and it never degrades." }
      ],
      relics: [
        { name: "Power Miner", tier: 1, text: "Gives the Echo pickaxe. While you wear it: a failed swing gets a second 50% chance, ore goes straight to the bank, and a rock gives 4 ores before it empties. The pickaxe's <i>Toggle</i> option turns on auto-smelting (copper and tin make bronze bars, iron makes iron bars, coal makes steel, mithril, adamantite and runite make their bars with no coal needed), auto-cutting the gems you find, and auto-banking." },
        { name: "Friendly Forager", tier: 2, text: "Each ore you mine can turn up a grimy herb (up to your Herblore level + 25). Herbs go to the bank." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Rocks refill faster than in the main game.",
        "Amethyst crystals give a set number of amethyst, then stay empty until they refill.",
        "Gems come with 1 in every 256 ores. The gem chances are close to the main game's but not exact.",
        "Not in the league: gem rocks, sandstone, granite, volcanic sulphur, clue geodes, the Motherlode Mine, mining gloves and the Mining Guild's hidden +7 boost.",
        "There are no quests, so nothing is locked behind one."
      ],
      osrs: "Mining"
    },
    {
      id: "fishing", name: "Fishing", group: "gathering", tracker: ["Fishing"], page: false,
      summary: "Fishing spots around the isle, feeding Cooking."
    },
    {
      id: "woodcutting", name: "Woodcutting", group: "gathering", tracker: ["Woodcutting"], page: false,
      summary: "Trees across the isle; logs feed Firemaking and Fletching."
    },
    {
      id: "hunter", name: "Hunter", group: "gathering", tracker: ["Hunter"], page: false,
      summary: "Traps, pitfalls, butterflies, implings, falconry and birdhouses."
    },
    {
      id: "farming", name: "Farming", group: "gathering", tracker: ["Farming"], page: false,
      summary: "Not in the league yet."
    },
    {
      id: "smithing", name: "Smithing", group: "production", tracker: ["Smithing"], page: false,
      summary: "Smelt bars at furnaces and smith them at anvils."
    },
    {
      id: "cooking", name: "Cooking", group: "production", tracker: ["Cooking"], page: false,
      summary: "Every cooked food, pies, pizzas, stews and wine."
    },
    {
      id: "fletching", name: "Fletching", group: "production", tracker: ["Fletching"], page: false,
      summary: "Bows, crossbows, arrows, bolts, darts and javelins."
    },
    {
      id: "crafting", name: "Crafting", group: "production", tracker: ["Crafting"], page: false,
      summary: "Gems, leather, dragonhide, jewellery, battlestaves, pottery and glass."
    },
    {
      id: "herblore", name: "Herblore", group: "production", tracker: ["Herblore"], page: false,
      summary: "Clean herbs and make potions, from attack potions to super combats."
    },
    {
      id: "runecraft", name: "Runecraft", group: "production", tracker: ["Runecraft"], page: false,
      summary: "14 altars, combination runes, pouches and the Abyss."
    },
    {
      id: "firemaking", name: "Firemaking", group: "production", tracker: ["Firemaking"], page: false,
      summary: "Light fires anywhere and cook on them."
    },
    {
      id: "construction", name: "Construction", group: "production", tracker: ["Construction"], page: false,
      summary: "Not in the league yet."
    },
    {
      id: "agility", name: "Agility", group: "utility", tracker: ["Agility"], page: false,
      summary: "The Brimhaven Agility Arena and marks of grace."
    },
    {
      id: "thieving", name: "Thieving", group: "utility", tracker: ["Thieving"], page: true,
      summary: "Pickpocket people and rob stalls for coins, gems and supplies.",
      notice: "Right now the only places to train Thieving are in Mor Ul Rek: TzHaar-Hur (level 90, needs a fire cape) and the TzHaar gem and ore stalls (levels 75 and 82). Everything else on this page works, but none of the other targets or stalls stand anywhere you can reach yet.",
      intro: [
        "Use an npc's <i>Pickpocket</i> option, or <i>Steal-from</i> on a market stall. A pickpocket can fail: the target hits you for its stun damage and you are stunned for about 5 seconds. A higher level makes failing less likely. Stalls never fail; once robbed, a stall stays empty for a while.",
        "Coins from pickpocketing come as coin pouches. Open them with <i>Open-all</i>. You can't pickpocket while you hold 28 pouches of one kind (84 with Dodgy Deals)."
      ],
      placeNames: [
        { area: "morulrek", box: [2515, 5065, 2530, 5080], name: "TzHaar gem and ore stalls", text: "Side by side in the south-east of the city." },
        { area: "morulrek", box: [2430, 5050, 2560, 5185], name: "TzHaar-Hur around the city", text: "TzHaar-Hur walk all over Mor Ul Rek. Bring a fire cape." }
      ],
      gear: [
        { name: "Gloves of silence", text: "5% better chance to pickpocket. They wear out after 62 failed pickpockets." },
        { name: "Dodgy necklace", text: "Stops 1 in 4 stuns, and crumbles after it has saved you 10 times. Made by enchanting an opal necklace." },
        { name: "Rogue equipment", text: "Each piece (mask, top, trousers, gloves, boots) gives a 15% chance of double loot from a pickpocket. The full set always doubles it." },
        { name: "Thieving cape or max cape", text: "10% better chance to pickpocket." },
        { name: "Fire cape", text: "Needed, worn or carried, to pickpocket TzHaar-Hur. An infernal cape works too." }
      ],
      relics: [
        { name: "Dodgy Deals", tier: 2, text: "Pickpockets never fail. Picking one pocket also picks every npc of the same kind within 5 tiles, for extra loot but no extra XP (each one counts toward the \"50 times\" tasks). Pickpockets and stall thefts repeat by themselves until something stops them. Pickpocketed items come noted. You can hold 84 coin pouches of a kind. Stalls never empty." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Stalls refill faster than in the main game, and nobody guards them.",
        "There are no quests, so cave goblins, Fremennik citizens, Knights of Varlamore, vyres and elves are not locked. The Digsite workman gives his after-quest loot.",
        "Not in the league: clue scrolls from pickpocketing, blackjacking, the pirate, the Port Roberts and wine stalls, Pyramid Plunder, Rogues' Den, the Rocky pet, and the Ardougne diary bonuses."
      ],
      osrs: "Thieving"
    },
    {
      id: "slayer", name: "Slayer", group: "utility", tracker: ["Slayer"], page: false,
      summary: "Turael, Vannaka and Duradel, the rewards shop and superiors."
    }
  ]
};
