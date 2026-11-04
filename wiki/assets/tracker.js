(function () {
  "use strict";

  var DOCS_URL = "https://github.com/JmaoW/rsmod/blob/main/docs/";
  var TESTED_LABEL = { no: "Not tested", issues: "Issues", passed: "Passed" };

  var data = window.ISLE_CONTENT;
  var el = function (id) { return document.getElementById(id); };
  var state = { section: "", q: "", tested: "", prio: "", built: "" };

  if (!data || !data.sections) {
    el("sections").innerHTML = '<p class="empty">The tracker data (data/content.js) could not be loaded.</p>';
    return;
  }

  function avg(items) {
    if (!items.length) return 0;
    var sum = 0;
    items.forEach(function (it) { sum += it.built; });
    return Math.round(sum / items.length);
  }

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  function allItems() {
    var out = [];
    data.sections.forEach(function (s) { out = out.concat(s.items); });
    return out;
  }

  function countTested(items, v) {
    return items.filter(function (it) { return (it.tested || "no") === v; }).length;
  }

  function matches(it) {
    var tested = it.tested || "no";
    if (state.tested && tested !== state.tested) return false;
    if (state.prio && it.priority !== state.prio) return false;
    if (state.built === "todo" && it.built >= 100) return false;
    if (state.built === "ready" && (it.built < 100 || tested === "passed")) return false;
    if (state.built === "zero" && it.built > 0) return false;
    if (state.q) {
      var hay = (it.name + " " + (it.about || "") + " " + (it.left || "")).toLowerCase();
      if (hay.indexOf(state.q) === -1) return false;
    }
    return true;
  }

  function bar(pct) {
    return '<span class="bar"><span class="' + (pct >= 100 ? "done" : "") + '" style="width:' + pct + '%"></span></span>';
  }

  function renderStats() {
    var items = allItems();
    var p0Left = items.filter(function (it) { return it.priority === "P0" && it.built < 100; }).length;
    var passed = countTested(items, "passed");
    var issues = countTested(items, "issues");
    var notStarted = items.filter(function (it) { return it.built === 0; }).length;
    el("stats").innerHTML =
      stat("Built overall", avg(items) + "%", items.length + " items") +
      stat("Passed in game", passed + " / " + items.length, issues + (issues === 1 ? " item has" : " items have") + " issues") +
      stat("P0 still being built", p0Left, "must be done before the event") +
      stat("Not started", notStarted, "items at 0%");
  }

  function stat(label, value, sub) {
    return '<div class="stat"><div class="stat-label">' + esc(label) + '</div><div class="stat-value">' +
      esc(value) + '</div><div class="stat-sub">' + esc(sub) + "</div></div>";
  }

  function renderChips() {
    var html = chip("", "All", avg(allItems()));
    data.sections.forEach(function (s) { html += chip(s.id, s.name, avg(s.items)); });
    el("chips").innerHTML = html;
  }

  function chip(id, name, pct) {
    return '<button class="chip" data-section="' + esc(id) + '" aria-pressed="' + (state.section === id) + '">' +
      esc(name) + '<span class="n">' + pct + "%</span></button>";
  }

  function renderSections() {
    var html = "";
    var shown = 0;
    data.sections.forEach(function (s) {
      if (state.section && state.section !== s.id) return;
      var rows = s.items.filter(matches);
      if (!rows.length) return;
      shown += rows.length;
      var pct = avg(s.items);
      var started = s.items.filter(function (it) { return it.built > 0; }).length;
      html += '<section class="section" id="s-' + esc(s.id) + '">' +
        '<div class="section-head"><h2>' + esc(s.name) + '</h2><span class="pct">' + pct + "%</span>" +
        "<p>" + esc(s.blurb) + "</p>" +
        '<span class="counts">' + started + " of " + s.items.length + " started · " +
        countTested(s.items, "passed") + " passed" +
        (countTested(s.items, "issues") ? " · " + countTested(s.items, "issues") + " with issues" : "") + "</span>" +
        '<div class="section-bar">' + bar(pct) + "</div></div>" +
        '<div class="cols"><span>Item</span><span>Built</span><span>Tested</span></div>';
      rows.forEach(function (it) { html += row(it); });
      html += "</section>";
    });
    el("sections").innerHTML = shown ? html : '<p class="empty">Nothing matches those filters.</p>';
  }

  function row(it) {
    var tested = it.tested || "no";
    var detail = "";
    if (it.about) detail += "<p>" + esc(it.about) + "</p>";
    if (it.left) detail += '<p><span class="k">Still to do:</span> ' + esc(it.left) + "</p>";
    if (tested === "issues" && it.issue) detail += '<p><span class="k">Issues found:</span> ' + esc(it.issue) + "</p>";
    if (it.doc) detail += '<p><a href="' + DOCS_URL + encodeURI(it.doc) + '" target="_blank" rel="noopener">Build notes</a></p>';
    return '<div class="row">' +
      '<button class="row-main" aria-expanded="false">' +
      '<span class="name"><span class="chev" aria-hidden="true">&#9654;</span><span class="t">' + esc(it.name) +
      '</span><span class="prio ' + esc(it.priority) + '">' + esc(it.priority) + "</span></span>" +
      '<span class="built">' + bar(it.built) + '<span class="num">' + it.built + "%</span></span>" +
      '<span><span class="pill ' + tested + '">' + TESTED_LABEL[tested] + "</span></span>" +
      "</button>" +
      '<div class="detail">' + detail + "</div></div>";
  }

  function render() {
    renderChips();
    renderSections();
  }

  el("chips").addEventListener("click", function (e) {
    var b = e.target.closest(".chip");
    if (!b) return;
    state.section = b.getAttribute("data-section");
    render();
  });

  el("sections").addEventListener("click", function (e) {
    var b = e.target.closest(".row-main");
    if (!b) return;
    var r = b.parentNode;
    var open = !r.classList.contains("open");
    r.classList.toggle("open", open);
    b.setAttribute("aria-expanded", String(open));
  });

  el("q").addEventListener("input", function (e) { state.q = e.target.value.trim().toLowerCase(); renderSections(); });
  el("f-tested").addEventListener("change", function (e) { state.tested = e.target.value; renderSections(); });
  el("f-prio").addEventListener("change", function (e) { state.prio = e.target.value; renderSections(); });
  el("f-built").addEventListener("change", function (e) { state.built = e.target.value; renderSections(); });

  el("footer").textContent = "Last updated " + data.updated + ".";
  renderStats();
  render();
})();
