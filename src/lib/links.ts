/**
 * Every place that needs to point at the app, or at the app from the web.
 *
 * The APK lives behind one short link so a new build is a single edit here
 * instead of a hunt through the pages.
 */
export const APK_URL = "https://is.gd/P2A3Io";

/** The scheme the Android app registers, e.g. oru://song/12. */
export const APP_SCHEME = "oru";

export const APP_PACKAGE = "com.oru.app";

/** Where the site is published; used when a link has no window to read from. */
export const SITE_ORIGIN = "https://oru.freebuff.app";

export function isAndroid(): boolean {
  return /android/i.test(navigator.userAgent);
}

/**
 * Whether this browser understands `intent://` addresses.
 *
 * Chrome, Edge, Samsung Internet, Opera and every in-app browser built on
 * Chromium resolve them against the installed app. Firefox and its forks do
 * not, which is why the plain `oru://` scheme is the fallback rather than an
 * afterthought.
 */
export function usesIntentUrls(): boolean {
  return /Chrome\/|Chromium\/|CriOS\/|SamsungBrowser\/|EdgA\/|EdgiOS\/|OPR\/|OculusBrowser\//.test(
    navigator.userAgent,
  );
}

/** The web address a song is shared as; the app links to the same page. */
export function songWebUrl(songId: number): string {
  return `${window.location.origin}/song/${songId}`;
}

/** A link with a screen on both sides of the fence. */
export interface AppLink {
  /** Where the app is sent, e.g. oru://song/12. */
  app: string;
  /** The page a browser without the app stays on, path and query. */
  web: string;
  /** What the link names, for the "open in the app" prompt. */
  label: string;
}

/**
 * The app address for a web path, or null when the app has no screen for it.
 *
 * Every content page and every tab has a destination, so a link shared from
 * anywhere — a song, an artist, a release, a playlist, search, the studio, the
 * bell — lands on the screen it names instead of the front page. The two
 * exceptions are deliberate: the landing page keeps the download button, and
 * sign-in stays on the web because the app has its own auth screen and a
 * half-finished hand-off between the two would lose the reason for the visit.
 */
export function appLinkFor(pathname: string, search = ""): AppLink | null {
  const parts = pathname.split("/").filter(Boolean);
  const query = new URLSearchParams(search);

  if (parts.length === 0) return null;

  const [kind, id] = parts;

  if (parts.length === 2 && /^\d+$/.test(id)) {
    if (kind === "song") {
      return { app: `${APP_SCHEME}://song/${id}`, web: `/song/${id}`, label: "this song" };
    }
    if (kind === "artist") {
      return { app: `${APP_SCHEME}://artist/${id}`, web: `/artist/${id}`, label: "this artist" };
    }
    if (kind === "release") {
      return { app: `${APP_SCHEME}://release/${id}`, web: `/release/${id}`, label: "this release" };
    }
    // The app calls a saved playlist a playlist, whatever the URL says.
    if (kind === "library") {
      return { app: `${APP_SCHEME}://playlist/${id}`, web: `/library/${id}`, label: "this playlist" };
    }
    return null;
  }

  if (parts.length !== 1) return null;

  switch (kind) {
    case "listen":
      // The app's front door is its home tab.
      return { app: `${APP_SCHEME}://listen`, web: "/listen", label: "ORU" };
    case "library":
      return { app: `${APP_SCHEME}://library`, web: "/library", label: "your library" };
    case "studio":
      return { app: `${APP_SCHEME}://studio`, web: "/studio", label: "the studio" };
    case "notifications":
      return { app: `${APP_SCHEME}://notifications`, web: "/notifications", label: "your drops" };
    case "settings":
      return { app: `${APP_SCHEME}://settings`, web: "/settings", label: "your settings" };
    case "shares":
      return { app: `${APP_SCHEME}://shares`, web: "/shares", label: "shared songs" };
    case "moderation":
      return { app: `${APP_SCHEME}://moderation`, web: "/moderation", label: "the review queue" };
    case "about":
      return { app: `${APP_SCHEME}://about`, web: "/about", label: "about ORU" };
    case "search": {
      // A search link has to keep its words: the app opens on the results.
      const term = (query.get("q") || "").trim();
      const suffix = term ? `?q=${encodeURIComponent(term)}` : "";
      return {
        app: `${APP_SCHEME}://search${suffix}`,
        web: `/search${suffix}`,
        label: term ? `"${term}"` : "search",
      };
    }
    // /auth and /share are web flows; the first has an app equivalent that is
    // already signed in, the second is how a browser hands a link to the app.
    default:
      return null;
  }
}

/**
 * An Android intent URL. Chrome resolves it against the installed app and
 * opens it directly, without the digital asset link verification that plain
 * https links need — which matters because every build is signed fresh.
 * Falls back to the web page when the app is not installed.
 */
export function intentUrl(link: AppLink, origin = window.location.origin): string {
  const path = link.app.slice(`${APP_SCHEME}://`.length);
  return (
    `intent://${path}#Intent;scheme=${APP_SCHEME};package=${APP_PACKAGE};` +
    `S.browser_fallback_url=${encodeURIComponent(`${origin}${link.web}`)};end`
  );
}

/**
 * The address to navigate to for this browser: an intent URL where the
 * browser understands one, the bare scheme where it does not (Firefox opens
 * the app through the scheme and stays put when the app is missing).
 */
export function handoffUrl(link: AppLink, origin = window.location.origin): string {
  return usesIntentUrls() ? intentUrl(link, origin) : link.app;
}

/** Hands the current page to the app, or leaves the browser where it is. */
export function openInApp(link: AppLink): void {
  window.location.href = handoffUrl(link);
}
