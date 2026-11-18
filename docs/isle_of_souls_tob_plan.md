# Theatre of Blood plan

Scoped with the user on 2026-11-18 and built the same day, all at once (the user asked for no stages). The as-built notes, the guesses and the client checklist are at the end ("As built").

## Decisions

| Question | Answer |
| --- | --- |
| Modes | Normal only. Numbers live in a per-mode table so Entry or Hard can be added later. |
| Entrance | The real Ver Sinhaza, reached by a portal on the isle (same pattern as ToA). Real notice board party screens, entrance, retrieval chest and Rewards Chest. |
| Party size | Up to 8, set by an event setting (`tob.partyCap`, default 8). See "Parties larger than 5". |
| Reward items | All of them: Scythe of Vitur, Sanguinesti staff, Avernic defender, ornament kits and Lil' Zik. |
| Loot | Same rules as ToA: wiki rates (purple 1 in 9.1 with no deaths), boosted by the relic drop multiplier (`relics.dropMultiplier`), team uniques (`tob.teamUniques`, on by default). Pet unboosted. |
| Staging | All at once (user, 2026-11-18). |
| Portal | In the Soul Wars lobby for now (the user moves it later). |
| Tasks | Only the Raging Echoes ToB tasks; no extra "complete once" task. |
| Shorter run | None: a run is all six rooms. |

## What already exists

- Cache (rev 233): about 206 ToB npc symbols, 489 locs, 96 varbits, 85 seqs, 57 spotanims; interfaces 23 `tob_chests`, 28 `tob_hud`, 50 `tob_partydetails`, 363 `tob_scoreboard`, 364 `tob_partylist`, 405 `tob_midway_stores`, 459 `tob_infoboard`; varps 1740 `tob_mycontroller` and the transmit varps; all the reward objs, the six boss books and the escape crystal (`tob_teleport`, 22517).
- Combat: Justiciar damage reduction (`DamageReductionAttributes.Justiciar`), the scythe weapon category and styles, the Dawnbringer special. Powered-staff accuracy/max hit helpers exist (`PlayerAttackManager.rollStaffAccuracy`), used today only by Tumeken's shadow (`content/other/special-weapons`).
- Not built: the scythe's extra hits and charging, the Sanguinesti staff's spell, heal and charging, the Avernic defender recipe, ornament kits, Lil' Zik.
- The static spawn list (`osrs-npc-spawns/underground.toml`) places ToB bosses in the real theatre; they must come out, since each raid spawns its own.
- League tasks: 946/947/948 (complete 25/50/100, Normal or Hard), 940 Avernic defender, 941 Sanguinesti staff, 942 Scythe, 943 a Justiciar piece, 944 full Justiciar, 945 Ghrazi rapier, 1135/1136 ornament kits on the scythe. Left out: 1425 (Combat Achievements) and 939 (Amulet of blood fury, needs blood shards from Darkmeyer).
- Reference: tsps `docs/theatre-of-blood.md` and `server/plugins/minigames/tob/*.js` (about 7,400 lines, Normal only, phase 1 tested, bosses untested). Facts only, never port code. Its doc lists the client contracts it found (party screen scripts 2317/2321/2323/2340, pause-button children, HUD varbits 6440-6449, supply points varbit 6460, chest varbits 6450-6454), captured projectiles/timings, and its own guesses. Verify every UI contract against this client's cs2 before building on it, as with ToA.

## Parties larger than 5

