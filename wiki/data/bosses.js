// Isle of Souls boss pages: the hand-written half.
//
// The other half (stats, drop tables with league rates, collection log, where each entrance is,
// waves, league tasks) is generated from the game code into bosses-generated.js by
// tools/wiki/gen_bosses.py. Re-run that after changing a boss, its drops or its entrance.
//
// Each page has:
//   id          page address: boss.html?b=<id>, and the key into bosses-generated.js "pages"
//   name        shown name
//   group       "bosses", "raids" or "planned"
//   tracker     the content tracker rows it takes its build and test state from (data/content.js)
//   built       false for a boss that is not in the league yet (tagged "Not in the league yet")
//   summary     one line for the Bosses page
//   notice      optional warning shown at the top of the page
//   pvp         the event-settings pvp key of the area, so the page says when PvP is on there
//   intro       paragraphs: what it is here
//   getThere    paragraphs: how to get in. {dir:<key>} is replaced by where that landmark is.
//   team        lines: how a team shares it (instance, loot, kill count, respawn)
//   fights      [{id, name, text: [paragraphs], attacks: [{name, text}]}]: one per boss on the page,
//               id = the key into bosses-generated.js "fights"
//   loot        paragraphs on how loot is handed out, above the drop tables
//   echo        {name, tracker, text}: the planned echo version
//   differences lines on what is different from the main game or left out
//   osrs        the OSRS Wiki page
//
// Text may use <b>, <i> and <a> tags. {secs:N} becomes how long N ticks of respawn time take at the
// league's respawn speed ("about 9 seconds"). {zulrahScales}, {toaPaths}, {toaTeamUniques}, {tobCap}, {coxCap} and {nodes}
// come from the event settings.

