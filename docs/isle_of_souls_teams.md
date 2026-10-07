# Isle of Souls teams

Teams of players who share their league tasks, counters and points. Code is in
`content/areas/misc/isle-of-souls/.../teams` and `.../tasks/LeagueTaskTracker.kt`.

## Files

Both live in `.data/league/` (relative to the server's working directory, git-ignored):

| File | Who writes it | Purpose |
| --- | --- | --- |
| `teams.json` | You, and the admin commands | Which players are on which team |
| `team-progress.json` | The server only | Each team's completed tasks (with times) and counters |
| `announcements.json` | You | What is announced server-wide (written with defaults on first start) |
| `team-banks.json` | The server only | Each team's shared bank: every item, and the tab layout |
| `event.json` | The server only | Whether the standings are frozen, and the frozen snapshot |
| `leaderboard.html`, `leaderboard.json` | The server only | The live scoreboard (see below) |

Roster edits can never touch progress, because they are separate files.

### teams.json

```json
{
  "teams": [
    { "id": 1, "name": "The Miners", "players": ["alice", "bob"] },
    {
      "id": 2,
      "name": "Axe Men",
      "players": [{ "login": "mcgee", "name": "McGee" }, "dave"]
    }
  ]
}
```

- `players` are **login names** (what is typed on the login screen), matched ignoring case. An entry
  is either the plain login name, or `{ "login": ..., "name": ... }` to choose how the player's name
  is shown in chat.
- Shown names: the `name` in the file if there is one, otherwise the player's in-game display name
  while they are logged in, otherwise their login name in title case (`some_name` becomes `Some
  Name`). The admin commands keep the names you wrote when they save the file.
- A player can be on one team only; a repeated name or id is an error.
- `name` is optional (defaults to `Team <id>`).
- Read at server start. A file that exists but is invalid **stops the server** with the reason,
  rather than starting empty and overwriting it later.

## Commands (admin unless noted)

All chat wording lives in `TeamMessages` (and `RelicMessages` for relics), with the colours in
`LeagueChat`: blue for names and numbers, green for a change that went through, red for a refusal.
The unit tests spell out every message exactly.

| Command | What it does |
| --- | --- |
| `::myteam` (anyone) | Your team, its points and tasks, and your teammates |
| `::teams` (anyone) | Every team, ranked by points: `Alpha - 340 pts, 12 tasks - Alice, Bob` (online players green, offline grey) |
| `::teamnew <id> <name...>` | Make an empty team |
| `::teamset <id> <player...>` | Put a player on a team (leaving their old one). Online players are synced straight away |
| `::teamremove <player...>` | Take a player off their team. They keep the progress they have as their own |
| `::teamreload` | Re-read `teams.json` after editing it by hand. A bad file is refused and nothing changes |
| `::teamreset <id|all> confirm` | Erase a team's (or every team's) tasks, points and counters. Backs the file up first. `all` also lifts a freeze |
| `::leaguefreeze` | Freeze the standings where they are now and announce the top teams |
| `::leagueunfreeze` | Make the standings live again |
| `::announcereload` | Re-read `announcements.json` after editing it |
|all> confirm` | Erase a team's (or every team's) tasks, points and counters. Backs the file up first |

Roster commands save to `teams.json`. If you edited `teams.json` by hand while the server was
running, a roster command is **refused** until you run `::teamreload`, so a hand edit is never
silently overwritten. Names with spaces work: `::teamset 2 some name`.

## How the sharing works

- The team's record is the truth. A completion by either player is recorded for the team and applied
  to every online member at once (chat message, pop-up, Leagues tab). Counters (for example "mine 50
  iron ore") are one shared total.
- Points are always the sum of the team's completed tasks, so both players always match. Relic tiers
  unlock from those shared points; relic **choices** stay individual.
- A player's own league varps are only a copy. They are rebuilt from the team on login and when the
  player changes team. Nothing ever copies a player's varps back into the team.
- Level and XP tasks complete for both as soon as either reaches them; levels and XP stay individual.
- Players not on any team behave exactly as before (their progress is their own).

## Shared loot

Teammates share one "observer id" (the engine's group-visibility id for ground items), set when they
log in and whenever an admin changes their team. A drop made for one of them, either a kill drop or
something they dropped, is visible to and can be picked up by the other, and by nobody outside the
team until it turns public after the usual delay. Changing someone's team resends the ground items
around them; items they dropped under their old id stay private to that old id.

## Shared bank

Everyone on a team uses one bank. Booths, chests, bankers and the auto-banking relics all work on it
without knowing, because the team's members are simply given the same bank inventory. Players on no
team keep their own bank.

- **One at a time.** The bank screen addresses items by slot, and slots shift as items come and go,
  so two people inside it would click each other's items. If a teammate has the bank open (or it is
  still tidying itself after they closed it), you get "<name> is using this bank right now." and it
  does not open. This is `BankAccess` in the bank module and applies to any bank inventory that more
  than one player holds. Items auto-banked by a relic still arrive while a teammate is in it.
- **Tab layout.** How many items each tab holds is a per-player setting, but it describes the shared
  slots, so whoever opens the bank is given the layout the last user left (their live one if they
  are online, otherwise the saved one). A selected tab that no longer exists falls back to Main.
- **Saving.** The team bank is saved to `team-banks.json` when someone closes the bank or logs out,
  and by a timer every few seconds when anything changed (relics add items with no bank screen open).
  A rolling backup, at most one per ten minutes, is kept as `team-banks.bak.json`. The normal player
  save also writes the shared bank into each member's own database row, but that copy is never read.
- **Joining and leaving.** A player added to a team mid-session gets the team bank at once (a bank
  screen they had open is closed). A player removed from a team gets a new empty bank of their own;
  the team keeps its items.
- **First member in.** If a team has no saved bank yet and the first member to log in already has
  items in their personal bank, that bank becomes the team bank. Any later member's personal items
  stay in the database but cannot be reached in game (a warning is logged).
- **Known gap.** Opening the bank through the bank tutor's tutorial is covered, but anything new
  that opens `bank_main` directly instead of through `BankAccess` would skip the one-at-a-time check.

## Team instances

`TeamInstances` hands out one private copy of a map area per team for each instance type. Both
partners are sent into the same copy, so they can come and go as long as one stays inside.

- **Alive while anyone is inside.** When everyone has left, the next entry creates a fresh copy, so
  nobody resumes a half-finished run. The engine frees the empty region itself.
- **Solo is fine.** One player can enter alone; a player with no team gets an instance of their own.
- **Death.** The engine respawns a dead player on the normal map (not yet tried inside an instance), so they walk back in to rejoin.
- **Logging out inside.** Instances never survive a logout, so a player saved inside one is moved to
  the first registered type's exit while their account loads (before the client builds its map).
- **Capacity.** The engine has 1,615 small and 420 large region slots. `::instances` shows the
  live ones.

Content adds a type by calling `TeamInstances.register(TeamInstanceType(name, template, entry,
exit))` from a script's `startup()`, then `enter(player, type)` from its entrance and `leave(player)`
from its exit. The template is a normal `RegionTemplate` (see `TestPatchInstance`). Fight Caves and
Inferno will be types of this kind.

| Command (admin) | What it does |
| --- | --- |
| `::instance patch` | Enter your team's copy of the 32x32 test patch of the island |
| `::instance leave` | Leave the instance you are in |
| `::instances` | List live instances and who is in them |

## Protection against accidental loss

- `team-progress.json` changes only when a task/counter changes or on an explicit `::teamreset`,
  which first copies it to `team-progress.<date>-<time>.bak.json`.
- Progress is written to a temp file and moved into place, so a crash cannot leave half a file.
- `::leaguereset` on a team member no longer wipes anything shared: it clears only their relic
  choices and tells them to use `::teamreset`.
- A corrupt `teams.json`, `team-progress.json` or `team-banks.json` stops startup; a bad
  `::teamreload` is refused.
- `team-banks.json` is saved by the same temp-file-and-move step as the others.

## Before the event

1. Stop the server. Delete `.data/league/team-progress.json` (or run `::teamreset all confirm`).
2. Delete `.data/league/team-banks.json` too, or the teams start with their old banks. Players who
   tested should also empty their personal bank first: the first member of a team to log in
   brings their personal bank in as the team bank.
3. Write `.data/league/teams.json`.
4. Relic choices are per player and are not part of a team reset: each player who tested needs
   `::leaguereset` (their own relics) or a fresh account.
5. Start the server, log in, check `::teams` and `::myteam`.

## Not built yet

The leaderboard / ranking screen, and any Fight Caves / Inferno content (waves, bosses, and NPCs that
attack both players at once). The progress file already stores a completion time for every task,
which is what the "fastest to finish" tiebreak will read.

## Ranking, freezing and the scoreboard

**Ranking rules** (`TeamRanking`): more League Points ranks higher; on equal points the team that
reached them first ranks higher (the time of its latest completed task), so if several teams finish
every task the first to finish wins; teams still level share a rank and are listed by name.
`::teams`, the announcements and the scoreboard all use the same ranking.

**Freezing.** `::leaguefreeze` snapshots the standings. Until `::leagueunfreeze`, `::teams`, the
scoreboard and the lead announcements show the snapshot, while teams keep playing and earning
(their real points keep counting). The freeze is saved in `event.json`, so a restart does not lift
it. Freezing twice is refused. A full `::teamreset all confirm` lifts a leftover freeze. On unfreezing,
a lead change that happened meanwhile is announced.

**Scoreboard.** Whenever the standings change the server rewrites `leaderboard.html` (a
self-contained page that refreshes itself every 10 seconds; open it from disk in a browser, for a
second monitor or a stream) and `leaderboard.json` (the same data). They show the official
standings, including a "frozen" banner.

## Announcements

Server-wide lines starting with a blue `League:`. What is announced is read from
`.data/league/announcements.json` (created with these defaults the first time) and can be changed
while the server runs with `::announcereload`. A misspelt setting is rejected, so a typo cannot
silently switch something off.

```json
{
  "leadChanges": true,
  "tasks": {
    "announceTiers": ["elite", "master"],
    "firstCompletionTiers": ["hard"],
    "announceIds": [],
    "firstCompletionIds": []
  },
  "levels": { "announce": [99] },
  "deaths": true,
  "drops": { "objs": [] }
}
```

- **Tasks.** `announceTiers`/`announceIds` announce every completion. `firstCompletionTiers`/
  `firstCompletionIds` announce only the first team to complete each task (and win over a plain
  announcement so nothing is said twice). Tiers are easy, medium, hard, elite, master.
- **Levels.** Any level listed, for any skill, for any player.
- **Deaths.** Every player death.
- **Lead changes.** When a different team takes first place (never while frozen).
- **Drops.** `drops.objs` lists object internal names (such as `abyssal_whip`). **Nothing announces
  drops yet:** the engine has no drop tables, so the code that rolls a drop must call
  `LeagueAnnouncer.drop(player, obj, count)` when it hands out a rare one.

Adding another kind of announcement: add a function to `LeagueAnnouncer` and a message to
`AnnouncementMessages`, a switch to `AnnouncementConfig`, and call it from wherever the thing is
noticed. Tasks publish a `LeagueTaskCompleted` event for this; deaths use a small hook added to the
engine's `PlayerDeath` (`PlayerDeathEvents.Died`); level-ups use the engine's default
"advance stat" script, of which only one can be bound.
