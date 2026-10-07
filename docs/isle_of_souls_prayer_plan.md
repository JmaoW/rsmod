# Isle of Souls: Prayer

Built 2026-10-06. Scope agreed first, then implemented. **Not yet tried in a real client.**

## What already existed
`content/interfaces/prayer-tab`: the tab, toggling, prayers that exclude each other, level and Defence
requirements, overhead icons, prayer drain (with the equipment prayer bonus), quick prayers, Preserve,
Rapid Heal and Rapid Restore. The attack, strength, defence, ranged and magic prayer boosts and the
protection prayers were already in the combat formulas.

## What was built

| Piece | Where |
|-------|-------|
| Bury bones (inventory op 1), bone table with wiki experience | `content/skills/prayer` (`Burying`, `PrayerBones`) |
| Altars: Pray-at restores prayer, using bones on an altar offers them | `content/skills/prayer` (`Altars`) |
| A normal altar and a chaos altar placed beside the bank | `isleofsouls/prayer/LeaguePrayerScript` |
| Every prayer unlocked on login | `isleofsouls/prayer/LeaguePrayerScript` |
| Redemption | `StandardPlayerHitProcessor` in `api/player` |
| "Prayer switched on" event | `PrayerEvents.Enabled` in `prayer-tab` |
| 14 league tasks | `LeagueSkillTasks`, `LeagueTaskList`, `IsleOfSoulsTaskEnums` |
| Test helper for using an item on a loc | `GameTestScope.opLocT` |

### Decisions (yours)
- A normal altar and a chaos altar stand near the bank. Both give **3.5 times** the bone's burying
  experience; the chaos altar also has a **50% chance not to use up the bone**.
- All prayers are unlocked from the start. Level and Defence requirements stay.
- Smite, Retribution and Protect Item stay switchable but do nothing (no PvP, and death does not drop items yet).
- The Chaos Temple tasks (969, 970) are included: offering at the chaos altar completes them.

### Details I chose (not from the wiki)
- **Bury:** the bone is used up and the experience given on the click, then the player digs for one
  more tick ("You dig a hole in the ground..." then "You bury the bones."), so a bone is buried
  every 2 ticks. The wiki has no figure for this.
- **Offering** continues while the player has the bone, one bone every **3 ticks**. A saved bone at the chaos altar shows the wiki message "The Dark Lord spares your sacrifice but still rewards you for your efforts."; every other offering says "The gods are pleased with your offering." It keeps going
  through op 4 of the altar, the same trick cooking uses, so walking away stops it.
- **Pray-at** kneels for 2 ticks and then restores prayer to the base level. At full prayer it says
  "You already have full Prayer points."
- **Altar positions:** standard altar at (2208, 2857) and chaos altar at (2212, 2857), both 2x1,
  facing south, in the open plaza just south of the bank chest at (2212, 2860). The first
  placement, on the strip north of the bank, was unreachable in the client: the bank's crates and
  barrels cut that strip off. These tiles come from a flood fill over the real collision flags
  starting at the bank chest, so the ground and the tile in front of each altar are reachable. If
  either faces the wrong way or sits badly, change `ALTAR`, `CHAOS_ALTAR` or `ALTAR_ANGLE` in
  `LeaguePrayerScript`.
- **Unlocks:** each prayer's own config names an unlock varbit and the value it needs. Login sets
  them: Rigour, Augury and Preserve (1 each) and Knight Waves state 8 (Chivalry, Piety). Deadeye and
  Mystic Vigour are not unlocks but scroll upgrades of Eagle Eye and Mystic Might, so they are
  left off.
- **Redemption:** when a hit leaves you alive under 10% of your hitpoints, you heal a quarter of
  your Prayer level and lose all your prayer points. It does not trigger on a fatal hit.

### Bone table (wiki, burying experience)
Bones, burnt bones, wolf bones 4.5; bat 5.3; big 15; zogre 22.5; babydragon 30; wyrm 50; dragon and
wyvern 72; drake 80; Fayrg 84; lava dragon 85; Raurg 96; hydra 110; dagannoth 125; Ourg 140; superior
dragon 150. Baby wyrm bones are left out (the wiki table does not list them).

### League tasks added (14, 1,360 points)

| Task | Trigger |
|------|---------|
| 94 Restore 5 Prayer Points at an Altar (easy) | Pray-at restoring 5 or more points at once |
| 95 Superhuman Strength and Improved Reflexes (easy) | both switched on together |
| 97 Bury Some Bones (easy) | any bone buried |
| 188 Bury Some Wyvern or Dragon Bones (medium) | wyvern or dragon bones buried |
| 387 Use the Protect from Melee Prayer (medium) | switched on |
| 969 Sacrifice Some Bones at the Chaos Temple (medium) | any bone offered at the chaos altar (saved or not) |
| 65 Restore 75 Prayer Points at an Altar (hard) | Pray-at restoring 75 or more at once |
| 726 Use the Chivalry Prayer (hard) | switched on |
| 970 Sacrifice Some Dragon Bones at the Chaos Temple (hard) | dragon bones offered at the chaos altar |
| 727 Use the Piety Prayer (elite) | switched on |
| 234 / 235 / 1464 / 1487 Level 99 and 25M / 35M / 50M XP (elite) | checked after burying, offering and login |

The experience milestones only run when prayer experience comes from burying or offering, or at
login. **When combat gives prayer experience, call `checkMilestones(player, stats.prayer)` from
that hook too** (same note as the other skills' milestones).

### Left out on purpose
Prayer potions (herblore), scattering ashes, bonecrusher and ensouled heads, Ectofuntus, gilded
altars and incense burners, house altars, enriched bones, prayer scrolls, the lectern, the prayer
bonus tasks (134, 66: nothing on the isle gives a prayer bonus yet), the place-bound tasks (Lumbridge,
Hosidius, Prifddinas, lava dragon bones in the Wilderness) and Jad-without-prayer (belongs to the
Caves). Also not done: the bury and altar sounds (no named synths), and the real game's "praying at
an altar resets the drain timer". The Golden God coin offering and the tier 5 prayer-gain passive
can use `Altars.BoneOffered` and `Altars.PrayerRestored` later.

## Tests
`content/skills/prayer` (13: bone table, burying, altars), `LeaguePrayerTasksTest` and
`LeaguePrayerScriptTest` in the isle module (12), and four Redemption tests in
`StandardPlayerHitProcessorTest`. The whole isle suite (291 tests) and `api/player` pass.

## Check in the real client
- [ ] Both altars appear beside the bank, face the right way, and can be reached
- [ ] Pray-at restores prayer and shows the message; "Check" or other options do nothing odd
- [ ] Using bones on each altar offers them one at a time; the chaos altar sometimes keeps the bone
- [ ] "Bury" works from the inventory and the player does not get stuck
- [ ] The prayer tab shows Chivalry, Piety, Rigour, Augury and Preserve as usable (no lock icons)
- [ ] Redemption triggers once at low hitpoints (you see the effect)
