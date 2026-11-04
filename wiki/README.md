# Isle of Souls wiki

The player wiki for the Isle of Souls league. Plain HTML, no build step.

- `index.html`: home page
- `tracker.html`: content tracker (every planned item, % built, tested state)
- `skills.html`, `skill.html?s=<id>`: the Skills section, one template for every skill page
- `data/content.js`: the tracker's data, the file to edit
- `data/skills.js`: the hand-written half of the skill pages (overview, gear, relics, place names)
- `data/skills-generated.js`: the generated half (level tables, where things are, league tasks).
  Do not edit; re-run `python tools/wiki/gen_skills.py` after changing a skill
- `assets/`: shared styles and scripts

## Updating the tracker

Edit `data/content.js`. To mark something tested, set its `tested` to `"passed"`, or to `"issues"`
and add an `issue: "what went wrong"` line. Change `updated` at the top when you edit.

## Viewing it

Double-click `index.html`, or serve the folder:

```bash
python -m http.server 8765 --directory wiki
```

then open http://localhost:8765. It can also be hosted as-is on GitHub Pages or any static host.
