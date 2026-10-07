# Isle of Souls: Foundation Checklist

Step 1 of the build order in `isle_of_souls_content_plan.md`: prove that what is already built works
in a real client, before building combat, bosses and the rest on top of it. Only you can do the
client parts, so this is a script to follow and write results into.

Mark each line **pass**, **fail** (with what you saw) or **skip**. Send me the failures; I fix them
in order.

---

## A. Pack the cache (needed once)

`./gradlew packCache` deletes and rebuilds `.data/cache/enriched`, `game` and `js5`, so **stop the
server first**, then:

```bash
./gradlew packCache
```

Then start the server again and restart the client. The island area, the Home Teleport text and
other cache edits only show up after this.

- [X] Server starts after packing with no errors in the log
- [X] Home Teleport spell is named "Home Teleport" in the spellbook, with the tooltip
  "Teleports you to the Soul Wars lobby. Requires no runes."

## B. Walk the isle (one account)

Log in with a new account (new characters spawn in the Soul Wars lobby graveyards).

- [X] New character appears in a lobby graveyard, not on the normal spawn
- [X] Home Teleport lands back in a graveyard, and works while in combat
- [X] You can walk out of the lobby onto the rest of the isle (no invisible walls or trapped areas)
- [X] Banks you can reach: booths and chests open, banker NPCs open the bank
- [X] Trees, rocks and fishing spots exist and work: mine copper and tin, chop a tree, catch shrimp,
  cook it, light a fire, smelt a bar, smith an item (the smithing screens have never been seen in
  the client)
- [X] Note anything missing for later: altars, ranges, furnaces, anvils, shops, NPCs worth keeping
  (write them in the list at the bottom)
- [X] Note any spot you cannot walk to or that looks wrong

## C. League screens (one account)

- [X] Leagues tab opens; Tasks, Relics and Rank buttons open their screens and close
- [X] Completing a task shows the pop-up, chat message and updates points
- [X] Relic selection works and the tier 1 tool is handed out; the tool's Toggle option opens its menu
- [X] Reload the client and log in again: relic tools come back, task progress remains

## D. Teams (two accounts, two clients)

Write `.data/league/teams.json` (see `isle_of_souls_teams.md`) with both login names on one team,
or use `::teamnew 1 Test` then `::teamset 1 <login>` for each.

- [X] `::myteam` and `::teams` show the right team and members
- [X] A task completed by one player pops up for the other with "Completed by <name>"
- [X] A counter task (such as "Mine 5 Tin Ore") is a single shared total across both players
- [X] Team points match on both Leagues tabs
- [X] Shared bank: both see the same items; opening it while the partner has it open shows
  "<name> is using this bank right now."
- [X] Shared loot: an item one player drops can be picked up by the other, and not by a third player
- [X] Log one player out and in: points and tasks still match
- [ ] Freeze and unfreeze: `::leaguefreeze`, check `::teams`, `::leagueunfreeze`
- [ ] Open `.data/league/leaderboard.html` in a browser and check it updates

## E. Instances (two accounts)

- [X] `::instance patch` sends one player into the test patch (around 2190, 2931)
- [X] The second player's `::instance patch` lands in the **same** copy (you can see each other)
- [X] A third player outside the team is **not** in that copy
- [X] `::instances` lists it with both names
- [X] `::instance leave` returns a player to the normal map, partner stays
- [X] Both leave, then enter again: you get a fresh copy
- [X] **Log out inside the instance, then log in:** you arrive on the normal map, not a broken or
  black screen
- [ ] **Die inside the instance** (there is no damage command; spawn something that can kill you with
  `::npcadd`, or drop your own hitpoints and let it hit you): you respawn in the lobby, and can walk
  back and rejoin your partner. `::mypos` and `::tele <x> <z>` help you get around

Instances and the login-inside-an-instance path have only been tested in code, so these are the
most likely failures.

---

## Results and notes

Write failures and observations here.

-

Things the isle is missing (for later):

-
