# World NPC spawns

Built 2026-10-09. Every NPC the OSRS world has (outside the places listed under "Left out") is
spawned at the real tile, including the dungeons, so slayer and combat content has monsters to use.

## Where it comes from

`npc-spawns-osrs.json` from [dennisdev/rs-map-viewer](https://github.com/dennisdev/rs-map-viewer)
(`src/mapviewer/data/npc/`). It is from **August 2023**, so newer NPCs are missing (see "Gaps").
The repo carries no licence file for that data; its README credits other community sources. Fine for
this event, worth knowing before anything is shared more widely.

## How it works

```
tools/spawns/convert.py   source json  ->  surface.toml + underground.toml  +  last_report.txt
tools/spawns/gaps.py      what is missing                                  ->  gaps.txt
tools/spawns/exclusions.txt   your own exclusions (npc / rect / near rules)
content/areas/misc/osrs-npc-spawns   the module: one MapNpcSpawnBuilder loading the two toml files
```

- **Never edit the toml by hand.** Change `exclusions.txt` (or the script) and run
  `python tools/spawns/convert.py`, then `./gradlew packCache`. Spawns are packed into the cache; a
  normal server start does not read the toml.
- `convert.py` reads the real rev 233 cache (`.data/cache/vanilla`) directly, so it needs no Gradle.
  `--fetch` downloads a fresh copy of the source json into `tools/spawns/source/` (git-ignored).
- `surface.toml` is the overworld; `underground.toml` is everything from tile y 4032 up (dungeons,
  and areas that sit off the main map).

## What the converter checks and drops

Every spawn is checked against the cache before it is written. Details and per-npc counts are in
`tools/spawns/last_report.txt`.

| Dropped | Why |
|---|---|
| nameless npc types (760) | Cache types with no name and no transform are unclickable placeholders (they belong to areas the cache does not contain). |
| renamed ids (31) | The id now belongs to a different npc (e.g. 3959 was Jungle savage, is Tormented Warrior). Kept ones are typos or quest-state transforms. |
| map square not in the cache (64) | Areas from newer cache versions. |
| Lumbridge squares (581) | The hand-made Lumbridge file already covers them (578 of its 585 spawns matched the source tile for tile, so the source is trustworthy). |
| Yarsul (1) | We place him ourselves in the Mining Guild; the source has him one tile away. |
| duplicates (6) | Same npc, same tile twice. |

**Kept on purpose:** spawns standing on "blocked" terrain (1,636). Water dwellers (ducks, fishing
spots, whirlpools, icebergs) stand on it by design. Fight Caves, Inferno, Zulrah and TzHaar City
spawns stay (we use instances for those).

## Left out because of our content

Nothing beyond the list above. Worth a look if you see doubled NPCs: our runtime placements are
Percy and Yarsul (Mining Guild), the Mage of Zamorak/Aubury/Dark mage (Runecraft sites), Grace and
the Agility arena clerk/Jackie, thieving targets, hunter creatures. Percy/Yarsul are handled; the
rest sit on the isle where the source has none of those NPCs.

## Limits to know

- **The engine holds 32,767 NPCs** (`NpcList`). The world takes about 23,300 (22,700 plus the 585
  from Lumbridge), which leaves roughly 9,400 for runtime NPCs: instance waves (Fight Caves, Inferno,
  Zulrah), thieving/hunter placements and respawning. Comfortable for a few dozen teams, but if
  instances start failing to spawn, this is why.
- Spawns carry no wander range or aggression; NPCs use their cache type defaults.
- **Drops:** every attackable monster that spawns has its real drop table, imported by
  `tools/drops` (`isle_of_souls_drops.md`); about 45 npc types (ducks, leeches, the revenants) have
  none on the wiki either and drop bones only.
- **Disguised monsters:** the source spawns the disguise, not the monster (Rocks and Whirlpool for
  the Cave kraken/Kraken, Sandy rocks for Sand crabs, Fungi for zygomites). The reveal needs a
  script; nothing here does it.

## Gaps (`tools/spawns/gaps.txt`)

1. **220 attackable NPC names added after the source list** (cache id above 12223). Most relevant for
   slayer dungeons: Araxyte and Araxxor (Morytania), the Varlamore set (Sulphur/Frost/Earthen/Lesser
   Nagua, Jaguar warriors, Knights of Varlamore, Minotaurs, Colossi, Emissaries, Custodian stalkers,
   Scarred/Warped/Armoured variants), Dire wolves, Amoxliatl, the Hueycoatl, Yama, Doom of Mokhaiotl,
   and new-id dragons (blue, black, red, brutal blue, green).
2. **Older monsters the source does not have (408 names).** Slayer-relevant: Aquanite, Cave kraken
   and Kraken (disguised only), Gryphon, Molanisk, Wall beast, Mutated zygomite, Mogre (only the
   guard), Venator, Wyrmling/Lava strykewyrm, Shadow Wyrm/Guardian Drake/Colossal Hydra (Karuulm
   bosses), Skotizo, Spindel/Artio/Calvar'ion, Revenants (only 15 of the many in the cave).
   The rest are bosses and instance variants (Nex, Great Olm, Zulrah, Hunllef, Nightmare, ...)
   that are meant to be scripted, not world spawns.

To fill a gap: add the spawns to a small hand-made `npcs.toml` (the format is in
`LumbridgeNpcSpawns`), or list them in a new source file and extend `convert.py`. The wiki's NPC
pages give tiles.
