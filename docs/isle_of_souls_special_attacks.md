# Special attacks

Status: built 2026-10-22 overnight. All 268 weapons in the cache's `sa_energy_requirements` enum have a special attack
(checked by `SpecialAttackCoverageTest`). Tests pass (special-attacks 51, dragonfire 28, isle combat-flow 5 + coverage).
**Not seen in a client. Needs `./gradlew packCache` is NOT required** (no new cache edits), but the animations/graphics are
best cache matches (ids from TSPS, checked against rev 233 symbol names).

## Where things are
- `content/other/special-attacks`: handlers by family (`melee/`, `ranged/`, `magic/`, `boost/`), `support/` (bases, DoT, drains,
  prayer lock, damage guards, soul stacks, weapon poison). `tools/specials/gen_refs.py` generates `SpecialObjs.kt` (every
  special weapon) and `SpecialFxRefs.kt` (fx ids in `fx_ids.json`).
- `content/other/dragonfire`: shield charges + shield special (dragonfire shield, ward, ancient wyvern shield).
- `isle-of-souls/revenant/RevenantSpecials.kt`: Swarm, Bear Down, Condemn. `dungeons/SpecialCureScript.kt`: poison/venom cure.
- Engine: `SpecialAttack.Shield` + `registerShield`; `PlayerAttackManager` (defenceFactor, ignoreProtection, queueTypelessHit,
  `PlayerAttackEvents.Hit`, `AttackBuffs`); `PlayerHitModifiers` (extra player hit modifiers); combat scripts now run a staff's
  special whichever style it is held in (bash or autocast spell).

## Review these first (guesses / deviations)
- Numbers not in the cache text are guesses: Snapshot accuracy 0.7 (TSPS has 10/7), Nightmare staff max hit `magic/3+11`, Eye of
  Ayak base 30, Ursine bleed (half the damage), Swarm arrows 0.5x, Sol Slam (no energy, prayer bonus %), Soulreaper stacks
  (+1 per hit taken, 5 max, 33 ticks), Retainer (stuns a half-health juvenile/juvinate 100 ticks), Entice (2x accuracy, 10% per ally).
- Wiki-limited specials work anywhere: Brine sabre (underwater), Keris partisans (Tombs of Amascut), Dawnbringer/Pulsate (Verzik).
- Moon-set weapons (blue/blood/eclipse) need the full set; their set effects are not built.
- Enchanted bolt effects are built (2026-10-22, `docs/isle_of_souls_enchanted_bolts.md`): Armadyl Eye doubles the chance of the bolt's effect and Evoke makes it work on a hit. Annihilate's bolts and the ballistas' javelins roll no bolt effect.
- Toxic Siphon needs a loaded blowpipe (darts+scales in varobj bits); the blowpipe itself still has no normal attack or loading.
- Poison on players is the player's own poison (orb, antipoison protection and cure, saved on log-out), whoever poisons them (`PlayerPoisonEvents.Apply`, answered by `SicknessScript`); NPC poison follows the wiki. Weapon poison: 1 in 4 on a damaging hit, 4/5/6
  (melee/knives/javelins) or 2/3/4 (arrows/bolts/darts). No venom.
- Crystalline Severance only locks a player target's protection prayers; the Hunllef's own prayer is not touched
  (`GauntletHunllefScript` `ModifyAny` would be the hook).
- Specialist relic (tier 8) untouched, as agreed.
- Spotless was not run; the sandbox ran `packCache` only on a throwaway copy.
