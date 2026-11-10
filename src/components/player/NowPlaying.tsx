import { useEffect, useLayoutEffect, useRef, useState } from "react";
import gsap from "gsap";
import { Link } from "react-router-dom";
import { formatDuration } from "../../lib/format";
import { motionSafe } from "../../lib/motion";
import { useLibrary } from "../../state/LibraryContext";
import { usePlayer } from "../../state/PlayerContext";
import { useToasts } from "../../state/ToastContext";
import { Cover } from "../media/Cover";
import { ShareButton } from "../song/ShareButton";
import {
  ChevronDown,
  Heart,
  Loader2,
  Pause,
  Play,
  PlaylistAdd,
  Queue,
  Repeat,
  Shuffle,
  SkipBack,
  SkipForward,
  Timer,
  Volume2,
} from "../ui/icons";
import { PlaylistPicker } from "./PlaylistPicker";
import { QueueSheet } from "./QueueSheet";
import { Scrubber } from "./Scrubber";
import { SleepTimerSheet } from "./SleepTimerSheet";

type Sheet = "queue" | "playlist" | "sleep" | null;

/**
 * The full player, laid out like the Android one.
 *
 * Reading down: what is playing, the artwork, the title with its artist and a
 * like, the length and genres, the progress bar with its two times, the five
 * transport buttons, then the four extras (add, queue, share, sleep).
 */
