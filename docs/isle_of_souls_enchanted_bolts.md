# Enchanted bolts and player poison (2026-10-22)

Built together with a fix that makes poison from another player behave like poison from an npc.

## What was built

- **Bolt effects** for all ten gems, on regular and dragon bolts, fired from any crossbow. Module `content/other/bolt-effects`:
  `BoltGem` (the chance table and the name parsing), `EnchantedBolts` (the effects), `EnchantedBoltsScript` (registers it).
- **Hook** in `api/combat/combat-manager`: `BoltEffectRegistry` holds a `BoltEffectHandler`. `PlayerAttackManager.rollRangedDamage`
  asks it for the shot's effect (so every crossbow path gets it: normal attacks, Armadyl Eye, Evoke and weapon scripts that roll
  through the manager) and `queueRangedHit` runs the rest of the effect (graphic, poison, drains, healing, holding) once the hit is queued.
- **Enchant Crossbow Bolt**: ten spells in `content/skills/magic/utility-spells` (`BoltEnchanting`, `BoltEnchantments`). A cast turns up
  to 10 bolts of its gem, regular ones first and then dragon bolts. Level, xp and the shared button are set in `SpellObjEditor`
  (`api/spells/.../SpellObjs.kt`) because the cache only holds their runes.
- **Player poison**: `SpecialDots.poison` on a player publishes `PlayerPoisonEvents.Apply`, which `SicknessScript` answers with
  `PlayerPoison`. So a player poisoned by a poisoned weapon, an emerald bolt or a Smoke spell gets the orb, antipoison protection, antipoison
  cure, the 30-tick hits and the log-out save, exactly as from an npc. Smoke spells (Ancients) can now poison players too.

## The effects (OSRS Wiki, "Bolt effects")

Chances are PvM / PvP in percent. "Bypass" means the effect can work on a shot that would have missed (and then it hits).

| Gem | Chance | Bypass | Effect |
|---|---|---|---|
| Opal | 5 / 5 | yes | Max hit + Ranged level / 10 |
| Jade | 6 / 6 | yes | Holds the target 8 ticks (npcs: unless freeze-immune; players: not through Protect from Magic) |
| Pearl | 6 / 6 | yes | Max hit + Ranged / 20, or / 15 against fiery targets; none against a player with a water staff |
| Red topaz | - / 4 | yes | Lowers a player's Magic by 1 (players only) |
| Sapphire | 25 / 5 | no | Player: drains Prayer by Ranged / 20, shooter gets half. Npc: shooter gets (npc Ranged / 10) / 8 Prayer |
| Emerald | 55 / 54 | no | Poison starting at 5 |
| Ruby | 6 / 11 | yes | 20% of the target's hitpoints (at most 100) for 10% of the shooter's; needs 10 hitpoints |
| Diamond | 10 / 5 | yes | Never misses, max hit x1.15 |
| Dragonstone | 6 / 6 | no | Max hit + Ranged x 20%; none against fiery targets or a dragonfire shield or antifire |
| Onyx | 11 / 10 | no | Max hit x1.2, shooter heals a quarter of the damage; none against undead |

- **Zaryte crossbow**: every effect 10% stronger (Opal /9, Pearl /13 and /18, Emerald 6, Ruby 22% / 110, Diamond x1.26, Dragonstone 22%,
  Onyx x1.32; jade, topaz and sapphire do not change, as on the wiki).
- **Armadyl Eye** doubles the chance of the effect; **Evoke** makes it work if the shot hits.
- No Kandarin diary bonus (the league has no diaries).

## Guesses and gaps (look at these first)

- **The spellbook UI for the bolt spells is unverified.** The cache has no component for the ten bolt spells and no interface that lists
  them, so the spellbook's "View" button on the bolt entry (`magic_spellbook:xbows_enchant`) asks with a chat menu (three pages) instead.
  If the client opens its own panel as well, the menu will still show; needs a client check. Needs `packCache` (the spell data is
  packed from `SpellObjEditor`).
- Fiery npcs are matched by internal name (adult chromatic and metal dragons, lava dragons, King Black Dragon, fire giants, pyrefiends, fire
  elementals), players by a fire staff or fire cape; water staves by name too (`BoltTargets`).
- Ruby needs 10 hitpoints (a guess, to stop the cost rounding to 0); its hitpoint cost is a typeless hit.
- Jade on npcs has no Agility roll (npcs have no Agility); on players the wiki's double roll and Agility curve are not built, only the
  Protect from Magic block.
- Sapphire's low-hitpoints rule and the wiki's overhit clamp are not built.
- A shot fired with the last bolt in the quiver rolls no effect (the quiver is empty when the damage is rolled).
- Annihilate (dragon crossbow special) and the ballistas roll damage in their own way and no bolt effect.
- Effects run when the hit is queued, not when it lands (the graphic is delayed to the landing).
- No sounds (none are named in the cache).
- No league tasks: none of the 1,589 original tasks is about bolt effects.

## Tests

- `content/other/bolt-effects`: `BoltGemTest` (names, chances, fiery targets), `EnchantedBoltsTest` (every gem, Zaryte, negations,
  poison on players, chances over 20,000 shots), `CrossbowSpecialBoltsTest` (Evoke, Armadyl Eye).
- `content/skills/magic/utility-spells`: `UtilitySpellsTest` (levels, ten bolts a cast, dragon bolts, no bolts, level).
- `content/areas/misc/isle-of-souls`: `PvpPoisonTest` (a player's poison is the player's own poison).

Needs `packCache` after pulling (spell data and the new spell objs).
