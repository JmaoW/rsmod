"""Writes the skill icons for the wiki's Skills pages, from the vanilla cache.

    python tools/wiki/gen_skill_icons.py

Each skill's icon is the 25 x 25 sprite the client shows in the Skills tab (sprite groups 197 to 221),
saved as wiki/assets/skills/<skill>.png. The page picks them by skill id (wiki/data/skills.js), with
`icons: [...]` for a page that covers several skills (Melee shows Attack, Strength and Defence).
"""
import importlib.util
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
# Loaded by path, like gen_relics.py: tools/spawns has a module of the same name as sprites' js5.
_spec = importlib.util.spec_from_file_location("cs2_sprites", os.path.join(ROOT, "tools", "cs2", "sprites.py"))
sprites = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(sprites)

# Skills-tab sprite group for each skill (checked by eye against the cache, rev 233).
SPRITES = {
    "attack": 197, "strength": 198, "defence": 199, "ranged": 200, "prayer": 201, "magic": 202,
    "hitpoints": 203, "agility": 204, "herblore": 205, "thieving": 206, "crafting": 207,
    "fletching": 208, "mining": 209, "smithing": 210, "fishing": 211, "cooking": 212,
    "firemaking": 213, "woodcutting": 214, "runecraft": 215, "slayer": 216, "farming": 217,
    "hunter": 220, "construction": 221,
}


def main():
    out = os.path.join(ROOT, "wiki", "assets", "skills")
    os.makedirs(out, exist_ok=True)
    for name, group in SPRITES.items():
        w, h, rgba = sprites.frames(group)[0]
        sprites.write_png(os.path.join(out, name + ".png"), w, h, rgba)
    print(f"{len(SPRITES)} skill icons written to {os.path.relpath(out, ROOT)}")


if __name__ == "__main__":
    main()
