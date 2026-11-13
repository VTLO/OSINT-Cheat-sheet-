/* Offline cache so the installed PC app works without a connection. */
const CACHE = "vtlosint-v1";
const FILES = ["./", "index.html", "style.css", "app.js", "data.js", "icon.png", "manifest.webmanifest",
  "fonts/vt323.ttf", "fonts/montserrat_bold.ttf", "fonts/montserrat_medium.ttf"];

self.addEventListener("install", (e) => e.waitUntil(caches.open(CACHE).then((c) => c.addAll(FILES))));
self.addEventListener("activate", (e) => e.waitUntil(
  caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))));
self.addEventListener("fetch", (e) => {
  if (new URL(e.request.url).origin !== location.origin) return;
  // Network first so a rebuilt tool list shows up, cache as the offline fallback.
  e.respondWith(fetch(e.request).then((r) => {
    const copy = r.clone();
    caches.open(CACHE).then((c) => c.put(e.request, copy));
    return r;
  }).catch(() => caches.match(e.request)));
});