The client is built for 5:
- the party screen (50) has 5 member lines and kick buttons (pause children 7-11);
- the HUD (28) has 5 health slots (varbits 6442-6446);
- the vault has 5 monumental chests (multilocs 33086-33090 on varbits 6450-6454);
- wiki boss scaling stops at 5 (hp 75% up to 3, 87.5% at 4, 100% at 5), and several mechanics have caps tied to 5 (Maiden's Matomenos at most 10, Xarpus' exhumed timing per size, Sotetseg's big ball 121-188 for 2-5).

Plan: M0 checks whether the screens have spare rows. Where they do not, players 6-8 are listed in chat, the HUD and party screen show the first 5, players 6-8 get extra chests in the vault (plain copies of the chest without the own/teammate state), and boss numbers past 5 are extrapolated (to be listed as guesses).

## Milestones

**M0 probe.** Copy the theatre's map squares (x 3136-3391, y 4224-4479, planes 0-3, per tsps) into one per-raid region and check collision and drawing; check Ver Sinhaza is in the map. Decode interfaces 364, 50, 28, 405, 23 against this client's cs2 (row counts for the 8-player question). Remove the static boss spawns. Add the `tob` event-settings block.

**M1 shell.** Isle portal; Ver Sinhaza notice board with the real party screens (applications, kicks, preferences); entrance flow and death warning; raid state (rooms in order, barriers, passages, orators, deaths and the jail, wipes, retrieval chest, logouts, blocked teleports, escape crystal); HUD; completion count on the collection log varp; dev commands (`::tob`, `::tobroom`, `::tobcomplete`). Rooms are stubs that can be completed by command.

**M2 Maiden + Bloat**, plus the first supply chest (points by damage share, interface 405).

**M3 Nylocas + Sotetseg**, plus the second supply chest. Nylocas waves from the wiki table; Sotetseg's maze and Shadow Realm.

**M4 Xarpus + Verzik.** Xarpus' exhumed, poison and counter; the Dawnbringer; Verzik's three phases.

**M5 vault, loot, items, tasks.** Monumental chests and the loot interface (23), Rewards Chest; ToA loot rules; boss books; reward item effects (scythe, Sanguinesti staff, Avernic defender, ornament kits, Lil' Zik); league tasks; wiki page entry.

## As built (2026-11-18)

Code is in `content/areas/misc/isle-of-souls/src/main/kotlin/.../isleofsouls/tob/`:

| File | What it holds |
| --- | --- |
| `TobLayout.kt` | The region template (the whole theatre, 32x32 zones, copied unchanged into one large region), real/region tiles, the rooms (arrival, bounds, arena, barriers, exits, jails, orator and supply chest tiles), Ver Sinhaza and the isle portal tiles. |
| `TobConfigs.kt`, `TobSettings.kt` | Cache references; the `tob` block of `event-settings.json` (`partyCap` 1-8, default 8; `uniqueMultiplier`; `teamUniques`, default true). |
| `TobParties.kt`, `TobBoard.kt` | Parties (cap from the event file, ten applicants, blocks) and the notice board's screens 364/50. |
| `TobRaid.kt`, `TobRaids.kt`, `TobHud.kt` | The raid, its members and events; going in, barriers, passages, completing rooms (restore, release the jailed, times), deaths and the jail, wipes, leaving; the HUD (28). |
| `TobRoomRuntime.kt`, `TobRoomDirector.kt` | The room framework (spawns, locs, projectiles, monsters, hits, the HUD bar, scaling) and its wiring (built on arrival, started at the barrier, ticked, hits trimmed and counted). |
| `TobMaiden.kt`, `TobBloat.kt`, `TobNylocas.kt`, `TobSotetseg.kt`, `TobXarpus.kt`, `TobVerzik.kt` | The six fights. Each class comment lists its mechanics, the wiki numbers and the guesses. |
| `TobSupplies.kt` | The supply chests after the Bloat and Sotetseg and their shop (405). |
| `TobRewards.kt`, `TobVaultScript.kt` | Completion counts, the loot roll, the monumental chests and their interface (23), the Rewards Chest by Ver Sinhaza's bank. |
| `TobRewardItems.kt`, `TobGearTasks.kt` | The Scythe of Vitur and Sanguinesti staff (attacks, charging), the Avernic defender, the ornament kits; the league's equip and kit tasks. |
| `TobLobbyScript.kt` | The portals, the board, the entrance, the bank booths, the HUD in town, barriers, passages, orators, the vault's way out, the escape crystal, deaths and logouts. |

Elsewhere: `LobbySpawnScript` asks `TobRaids.respawnFor`; `EventSettings` reads `tob`; `IsleOfSoulsModule` binds `TobWeapons`; the league task list, enum, edits and series have the 11 tasks (940-948, 1135, 1136; `struct.sym` names added); `BaseVarObjBits.tob_charges` (varobj 65526) holds the weapons' charges; `synth.sym` has 11 invented `tob_*` names; `multiway.json` makes the theatre multi-combat; `tools/spawns/exclusions.txt` drops the static theatre spawns (the regenerated `underground.toml` is 64 lines shorter).

Facts found while building:
- The theatre is x 3136-3391, z 4224-4479 (32x32 zones): it fits one large region without packing. The walkways of the Maiden, Bloat, Nylocas and Sotetseg rooms are bridged tiles on level 1 (players stand on level 0); Xarpus' room really is on level 1; the Shadow Realm is level 3 (x 3354-3367, z 4309-4329).
- Map locs: the barriers (`tob_arena_barrier`), passages (`tob_dungeon_walkway_exit_clickbox`), Xarpus' door, his Dawnbringer skeleton (3171, 4397, 1), the Nylocas supports (3x3 at the arena corners), the Sotetseg maze's 210 dark tiles in the arena and 210 in the realm, the realm's portal (3357, 4330, 3), and in Ver Sinhaza the entrance, notice board, both retrieval chests, the Rewards Chest (3650, 3217) and the bank booths. Not in the map: the orators, supply chests, Verzik's pillars, the monumental chests (all placed by the code).
- Party screens (decoded from scripts 2317/2319/2323/2330/2340/5220): TSPS's contract is right, except that 2317's int is what the viewer is (only a leader, 2, gets Kick/Quit), not the slot. 2323 counts applicants as rows drawn minus five, so exactly five member lines must be sent. Mode 0 is Normal (enum 3852).
- Npc configs (decoded): Verzik on her throne (8369, size 5) has Talk-to itself; phase 1 has 2000 hitpoints, phases 2 and 3 have 3500 (sizes 3 and 7); Maiden 3500; Xarpus 5000 (size 3 waiting, 5 fighting); Sotetseg 4000; Vasilias 2500; the Nylocas support npc 130; Verzik's pillar 185; the Matomenos 100 (Maiden) and 200 (Verzik).

### Deviations and guesses to review

- Parties of six to eight: the board draws the first five members (the rest are listed in chat when the panel opens; the leader cannot kick them from the screen, and they leave with the panel's Leave button); the HUD shows the first five; players six to eight share the vault's chests (slot 6 uses chest 1 and so on, each player seeing only their own loot). Bosses scale on past five at +12.5% hitpoints a player, and the size tables (Maiden's Matomenos, Xarpus' exhumed) are extended by my guesses.
- Deaths keep items, as everywhere on the isle: a wipe puts the party outside with nothing lost, so the retrieval chest is always empty (and says so).
- A logout leaves the raid (the engine moves anyone saved inside an instance back to the lobby). The wiki's "rejoin your party among the dead" is not done.
- Teleports are not blocked (as in ToA): teleporting out leaves the raid. The escape crystal's Teleport takes you out and is used up (as TSPS has it).
- No room music and no completion jingle (the cache's midi/jingle symbols have no ToB names).
- The scythe and staff are charged straight from the inventory (blood runes and vials of blood used on them, or their Charge op), not through the Vyre well. The uncharged scythe fights as a plain weapon.
- The ornament kits drop only in Hard Mode in the real game, which is not built; they are rolled in the vault at Hard Mode rates (holy 1/100, sanguine 1/150, scaled by standing) so the two kit tasks can be done.
- Sotetseg's unblocked ball turns all prayers off (the engine has no protection-prayer lock-out).
- Verzik phase 1: a Dawnbringer hit counts as Pulsate (uncapped) when it does 75 or more; the Dawnbringer has no regular attack here.
- Every guess listed in each fight's class comment (most are TSPS's; see `tsps/docs/theatre-of-blood.md`, "Estimates").
- Not built: spectating, the scoreboard and info board (363/459), MVP points, Combat Achievements, the Mysterious Stranger's first talk and shop, the escape crystal's Configure and Auto-afk, the first-time death warning, Xarpus absorbing exhumed into its stats, the Vyre well.

### Needs `packCache`

New `synth.sym`, `struct.sym` and `varobj.sym` names, the league task struct edits and enum entries, the weapons' charged/uncharged params, the `tob_chests` inventory made permanent, and the multiway area. Tested in a sandbox copy of `.data`: `packCache`, a server start (342 scripts loaded, no errors), 11 unit tests (`TobRulesTest`) and 16 integration tests (`TobRaidTest`, `TobFightTest`: parties, going in, barriers, deaths and the jail, a wipe, supplies, the vault and loot, and every fight run through all its phases to its end). The isle's other unit tests and the task, event, lobby and ToA integration tests still pass (the old `FightCaveWavesTest` shortened-file failure is unrelated).

### Client checklist

1. The portal in the Soul Wars lobby (2221, 2821) and the one back beside Ver Sinhaza's notice board (3660, 3220).
2. The notice board: party list rows, My party, Refresh, the panel (Apply, Accept, Decline, Kick, preferred size and level cycling), the HUD names in town.
3. Going in: the red fade, arrival in the Maiden's room, the HUD in the theatre (names, health, the room's bar).
4. Each barrier: the leader's "Is your party ready to fight?", the others crossing after, "You must stay and fight!".
5. The Maiden appearing and her attacks, blood splats, Matomenos walking in; her death and the pool.
6. The Bloat's loop, flies, sleeping, hands; the supply chest after it (points, the 405 shop, one stamina potion).
7. The Nylocas coming out of the tunnels, the supports, Vasilias' style changes.
8. Sotetseg's balls, the big ball, the maze (the runner in the realm, the glowing tile, wrong tiles, the tornado, the portal home).
9. Xarpus: the exhumed, his rise (the size change), spit and pools, the quadrant stare.
10. The Dawnbringer from the skeleton; Verzik's Talk-to, the pillars, phase 2's flight and attacks, phase 3, her death, the treasure room door.
11. The vault: the chests marked as yours (purple with a unique), the loot interface, the Rewards Chest by the bank for leftovers.
12. The scythe's hits on a big monster, the staff's spell and heal, charging both, the Avernic defender, the ornament kits and their tasks.
