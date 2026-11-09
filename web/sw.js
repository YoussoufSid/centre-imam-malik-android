// Service worker: lets the dashboard be installed as a desktop app and open without network.
// Network-first for our own files (always the latest version when online), cache as fallback.
const CACHE = "imam-malik-v4";
const SHELL = ["./", "./index.html", "./styles.css?v=4", "./app.js?v=4", "./firebase-config.js?v=4", "./logo.png",
  "./icon-192.png", "./icon-512.png", "./manifest.webmanifest"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin) return;  // Firebase & fonts: browser default
  e.respondWith(fetch(e.request).then((res) => {
    const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); return res;
  }).catch(() => caches.match(e.request).then((r) => r || caches.match("./index.html"))));
});
