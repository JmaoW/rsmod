# Npc type attributes (undead, demon, ...)

`CombatNpcAttributeCollector` builds combat attributes from npc params. The salve amulet, demonbane
weapons (Arclight, Emberlight, burning claws, scorching bow, demonbane spells), dragon hunter
weapons, the keris variants, Gadderhammer, leaf-bladed and rat bone weapons and the Tormented Demon
bonus all depend on them. Only tests and the revenants (`RevenantNpcEdits`) ever set them, so the
bonuses never applied against a real npc.

## What the cache already had

Dumping the packed cache's npc params (2026-10-19) showed three of the params are Jagex's own and
already set: `draconic` (151 npc types), `golem` (48) and `kalphite` (46). The task description
said none were populated; those three are, so they are left alone. `vampyres` is a real npc
category in the cache too.

Missing and now supplied: `undead`, `demon`, `demonbane_resistant`, `shade`, `leafy`, `xerician`,
`rat`, `tormented_demon`, `amascutnpc`. (`revenant` stays with `RevenantNpcEdits`; the revenants also
get `undead` from the wiki data, which is right: the salve amulet works on them.)

## Where the data comes from

`tools/npc-attributes/derive_attributes.py` writes
`api/cache-enricher/src/main/resources/org/rsmod/api/cache/enricher/npc/npc_attributes.toml`
(re-run it after `--fetch`; it needs `.data/symbols/npc.sym` and the osrsreboxed file that
`tools/drops` already caches):

| Param | Source | npc types |
|---|---|---|
| `undead`, `demon`, `shade`, `leafy`, `xerician` | osrsreboxed-db `attributes` (wiki infobox) by npc id | 520 / 191 / 6 / 13 / 32 |
| `rat` | osrsreboxed-db category `rats`, `brine rats` | 37 |
| `demonbane_resistant` | wiki "Demon (attribute)": only Duke Sucellus has reduced effectiveness (70%, so Arclight gives 49% = the 149/100 in the formulas) | 3 |
| `tormented_demon` | name | 8 |
| `amascutnpc` | every Tombs of Amascut monster: npc ids 11696-11805 and Amascut (osrsreboxed has none; the formulas scale defence rolls by the raid level and add the Keris and shadow bonuses) | 111 |

Ice Demon (115%), Yama (120%) and Void Flares (200%) are *more* effective against demonbane. The
engine only has a "resistant" flag, so they get the normal bonus; a multiplier param would be needed.
All 785 classified ids were checked against the cache's own npc names and matched.

## How it is applied

The data file is an enricher input like `npc_anims.toml`, not an `NpcEditor`: there are about 800
npc types, so `DefaultNpcCacheEnricher.loadDerivedAttributes` reads it and emits one attribute-only
type per npc name, which `CacheEnrichmentConfigs` merges with the cache type and with whatever
`npcs.toml` or the derived anims set for it. **Needs `./gradlew packCache`.**

`td_shield_active` defaults to true, so the Tormented Demon's unshielded damage bonus stays off until
a script drops the shield (there is no Tormented Demon fight yet).

## Tests

`NpcTypeAttributesTest` (combat-formulas integration, runs against the real cache, so it fails
until `packCache` has run): an ordinary goblin has no type attributes; a zombie is undead; Black
demon is a demon; Duke Sucellus is a demon with demonbane resistance; Vorkath is draconic and
undead; shades, rats, Amascut, kalphite and golems; and the salve amulet (e), Arclight (and its
weaker bonus against resistant demons) and dragon hunter lance raise melee hit chance against a
matching twin npc and not against an ordinary one.

Sandbox run 2026-10-19: combat-formulas unit + integration and cache-enricher, 190 tests, 0 failures.
