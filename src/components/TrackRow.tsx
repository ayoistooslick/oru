import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Heart, Pause, Play, X } from "lucide-react";
import { useLibrary } from "../state/LibraryContext";
import { usePlayer } from "../state/PlayerContext";
import { useToasts } from "../state/ToastContext";
import type { Song } from "../lib/types";
import { Cover } from "./Cover";

interface TrackRowProps {
  song: Song;
  queue?: Song[];
  position?: number;
  showArtist?: boolean;
  showStatus?: boolean;
  actions?: ReactNode;
  onRemove?: () => void;
}

/** One line in a list: cover, name, like. The row itself starts the song. */
export function TrackRow({
  song,
  queue,
  position,
  showArtist = true,
  showStatus = false,
  actions,
  onRemove,
}: TrackRowProps) {
  const { current, isPlaying, playSong, toggle } = usePlayer();
  const { likedIds, toggleLike } = useLibrary();
  const toasts = useToasts();
  const isActive = current?.id === song.id;
  const liked = likedIds.has(song.id);
  const published = song.status === "published";

  const handlePlay = () => {
    if (!song.audio_url) {
      toasts.info("Not ready yet", "This song is still a draft.");
      return;
    }
    if (isActive) {
      toggle();
      return;
    }
    playSong(song, queue);
    toasts.playing(song.title, `by ${song.artist_name}`);
  };

  const handleLike = () => {
    toggleLike(song)
      .then((next) => {
        if (next) toasts.saved("Saved to your library", song.title);
      })
      .catch((error: Error) => toasts.error("Could not save that", error.message));
  };

  return (
    <div
      className={`group grid grid-cols-[auto_1fr_auto] items-center gap-3 rounded-lg px-2 py-1.5 transition ${
        isActive ? "bg-white/10" : "hover:bg-white/5"
      }`}
    >
      <button
        type="button"
        onClick={handlePlay}
        className="relative h-10 w-10 shrink-0"
        aria-label={isActive && isPlaying ? `Pause ${song.title}` : `Play ${song.title}`}
      >
        <span className="absolute inset-0 grid place-items-center">
          <Cover src={song.cover_url} alt={song.title} seed={song.id} className="h-10 w-10" />
        </span>
        <span className="absolute inset-0 grid place-items-center rounded bg-black/60 opacity-0 transition group-hover:opacity-100">
          {isActive && isPlaying ? (
            <Pause className="h-4 w-4 text-white" fill="currentColor" />
          ) : (
            <Play className="h-4 w-4 translate-x-[1px] text-white" fill="currentColor" />
          )}
        </span>
      </button>

      <button type="button" onClick={handlePlay} className="min-w-0 text-left">
        <p className={`truncate text-[15px] font-semibold ${isActive ? "text-[#1ed760]" : "text-white"}`}>
          {position !== undefined ? (
            isActive && isPlaying ? (
              <span className="mr-3 inline-flex h-3 w-5 items-end gap-[2px]" aria-hidden="true">
                <i className="eq-bar" />
                <i className="eq-bar" />
                <i className="eq-bar" />
              </span>
            ) : (
              <span className="mr-3 inline-block w-5 text-right text-[13.5px] font-normal text-ink-soft tabular-nums">
                {position}
              </span>
            )
          ) : null}
          {song.title}
        </p>
        <p className="mt-0.5 flex items-center gap-2 truncate text-[13.5px] text-ink-soft">
          {showArtist ? (
            <Link to={`/artist/${song.artist_id}`} onClick={(event) => event.stopPropagation()} className="truncate hover:text-white hover:underline">
              {song.artist_name}
            </Link>
          ) : song.genres.slice(0, 2).join(", ") || "No genre"}
          {published && song.shared_by ? <span className="truncate">· Shared by {song.shared_by.display_name}</span> : null}
          {showStatus && !published ? (
            <span className="rounded bg-white/15 px-1.5 py-px text-[10.5px] font-bold uppercase">{song.status}</span>
          ) : null}
        </p>
      </button>

      <div className="flex items-center gap-1">
        {actions}
        {onRemove ? (
          <button
            type="button"
            onClick={onRemove}
            className="grid h-9 w-9 place-items-center rounded-full text-ink-soft transition hover:bg-white/10 hover:text-white"
            aria-label={`Remove ${song.title}`}
          >
            <X className="h-4 w-4" />
          </button>
        ) : null}
        {published ? (
          <button
            type="button"
            onClick={handleLike}
            className={`grid h-9 w-9 place-items-center rounded-full transition hover:bg-white/10 ${
              liked ? "opacity-100" : "opacity-0 group-hover:opacity-100"
            }`}
            aria-label={liked ? `Remove ${song.title} from likes` : `Like ${song.title}`}
            aria-pressed={liked}
          >
            <Heart
              className={`h-[17px] w-[17px] ${liked ? "text-[#1ed760]" : "text-ink-soft"}`}
              fill={liked ? "currentColor" : "none"}
            />
          </button>
        ) : null}
      </div>
    </div>
  );
}
