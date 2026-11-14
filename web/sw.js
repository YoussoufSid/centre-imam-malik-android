// Service worker: lets the dashboard be installed as a desktop app and open without network.
// Network-first for our own files (always the latest version when online), cache as fallback.
const CACHE = "imam-malik-v22";
const SHELL = ["./", "./index.html", "./styles.css?v=22", "./app.js?v=22", "./firebase-config.js?v=22", "./logo.png", "./building.jpg",
  "./icon-192.png", "./icon-512.png", "./manifest.webmanifest", "./vendor/qrcode.js"];

// Firebase libraries and fonts come from Google's servers: keep a copy so the dashboard also opens offline.
const FIREBASE = ["app", "auth", "firestore"].map((m) => `https://www.gstatic.com/firebasejs/10.14.1/firebase-${m}.js`);
const isCdn = (u) => u.hostname === "www.gstatic.com" || u.hostname === "fonts.googleapis.com" || u.hostname === "fonts.gstatic.com";

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then(async (c) => {
    await c.addAll(SHELL);
    await Promise.all(FIREBASE.map((u) => fetch(u, { mode: "cors" }).then((r) => r.ok && c.put(u, r)).catch(() => {})));
  }).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET") return;
  if (isCdn(url)) {   // versioned libraries / fonts: cache first, then network (and keep a copy)
    e.respondWith(caches.match(e.request).then((hit) => hit || fetch(e.request).then((res) => {
      if (res.ok || res.type === "opaque") { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
      return res;
    })));
    return;
  }
  if (url.origin !== location.origin) return;  // Firebase data: handled by Firestore's own offline cache
  e.respondWith(fetch(e.request).then((res) => {
    const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); return res;
  }).catch(() => caches.match(e.request).then((r) => r || caches.match("./index.html"))));
});
