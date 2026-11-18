/* VTLOsint web app — the investigation pipeline: phases along a circuit spine, then sections and tools. */
(function () {
  "use strict";

  const store = {
    get(key, fallback) {
      try { const v = localStorage.getItem("vtlo." + key); return v === null ? fallback : JSON.parse(v); } catch (e) { return fallback; }
    },
    set(key, value) { try { localStorage.setItem("vtlo." + key, JSON.stringify(value)); } catch (e) { /* private mode */ } },
  };

  const $ = (id) => document.getElementById(id);
  /** Builds an element; text is always set with textContent, never parsed as HTML. */
  function el(tag, attrs, ...children) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (k === "class") node.className = v;
      else if (k === "text") node.textContent = v;
      else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
      else node.setAttribute(k, v);
    }
    for (const child of children.flat()) if (child != null) node.append(child);
    return node;
  }

  function toast(message) {
    const t = $("toast");
    t.textContent = message;
    t.classList.add("show");
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => t.classList.remove("show"), 1800);
  }

  function copy(text) {
    navigator.clipboard.writeText(text).then(() => toast("Copié dans le presse-papiers"), () => toast("Copie impossible"));
  }

  function host(url) {
    try { return new URL(url).hostname.replace(/^www\./, ""); } catch (e) { return url; }
  }

  // ------------------------------------------------------------------ data

  const { phases, sections } = window.VTLO_DATA;
  const toolsByUrl = new Map();
  for (const s of sections) for (const t of s.tools) if (!toolsByUrl.has(t.url)) toolsByUrl.set(t.url, t);
  for (const p of phases) {
    p.sections = sections.filter((s) => s.phase === p.id);
    p.tools = p.sections.reduce((n, s) => n + s.tools.length, 0);
  }
  $("stats").textContent = `${toolsByUrl.size} OUTILS · ${sections.length} SECTIONS · ${phases.length} PHASES`;
  $("search").placeholder = `Rechercher parmi ${toolsByUrl.size} outils…`;

  const favorites = () => store.get("favorites", []);
  const isFavorite = (tool) => favorites().includes(tool.url);
  function toggleFavorite(tool) {
    const favs = favorites();
    const i = favs.indexOf(tool.url);
    if (i >= 0) favs.splice(i, 1); else favs.push(tool.url);
    store.set("favorites", favs);
    toast(i >= 0 ? "Retiré des favoris" : "Ajouté aux favoris");
    route();
  }

  // Phase illustrations, drawn like the infographic's grey shapes with a blue accent.
  const ICONS = {
    radar: [["#9AA3AF", "M32,6 L50.4,13.6 L58,32 L50.4,50.4 L32,58 L13.6,50.4 L6,32 L13.6,13.6 Z"], ["#2A303B", "M32,12a20,20 0,1 1,0 40a20,20 0,1 1,0 -40z"],
      ["none", "M32,18a14,14 0,1 1,0 28a14,14 0,1 1,0 -28zM32,25a7,7 0,1 1,0 14a7,7 0,1 1,0 -14z", "#5C6675"], ["#2F9BEF", "M32,32 L32,12 A20,20 0,0 1,50 23 Z"],
      ["none", "M32,2V12M32,52V62M2,32H12M52,32H62", "#C9CED6"]],
    identity: [["#9AA3AF", "M8,14h48v36h-48z"], ["#2A303B", "M11,17h42v30h-42z"], ["#2F9BEF", "M23,24a6,6 0,1 1,0 12a6,6 0,1 1,0 -12zM13,45c0,-6 4,-9 10,-9s10,3 10,9z"],
      ["#9AA3AF", "M37,24h12v3h-12zM37,31h12v3h-12zM37,38h8v3h-8z"]],
    network: [["none", "M32,32L14,14M32,32L52,16M32,32L12,50M32,32L50,52M14,14L52,16M12,50L50,52", "#5C6675"],
      ["#9AA3AF", "M14,8a6,6 0,1 1,0 12a6,6 0,1 1,0 -12zM52,10a6,6 0,1 1,0 12a6,6 0,1 1,0 -12zM12,44a6,6 0,1 1,0 12a6,6 0,1 1,0 -12zM50,46a6,6 0,1 1,0 12a6,6 0,1 1,0 -12z"],
      ["#2F9BEF", "M32,23a9,9 0,1 1,0 18a9,9 0,1 1,0 -18z"]],
    media: [["#9AA3AF", "M6,12h44v34h-44z"], ["#2A303B", "M9,15h38v28h-38z"], ["#5C6675", "M9,43L22,27L32,37L38,31L47,43z"], ["#C9CED6", "M38,19a4,4 0,1 1,0 8a4,4 0,1 1,0 -8z"],
      ["#2F9BEF", "M48,30c-6,0 -10,4.5 -10,10c0,7 10,18 10,18s10,-11 10,-18c0,-5.5 -4,-10 -10,-10zM48,36a4,4 0,1 1,0 8a4,4 0,1 1,0 -8z"]],
    stack: [["#C9CED6", "M32,40L54,48L32,56L10,48Z"], ["#5C6675", "M10,48L32,56V60L10,52Z"], ["#9AA3AF", "M32,56L54,48V52L32,60Z"], ["#9AA3AF", "M32,26L54,34L32,42L10,34Z"],
      ["#5C6675", "M10,34L32,42V46L10,38Z"], ["#C9CED6", "M32,42L54,34V38L32,46Z"], ["#2F9BEF", "M32,12L54,20L32,28L10,20Z"], ["#1F5F94", "M10,20L32,28V32L10,24Z"],
      ["#2580C9", "M32,28L54,20V24L32,32Z"]],
    funnel: [["#5C6675", "M2,10h7v6h-7zM12,10h7v6h-7zM45,10h7v6h-7z"], ["#C9CED6", "M22,10h7v6h-7zM55,10h7v6h-7z"], ["#9AA3AF", "M10,22H54L38,40V54L26,58V40Z"],
      ["#2F9BEF", "M14,24H50L42,32H22Z"], ["#2F9BEF", "M52,46h8v8h-8z"]],
  };
  function icon(name) {
    const NS = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(NS, "svg");
    svg.setAttribute("viewBox", "0 0 64 64");
    svg.setAttribute("class", "icon");
    svg.setAttribute("aria-hidden", "true");
    for (const [fill, d, stroke] of ICONS[name] || ICONS.funnel) {
      const path = document.createElementNS(NS, "path");
      path.setAttribute("d", d);
      path.setAttribute("fill", fill);
      if (stroke) { path.setAttribute("stroke", stroke); path.setAttribute("stroke-width", "1.8"); }
      svg.append(path);
    }
    return svg;
  }

  // ------------------------------------------------------------------ command-line tools

  const PIP = {
    "sherlock-project/sherlock": ["sherlock-project", "sherlock --help"],
    "soxoj/maigret": ["maigret", "maigret --help"],
    "mxrch/ghunt": ["ghunt", "ghunt --help"],
    "megadose/toutatis": ["toutatis", "toutatis -h"],
  };
  const quote = (s) => "'" + s.replace(/'/g, "'\\''") + "'";

  /** How a tool runs from a shell, or null for web-only tools (mirrors the Android Termux class). */
  function cliInfo(tool) {
    let u;
    try { u = new URL(tool.url); } catch (e) { return null; }
    const h = u.hostname.toLowerCase().replace(/^www\./, "");
    if (h === "github.com") {
      const parts = u.pathname.split("/").filter(Boolean);
      if (parts.length < 2) return null;
      const repo = parts[1].replace(/\.git$/, "");
      const pip = PIP[(parts[0] + "/" + repo).toLowerCase()];
      if (pip) return { kind: "pip", pkg: pip[0], cmd: pip[1] };
      return { kind: "git", url: `https://github.com/${parts[0]}/${repo}`, dir: repo };
    }
    if (tool.url.includes("kali.org/tools/metagoofil")) return { kind: "git", url: "https://github.com/kurobeats/metagoofil", dir: "metagoofil" };
    if (h === "exiftool.org") return { kind: "exiftool" };
    if (h === "testssl.sh") return { kind: "git", url: "https://github.com/testssl/testssl.sh", dir: "testssl.sh" };
    return null;
  }

  function script(info, target) {
    const termux = target === "termux";
    if (info.kind === "pip") {
      return termux
        ? `pkg install -y python && pip install --upgrade ${info.pkg} && ${info.cmd}\necho\nexec bash`
        : `python3 -m pip install --upgrade ${info.pkg} && ${info.cmd}`;
    }
    if (info.kind === "exiftool") {
      return termux
        ? "pkg install -y exiftool && exiftool -ver\necho\nexec bash"
        : "# Debian/Ubuntu: sudo apt install libimage-exiftool-perl\n# macOS:         brew install exiftool\n# Windows:       https://exiftool.org (exiftool(-k).exe)\nexiftool -ver";
    }
    const d = quote(info.dir);
    const fetch = `if [ -d ${d} ]; then cd ${d} && git pull --ff-only; else git clone --depth 1 ${quote(info.url)} ${d} && cd ${d}; fi\n`;
    if (termux) {
      return "command -v git >/dev/null || pkg install -y git\ncd ~\n" + fetch
        + "if ls requirements*.txt REQUIREMENTS setup.py pyproject.toml >/dev/null 2>&1; then\n"
        + "  command -v python >/dev/null || pkg install -y python\n"
        + "  for f in requirements.txt REQUIREMENTS; do [ -f \"$f\" ] && pip install -r \"$f\"; done\n"
        + "  if [ -f setup.py ] || [ -f pyproject.toml ]; then pip install .; fi\n"
        + "elif [ -f go.mod ]; then\n  pkg install -y golang && go build ./...\n"
        + "elif [ -f package.json ]; then\n  pkg install -y nodejs && npm install\n"
        + "elif [ -f Gemfile ]; then\n  pkg install -y ruby && gem install bundler && bundle install\nfi\n"
        + "echo; echo \"[+] Installed in $(pwd) - see README.md for usage\"; ls\necho\nexec bash";
    }
    return "# Linux, macOS or Windows (WSL / Git Bash) — needs git, plus python3, go, node or ruby as the project requires\ncd ~\n" + fetch
      + "if ls requirements*.txt REQUIREMENTS setup.py pyproject.toml >/dev/null 2>&1; then\n"
      + "  python3 -m venv .venv && . .venv/bin/activate\n"
      + "  for f in requirements.txt REQUIREMENTS; do [ -f \"$f\" ] && pip install -r \"$f\"; done\n"
      + "  if [ -f setup.py ] || [ -f pyproject.toml ]; then pip install .; fi\n"
      + "elif [ -f go.mod ]; then\n  go build ./...\n"
      + "elif [ -f package.json ]; then\n  npm install\n"
      + "elif [ -f Gemfile ]; then\n  bundle install\nfi\n"
      + "echo; echo \"[+] Installed in $(pwd) - see README.md for usage\"; ls";
  }

  // ------------------------------------------------------------------ building blocks

  function card(tabText, ...children) {
    return el("div", { class: "card" }, el("span", { class: "tab", text: tabText }), ...children);
  }

  function heading(kicker, headline, body) {
    return [kicker && el("p", { class: "kicker", text: kicker }), el("h2", { text: headline }), body && el("p", { class: "body", text: body })];
  }

  function step(side, row, content) {
    const s = el("div", { class: "step " + side }, el("span", { class: "wire" }), el("span", { class: "node" }), content);
    if (row) s.style.gridRow = `${row} / span 2`;
    return s;
  }

  function toolRow(tool) {
    const term = !!cliInfo(tool);
    return el("button", { class: "row", title: tool.url, onclick: () => openTool(tool),
      oncontextmenu: (e) => { e.preventDefault(); showMenu(tool); } },
      el("span", { class: "texts" }, el("strong", { text: tool.name }),
        el("small", { text: host(tool.url) + (tool.description ? " — " + tool.description : "") })),
      el("span", { class: "tag" + (term ? " term" : ""), text: term ? ">_ TERMINAL" : "WEB" }),
      el("span", { class: "opts", title: "Options", "aria-label": "Options", role: "button",
        onclick: (e) => { e.stopPropagation(); showMenu(tool); }, text: "⋯" }));
  }

  /** A section as a tabbed card of tool rows, or null when the query matches nothing in it. */
  function sectionCard(section, query, tabText) {
    const rows = [];
    if (section.notes && (!query || section.notes.toLowerCase().includes(query) || section.title.toLowerCase().includes(query))) {
      rows.push(el("button", { class: "row", onclick: () => showNotes(section) },
        el("span", { class: "texts" }, el("strong", { text: "≡ Notes, requêtes et commandes" }), el("small", { text: section.notes.split("\n")[0] }))));
    }
    for (const t of section.tools) {
      if (query && ![t.name, t.url, t.description, section.title].some((x) => x && x.toLowerCase().includes(query))) continue;
      rows.push(toolRow(t));
    }
    if (!rows.length) return null;
    return { count: rows.length, node: card(tabText || section.title.toUpperCase(), el("div", { class: "rows" }, rows)) };
  }

  // ------------------------------------------------------------------ views

  const view = $("view");
  const search = $("search");

  function home() {
    const pipeline = el("div", { class: "pipeline" }, el("div", { class: "spine" }));
    const items = [];
    const favs = favorites().map((u) => toolsByUrl.get(u)).filter(Boolean);
    if (favs.length) items.push(card("FAVORIS", el("div", { class: "rows" }, favs.map(toolRow))));
    phases.forEach((p, i) => {
      const c = card("PHASE " + p.id, icon(p.icon), ...heading(p.kicker, p.headline, p.body),
        el("p", { class: "more", text: `${p.sections.length} sections · ${p.tools} outils  →` }));
      c.classList.add("link");
      c.tabIndex = 0;
      c.addEventListener("click", () => { location.hash = "#/phase/" + p.id; });
      c.addEventListener("keydown", (e) => { if (e.key === "Enter") location.hash = "#/phase/" + p.id; });
      items.push(c);
      if (i === 2) items.push(el("div", { class: "quote" }, el("span", { text: "La vérification croisée des sources est le cœur de l'OSINT." })));
    });
    items.forEach((node, i) => pipeline.append(step(i % 2 ? "right" : "left", i + 1, node)));
    pipeline.firstChild.style.gridRow = `1 / ${items.length + 2}`;
    view.replaceChildren(pipeline);
  }

  function phasePage(id) {
    const p = phases.find((x) => x.id === id);
    if (!p) return home();
    const list = el("div", { class: "sections" });
    for (const s of p.sections) {
      const c = sectionCard(s, "");
      if (c) list.append(step("left", 0, c.node));
    }
    view.replaceChildren(
      el("a", { class: "back", href: "#/", text: "← Pipeline" }),
      card("PHASE " + p.id, icon(p.icon), ...heading(p.kicker, p.headline, `${p.body}\n${p.sections.length} sections · ${p.tools} outils`)),
      list);
  }

  function results(query) {
    const list = el("div", { class: "sections" });
    let total = 0;
    for (const p of phases) {
      for (const s of p.sections) {
        const c = sectionCard(s, query, `PHASE ${p.id} · ${s.title.toUpperCase()}`);
        if (!c) continue;
        total += c.count;
        list.append(step("left", 0, c.node));
      }
    }
    view.replaceChildren(el("p", { class: "count", text: total ? `${total} résultat(s)` : "Aucun outil ne correspond." }), list);
  }

  function route() {
    const q = search.value.trim().toLowerCase();
    if (q) return results(q);
    const m = location.hash.match(/^#\/phase\/(\d+)/);
    if (m) phasePage(+m[1]); else home();
  }
  window.addEventListener("hashchange", () => { search.value = ""; route(); window.scrollTo(0, 0); });
  search.addEventListener("input", route);

  // ------------------------------------------------------------------ tool actions

  const modal = $("modal");
  function showModal(...content) {
    $("modal-body").replaceChildren(...content);
    modal.hidden = false;
    const focusable = modal.querySelector("input, select, textarea, button:not(.modal-close)");
    if (focusable) focusable.focus();
  }
  function closeModal() { modal.hidden = true; }
  modal.addEventListener("click", (e) => { if (e.target === modal || e.target.classList.contains("modal-close")) closeModal(); });

  function openTool(tool) {
    if (cliInfo(tool)) showMenu(tool);
    else window.open(tool.url, "_blank", "noopener");
  }

  function showMenu(tool) {
    const info = cliInfo(tool);
    const content = [el("h3", { text: tool.name }), el("p", { class: "count", text: tool.url })];
    if (info) {
      const pre = el("pre", { class: "mono" });
      const tabs = el("div", { class: "tabs-row" });
      const setTarget = (t) => {
        pre.textContent = script(info, t);
        tabs.querySelectorAll("button").forEach((b) => b.classList.toggle("on", b.dataset.t === t));
      };
      tabs.append(el("button", { "data-t": "pc", text: "Terminal PC", onclick: () => setTarget("pc") }),
        el("button", { "data-t": "termux", text: "Android · Termux", onclick: () => setTarget("termux") }));
      content.push(el("label", { text: "Installer et lancer depuis un terminal" }), tabs, pre,
        el("div", { class: "btns" }, el("button", { class: "btn", text: "Copier la commande", onclick: () => copy(pre.textContent) })));
      setTarget("pc");
    }
    const items = [
      ["Ouvrir dans un nouvel onglet", () => window.open(tool.url, "_blank", "noopener")],
      ["Copie archivée (Wayback Machine)", () => window.open("https://web.archive.org/web/" + tool.url, "_blank", "noopener")],
      [isFavorite(tool) ? "Retirer des favoris" : "Ajouter aux favoris", () => { toggleFavorite(tool); closeModal(); }],
      ["Copier le lien", () => copy(tool.url)],
    ];
    for (const [label, action] of items) content.push(el("button", { class: "menu-item", text: label, onclick: action }));
    showModal(...content);
  }

  function showNotes(section) {
    showModal(el("h3", { text: section.title }), el("pre", { class: "mono", text: section.notes }),
      el("div", { class: "btns" }, el("button", { class: "btn", text: "Tout copier", onclick: () => copy(section.notes) })));
  }

  function showTerminalHelp() {
    showModal(el("h3", { text: "Outils en ligne de commande" }),
      el("p", { class: "body", text: "Les outils marqués >_ TERMINAL (Sherlock, Maigret, GHunt, theHarvester…) se lancent depuis un terminal. "
        + "Ouvrez l'outil pour copier sa commande d'installation : Terminal PC (Linux, macOS, Windows avec WSL ou Git Bash) "
        + "ou Android · Termux. Prérequis sur PC : git et, selon le projet, python3, go, node ou ruby." }));
  }

  // Dork builder — operators and templates from the cheat sheet.
  const TEMPLATES = [
    ["Dork · Texte sur un site", 'intext:"{T}" site:example.com'],
    ["Dork · Documents sur un site", "site:{T} ext:doc | ext:docx | ext:odt | ext:rtf | ext:sxw | ext:psw | ext:ppt | ext:pptx | ext:pps | ext:csv"],
    ["Dork · Répertoire ouvert (index of)", 'intitle:"index of" "{T}"'],
    ["Dork · Tous les mots dans le titre", "allintitle: {T}"],
    ["Dork · Tous les mots dans l'URL", "allinurl: {T}"],
    ["Dork · Pages qui pointent vers", "link:{T}"],
    ["Dork · Définir un terme", "define:{T}"],
    ["Facebook · Recherche de groupes", 'site:facebook.com inurl:group "{T}"'],
    ["Facebook · Publications de groupe", 'site:facebook.com inurl:wall "{T}"'],
    ["Facebook · Pages", 'site:facebook.com inurl:pages "{T}"'],
    ["Facebook · Profils publics", 'allinurl: people "{T}" site:facebook.com'],
    ["Facebook · Par ID", "id {T} site:facebook.com *"],
    ...["photos-by", "photos-liked", "photos-of", "photos-commented", "friends", "videos", "videos-by", "videos-liked",
      "videos-commented", "events-joined", "relatives"].map((p) => ["FQL · " + p + " (ID)", "https://www.facebook.com/search/{T}/" + p]),
    ["LinkedIn · Profils publics", 'site:linkedin.com inurl:pub "{T}"'],
    ["LinkedIn · Profils mis à jour", 'site:linkedin.com inurl:updates "{T}"'],
    ["LinkedIn · Pages d'entreprise", 'site:linkedin.com inurl:companies "{T}"'],
    ["MySpace · Profils", 'site:myspace.com inurl:profile "{T}"'],
    ["MySpace · Blogs", 'site:myspace.com inurl:blogs "{T}"'],
    ["MySpace · Vidéos", 'site:myspace.com "{T}" "videos"'],
    ["Telegram · Canaux et utilisateurs", 'site:t.me "{T}"'],
    ["Pastebin · Mentions", 'site:pastebin.com "{T}"'],
  ];
  const ENGINES = [["Google", "https://www.google.com/search?q="], ["Bing", "https://www.bing.com/search?q="],
    ["DuckDuckGo", "https://duckduckgo.com/?q="], ["Yandex", "https://yandex.com/search/?text="]];

  function showDorks() {
    const target = el("input", { placeholder: "ex. johndoe ou exemple.com" });
    const tpl = el("select", {}, el("option", { value: "", text: "— Choisir un modèle —" }), TEMPLATES.map(([l], i) => el("option", { value: String(i), text: l })));
    const ops = ["site", "inurl", "intitle", "intext", "filetype"].map((o) => [o, el("input", { placeholder: o + ":" })]);
    const exclude = el("input", { placeholder: "mots à exclure (-mot)" });
    const query = el("textarea", { rows: "3" });
    query.style.fontFamily = "Montserrat, sans-serif";
    query.style.fontSize = "15px";
    query.style.color = "var(--text)";
    tpl.addEventListener("change", () => {
      if (tpl.value) query.value = TEMPLATES[+tpl.value][1].replace(/\{T\}/g, target.value.trim() || "TARGET");
    });
    const generate = () => {
      const parts = [];
      if (target.value.trim()) parts.push(`"${target.value.trim()}"`);
      for (const [o, input] of ops) {
        let v = input.value.trim();
        if (v) parts.push(o + ":" + (v.includes(" ") ? `"${v}"` : v));
      }
      for (const w of exclude.value.trim().split(/\s+/)) if (w) parts.push("-" + w);
      query.value = parts.join(" ");
    };
    const go = (base) => {
      const q = query.value.trim();
      if (!q) return toast("Générez ou tapez d'abord une requête");
      window.open(/^https?:\/\//.test(q) ? q : base + encodeURIComponent(q), "_blank", "noopener");
    };
    const opGrid = el("div", {}, ops.map(([, i]) => i), exclude);
    opGrid.style.display = "grid";
    opGrid.style.gridTemplateColumns = "1fr 1fr";
    opGrid.style.gap = "8px";
    showModal(el("h3", { text: "Générateur de dorks" }),
      el("label", { text: "Cible (nom, pseudo, domaine, ID Facebook…)" }), target,
      el("label", { text: "Modèle" }), tpl,
      el("label", { text: "Ou combiner des opérateurs" }), opGrid,
      el("div", { class: "btns" }, el("button", { class: "btn ghost", text: "Générer la requête", onclick: generate })),
      el("label", { text: "Requête" }), query,
      el("div", { class: "btns" }, ENGINES.map(([n, u]) => el("button", { class: "btn", text: n, onclick: () => go(u) })),
        el("button", { class: "btn ghost", text: "Copier", onclick: () => copy(query.value) })));
  }

  // EXIF viewer / remover, entirely in the browser.
  const EXIF_TAGS = {
    0x010e: "ImageDescription", 0x010f: "Make", 0x0110: "Model", 0x0112: "Orientation", 0x0131: "Software",
    0x0132: "DateTime", 0x013b: "Artist", 0x8298: "Copyright", 0x829a: "ExposureTime", 0x829d: "FNumber",
    0x8827: "ISO", 0x9003: "DateTimeOriginal", 0x9004: "DateTimeDigitized", 0x920a: "FocalLength",
    0xa002: "PixelXDimension", 0xa003: "PixelYDimension", 0xa430: "CameraOwnerName", 0xa431: "BodySerialNumber",
    0xa433: "LensMake", 0xa434: "LensModel",
  };
  const GPS_TAGS = { 1: "GPSLatitudeRef", 2: "GPSLatitude", 3: "GPSLongitudeRef", 4: "GPSLongitude", 5: "GPSAltitudeRef",
    6: "GPSAltitude", 7: "GPSTimeStamp", 29: "GPSDateStamp" };
  const SIZES = [0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8];

  function readExif(buffer) {
    const v = new DataView(buffer);
    if (v.byteLength < 4 || v.getUint16(0) !== 0xffd8) throw new Error("Seules les photos JPEG contiennent un EXIF lisible ici");
    let off = 2;
    while (off + 4 <= v.byteLength) {
      const marker = v.getUint16(off);
      if ((marker & 0xff00) !== 0xff00) break;
      const len = v.getUint16(off + 2);
      if (marker === 0xffe1 && v.getUint32(off + 4) === 0x45786966) return parseTiff(v, off + 10);
      off += 2 + len;
    }
    return {};
  }

  function parseTiff(v, start) {
    const le = v.getUint16(start) === 0x4949;
    const u16 = (o) => v.getUint16(o, le);
    const u32 = (o) => v.getUint32(o, le);
    const out = {};
    function value(type, count, at) {
      const vals = [];
      for (let i = 0; i < count && vals.length < 64; i++) {
        const o = at + i * SIZES[type];
        if (type === 3) vals.push(u16(o));
        else if (type === 4) vals.push(u32(o));
        else if (type === 9) vals.push(v.getInt32(o, le));
        else if (type === 5) vals.push(u32(o) / (u32(o + 4) || 1));
        else if (type === 10) vals.push(v.getInt32(o, le) / (v.getInt32(o + 4, le) || 1));
        else vals.push(v.getUint8(o));
      }
      if (type === 2) return String.fromCharCode(...vals).replace(/\0+$/, "").trim();
      return vals.length === 1 ? vals[0] : vals;
    }
    function ifd(offset, names, follow) {
      if (offset + 2 > v.byteLength) return;
      const n = u16(offset);
      for (let i = 0; i < n; i++) {
        const e = offset + 2 + i * 12;
        if (e + 12 > v.byteLength) return;
        const tag = u16(e), type = u16(e + 2), count = u32(e + 4);
        if (!SIZES[type]) continue;
        const at = SIZES[type] * count > 4 ? start + u32(e + 8) : e + 8;
        if (at + SIZES[type] * Math.min(count, 64) > v.byteLength) continue;
        if (follow && tag === 0x8769) ifd(start + u32(e + 8), EXIF_TAGS, false);
        else if (follow && tag === 0x8825) ifd(start + u32(e + 8), GPS_TAGS, false);
        else if (names[tag]) out[names[tag]] = value(type, count, at);
      }
    }
    ifd(start + u32(start + 4), EXIF_TAGS, true);
    return out;
  }

  function gps(tags) {
    const dms = (a) => Array.isArray(a) && a.length === 3 ? a[0] + a[1] / 60 + a[2] / 3600 : null;
    let lat = dms(tags.GPSLatitude), lon = dms(tags.GPSLongitude);
    if (lat == null || lon == null) return null;
    if (tags.GPSLatitudeRef === "S") lat = -lat;
    if (tags.GPSLongitudeRef === "W") lon = -lon;
    return [lat, lon];
  }

  function showExif() {
    const input = el("input", { type: "file", accept: "image/*" });
    const out = el("div", {});
    input.addEventListener("change", async () => {
      const file = input.files[0];
      if (!file) return;
      out.replaceChildren(el("p", { class: "count", text: file.name + " · " + file.type }));
      let tags = {};
      try { tags = readExif(await file.arrayBuffer()); } catch (err) { out.append(el("p", { text: err.message })); }
      const where = gps(tags);
      if (where) {
        const [lat, lon] = where.map((x) => x.toFixed(6));
        out.append(el("p", { text: `⚠ Position GPS : ${lat}, ${lon}` }),
          el("div", { class: "btns" }, el("button", { class: "btn", text: "Voir sur la carte",
            onclick: () => window.open(`https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=16/${lat}/${lon}`, "_blank", "noopener") })));
      }
      const names = Object.keys(tags);
      out.append(names.length
        ? el("table", { class: "exif" }, names.map((k) => el("tr", {}, el("td", { text: k }),
          el("td", { text: Array.isArray(tags[k]) ? tags[k].map((x) => +x.toFixed ? +x.toFixed(4) : x).join(", ") : String(tags[k]) }))))
        : el("p", { class: "count", text: "Aucune métadonnée EXIF trouvée." }));
      out.append(el("div", { class: "btns" }, el("button", { class: "btn ghost", text: "Télécharger une copie nettoyée", onclick: () => cleanCopy(file) })));
    });
    showModal(el("h3", { text: "Métadonnées EXIF" }),
      el("p", { class: "count", text: "Lit l'appareil, la date et la position GPS. La copie nettoyée est réencodée sans aucune métadonnée, comme exiftool -all=. Rien ne quitte votre ordinateur." }),
      input, out);
  }

  function cleanCopy(file) {
    const img = new Image();
    const url = URL.createObjectURL(file);
    img.onload = () => {
      const canvas = el("canvas", {});
      canvas.width = img.naturalWidth;
      canvas.height = img.naturalHeight;
      canvas.getContext("2d").drawImage(img, 0, 0);
      URL.revokeObjectURL(url);
      canvas.toBlob((blob) => {
        const a = el("a", { href: URL.createObjectURL(blob), download: file.name.replace(/\.[^.]+$/, "") + "_clean.jpg" });
        a.click();
        toast("Copie nettoyée enregistrée");
      }, "image/jpeg", 0.95);
    };
    img.onerror = () => toast("Ce format d'image ne peut pas être nettoyé ici");
    img.src = url;
  }

  // ------------------------------------------------------------------ French guide

  const guideBox = $("guide");
  const STEPS = [
    {
      title: "Bienvenue sur VTLOsint",
      body: "Plus de 550 outils OSINT rangés comme une enquête : six phases, de la recherche brute à la vérification.",
      art() {
        const items = phases.map((p) => el("li", { text: `PHASE ${p.id} · ${p.kicker}` }));
        const box = el("div", {}, el("p", { class: "kicker", text: "VTLOSINT" }), el("ul", { class: "mini" }, items));
        box.firstChild.style.cssText = "color:var(--blue);font-size:28px;text-align:center;margin:0 0 10px";
        return { node: box, tick: (n) => items.forEach((li, i) => { li.classList.toggle("dim", i > n % 8); li.classList.toggle("lit", i === n % 8); }) };
      },
    },
    {
      title: "Six phases",
      body: "Recherche → Identités → Réseaux sociaux → Images & Géo → Infrastructure → Vérification. Cliquez sur une phase pour ouvrir ses sections.",
      art() {
        const holder = el("div");
        return { node: holder, tick: (n) => {
          if (n % 2) return;
          const p = phases[(n / 2) % phases.length];
          const c = card("PHASE " + p.id, icon(p.icon), ...heading(p.kicker, p.headline));
          c.classList.add("slide-in");
          holder.replaceChildren(c);
        } };
      },
    },
    {
      title: "Sections et outils",
      body: "Chaque phase regroupe des sections. WEB s'ouvre dans un nouvel onglet, >_ TERMINAL donne la commande d'installation pour PC ou Termux.",
      art() {
        const demo = [["Shodan", "shodan.io", false], ["Sherlock", "github.com", true], ["Wayback Machine", "web.archive.org", false]];
        const rows = demo.map(([n, h, t]) => el("div", { class: "row" }, el("span", { class: "texts" }, el("strong", { text: n }), el("small", { text: h })),
          el("span", { class: "tag" + (t ? " term" : ""), text: t ? ">_ TERMINAL" : "WEB" })));
        return { node: card("IP ADDRESSES", rows), tick: (n) => rows.forEach((r, i) => r.classList.toggle("hl", i === n % 3)) };
      },
    },
    {
      title: "Options d'un outil",
      body: "Le bouton ⋯ ou le clic droit ouvre les options : favoris, copie archivée sur la Wayback Machine, copie du lien. Vos favoris s'affichent en tête du pipeline.",
      art() {
        const items = ["★  Ajouter aux favoris", "Copie archivée (Wayback Machine)", "Ouvrir dans un nouvel onglet", "Copier le lien"]
          .map((t) => el("div", { class: "menu-item", text: t }));
        const box = el("div", { class: "modal-box" }, el("h3", { text: "Sherlock" }), items);
        box.style.position = "static";
        return { node: box, tick: (n) => items.forEach((it, i) => it.classList.toggle("hl", i === n % items.length)) };
      },
    },
    {
      title: "Rechercher",
      body: "La barre de recherche filtre tous les outils par nom, site ou description, toutes phases confondues. Raccourci clavier : /",
      art() {
        const field = el("div");
        field.style.cssText = "background:var(--surface);border:1.5px solid var(--blue);border-radius:4px;padding:12px 14px";
        const result = card("PHASE 3 · SOCIAL NETWORKS", el("div", { class: "row" }, el("span", { class: "texts" }, el("strong", { text: "Sherlock" }),
          el("small", { text: "github.com" })), el("span", { class: "tag term", text: ">_ TERMINAL" })));
        const word = "sherlock";
        return { node: el("div", {}, field, result), tick: (n) => {
          const k = Math.min(n % (word.length + 5), word.length);
          field.textContent = k ? word.slice(0, k) + "▌" : "Rechercher…";
          field.style.color = k ? "var(--text)" : "var(--dim)";
          result.style.opacity = k === word.length ? 1 : 0;
        } };
      },
    },
    {
      title: "Outils intégrés",
      body: "Générateur de Google Dorks, lecteur et nettoyeur EXIF (rien ne quitte votre ordinateur), aide pour le terminal, et ce guide.",
      art() {
        const nav = document.querySelector(".quick").cloneNode(true);
        nav.querySelectorAll("button").forEach((b) => b.setAttribute("tabindex", "-1"));
        nav.style.justifyContent = "center";
        return { node: nav, tick: (n) => {
          const b = nav.children[n % nav.children.length];
          b.classList.remove("bounce");
          void b.offsetWidth;
          b.classList.add("bounce");
        } };
      },
    },
    {
      title: "OSINT éthique",
      body: "Utilisez ces outils légalement, uniquement sur des cibles que vous êtes autorisé à étudier. Ce guide reste accessible avec le bouton Guide.",
      art() {
        const q = el("div", { class: "quote breathe" }, el("span", { text: "La vérification croisée des sources est le cœur de l'OSINT." }));
        return { node: q, tick: () => {} };
      },
    },
  ];

  let guideIndex = 0, guideTimer = null;
  function showGuide(index, direction) {
    guideIndex = Math.max(0, Math.min(STEPS.length - 1, index));
    clearInterval(guideTimer);
    const s = STEPS[guideIndex];
    const art = s.art();
    const stage = el("div", { class: "guide-stage" }, art.node);
    const textBox = el("div", {}, el("h3", { text: s.title }), el("p", { class: "text", text: s.body }));
    for (const n of [stage, textBox]) { n.classList.add("slide-in"); n.style.setProperty("--from", (direction < 0 ? -40 : 40) + "px"); }
    const last = guideIndex === STEPS.length - 1;
    guideBox.replaceChildren(el("div", { class: "guide-box", role: "dialog", "aria-modal": "true", "aria-label": "Guide" },
      el("div", { class: "guide-top" }, el("span", { class: "tab", text: `GUIDE ${guideIndex + 1} / ${STEPS.length}` }),
        el("button", { class: "guide-skip", text: "PASSER", onclick: closeGuide })),
      stage, textBox,
      el("div", { class: "guide-dots" }, STEPS.map((_, i) => el("i", { class: i === guideIndex ? "on" : "" }))),
      el("div", { class: "btns" },
        el("button", { class: "btn ghost", text: "Précédent", onclick: () => showGuide(guideIndex - 1, -1),
          style: guideIndex === 0 ? "visibility:hidden" : "" }),
        el("button", { class: "btn next", text: last ? "C'est parti !" : "Suivant",
          onclick: () => last ? closeGuide() : showGuide(guideIndex + 1, 1) }))));
    guideBox.hidden = false;
    let n = 0;
    art.tick(n++);
    guideTimer = setInterval(() => art.tick(n++), 700);
    guideBox.querySelector(".btn.next").focus();
  }
  function closeGuide() {
    clearInterval(guideTimer);
    guideBox.hidden = true;
    store.set("guideSeen", true);
  }

  // ------------------------------------------------------------------ wiring

  document.querySelector('[data-action="dorks"]').addEventListener("click", showDorks);
  document.querySelector('[data-action="exif"]').addEventListener("click", showExif);
  document.querySelector('[data-action="terminal"]').addEventListener("click", showTerminalHelp);
  document.querySelector('[data-action="guide"]').addEventListener("click", () => showGuide(0, 1));

  document.addEventListener("keydown", (e) => {
    if (!guideBox.hidden) {
      if (e.key === "Escape") closeGuide();
      else if (e.key === "ArrowRight") showGuide(guideIndex + 1, 1);
      else if (e.key === "ArrowLeft") showGuide(guideIndex - 1, -1);
      return;
    }
    if (!modal.hidden) { if (e.key === "Escape") closeModal(); return; }
    const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName);
    if (typing) { if (e.key === "Escape") { search.value = ""; route(); document.activeElement.blur(); } return; }
    if (e.key === "/") { e.preventDefault(); search.focus(); }
  });

  route();
  if (!store.get("guideSeen", false)) showGuide(0, 1);

  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }
})();
