import { useState } from "react";
import { Link } from "react-router-dom";
import { getToken, request } from "../../lib/api";
import { useResource } from "../../hooks/useResource";
import type { Paginated, Playlist, Song } from "../../lib/types";
import { useToasts } from "../../state/ToastContext";
import { Loader2 } from "../ui/icons";
import { PlayerSheet } from "./PlayerSheet";

/** Puts the song that is playing into one of your playlists. */
export function PlaylistPicker({ song, onClose }: { song: Song; onClose: () => void }) {
  const toasts = useToasts();
  const signedIn = Boolean(getToken());
  const [savingId, setSavingId] = useState<number | null>(null);
  const playlists = useResource<Paginated<Playlist>>(
    (signal) => (signedIn ? request("/playlists?limit=50", { signal }) : Promise.resolve({ items: [], limit: 50, offset: 0 })),
    [signedIn],
  );

  const add = async (playlist: Playlist) => {
    setSavingId(playlist.id);
    try {
      await request(`/playlists/${playlist.id}/songs/${song.id}`, { method: "POST" });
      toasts.saved("Added to playlist", `${song.title} is in ${playlist.title}`);
      onClose();
    } catch {
      toasts.error("Could not add that song", "It may already be in this playlist.");
    } finally {
      setSavingId(null);
    }
  };

  return (
    <PlayerSheet title="Add to a playlist" subtitle={song.title} onClose={onClose}>
      {!signedIn ? (
        <p className="rounded-xl bg-ink/6 p-4 text-[14px] leading-relaxed text-ink-soft">
          <Link to="/auth" className="font-bold text-flame-ink underline underline-offset-4" onClick={onClose}>
            Sign in
          </Link>{" "}
          to save songs to a playlist.
        </p>
      ) : playlists.loading && !playlists.data ? (
        <div className="flex items-center gap-2 px-1 py-6 text-[14px] text-ink-soft">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading your playlists
        </div>
      ) : playlists.error ? (
        <p className="px-1 py-4 text-[14px] text-ink-soft">{playlists.error}</p>
      ) : (playlists.data?.items.length ?? 0) === 0 ? (
        <p className="rounded-xl bg-ink/6 p-4 text-[14px] leading-relaxed text-ink-soft">
          No playlists yet.{" "}
          <Link to="/library" className="font-bold text-flame-ink underline underline-offset-4" onClick={onClose}>
            Make one
          </Link>{" "}
          first.
        </p>
      ) : (
        <ul className="space-y-1">
          {playlists.data?.items.map((playlist) => (
            <li key={playlist.id}>
              <button
                type="button"
                onClick={() => void add(playlist)}
                disabled={savingId !== null}
                className="flex w-full items-center justify-between gap-3 rounded-xl px-3 py-3 text-left transition hover:bg-ink/6 disabled:opacity-60"
              >
                <span className="min-w-0">
                  <span className="block truncate text-[14.5px] font-semibold">{playlist.title}</span>
                  <span className="block truncate text-[12.5px] text-ink-soft">{playlist.song_count ?? 0} songs</span>
                </span>
                {savingId === playlist.id ? <Loader2 className="h-4 w-4 shrink-0 animate-spin text-ink-faint" /> : null}
              </button>
            </li>
          ))}
        </ul>
      )}
    </PlayerSheet>
  );
}
