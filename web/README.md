# VTLOsint — PC web app

The same deck as the Android app, for the desktop: section card, orange LCD, 8-pad banks, D-pad and
REC / PLAY / STOP keys, plus search, a Google dork builder, an EXIF viewer/remover that runs entirely in
the browser, and copy-ready install commands for the command-line tools (PC terminal or Termux).

**Ready to use:** [`dist/`](dist) — open `dist/index.html`, or download
[`../downloads/VTLOsint-web.zip`](../downloads/VTLOsint-web.zip).

Keyboard: ← → tool · ↑ ↓ section · Enter open · F favorite · Backspace back · `[` `]` bank · `/` search.

## Build

```sh
python3 web/build.py   # src/ + README.md + awesome-article.md -> dist/ and downloads/VTLOsint-web.zip
```

`build.py` parses the Markdown with the same rules as the Android app and embeds the result in
`dist/data.js`, so `index.html` also works opened straight from disk. Fonts: Montserrat and VT323
(SIL Open Font License 1.1, licenses in `dist/fonts`).
