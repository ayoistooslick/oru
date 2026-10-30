import { useEffect, useLayoutEffect, useRef } from "react";
import { ChevronDown, Heart, Loader2, Pause, Play, SkipBack, SkipForward, Volume2 } from "lucide-react";
import gsap from "gsap";
import { Link } from "react-router-dom";
import { mediaUrl } from "../lib/api";
import { motionSafe } from "../lib/motion";
import { useLibrary } from "../state/LibraryContext";
import { usePlayer } from "../state/PlayerContext";
import { useToasts } from "../state/ToastContext";
import { Cover } from "./Cover";
import { ShareButton } from "./ShareButton";
import { Scrubber } from "./PlayerBar";

export function NowPlaying() {
  const {
    current,
    isPlaying,
    isLoading,
    toggle,
    next,
    previous,
    seek,
    position,
    duration,
    setExpanded,
    volume,
    setVolume,
  } = usePlayer();
  const { likedIds, toggleLike } = useLibrary();
  const toasts = useToasts();
  const panel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setExpanded(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [setExpanded]);

  useLayoutEffect(() => {
    const element = panel.current;
    if (!element || motionSafe()) return;
    const ctx = gsap.context(() => {
      const timeline = gsap.timeline();
      timeline
        .from("[data-np-art]", { scale: 0.9, y: 30, opacity: 0, duration: 0.6, ease: "power3.out" }, 0)
        .from("[data-np-line]", { y: 16, opacity: 0, duration: 0.45, stagger: 0.06, ease: "power3.out" }, 0.12)
        .from("[data-np-controls]", { y: 18, opacity: 0, duration: 0.45, stagger: 0.05, ease: "power3.out" }, 0.2);
    }, element);
    return () => ctx.revert();
  }, []);

  if (!current) return null;
  const liked = likedIds.has(current.id);
  const artwork = mediaUrl(current.cover_url);

  return (
    <div
      ref={panel}
      className="fixed inset-0 z-[80] overflow-y-auto bg-black text-white"
      role="dialog"
      aria-modal="true"
      aria-label={`Now playing ${current.title}`}
    >
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-[70vh] scale-125 opacity-45 blur-[90px]"
        style={artwork ? { backgroundImage: `url(${artwork})`, backgroundSize: "cover", backgroundPosition: "center" } : undefined}
        aria-hidden="true"
      />
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-night/70 via-night/85 to-night" aria-hidden="true" />

      <div className="relative mx-auto flex min-h-full w-full max-w-2xl flex-col px-5 pt-[max(env(safe-area-inset-top),1rem)] pb-10 sm:px-8">
        <div className="flex items-center justify-between">
          <button
            type="button"
            onClick={() => setExpanded(false)}
            className="grid h-11 w-11 place-items-center rounded-full bg-white/10 transition press hover:bg-white/20"
            aria-label="Close the player"
          >
            <ChevronDown className="h-6 w-6" />
          </button>            <span className="text-[11px] font-bold uppercase tracking-[0.22em] text-white/70">Now playing</span>
          <button
            type="button"
            onClick={() =>
              void toggleLike(current)
                .then((on) => {
                  if (on) toasts.saved("Added to your likes", current.title);
                })
                .catch(() => toasts.error("Could not save that", "Check your connection and try again."))
            }
            className="grid h-11 w-11 place-items-center rounded-full bg-white/10 transition press hover:bg-white/20"
            aria-label={liked ? "Remove from likes" : "Add to likes"}
            aria-pressed={liked}
          >
            <Heart className="h-5 w-5 text-flame" fill={liked ? "currentColor" : "none"} />
          </button>
          <div className="flex items-center gap-2">
            <ShareButton
              title={`${current.title} by ${current.artist_name}`}
              text={`Listen to ${current.title} by ${current.artist_name} on ORU`}
            />
          </div>
        </div>

        <div data-np-art className="mx-auto mt-8 w-full max-w-[min(78vw,420px)]">
          <Cover
            src={current.cover_url}
            alt={current.title}
            seed={current.id}
            className="aspect-square w-full shadow-[0_40px_90px_-40px_rgba(0,0,0,0.9)]"
            rounded="rounded-lg"
          />
        </div>

        <div className="mt-8">
          <div data-np-line className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <h1 className="truncate text-[26px] font-extrabold leading-tight tracking-[-0.03em] sm:text-[32px]">
                {current.title}
              </h1>
              <Link
                to={`/artist/${current.artist_id}`}
                onClick={() => setExpanded(false)}
                className="mt-1 inline-block truncate text-[16px] text-white/60 transition hover:text-white"
              >
                {current.artist_name}
              </Link>
            </div>
            {isLoading ? <Loader2 className="mt-2 h-5 w-5 shrink-0 animate-spin text-white/60" /> : null}
          </div>

          <div data-np-line className="mt-6">
            <Scrubber position={position} duration={duration} onSeek={seek} />
          </div>

          <div data-np-controls className="mt-6 flex items-center justify-center gap-7">
            <button
              type="button"
              onClick={previous}
              className="grid h-12 w-12 place-items-center rounded-full transition press hover:bg-white/10"
              aria-label="Previous track"
            >
              <SkipBack className="h-6 w-6" fill="currentColor" />
            </button>
            <button
              type="button"
              onClick={toggle}
              className="grid h-[74px] w-[74px] place-items-center rounded-full bg-white text-black transition press hover:scale-105"
              aria-label={isPlaying ? "Pause" : "Play"}
            >
              {isPlaying ? (
                <Pause className="h-8 w-8" fill="currentColor" />
              ) : (
                <Play className="h-8 w-8 translate-x-[2px]" fill="currentColor" />
              )}
            </button>
            <button
              type="button"
              onClick={next}
              className="grid h-12 w-12 place-items-center rounded-full transition press hover:bg-white/10"
              aria-label="Next track"
            >
              <SkipForward className="h-6 w-6" fill="currentColor" />
            </button>
          </div>

          <div data-np-controls className="mt-7 hidden items-center gap-3 text-white/50 sm:flex">
            <Volume2 className="h-4 w-4" />
            <input
              type="range"
              min={0}
              max={1}
              step={0.05}
              value={volume}
              onChange={(event) => setVolume(Number(event.target.value))}
              aria-label="Volume"
              className="scrub h-4 flex-1"
              style={{
                background: `linear-gradient(to right, var(--color-flame) 0%, var(--color-flame) ${volume * 100}%, rgba(255,255,255,0.26) ${volume * 100}%, rgba(255,255,255,0.26) 100%)`,
              }}
            />
          </div>

          {(current.description || current.genres.length > 0) && (
            <div data-np-line className="mt-8 rounded-3xl border border-white/10 bg-white/[0.06] p-5">
              <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-white/45">About this track</p>
              <p className="mt-2 text-[15px] leading-relaxed text-white/80">
                {current.description || "No notes from the artist yet."}
              </p>
              {current.genres.length > 0 ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  {current.genres.map((genre) => (
                    <span key={genre} className="rounded-full bg-white/10 px-3 py-1 text-[12.5px] font-semibold text-white/75">
                      {genre}
                    </span>
                  ))}
                </div>
              ) : null}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
