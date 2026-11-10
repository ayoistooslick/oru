import { Heart, Pause, Play, Repeat, Shuffle, SkipBack, SkipForward, Volume2 } from "../ui/icons";
import { useLibrary } from "../../state/LibraryContext";
import { usePlayer } from "../../state/PlayerContext";
import { Cover } from "../media/Cover";
import { Scrubber } from "./Scrubber";

/** The bar that stays above the tabs: compact on phones, three columns on desktop. */
export function PlayerBar() {
  const {
    current,
    isPlaying,
    toggle,
    next,
    previous,
    position,
    duration,
    seek,
    setExpanded,
    volume,
    setVolume,
    shuffle,
    repeatMode,
    toggleShuffle,
    cycleRepeat,
  } = usePlayer();
  const { likedIds, toggleLike } = useLibrary();

  if (!current) return null;
  const liked = likedIds.has(current.id);

  const heart = (
    <button
      type="button"
      onClick={() => void toggleLike(current).catch(() => undefined)}
      className="grid h-9 w-9 place-items-center rounded-full text-ink-soft transition hover:text-ink"
      aria-label={liked ? "Remove from likes" : "Add to likes"}
      aria-pressed={liked}
    >
      <Heart className={`h-[18px] w-[18px] ${liked ? "text-flame-ink" : ""}`} fill={liked ? "currentColor" : "none"} />
    </button>
  );

  return (
    <div className="shrink-0 bg-paper-deep" data-tour="player">
      {/* Phone */}
      <div className="flex items-center gap-3 px-3 py-2 lg:hidden">
        <div className="min-w-0 flex-1">
          <button
            type="button"
            onClick={() => setExpanded(true)}
            className="flex w-full min-w-0 items-center gap-3 text-left"
            aria-label={`Open ${current.title}`}
          >
            <Cover src={current.cover_url} alt={current.title} seed={current.id} className="h-11 w-11 shrink-0" />
            <span className="min-w-0">
              <span className="block truncate text-[14px] font-bold">{current.title}</span>
              <span className="block truncate text-[12.5px] text-ink-soft">{current.artist_name}</span>
            </span>
          </button>
          <Scrubber position={position} duration={duration} onSeek={seek} showTimes={false} className="mt-0.5 w-full" />
        </div>
        {heart}
        <button
          type="button"
          onClick={toggle}
          className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-flame text-night shadow-ember press"
          aria-label={isPlaying ? "Pause" : "Play"}
        >
          {isPlaying ? <Pause className="h-5 w-5" fill="currentColor" /> : <Play className="h-5 w-5 translate-x-[1px]" fill="currentColor" />}
        </button>
      </div>

      {/* Desktop */}
      <div className="hidden items-center gap-4 px-4 py-2.5 lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(360px,2fr)_minmax(0,1fr)]">
        <div className="flex min-w-0 items-center gap-3">
          <Cover src={current.cover_url} alt={current.title} seed={current.id} className="h-14 w-14 shrink-0" />
          <div className="min-w-0">
            <button
              type="button"
              onClick={() => setExpanded(true)}
              className="block max-w-full truncate text-left text-[14px] font-semibold text-ink hover:underline"
            >
              {current.title}
            </button>
            <p className="truncate text-[12px] text-ink-soft">{current.artist_name}</p>
          </div>
          <div className="ml-2">{heart}</div>
        </div>

        <div className="flex flex-col items-center gap-1">
          <div className="flex items-center gap-4">
            <button
              type="button"
              onClick={toggleShuffle}
              className={`grid h-8 w-8 place-items-center rounded-full transition hover:text-ink ${shuffle ? "text-flame-ink" : "text-ink-soft"}`}
              aria-label={shuffle ? "Turn shuffle off" : "Turn shuffle on"}
              aria-pressed={shuffle}
              title={shuffle ? "Shuffle on" : "Shuffle off"}
            >
              <Shuffle className="h-4 w-4" />
            </button>
            <button type="button" onClick={previous} className="text-ink-soft transition hover:text-ink" aria-label="Previous song">
              <SkipBack className="h-5 w-5" fill="currentColor" />
            </button>
            <button
              type="button"
              onClick={toggle}
              className="grid h-8 w-8 place-items-center rounded-full bg-flame text-night transition hover:scale-105 press"
              aria-label={isPlaying ? "Pause" : "Play"}
            >
              {isPlaying ? <Pause className="h-4 w-4" fill="currentColor" /> : <Play className="h-4 w-4 translate-x-[1px]" fill="currentColor" />}
            </button>
            <button type="button" onClick={next} className="text-ink-soft transition hover:text-ink" aria-label="Next song">
              <SkipForward className="h-5 w-5" fill="currentColor" />
            </button>
            <button
              type="button"
              onClick={cycleRepeat}
              className={`relative grid h-8 w-8 place-items-center rounded-full transition hover:text-ink ${repeatMode !== "off" ? "text-flame-ink" : "text-ink-soft"}`}
              aria-label={`Repeat ${repeatMode === "off" ? "off" : repeatMode === "all" ? "all songs" : "one song"}; change mode`}
              title={`Repeat ${repeatMode}`}
            >
              <Repeat className="h-4 w-4" />
              {repeatMode === "one" ? <span className="absolute right-0 top-0 text-[9px] font-black">1</span> : null}
            </button>
          </div>
          <Scrubber position={position} duration={duration} onSeek={seek} className="w-full max-w-xl" />
        </div>

        <div className="flex items-center justify-end gap-2">
          <Volume2 className="h-4 w-4 text-ink-soft" />
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={volume}
            onChange={(event) => setVolume(Number(event.target.value))}
            aria-label="Volume"
            className="scrub h-1 w-24"
            style={{
              background: `linear-gradient(to right, var(--color-flame) 0%, var(--color-flame) ${volume * 100}%, var(--color-hair) ${volume * 100}%, var(--color-hair) 100%)`,
            }}
          />
        </div>
      </div>
    </div>
  );
}
