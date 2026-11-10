/**
 * Every place that needs to point at the app, or at the app from the web.
 *
 * The APK lives behind one short link so a new build is a single edit here
 * instead of a hunt through the pages.
 */
export const APK_URL = "https://is.gd/qrRMnN";

/** The scheme the Android app registers, e.g. oru://song/12. */
export const APP_SCHEME = "oru";

export const APP_PACKAGE = "com.oru.app";

export function isAndroid(): boolean {
  return /android/i.test(navigator.userAgent);
}

/** The web address a song is shared as; the app links to the same page. */
export function songWebUrl(songId: number): string {
  return `${window.location.origin}/song/${songId}`;
}

/** A link with a screen on both sides of the fence. */
export interface AppLink {
  /** Where the app is sent, e.g. oru://song/12. */
  app: string;
  /** The page a browser without the app stays on. */
  web: string;
}

/**
 * The app address for a web path, or null when the app has no screen for it.
 *
 * Only pages that name something get a link: a shared song, an artist, a
 * release, a playlist. The front door, the settings page and the studio stay
 * in the browser, so landing on oru.freebuff.app never yanks anyone away.
 */
export function appLinkFor(pathname: string): AppLink | null {
  const parts = pathname.split("/").filter(Boolean);
  if (parts.length !== 2) return null;
  const [kind, id] = parts;
  if (!/^\d+$/.test(id)) return null;
  if (kind === "song") return { app: `${APP_SCHEME}://song/${id}`, web: `/song/${id}` };
  if (kind === "artist") return { app: `${APP_SCHEME}://artist/${id}`, web: `/artist/${id}` };
  if (kind === "release") return { app: `${APP_SCHEME}://release/${id}`, web: `/release/${id}` };
  // The app calls a saved playlist a playlist, whatever the URL says.
  if (kind === "library") return { app: `${APP_SCHEME}://playlist/${id}`, web: `/library/${id}` };
  return null;
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

/** Hands the current page to the app, or leaves the browser where it is. */
export function openInApp(link: AppLink): void {
  window.location.href = intentUrl(link);
}
