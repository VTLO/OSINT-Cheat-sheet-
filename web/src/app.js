/* VTLOsint web app — same deck as the Android app: section card, LCD, 8-pad banks, D-pad, REC/PLAY/STOP. */
(function () {
  "use strict";

  const BANK = 8;
  const FAVORITES = "Favorites";
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
    navigator.clipboard.writeText(text).then(() => toast("Copied to clipboard"), () => toast("Copy failed"));
  }

  function host(url) {
    try { return new URL(url).hostname.replace(/^www\./, ""); } catch (e) { return url; }
  }

  // ------------------------------------------------------------------ data

  const sections = window.VTLO_DATA.sections;
  const toolsByUrl = new Map();
  for (const s of sections) for (const t of s.tools) if (!toolsByUrl.has(t.url)) toolsByUrl.set(t.url, t);
  $("subtitle").textContent = `OSINT TOOLKIT · ${toolsByUrl.size} TOOLS · ${sections.length} SECTIONS`;
  $("search").placeholder = `Search ${toolsByUrl.size} tools…`;

  let groups = [];
  function buildGroups() {
    groups = [];
    const favs = store.get("favorites", []).map((u) => toolsByUrl.get(u)).filter(Boolean);
    if (favs.length) groups.push({ title: FAVORITES, entries: favs.map((t) => ({ section: FAVORITES, tool: t })) });
    for (const s of sections) {
      const entries = [];
      if (s.notes) entries.push({ section: s.title, notes: s.notes });
      for (const t of s.tools) entries.push({ section: s.title, tool: t });
      groups.push({ title: s.title, entries });
    }
  }
  buildGroups();

  let group = store.get("group", 0);
  let item = store.get("item", 0);
  const history = [];
  function clamp() {
    if (!(group >= 0 && group < groups.length)) group = 0;
    if (!(item >= 0 && item < groups[group].entries.length)) item = 0;
  }
  clamp();
  const current = () => groups[group].entries[item];
  const isFavorite = (tool) => store.get("favorites", []).includes(tool.url);

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

  // ------------------------------------------------------------------ deck

  const padsBox = $("pads");
  const pads = [];
  for (let i = 0; i < BANK; i++) {
    const pad = el("button", { class: "pad", onclick: () => onPad(i), oncontextmenu: (e) => { e.preventDefault(); select(i); showMenu(); } });
    pads.push(pad);
    padsBox.append(pad);
  }

  function padClass(entry) {
    if (!entry) return "empty";
    if (!entry.tool) return "notes";
    return cliInfo(entry.tool) ? "cli" : "web";
  }

  function remember() {
    history.push([group, item]);
    if (history.length > 50) history.shift();
  }

  function render() {
    clamp();
    const g = groups[group];
    const e = current();
    const bank = Math.floor(item / BANK);
    const banks = Math.ceil(g.entries.length / BANK);
    const tools = g.entries.filter((x) => x.tool).length;

    $("card-tab").textContent = `SECTION ${String(group + 1).padStart(2, "0")} / ${String(groups.length).padStart(2, "0")}`;
    $("card-kicker").textContent = `${tools} ${tools === 1 ? "tool" : "tools"}${tools < g.entries.length ? " · notes" : ""}`;
    $("card-title").textContent = g.title;

    $("lcd-status").textContent = `Tool [${item + 1}/${g.entries.length}]  BANK ${String.fromCharCode(65 + (bank % 26))}${banks > 1 ? "/" + banks : ""}`;
    if (e.tool) {
      const cli = cliInfo(e.tool);
      $("lcd-name").textContent = e.tool.name;
      $("lcd-host").textContent = "Host: " + host(e.tool.url);
      const type = cli ? "CLI · Terminal" : "Web tool";
      $("lcd-type").textContent = e.tool.description ? `${type} · ${e.tool.description}` : "Type: " + type;
    } else {
      $("lcd-name").textContent = "Notes & commands";
      $("lcd-host").textContent = "Host: cheat sheet";
      $("lcd-type").textContent = "Type: Notes";
    }

    for (let i = 0; i < BANK; i++) {
      const entry = g.entries[bank * BANK + i];
      const pad = pads[i];
      pad.className = "pad " + padClass(entry) + (bank * BANK + i === item ? " selected" : "");
      pad.textContent = entry ? (entry.tool ? entry.tool.name : "Notes") : "";
      pad.disabled = !entry;
      pad.title = entry && entry.tool ? entry.tool.name + " — " + host(entry.tool.url) : "";
    }
    $("rec-led").className = "led" + (e.tool && isFavorite(e.tool) ? " on-red" : "");
    store.set("group", group);
    store.set("item", item);
    renderList();
  }

  function move(step) {
    remember();
    item += step;
    if (item < 0) { group = (group - 1 + groups.length) % groups.length; item = groups[group].entries.length - 1; }
    else if (item >= groups[group].entries.length) { group = (group + 1) % groups.length; item = 0; }
    render();
  }
  function jumpGroup(step) { remember(); group = (group + step + groups.length) % groups.length; item = 0; render(); }
  function jumpBank(step) {
    const banks = Math.ceil(groups[group].entries.length / BANK);
    if (banks <= 1) return toast("This section has a single bank");
    remember();
    item = (((Math.floor(item / BANK) + step) % banks + banks) % banks) * BANK;
    render();
  }
  function select(i) {
    const target = Math.floor(item / BANK) * BANK + i;
    if (target >= groups[group].entries.length || target === item) return;
    remember();
    item = target;
    render();
  }
  function onPad(i) {
    if (Math.floor(item / BANK) * BANK + i === item) open(current());
    else select(i);
  }
  function goBack() {
    if (!history.length) return toast("Nothing to go back to");
    [group, item] = history.pop();
    render();
  }

  function open(entry) {
    if (!entry.tool) return showNotes(entry);
    if (cliInfo(entry.tool)) return showMenu(entry.tool);
    window.open(entry.tool.url, "_blank", "noopener");
  }

  function toggleFavorite() {
    const e = current();
    if (!e.tool) return toast("Notes can't be favorited");
    const favs = store.get("favorites", []);
    const i = favs.indexOf(e.tool.url);
    if (i >= 0) favs.splice(i, 1); else favs.push(e.tool.url);
    store.set("favorites", favs);
    const shown = e;
    buildGroups();
    history.length = 0;
    group = 0; item = 0;
    groups.forEach((g, gi) => g.entries.forEach((x, xi) => {
      if (x.section === shown.section && x.tool && x.tool.url === shown.tool.url) { group = gi; item = xi; }
    }));
    render();
    toast(i >= 0 ? "Removed from favorites" : "Added to favorites");
  }

  // D-pad
  const dpad = $("dpad");
  dpad.addEventListener("click", (ev) => {
    const key = ev.target.getAttribute && ev.target.getAttribute("data-key");
    ({ up: () => jumpGroup(-1), down: () => jumpGroup(1), left: () => move(-1), right: () => move(1), ok: () => open(current()) }[key] || (() => {}))();
  });
  dpad.addEventListener("contextmenu", (ev) => {
    if (ev.target.getAttribute("data-key") === "ok") { ev.preventDefault(); showMenu(); }
  });
  function flashKey(key) {
    const zone = dpad.querySelector(`[data-key="${key}"]`);
    if (!zone) return;
    zone.classList.add("active");
    setTimeout(() => zone.classList.remove("active"), 140);
  }

  document.querySelectorAll("[data-move]").forEach((b) => b.addEventListener("click", (e) => { e.stopPropagation(); move(+b.dataset.move); }));
  document.querySelectorAll("[data-bank]").forEach((b) => b.addEventListener("click", () => jumpBank(+b.dataset.bank)));
  document.querySelector('[data-transport="rec"]').addEventListener("click", toggleFavorite);
  document.querySelector('[data-transport="play"]').addEventListener("click", () => open(current()));
  document.querySelector('[data-transport="stop"]').addEventListener("click", goBack);
  $("lcd").addEventListener("click", () => open(current()));
  $("lcd").addEventListener("contextmenu", (e) => { e.preventDefault(); showMenu(); });

  // ------------------------------------------------------------------ list / search

  const search = $("search");
  search.addEventListener("input", renderList);

  function entryRow(entry, isCurrent, onClick) {
    const cli = entry.tool && cliInfo(entry.tool);
    const sub = entry.tool
      ? (cli ? ">_ Terminal · " : "") + host(entry.tool.url) + (entry.tool.description ? " — " + entry.tool.description : "")
      : entry.notes.split("\n")[0];
    return el("button", { class: "row" + (isCurrent ? " current" : ""), onclick: onClick,
      oncontextmenu: (e) => { if (entry.tool) { e.preventDefault(); showMenu(entry.tool); } } },
      el("strong", { text: entry.tool ? entry.tool.name : "≡ Notes, queries & commands" }),
      el("small", { class: cli ? "cli" : "", text: sub }));
  }

  function jumpTo(entry) {
    groups.forEach((g, gi) => {
      const xi = g.entries.indexOf(entry);
      if (xi >= 0 && (gi !== group || xi !== item)) { remember(); group = gi; item = xi; }
    });
    render();
  }

  function renderList() {
    const list = $("list");
    list.replaceChildren();
    const q = search.value.trim().toLowerCase();
    if (!q) {
      groups[group].entries.forEach((entry, i) => list.append(entryRow(entry, i === item, () => {
        if (i === item) open(entry); else { remember(); item = i; render(); }
      })));
      return;
    }
    let hits = 0;
    for (const g of groups) {
      const matches = g.entries.filter((e) => e.tool
        ? [e.tool.name, e.tool.url, e.tool.description, g.title].some((x) => x && x.toLowerCase().includes(q))
        : e.notes.toLowerCase().includes(q) || g.title.toLowerCase().includes(q));
      if (!matches.length) continue;
      hits += matches.length;
      list.append(el("div", { class: "list-head" }, el("span", { class: "tab", text: g.title.toUpperCase() }), el("b", { text: String(matches.length) })));
      for (const m of matches) list.append(entryRow(m, false, () => { jumpTo(m); open(m); }));
    }
    if (!hits) list.append(el("p", { class: "empty-note", text: "No tool matches “" + search.value + "”." }));
  }

  // ------------------------------------------------------------------ modals

  const modal = $("modal");
  function showModal(...content) {
    $("modal-body").replaceChildren(...content);
    modal.hidden = false;
    const focusable = modal.querySelector("input, select, textarea, button:not(.modal-close)");
    if (focusable) focusable.focus();
  }
  function closeModal() { modal.hidden = true; }
  modal.addEventListener("click", (e) => { if (e.target === modal || e.target.classList.contains("modal-close")) closeModal(); });

  function showMenu(tool) {
    tool = tool || current().tool;
    if (!tool) return showNotes(current());
    const info = cliInfo(tool);
    const content = [el("h3", { text: tool.name }), el("p", { class: "kicker", text: tool.url })];
    if (info) {
      let target = "pc";
      const pre = el("pre", { class: "lcd-text" });
      const tabs = el("div", { class: "tabs-row" });
      const setTarget = (t) => {
        target = t;
        pre.textContent = script(info, t);
        tabs.querySelectorAll("button").forEach((b) => b.classList.toggle("on", b.dataset.t === t));
      };
      tabs.append(el("button", { "data-t": "pc", text: "PC terminal", onclick: () => setTarget("pc") }),
        el("button", { "data-t": "termux", text: "Android · Termux", onclick: () => setTarget("termux") }));
      content.push(el("label", { text: "Install & run from a terminal" }), tabs, pre,
        el("div", { class: "btns" }, el("button", { class: "key-btn primary", text: "Copy command", onclick: () => copy(pre.textContent) })));
      setTarget("pc");
    }
    const fav = isFavorite(tool);
    const items = [
      ["Open in a new tab", () => window.open(tool.url, "_blank", "noopener")],
      ["Open archived copy (Wayback Machine)", () => window.open("https://web.archive.org/web/" + tool.url, "_blank", "noopener")],
      [fav ? "Remove from favorites" : "Add to favorites", () => {
        const e = groups[group].entries.find((x) => x.tool === tool);
        if (e) { item = groups[group].entries.indexOf(e); toggleFavorite(); }
        else {
          const favs = store.get("favorites", []);
          if (fav) favs.splice(favs.indexOf(tool.url), 1); else favs.push(tool.url);
          store.set("favorites", favs); buildGroups(); render();
        }
        closeModal();
      }],
      ["Copy link", () => copy(tool.url)],
    ];
    for (const [label, action] of items) content.push(el("button", { class: "menu-item", text: label, onclick: action }));
    showModal(...content);
  }

  function showNotes(entry) {
    showModal(el("h3", { text: entry.section }), el("pre", { class: "lcd-text", text: entry.notes }),
      el("div", { class: "btns" }, el("button", { class: "key-btn primary", text: "Copy all", onclick: () => copy(entry.notes) })));
  }

  // Dork builder — operators and templates from the cheat sheet.
  const TEMPLATES = [
    ["Dork · Text on a site", 'intext:"{T}" site:example.com'],
    ["Dork · Documents on a site", "site:{T} ext:doc | ext:docx | ext:odt | ext:rtf | ext:sxw | ext:psw | ext:ppt | ext:pptx | ext:pps | ext:csv"],
    ["Dork · Open directory (index of)", 'intitle:"index of" "{T}"'],
    ["Dork · All words in title", "allintitle: {T}"],
    ["Dork · All words in URL", "allinurl: {T}"],
    ["Dork · Pages linking to", "link:{T}"],
    ["Dork · Define term", "define:{T}"],
    ["Facebook · Group search", 'site:facebook.com inurl:group "{T}"'],
    ["Facebook · Group wall posts", 'site:facebook.com inurl:wall "{T}"'],
    ["Facebook · Pages", 'site:facebook.com inurl:pages "{T}"'],
    ["Facebook · Public profiles", 'allinurl: people "{T}" site:facebook.com'],
    ["Facebook · By ID", "id {T} site:facebook.com *"],
    ...["photos-by", "photos-liked", "photos-of", "photos-commented", "friends", "videos", "videos-by", "videos-liked",
      "videos-commented", "events-joined", "relatives"].map((p) => ["FQL · " + p + " (ID)", "https://www.facebook.com/search/{T}/" + p]),
    ["LinkedIn · Public profiles", 'site:linkedin.com inurl:pub "{T}"'],
    ["LinkedIn · Updated profiles", 'site:linkedin.com inurl:updates "{T}"'],
    ["LinkedIn · Company profiles", 'site:linkedin.com inurl:companies "{T}"'],
    ["MySpace · Profiles", 'site:myspace.com inurl:profile "{T}"'],
    ["MySpace · Blogs", 'site:myspace.com inurl:blogs "{T}"'],
    ["MySpace · Videos", 'site:myspace.com "{T}" "videos"'],
    ["Telegram · Channels & users", 'site:t.me "{T}"'],
    ["Pastebin · Mentions", 'site:pastebin.com "{T}"'],
  ];
  const ENGINES = [["Google", "https://www.google.com/search?q="], ["Bing", "https://www.bing.com/search?q="],
    ["DuckDuckGo", "https://duckduckgo.com/?q="], ["Yandex", "https://yandex.com/search/?text="]];

  function showDorks() {
    const target = el("input", { placeholder: "e.g. johndoe or example.com" });
    const tpl = el("select", {}, el("option", { value: "", text: "— Pick a template —" }), TEMPLATES.map(([l], i) => el("option", { value: String(i), text: l })));
    const ops = ["site", "inurl", "intitle", "intext", "filetype"].map((o) => [o, el("input", { placeholder: o + ":" })]);
    const exclude = el("input", { placeholder: "exclude words (-word)" });
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
      if (!q) return toast("Generate or type a query first");
      window.open(/^https?:\/\//.test(q) ? q : base + encodeURIComponent(q), "_blank", "noopener");
    };
    const opGrid = el("div", {}, ops.map(([, i]) => i), exclude);
    opGrid.style.display = "grid";
    opGrid.style.gridTemplateColumns = "1fr 1fr";
    opGrid.style.gap = "8px";
    showModal(el("h3", { text: "Dork Builder" }),
      el("label", { text: "Target (name, username, domain, Facebook ID…)" }), target,
      el("label", { text: "Template" }), tpl,
      el("label", { text: "Or combine operators" }), opGrid,
      el("div", { class: "btns" }, el("button", { class: "key-btn", text: "Generate query", onclick: generate })),
      el("label", { text: "Query" }), query,
      el("div", { class: "btns" }, ENGINES.map(([n, u]) => el("button", { class: "key-btn primary", text: n, onclick: () => go(u) })),
        el("button", { class: "key-btn", text: "Copy", onclick: () => copy(query.value) })));
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
    if (v.byteLength < 4 || v.getUint16(0) !== 0xffd8) throw new Error("Only JPEG photos carry EXIF this tool can read");
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
      out.replaceChildren(el("p", { class: "kicker", text: file.name + " · " + file.type }));
      let tags = {};
      try { tags = readExif(await file.arrayBuffer()); } catch (err) { out.append(el("p", { text: err.message })); }
      const where = gps(tags);
      if (where) {
        const [lat, lon] = where.map((x) => x.toFixed(6));
        out.append(el("p", { text: `⚠ GPS location: ${lat}, ${lon}` }),
          el("div", { class: "btns" }, el("button", { class: "key-btn primary", text: "Show on map",
            onclick: () => window.open(`https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=16/${lat}/${lon}`, "_blank", "noopener") })));
      }
      const names = Object.keys(tags);
      out.append(names.length
        ? el("table", { class: "exif" }, names.map((k) => el("tr", {}, el("td", { text: k }),
          el("td", { text: Array.isArray(tags[k]) ? tags[k].map((x) => +x.toFixed ? +x.toFixed(4) : x).join(", ") : String(tags[k]) }))))
        : el("p", { class: "kicker", text: "No EXIF metadata found." }));
      out.append(el("div", { class: "btns" }, el("button", { class: "key-btn", text: "Download clean copy", onclick: () => cleanCopy(file) })));
    });
    showModal(el("h3", { text: "EXIF Metadata Viewer / Remover" }),
      el("p", { class: "kicker", text: "Read camera, date and GPS tags. The clean copy is re-encoded without any metadata, like exiftool -all=. Nothing leaves your computer." }),
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
        toast("Clean copy saved");
      }, "image/jpeg", 0.95);
    };
    img.onerror = () => toast("This image format can't be cleaned here");
    img.src = url;
  }

  // ------------------------------------------------------------------ French guide

  const guideBox = $("guide");
  function demoDpad() {
    return dpad.cloneNode(true);
  }
  const STEPS = [
    {
      title: "Bienvenue sur VTLOsint",
      body: "Plus de 550 outils OSINT réunis dans une seule application, rangés par sections. Version PC et Android.",
      art() {
        const icon = el("img", { src: "icon.png", alt: "", width: "110", height: "110", class: "pulse" });
        const name = el("div", { class: "lcd-name" });
        const lcd = el("div", { class: "bezel" }, el("div", { class: "lcd" }, el("div", { class: "lcd-body" }, name)));
        lcd.style.width = "240px";
        lcd.style.marginTop = "18px";
        const box = el("div", {}, icon, lcd);
        box.style.textAlign = "center";
        return { node: box, tick: (n) => { const w = "VTLOSINT"; name.textContent = w.slice(0, Math.min(n % (w.length + 4), w.length)) + (n % 2 ? " " : "_"); } };
      },
    },
    {
      title: "Les pads",
      body: "Chaque section affiche ses outils par banque de 8 pads. Gris = site web · Vert = outil en ligne de commande · Jaune = notes. Un clic choisit, un second clic ouvre.",
      art() {
        const kinds = ["web", "web", "cli", "notes", "web", "cli", "web", "empty"];
        const labels = ["Shodan", "Wayback", "Sherlock", "Notes", "TinEye", "Maigret", "Pimeyes", ""];
        const grid = el("div", { class: "pads demo-pads" }, labels.map((l, i) => el("div", { class: "pad " + kinds[i], text: l })));
        return { node: grid, tick: (n) => [...grid.children].forEach((p, i) => p.classList.toggle("selected", i === n % 7)) };
      },
    },
    {
      title: "La croix directionnelle",
      body: "Comme sur un Flipper Zero : ◀ ▶ changent d’outil, ▲ ▼ de section, ● ouvre l’outil. Au clavier : flèches, Entrée, F (favori), Retour arrière.",
      art() {
        const pad = demoDpad();
        pad.removeAttribute("id");
        pad.style.width = pad.style.height = "170px";
        const cap = el("div", { class: "caption" });
        const keys = [["right", "▶  Outil suivant"], ["left", "◀  Outil précédent"], ["down", "▼  Section suivante"],
          ["up", "▲  Section précédente"], ["ok", "●  Ouvrir · clic droit : options"], ["", " "]];
        const box = el("div", {}, pad, cap);
        box.style.textAlign = "center";
        return { node: box, tick: (n) => {
          const [k, t] = keys[n % keys.length];
          pad.querySelectorAll(".dpad-zone").forEach((z) => z.classList.toggle("active", z.dataset.key === k));
          cap.textContent = t;
        } };
      },
    },
    {
      title: "REC · PLAY · STOP",
      body: "Les touches de la boîte à rythmes pilotent l’outil sélectionné. Le voyant rouge de REC s’allume quand l’outil est dans vos favoris.",
      art() {
        const led = el("b", { class: "led on-red blink" });
        const row = el("div", { class: "transport" },
          el("div", { class: "tkey rec" }, el("span", {}, led, "REC"), el("i")),
          el("div", { class: "tkey" }, el("span", {}, el("b", { class: "led on-green" }), "PLAY"), el("i")),
          el("div", { class: "tkey" }, el("span", { text: "STOP" }), el("i")));
        const chassis = el("div", { class: "chassis" }, row);
        chassis.style.position = "static";
        chassis.style.width = "300px";
        const cap = el("div", { class: "caption" });
        const caps = ["REC  →  ajouter aux favoris", "PLAY  →  ouvrir l’outil", "STOP  →  revenir à l’outil précédent", "BANK ◀ ▶  →  changer de banque"];
        return { node: el("div", {}, chassis, cap), tick: (n) => { cap.textContent = caps[Math.floor(n / 2) % caps.length]; } };
      },
    },
    {
      title: "Outils intégrés",
      body: "Recherche dans tous les outils, générateur de Google Dorks, lecteur et nettoyeur EXIF, et commandes prêtes à copier pour installer Sherlock, Maigret, GHunt…",
      art() {
        const nav = document.querySelector(".icons").cloneNode(true);
        nav.querySelectorAll("button").forEach((b) => b.setAttribute("tabindex", "-1"));
        return { node: nav, tick: (n) => {
          const b = nav.children[n % nav.children.length];
          b.classList.remove("bounce");
          void b.offsetWidth;
          b.classList.add("bounce");
        } };
      },
    },
    {
      title: "Prêt ?",
      body: "Utilisez ces outils légalement, uniquement sur des cibles que vous êtes autorisé à étudier. Ce guide reste disponible avec le bouton Guide.",
      art() {
        const h = el("h2", { text: "Chercher, vérifier, documenter." });
        const card = el("div", { class: "card" }, el("span", { class: "tab", text: "OSINT ÉTHIQUE" }), el("p", { class: "kicker", text: "Mission" }), h);
        card.style.width = "320px";
        h.style.fontSize = "20px";
        return { node: card, tick: (n) => { h.style.opacity = n % 2 ? "0.55" : "1"; h.style.transition = "opacity .6s"; } };
      },
    },
  ];

  let guideIndex = 0, guideTimer = null;
  function showGuide(index, direction) {
    guideIndex = Math.max(0, Math.min(STEPS.length - 1, index));
    clearInterval(guideTimer);
    const step = STEPS[guideIndex];
    const art = step.art();
    const stage = el("div", { class: "guide-stage" }, art.node);
    const textBox = el("div", {}, el("h3", { text: step.title }), el("p", { text: step.body }));
    for (const n of [stage, textBox]) { n.classList.add("slide-in"); n.style.setProperty("--from", (direction < 0 ? -40 : 40) + "px"); }
    const last = guideIndex === STEPS.length - 1;
    guideBox.replaceChildren(el("div", { class: "guide-box", role: "dialog", "aria-modal": "true", "aria-label": "Guide" },
      el("div", { class: "guide-top" }, el("span", { class: "tab", text: `GUIDE ${guideIndex + 1} / ${STEPS.length}` }),
        el("button", { class: "guide-skip", text: "PASSER", onclick: closeGuide })),
      stage, textBox,
      el("div", { class: "guide-dots" }, STEPS.map((_, i) => el("i", { class: i === guideIndex ? "on" : "" }))),
      el("div", { class: "btns" },
        el("button", { class: "key-btn", text: "Précédent", onclick: () => showGuide(guideIndex - 1, -1),
          style: guideIndex === 0 ? "visibility:hidden" : "" }),
        el("button", { class: "key-btn primary", text: last ? "C'est parti !" : "Suivant",
          onclick: () => last ? closeGuide() : showGuide(guideIndex + 1, 1) }))));
    guideBox.hidden = false;
    let n = 0;
    art.tick(n++);
    guideTimer = setInterval(() => art.tick(n++), 650);
    guideBox.querySelector(".key-btn.primary").focus();
  }
  function closeGuide() {
    clearInterval(guideTimer);
    guideBox.hidden = true;
    store.set("guideSeen", true);
  }

  // ------------------------------------------------------------------ wiring

  document.querySelector('[data-action="search"]').addEventListener("click", () => { search.focus(); search.select(); });
  document.querySelector('[data-action="dorks"]').addEventListener("click", showDorks);
  document.querySelector('[data-action="exif"]').addEventListener("click", showExif);
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
    if (typing) { if (e.key === "Escape") document.activeElement.blur(); return; }
    const keys = {
      ArrowLeft: () => { flashKey("left"); move(-1); }, ArrowRight: () => { flashKey("right"); move(1); },
      ArrowUp: () => { flashKey("up"); jumpGroup(-1); }, ArrowDown: () => { flashKey("down"); jumpGroup(1); },
      Enter: () => { flashKey("ok"); open(current()); }, f: toggleFavorite, F: toggleFavorite, Backspace: goBack,
      "/": () => { search.focus(); }, "[": () => jumpBank(-1), "]": () => jumpBank(1),
    };
    if (keys[e.key]) { e.preventDefault(); keys[e.key](); }
  });

  render();
  if (!store.get("guideSeen", false)) showGuide(0, 1);

  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }
})();