window.ISLE_BOSSES = {
  updated: "2026-11-25",
  intro: [
    "Bosses are fought as a team: most put your team in a private copy of the lair, so you never wait for another team. Kills count for the whole team's tasks.",
    "Drop rates on these pages are the main game's. Drops marked <b>boosted</b> get the league's relic drop boost on top, shown in their own columns."
  ],
  groups: [
    { id: "bosses", name: "Bosses" },
    { id: "raids", name: "Raids and minigames" },
    { id: "planned", name: "Not in the league yet" }
  ],
  pages: [
    // ------------------------------------------------------------------ bosses
    {
      id: "zulrah", name: "Zulrah", group: "bosses", tracker: ["Zulrah"],
      summary: "The serpent of the shrine: four rotations, three forms, venom, clouds and snakelings.",
      intro: [
        "Zulrah is one snake fighting in three forms. She dives, comes up somewhere else on the shrine in the next form, and works through one of four fixed rotations. All four rotations, the venom clouds, the snakelings and the tail swings are in."
      ],
      getThere: [
        "Board the sacrificial boat, {dir:zulrah_boat}. It takes your team to its own copy of the shrine. Read the Zul-andra teleport scroll beside where you land to leave."
      ],
      team: [
        "Your team gets its own shrine. Zulrah picks one random player per phase and shoots only them.",
        "She comes back in the same shrine for whoever is still inside, with a new rotation. Every kill counts.",
        "Drops land on the tile of the player who killed her (she is in the water)."
      ],
      fights: [
        {
          id: "zulrah", name: "Zulrah",
          text: ["All forms share one pool of 500 hitpoints. She dies at 0 whatever form she is in, and nothing caps the damage you deal."],
          attacks: [
            { name: "Green (serpentine)", text: "Ranged shots, up to 41. Pray Protect from Missiles. Weak to magic." },
            { name: "Blue (tanzanite)", text: "Magic or ranged shots, up to 41. Weak to ranged." },
            { name: "Red (magma)", text: "Two tail swings at the tile you stand on: she turns to face it, and the tail lands about 3 seconds later. Move two tiles away. A hit does 20-30, stuns and throws you back. Prayer does not help." },
            { name: "Venom", text: "One damaging shot in four envenoms you: 6 damage, then 2 more each time, up to 20. There is no antivenom on the isle: an antipoison's protection or a charged serpentine helm stops it, and leaving the shrine cures it." },
            { name: "Venom clouds", text: "3x3 clouds on the platform that hurt 1-4 every tick you stand in them. The first phase of every rotation fills the shrine and leaves the two tips clear." },
            { name: "Snakelings", text: "Orbs that hatch into snakelings (melee or magic, 1 hitpoint). They die after about 40 seconds or with Zulrah." },
            { name: "Jad phase", text: "A green phase on the east or west that alternates ranged and magic, 8-10 attacks. Switch prayer every shot." }
          ]
        }
      ],
      loot: [
        "Zulrah's scales come <b>{zulrahScales}x</b> as fast in this league.",
        "Use a chisel on a tanzanite fang, magic fang or serpentine visage to dismantle it for 20,000 scales."
      ],
      differences: [
        "Melee is not limited to halberds (she is in the water anyway).",
        "The cloud, orb and surfacing tiles come from another server's data, not the main game. They may be a little off.",
        "No antivenom, antidote+ or serpentine helm crafting on the isle.",
        "No quest, deathbank, Konar keys or Combat Achievements."
      ],
      osrs: "Zulrah"
    },
    {
      id: "vorkath", name: "Vorkath", group: "bosses", tracker: ["Vorkath"],
      summary: "The undead dragon of Ungael: acid, the zombified spawn, fireballs and four kinds of dragonfire.",
      intro: [
        "The full Vorkath fight in a crater of your own. He never moves: it is all about the two specials he takes turns with, and dragonfire."
      ],
      getThere: [
        "Use Torfinn's boat, {dir:vorkath_boat}, just east of the Dagannoth Kings' ladder. Climb over the ice chunks at the south of the crater to leave.",
        "Vorkath sleeps in the middle of the crater: <i>Poke</i> him to start. He rises over about 4 seconds and attacks soon after."
      ],
      team: [
        "Your team gets its own crater. He fights whoever poked him, or the nearest player once they are gone.",
        "After a kill he lies down asleep again in the same spot, ready for the next poke. Nobody is sent out.",
        "Drops fall just outside his body, on the side nearest the player who gets them."
      ],
      fights: [
        {
          id: "vorkath", name: "Vorkath",
          text: ["An attack every 5 ticks. After every 6 normal attacks (5 for the first) he uses a special, acid and spawn in turn."],
          attacks: [
            { name: "Melee", text: "Up to 32, only when you are next to him." },
            { name: "Ranged and magic", text: "Up to 32 and 30. Pray Protect from Missiles or Magic." },
            { name: "Dragonfire", text: "Fiery, venomous (always envenoms) and corrupting (turns every prayer off). Up to 80 unprotected. An anti-dragon shield with a super antifire potion takes it to 0." },
            { name: "Fireball", text: "Up to 121 on the tile he aimed at, half next to it. Nothing protects you: a shadow marks the tile, so move." },
            { name: "Acid special", text: "Acid pools fill the crater, then 25 quick shots at the tile you stood on, one a tick (25-41 if you're still there). Keep walking between pools. Acid heals him, and he takes half damage until it ends." },
            { name: "Spawn special", text: "An icy breath freezes you and a zombified spawn lands about 8 tiles away and walks at you. If it reaches you it explodes. Kill it (Crumble Undead kills it in one cast). He takes no damage while it lives." }
          ]
        }
      ],
      loot: [
        "<b>Vorkath's head</b> is guaranteed on your 10th kill and every 50th after that (50, 100, 150...), as well as the normal 1 in 50.",
        "Use the head on Ava's accumulator with 75 mithril arrows in your pack to make <b>Ava's assembler</b>. The <b>dragonbone necklace</b> restores 1-5 Prayer for each bone you bury."
      ],
      differences: [
        "In a team, only the target is frozen or shot at by the acid special; the others can keep attacking (but he is immune during the spawn and halved during acid for everyone).",
        "The fireball's shadow is an isle addition (the main game has none).",
        "The assembler is made from the head on the accumulator, not by Ava. Its upgrades, the jar of decay, Vorki's uses and the turquoise slayer helmet are not in."
      ],
      osrs: "Vorkath"
    },
    {
      id: "kalphite_queen", name: "Kalphite Queen", group: "bosses", tracker: ["Kalphite Queen"],
      summary: "Two forms with the molt between, workers hatching from the cocoons, spines and bouncing magic.",
      intro: [
        "The queen of the Kalphite Lair, in a chamber of your own. She fights in two forms: kill the crawling form, she molts, and you fight the airborne form at full health."
      ],
      getThere: [
        "Go into the Kalphite Lair (entrance {dir:kalphite}) and down to its lowest level. The tunnel there always has a rope: <i>Climb-down</i> takes your team into its chamber. The rope in the chamber takes you back up."
      ],
      team: [
        "Your team gets its own chamber. No health scaling for a second player.",
        "She comes back {secs:50} after a kill for whoever is still inside.",
        "The player who did the most damage gets the drops, rolled at her middle tile."
      ],
      fights: [
        {
          id: "kalphite_queen", name: "Kalphite Queen",
          text: [
            "She waits until someone comes within 12 tiles or hurts her. She attacks every 4 ticks, up to 31.",
            "The <b>crawling form</b> resists magic and ranged; the <b>airborne form</b> resists melee. Drained Defence carries over the molt. During the molt (20 ticks) she can't be attacked.",
            "After 20 minutes in the airborne form she goes back to crawling with the health she has left."
          ],
          attacks: [
            { name: "Melee", text: "Half her attacks next to you. Protect from Melee." },
            { name: "Spines (ranged)", text: "Hit the target and everyone within a tile of them. A spine that rolls damage takes a Prayer point even through Protect from Missiles." },
            { name: "Magic", text: "Bounces from the target to the nearest player within 2 tiles, and on. Protect from Magic." },
            { name: "Workers", text: "Each hit on her (even a 0) has a 1 in 20 chance to hatch a Kalphite worker from a cocoon. Up to 12 at once; each lasts 100 ticks." }
          ]
        }
      ],
      echo: {
        name: "Echo Kalphite Queen", tracker: "Kalphite Queen (echo)",
        text: "A harder queen, reached with the echo orb the normal queen drops. Drops the drygore blowpipe and also rolls the normal queen's table."
      },
      differences: [
        "No rope needed: the tunnel always shows <i>Climb-down</i>.",
        "Kalphite soldiers and guardians in the lair poison you (one landed bite in four).",
        "Not in: the tattered head on the 256th kill, Konar keys, the legs that fall off in the molt, the hive-wall tunnel."
      ],
      osrs: "Kalphite Queen"
    },
    {
      id: "king_black_dragon", name: "King Black Dragon", group: "bosses", tracker: ["King Black Dragon"],
      pvp: "revenantCaves",
      summary: "The three-headed dragon in his lair: dragonfire and three special breaths.",
      intro: [
        "The King Black Dragon in a lair of your own. Bring an anti-dragon shield or antifire: he breathes from a distance."
      ],
      getThere: [
        "Go into the Revenant Caves (entrance {dir:revenant}). A copy of the King Black Dragon's lever hangs on a wall a few tiles from the cave's exit to the surface. <i>Pull</i> or <i>Private</i> takes your team to its lair, for free. The lever on the lair's south wall takes you back."
      ],
      team: [
        "Your team gets its own lair. He appears about 6 seconds after you arrive and comes straight at you.",
        "The player who did the most damage gets the drops. The next dragon appears {secs:16} after a kill for whoever is still in the lair.",
        "Kills count for the whole team."
      ],
      fights: [
        {
          id: "king_black_dragon", name: "King Black Dragon",
          text: ["Up close one attack in two is a breath, the rest bites. From a distance it is always a breath."],
          attacks: [
            { name: "Bite", text: "Melee. Protect from Melee." },
            { name: "Fire breath", text: "Half his breaths, up to 65 unprotected. A shield, antifire or Protect from Magic cuts it down." },
            { name: "Poison breath", text: "Up to 50, and one time in two that it hurts, it poisons you (starting at 8)." },
            { name: "Ice breath", text: "Up to 50, and one time in two that it hurts, it freezes you for 10 ticks." },
            { name: "Shock breath", text: "Up to 50, and one time in two that it hurts, every stat but Hitpoints drops by 2. It stacks." }
          ]
        }
      ],
      echo: {
        name: "Echo King Black Dragon", tracker: "King Black Dragon (echo)",
        text: "A harder dragon, reached with the echo orb the normal one drops. Drops the thunder khopesh and thousand-dragon ward, and also rolls the normal table."
      },
      differences: [
        "Every lair is private and free (no 50,000 coin fee).",
        "The odds of a special breath's extra effect (1 in 2) are a guess: the wiki only says \"a chance\"."
      ],
      osrs: "King Black Dragon"
    },
    {
      id: "scurrius", name: "Scurrius", group: "bosses", tracker: ["Scurrius"],
      summary: "The rat king of the Varrock Sewers: three phases, falling bricks and rats.",
      intro: [
        "Scurrius in a lair of your own, with the private room's numbers (500 hitpoints)."
      ],
      getThere: [
        "Go into the Varrock Sewers (entrance {dir:varrock}) and east to the broken bars. <i>Climb-through</i> takes your team into its lair. The sewage water on the far side is the way out."
      ],
      team: [
        "Your team gets its own lair. He comes back {secs:30} after a kill.",
        "The player who did the most damage rolls his main table. Everyone else in the lair rolls the second table (the wiki's non-MVP table).",
        "The food piles round the room heal you to full, once every 10 minutes, outside combat."
      ],
      fights: [
        {
          id: "scurrius", name: "Scurrius",
          text: ["He attacks every 4 ticks, then every 3 in the last phase."],
          attacks: [
            { name: "Phase 1 (above 80%)", text: "He walks up and swipes with his tail (up to 13, Protect from Melee). Now and then he brings bricks down." },
            { name: "Phase 2 (80% to 30%)", text: "He walks to a food pile and eats ten bites, five of which heal him (5 for each player in the room). While eating he uses only magic (up to 8), ranged (up to 7), bricks and rats, and takes 10% less damage. He eats once per fight." },
            { name: "Phase 3 (below 30%)", text: "He roars, goes to the middle and stays there: magic, ranged, bricks (often two or three in a row) and rats." },
            { name: "Falling bricks", text: "Shadows mark your tile and 14-19 others. About 3 seconds later anyone on one takes 15-22. Prayer doesn't help: move." },
            { name: "Giant rats", text: "Six at a time. Any hit kills one; they bite for up to 4 (up to 1 through Protect from Melee). The bone mace, bow and staff attack every tick against them." }
          ]
        }
      ],
      differences: [
        "No public room, no Peek counts, no Combat Achievements.",
        "How much he heals when he eats, and every timing, are guesses from another server."
      ],
      osrs: "Scurrius"
    },
    {
      id: "giants", name: "Obor and Bryophyta", group: "bosses", tracker: ["Obor and Bryophyta"],
      summary: "The hill giant and moss giant bosses, behind their gates, with their key chests.",
      intro: [
        "Obor (the hill giant boss) and Bryophyta (the moss giant boss), each in a lair of your own. Kill the boss, then open the chest with a key for its loot."
      ],
      getThere: [
        "<b>Obor:</b> the gate in the Edgeville Dungeon (entrance {dir:edgeville}). It's locked until a <b>giant key</b> has opened it once; after that it opens without one. You arrive north of the fence: <i>Climb</i> the rocks into the pit.",
        "<b>Bryophyta:</b> the gate in the Varrock Sewers (entrance {dir:varrock}), opened the same way with a <b>mossy key</b>. It drops your team straight into her lair, and she attacks at once.",
        "Hill giants drop giant keys and moss giants drop mossy keys (both boosted by the league's drop relics). The bosses drop them too, 1 in 16.",
        "To leave: Obor's gate halves or Bryophyta's rock pile (<i>Quick-exit</i> skips the question)."
      ],
      team: [
        "One lair per team for each giant. Only players in Obor's pit are fought, and only they can hurt him.",
        "After a kill, the first click on the chest brings the boss back {secs:15} later. With a key it also spends the key and spills the chest's loot beside it: one key per kill.",
        "Giant bones always drop: bury them where they fall (150 Prayer XP)."
      ],
      fights: [
        {
          id: "obor", name: "Obor",
          attacks: [
            { name: "Punch", text: "Up close, up to 22. One punch in four knocks you back up to 5 tiles." },
            { name: "Boulder", text: "From range, up to 26. Protect from Missiles halves it." },
            { name: "Stamp", text: "Rocks fall along the line to you. Step off it." }
          ]
        },
        {
          id: "bryophyta", name: "Bryophyta",
          attacks: [
            { name: "Punch", text: "Up close, up to 16. One that hits in four poisons you (8)." },
            { name: "Earth blast", text: "Magic, up to 10, from anywhere within 8 tiles. She never moves." },
            { name: "Growthlings", text: "Once she's lost a quarter of her health, she may call three growthlings. She takes no damage while one lives. Only an axe or magic secateurs, equipped, can kill them (one per hit). The logs in her lair give you a bronze axe." }
          ]
        }
      ],
      loot: [
        "Use Bryophyta's essence on a battlestaff (62 Crafting) to make <b>Bryophyta's staff</b>, and charge it with nature runes."
      ],
      differences: [
        "Bryophyta's staff never saves a nature rune (the main game saves 1 in 15).",
        "Her mossy key is 1 in 16 on a moss giant task too (the main game gives 1 in 8).",
        "No entry fade, no Combat Achievements."
      ],
      osrs: "Obor"
    },
    {
      id: "dagannoth_kings", name: "Dagannoth Kings", group: "bosses", tracker: ["Dagannoth Kings"],
      summary: "Rex, Prime and Supreme in their lair, each weak to a different style, with the spinolyps.",
      intro: [
        "The three Dagannoth Kings in a copy of their lair for your team. Each king is weak to one style and fights with another, so the usual way is to take them one at a time."
      ],
      getThere: [
        "Climb down the Kings' ladder, {dir:dk_ladder}, west of Vorkath's boat. <i>Standard</i> and <i>Slayer</i> both go to your team's lair (the kings count for a Dagannoth task either way). <i>Peek</i> says how many of your team are in. The ladder up beside where you land takes you out."
      ],
      team: [
        "Your team gets its own lair. A king that has a target keeps them anywhere in the lair until they leave or die.",
        "Each king comes back {secs:150} after it dies.",
        "Drops fall at the middle of the king, for whoever did the most damage."
      ],
      fights: [
        {
          id: "dagannoth_rex", name: "Dagannoth Rex",
          attacks: [{ name: "Melee (slash)", text: "Up to 26, every 4 ticks. Protect from Melee. Weak to magic: he has very high ranged Defence." }]
        },
        {
          id: "dagannoth_prime", name: "Dagannoth Prime",
          attacks: [{ name: "Magic", text: "Up to 50, every 4 ticks, and it also hits everyone within a tile of his target. Protect from Magic. Weak to ranged." }]
        },
        {
          id: "dagannoth_supreme", name: "Dagannoth Supreme",
          attacks: [{ name: "Ranged", text: "Up to 30, every 4 ticks, and it also hits everyone within 3 tiles of his target. Protect from Missiles. Weak to melee." }]
        },
        {
          id: "spinolyp", name: "Spinolyps",
          text: ["25 of them round the water's edge. They never move, and dive now and then."],
          attacks: [{ name: "Spine or magic", text: "Every 4 ticks at the nearest player within 10 tiles, up to 10. The magic hit drains a Prayer point; the spine can poison." }]
        }
      ],
      differences: [
        "Every lair is private; there is no separate Slayer-only lair.",
        "The Waterbirth Island Dungeon itself is not on the isle, only the kings' lair.",
        "Not in: the echo kings (amulet and ring of kings), the spectral shield halving the spinolyp drain."
      ],
      echo: {
        name: "Echo Dagannoth Kings", tracker: "Dagannoth Kings (echo)",
        text: "Harder kings, reached with the kingly orb. Drop the amulet and ring of kings."
      },
      osrs: "Dagannoth Kings"
    },
    {
      id: "wilderness", name: "Wilderness bosses", group: "bosses", tracker: ["Wilderness bosses"],
      pvp: "wildernessBossLairs",
      summary: "Callisto, Venenatis and Vet'ion, and their smaller versions Artio, Spindel and Calvar'ion.",
      intro: [
        "The six Wilderness bosses, each in its real lair. These lairs are <b>shared</b>: every team fights the same boss, and the loot goes by damage dealt.",
        "Artio, Spindel and Calvar'ion are the weaker versions of Callisto, Venenatis and Vet'ion. Their kills count for the bigger boss's tasks."
      ],
      getThere: [
        "Go into the Revenant Caves (entrance {dir:revenant}). Each lair has its door in the caves (see the list below for where). <i>Enter</i> takes you to the lair; <i>Peek</i> says how many are inside. There is no fee.",
        "Leaving Callisto's, Venenatis's or Vet'ion's lair puts you in the Escape Caves, full of bears and spiders that chase you. Its exits all lead back to the Revenant Caves. The three smaller lairs open straight back into the Revenant Caves."
      ],
      team: [
        "Everyone in a lair is a target. A boss left alone for 50 ticks heals and walks home.",
        "The top ten damage dealers each roll the drop table, with ordinary drops scaled by their share of the damage. Each kill gives one unique roll, handed to one of the ten by share. Pets are rolled for each player at their share.",
        "A kill counts once per team, for the teammate who did the most damage. Each boss comes back {secs:11} after it dies (Vet'ion {secs:15})."
      ],
      fights: [
        {
          id: "callisto", name: "Callisto",
          text: ["1,000 hitpoints. He walks up to his target. Melee, then 6 ticks; ranged 5; the orb 8."],
          attacks: [
            { name: "Melee", text: "35-55, never misses, and hits everyone within a tile of the target. Protect from Melee takes off half." },
            { name: "Crescent (ranged)", text: "Up to 31 at everyone out of reach. Protect from Missiles stops it." },
            { name: "Wind orb", text: "Every 8-12 attacks. Without Protect from Magic you're thrown 3 tiles away: 5 damage, plus 15 for each tile a wall stopped you short." },
            { name: "Roars and traps", text: "At 66% and 33% he roars (breaking a freeze). From the first roar, five bear traps go down every 6 ticks: stepping on one holds you and hits up to 15." }
          ]
        },
        {
          id: "artio", name: "Artio",
          text: ["450 hitpoints. Callisto's fight with smaller numbers: melee 15-35, crescent up to 17."]
        },
        {
          id: "venenatis", name: "Venenatis",
          text: ["850 hitpoints. She never walks to a target: next to her you get the lunge, everyone else 8 ranged then 8 magic attacks. Every 4 attacks she moves."],
          attacks: [
            { name: "Lunge, ranged, magic", text: "Up to 21, 35 and 30." },
            { name: "Spiderlings", text: "Two on her first ranged attack, up to six. Each bites for up to 3 and drains a Prayer point. Each one alive raises her max hit by 10% and cuts your protection prayer by 20%: kill them." },
            { name: "Sticky web", text: "A 7x5 web for 12 ticks: 3 damage a tick, 3 Prayer and 10% run energy." }
          ]
        },
        {
          id: "spindel", name: "Spindel",
          text: ["515 hitpoints. Venenatis's fight with smaller numbers: up to 14, 31 and 24."]
        },
        {
          id: "vetion", name: "Vet'ion",
          text: ["255 hitpoints in each form. He walks up to his target and stands still while each attack lands."],
          attacks: [
            { name: "Lightning", text: "Glowing circles mark five tiles; a moment later the strike hits the 3x3 round each: up to 44 in the middle, half round it. Prayer doesn't help: move." },
            { name: "Shield bash", text: "Next to him, shadows mark two lines in front of you for 4 ticks; then the bash hits up to 35 and stuns for 8 ticks." },
            { name: "Hellhounds", text: "At half health he calls two skeleton hellhounds and can't be hurt while they live." },
            { name: "Second form", text: "When the first form falls he rises with full health, enraged: twelve strikes in a circle, harder hits, greater hellhounds. Five minutes without a kill and he goes back." }
          ]
        },
        {
          id: "calvarion", name: "Calvar'ion",
          text: ["150 hitpoints in each form. Vet'ion's fight with smaller numbers: lightning up to 26, bash up to 26."]
        }
      ],
      loot: [
        "Use the Voidwaker hilt, blade and gem on each other to make the <b>Voidwaker</b>."
      ],
      differences: [
        "No fee, no kill-count gate, no teleport lock, no Wilderness diary or Slayer requirements.",
        "The lair doors are in the Revenant Caves rather than on the Wilderness surface.",
        "The Escape Caves don't drain your prayer.",
        "How often some attacks come (the orb, the bash), trap and web lengths and stun lengths are guesses."
      ],
      osrs: "Callisto"
    },
    {
      id: "god_wars", name: "God Wars Dungeon", group: "bosses", tracker: ["God Wars Dungeon"],
      summary: "Kree'arra, General Graardor, Commander Zilyana and K'ril Tsutsaroth, with their bodyguards.",
      intro: [
        "The four generals of the God Wars Dungeon, each with three bodyguards, behind the doors of their strongholds. The followers in the dungeon attack you unless you wear an item of their god."
      ],
      getThere: [
        "The God Wars Dungeon entrance is {dir:godwars}. Climb down the rope into the dungeon.",
        "<b>Getting to the doors.</b> Saradomin's side needs 70 Agility (not boosted) to tie and climb the ropes. Armadyl's is across the pillar, Bandos's past the gong, and Zamorak's across the river (you swim; swimming north drains your prayer).",
        "<b>Kill count.</b> A door opens once you have killed 40 followers of that god. It's a one-off: entering doesn't spend them. Or use an <b>ecumenical key</b> instead (it is used up). Any follower drops one 1 in 60, and you can carry up to three.",
        "Each door has <i>Open (normal)</i> for the shared room, <i>Open (private)</i> for your team's own room, and <i>Peek</i>. The altar's <i>Teleport</i> takes you out."
      ],
      team: [
        "Private rooms are one per team. The general and bodyguards come back {secs:150} after they die, while anyone is inside.",
        "When a room empties, everyone in it heals to full and their stats reset.",
        "The <b>altars</b> restore your Prayer, plus one point over your maximum for each item of that god you wear, once every 10 minutes and not in combat."
      ],
      fights: [
        {
          id: "kreearra", name: "Kree'arra",
          text: ["Can't be hit with melee (nor her bodyguards and the Armadyl followers), except with a halberd or salamander. She attacks every 3 ticks."],
          attacks: [
            { name: "Tornadoes", text: "When someone is attacking her: ranged up to 69 (Protect from Missiles) or magic up to 21 (Protect from Magic) at everyone in the chamber, each pushed a tile away." },
            { name: "Melee", text: "When nobody attacks her she flies to her target and swipes, up to 26 (Protect from Melee)." },
            { name: "Bodyguards", text: "Flight Kilisa melee 15, Wingman Skree magic 16, Flockleader Geerin ranged 25, every 5 ticks." }
          ]
        },
        {
          id: "graardor", name: "General Graardor",
          text: ["Every 6 ticks, from next to his target."],
          attacks: [
            { name: "Punch", text: "Two attacks in three: crush up to 60. Protect from Melee." },
            { name: "Ground slam", text: "One in three: 15-35 to everyone in the chamber (ranged, Protect from Missiles)." },
            { name: "Sergeants", text: "Strongstack melee 15, Steelwill magic 15, Grimspike ranged 21. They switch to whoever last hurt Graardor." }
          ]
        },
        {
          id: "zilyana", name: "Commander Zilyana",
          text: ["Fast: an attack every 2 ticks, from next to her target."],
          attacks: [
            { name: "Melee", text: "Up to 27. Protect from Melee." },
            { name: "Magic", text: "10-20 when it lands. Protect from Magic." },
            { name: "Bodyguards", text: "Starlight melee 15, Growler magic 16, Bree ranged 16, every 5 ticks." }
          ]
        },
        {
          id: "kril", name: "K'ril Tsutsaroth",
          text: ["Every 6 ticks, from next to his target."],
          attacks: [
            { name: "Swipe", text: "Two in three: slash up to 46 (Protect from Melee). One that lands can poison you, even through the prayer." },
            { name: "Magic", text: "One in three: 10-30. Protect from Magic." },
            { name: "Prayer smash", text: "Against someone praying Protect from Melee, one swipe in nine: he shouts, always hits 35-49 through the prayer and drains half your Prayer points (a quarter with a spectral spirit shield)." },
            { name: "Bodyguards", text: "Tstanon Karlak melee 15, Zakl'n Gritch ranged 21, Balfrug Kreeyath magic 16. When K'ril dies they each go for a random player." }
          ]
        }
      ],
      differences: [
        "Nex and the Ancient Prison are not in.",
        "The generals and bodyguards ignore god items: they always attack.",
        "The crossings don't need their items (hammer, grapple, rope) for the event.",
        "Private rooms are free (no 150,000 coin fee), and Peek counts only the shared room."
      ],
      osrs: "God Wars Dungeon"
    },

    // ------------------------------------------------------------------ raids and minigames
    {
      id: "fight_caves", name: "Fight Caves", group: "raids", tracker: ["Fight Caves"],
      summary: "Waves of TzHaar monsters ending with TzTok-Jad, for the fire cape.",
      intro: [
        "The TzHaar Fight Cave, for your team. Clear the waves and kill TzTok-Jad for a fire cape each."
      ],
      getThere: [
        "The cave entrance is in Mor Ul Rek. Go into Karamja Dungeon (entrance {dir:karamja}) and through the cave at its east end into Mor Ul Rek. <i>Enter</i> the Fight Cave there.",
        "Teammates who enter while a run is going join it. A run ends when nobody is left inside (death, leaving, teleport or logout), and the next entry starts fresh. If you die you can walk back in and rejoin a run that's still going."
      ],
      team: [
        "Your team gets its own cave. Monsters go for the nearest player. No extra waves or health for a second player.",
        "Everyone <b>inside the cave</b> when Jad dies gets the rewards, and you're all sent out shortly after."
      ],
      fights: [
        {
          id: "jad", name: "TzTok-Jad",
          text: ["Jad attacks <b>every player in the cave</b> at once, each with their own roll."],
          attacks: [
            { name: "Magic and ranged", text: "Up to 97. Watch the animation and switch prayer: prayer is checked when the hit lands, so the right prayer blocks all of it." },
            { name: "Melee", text: "When you stand next to him." },
            { name: "Healers", text: "Below 150 hitpoints, once, Yt-HurKot healers appear and heal him 5 every 4 ticks while near him. They only fight if attacked." }
          ]
        }
      ],
      loot: [
        "Each player inside gets a <b>fire cape</b>, <b>8,032 Tokkul</b> and a <b>1 in 200</b> chance of the TzRek-Jad pet."
      ],
      differences: [
        "The event chooses between the full 63 waves and a short version; players can't pick.",
        "Monster spawn points are moved so every monster can walk to you (the engine has no pathfinding).",
        "Tz-Kih drains 1 Prayer point a hit; Yt-MejKot heals itself or a monster below half health. The fire cape can't be traded in yet."
      ],
      osrs: "TzHaar Fight Cave"
    },
    {
      id: "inferno", name: "Inferno", group: "raids", tracker: ["Inferno"],
      notice: "The Inferno can't be entered at the moment. It opens for good once you give up a fire cape, but the TzHaar who took them was removed on 2026-10-10 and nothing has taken his place yet.",
      summary: "The Inferno's waves, the pillars, triple Jad and TzKal-Zuk, for the infernal cape.",
      intro: [
        "The Inferno, for your team: waves of TzHaar monsters, the three pillars, the Jad waves and TzKal-Zuk behind the Ancestral Glyph."
      ],
      getThere: [
        "In Mor Ul Rek, through Karamja Dungeon (entrance {dir:karamja}) and the cave at its east end. The entrance only lets you in once you've sacrificed a fire cape (one cape, once)."
      ],
      team: [
        "Your team gets its own arena. Monsters go for the nearest player; Jad and Zuk hit everyone.",
        "Everyone inside when Zuk dies gets the rewards. A run counts once per team for the \"Complete N times\" tasks."
      ],
      fights: [
        {
          id: "zuk", name: "TzKal-Zuk",
          text: ["The last wave puts you on the north edge of the arena with Zuk in the wall and the Ancestral Glyph moving along the lava in front of you."],
          attacks: [
            { name: "Zuk's shot", text: "Every 10 ticks (7 when enraged) at every player, up to 148. No prayer stops it: stand behind the glyph." },
            { name: "The glyph", text: "600 hitpoints. While it stands, it covers anyone close behind it. Once it breaks, nothing does." },
            { name: "Sets", text: "A Jal-Xil and a Jal-Zek arrive behind you every 3.5 minutes and attack the glyph until a player hits them. Draw them off." },
            { name: "Jad", text: "At 480 hitpoints Zuk's own JalTok-Jad arrives, with three healers." },
            { name: "Enrage", text: "Below 240 Zuk attacks faster and four Jal-MejJak appear: they heal him, and once hit they throw lava instead." }
          ]
        }
      ],
      loot: [
        "Each player inside gets an <b>infernal cape</b>, <b>16,440 Tokkul</b> and a <b>1 in 100</b> chance of Jal-Nib-Rek."
      ],
      differences: [
        "The event chooses between the full 69 waves and a short version.",
        "No rock outcrops (blind spots) on Zuk's platform: they aren't in the map data.",
        "Zuk's damage can be tick-eaten. Healers you've hit don't go back to healing."
      ],
      osrs: "The Inferno"
    },
    {
      id: "toa", name: "Tombs of Amascut", group: "raids", tracker: ["Tombs of Amascut"],
      pvp: "soulWarsArena",
      summary: "The full raid for your party: four paths, the Wardens, invocations and loot points.",
      intro: [
        "The Tombs of Amascut in a copy of its own for your party: all four paths (Het, Apmeken, Crondis, Scabaras) with their bosses, the Wardens, the invocations and raid level, supplies and the vault."
      ],
      getThere: [
        "The pyramid door stands in the Soul Wars arena, east of the Gauntlet's portal. Reach the arena through the blue or red portal in the lobby's waiting areas. The door takes you to the raid lobby; its exit brings you back.",
        "Form a party at the obelisk (up to 8 players, not only your league team), set the invocations, and go in together."
      ],
      team: [
        "A raid must clear <b>{toaPaths}</b> of the 4 paths before the Wardens open.",
        "Loot points are per player: everyone starts on 5,000, can hold up to 64,000, and loses 20% of their own points on a death.",
        "A failed raid keeps your items. Everything the raid gave you is taken away when you leave."
      ],
      fights: [
        {
          id: "toa", name: "The raid",
          attacks: [
            { name: "Path of Het", text: "The beam puzzle, then Akkha and his shadows." },
            { name: "Path of Apmeken", text: "Eight baboon waves with Apmeken's Sight, then Ba-Ba with her boulders and rockfalls." },
            { name: "Path of Crondis", text: "Water the palm past crocodiles and traps, then Zebak with his waves, jugs and blood clouds." },
            { name: "Path of Scabaras", text: "The four puzzle chambers, then Kephri with her eggs, swarms and overlords." },
            { name: "The Wardens", text: "The obelisk, then the charged Warden and its core, then the final room's floor slams, siphons and phantoms." }
          ]
        }
      ],
      loot: [
        "The chance of a purple (unique) depends on the raid level and the party's loot points (up to 55%). The league's drop relics multiply it on top: each player's boost counts by their share of the points. The pet is not boosted.",
        "One unique roll and one pet roll per raid, on the whole party's points, handed to a player by their share. When the raid gives a purple, everyone else still in the raid also gets a unique of their own in place of their common loot (from raid level <b>{toaTeamUniques}</b> up).",
        "Bosses hand out their book once per player, and their trophy to the top damage dealer."
      ],
      differences: [
        "Raids below level 150 (Entry mode) don't count for the completion tasks, as in the main game.",
        "Team uniques are an isle rule (the Raging Echoes league gave them from level 500).",
        "Some numbers are guesses (see the build notes). Still being polished after playtests."
      ],
      osrs: "Tombs of Amascut"
    },
    {
      id: "tob", name: "Theatre of Blood", group: "raids", tracker: ["Theatre of Blood"],
      summary: "Normal mode for parties of up to {tobCap}: the six fights, the supply chests and the vault.",
      intro: [
        "The Theatre of Blood (Normal mode) in a copy of its own for your party: the Maiden of Sugadinti, the Pestilent Bloat, the Nylocas, Sotetseg, Xarpus and Verzik Vitur, then the vault. Every run is all six rooms."
      ],
      getThere: [
        "A portal in the Soul Wars lobby takes you to the real Ver Sinhaza, with its bank. The portal beside the notice board brings you back.",
        "Make or join a party at the notice board (up to <b>{tobCap}</b> players, not only your league team), then go in through the theatre's entrance together."
      ],
      team: [
        "Bosses scale with the party as in the main game up to 5. Past 5 they get 12.5% more hitpoints a player.",
        "The board and the health bars show the first five players; players six to eight are listed in chat.",
        "A player who dies waits in the room's jail until the room is cleared. If everyone dies, the party is put outside. Your items are kept (the isle's rule), so the retrieval chest is always empty.",
        "There are supply chests after the Bloat and after Sotetseg, paid for with points earned from damage."
      ],
      fights: [
        {
          id: "tob", name: "The six rooms",
          attacks: [
            { name: "The Maiden of Sugadinti", text: "Blood throws and splats, and Matomenos that walk to her and heal her if they reach her." },
            { name: "The Pestilent Bloat", text: "Walks its loop dropping flies and hands from the ceiling. Hit it while it sleeps." },
            { name: "The Nylocas", text: "Waves crawl out of the tunnels at the pillars; kill each colour with the matching style. Then Nylocas Vasilias, who changes style." },
            { name: "Sotetseg", text: "Red and blue balls, the big ball shared by the party, and the maze: one player runs it in the Shadow Realm while the rest follow the glowing path." },
            { name: "Xarpus", text: "Exhumed pools to heal it first, then spit and poison pools, then the stare: don't hit it in the quadrant it faces." },
            { name: "Verzik Vitur", text: "Talk to her to start. Phase 1 behind the pillars (use the Dawnbringer), phase 2's flight, bounces and Nylocas, phase 3's webs, yellows, tornadoes and green ball." }
          ]
        }
      ],
      loot: [
        "One roll for a purple (unique) per raid, at the wiki's rate (1 in 9.1 with no deaths). The league's drop relics multiply it on top, each player's boost counting by their share of the party's points. Lil' Zik is not boosted.",
        "The finder gets the unique. When team uniques are on (they are by default), everyone else still in the raid gets a unique of their own in place of their common loot.",
        "Each player's loot waits in their own monumental chest in the vault (purple if it holds a unique). Leave before opening it and it goes to the Rewards Chest by Ver Sinhaza's bank."
      ],
      differences: [
        "Normal mode only. Parties of up to {tobCap} (the main game stops at 5).",
        "The ornament kits are rolled in the vault at Hard Mode rates, so their tasks can be done.",
        "The scythe and staff are charged straight from the inventory, not through the Vyre well.",
        "Logging out or teleporting leaves the raid. No spectating, scoreboard or MVP.",
        "Some numbers are guesses (see the build notes). Still being polished after playtests."
      ],
      osrs: "Theatre of Blood"
    },
    {
      id: "cox", name: "Chambers of Xeric", group: "raids", tracker: ["Chambers of Xeric"],
      summary: "Normal and Challenge Mode, random layouts, the full prep and the Great Olm.",
      intro: [
        "The Chambers of Xeric on the real Mount Quidamortem, in a copy of its own for your party. Normal raids get a random layout from the main game's patterns: two floors of combat rooms, puzzles, scavengers and resource rooms, then the Great Olm. Challenge Mode is the fixed three-floor layout with every room."
      ],
      getThere: [
        "A portal in the Soul Wars lobby, beside the Theatre of Blood's, takes you to Mount Quidamortem; the portal there brings you back. Xeric's talisman's <i>Xeric's Honour</i> lands there too, once you've read the ancient tablet from the raid.",
        "The way down the mountain is fenced off for the event.",
        "Make a party at the recruiting board (up to <b>{coxCap}</b> players, not only your league team) and go in through the entrance. The leader starts the raid from the side panel."
      ],
      team: [
        "Monsters scale with the party's size and levels, as in the main game, for any size up to the cap.",
        "Gather and make your own supplies inside: fish and bats, herbs, the raid's potions (overloads, Xeric's aid, revitalisation, prayer enhance), kindling, and storage units built from mallignum planks.",
        "Points come from damage, cooking, potions, storage units, shortcuts and thieving. A death costs 40% of your points. Your items are kept (the isle's rule).",
        "A player who dies wakes at the start of the floor they reached."
      ],
      fights: [
        {
          id: "olm", name: "The Great Olm",
          text: ["At least four phases (one more for every 8 players). Kill both claws each phase, then the head on the last."],
          attacks: [
            { name: "Phase specials", text: "Crystal bursts, lightning and teleport swaps, in the main game's order." },
            { name: "Head attacks", text: "Ranged and magic, with a 20% chance to switch style after each attack. Spheres take half your hitpoints unless prayed against." },
            { name: "Acid, fire and crystals", text: "Acid spray and drip, deep burn, the fire wall, falling crystals and crystal bombs." },
            { name: "Left claw", text: "Heals, shocks, stuns or brings the roof down." }
          ]
        }
      ],
      loot: [
        "The chance of a unique is the wiki's: 1% for every 8,676 points the party earns (65.7% at 570,000; more points roll more). The league's drop relics multiply it on top, with each player's boost counting by their share of the points. The olmlet is not boosted.",
        "When team uniques are on (they are by default), everyone else in the raid also gets a unique of their own in place of their commons.",
        "Loot waits in the ancient chest at the end. Leaving with loot still in it sends it to your bank.",
        "Challenge Mode within the target time adds the twisted ancestral kit (1 in 75) and metamorphic dust (1 in 400). Captain Rimor hands out capes for Challenge Mode completions."
      ],
      differences: [
        "Parties are made on the board only (there is no friends chat), so the channel's rank rules are left out.",
        "Logging out leaves the raid. Some rooms open their way on when done instead of with an exit crystal.",
        "The crab puzzle is simplified. Xeric's talisman only does Xeric's Honour. The Kodai wand's rune saving isn't built.",
        "Some numbers are guesses (see the build notes). Still being polished after playtests."
      ],
      osrs: "Chambers of Xeric"
    },
    {
      id: "gauntlet", name: "The Gauntlet", group: "raids", tracker: ["The Gauntlet"],
      pvp: "soulWarsArena",
      summary: "Normal and Corrupted, as a duo: gather, craft crystal gear, then fight the Hunllef.",
      intro: [
        "The Gauntlet and the Corrupted Gauntlet, for your team in one shared maze. Gather resources, craft crystal weapons and armour and cook food, then fight the Hunllef together."
      ],
      getThere: [
        "The portal stands in the Soul Wars arena, west of the Tombs of Amascut door. Reach the arena through the blue or red portal in the lobby's waiting areas. The portal takes you to the Gauntlet lobby; the platform there brings you back.",
        "Talk to Bryn first. You go in with an <b>empty pack and nothing worn</b>: the run hands out a starting kit and takes it back on the way out. Corrupted opens after one normal completion.",
        "A crystal singing bowl beside the portal on the isle turns the seeds from the chest into crystal armour, tools and weapons."
      ],
      team: [
        "Both teammates enter together into one maze and fight one Hunllef. Nodes are shared, packs are not. A teammate can still join while you're preparing.",
        "The Hunllef switches target every attack between the players still alive.",
        "A player who dies is out of the run (they lose the kit and get the \"anything else\" reward). The run ends when both are out or the Hunllef dies.",
        "Points, rewards and stats are per player. Completions count once per team for team tasks."
      ],
      fights: [
        {
          id: "hunllef", name: "The Hunllef",
          text: ["600 hitpoints (Corrupted: 1,000). Its attack style changes every four attacks, and it protects against one style at a time."],
          attacks: [
            { name: "Ranged and magic", text: "It changes style every four attacks (across both players). Your protection prayer and armour tier decide how hard it hits you." },
            { name: "Its prayer", text: "It switches protection after every sixth hit you land that isn't of the style it's protecting." },
            { name: "Stomp", text: "Standing under it." },
            { name: "Tornadoes and floor", text: "Tornadoes chase you for 20 ticks; floor tiles light up and then hurt. Both get worse as its health falls." }
          ]
        }
      ],
      loot: [
        "A Hunllef kill gives crystal shards and two rolls on the reward chest (three in Corrupted, plus the cape the first time). Anything else gives one item from the smaller table, by points.",
        "Tertiary rolls: elite clue, crystal weapon seed, crystal armour seed, enhanced crystal weapon seed and the Youngllef pet."
      ],
      echo: {
        name: "Echo Corrupted Hunllef", tracker: "Corrupted Hunllef (echo)",
        text: "A harder Hunllef that skips the gathering part. Drops the crystal blessing."
      },
      differences: [
        "A duo run in one maze (the main game is solo).",
        "Resource nodes {nodes} for the event.",
        "No Song of the Elves check. Only a small set of crystal singing recipes (no charging or degrading)."
      ],
      osrs: "The Gauntlet"
    },
    {
      id: "colosseum", name: "Fortis Colosseum", group: "raids", tracker: ["Fortis Colosseum"],
      summary: "Twelve waves with modifiers, then Sol Heredit, as a duo.",
      intro: [
        "The Fortis Colosseum for your team: twelve waves from the wiki with all 14 modifiers, ending with Sol Heredit. Loot builds up in a chest each wave."
      ],
      getThere: [
        "A copy of the city's arch, {dir:colosseum_arch}, takes you to the real lobby under the Colosseum; its stairs bring you back. The ring of dueling's <i>Fortis Colosseum</i> goes there too.",
        "Speak to Minimus once to open the tunnel. The tunnel puts your team in its own copy of the arena."
      ],
      team: [
        "Before each wave, the first player to confirm the intermission screen picks the team's modifier.",
        "Each monster goes for the nearest player. Damage, Doom, Blasphemy and the \"no damage\" Glory are per player.",
        "A player who dies wakes in the lobby with their chest frozen, and walks back in at the next intermission. If everyone fighting dies, the run is lost.",
        "Between waves, leaving is a forfeit: your chest comes out for you and your partner can carry on. Teleporting or logging out loses the loot."
      ],
      fights: [
        {
          id: "sol", name: "Sol Heredit",
          text: ["Wave 12. He greets each player who hasn't beaten him, jumps down, and the barricade goes up."],
          attacks: [
            { name: "Spear and shield", text: "His spear and shield patterns, as in the main game." },
            { name: "Triple attack", text: "Three hits in a row; pray early." },
            { name: "Grapple", text: "He calls a gear slot: click it. On the last tick it is a perfect parry." },
            { name: "Phases", text: "Sand and crystals fill the arena as he weakens, then he enrages." }
          ]
        }
      ],
      loot: [
        "Each cleared wave adds its loot to each player's chest. Beating Sol gives each player wave 12's loot with Dizana's quiver, and a 1 in 200 chance of Smol Heredit.",
        "Splinters charge Dizana's quiver; the Shrine of Ralos in the lobby blesses it for 150,000 splinters. An echo crystal on guardian boots makes echo boots."
      ],
      echo: {
        name: "Echo Sol Heredit", tracker: "Sol Heredit (echo)",
        text: "Skips straight to the last wave. Drops the sunlight spear and sunlit bracers."
      },
      differences: [
        "A duo run (the main game is solo); the duo rules above are the isle's own.",
        "No Glory gate on the bank chest or the ring of dueling for the event.",
        "Some numbers are guesses and may need tuning."
      ],
      osrs: "Fortis Colosseum"
    },

    // ------------------------------------------------------------------ planned
    {
      id: "cerberus", name: "Cerberus", group: "planned", built: false, tracker: ["Cerberus"],
      summary: "Planned. Needed first for the Cerberus echo boss.",
      intro: ["Cerberus, the hellhound of Taverley Dungeon's Slayer cave, is planned but not built yet."],
      echo: { name: "Echo Cerberus", tracker: "Cerberus (echo)", text: "Drops the echo godsword. Needs the normal Cerberus first." },
      osrs: "Cerberus"
    },
    {
      id: "grotesque_guardians", name: "Grotesque Guardians", group: "planned", built: false, tracker: ["Grotesque Guardians"],
      summary: "Planned. Needed first for the Grotesque Guardians echo boss.",
      intro: ["Dusk and Dawn on the roof of the Slayer Tower are planned but not built yet."],
      echo: { name: "Echo Grotesque Guardians", tracker: "Grotesque Guardians (echo)", text: "Drops the gloves of the damned. Needs the normal Guardians first." },
      osrs: "Grotesque Guardians"
    },
    {
      id: "thermonuclear_smoke_devil", name: "Thermonuclear smoke devil", group: "planned", built: false, tracker: ["Thermonuclear smoke devil"],
      summary: "Planned. Needed first for its echo boss.",
      intro: ["The boss of the Smoke Devil Dungeon is planned but not built yet. Its dungeon is already open (entrance {dir:smoke})."],
      echo: { name: "Echo Thermonuclear smoke devil", tracker: "Thermonuclear smoke devil (echo)", text: "Drops the devil's element. Needs the normal boss first." },
      osrs: "Thermonuclear smoke devil"
    },
    {
      id: "hespori", name: "Hespori", group: "planned", built: false, tracker: ["Hespori"],
      summary: "Planned. Needs Farming first.",
      intro: ["Hespori grows from a Farming patch, so it waits for Farming to be built."],
      echo: { name: "Echo Hespori", tracker: "Hespori (echo)", text: "Drops nature's reprisal (the tangled lizard). Needs Farming and Hespori first." },
      osrs: "Hespori"
    }
  ]
};
