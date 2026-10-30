# Isle of Souls: the revenant weapons, the bracelet of ethereum and the amulet of avarice

Status: **built and tested 2026-10-19, not yet seen in a real client.** 26 + 12 new integration tests
(`RevenantEtherTest`, `RevenantWeaponsTest`) and 2 more in `LeagueGearTasksTest` pass, as do the
combat-formula, game-process, drop-table and player suites. In the whole isle suite two Zulrah tests
(`ZulrahLobbyTest` boat tile, `ZulrahRunTest` ten times the scales) fail; neither touches this work
(the scales one fails the same with the new drop event taken out), and the unit test
`FightCaveWavesTest` is still the old failure.
**Left for the user:** `./gradlew packCache` (new area, varobj/varp/struct symbols, task struct
edits, the new tasks in the client list) and a client check.

Scope decisions made with the user (2026-10-19): the full wiki charging, the bracelet's effects,
the upgrades and the league tasks; no bank "Configure-Charges"; deaths do not drop items, so the
death rules are not built; the weapons' bonus applies everywhere; revenants drop ten times the
wiki's ether; the Wilderness is still marked for the Revenant Caves, the Escape Caves and the
boss lairs.

Sources: the OSRS wiki pages of the bracelet of ethereum, the three weapons and their upgrades, and
the cache (item options and ids, the `wild_cave_*` objects).

## What it does

