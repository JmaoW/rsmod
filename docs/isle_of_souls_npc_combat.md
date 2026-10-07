# Isle of Souls: how npcs attack (for boss scripts)

Code: `api/combat/combat-scripts` (`NpcAttack`, `NpcAttacks`, `NpcTargeting`). The tests are in
`NpcAttacksTest`.

## What exists

- **Normal monsters** attack with melee only, using their cache stats (`NvPCombat`), and keep doing
  so. Nothing about them changed.
- **Everything is multi-combat.** The check `combat_multiway_everywhere` in `Constants.kt` makes
  every area multi-combat, so any number of monsters can attack one player and any number of
  players can attack one monster.
- **Bosses** use the new pieces below to attack with ranged, magic or melee, with projectiles, and
  against more than one player.

## The three pieces

**`NpcAttack`** describes one attack (a value you create once and reuse):

```kotlin
val jadMage = NpcAttack(
    style = NpcAttackStyle.Magic,
    anim = seqs.jad_magic,                 // optional: defaults to the npc's attack_anim param
    projectile = spotanims.jad_mage_ball,  // the projectile graphic...
    projanim = projanims.magic_spell,      // ...and how it flies (needs both, or neither)
    impactSpot = spotanims.jad_mage_hit,   // optional: played on the target when it lands
    maxHit = 97,                           // optional: fixed max hit instead of the formula
)
```

`alwaysHits = true` skips the accuracy roll. `meleeType` picks stab, slash or crush for melee.

**`NpcAttacks`** performs it:

- `perform(npc, primary, attack)` attacks the players that the npc's targeting style picks, given
  the player it is fighting. This is what boss scripts call.
- `attack(npc, target, attack)` attacks exactly one player.

Each attack plays the animation (once), launches a projectile at each target, rolls accuracy and
damage separately for each target, applies protection prayers, and delays the hit until the
projectile lands. It does **not** decide when the npc attacks again: set `npc.actionDelay` from the
attack rate in your script, as the melee code does.

**`NpcTargeting`** holds each npc type's `TargetingStyle`:

| Style | What the attack does |
| --- | --- |
| `One` (default) | Hits only the player it is fighting. |
| `Alternating` | Hits one player, taking turns between everyone in range. |
| `All` | Hits every player in range at once. TzTok-Jad uses this: one projectile per teammate, each with its own roll. |

A boss script sets the style once at startup: `targeting.setStyle(npcs.jad, TargetingStyle.All)`.
"In range" is the same level and within 32 tiles, and allowed by every `TargetScope`. The isle
registers one (`InstanceTargetScope`) so a npc inside a team instance only attacks players in that
same instance, never the copy next to it.

## Writing a boss (outline)

1. Create the npc type for it if the cache has none (see below).
2. In a `PluginScript`, set its `TargetingStyle` and register its attack scripts with
   `onAiOpPlayer2`/`onAiApPlayer2` for that npc type (ap is for attacks from a distance).
3. In the attack handler, pick an `NpcAttack` (Jad chooses mage or ranged at random) and call
   `attacks.perform(npc, target, attack)`, then set `npc.actionDelay`.
4. Add a drop table (`isle_of_souls_drops.md`) and kill-count tasks.

## Not built yet

- **Phases and mechanics** (Jad's healers, prayer switching telegraphs, Zuk's shield): per boss.
- **A shared boss framework** for arenas and waves (the Fight Caves are the first user).
- **Echo boss npcs.** This cache is older than the wiki's npc list, so the echo bosses' own npc
  types (new ids from 15,000 up) are not in it; only a few echo parts exist (`black_dragon_echokbd`,
  `dagannoth_water_creature_echo`). Their drop tables are imported and wait for an npc to attach
  to (see `isle_of_souls_drops.md`); the npc types themselves have to be made.
- **NPC vs NPC combat** (thralls, the Guardian relic): the formulas exist but there is no attack
  service for it yet.
