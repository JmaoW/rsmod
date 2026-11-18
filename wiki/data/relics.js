// Isle of Souls Relics page: the hand-written half.
//
// The other half (point thresholds, XP and drop multipliers, relic icons, task point totals) is
// generated from the game code and event settings into relics-generated.js by
// tools/wiki/gen_relics.py. Re-run that after changing a threshold or multiplier.
//
//   intro     paragraphs at the top of the page
//   passives  extra passive lines per tier ("1" to "8"), on top of the generated XP and drop lines
//   relics    by the relic's name in the game code (RelicData.kt):
//     name      shown name, when it differs from the game code's (optional)
//     tracker   the content tracker row it takes its build and test state from (data/content.js)
//     summary   one line, shown under the name
//     soon      tag for a relic that isn't in the league yet; its effects are what is planned
//     item      {name, text}: the item the relic gives
//     effects   what it does
//     use       how to use its item: options, switches
//     notes     limits and things worth knowing
//     helps     "all" to say it helps every skill. Otherwise the skill links come from the skill
//               pages that list this relic (data/skills.js), plus any skill ids given here.
//
// Text may use <b>, <i> and <a> tags.

window.ISLE_RELICS = {
  updated: "2026-11-04",
  intro: [
    "Relics are the league's big unlocks. There are eight tiers, and you pick <b>one relic from each tier</b>. A pick is permanent: you can't swap it later.",
    "Tasks earn League Points, and your team shares one points total. Each player picks their own relics, though, so you and your teammate can take different ones and cover more ground. A tier opens once your team has the points for it and you have picked a relic from the tier before it.",
    "Choosing a relic from a tier also turns on that tier's <b>passive</b> for you (higher XP rates, better drops and so on, listed with each tier below).",
    "Relics that give an item only work while the item is in your inventory or worn, unless the relic says otherwise. Lose the item and it comes back the next time you log in (it goes to your bank if your inventory is full)."
  ],
  screenNote: "The Relics screen in game still shows the original Raging Echoes text. Where it and this page disagree, this page is what the relic does here.",
  passives: {
    "1": ["Run energy never drains while you run."],
    "3": [
      "Slayer reward points from tasks are 5x as many.",
      "The <i>Bigger and Badder</i> Slayer unlock is free.",
      "Superior Slayer monsters turn up 1 time in 50 instead of 1 in 200."
    ]
  },
  relics: {
    // ------------------------------------------------------------------ tier 1
    "Power Miner": {
      tracker: "Power Miner (tier 1)",
      summary: "Mine faster and straight to the bank, with bars and cut gems on the way.",
      item: { name: "Echo pickaxe", text: "A pickaxe that needs no Mining level and never degrades." },
      effects: [
        "A failed mining roll gets a second, 50% chance to succeed.",
        "Ore goes straight to your bank.",
        "A rock gives at least 4 ores before it can run out.",
        "<b>Auto-smelt</b> (off at first): ore is banked as bars instead, with the Smithing XP and no level needed. Copper and tin give bronze bars, iron gives iron bars, coal gives steel bars, and mithril, adamantite and runite give their own bars with no coal needed.",
        "<b>Auto-gem cut</b> (off at first): gems you find while mining are cut, with the Crafting XP and no level needed."
      ],
      use: ["<i>Toggle</i> on the pickaxe (held or worn) switches auto-smelt, auto-gem cut and auto-bank on and off."]
    },
    "Lumberjack": {
      tracker: "Lumberjack (tier 1)",
      summary: "Chop faster and straight to the bank, or burn or fletch the logs as you go.",
      item: { name: "Echo axe", text: "An axe that needs no Woodcutting level and never degrades." },
      effects: [
        "A failed chop gets a second, 50% chance to succeed.",
        "Logs go straight to your bank.",
        "<b>Auto-burn</b> (off at first): logs are burnt as you cut them, for the Firemaking XP with no level needed.",
        "<b>Auto-fletch</b> (off at first): logs are made into arrow shafts, for the Fletching XP with no level needed. Better logs make more shafts: 15 from normal logs up to 105 from redwood. Logs that make no shafts, such as teak, are left alone."
      ],
      use: ["<i>Toggle</i> on the axe (held or worn) switches auto-burn, auto-fletch and auto-bank. Only one of auto-burn and auto-fletch can be on."]
    },
    "Animal Wrangler": {
      tracker: "Animal Wrangler (tier 1)",
      summary: "Fishing with cooking built in, and Hunter traps that never fail.",
      item: { name: "Echo harpoon", text: "Works as every fishing tool (net, big net, lobster pot, rod, karambwan vessel and harpoon), needs no bait, and fishes a tick faster." },
      effects: [
        "A failed catch gets a second, 50% chance to succeed.",
        "Fish go straight to your bank (except karambwanji, which you keep as bait).",
        "<b>Auto-cook</b> (on at first): fish are cooked as you catch them, for the Cooking XP with no level needed.",
        "You never burn food, wherever you cook it.",
        "Hunter, with or without the harpoon: traps never fail, box traps look for a catch every 2 ticks instead of 5, a chinchompa catch gives twice the chinchompas and twice the XP, and impling jars never break when opened."
      ],
      use: ["<i>Toggle</i> on the harpoon (held or worn) switches auto-cook and auto-bank."]
    },

    // ------------------------------------------------------------------ tier 2
    "Corner Cutter": {
      tracker: "Corner Cutter (tier 2)",
      summary: "Agility XP just for running, and obstacles that never fail.",
      item: { name: "Sage's greaves", text: "Boots that give Agility XP while you run in them." },
      effects: [
        "While you wear the greaves, running gives Agility XP: 10 plus two-thirds of your Agility level about every 10 ticks, before the league multiplier.",
        "Every Agility obstacle and trap succeeds, with or without the greaves.",
        "A finished lap of a course counts as two and gives 25% more XP, and a mark of grace comes with 10,000 coins."
      ],
      notes: ["The only Agility training in the league is the Brimhaven Agility Arena, which has no laps or marks of grace, so the last line doesn't come up yet."],
      helps: ["agility"]
    },
    "Friendly Forager": {
      tracker: "Friendly Forager (tier 2)",
      summary: "Herbs from every gathering skill, and cheaper, bigger potions.",
      item: { name: "Forager's pouch", text: "Carry it for the herb finding." },
      effects: [
        "Every ore, log, fish and Hunter catch (traps, pits, butterflies, implings, falconry and birdhouses) also finds a random grimy herb, up to what your Herblore level + 25 can clean. You get a little Herblore XP for it, and the herb goes to your bank.",
        "Herblore: secondary ingredients have a 90% chance not to be used up, and potions come out with 4 doses instead of 3."
      ],
      notes: ["There is no herb sack, so herbs go to the bank. A herb is lost if the bank is full."]
    },
    "Dodgy Deals": {
      tracker: "Dodgy Deals (tier 2)",
      summary: "Pickpocket a whole crowd at once and never get caught.",
      effects: [
        "Pickpocketing someone also robs everyone of the same kind in an 11 by 11 square around them, for extra loot (not extra XP).",
        "Every Thieving check succeeds: you never fail a pickpocket or a stall.",
        "Pickpocketing and stealing repeat by themselves until something stops you (a full inventory, too many coin pouches, or clicking elsewhere).",
        "Pickpocketed items come noted.",
        "You can carry three times as many coin pouches.",
        "Stalls never empty."
      ]
    },

    // ------------------------------------------------------------------ tier 3
    "Clue Compass": {
      tracker: "Clue Compass (tier 3)",
      soon: "Not in the league yet",
      summary: "Planned: teleport to boss entrances and other named places on the isle.",
      effects: ["Planned: an item that teleports you to boss entrances and other named places on the isle. The list isn't decided yet."]
    },
    "Bank Heist": {
      tracker: "Bank Heist (tier 3)",
      soon: "Not in the league yet",
      summary: "Planned: teleport to the banks on the isle.",
      effects: ["Planned: an item that teleports you to the banks, bank chests and deposit boxes on the isle."]
    },
    "Fairy's Flight": {
      tracker: "Fairy's Flight (tier 3)",
      soon: "Not in the league yet",
      summary: "Planned: teleport to named places on the isle.",
      effects: ["Planned: an item that teleports you to named places around the isle. The list isn't decided yet."]
    },

    // ------------------------------------------------------------------ tier 4
    "Golden God": {
      tracker: "Golden God (tier 4)",
      summary: "Free, richer alchemy, coins for Prayer XP and noted shop buys.",
      effects: [
        "High and Low Alchemy need no runes and no Magic level, give 15% more coins, and have a 65% chance not to use up the item.",
        "Alchemy keeps casting on its own down the whole stack. You can walk and fight while it does; it stops when the stack runs out, you move the item, or you cast something else.",
        "Use coins on a Prayer altar to offer them: every 20,000 coins gives the XP of offering one dragon bone on that altar (the chaos altar's saving still applies). Fewer than 20,000 coins does nothing.",
        "Items bought from the rune shop, Yarsul's pickaxe shop and Percy's nugget shop come noted when they can be."
      ],
      use: ["The shop screen gets a <i>Noted items</i> button that switches noted buying off and on."]
    },
    "Reloaded": {
      tracker: "Reloaded (tier 4)",
      summary: "Pick one more relic from tiers 1 to 3.",
      effects: [
        "Lets you pick one extra relic from tier 1, 2 or 3, on top of the one you already have from that tier. It works just like a normal pick, item and all.",
        "Only the relics count: you don't get a tier's passive twice."
      ],
      use: ["Open the Relics screen, click the extra relic you want and confirm. You only get one extra pick, and it's permanent."]
    },
    "Equilibrium": {
      tracker: "Equilibrium (tier 4)",
      summary: "Bonus XP on every drop, more in your lowest skill.",
      effects: [
        "Every XP drop gives extra XP equal to 10% of your total level, or 20% if it's your lowest skill (by XP; a tie counts).",
        "The extra isn't multiplied by the league or server XP rates, so it is small next to your normal XP, but it adds up on fast actions."
      ],
      helps: "all"
    },

    // ------------------------------------------------------------------ tier 5
    "Treasure Arbiter": {
      tracker: "Treasure Arbiter (tier 5)",
      soon: "Not in the league yet",
      summary: "Planned: clues drop more often and caskets always roll the most rewards.",
      effects: ["Planned: clue scrolls drop more often, every clue has the fewest steps, and caskets always roll the most rewards. Needs clue scrolls in the league first."]
    },
    "Production Master": {
      tracker: "Production Master (tier 5)",
      summary: "Every production job finishes in one go.",
      effects: [
        "A whole batch is made in one action instead of one item at a time: smelting, smithing (as many as your bars allow), fletching (ammo up to 10 sets per action), cleaning herbs, potions (unless the secondary stacks), cooking on a range or fire, pies, pizzas and wine, and every Crafting job (leather, gems, glass, jewellery, pottery, battlestaves and spinning)."
      ]
    },
    "Slayer Master": {
      tracker: "Slayer Master (tier 5)",
      summary: "Always on task, every Slayer perk, and a big bonus on each 100th kill.",
      effects: [
        "You are always on task for any monster a Slayer master could give you: you get its Slayer XP, the Slayer helmet's bonus and a chance at its superior without having the task.",
        "Every Slayer unlock is yours for free.",
        "Cancelling and blocking tasks is free, and so is everything in the Slayer reward store.",
        "The 100th kill of each kind of Slayer monster gives bonus Slayer XP: 1,000 for the lowest-level monsters up to 15,000 for level 99 ones (before multipliers)."
      ]
    },

    // ------------------------------------------------------------------ tier 6
    "Total Recall": {
      tracker: "Total Recall (tier 6)",
      summary: "Save a spot and come back to it, stats and all.",
      item: { name: "Crystal of echoes", text: "Remembers one spot along with your Hitpoints, Prayer and special attack energy." },
      effects: [
        "<i>Save-memory</i> saves where you stand and your Hitpoints, Prayer and special attack energy.",
        "<i>Teleport-back</i> takes you back there and sets those three back to what was saved. The memory stays, so you can use it again and again, and it lasts through death.",
        "<i>Check-memories</i> tells you what is saved."
      ],
      notes: ["You can't save a memory inside an instance, such as a boss lair or a raid."]
    },
    "Banker's Note": {
      tracker: "Banker's Note (tier 6)",
      summary: "Note and un-note items anywhere.",
      item: { name: "Banker's note", text: "Use an item on it to note or un-note it." },
      effects: [
        "Use any item that can be noted on the banker's note to note it, or a noted item to un-note it.",
        "<i>Note-Quantity</i> and <i>Un-note-Quantity</i> set how many go at once (0 means all of them, or as many as fit).",
        "<i>Activate</i> repeats it on the last item you used, and <i>Toggle</i> switches Activate between noting and un-noting."
      ],
      notes: ["The note forgets the last item when you log out."]
    },

    // ------------------------------------------------------------------ tier 7
    "Pocket Kingdom": {
      name: "Pocket Workshop",
      tracker: "Pocket Workshop (tier 7)",
      soon: "Not in the league yet",
      summary: "Planned: a portable furnace, anvil, range and loom.",
      effects: ["Planned: an item that works as a furnace, anvil, range and loom wherever you are. It replaces Raging Echoes' Pocket Kingdom, which needs content the isle doesn't have. (The Relics screen still says Pocket Kingdom.)"]
    },
    "Grimoire": {
      tracker: "Grimoire (tier 7)",
      summary: "Switch spellbooks any time, for free.",
      item: { name: "Arcane grimoire", text: "Swaps your spellbook, held or worn in the shield slot." },
      effects: [
        "<i>Change Spellbook</i> switches to the standard, Ancient, Lunar or Arceuus spellbook for free.",
        "<i>Previous Spellbook</i> switches back to the one you had before, and <i>Check Previous</i> tells you which that is.",
        "It is the only way to leave the standard spellbook in the league."
      ],
      helps: ["magic"]
    },
    "Overgrown": {
      tracker: "Overgrown (tier 7)",
      soon: "Not in the league yet",
      summary: "Planned: Farming boosts.",
      effects: ["Planned: Farming boosts. Needs Farming in the league first."]
    },

    // ------------------------------------------------------------------ tier 8
    "Specialist": {
      tracker: "Specialist (tier 8)",
      summary: "Cheap, accurate special attacks that pay energy back.",
      effects: [
        "Every special attack costs 20% energy, whatever it normally costs.",
        "Special attacks are twice as accurate.",
        "A special attack that misses gives back 10% energy.",
        "Every kill restores 15% energy, however you made it."
      ]
    },
    "Guardian": {
      tracker: "Guardian (tier 8)",
      summary: "A guardian that fights beside you with your target's weakest style.",
      item: { name: "Guardian horn", text: "Summons the guardian for 30 minutes." },
      effects: [
        "The guardian follows you and attacks whatever you are fighting every 4 ticks, hitting 5 to 13.",
        "It always uses the style your target is weakest to (melee, ranged or magic).",
        "With ranged or magic it also hits the monsters right next to your target (the whole isle is multi-combat).",
        "Its kills and drops are yours. It never attacks players, works alongside an Arceuus thrall, and leaves when you die or log out."
      ],
      use: ["<i>Summon</i> and <i>Dismiss</i> on the horn call and send away the guardian. <i>Toggle-modes</i> switches the area attack off and on, and <i>Change-outfit</i> changes how it looks."],
      notes: ["The real guardian's look can't be shown in this game version, so it appears as a Kourend royal guard or a white knight."]
    },
    "Last Stand": {
      tracker: "Last Stand (tier 8)",
      summary: "Survive a killing blow, hit back hard and heal from it.",
      effects: [
        "A hit that would kill you leaves you on 1 Hitpoint instead.",
        "Attack, Strength, Defence, Ranged and Magic jump to 255, then drain 16 a tick down to 15 above your base level.",
        "For the next 16 ticks nothing can take you below 1 Hitpoint.",
        "When those 16 ticks end, you heal by the damage you dealt during them (up to your Hitpoints level).",
        "It can't happen again for 3 minutes, unless you die first."
      ],
      notes: ["Logging out resets the 3-minute wait."]
    }
  }
};
