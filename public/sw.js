/* ORU is an Android app now, so the site no longer needs an offline shell.
   Any worker left over from the old web app clears itself out here: caches go,
   the registration goes, and open tabs reload onto the download page. */

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(keys.map((key) => caches.delete(key)));
      const tabs = await self.clients.matchAll({ type: "window" });
      tabs.forEach((tab) => {
        if ("navigate" in tab) tab.navigate(tab.url).catch(() => undefined);
      });
      await self.registration.unregister();
    })(),
  );
});

/* No fetch handler on purpose: every request goes to the network. */
