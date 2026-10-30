# Multi-level scenery: the Kourend statue

How to place scenery that stands on several levels of one tile and is seen from the ground, such as the
Kourend statue (centre of Kourend, and the Catacombs of Kourend entrance on the Isle of Souls). Written
2026-10-19 after working out why the pieces would not show.

## What the statue is

Four 5x5 locs stacked on one tile (the tile is the south-west corner), one per level, all `CentrepieceStraight` (shape 10), all facing South
(angle 3):

| Level | Loc id | Name |
| --- | --- | --- |
| 0 | 27785 | `zeah_kourend_statue_plinth` (the only one with ops) |
| 1 | 14193 | `kourend_statue_complete_bottom` |
| 2 | 14194 | `kourend_statue_complete_middle` |
| 3 | 14195 | `kourend_statue_complete_top` |

The centre statue is at `1634,3671`. The catacombs entrance is at `2233,2866` (it started at `2202,2817` and was moved on 2026-10-19).

## The rule

The client only draws a loc on a level above the player from its **own map files**. Two things are needed:

1. The loc has to be a **static loc in the client's `l` map file**. A loc the server spawns with
   `locRepo.add` on levels 1-3 never shows from the ground, even though the server has it and the
   `::locs` log lists it. (Spawned locs on the player's own level work fine, which is why the plinth on
   level 0 always drew.)
2. The tile needs the **visible-below rule** (tile rule 8, `VISIBLE_BELOW`) on each upper level the loc
   stands on. The centre statue's tile has rule 8 on levels 1-3, plus a height on level 3.

Both edits go into the client cache and the game cache, so the server and client agree.

## What did not work

- Rule 8 on levels 1-3 alone, with the pieces spawned by the server.
- Rule 8 plus a height of 30 on level 3, still spawned by the server.
- Making `PlayerZoneUpdateProcessor` send spawned locs on the levels above the player (zone reset plus
  persistent locs). It was tested in the zone-processor integration tests, but the real client still
  did not show the pieces, so it was reverted. The processor sends the player's own level only, as in
  vanilla.

## How it is done in the repo

All of it runs during `packCache`, in `api/cache-enricher`:

- `map/loc/MapLocEnricher.kt` takes a list of `MapLocSpawn(id, coords, shape, angle)`, decrypts the
  square's `l[x]_[z]` file with its XTEA key from `.data/cache/xteas.json`, decodes it with
  `MapLocListDecoder`, adds the locs and writes it back with `MapLocListEncoder`.
- `map/tile/MapTileRuleEnricher.kt` takes a list of `TileRule(coords, rule, height)` and edits the
  square's `m` file at byte level (one rule opcode before each tile's terminator, or a height as the
  tile's last opcode). A tile that already has a rule is an error, since the cache keeps one rule per
  tile.
- `CacheEnrichmentMaps` holds the two lists, `tileRules` and `mapLocs`, and applies both in
  `encodeClientMaps(dest)`.
- `GameServerCachePacker.encodeClientMaps` calls it for the **js5 cache** too. The js5 cache (what
  the client is served) is copied from `vanilla` and normally only gets configs and resources, so
  without this the client keeps the vanilla map squares. The game cache gets the same edit through
  `encodeAll`.

The plinth is not a map loc. It is the dungeon's entrance loc, spawned by `DungeonScript.placeEntrances`
from the `Dungeon` entry (`Dungeon.CATACOMBS`: `entranceType`, `entrance`, `angle = South`). Keeping
the interactive loc server-side means its ops and replacement logic stay in code.

## Moving it or adding a similar object

1. Pick the tile. Square key is `x / 64`, `z / 64`; the local tile is `x % 64`, `z % 64`.
2. In `CacheEnrichmentMaps`, change the `KOUREND_STATUE` constant; `tileRules` and `mapLocs` follow it.
3. Move the interactive loc (the plinth) by changing the `Dungeon` entry's `entrance` and `front`.
4. Make sure the tile has no rule already (the enricher fails the pack loudly if it does), and note that
   a tile that is shared with another loc on the same layer will clash.
5. Stop the server, run `./gradlew packCache`, then restart the server **and** the client. `packCache`
   deletes `game`, `enriched` and `js5`, so the server must not be running.
6. Check the server side with `::locs <x> <z> <level>` (scans levels 0-3 and a radius of 3; the full
   list goes to the server log). This confirms the server has the locs, not that the client draws them.

To check a pack without touching the real caches, pack against a copy of `.data` and decode the `l` file
from both `game` and `js5`. The 2026-10-19 check of square `34,44` went from 3549 locs in vanilla to 3552
in both caches.

## The gap between the pieces (tile heights, and the 5x5 footprint)

The statue locs are **5x5** (loc config sizeX/sizeZ = 5, with the loc's tile as its south-west corner), not
1x1. That decides which tiles matter, and it is why the first height attempt (two corner tiles of a
1x1) changed nothing and the thin gap stayed. Read the loc's size from its config before placing it.

How the client places a loc (decompiled client, `Fonts.method7226` in NestRSPS-Client, and the terrain
decode in `class67`):

- A loc is drawn at the average height of four tiles: x + (sizeX >> 1) and x + ((sizeX + 1) >> 1), the same
  for z, on the loc's own level. For a 5x5 that is x + 2..3 and z + 2..3.
- A tile on level 0 with a height byte `h` is `-h * 8`. A tile on an upper level with a height byte `h` is
  the same tile one level down minus `h * 8`; with no height it is minus 240. So `30` is the default.
- The centre statue's tiles are not stacked 240 apart. On levels 1 and 2 every tile from the statue's
  second row north (dz 1..6, dx 0..5) has height 34 (level 1) and 28 (level 2); on level 3, dz 0..5 has 30.
  The pieces are therefore stacked 272, 224 and 240 apart. At plain 240 steps the first two pieces sit
  8 units too far apart, which is a thin gap.
- The visible-below rule (8) covers the whole 5x5 (dx 0..4, dz 0..4) on levels 1 to 3.

`CacheEnrichmentMaps.tileRules` copies that pattern for any tile `KOUREND_STATUE`. The method for another
multi-level piece: dump the original square's `m` file (opcodes are shorts: 0 end, 1 height byte then
end, 2-49 overlay with a short id, 50-81 rule, 82+ underlay) for every level around the original, and copy
the rules and heights as offsets from the loc's tile.

Footprints also matter for the server: the plinth blocks 5x5 tiles, so the dungeon's `front` (where a
player lands when leaving the catacombs) has to be outside `x..x+4, z..z+4`.

## Facts used to get here

- The server's loc registry is keyed by coords and layer, so several levels on one tile are fine.
- Map `l` files are XTEA-encrypted from the 5 byte container header on. The short smart decoding is the
  one in `MapLocListDecoder`; loc ids use the incremental smart (32767 steps).
- Tile settings in the `m` files: rule 8 is `VISIBLE_BELOW` (`MapTileSimpleDefinition`).
- The first terrain edit did not reach the client because only the game cache was written. The js5
  cache needs its own write.
