// Renders the Relics page (relics.html) from data/relics.js (hand-written), data/relics-generated.js
// (thresholds, multipliers and icons, from the game code and event settings), data/skills.js (which
// skill pages mention each relic) and data/content.js (build and test state).
(function () {
  "use strict";

  var H = window.ISLE_RELICS;
  var G = window.ISLE_RELICS_GEN;
  var S = window.ISLE_SKILLS;
  var C = window.ISLE_CONTENT;
  var TESTED_LABEL = { no: "Not tested", issues: "Issues", passed: "Passed" };
  var COMBAT = "Attack, Strength, Defence, Hitpoints, Ranged, Prayer and Magic";

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  // Hand-written text may hold <b>, <i> and <a href="..."> tags; everything else is escaped.
  function rich(s) {
    return esc(s)
      .replace(/&lt;(\/?)(b|i)&gt;/g, "<$1$2>")
      .replace(/&lt;a href=&quot;([^&]*)&quot;&gt;/g, '<a href="$1">').replace(/&lt;\/a&gt;/g, "</a>");
  }

  function slug(name) {
    return name.toLowerCase().replace(/'/g, "").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
  }

  function num(n) { return Number(n).toLocaleString("en-GB"); }

  function mult(x) { return (Math.round(x * 100) / 100) + "x"; }

  // ------------------------------------------------------------------ data joins

  function relicInfo(r) {
    var h = (H.relics && H.relics[r.name]) || {};
    return { gen: r, hand: h, name: h.name || r.name, id: slug(h.name || r.name) };
  }

  function trackerItem(name) {
    if (!C || !C.sections || !name) return null;
    for (var i = 0; i < C.sections.length; i++) {
      var hit = C.sections[i].items.filter(function (it) { return it.name === name; })[0];
      if (hit) return hit;
    }
    return null;
  }

  function stateLine(it) {
    if (!it) return "";
    return '<span class="built"><span class="bar"><span class="' + (it.built >= 100 ? "done" : "") + '" style="width:' + it.built +
      '%"></span></span><span class="num">' + it.built + "%</span></span>" +
      '<span class="pill ' + it.tested + '">' + TESTED_LABEL[it.tested] + "</span>";
  }

  // Skill pages that list this relic (data/skills.js), plus any skill ids the relic names itself.
  function helpsWith(info) {
    if (info.hand.helps === "all") return '<a href="skills.html">Every skill</a>';
    if (!S || !S.skills) return "";
    var extra = Array.isArray(info.hand.helps) ? info.hand.helps : [];
    var names = [info.name, info.gen.name];
    var skills = S.skills.filter(function (s) {
      return s.page && (extra.indexOf(s.id) !== -1 ||
        (s.relics || []).some(function (r) { return names.indexOf(r.name) !== -1; }));
    });
    return skills.map(function (s) {
      return '<a href="skill.html?s=' + encodeURIComponent(s.id) + '#relics">' + esc(s.name) + "</a>";
    }).join(", ");
  }

  // ------------------------------------------------------------------ passives

  // The XP and drop rate a player has once they have picked a relic from [tier] (and every tier before).
  function ratesAt(tier) {
    var xp = G.xp.base, drop = 1;
    Object.keys(G.xp.byRelicTier).forEach(function (t) { if (+t <= tier) xp = G.xp.byRelicTier[t]; });
    Object.keys(G.drops).forEach(function (t) { if (+t <= tier) drop = G.drops[t]; });
    return { xp: xp, drop: drop, combat: tier >= G.xp.combatBonusTier ? G.xp.combatBonus : 1 };
  }

  function passiveLines(tier) {
    var lines = [];
    var now = ratesAt(tier), before = tier > 1 ? ratesAt(tier - 1) : null;
    if (!before) lines.push("League XP rate: <b>" + mult(now.xp) + "</b>.");
    else if (now.xp !== before.xp) lines.push("League XP rate goes up from " + mult(before.xp) + " to <b>" + mult(now.xp) + "</b>.");
    if (tier === G.xp.combatBonusTier && G.xp.combatBonus !== 1) {
      lines.push("Combat XP (" + COMBAT + ") is <b>" + mult(G.xp.combatBonus) + "</b> on top of the league rate.");
    }
    if (now.drop !== (before ? before.drop : 1)) {
      lines.push("Rare monster drops are <b>" + mult(now.drop) + "</b> as likely" +
        (before && before.drop !== 1 ? " (instead of " + mult(before.drop) + ")" : "") + ".");
    }
    return lines.concat(((H.passives || {})[String(tier)] || []).map(rich));
  }

  // ------------------------------------------------------------------ page parts

  function overview(byTier) {
    var rows = byTier.map(function (relics, i) {
      var tier = i + 1, now = ratesAt(tier), before = tier > 1 ? ratesAt(tier - 1) : null;
      function cell(v, prev, extra) {
        var changed = !before || v !== prev;
        return '<td class="n">' + (changed ? "<b>" + mult(v) + "</b>" : mult(v)) + (extra || "") + "</td>";
      }
      var combat = tier === G.xp.combatBonusTier && G.xp.combatBonus !== 1
        ? '<div class="muted">' + mult(now.xp * G.xp.combatBonus) + " combat</div>" : "";
      return "<tr><td><a href=\"#tier-" + tier + "\">Tier " + tier + "</a></td>" +
        '<td class="n">' + num(G.thresholds[i]) + "</td>" +
        cell(now.xp, before && before.xp, combat) +
        cell(now.drop, before && before.drop) +
        "<td>" + relics.map(function (info) {
          return '<a href="#' + info.id + '"' + (info.hand.soon ? ' class="soon-link"' : "") + ">" + esc(info.name) + "</a>";
        }).join(", ") + "</td></tr>";
    }).join("");
    var t = G.tasks;
    return '<section class="panel" id="tiers"><h2>Tiers at a glance</h2>' +
      '<div class="table-wrap"><table class="data"><thead><tr><th>Tier</th><th class="n">Team points</th>' +
      '<th class="n">XP rate</th><th class="n">Rare drops</th><th>Relics</th></tr></thead><tbody>' + rows + "</tbody></table></div>" +
      '<p class="muted">XP rate and rare drops are what you have once you pick a relic from that tier; bold marks where they go up. ' +
      (t ? "The task list holds " + num(t.count) + " tasks worth " + num(t.points) + " points; " + num(t.liveCount) +
        " of them, worth " + num(t.livePoints) + ", can be completed on the server today. " : "") +
      "The points needed for each tier may still change before the event.</p></section>";
  }

  function relicCard(info) {
    var h = info.hand, r = info.gen;
    var tr = trackerItem(h.tracker);
    var head = '<div class="relic-head">' +
      (r.icon ? '<img class="relic-icon" src="' + esc(r.icon) + '" alt="" width="64" height="64">' : "") +
      "<div><h3>" + esc(info.name) + "</h3>" + (h.summary ? '<p class="relic-sum">' + rich(h.summary) + "</p>" : "") +
      '<div class="relic-state">' + (h.soon ? '<span class="tag">' + esc(h.soon) + "</span>" : stateLine(tr)) + "</div></div></div>";
    var body = "";
    if (h.item) body += '<p class="relic-item"><span class="k">You get</span> <b>' + esc(h.item.name) + "</b>. " + rich(h.item.text) + "</p>";
    if (h.effects && h.effects.length) {
      body += '<ul class="bullets">' + h.effects.map(function (e) { return "<li>" + rich(e) + "</li>"; }).join("") + "</ul>";
    }
    if (h.use && h.use.length) {
      body += '<p class="relic-use"><span class="k">How to use</span> ' + h.use.map(rich).join(" ") + "</p>";
    }
    (h.notes || []).forEach(function (n) { body += '<p class="relic-note">' + rich(n) + "</p>"; });
    var helps = h.soon ? "" : helpsWith(info);
    if (helps) body += '<p class="relic-helps"><span class="k">Skill pages</span> ' + helps + "</p>";
    return '<article class="relic' + (h.soon ? " soon" : "") + '" id="' + info.id + '">' + head + '<div class="relic-body">' + body + "</div></article>";
  }

  function tierSection(relics, i) {
    var tier = i + 1;
    var lines = passiveLines(tier);
    var passives = '<div class="passives"><span class="k">Tier passive</span>' +
      (lines.length ? '<ul class="bullets">' + lines.map(function (l) { return "<li>" + l + "</li>"; }).join("") + "</ul>"
        : '<p class="muted">None.</p>') + "</div>";
    return '<section class="tier-block" id="tier-' + tier + '"><div class="tier-head"><h2>Tier ' + tier + "</h2>" +
      '<span class="muted">' + (G.thresholds[i] ? "Unlocks at " + num(G.thresholds[i]) + " team points" : "Pick one at the start") +
      "</span></div>" + passives + '<div class="relic-grid">' + relics.map(relicCard).join("") + "</div></section>";
  }

  function render() {
    var root = document.getElementById("relics");
    if (!H || !G) {
      root.innerHTML = '<div class="page-head"><h1>Relics</h1><p>The relic data didn\'t load.</p></div>';
      return;
    }
    var byTier = [];
    G.relics.forEach(function (r) {
      (byTier[r.tier - 1] = byTier[r.tier - 1] || []).push(relicInfo(r));
    });
    var html = '<div class="page-head"><h1>Relics</h1>' + (H.intro || []).map(function (p) { return "<p>" + rich(p) + "</p>"; }).join("") + "</div>";
    if (H.screenNote) html += '<div class="notice">' + rich(H.screenNote) + "</div>";
    html += '<nav class="toc"><a href="#tiers">At a glance</a>' + byTier.map(function (_, i) {
      return '<a href="#tier-' + (i + 1) + '">Tier ' + (i + 1) + "</a>";
    }).join("") + "</nav>";
    html += overview(byTier);
    html += byTier.map(tierSection).join("");
    html += '<p class="footer">Points, rates and icons generated from the game code on ' + esc(G.generated) +
      ". Text last edited " + esc(H.updated) + ".</p>";
    root.innerHTML = html;
    // The page is drawn after load, so jump to the #relic the link asked for now that it exists.
    if (location.hash) {
      var target = document.getElementById(location.hash.slice(1));
      if (target) target.scrollIntoView();
    }
  }

  render();
})();
