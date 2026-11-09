import { useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import { appLinkFor, intentUrl, isAndroid } from "../lib/links";
import { Headphones } from "./icons";

/** Said once and the browser keeps the page for the rest of the visit. */
const OFF_KEY = "oru-app-off";

/**
 * True for the page a tab was opened on, false once someone has clicked around
 * inside the site. React Router numbers its own entries from zero, so a fresh
 * document — a link opened from another app — reads as entry zero.
 */
function isEntryPage(): boolean {
  const state = window.history.state as { idx?: unknown } | null;
  return typeof state?.idx !== "number" || state.idx === 0;
}

/**
 * Hands a link to the app the way Spotify does it.
 *
 * Any page that names something — a song, an artist, a release, a playlist —
 * asks Android for `intent://song/12` as soon as it loads. With ORU installed
 * the app takes over in place of the tab; without it the browser stays on the
 * page and plays. The prompt is only for the two cases the hand-off cannot
 * cover: a browser that blocks intent URLs, and a listener who would rather
 * keep listening here.
 */
export function OpenInApp() {
  const { pathname } = useLocation();
  const link = useMemo(() => (isAndroid() ? appLinkFor(pathname) : null), [pathname]);
  const [prompted, setPrompted] = useState(false);

  useEffect(() => {
    setPrompted(false);
    if (!link || !isEntryPage() || sessionStorage.getItem(OFF_KEY)) return;

    const firedKey = `oru-app-${link.web}`;
    let takenOver = false;
    const watch = () => {
      if (document.visibilityState === "hidden") takenOver = true;
    };
    document.addEventListener("visibilitychange", watch);

    if (!sessionStorage.getItem(firedKey)) {
      sessionStorage.setItem(firedKey, "1");
      window.location.href = intentUrl(link);
    }

    // Still on screen a moment later means the app did not open, so offer the
    // hand-off by hand instead of leaving a page that looks inert.
    const timer = window.setTimeout(() => {
      if (!takenOver) setPrompted(true);
    }, 1400);

    return () => {
      window.clearTimeout(timer);
      document.removeEventListener("visibilitychange", watch);
    };
  }, [link]);

  if (!link || !prompted) return null;

  return (
    <div className="fixed inset-x-4 bottom-[calc(env(safe-area-inset-bottom,0px)+8.5rem)] z-[60] mx-auto flex max-w-md items-start gap-3 rounded-2xl border border-hair bg-card p-4 shadow-float md:right-6 md:left-auto md:bottom-8 md:mx-0">
      <span className="mt-0.5 grid h-9 w-9 shrink-0 place-items-center rounded-full bg-flame-soft text-flame-ink">
        <Headphones className="h-[18px] w-[18px]" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-[14px] font-bold text-ink">Open this in the ORU app</p>
        <p className="mt-0.5 text-[12.5px] leading-snug text-ink-soft">
          It plays offline and keeps going with the screen off.
        </p>
        <div className="mt-3 flex items-center gap-2">
          <button
            type="button"
            onClick={() => {
              window.location.href = intentUrl(link);
            }}
            className="inline-flex h-9 items-center rounded-full bg-flame px-4 text-[13px] font-bold text-cream transition hover:bg-flame-ink press"
          >
            Open the app
          </button>
          <button
            type="button"
            onClick={() => {
              sessionStorage.setItem(OFF_KEY, "1");
              setPrompted(false);
            }}
            className="inline-flex h-9 items-center rounded-full px-3 text-[13px] font-semibold text-ink-soft transition hover:text-ink"
          >
            Not now
          </button>
        </div>
      </div>
    </div>
  );
}
