import { Pause, Play } from "lucide-react";
import { usePlayer } from "../state/PlayerContext";
import { useToasts } from "../state/ToastContext";
import type { Song } from "../lib/types";
import { Cover } from "./Cover";

/** A tile in a shelf: artwork, name, and a green play button on hover. */
export function TrackCard({ song, queue }: { song: Song; queue?: Song[] }) {
  const { current, isPlaying, playSong, toggle } = usePlayer();
  const toasts = useToasts();
  const active = current?.id === song.id;

  const handlePlay = () => {
    if (!song.audio_url) {
      toasts.info("Not ready yet", "This song is still a draft.");
      return;
    }
    if (active) {
      toggle();
      return;
    }
    playSong(song, queue);
    toasts.playing(song.title, `by ${song.artist_name}`);
  };

  return (
    <figure className="group w-[152px] shrink-0 rounded-lg bg-[#181818] p-3 transition hover:bg-[#282828] sm:w-[176px]">
      <div className="relative">
        <button type="button" onClick={handlePlay} className="block w-full" aria-label={`Play ${song.title}`}>
          <Cover
            src={song.cover_url}
            alt={song.title}
            seed={song.id}
            className="aspect-square w-full"
            rounded="rounded-md"
          />
        </button>
        <button
          type="button"
          onClick={handlePlay}
          aria-label={active && isPlaying ? `Pause ${song.title}` : `Play ${song.title}`}
          className={`absolute right-2 bottom-14 grid h-11 w-11 place-items-center rounded-full bg-[#1ed760] text-black shadow-[0_8px_16px_rgba(0,0,0,0.5)] transition-all duration-200 press ${
            active
              ? "translate-y-0 opacity-100"
              : "translate-y-2 opacity-0 group-hover:translate-y-0 group-hover:opacity-100"
          }`}
        >
          {active && isPlaying ? (
            <Pause className="h-5 w-5" fill="currentColor" />
          ) : (
            <Play className="h-5 w-5 translate-x-[1px]" fill="currentColor" />
          )}
        </button>
      </div>
      <figcaption className="mt-2.5">
        <p className={`truncate text-[15px] font-bold ${active ? "text-[#1ed760]" : ""}`}>{song.title}</p>
        <p className="mt-0.5 truncate text-[13.5px] text-ink-soft">{song.artist_name}</p>
      </figcaption>
    </figure>
  );
}
