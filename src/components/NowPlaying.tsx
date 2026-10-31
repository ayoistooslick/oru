import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { ChevronDown, Heart, Loader2, MoreHorizontal, Pause, Play, Repeat, Shuffle, SkipBack, SkipForward, Volume2, X } from "lucide-react";
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
    expanded,
    setExpanded,
    volume,
    setVolume,
    shuffle,
    repeatMode,
    toggleShuffle,
    cycleRepeat,
  } = usePlayer();
  const { likedIds, toggleLike } = useLibrary();
  const toasts = useToasts();
  const panel = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const [actionsOpen, setActionsOpen] = useState(false);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        if (actionsOpen) setActionsOpen(false);
        else setExpanded(false);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [actionsOpen, setExpanded]);

  useEffect(() => {
    if (!actionsOpen) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setActionsOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [actionsOpen]);

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

  // The panel only exists while it is open. It used to render whenever a song
  // was loaded, so the close button flipped a flag nothing was reading.
  if (!current || !expanded) return null;
  const liked = likedIds.has(current.id);
  const close = () => {
    setActionsOpen(false);
    setExpanded(false);
  };
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
            onClick={close}
            className="grid h-11 w-11 place-items-center rounded-full bg-white/10 transition press hover:bg-white/20"
            aria-label="Close the player"
          >
            <ChevronDown className="h-6 w-6" />
          </button>
          <span className="text-[11px] font-bold uppercase tracking-[0.22em] text-white/70">Now playing</span>
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
          <div ref={menuRef} className="relative">
            <button
              type="button"
              onClick={() => setActionsOpen((open) => !open)}
              className="grid h-11 w-11 place-items-center rounded-full bg-white/10 transition press hover:bg-white/20"
              aria-label={actionsOpen ? "Hide track menu" : "Show track menu"}
              aria-expanded={actionsOpen}
              aria-controls="now-playing-actions"
            >
              {actionsOpen ? <X className="h-5 w-5" /> : <MoreHorizontal className="h-5 w-5" />}
            </button>
            {actionsOpen ? (
              <div id="now-playing-actions" role="menu" className="absolute right-0 top-12 z-30 w-52 rounded-xl border border-white/10 bg-[#282828] p-2 shadow-xl">
                <div className="flex items-center justify-between gap-3 rounded-lg px-3 py-2 text-[14px] font-semibold">
                  <span>Share this song</span>
                  <ShareButton
                    title={`${current.title} by ${current.artist_name}`}
                    text={`Listen to ${current.title} by ${current.artist_name} on ORU`}
                    className="grid h-9 w-9 place-items-center rounded-full text-white transition hover:bg-white/10"
                  />
                </div>
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => setActionsOpen(false)}
                  className="w-full rounded-lg px-3 py-2 text-left text-[13.5px] font-semibold text-ink-soft transition hover:bg-white/10 hover:text-white"
                >
                  Hide menu
                </button>
              </div>
            ) : null}
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
              <p className="mt-1 truncate text-[16px] text-white/60">
                <Link to={`/artist/${current.artist_id}`} onClick={close} className="transition hover:text-white hover:underline">
                  {current.artist_name}
                </Link>
                {current.featuring?.length ? (
                  <span className="text-white/45"> feat. {current.featuring.map((guest) => guest.name).join(", ")}</span>
                ) : null}
              </p>
              {current.status === "published" && current.shared_by ? (
                <p className="mt-1 text-[13px] text-white/55">Shared by {current.shared_by.display_name}</p>
              ) : null}
            </div>
            {isLoading ? <Loader2 className="mt-2 h-5 w-5 shrink-0 animate-spin text-white/60" /> : null}
          </div>

          <div data-np-line className="mt-6">
            <Scrubber position={position} duration={duration} onSeek={seek} />
          </div>

          <div data-np-controls className="mt-6 flex items-center justify-center gap-1 sm:gap-5">
            <button
              type="button"
              onClick={toggleShuffle}
              className={`grid h-10 w-10 place-items-center rounded-full transition hover:bg-white/10 ${shuffle ? "text-[#1ed760]" : "text-white/55"}`}
              aria-label={shuffle ? "Turn shuffle off" : "Turn shuffle on"}
              aria-pressed={shuffle}
            >
              <Shuffle className="h-4 w-4" />
            </button>
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
            <button
              type="button"
              onClick={cycleRepeat}
              className={`relative grid h-10 w-10 place-items-center rounded-full transition hover:bg-white/10 ${repeatMode !== "off" ? "text-[#1ed760]" : "text-white/55"}`}
              aria-label={`Repeat ${repeatMode}; change mode`}
            >
              <Repeat className="h-4 w-4" />
              {repeatMode === "one" ? <span className="absolute right-1 top-0 text-[9px] font-black">1</span> : null}
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