export function NowPlaying() {
  const {
    current,
    queue,
    index,
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
    playQueue,
    setSleepTimer,
    sleepTimer,
    sleepRemaining,
  } = usePlayer();
  const { likedIds, toggleLike } = useLibrary();
  const toasts = useToasts();
  const panel = useRef<HTMLDivElement>(null);
  const [sheet, setSheet] = useState<Sheet>(null);

  // Escape closes the sheet on top first, then the player itself.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      if (sheet) setSheet(null);
      else setExpanded(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [sheet, setExpanded]);

  // Closing the panel must not leave a sheet floating on the page behind it.
  useEffect(() => {
    if (!expanded) setSheet(null);
  }, [expanded]);

  useLayoutEffect(() => {
    const element = panel.current;
    if (!element || motionSafe()) return;
    const ctx = gsap.context(() => {
      const timeline = gsap.timeline();
      timeline
        .from("[data-np-art]", { scale: 0.94, y: 24, opacity: 0, duration: 0.55, ease: "power3.out" }, 0)
        .from("[data-np-line]", { y: 14, opacity: 0, duration: 0.4, stagger: 0.05, ease: "power3.out" }, 0.1)
        .from("[data-np-controls]", { y: 16, opacity: 0, duration: 0.4, stagger: 0.05, ease: "power3.out" }, 0.18);
    }, element);
    return () => ctx.revert();
  }, []);

  if (!current || !expanded) return null;

  const liked = likedIds.has(current.id);
  const close = () => {
    setSheet(null);
    setExpanded(false);
  };
  const total = duration || (current.duration_ms ?? 0) / 1000;
  const details = [total > 0 ? formatDuration(total) : "", current.genres.join(" · ")].filter(Boolean).join(" · ");
  const sleepLabel = !sleepTimer
    ? "Sleep"
    : sleepTimer.atSongEnd
      ? "Song end"
      : `${Math.max(1, Math.ceil(sleepRemaining / 60))}m`;

  const actionClass = "flex flex-col items-center gap-1 rounded-2xl px-2 py-1 transition hover:bg-ink/6";
  const actionIconClass = "grid h-11 w-11 place-items-center rounded-full text-ink-soft";

  return (
    <div ref={panel} className="fixed inset-0 z-[80] flex flex-col bg-paper text-ink" role="dialog" aria-modal="true" aria-label={`Now playing ${current.title}`}>
      <div className="mx-auto flex w-full max-w-2xl items-center gap-2 px-5 pt-[max(env(safe-area-inset-top),0.75rem)] pb-1">
        <button
          type="button"
          onClick={close}
          className="grid h-11 w-11 shrink-0 place-items-center rounded-full text-ink transition hover:bg-ink/8 press"
          aria-label="Close the player"
        >
          <ChevronDown className="h-7 w-7" />
        </button>
        <div className="min-w-0 flex-1 text-center">
          <p className="truncate text-[11px] font-bold uppercase tracking-[0.22em] text-ink-faint">
            {queue.length > 1 ? `Track ${index + 1} of ${queue.length}` : "Now playing"}
          </p>
        </div>
        <span className="h-11 w-11 shrink-0" aria-hidden="true" />
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
        <div className="mx-auto w-full max-w-2xl px-5 pb-10">
          <div data-np-art className="mt-3">
            <Cover
              src={current.cover_url}
              alt={current.title}
              seed={current.id}
              className="aspect-square w-full border border-hair bg-card shadow-[0_18px_40px_-24px_rgba(34,26,19,0.55)]"
              rounded="rounded-[22px]"
            />
          </div>

          <div data-np-line className="mt-5 flex items-start gap-3">
            <div className="min-w-0 flex-1">
              <h1 className="text-[26px] font-extrabold leading-[1.15] tracking-[-0.03em] sm:text-[30px]">
                {current.title}
              </h1>
              <p className="mt-1 truncate text-[15.5px] text-ink-soft">
                <Link to={`/artist/${current.artist_id}`} onClick={close} className="transition hover:text-ink hover:underline">
                  {current.artist_name}
                </Link>
                {current.featuring?.length ? (
                  <span className="text-ink-faint"> feat. {current.featuring.map((guest) => guest.name).join(", ")}</span>
                ) : null}
              </p>
            </div>
            <button
              type="button"
              onClick={() =>
                void toggleLike(current)
                  .then((on) => {
                    if (on) toasts.saved("Added to your likes", current.title);
                  })
                  .catch(() => toasts.error("Could not save that", "Check your connection and try again."))
              }
              className="mt-0.5 grid h-11 w-11 shrink-0 place-items-center rounded-full transition hover:bg-ink/8 press"
              aria-label={liked ? "Remove from likes" : "Add to likes"}
              aria-pressed={liked}
            >
              <Heart className={`h-[22px] w-[22px] ${liked ? "text-flame" : "text-ink-soft"}`} fill={liked ? "currentColor" : "none"} />
            </button>
          </div>

          {details || current.shared_by ? (
            <p data-np-line className="mt-1 text-[12px] font-semibold uppercase tracking-[0.12em] text-ink-faint">
              {details}
              {details && current.shared_by ? " · " : null}
              {current.shared_by ? `Shared by ${current.shared_by.display_name}` : null}
            </p>
          ) : null}

          <div data-np-line className="mt-4">
            <Scrubber position={position} duration={total} onSeek={seek} showTimes={false} />
            <div className="flex items-center justify-between text-[11.5px] font-semibold tabular-nums text-ink-faint">
              <span>{formatDuration(position)}</span>
              <span className="flex items-center gap-2">
                {isLoading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
                {formatDuration(total)}
              </span>
            </div>
          </div>

          <div data-np-controls className="mt-4 flex items-center justify-center">
            <button
              type="button"
              onClick={toggleShuffle}
              className={`grid h-[46px] w-[46px] place-items-center rounded-full transition hover:bg-ink/8 press ${
                shuffle ? "text-flame" : "text-ink-faint"
              }`}
              aria-label={shuffle ? "Turn shuffle off" : "Turn shuffle on"}
              aria-pressed={shuffle}
            >
              <Shuffle className="h-5 w-5" />
            </button>
            <button
              type="button"
              onClick={previous}
              className="ml-2 grid h-[52px] w-[52px] place-items-center rounded-full text-ink transition hover:bg-ink/8 press"
              aria-label="Previous track"
            >
              <SkipBack className="h-6 w-6" fill="currentColor" />
            </button>
            <button
              type="button"
              onClick={toggle}
              className="mx-3 grid h-[72px] w-[72px] shrink-0 place-items-center rounded-full bg-flame text-night shadow-ember transition press hover:scale-105"
              aria-label={isPlaying ? "Pause" : "Play"}
            >
              {isPlaying ? <Pause className="h-8 w-8" fill="currentColor" /> : <Play className="h-8 w-8 translate-x-[2px]" fill="currentColor" />}
            </button>
            <button
              type="button"
              onClick={next}
              className="mr-2 grid h-[52px] w-[52px] place-items-center rounded-full text-ink transition hover:bg-ink/8 press"
              aria-label="Next track"
            >
              <SkipForward className="h-6 w-6" fill="currentColor" />
            </button>
            <button
              type="button"
              onClick={cycleRepeat}
              className={`relative grid h-[46px] w-[46px] place-items-center rounded-full transition hover:bg-ink/8 press ${
                repeatMode !== "off" ? "text-flame" : "text-ink-faint"
              }`}
              aria-label={`Repeat ${repeatMode}; change mode`}
              aria-pressed={repeatMode !== "off"}
            >
              <Repeat className="h-5 w-5" />
              {repeatMode === "one" ? <span className="absolute right-1.5 top-1 text-[9px] font-black">1</span> : null}
            </button>
          </div>

          <div className="mt-5 border-t border-hair pt-3">
            <div className="grid grid-cols-4" data-np-controls>
              <button type="button" onClick={() => setSheet("playlist")} className={actionClass} aria-label="Add to a playlist">
                <span className={actionIconClass}>
                  <PlaylistAdd className="h-5 w-5" />
                </span>
                <span className="text-[11px] font-semibold text-ink-faint">Add</span>
              </button>
              <button type="button" onClick={() => setSheet("queue")} className={actionClass} aria-label="Play queue">
                <span className={actionIconClass}>
                  <Queue className="h-5 w-5" />
                </span>
                <span className="text-[11px] font-semibold text-ink-faint">Queue</span>
              </button>
              <div className="flex flex-col items-center gap-1 px-2 py-1">
                <ShareButton
                  title={`${current.title} by ${current.artist_name}`}
                  text={`Listen to ${current.title} by ${current.artist_name} on ORU`}
                  className={actionIconClass}
                />
                <span className="text-[11px] font-semibold text-ink-faint">Share</span>
              </div>
              <button type="button" onClick={() => setSheet("sleep")} className={actionClass} aria-label="Sleep timer">
                <span className={`${actionIconClass} ${sleepTimer ? "text-flame" : ""}`}>
                  <Timer className="h-5 w-5" />
                </span>
                <span className={`text-[11px] font-semibold ${sleepTimer ? "text-flame-ink" : "text-ink-faint"}`}>{sleepLabel}</span>
              </button>
            </div>
          </div>

          <div data-np-controls className="mt-4 hidden items-center gap-3 text-ink-faint sm:flex">
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
                background: `linear-gradient(to right, var(--color-flame) 0%, var(--color-flame) ${volume * 100}%, var(--color-hair) ${volume * 100}%, var(--color-hair) 100%)`,
              }}
            />
          </div>

          {current.description ? (
            <div data-np-line className="mt-6 rounded-2xl border border-hair bg-card p-5">
              <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-ink-faint">About this track</p>
              <p className="mt-2 text-[15px] leading-relaxed text-ink-soft">{current.description}</p>
            </div>
          ) : null}
        </div>
      </div>

      {sheet === "queue" ? (
        <QueueSheet
          queue={queue}
          index={index}
          onClose={() => setSheet(null)}
          onPick={(at) => {
            playQueue(queue, at);
            setSheet(null);
          }}
        />
      ) : null}
      {sheet === "playlist" ? <PlaylistPicker song={current} onClose={() => setSheet(null)} /> : null}
      {sheet === "sleep" ? (
        <SleepTimerSheet
          sleepTimer={sleepTimer}
          sleepRemaining={sleepRemaining}
          setSleepTimer={setSleepTimer}
          onClose={() => setSheet(null)}
        />
      ) : null}
    </div>
  );
}
