# Isle of Souls: real rewards and drops skipped

Kept so they can be revisited (content plan, open question 3: if a real reward is not in this cache,
skip it and note it here).

## Agility

| What | Why it was skipped or changed |
|------|-------------------------------|
| Brimhaven voucher | The cache has no item of that name. `agilityarena_ticket_new` (a newer arena currency, called "Agility arena ticket" in this cache) stands in, so a voucher looks like a ticket in the client. Confirm in the client, and rename it if it reads badly. |
| Pirate Jackie's toadflax (3), snapdragon (10), graceful recolour (250) and amylase packs (60) | The shop prices by an item's value and these items are used elsewhere; only the pirate hook (800) is sold. Amylase packs are sold by Grace for marks of grace instead. |
| Pyramid tops, hallowed marks, Prifddinas crystal shards | No source on the isle (no Agility Pyramid, Hallowed Sepulchre or Prifddinas course). |
| Karamja gloves bonuses (arena XP and double tickets) | Nothing on the isle gives Karamja gloves. |

## Drop tables

Found by loading the imported drop tables (`tools/drops`) against this cache (revision 233); each of these
lines is skipped when the server starts. They are quest and minigame items with a bracket in the wiki
name, or items I could not find under any name. If you know the in-game name or internal name of one,
add it to `ITEM_ALIASES` or `OBJ_ALIASES` in `tools/drops/wiki_import.py` and import again. The full list
is in `DropDataReport.warnings`; the `OsrsDropDataTest` integration test writes it to
`isle-drop-warnings.txt` in the temp directory.

| Item | Monsters whose tables name it |
|------|-------------------------------|
| Water sapphire | Sea Snake Hatchling, Sea Snake Young |
| Air diamond | Killerwatt |
| Bones (Ape Atoll) | Skeleton (Ape Atoll) |
| Fire ruby | Infernal Mage |
| Giant crab meat | Crab |
| Key (Olaf's Quest) | Skeleton fremennik |
| Key (orange) | Ghost (Melzar's Maze) |
| Key (red) | Zombie rat |
| Key (yellow) | Skeleton (Melzar's Maze) |
| Map part (Lozar) | Wormbrain |
| Necklace of Fangs | Turoth |
| Potion (Apothecary) | Imp |
| Pristine spider silk | Sarachnis |
| Rock (elemental) | Earth elemental |

Wiki names that differ from this cache are renamed by `ITEM_ALIASES` (Javelin tips are "heads", Goat horn is
"Desert goat horn", Steel cannonball is the Cannonball, bracketed variants of bones, raw chicken and raw beef
are the plain item). Items that share a name with another item are picked by internal name in `OBJ_ALIASES`
(the moon key's tooth half is `varlamore_key_half_1`, the Blamish shells, the pirate eyepatches, the Troll
Stronghold prison key, Johnathon's crest part and Zebak's fang).
