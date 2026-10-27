# VTLOsint — Android app

An all-in-one Android app that puts **every tool listed in this repository** in your pocket.

**Download:** [`release/VTLOsint.apk`](release/VTLOsint.apk) (Android 8.0+, ~450 KB, no trackers, no dependencies).
CI also builds a fresh APK on every change to the cheat sheet (Actions → *Android APK* → artifact).

## What it does

| Kind of tool | How it runs on the phone |
| --- | --- |
| **Web tools** (Shodan, VirusTotal, TinEye, Pimeyes, Wayback, …) | Built-in OSINT browser: address bar, file upload (reverse image search), Wayback copy, share, open externally |
| **Command-line tools** (Sherlock, Maigret, GHunt, theHarvester, recon-ng, PhoneInfoga, ExifTool, testssl.sh, every GitHub repo in the list, …) — marked ⌨ | One tap sends an install-and-run script to [Termux](https://f-droid.org/packages/com.termux/). The script is shown (and editable) before it runs |
| **Notes & commands** (EXIF commands, dorks, Facebook/LinkedIn/MySpace queries, FQL, Shodan) | Readable, copyable note pages |
| **Built-in tools** | Google **Dork Builder** (operators + all query templates from the cheat sheet, searches Google/Bing/DuckDuckGo/Yandex) and an **EXIF viewer/remover** (shows GPS on a map, saves a metadata-free copy — like `exiftool -all=`) |

The home screen is styled like a Flipper Zero remote: an orange LCD shows one tool at a time,
the D-pad steps through tools (◀ ▶) and categories (▲ ▼), ● opens the tool (hold for options) and ↩ goes back.

Plus: instant search across all tools, collapsible categories, favorites, and the *Awesome Article* reading list.

The tool list is **not duplicated** in the app: `README.md` and `awesome-article.md` are bundled at build
time and parsed at runtime, so anything contributed to the cheat sheet appears in the next build automatically.

## Running command-line tools (Termux)

1. Install **Termux** from [F-Droid](https://f-droid.org/packages/com.termux/) (the Play Store build is outdated) and open it once.
2. In Termux run:
   ```sh
   mkdir -p ~/.termux && echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings
   ```
3. Android 10+: allow Termux *Display over other apps*.
4. Tap a ⌨ tool → **Install & run in Termux** and grant the *Run commands in Termux* permission.

The generic script clones the repo into `~/<repo>` and installs dependencies based on what it finds
(`requirements.txt`/`setup.py` → pip, `go.mod` → Go, `package.json` → npm, `Gemfile` → Bundler), then leaves
you in a shell in that folder. Some heavy tools (e.g. `face_recognition`, which needs dlib) may need extra packages.

## Build from source

```sh
cd android
./gradlew assembleRelease      # needs JDK 17+ and the Android SDK (platform 34)
# -> app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the Android debug key so it installs directly; sign it with your own key for distribution.

> Use these tools legally and ethically, only on targets you are authorized to investigate.
