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
//   soon      the tag shown instead of "Page coming" (optional)
//   where     paragraphs for "Where to train" when the skill has no fixed places (optional)
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
      id: "melee", name: "Melee", group: "combat", tracker: ["Attack", "Strength", "Defence"], page: true,
      icons: ["attack", "strength", "defence"],
      summary: "Attack, Strength and Defence, trained on the monsters across the isle and its dungeons.",
      intro: [
        "Attack, Strength and Defence are trained together by fighting with a melee weapon. Your attack style decides which one gets the XP: 4 XP per damage in that skill (split three ways on a shared style), plus 1.33 Hitpoints XP per damage.",
        "Attack decides which weapons you can wield, Defence which armour you can wear, and Strength how hard you hit. Every weapon's special attack works.",
        "Combat masteries (from the Leagues screen) add more on top: each mastery task gives 2 points to spend on melee, ranged or magic tiers. Melee masteries give rerolls on low hits, echo hits that can chain, faster attacks and healing when you hit."
      ],
      where: [
        "On any monster. The table below lists every monster standing somewhere you can walk to, from the chickens on the isle to the Alchemical Hydra. Bosses will get their own pages."
      ],
      relics: [
        { name: "Specialist", tier: 8, text: "Special attacks cost 20% energy and are twice as accurate. A missed hit gives 10% energy back, and a kill gives 15%." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      osrs: "Combat"
    },
    {
      id: "ranged", name: "Ranged", group: "combat", tracker: ["Ranged"], page: true,
      summary: "Bows, crossbows, thrown weapons and chinchompas.",
      intro: [
        "Ranged is trained by fighting with a bow, crossbow, thrown weapon or chinchompas: 4 Ranged XP per damage (2 Ranged and 2 Defence on the longrange style), plus 1.33 Hitpoints XP per damage.",
        "Arrows, bolts, darts and javelins come from Fletching, and chinchompas from Hunter (box traps). Every weapon's special attack works, and enchanted bolts have their effects.",
        "Combat masteries (from the Leagues screen) add more on top: each mastery task gives 2 points to spend on melee, ranged or magic tiers. Ranged masteries raise your lowest hits, build up damage over a streak, speed up attacks and let you skip accuracy rolls."
      ],
      where: [
        "On any monster: see the list of monsters you can reach on the <a href=\"skill.html?s=melee#t-monsters\">Melee page</a>."
      ],
      relics: [
        { name: "Specialist", tier: 8, text: "Special attacks cost 20% energy and are twice as accurate. A missed hit gives 10% energy back, and a kill gives 15%." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      osrs: "Ranged"
    },
    {
      id: "magic", name: "Magic", group: "combat", tracker: ["Magic"], page: true,
      summary: "All four spellbooks, alchemy, island teleports and team spells.",
      notice: "You start on the standard spellbook. The only way to change book is the Grimoire relic (tier 7), so Ancient, Lunar and Arceuus spells can't be used before then.",
      intro: [
        "Every spell in the four spellbooks is unlocked from the start, with no quests needed; you only need the level and the runes. Combat spells give their base XP plus 2 XP per damage. Utility spells (alchemy, Superheat Item, enchanting, charging orbs, Telekinetic Grab, Bones to Bananas and more) give their own XP.",
        "Teleports keep their real level, runes and XP, but every one lands somewhere on the isle (see <i>Where teleports go</i>). Lunar team spells work on your teammate.",
        "Combat masteries (from the Leagues screen) add more on top: each mastery task gives 2 points to spend on melee, ranged or magic tiers. Magic masteries add damage the longer you wait between casts, bonus damage on high hits, faster casting and healing on big hits."
      ],
      where: [
        "On any monster (see the <a href=\"skill.html?s=melee#t-monsters\">Melee page</a> for the list), or by casting utility spells anywhere. Runes come from Runecraft and the Mage of Zamorak's shop."
      ],
      relics: [
        { name: "Golden God", tier: 4, text: "High and Low Alchemy need no runes and no level, give 15% more coins, have a 65% chance not to use up the item, and repeat on the whole stack." },
        { name: "Grimoire", tier: 7, text: "Gives the Arcane grimoire: switch to any spellbook and back for free." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Tele Block and Teleport to Target do nothing (there's no target to send you to). Teleport to House is off.",
        "Left out: the Farming, Hunter and house spells (Geomancy, Cure Plant, Fertile Soil, Hunter Kit and others), and the Arceuus reanimation, Shadow Veil, Dark Lure, Corruption and Ward of Arceuus spells."
      ],
      osrs: "Magic"
    },
    {
      id: "hitpoints", name: "Hitpoints", group: "combat", tracker: ["Hitpoints"], page: true,
      summary: "Trained alongside every combat style.",
      intro: [
        "Hitpoints goes up by itself as you fight: 1.33 XP for every point of damage you deal in combat. Your Hitpoints level is how much damage you can take.",
        "Heal by eating food (see Cooking) or drinking potions like Saradomin brews (see Herblore). Hitpoints also come back slowly on their own. Redemption heals you when a hit leaves you below 10%."
      ],
      where: [
        "Wherever you train another combat skill."
      ],
      relics: [
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      osrs: "Hitpoints"
    },
    {
      id: "prayer", name: "Prayer", group: "combat", tracker: ["Prayer"], page: true,
      summary: "Bury bones, use the altars, every prayer unlocked from the start.",
      intro: [
        "Bury bones from your inventory, or use them on an altar for 3.5 times the XP. Bones come from the monsters you kill.",
        "<b>Every prayer is unlocked from the start</b>, Piety, Chivalry, Rigour, Augury and Preserve included. You still need the Prayer level (and the Defence level for Piety, Chivalry, Rigour and Augury) to use them.",
        "<i>Pray-at</i> on any altar fills your Prayer points back up."
      ],
      reach: {
        tower: "Climb down the trapdoor in the Crumbling Tower, {dir}."
      },
      placeNames: [
        { area: "isle", box: [2200, 2850, 2220, 2865], name: "Altar by the bank", text: "In the lobby plaza, just south of the bank chest." },
        { area: "tower", box: [1900, 9420, 1960, 9480], name: "Chaos altar", text: "Each bone offered here has a 50% chance not to be used up, so it's the best place to train." }
      ],
      gear: [
        { name: "Bones", text: "Dropped by monsters. The better the monster, the better the bones: dragons, wyverns, drakes, hydras and the Dagannoth Kings give the most." }
      ],
      relics: [
        { name: "Golden God", tier: 4, text: "You can offer 20,000 coins on an altar for the same XP as a dragon bone (more coins make more offerings)." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Every prayer is unlocked from the start, with no quests or scrolls. Deadeye and Mystic Vigour (the scroll upgrades) are not.",
        "Smite and Retribution can be switched on, but do nothing yet.",
        "Both altars give 3.5 times the burying XP; the chaos altar also saves half the bones.",
        "Not in the league: gilded altars, incense burners, the Ectofuntus, player-owned house altars, and bone shards."
      ],
      osrs: "Prayer"
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
      id: "fishing", name: "Fishing", group: "gathering", tracker: ["Fishing"], page: true,
      summary: "Fishing spots around the isle, feeding Cooking.",
      intro: [
        "Fishing works as in the main game: click a fishing spot with the right tool (and bait, if it needs it) and you keep fishing until your inventory is full. Each spot has two options, and the fish you get depends on the option and your level. Spots here stay where they are.",
        "Every spot is on the isle's coast. Gerrant sells the tools and bait from the far west coast ({dir:gerrant}), right beside the monkfish, shark and anglerfish spots. Shrimps and sardines are south-west of the lobby, karambwan in the south-east, and salmon and lobsters in the north-east."
      ],
      placeNames: [
        { area: "isle", box: [2075, 2885, 2095, 2910], name: "West coast, by Gerrant", text: "A monkfish spot, a shark spot and the anglerfish spot side by side." },
        { area: "isle", box: [2155, 2775, 2175, 2795], name: "South-west shore", text: "" },
        { area: "isle", box: [2255, 2780, 2275, 2795], name: "South-east shore", text: "Catch karambwanji first: they're the bait for karambwan." },
        { area: "isle", box: [2265, 2965, 2295, 2985], name: "North-east river mouth", text: "" }
      ],
      gear: [
        { name: "Gerrant's shop", text: "Gerrant, on the west coast ({dir:gerrant}), sells a small fishing net, fishing rod, fly fishing rod, harpoon, lobster pot, fishing bait, feathers and sandworms. He buys raw fish too. He doesn't sell a big fishing net or a karambwan vessel." },
        { name: "Echo harpoon", text: "Comes with the Animal Wrangler relic. It works as every fishing tool, needs no bait, and fishes a tick faster." }
      ],
      relics: [
        { name: "Animal Wrangler", tier: 1, text: "Gives the Echo harpoon. While you wear it: a failed catch gets a second 50% chance, fish go straight to the bank (not karambwanji), and every fish is cooked as you catch it, with Cooking XP and no level needed. You never burn food. Cooked-as-caught fish count for cooking tasks." },
        { name: "Friendly Forager", tier: 2, text: "Each fish you catch can turn up a grimy herb (up to your Herblore level + 25). Herbs go to the bank." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Fishing spots never move.",
        "Not in the league: barbarian fishing, aerial fishing, minnows, dark crabs, sacred and infernal eels, drift nets, Tempoross, the Fishing Guild, the angler's outfit, and spirit flakes or other catch-rate boosts."
      ],
      osrs: "Fishing"
    },
    {
      id: "woodcutting", name: "Woodcutting", group: "gathering", tracker: ["Woodcutting"], page: true,
      summary: "Trees across the isle; logs feed Firemaking and Fletching.",
      intro: [
        "Woodcutting works as in the main game: click a tree with an axe in your inventory or worn, and you keep chopping until your inventory is full or the tree falls. Each tree turns into a stump after a while and grows back later; better trees last longer. A higher level and a better axe chop faster.",
        "Normal trees grow all over the isle. A free bronze axe sits on a stump {dir:axe_stump}. Maples and dead trees stand south-east of the lobby, yews to the west and the north-west, and teak and mahogany trees in the far north."
      ],
      placeNames: [
        { area: "isle", box: [2180, 2984, 2212, 2996], name: "Teak and mahogany trees in the north", text: "A small group of hardwood trees near the north edge of the isle." },
        { area: "essence", box: [2880, 4800, 2940, 4860], name: "Dead trees in the essence mine", text: "" }
      ],
      gear: [
        { name: "Axes", text: "Take a free bronze axe from the stump {dir:axe_stump}. It comes back a few seconds after someone takes it." },
        { name: "Lumberjack outfit", text: "Worn, it gives more Woodcutting XP: 0.4% for the hat, 0.8% for the top, 0.6% for the legs and 0.2% for the boots, plus 0.5% for the full set, 2.5% in all. The forestry recolour counts the same." },
        { name: "Echo axe", text: "Comes with the Lumberjack relic. No level needed to use it, and it never degrades." }
      ],
      relics: [
        { name: "Lumberjack", tier: 1, text: "Gives the Echo axe. While you wear it: a failed chop gets a second 50% chance and logs go straight to the bank. The axe's <i>Toggle</i> option turns on auto-burning logs (with Firemaking XP) or auto-fletching them into arrow shafts (with Fletching XP, the real number of shafts for the log), and auto-banking." },
        { name: "Friendly Forager", tier: 2, text: "Each log you chop can turn up a grimy herb (up to your Herblore level + 25). Herbs go to the bank." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Trees grow back faster than in the main game.",
        "Not in the league: bird nests, the Woodcutting Guild, canoes, Forestry events, and the special effects of the infernal and crystal axes."
      ],
      osrs: "Woodcutting"
    },
    {
      id: "hunter", name: "Hunter", group: "gathering", tracker: ["Hunter"], page: true,
      summary: "Traps, pitfalls, butterflies, implings, falconry and birdhouses.",
      notice: "Right now only bird snares (crimson swifts from level 1, copper longtails from 9) and box traps (chinchompas from 53) can be trained: those are the only Hunter creatures standing anywhere you can reach. Everything else on this page works, but there are no butterflies, implings, kebbits, salamanders, big cats, deadfall boulders, net-trap trees, pitfalls or birdhouse spots on the isle yet.",
      intro: [
        "<b>Bird snares and box traps</b> are laid from your inventory where you stand. A creature of the right kind nearby walks into it, and you <i>Check</i> the trap to collect the catch and the XP. You can have one trap out, plus one more for every 20 Hunter levels (5 at most). A trap that catches nothing collapses after a minute.",
        "<b>Deadfalls</b> are set on boulders with a knife and logs, <b>net traps</b> on young trees with a rope and a small fishing net, and <b>pitfalls</b> are dug with a knife and logs, then you tease a big cat with a teasing stick and jump the pit so it follows you in.",
        "<b>Butterflies, moths and implings</b> are caught with a butterfly net (or barehanded at a higher level) into a jar. <b>Falconry</b> needs a falconer's glove. <b>Birdhouses</b> are built with Crafting, filled with 10 seeds and emptied 10 minutes later."
      ],
      placeNames: [
        { area: "isle", box: [2115, 2940, 2140, 2965], name: "Chinchompas in the north-west", text: "Box traps, level 53." },
        { area: "isle", box: [2195, 2950, 2220, 2975], name: "Copper longtails in the north", text: "Bird snares, level 9." },
        { area: "isle", box: [2145, 2810, 2170, 2840], name: "Crimson swifts south-west of the lobby", text: "Bird snares, level 1." }
      ],
      gear: [
        { name: "Bait", text: "Matching bait in a trap gives a slightly better chance (spicy tomato for grey chinchompas, spicy minced meat for red and black ones). A lit torch helps a little too." }
      ],
      relics: [
        { name: "Animal Wrangler", tier: 1, text: "Traps never fail. Box traps look for a catch every 2 ticks instead of 5, and a chinchompa catch gives two chinchompas and twice the XP. Impling jars never break." },
        { name: "Friendly Forager", tier: 2, text: "Each trap or pit you collect, butterfly or impling you catch, kebbit you hunt and birdhouse you empty can turn up a grimy herb (up to your Herblore level + 25). Herbs go to the bank." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Birdhouses take 10 minutes instead of 50.",
        "Falconry is simpler: there is no falcon to fetch, the catch comes straight back.",
        "There are no quests, so nothing is locked behind one.",
        "Not in the league: herbiboar, aerial fishing, drift nets, Hunter Rumours, the Guild Hunter outfit, the monkey trap and rabbit snares, big cat clothing recipes, and clue nests from birdhouses."
      ],
      osrs: "Hunter"
    },
    {
      id: "farming", name: "Farming", group: "gathering", tracker: ["Farming"], page: false, soon: "Not in the league yet",
      summary: "Not in the league yet."
    },
    {
      id: "smithing", name: "Smithing", group: "production", tracker: ["Smithing"], page: true,
      summary: "Smelt bars at furnaces and smith them at anvils.",
      intro: [
        "Use ore on a furnace (or click <i>Smelt</i>) to choose a bar and how many to make. Iron ore only works half the time; the other bars always do. Then use a bar on an anvil, or click <i>Smith</i>, with a hammer in your inventory, and pick what to make from the anvil's menu.",
        "The furnace and an anvil stand together at the south mine, {dir:furnace}, with a free hammer on the ground beside them that comes back a few seconds after someone takes it. The Mining Guild has two more anvils."
      ],
      placeNames: [
        { area: "isle", box: [2205, 2775, 2235, 2800], name: "By the south mine", text: "A furnace, an anvil and a free hammer, next to the rocks." }
      ],
      gear: [
        { name: "Hammer", text: "Needed to smith. A free one lies beside the anvil at the south mine." }
      ],
      relics: [
        { name: "Power Miner", tier: 1, text: "The Echo pickaxe can smelt ore into bars as you mine it, with Smithing XP and no level needed. Copper and tin make bronze, iron makes iron, coal makes steel, and mithril, adamantite and runite need no coal." },
        { name: "Production Master", tier: 5, text: "Smelting makes every bar your ores allow in one go, and smithing makes as many items as your bars allow." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "You can also smelt with the Superheat Item spell (see Magic).",
        "Not in the league: cannonballs, the Blast Furnace, the Giants' Foundry, the ring of forging, the goldsmith gauntlets, the Smiths' outfit, and the Lovakengj bars and Shayzien armour."
      ],
      osrs: "Smithing"
    },
    {
      id: "cooking", name: "Cooking", group: "production", tracker: ["Cooking"], page: true,
      summary: "Every cooked food, pies, pizzas, stews and wine.",
      intro: [
        "Use raw food on a fire or a range, or click it, and you cook every piece of that food you carry. Each one can burn: the chance drops as your level rises, and the tables say the level where each food stops burning. Breads, pies, pizzas, cakes, stews and other baked things need a range.",
        "Pies, pizzas, stews, potatoes and the bowl dishes are made by using their ingredients on each other first (see <i>Preparing food</i>). Unfermented wine turns into wine after a short wait, or into bad wine if you fail."
      ],
      where: [
        "On any fire: light your own with logs and a tinderbox (see Firemaking), or use one of the fires that are always burning. There's no range on the isle itself; the nearest is in Taverley Dungeon."
      ],
      placeNames: [
        { area: "taverley", box: [2900, 9690, 2940, 9730], name: "Range", text: "" }
      ],
      relics: [
        { name: "Animal Wrangler", tier: 1, text: "You never burn food. Fish you catch with the Echo harpoon are cooked as you catch them, with Cooking XP and no level needed." },
        { name: "Production Master", tier: 5, text: "Cooking on a fire or range cooks every raw piece in one go, and pies, pizzas and wine are made all at once." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "There is no \"how many?\" menu: cooking always does every piece, like choosing <i>All</i>.",
        "Not in the league: the Cooks' Guild, cooking gauntlets, the Lumbridge and Hosidius range bonuses, and making butter, cheese or flour (there's no dairy churn or windmill)."
      ],
      osrs: "Cooking"
    },
    {
      id: "fletching", name: "Fletching", group: "production", tracker: ["Fletching"], page: true,
      summary: "Bows, crossbows, arrows, bolts, darts and javelins.",
      intro: [
        "Use a knife on logs and pick what to make: arrow shafts, javelin shafts, unstrung bows, crossbow stocks or shields. String a bow by using a bow string on it. Crossbows are a stock and limbs put together with a hammer, then strung with a crossbow string.",
        "Ammunition is made by using the parts on each other: feathers on arrow shafts make headless arrows, then arrowtips finish them. Darts, bolts and javelins work the same way, and a chisel on a gem makes bolt tips."
      ],
      where: [
        "Anywhere: Fletching only needs the items in your inventory. Logs come from Woodcutting, metal parts (arrowtips, dart tips, bolts, limbs, javelin heads) from Smithing, and gems from Mining and Crafting."
      ],
      gear: [
        { name: "Feathers", text: "Gerrant on the west coast ({dir:gerrant}) sells them." },
        { name: "Knife, chisel and hammer", text: "Needed for cutting logs, cutting bolt tips and putting crossbows together. A free hammer lies by the furnace at the south mine." }
      ],
      relics: [
        { name: "Lumberjack", tier: 1, text: "The Echo axe's <i>Toggle</i> option can fletch every log you chop into arrow shafts, with the real number of shafts and the Fletching XP, and no level needed." },
        { name: "Production Master", tier: 5, text: "Cutting logs, stringing bows and cutting bolt tips is done all at once, and ammunition is made 10 sets at a time." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Broad arrows, broad bolts and amethyst broad bolts still need the Broader Fletching Slayer unlock.",
        "Not in the league: the ogre composite bow and ogre arrows."
      ],
      osrs: "Fletching"
    },
    {
      id: "crafting", name: "Crafting", group: "production", tracker: ["Crafting"], page: true,
      summary: "Gems, leather, dragonhide, jewellery, battlestaves, pottery and glass.",
      notice: "Right now there is no spinning wheel, loom, potter's wheel, pottery oven or tanner anywhere you can reach, so spinning, weaving, pottery and tanning hides can't be done yet. Gem cutting, jewellery, glass, battlestaves and sewing leather you already have all work.",
      intro: [
        "Most Crafting is using one item on another: a chisel on an uncut gem, a needle on leather (with thread in your inventory), an orb on a battlestaff, a glassblowing pipe on molten glass. Pick what to make and how many from the menu.",
        "Jewellery is made at a furnace: use a gold or silver bar on it with the right mould (and gem) in your inventory, and choose from the jewellery screen. Strings for amulets come from wool."
      ],
      where: [
        "Item-on-item crafts work anywhere. For jewellery and molten glass, use the furnace at the south mine, {dir:furnace}."
      ],
      placeNames: [
        { area: "isle", box: [2205, 2775, 2235, 2800], name: "Furnace at the south mine", text: "For jewellery and molten glass." }
      ],
      gear: [
        { name: "Chisel, needle and thread", text: "A chisel for gems and snelms, a needle and thread for leather. One spool of thread lasts 5 items." },
        { name: "Moulds", text: "Ring, necklace, amulet, bracelet and tiara moulds for jewellery." }
      ],
      relics: [
        { name: "Power Miner", tier: 1, text: "The Echo pickaxe can cut the gems you find while mining, with Crafting XP and no level needed." },
        { name: "Production Master", tier: 5, text: "Every crafting job makes all it can in one go." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Not in the league: crystal singing outside the Gauntlet, golem crafting, the gold tiara, the quest-only silver items, cured yak-hide, the Crafting Guild, and hemp, cotton, linen and cloth (the game has no items for them)."
      ],
      osrs: "Crafting"
    },
    {
      id: "herblore", name: "Herblore", group: "production", tracker: ["Herblore"], page: true,
      summary: "Clean herbs and make potions, from attack potions to super combats.",
      intro: [
        "Clean a grimy herb by clicking it. Use a clean herb on a vial of water for an unfinished potion, then add the secondary ingredient to finish it. Potions come out with 3 doses.",
        "Some secondaries are ground first with a pestle and mortar. Higher-level potions are upgrades of others: divine potions, stamina, extended antifires and super combats."
      ],
      where: [
        "Anywhere: Herblore only needs the items in your inventory. Herbs drop from monsters, and the Friendly Forager relic finds them while you gather."
      ],
      gear: [
        { name: "Vials of water and a pestle and mortar", text: "Needed for unfinished potions and for grinding." }
      ],
      relics: [
        { name: "Friendly Forager", tier: 2, text: "Finds grimy herbs while you mine, chop, fish and hunt. Secondary ingredients have a 90% chance not to be used up, and potions come out with 4 doses instead of 3." },
        { name: "Production Master", tier: 5, text: "Cleans every herb of a kind at once, and makes every potion your ingredients allow in one go (not for potions with stackable secondaries)." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Some secondary ingredients have no place to get them on the isle yet."
      ],
      osrs: "Herblore"
    },
    {
      id: "runecraft", name: "Runecraft", group: "production", tracker: ["Runecraft"], page: true,
      summary: "14 altars, combination runes, pouches and the Abyss.",
      intro: [
        "Mine essence, then take it to an altar and click <i>Craft-rune</i>: all the essence you carry (and in your pouches) turns into runes at once. The higher your level, the more runes each essence gives (see the last column of <i>Runes</i>). The first six altars take rune or pure essence; the rest need pure essence (Mining 30).",
        "Every altar is reached through the <b>Abyss</b>. Talk to the Mage of Zamorak, just north of the lobby, and choose to teleport: you land beside the Dark Mage in the middle of the Abyss, with a rift to every altar around you. Each altar's exit portal brings you back to the Mage."
      ],
      where: [
        "<b>Essence:</b> Aubury, next to the Mage of Zamorak, teleports you to the rune essence mine (see Mining).",
        "<b>Altars:</b> all 14, through the Abyss. No talismans or tiaras are needed to get in."
      ],
      gear: [
        { name: "Essence pouches", text: "The Dark Mage in the middle of the Abyss gives you the pouches your level allows. Pouches never wear out. The colossal pouch is made from the other four." },
        { name: "Talismans and tiaras", text: "Talismans drop from monsters. Blank tiaras are made with Crafting (a silver bar and a tiara mould at a furnace); use one with a talisman on the altar to make that altar's tiara." },
        { name: "Mage of Zamorak's rune shop", text: "He also sells runes, for early Magic." },
        { name: "Binding necklace", text: "Makes combination runes always work instead of half the time." }
      ],
      relics: [
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "The Abyss is safe: no monsters, no obstacles, no prayer drain and no skull. The Mage drops you straight into the middle.",
        "All rifts are open from the start, including blood, astral and wrath, which the real Abyss doesn't have.",
        "Blood runes are made at the true Blood Altar with pure essence. Kourend's dark-essence blood altar, soul runes, sunfire runes and aether runes are not in the league.",
        "Not in the league: the Guardians of the Rift minigame and its gear, and the Ourania (ZMI) altar."
      ],
      osrs: "Runecraft"
    },
    {
      id: "firemaking", name: "Firemaking", group: "production", tracker: ["Firemaking"], page: true,
      summary: "Light fires anywhere and cook on them.",
      intro: [
        "Use a tinderbox on logs in your inventory to light a fire where you stand. You keep trying until it catches, then step to the side. The fire burns for about a minute and leaves ashes.",
        "You can cook on any fire, your own or someone else's."
      ],
      where: [
        "Anywhere you can stand, except on a tile that already has something on the ground. The logs come from Woodcutting."
      ],
      gear: [
        { name: "Tinderbox", text: "Needed to light every fire." }
      ],
      relics: [
        { name: "Lumberjack", tier: 1, text: "The Echo axe's <i>Toggle</i> option can burn every log as you chop it, with Firemaking XP and no level needed." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Not in the league: bonfires (adding logs to a fire), Forester's campfires, Wintertodt, pyre logs, lighting logs that lie on the ground, firelighters and coloured logs, and the Pyromancer outfit."
      ],
      osrs: "Firemaking"
    },
    {
      id: "construction", name: "Construction", group: "production", tracker: ["Construction"], page: false, soon: "Not in the league yet",
      summary: "Not in the league yet."
    },
    {
      id: "agility", name: "Agility", group: "utility", tracker: ["Agility"], page: true,
      summary: "The Brimhaven Agility Arena and marks of grace.",
      notice: "There is no way to earn marks of grace yet (in the main game they drop on rooftop courses, which aren't in the league), so Grace's graceful outfit can't be bought.",
      intro: [
        "Agility is trained in the <b>Brimhaven Agility Arena</b>: a grid of pillars joined by obstacles and traps. One ticket dispenser at a time has a flashing arrow over it; get to it and tag it for an arena ticket, a voucher and Agility XP (30 for every ten levels, up to 300). The arrow moves on every so often.",
        "Fail an obstacle and you take a little damage and drop into the pit below. Pirate Jackie inside the arena trades tickets for Agility XP, and vouchers for the pirate hook."
      ],
      where: [
        "The arena hut on the beach, {dir:arena_hut}. Pay Cap'n Izzy the entry fee and climb down the ladder."
      ],
      gear: [
        { name: "Grace's shop", text: "Grace, {dir:grace}, sells the graceful outfit for marks of grace, and amylase packs." }
      ],
      relics: [
        { name: "Corner Cutter", tier: 2, text: "Gives Sage's greaves: running in them gives Agility XP. Every arena obstacle and trap succeeds, with or without them." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "The only place to train is the arena. Rooftop and other courses, and agility shortcuts on the isle, are not in the league.",
        "Not in the league: the Karamja gloves' bonuses and the diary double tickets."
      ],
      osrs: "Brimhaven_Agility_Arena"
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
      id: "slayer", name: "Slayer", group: "utility", tracker: ["Slayer"], page: true,
      summary: "Turael, Vannaka and Duradel, the rewards shop and superiors.",
      intro: [
        "Get a task from a Slayer master, then kill that many of the monster for Slayer XP (each kill gives XP equal to the monster's hitpoints) and Slayer points when the task is done. Some monsters need a Slayer level to attack, and some need gear: a nose peg, earmuffs, a facemask, a mirror shield and so on.",
        "Spend points at any master's <i>Rewards</i> option: unlocks, task extensions, blocks and items. <i>Trade</i> opens the shared Slayer Equipment shop. Superior versions of monsters can appear while you're on task once you buy that unlock."
      ],
      where: [
        "<b>Turael</b> stands {dir:turael}. <b>Duradel</b> is at the top of the Crumbling Tower, {dir:duradel}. <b>Vannaka</b> is in the Varrock Sewers (see Dungeons).",
        "Some tasks have no monster you can reach yet; those rows are faded in the task table, and you can skip them at a master (it costs Slayer points)."
      ],
      relics: [
        { name: "Slayer Master", tier: 5, text: "Unlocks every Slayer reward, makes rune pouches, herb sacks and looting bags free in the rewards shop, and gives bonus Slayer XP the first time you kill the 100th of each kind of task." },
        { name: "Equilibrium", tier: 4, text: "Every XP drop gives extra XP equal to 10% of your total level, or 20% in your lowest skill. The extra is not multiplied." }
      ],
      differences: [
        "Tasks follow the Leagues combat-level window, and fall back to the full list when nothing fits.",
        "Revenants can also be assigned by Vannaka and Duradel.",
        "No Wilderness or boss tasks."
      ],
      osrs: "Slayer"
    }
  ]
};
