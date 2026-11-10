import type { Song } from "../../lib/types";
import { Cover } from "../media/Cover";
import { PlayerSheet } from "./PlayerSheet";

/**
 * What is queued, so you can jump around in it.
 *
 * Picking a row moves the queue to that song and keeps playing, the same way
 * tapping a row does in the Android player.
 */
export function QueueSheet({
  queue,
  index,
  onPick,
  onClose,
}: {
  queue: Song[];
  index: number;
  onPick: (index: number) => void;
  onClose: () => void;
}) {
  const after = Math.max(0, queue.length - index - 1);
  const subtitle =
    queue.length === 0
      ? "Nothing is queued yet"
      : after === 0
        ? "This is the last song in the queue"
        : `${after} ${after === 1 ? "song" : "songs"} after this one`;

  return (
    <PlayerSheet title="Queue" subtitle={subtitle} onClose={onClose}>
      <ul className="space-y-1">
        {queue.map((song, at) => {
          const playing = at === index;
          return (
            <li key={`${song.id}-${at}`}>
              <button
                type="button"
                onClick={() => onPick(at)}
                className={`flex w-full items-center gap-3 rounded-xl px-2 py-2 text-left transition ${
                  playing ? "bg-flame-soft" : "hover:bg-ink/6"
                }`}
                aria-current={playing ? "true" : undefined}
              >
                <Cover src={song.cover_url} alt={song.title} seed={song.id} className="h-11 w-11 shrink-0" />
                <span className="min-w-0 flex-1">
                  <span className={`block truncate text-[14px] font-semibold ${playing ? "text-flame-ink" : "text-ink"}`}>
                    {song.title}
                  </span>
                  <span className="block truncate text-[12.5px] text-ink-soft">{song.artist_name}</span>
                </span>
                {playing ? <span className="text-[11px] font-bold uppercase tracking-wide text-flame-ink">Playing</span> : null}
              </button>
            </li>
          );
        })}
      </ul>
    </PlayerSheet>
  );
}
