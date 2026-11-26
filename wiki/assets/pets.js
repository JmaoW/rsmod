// Renders the Pets page (pets.html) from data/pets-generated.js (the game's pet table, written by
// tools/wiki/gen_pets.py) and data/content.js (build and test state).
(function () {
  "use strict";

  var P = window.ISLE_PETS;

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  function row(p) {
    var looks = p.looks.length > 1 ? esc(p.looks.join(", ")) : '<span class="muted">One look</span>';
    return "<tr><td><b>" + esc(p.name) + "</b></td><td>" + esc(p.source || "") + "</td><td>" +
      esc(p.chance || "") + "</td><td>" + looks + (p.morph ? '<br><span class="muted">' + esc(p.morph) +
      "</span>" : "") + "</td></tr>";
  }

  function table(list, withSource) {
    var head = withSource ? "<th>Pet</th><th>Where from</th><th>Chance</th><th>Looks</th>"
      : "<th>Pet</th><th></th><th></th><th>Looks</th>";
    return '<div class="table-wrap"><table><thead><tr>' + head + "</tr></thead><tbody>" +
      list.map(row).join("") + "</tbody></table></div>";
  }

  function render() {
    var root = document.getElementById("pets-list");
    if (!P || !root) return;
    var here = P.pets.filter(function (p) { return p.source; });
    var later = P.pets.filter(function (p) { return !p.source; });
    root.innerHTML =
      '<section class="section"><h2>Pets you can get on the isle (' + here.length + ")</h2>" +
      table(here, true) + "</section>" +
      '<section class="section"><h2>Not in the league yet (' + later.length + ")</h2>" +
      "<p>These pets exist (an admin can hand them out) but nothing on the isle drops them yet.</p>" +
      table(later, false) + "</section>";
  }

  render();
})();
