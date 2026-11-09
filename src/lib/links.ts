/**
 * Every place that needs to point at the app, or at the app from the web.
 *
 * The APK lives behind one short link so a new build is a single edit here
 * instead of a hunt through the pages.
 */
export const APK_URL = "https://is.gd/WNgSQY";

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

export function songDeepLink(songId: number): string {
  return `${APP_SCHEME}://song/${songId}`;
}

/**
 * An Android intent URL. Chrome resolves it against the installed app and
 * opens it directly, without the digital asset link verification that plain
 * https links need — which matters because every build is signed fresh.
 * Falls back to the web page when the app is not installed.
 */
export function songIntentUrl(songId: number, fallback: string): string {
  return (
    `intent://song/${songId}#Intent;scheme=${APP_SCHEME};package=${APP_PACKAGE};` +
    `S.browser_fallback_url=${encodeURIComponent(fallback)};end`
  );
}
