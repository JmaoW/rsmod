# Dragonfire

Built 2026-10-12 in `content/other/dragonfire`. Numbers are from the OSRS Wiki pages "Dragonfire" and
"Drake". Tests: `DragonfireTableTest` (unit, pins the wiki rows) and `DragonfireTest` (20 integration
tests with real dragons).

## Which dragons breathe

| Kind | Dragons | Where it breathes | Table |
| --- | --- | --- | --- |
| Chromatic | green, blue, red, black, lava, reanimated | next to the player only, 1 attack in 6 (wiki) | `Breath.Chromatic` |
| Brutal | brutal green/blue/red/black | up to 5 tiles; next to the player 1 in 3 (guess) | `Breath.Chromatic` |
| Metallic | bronze, iron, steel, mithril, adamant, rune | as brutal | `Breath.Metallic` (Protect from Magic does nothing) |
| King Black Dragon | `king_dragon` | as above, 1 in 2 (guess); half its breaths are fire, half poison/ice/shock | `KingBlackDragon`, `KingBlackDragonSpecial` |
| Elvarg | `elvarg`, `elvarg_alive` | as above, 1 in 2 (guess) | `Elvarg` |
| Drake | `drake` | see below | `DrakeBreath` |

Baby dragons never breathe. The type lists are in `DragonNpcs` (`DragonfireConfigs.kt`).

## How a breath is worked out

The dragon rolls its Magic accuracy against the player's Magic defence when it breathes (the wiki's
"burnt" or "resisted"). The damage is rolled **when the breath lands**, from what the player has on then:
shield (anti-dragon shield, dragonfire shield, dragonfire ward, ancient wyvern shield, charged or not),
Protect from Magic, and antifire (regular and extended, or super and extended super). `Breath` holds the
12 wiki rows per breath. The hit is typeless so the generic prayer reduction does not touch it
(`NpcAttack.typeless`).

## Drakes

Their normal attack is a ranged shot (max 15, Protect from Missiles stops it). After every 7 shots they
send a volcanic breath at the tile the player stands on: 4 hits (one a tick) of 6-8; 0-4 with a shield
or an antifire potion; none with a shield and an antifire potion, or super antifire alone. A player who
steps off the tile before it lands takes nothing (`NpcAttack.extraHits` queues the 3 later hits).

## Potions

Super antifire (3 min, Herblore 92, antifire(4) + crushed superior dragon bones) and extended super
antifire (6 min, Herblore 98, super antifire + lava shards) can be drunk and made. Pestle and mortar on
superior dragon bones makes the crushed bones. Dragon Slayer II is not required.

## Needs `packCache`

The long-ranged dragons get an attack range of 5 (`DragonNpcEdits`). Without a repack they only breathe
once next to the player. The animations and graphics used (`dragon_firebreath_attack`, `firebreath_travel`,
`dragon_ranged_*_attack`, `drake_ranged`, `drake_*_proj`) are the cache's best match and have not been
seen in a client.

## Not built

King Black Dragon's poison, freeze and stat-drain side effects; dragonfire shield charges and special
attack; the brutal dragons' magic attack; wyverns, Vorkath, Galvek; the guesses in the table above.
