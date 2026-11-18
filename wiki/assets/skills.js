// Renders the Skills index (skills.html) and the skill pages (skill.html?s=<id>) from
// data/skills.js (hand-written), data/skills-generated.js (from the game code) and data/content.js
// (build and test state).
(function () {
  "use strict";

  var H = window.ISLE_SKILLS;
  var G = window.ISLE_SKILLS_GEN || { skills: {}, areas: {}, entrances: {} };
  var C = window.ISLE_CONTENT;
  var TESTED_LABEL = { no: "Not tested", issues: "Issues", passed: "Passed" };
  var OSRS = "https://oldschool.runescape.wiki/w/";

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  // Hand-written text may hold a few tags; everything else is escaped.
  // {dir:<key>} becomes where that landmark is ("about 40 tiles south of the lobby"), from the game code.
  function rich(s) {
    return esc(s).replace(/&lt;(\/?)(b|i)&gt;/g, "<$1$2>").replace(/\{dir:(\w+)\}/g, function (m, k) {
      var e = G.entrances && G.entrances[k];
      return e && e.dir ? e.dir : "on the isle";
    });
  }

  function el(tag, cls, html) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html != null) e.innerHTML = html;
    return e;
  }

  // Skills-tab icons from the cache (tools/wiki/gen_skill_icons.py), one per skill the page covers.
  function icons(skill) {
    return '<span class="skill-icons">' + (skill.icons || [skill.id]).map(function (n) {
      return '<img src="assets/skills/' + esc(n) + '.png" alt="" width="25" height="25">';
    }).join("") + "</span>";
  }

  // ------------------------------------------------------------------ tracker state

  function trackerItems(skill) {
    if (!C || !C.sections) return [];
    var out = [];
    C.sections.forEach(function (s) {
      s.items.forEach(function (it) {
        if ((skill.tracker || []).indexOf(it.name) !== -1) out.push(it);
      });
    });
    return out;
  }

  function state(skill) {
    var items = trackerItems(skill);
    if (!items.length) return null;
    var built = Math.round(items.reduce(function (n, it) { return n + it.built; }, 0) / items.length);
    var tested = items.some(function (it) { return it.tested === "issues"; }) ? "issues"
      : items.every(function (it) { return it.tested === "passed"; }) ? "passed" : "no";
    return { built: built, tested: tested };
  }

  function bar(pct) {
    return '<span class="bar"><span class="' + (pct >= 100 ? "done" : "") + '" style="width:' + pct + '%"></span></span>';
  }

  function stateLine(st) {
    if (!st) return "";
    return '<span class="built">' + bar(st.built) + '<span class="num">' + st.built + "%</span></span>" +
      '<span class="pill ' + st.tested + '">' + TESTED_LABEL[st.tested] + "</span>";
  }

  // ------------------------------------------------------------------ league rates

  function ratesHtml(compact) {
    var xp = G.xp || {};
    var tiers = Object.keys(xp.byRelicTier || {}).sort(function (a, b) { return a - b; });
    var steps = tiers.map(function (t) { return "<b>" + xp.byRelicTier[t] + "x</b> once you pick a tier " + t + " relic"; });
    var line = "League XP multiplier: <b>" + (xp.base || 5) + "x</b> from the start, " + steps.join(", ") + ".";
    if (xp.combatBonus) {
      line += " Combat skills get <b>" + xp.combatBonus + "x</b> on top of that once you pick a tier " + xp.combatBonusTier + " relic.";
    }
    var speed = G.respawnSpeed && G.respawnSpeed !== 1
      ? "<p>Rocks, trees, stalls, ground items and monsters come back <b>" + G.respawnSpeed + "x</b> faster than in the main game.</p>" : "";
    return (compact ? "" : "<h2>League rates</h2>") + "<p>" + line + "</p>" + speed;
  }

  // ------------------------------------------------------------------ index page

  function renderIndex() {
    document.getElementById("rates").innerHTML = ratesHtml(false);
    var root = document.getElementById("groups");
    H.groups.forEach(function (g) {
      var skills = H.skills.filter(function (s) { return s.group === g.id; });
      if (!skills.length) return;
      var sec = el("section", "skill-group");
      sec.appendChild(el("h2", null, esc(g.name)));
      var grid = el("div", "skill-grid");
      skills.forEach(function (s) {
        var st = state(s);
        var tile = el(s.page ? "a" : "div", "skill-tile" + (s.page ? "" : " soon"));
        if (s.page) tile.href = "skill.html?s=" + encodeURIComponent(s.id);
        tile.innerHTML = "<h3>" + icons(s) + esc(s.name) + "</h3><p>" + rich(s.summary) + "</p>" +
          '<div class="tile-foot">' + (s.page ? "" : '<span class="tag">' + esc(s.soon || "Page coming") + "</span>") +
          '<span class="tile-state">' + stateLine(st) + "</span></div>";
        grid.appendChild(tile);
      });
      sec.appendChild(grid);
      root.appendChild(sec);
    });
    document.getElementById("footer").textContent = "Last updated " + H.updated + ".";
  }

  // ------------------------------------------------------------------ skill page

  function section(id, title, inner) {
    return '<section class="panel" id="' + id + '"><h2>' + esc(title) + "</h2>" + inner + "</section>";
  }

  function isNum(v) { return typeof v === "number"; }

  function table(t) {
    var reach = t.reachable;
    var head = t.cols.map(function (c, i) {
      var num = t.rows.length && isNum(t.rows[0][i]);
      return "<th" + (num ? ' class="n"' : "") + ">" + esc(c) + "</th>";
    }).join("");
    if (reach) head += "<th>Can you reach it?</th>";
    var body = t.rows.map(function (r, ri) {
      var cells = r.map(function (v) { return "<td" + (isNum(v) ? ' class="n"' : "") + ">" + esc(v) + "</td>"; }).join("");
      if (reach) cells += "<td>" + (reach[ri] ? '<span class="pill passed">Yes</span>' : '<span class="pill no">Not yet</span>') + "</td>";
      return "<tr" + (reach && !reach[ri] ? ' class="faded"' : "") + ">" + cells + "</tr>";
    }).join("");
    return (t.note ? '<p class="muted">' + rich(t.note) + "</p>" : "") +
      '<div class="table-wrap"><table class="data"><thead><tr>' + head + "</tr></thead><tbody>" + body + "</tbody></table></div>";
  }

  function inBox(b, x, z) { return x >= b[0] && z >= b[1] && x <= b[2] && z <= b[3]; }

  function reachText(skill, area) {
    var t = (skill.reach && skill.reach[area] != null) ? skill.reach[area]
      : (H.reach[area] != null ? H.reach[area] : H.reach._dungeon);
    if (!t) return "";
    var e = G.entrances[area];
    return t.replace("{dir}", e && e.dir ? e.dir : "on the isle");
  }

  function placesHtml(skill, gen) {
    if (!gen.places || !gen.places.length) {
      return "<p>Nothing for this skill stands anywhere a player can reach yet.</p>";
    }
    var areas = [];
    var byArea = {};
    gen.places.forEach(function (p) {
      if (!byArea[p.area]) { byArea[p.area] = []; areas.push(p.area); }
      var named = (skill.placeNames || []).filter(function (n) { return n.area === p.area && inBox(n.box, p.x, p.z); })[0];
      var key = named ? "n:" + named.name : "p:" + p.x + "," + p.z;
      var row = byArea[p.area].filter(function (r) { return r.key === key; })[0];
      if (!row) {
        row = {
          key: key, named: named, x: p.x, z: p.z, level: p.level, things: {},
          name: named ? named.name : (p.dir ? p.dir.charAt(0).toUpperCase() + p.dir.slice(1) : "")
        };
        byArea[p.area].push(row);
      }
      p.things.forEach(function (t) { row.things[t.name] = (row.things[t.name] || 0) + t.count; });
    });
    return areas.map(function (a) {
      var how = reachText(skill, a);
      var rows = byArea[a].map(function (r) {
        var things = Object.keys(r.things).sort().map(function (n) {
          return '<span class="thing">' + esc(n) + ' <span class="muted">&times;' + r.things[n] + "</span></span>";
        }).join("");
        var coords = '<span class="coords" title="Tile coordinates, for the map later">' + r.x + ", " + r.z +
          (r.level ? ", level " + r.level : "") + "</span>";
        return "<li>" + (r.name ? '<div class="place-name">' + esc(r.name) + " " + coords + "</div>" : "") +
          (r.named && r.named.text ? '<p class="place-text">' + rich(r.named.text) + "</p>" : "") +
          '<div class="things">' + things + (r.name ? "" : coords) + "</div></li>";
      }).join("");
      return '<div class="area"><h3>' + esc(G.areas[a] || a) + "</h3>" +
        (how ? '<p class="how">' + rich(how) + "</p>" : "") + '<ul class="places">' + rows + "</ul></div>";
    }).join("");
  }

  function lootList(rows, pct) {
    return rows.map(function (r) {
      return "<li>" + esc(r.item) + (r.amount && r.amount !== "1" ? " &times;" + esc(r.amount) : "") +
        (pct && r.pct != null ? ' <span class="muted">' + r.pct + "%</span>" : "") + "</li>";
    }).join("");
  }

  function thievingLoot(gen) {
    var targets = gen.targets.map(function (t) {
      var parts = "";
      if (t.pouch) parts += "<p>Coins come as <b>" + esc(t.pouch.name) + "</b> (" + esc(t.pouch.coins) + " coins each).</p>";
      if (t.fireCape) parts += "<p>Needs a fire cape, worn or carried.</p>";
      if (t.always.length) parts += "<p class='k'>Always</p><ul class='loot'>" + lootList(t.always, false) + "</ul>";
      if (t.rare.length) parts += "<p class='k'>Rare, rolled first</p><ul class='loot'>" + lootList(t.rare, true) + "</ul>";
      if (t.table.length) parts += "<p class='k'>One of</p><ul class='loot'>" + lootList(t.table, true) + "</ul>";
      parts += "<p class='muted'>Shown as: " + esc(t.npcs.join(", ")) + "</p>";
      return "<details class='drop'><summary>" + esc(t.name) + " <span class='muted'>level " + t.level + "</span></summary>" + parts + "</details>";
    }).join("");
    var stalls = gen.stalls.map(function (s) {
      return "<details class='drop'><summary>" + esc(s.name) + " <span class='muted'>level " + s.level + "</span></summary>" +
        "<p class='k'>One of</p><ul class='loot'>" + lootList(s.loot, true) + "</ul></details>";
    }).join("");
    return "<h3>Pickpocket loot</h3>" + targets + "<h3>Stall loot</h3>" + stalls;
  }

  function miningGems(gen) {
    var g = gen.gems;
    if (!g) return "";
    return "<p>1 in " + g.chance + " ores also gives an uncut gem: " +
      g.gems.map(function (x) { return esc(x.gem.replace("Uncut ", "")) + " " + x.share + "%"; }).join(", ") + ".</p>";
  }

  function tasksHtml(gen) {
    var ts = gen.tasks || [];
    if (!ts.length) return "<p>No league tasks for this skill.</p>";
    var total = ts.reduce(function (n, t) { return n + (t.points || 0); }, 0);
    var dead = ts.filter(function (t) { return !t.live; }).length;
    var rows = ts.map(function (t) {
      return "<tr" + (t.live ? "" : ' class="faded"') + '><td><span class="tier ' + t.tier.toLowerCase() + '">' + esc(t.tier) +
        "</span></td><td><b>" + esc(t.name) + "</b>" + (t.desc && t.desc.toLowerCase() !== t.name.toLowerCase() ? '<div class="muted">' + esc(t.desc) + "</div>" : "") +
        (t.live ? "" : ' <span class="pill no">Can\'t be done yet</span>') + '</td><td class="n">' + (t.points || "") + "</td></tr>";
    }).join("");
    return "<p>" + ts.length + " tasks worth " + total.toLocaleString() + " points." +
      (dead ? " " + dead + " of them can't be completed on the server yet." : "") + "</p>" +
      '<div class="table-wrap"><table class="data"><thead><tr><th>Tier</th><th>Task</th><th class="n">Points</th></tr></thead><tbody>' +
      rows + "</tbody></table></div>";
  }

  function renderSkill() {
    var root = document.getElementById("skill");
    var id = new URLSearchParams(location.search).get("s");
    var skill = H.skills.filter(function (s) { return s.id === id; })[0];
    if (!skill || !skill.page) {
      root.innerHTML = '<div class="page-head"><h1>' + (skill ? esc(skill.name) : "Skill not found") + "</h1><p>" +
        (skill ? "This page hasn't been written yet." : "There is no skill page called that.") +
        ' <a href="skills.html">Back to Skills</a>.</p></div>';
      return;
    }
    document.title = skill.name + " · Isle of Souls wiki";
    var gen = G.skills[skill.id] || {};
    var st = state(skill);

    var html = '<p class="crumbs"><a href="skills.html">Skills</a> &rsaquo; ' + esc(skill.name) + "</p>" +
      '<div class="page-head"><h1>' + icons(skill) + esc(skill.name) + "</h1><p>" + rich(skill.summary) + "</p>" +
      '<div class="skill-state">' + stateLine(st) +
      (skill.osrs ? '<a href="' + OSRS + encodeURIComponent(skill.osrs) + '" target="_blank" rel="noopener">OSRS Wiki: ' + esc(skill.osrs) + "</a>" : "") +
      "</div></div>";
    if (skill.notice) html += '<div class="notice">' + rich(skill.notice) + "</div>";

    var nav = [["overview", "Overview"], ["where", "Where to train"]];
    (gen.tables || []).forEach(function (t) { nav.push(["t-" + t.id, t.title]); });
    if (gen.targets) nav.push(["loot", "Loot"]);
    if (skill.gear && skill.gear.length) nav.push(["gear", "Gear and shops"]);
    if (skill.relics && skill.relics.length) nav.push(["relics", "Relics"]);
    if (skill.differences && skill.differences.length) nav.push(["diff", "Different from OSRS"]);
    nav.push(["tasks", "League tasks"]);
    html += '<nav class="toc">' + nav.map(function (n) { return '<a href="#' + n[0] + '">' + esc(n[1]) + "</a>"; }).join("") + "</nav>";

    html += section("overview", "Overview", (skill.intro || []).map(function (p) { return "<p>" + rich(p) + "</p>"; }).join(""));
    var where = (skill.where || []).map(function (p) { return "<p>" + rich(p) + "</p>"; }).join("");
    if (!skill.where || (gen.places && gen.places.length)) where += placesHtml(skill, gen);
    html += section("where", "Where to train", where);
    (gen.tables || []).forEach(function (t) {
      var after = "";
      if (skill.id === "mining" && t.id === "rocks") after = miningGems(gen);
      if (gen.lightChance && t.id === "logs") {
        after = "<p>Every log is equally hard to light: at level 1 a try works " + gen.lightChance.level1 +
          "% of the time, and from level " + gen.lightChance.certainFrom + " the first try always works.</p>";
      }
      html += section("t-" + t.id, t.title, table(t) + after);
    });
    if (gen.targets) html += section("loot", "Loot", thievingLoot(gen));
    if (skill.gear && skill.gear.length) {
      html += section("gear", "Gear and shops", "<dl class='defs'>" + skill.gear.map(function (g) {
        return "<dt>" + esc(g.name) + "</dt><dd>" + rich(g.text) + "</dd>";
      }).join("") + "</dl>");
    }
    if (skill.relics && skill.relics.length) {
      html += section("relics", "Relics", "<dl class='defs'>" + skill.relics.map(function (r) {
        var anchor = r.name.toLowerCase().replace(/'/g, "").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
        return '<dt><a href="relics.html#' + anchor + '">' + esc(r.name) + '</a> <span class="muted">tier ' + r.tier +
          "</span></dt><dd>" + rich(r.text) + "</dd>";
      }).join("") + "</dl>");
    }
    if (skill.differences && skill.differences.length) {
      html += section("diff", "Different from OSRS", "<ul class='bullets'>" +
        skill.differences.map(function (d) { return "<li>" + rich(d) + "</li>"; }).join("") + "</ul>");
    }
    html += section("tasks", "League tasks", tasksHtml(gen));
    html += '<p class="footer">Tables, places and tasks generated from the game code on ' + esc(G.generated || "?") +
      ". Text last edited " + esc(H.updated) + ".</p>";
    root.innerHTML = html;
  }

  if (!H) return;
  if (document.getElementById("groups")) renderIndex();
  if (document.getElementById("skill")) renderSkill();
})();
