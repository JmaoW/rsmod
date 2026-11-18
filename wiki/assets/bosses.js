// Renders the Bosses index (bosses.html) and the boss pages (boss.html?b=<id>) from data/bosses.js
// (hand-written), data/bosses-generated.js (from the game code) and data/content.js (build and test
// state).
(function () {
  "use strict";

  var H = window.ISLE_BOSSES;
  var G = window.ISLE_BOSSES_GEN || { fights: {}, pages: {}, entrances: {}, settings: {}, dropMultiplier: {} };
  var C = window.ISLE_CONTENT;
  var TESTED_LABEL = { no: "Not tested", issues: "Issues", passed: "Passed" };
  var OSRS = "https://oldschool.runescape.wiki/w/";
  var S = G.settings || {};

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  // Seconds that `ticks` of respawn time take at the league's respawn speed.
  function secs(ticks) {
    var s = ticks * 0.6 / (G.respawnSpeed || 1);
    if (s < 1.5) return "about a second";
    return "about " + Math.round(s) + " seconds";
  }

  var TOKENS = {
    zulrahScales: function () { return S.zulrahScaleMultiplier || 1; },
    toaPaths: function () { return (S.toa && S.toa.pathsRequired) || 4; },
    toaTeamUniques: function () { return (S.toa && S.toa.teamUniquesFromLevel) || 0; },
    nodes: function () { return S.gauntletNodesDeplete === false ? "never run out" : "run out as in the main game"; }
  };

  // Hand-written text may hold <b>, <i> and <a> tags; everything else is escaped.
  function rich(s) {
    return esc(s)
      .replace(/&lt;(\/?)(b|i)&gt;/g, "<$1$2>")
      .replace(/&lt;a href=&quot;([^&]*)&quot;&gt;/g, '<a href="$1">').replace(/&lt;\/a&gt;/g, "</a>")
      .replace(/\{dir:(\w+)\}/g, function (m, k) {
        var e = G.entrances && G.entrances[k];
        return e && e.dir ? e.dir : "on the isle";
      })
      .replace(/\{secs:(\d+)\}/g, function (m, n) { return secs(+n); })
      .replace(/\{(\w+)\}/g, function (m, k) { return TOKENS[k] ? esc(TOKENS[k]()) : m; });
  }

  function paras(list) { return (list || []).map(function (p) { return "<p>" + rich(p) + "</p>"; }).join(""); }

  function el(tag, cls, html) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html != null) e.innerHTML = html;
    return e;
  }

  // ------------------------------------------------------------------ tracker state

  function trackerItem(name) {
    var found = null;
    ((C && C.sections) || []).forEach(function (s) {
      s.items.forEach(function (it) { if (it.name === name) found = it; });
    });
    return found;
  }

  function state(names) {
    var items = (names || []).map(trackerItem).filter(Boolean);
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

  function tiers() {
    return Object.keys(G.dropMultiplier || {}).sort(function (a, b) { return a - b; });
  }

  function boostLine() {
    var t = tiers();
    if (!t.length) return "";
    return "League drop boost: drops marked <b>boosted</b> are " + t.map(function (k) {
      return "<b>" + G.dropMultiplier[k] + "x</b> as likely once you pick a tier " + k + " relic";
    }).join(", ") + " (the higher one replaces the lower).";
  }

  // ------------------------------------------------------------------ index page

  function tile(p, href, extra) {
    var st = state(p.tracker);
    var t = el(p.built === false && !href ? "div" : "a", "skill-tile" + (p.built === false ? " soon" : ""));
    t.href = href || "boss.html?b=" + encodeURIComponent(p.id);
    t.innerHTML = "<h3>" + esc(extra ? extra.name : p.name) + "</h3><p>" + rich(extra ? extra.text : p.summary) + "</p>" +
      '<div class="tile-foot">' + (p.built === false || extra ? '<span class="tag">Not in the league yet</span>' : "") +
      '<span class="tile-state">' + stateLine(extra ? state([extra.tracker]) : st) + "</span></div>";
    return t;
  }

  function renderIndex() {
    document.getElementById("intro").innerHTML = paras(H.intro) + "<p>" + boostLine() + "</p>";
    var root = document.getElementById("groups");
    H.groups.forEach(function (g) {
      var pages = H.pages.filter(function (p) { return p.group === g.id; });
      if (!pages.length) return;
      var sec = el("section", "skill-group");
      sec.appendChild(el("h2", null, esc(g.name)));
      var grid = el("div", "skill-grid");
      pages.forEach(function (p) { grid.appendChild(tile(p)); });
      sec.appendChild(grid);
      root.appendChild(sec);
    });
    var echoes = H.pages.filter(function (p) { return p.echo; });
    if (echoes.length) {
      var sec = el("section", "skill-group");
      sec.appendChild(el("h2", null, "Echo bosses"));
      sec.appendChild(el("p", "muted", "Harder versions of normal bosses, reached with the echo orb the normal boss drops. None is built yet; each is described on its normal boss's page."));
      var grid = el("div", "skill-grid");
      echoes.forEach(function (p) { grid.appendChild(tile(p, "boss.html?b=" + encodeURIComponent(p.id) + "#echo", p.echo)); });
      sec.appendChild(grid);
      root.appendChild(sec);
    }
    document.getElementById("footer").textContent = "Last updated " + H.updated + ". Generated data from " + (G.generated || "?") + ".";
  }

  // ------------------------------------------------------------------ boss page

  function section(id, title, inner) {
    return '<section class="panel" id="' + id + '"><h2>' + esc(title) + "</h2>" + inner + "</section>";
  }

  function dataTable(cols, rows, numCols) {
    var head = cols.map(function (c, i) { return "<th" + (numCols[i] ? ' class="n"' : "") + ">" + c + "</th>"; }).join("");
    var body = rows.map(function (r) {
      return "<tr" + (r.faded ? ' class="faded"' : "") + ">" + r.cells.map(function (v, i) {
        return "<td" + (numCols[i] ? ' class="n"' : "") + ">" + v + "</td>";
      }).join("") + "</tr>";
    }).join("");
    return '<div class="table-wrap"><table class="data"><thead><tr>' + head + "</tr></thead><tbody>" + body + "</tbody></table></div>";
  }

  function statsHtml(stats) {
    if (!stats || !stats.length) return "";
    var rows = stats.map(function (s) {
      return { cells: ["<b>" + esc(s.label || s.name) + "</b>", s.level, s.hitpoints.toLocaleString(), s.attack, s.strength, s.defence, s.ranged, s.magic, s.size + "x" + s.size] };
    });
    var html = dataTable(["", "Combat", "Hitpoints", "Attack", "Strength", "Defence", "Ranged", "Magic", "Size"], rows,
      [false, true, true, true, true, true, true, true, true]);
    var notes = stats.map(function (s) {
      var bits = [];
      if (s.weakness) bits.push("weak to " + esc(s.weakness));
      if (s.attackType) bits.push("attacks with " + esc(s.attackType.toLowerCase()));
      if (s.immune) bits.push("immune to " + esc(s.immune.join(", ")));
      if (s.slayerXp) bits.push(esc(s.slayerXp) + " Slayer XP");
      return bits.length ? "<li><b>" + esc(s.label || s.name) + ":</b> " + bits.join("; ") + "</li>" : "";
    }).join("");
    return html + (notes ? '<ul class="bullets stat-notes">' + notes + "</ul>" : "");
  }

  function fightHtml(f) {
    var gen = G.fights[f.id] || {};
    var html = '<div class="fight" id="f-' + esc(f.id) + '">' + (f.name ? "<h3>" + esc(f.name) + "</h3>" : "");
    html += statsHtml(gen.stats);
    html += paras(f.text);
    if (f.attacks && f.attacks.length) {
      html += "<dl class='defs'>" + f.attacks.map(function (a) {
        return "<dt>" + esc(a.name) + "</dt><dd>" + rich(a.text) + "</dd>";
      }).join("") + "</dl>";
    }
    return html + "</div>";
  }

  function qty(r) {
    var q = r.qty && r.qty !== "1" ? " &times;" + esc(r.qty) : "";
    return q + (r.noted ? ' <span class="muted">(noted)</span>' : "");
  }

  function itemCell(r) {
    return (r.unique ? "<b>" + esc(r.item) + "</b>" : esc(r.item)) + qty(r) +
      (r.boosted ? ' <span class="pill boost">boosted</span>' : "") +
      (r.table ? ' <span class="muted">(a roll on that table)</span>' : "");
  }

  function boostCells(r) {
    return tiers().map(function (t) { return r.boosted ? esc(r["t" + t]) : '<span class="muted">same</span>'; });
  }

  function boostCols() {
    return tiers().map(function (t) { return "Tier " + t + " relic (" + G.dropMultiplier[t] + "x)"; });
  }

  function dropRows(list) {
    return list.map(function (r) { return { cells: [itemCell(r), esc(r.text || "Always")].concat(boostCells(r)) }; });
  }

  function tableHtml(t, open) {
    var nCols = [false, true].concat(tiers().map(function () { return true; }));
    var cols = ["Item", "Chance"].concat(boostCols());
    var body = "";
    if (t.always.length) {
      body += "<p class='k'>Always</p><ul class='loot'>" + t.always.map(function (r) { return "<li>" + itemCell(r) + "</li>"; }).join("") + "</ul>";
    }
    if (t.main.length) {
      body += "<p class='k'>Main table" + (t.rolls > 1 ? ", rolled " + t.rolls + " times a kill" : ", one roll a kill") + "</p>" +
        dataTable(cols, dropRows(t.main), nCols);
    }
    if (t.tertiary.length) {
      body += "<p class='k'>Rolled on their own</p>" + dataTable(cols, dropRows(t.tertiary), nCols);
    }
    var count = t.always.length + t.main.length + t.tertiary.length;
    return "<details class='drop'" + (open ? " open" : "") + "><summary>" + esc(t.label) + " <span class='muted'>" + count +
      " lines</span></summary>" + body + "</details>";
  }

  function uniquesHtml(fights) {
    var rows = [];
    fights.forEach(function (f) {
      ((G.fights[f.id] || {}).uniques || []).forEach(function (u) {
        rows.push({ cells: [(fights.length > 1 ? '<span class="muted">' + esc(f.name) + ":</span> " : "") + "<b>" + esc(u.item) + "</b>" +
          (u.boosted ? ' <span class="pill boost">boosted</span>' : ""), esc(u.base)].concat(boostCells(u)) });
      });
    });
    if (!rows.length) return "";
    return "<h3>Uniques and pets, chance per kill</h3>" +
      dataTable(["Item", "Chance"].concat(boostCols()), rows, [false, true].concat(tiers().map(function () { return true; })));
  }

  function dropsHtml(page, gen) {
    var fights = (page.fights || []).filter(function (f) { return ((G.fights[f.id] || {}).tables || []).length; });
    var html = paras(page.loot);
    if (!fights.length) return html;
    html += "<p class='muted'>" + boostLine() + " Chances are per roll, from the main game's drop tables. Bold items are on the collection log.</p>";
    html += uniquesHtml(fights);
    html += "<h3>Full drop tables</h3>";
    fights.forEach(function (f) {
      G.fights[f.id].tables.forEach(function (t) { html += tableHtml(t, false); });
    });
    return html;
  }

  function entrancesHtml(gen) {
    var es = gen.entrances || [];
    if (!es.length) return "";
    return '<ul class="places">' + es.map(function (e) {
      var where = e.area ? esc(e.areaTitle) + (e.dir ? ", " + esc(e.dir) : "") : "";
      return '<li><div class="place-name">' + esc(e.label) + ' <span class="coords" title="Tile coordinates">' + e.x + ", " + e.z +
        (e.level ? ", level " + e.level : "") + "</span></div>" +
        '<div class="things">' + (e.area ? '<span class="thing">' + where + '</span><span class="pill passed">You can walk there</span>'
          : '<span class="pill no">No walking route found</span>') + "</div></li>";
    }).join("") + "</ul>";
  }

  var MONSTER_NAMES = {
    tztok_jad: "TzTok-Jad", zuk: "TzKal-Zuk", nibbler: "Jal-Nib", bat: "Jal-MejRah", blob: "Jal-Ak", melee: "Jal-ImKot",
    ranger: "Jal-Xil", mager: "Jal-Zek", jad: "JalTok-Jad", jad_triple: "JalTok-Jad"
  };

  function wavesHtml(gen, page) {
    var w = gen.waves;
    if (!w) return "";
    var full = page.id === "fight_caves" ? 63 : 69;
    var txt = "<p>The event is set to the <b>" + esc(w.mode) + "</b> version: <b>" + w.count + " wave" + (w.count === 1 ? "" : "s") + "</b>" +
      (w.mode === "full" ? "." : " (the full version has " + full + ").") + "</p>";
    if (w.list && w.list.length) {
      txt += '<ol class="waves">' + w.list.map(function (wave) {
        var c = {};
        wave.forEach(function (m) { c[m] = (c[m] || 0) + 1; });
        return "<li>" + Object.keys(c).map(function (m) {
          return esc(MONSTER_NAMES[m] || m.replace(/_/g, " ")) + (c[m] > 1 ? " &times;" + c[m] : "");
        }).join(", ") + "</li>";
      }).join("") + "</ol>";
    }
    if (w.count === 1) {
      txt += "<p class='muted'>Only the boss wave is set at the moment, so the fight can be tested quickly.</p>";
    }
    return txt;
  }

  function clogHtml(gen, page) {
    var cats = gen.clog || [];
    if (!cats.length) return "";
    var fromTables = (page.fights || []).some(function (f) { return ((G.fights[f.id] || {}).tables || []).length; });
    return cats.map(function (c) {
      var items = c.items.map(function (n, i) {
        var missing = fromTables && page.built !== false && !c.fromDrops[i];
        return '<span class="thing' + (missing ? " faded" : "") + '"' + (missing ? ' title="Not in these drop tables"' : "") + ">" + esc(n) + "</span>";
      }).join("");
      return (cats.length > 1 ? "<h3>" + esc(c.name) + "</h3>" : "") + '<div class="things">' + items + "</div>";
    }).join("") + (fromTables && page.built !== false && cats.some(function (c) { return c.fromDrops.indexOf(false) !== -1; })
      ? "<p class='muted'>Faded slots don't come from the drop tables above.</p>" : "");
  }

  function echoHtml(page, gen) {
    var e = page.echo;
    var st = state([e.tracker]);
    var html = '<p class="echo-head"><b>' + esc(e.name) + '</b> <span class="tag">Not in the league yet</span> ' + stateLine(st) + "</p>" +
      "<p>" + rich(e.text) + "</p>";
    var t = gen.echo;
    if (t && t.tertiary && t.tertiary.length) {
      html += "<p class='muted'>Planned drops: " + t.tertiary.map(function (r) { return esc(r.item) + " " + esc(r.text); }).join(", ") +
        (t.inherits && t.inherits.length ? ", on top of the normal " + esc(t.inherits[0].join(", ")) + " table." : ".") + "</p>";
    }
    return html;
  }

  function tasksHtml(ts) {
    if (!ts || !ts.length) return "<p>No league tasks for this yet.</p>";
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

  function renderBoss() {
    var root = document.getElementById("boss");
    var id = new URLSearchParams(location.search).get("b");
    var page = H.pages.filter(function (p) { return p.id === id; })[0];
    if (!page) {
      root.innerHTML = '<div class="page-head"><h1>Boss not found</h1><p>There is no boss page called that. <a href="bosses.html">Back to Bosses</a>.</p></div>';
      return;
    }
    document.title = page.name + " · Isle of Souls wiki";
    var gen = G.pages[page.id] || {};
    var built = page.built !== false;
    var st = state(page.tracker);
    var group = H.groups.filter(function (g) { return g.id === page.group; })[0];

    var html = '<p class="crumbs"><a href="bosses.html">Bosses</a> &rsaquo; ' + esc(group ? group.name : "") + " &rsaquo; " + esc(page.name) + "</p>" +
      '<div class="page-head"><h1>' + esc(page.name) + "</h1><p>" + rich(page.summary) + "</p>" +
      '<div class="skill-state">' + (built ? "" : '<span class="tag">Not in the league yet</span>') + stateLine(st) +
      (page.osrs ? '<a href="' + OSRS + encodeURIComponent(page.osrs.replace(/ /g, "_")) + '" target="_blank" rel="noopener">OSRS Wiki: ' + esc(page.osrs) + "</a>" : "") +
      "</div></div>";
    if (page.notice) html += '<div class="notice">' + rich(page.notice) + "</div>";
    if (page.pvp && S.pvp && S.pvp[page.pvp]) {
      html += '<div class="notice"><b>PvP is on here.</b> Other players can attack you on the way in' +
        (page.pvp === "wildernessBossLairs" ? " and inside the lairs" : "") + ".</div>";
    }
    (gen.entrances || []).forEach(function (e) {
      if (!e.area) html += '<div class="notice">No walking route was found to the ' + esc(e.label.toLowerCase()) + " (" + e.x + ", " + e.z + "). It may not be reachable yet.</div>";
    });

    var hasDrops = (page.fights || []).some(function (f) { return ((G.fights[f.id] || {}).tables || []).length; }) || (page.loot && page.loot.length);
    var nav = [["overview", "Overview"]];
    if (page.getThere) nav.push(["there", "Getting there"]);
    if (page.team) nav.push(["team", "Teams and kills"]);
    if (gen.waves) nav.push(["waves", "Waves"]);
    if (page.fights) nav.push(["fight", page.fights.length > 1 ? "The fights" : "The fight"]);
    if (built && hasDrops) nav.push(["drops", "Loot"]);
    if (gen.clog && gen.clog.length) nav.push(["clog", "Collection log"]);
    if (page.echo) nav.push(["echo", "Echo version"]);
    if (page.differences) nav.push(["diff", "Different from OSRS"]);
    if (built) nav.push(["tasks", "League tasks"]);
    html += '<nav class="toc">' + nav.map(function (n) { return '<a href="#' + n[0] + '">' + esc(n[1]) + "</a>"; }).join("") + "</nav>";

    html += section("overview", "Overview", paras(page.intro));
    if (page.getThere) html += section("there", "Getting there", paras(page.getThere) + entrancesHtml(gen));
    if (page.team) html += section("team", "Teams and kills", "<ul class='bullets'>" + page.team.map(function (t) { return "<li>" + rich(t) + "</li>"; }).join("") + "</ul>");
    if (gen.waves) html += section("waves", "Waves", wavesHtml(gen, page));
    if (page.fights) {
      html += section("fight", page.fights.length > 1 ? "The fights" : "The fight", page.fights.map(fightHtml).join(""));
    } else if (!built) {
      var stats = [];
      Object.keys(G.fights).forEach(function (k) { if (k === page.id) stats = G.fights[k].stats; });
      if (stats.length) html += section("fight", "Stats", "<p class='muted'>From the game cache, for when it is built.</p>" + statsHtml(stats));
    }
    if (built && hasDrops) html += section("drops", "Loot", dropsHtml(page, gen));
    if (gen.clog && gen.clog.length) html += section("clog", "Collection log", clogHtml(gen, page));
    if (page.echo) html += section("echo", "Echo version", echoHtml(page, gen));
    if (page.differences) {
      html += section("diff", "Different from OSRS", "<ul class='bullets'>" + page.differences.map(function (d) { return "<li>" + rich(d) + "</li>"; }).join("") + "</ul>");
    }
    if (built) html += section("tasks", "League tasks", tasksHtml(gen.tasks));
    html += '<p class="footer">Stats, drops, places and tasks generated from the game code on ' + esc(G.generated || "?") +
      ". Text last edited " + esc(H.updated) + ".</p>";
    root.innerHTML = html;
    if (location.hash) {
      var target = document.getElementById(location.hash.slice(1));
      if (target) target.scrollIntoView();
    }
  }

  if (!H) return;
  if (document.getElementById("groups")) renderIndex();
  if (document.getElementById("boss")) renderBoss();
})();