| Thing | Rule |
|---|---|
| Charging | Use revenant ether (`wild_cave_shard`) on a weapon. The first 1,000 activate it and are never spent; up to 16,000 more can be added (17,000 stored). Under 1,000 will not activate. A count dialog asks how much. |
| Spending | Each attack (hit or miss) spends 1 ether. A weapon with only its activation left stays charged but loses its +50% passive; the bows and the sceptres' own spell cannot be used. |
| Chainmaces | Fight as usual. The ether is spent before the roll, so the very last ether is spent by an attack with no bonus. |
| Bows | Need no ammunition. Roll first, then pay. Craw's bow uses the `wild_cave_bow_arrow_*` graphics, the Webweaver the `*02` ones (guess). |
| Sceptres | The non-autocast ones are powered staves with a built-in spell: max hit Magic/3 - 8 (Thammaron's) or - 6 (Accursed), the revenants' own magic graphics and the cast animation of the other powered staves (guesses). Any combat spell cast with a sceptre (the "(a)" forms autocast) spends 1 ether (`SpellCast` event). |
| Boost | +50% accuracy and damage against every npc (the Wilderness check is bypassed on purpose, see below) while the weapon has ether to spend. |
| Options | Check (3rd), Swap (4th, sceptres: same ether, other form), Uncharge (5th: all ether back, activation too, asks first), Dismantle (3rd of an uncharged weapon: 7,500 ether, or takes the part off an upgrade). |
| Upgrades | Use Claws of Callisto on Viggora's chainmace (u) (Smithing 85), Fangs of Venenatis on Craw's bow (u) (Fletching 85), the Skull of Vet'ion on Thammaron's sceptre (u) or (au) (Crafting 85). No experience, no tools. The skill is the boosted level. The coins-to-an-NPC route is not built. Taking a part off with Dismantle is assumed for all three (the wiki says it only for the skull). |
| Bracelet | Ether charges it (no activation, 16,000 at most). Worn charged: revenants take 3/4 off every hit (rounded) and spend an ether per revenant attack, and do not start fights with the wearer (`HuntTolerance`; one the wearer attacked still fights back). "Toggle-absorption" (stored on the item): the ether a revenant would drop goes into the worn bracelet (charged or not) as far as it has room, the rest stays on the ground. Uncharge returns all; Dismantle (uncharged) gives 250. |
| Ether drops | Ten times the wiki, as the `revenantEtherMultiplier` setting in `.data/league/event-settings.json` (default 10): `revenants.toml` holds the wiki's amounts (2 to 8 for a revenant goblin) and `EventSettingsScript` multiplies the count when it drops, so 20 to 80 in steps of 10. `ETHER_SCALE` in `tools/drops/revenants.py` stays 1. |
| Tasks | Equip a Bracelet of Ethereum (1014, medium), Thammaron's sceptre (697), Viggora's chainmace (910), Craw's bow (1018), the Webweaver (1257), the Accursed sceptre (1258), the Ursine chainmace (1259) (elite, either form), and Obtain Every Revenant Weapon (1019, master: the three base weapons dropped by revenants, tracked by three team counters). Their area was cleared like the other Wilderness tasks. |

## The amulet of avarice

Added the same day. Its +20% accuracy and damage against revenants was already in the combat
formulas (35% with a Forinthry Surge skull) but **never applied**: it reads the `revenant` npc
param, which nothing set. `RevenantNpcEdits` (in `AvariceScript.kt`) now sets it on the eleven
revenants (needs `packCache`). `AvariceScript` adds the rest:

- Wearing it skulls the player (`skullicon_red`), which also gives the revenants' skulled drop
  tables. Taken off, the skull fades after 20 minutes (a soft timer, `avarice_skull`); wearing it
  again cancels that. The state is the permanent varp `isle_avarice_skull` (0 none, 1 worn, 2
  fading). A fading skull gets its full 20 minutes again at the next login (guess).
- The drops a revenant leaves for a wearer are noted if they have a noted form (`NpcDropEvents.Claim`).
  Uniques (the drops the tables mark `boosted`) are not; stacks such as ether have no noted form.
  Revenants only: the Wilderness Slayer Cave and the Chaos Temple, the wiki's other places, are not
  on the isle.
- Not built: the warning before it skulls you (an equip cannot be refused here), and the Revenant
  maledictus's Forinthry Surge skull (no maledictus).

`AvariceTest` covers the skull, the fade, the noting and the bonus attributes.

Also seen, not touched: the `undead` and `demon` npc params that the salve amulet and the demonbane
weapons read are not set by anything in the repo either, so those bonuses look dead the same way.

## The Wilderness

The `wilderness` area (map squares x 49-53, z 156-161, and the three singles lairs' squares) is packed
by `WildernessMapAreas`. Only the aggression check reads it: an npc skips a player more than twice
its level unless the player is in the Wilderness (this was a stub that always said no, so revenants
now go for high-level players in the caves). The revenant weapons' bonus does not read it: the
npc attribute collector marks every npc as in the Wilderness, because the isle has none.

## Where everything is

| File | What it is |
|---|---|
| `api/player/.../worn/RevenantEther.kt` | Reads the stored ether of an item (the combat formulas use it too). |
| `api/config/.../BaseVarObjBits.kt` | `ether_charges` (15 bits) and `ether_absorb` (1 bit), symbols in `varobj.sym`. |
| `api/combat/combat-formulas/.../collector/*` | The weapon attributes need ether to spend; every npc counts as Wilderness. |
| `api/game-process/.../hunt/HuntTolerance.kt` | New hook (like `RunDrainMod`) so a player can go unnoticed; the hunt processor reads the `wilderness` area. |
| `api/drops/.../NpcDropEvents.kt`, `api/death/.../NpcDeath.kt` | New `Claim` event: a subscriber removes drops it takes before they are put on the ground (the loot tracker and `Rolled` still list them). |
| `isleofsouls/revenant/RevenantConfigs.kt` | The objs, the weapon table, swaps and upgrades. |
| `.../EtherCharges.kt` | Ether arithmetic on items and on what is worn. |
| `.../RevenantEtherScript.kt` | Charge, check, uncharge, dismantle, swap, upgrade, bracelet options. |
| `.../RevenantWeapons.kt` | The three weapon types in the weapon registry, and the spell ether. |
| `.../RevenantBraceletScript.kt` | Absorption, tolerance, and the obtain-every-weapon task. |
| `dungeons/DungeonRevenantScript.kt` | The bracelet takes damage off a revenant's hit. |
| `map/WildernessMapAreas.kt` | The area. |

## Not built

- The weapons' special attacks (Viggora's/Ursine Bear Down, Swarm, Condemn): not asked for.
- Andros Mai, Derse Venator and Phabelle Bile (the 500,000 coin upgrades).
- The bank's "Configure-Charges" option, and Check/Toggle on the worn item.
- Death rules (ether and the activation always lost): deaths drop nothing here.
- The wiki's boosted-on-ghost-task rule for ether.

## Client checklist

1. After `packCache` and a restart, kill revenants: ether drops in tens; use it on a Craw's bow (u).
2. Wield a charged bow: no arrow needed, one ether per shot, "no ether" message when empty.
3. Wield a charged sceptre (the non-autocast one): it casts by itself; Swap gives the autocast form.
4. Wear a charged bracelet in the Revenant Caves: revenants leave you alone; hit one and its hits
   are a quarter size; toggle absorption and kill one.
5. Check the item options in the client (3rd/4th/5th) match: the cache's lists were read, not clicked.
6. Check the six task rows show (they should list as "Wilderness"-free) and that the struct text is right.
