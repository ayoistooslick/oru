import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { Globe2, Lock, Play, Plus, Search, X } from "lucide-react";
import { request } from "../lib/api";
import { useResource } from "../hooks/useResource";
import type { Paginated, Playlist, Song } from "../lib/types";
import { TrackRow } from "../components/TrackRow";
import { Cover } from "../components/Cover";
import { Button, EmptyState, Field, Spinner } from "../components/ui";
import { useAuth } from "../state/AuthContext";
import { usePlayer } from "../state/PlayerContext";
import { useToasts } from "../state/ToastContext";

type PlaylistDetail = Playlist & { songs: (Song & { position: number })[] };

export function PlaylistPage() {
  const params = useParams();
  const playlistId = Number(params.playlistId);
  const { user } = useAuth();
  const toasts = useToasts();
  const { playQueue } = usePlayer();

  const detail = useResource<{ playlist: PlaylistDetail }>(
    (signal) => request(`/playlists/${playlistId}`, { signal }),
    [playlistId],
  );

  const playlist = detail.data?.playlist ?? null;
  const songs = useMemo(() => playlist?.songs ?? [], [playlist]);
  const isOwner = Boolean(playlist && user && playlist.owner_id === user.id);

  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState("");
  const [isPublic, setIsPublic] = useState(true);
  const [saving, setSaving] = useState(false);

  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [adding, setAdding] = useState<number | null>(null);

  useEffect(() => {
    if (!playlist) return;
    setTitle(playlist.title);
    setIsPublic(playlist.is_public);
  }, [playlist]);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(query.trim()), 280);
    return () => window.clearTimeout(timer);
  }, [query]);

  const suggestions = useResource<Paginated<Song>>(
    (signal) => request(`/songs?limit=8&q=${encodeURIComponent(debounced)}`, { signal }),
    [debounced],
  );

  const inPlaylist = new Set(songs.map((song) => song.id));
  const candidates = (suggestions.data?.items ?? []).filter((song) => !inPlaylist.has(song.id));

  if (detail.loading && !detail.data) {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
        <div className="flex items-center justify-center gap-3 py-10 text-ink-soft">
          <Spinner />
          <span className="text-[15px] font-semibold">Opening playlist</span>
        </div>
      </div>
    );
  }

  if (detail.error || !playlist) {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
        <EmptyState
          title="Playlist not found"
          message={detail.error ?? "It may be private or deleted."}
          action={
            <Link to="/library" className="rounded-full bg-white px-5 py-2.5 text-[14.5px] font-bold text-black press">
              Back to library
            </Link>
          }
        />
      </div>
    );
  }

  const saveEdits = async (event: FormEvent) => {
    event.preventDefault();
    const name = title.trim();
    if (!name) return;
    setSaving(true);
    try {
      await request(`/playlists/${playlist.id}`, { method: "PATCH", body: { title: name, is_public: isPublic } });
      toasts.saved("Playlist saved", isPublic ? "Anyone can open it" : "Only you can see it");
      setEditing(false);
      detail.reload();
    } catch (error) {
      toasts.error("Could not save it", error instanceof Error ? error.message : "Try again.");
    } finally {
      setSaving(false);
    }
  };

  const addSong = async (song: Song) => {
    setAdding(song.id);
    try {
      await request(`/playlists/${playlist.id}/songs/${song.id}`, { method: "POST" });
      toasts.saved("Added to playlist", song.title);
      detail.reload();
    } catch (error) {
      toasts.error("Could not add it", error instanceof Error ? error.message : "Try again.");
    } finally {
      setAdding(null);
    }
  };

  const removeSong = async (song: Song) => {
    try {
      await request(`/playlists/${playlist.id}/songs/${song.id}`, { method: "DELETE" });
      toasts.info("Removed", song.title);
      detail.reload();
    } catch (error) {
      toasts.error("Could not remove it", error instanceof Error ? error.message : "Try again.");
    }
  };

  return (
    <div className="pb-8">
      <header className="bg-gradient-to-b from-[#4a3d8f]/80 to-[#121212] px-4 pb-6 pt-8 sm:px-6 lg:px-8">
        <div className="flex flex-col items-start gap-5 sm:flex-row sm:items-end">
          <Cover
            src={null}
            alt={playlist.title}
            seed={`pl-${playlist.id}`}
            className="h-40 w-40 shrink-0 sm:h-52 sm:w-52"
            rounded="rounded-md"
          />
          <div className="min-w-0">
            <p className="text-[13px] font-bold text-white/70">
              {playlist.is_public ? "Public playlist" : "Private playlist"}
            </p>
            <h1 className="mt-1 break-words text-[34px] font-extrabold leading-none tracking-[-0.035em] sm:text-[52px]">
              {playlist.title}
            </h1>
            <p className="mt-3 text-[14.5px] text-white/80">
              {songs.length} {songs.length === 1 ? "song" : "songs"}
            </p>
          </div>
        </div>
      </header>

      <div className="px-4 sm:px-6 lg:px-8">
        <div className="flex items-center gap-4 py-5">
          <button
            type="button"
            onClick={() => songs.length && playQueue(songs, 0)}
            disabled={!songs.length}
            className={`grid h-14 w-14 place-items-center rounded-full transition press ${
              songs.length ? "bg-[#1ed760] text-black hover:scale-105" : "bg-white/10 text-white/40"
            }`}
            aria-label="Play all songs"
          >
            <Play className="h-6 w-6 translate-x-[2px]" fill="currentColor" />
          </button>
          {isOwner ? (
            <Button variant="outline" onClick={() => setEditing((value) => !value)} className="gap-2">
              {editing ? <X className="h-4 w-4" /> : <Lock className="h-4 w-4" />}
              {editing ? "Close" : "Edit"}
            </Button>
          ) : null}
        </div>

        {editing && isOwner ? (
          <form onSubmit={saveEdits} className="mb-6 rounded-lg bg-[#181818] p-5">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
              <div className="flex-1">
                <Field label="Name" value={title} onChange={(event) => setTitle(event.target.value)} maxLength={100} />
              </div>
              <label className="flex h-12 shrink-0 cursor-pointer items-center gap-2.5 rounded-lg bg-white/10 px-4 text-[14px] font-semibold">
                <input
                  type="checkbox"
                  checked={isPublic}
                  onChange={(event) => setIsPublic(event.target.checked)}
                  className="h-4 w-4 accent-[#1ed760]"
                />
                {isPublic ? (
                  <>
                    <Globe2 className="h-4 w-4 text-[#1ed760]" /> Anyone can see it
                  </>
                ) : (
                  <>
                    <Lock className="h-4 w-4" /> Only me
                  </>
                )}
              </label>
              <Button type="submit" disabled={saving || !title.trim()} className="sm:w-32">
                {saving ? "Saving" : "Save"}
              </Button>
            </div>
          </form>
        ) : null}

        {songs.length ? (
          <div className="rounded-lg bg-[#181818] p-2">
            {songs.map((song, index) => (
              <TrackRow
                key={song.id}
                song={song}
                queue={songs}
                position={index + 1}
                onRemove={isOwner ? () => void removeSong(song) : undefined}
              />
            ))}
          </div>
        ) : (
          <EmptyState
            title="No songs yet"
            message="Search below and add songs to this playlist."
          />
        )}

        {isOwner ? (
          <section className="mt-8">
            <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">Add songs</h2>
            <div className="relative mb-4 max-w-lg">
              <Search className="absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-ink-faint" />
              <input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search for songs"
                className="h-12 w-full rounded-full border border-[#3e3e3e] bg-[#242424] pl-11 pr-4 text-[15px] text-white outline-none transition placeholder:text-ink-faint focus:border-white"
                aria-label="Search songs to add"
              />
            </div>

            {suggestions.loading && !suggestions.data ? (
              <div className="flex items-center gap-3 py-6 text-ink-soft">
                <Spinner />
                <span className="text-[14.5px] font-semibold">Searching</span>
              </div>
            ) : candidates.length === 0 ? (
              <p className="rounded-lg bg-[#181818] px-5 py-6 text-[14.5px] text-ink-soft">
                {debounced ? `Nothing found for "${debounced}".` : "Start typing to search songs."}
              </p>
            ) : (
              <div className="rounded-lg bg-[#181818] p-2">
                {candidates.map((song) => (
                  <TrackRow
                    key={song.id}
                    song={song}
                    queue={candidates}
                    actions={
                      <button
                        type="button"
                        onClick={() => void addSong(song)}
                        disabled={adding === song.id}
                        className="grid h-9 w-9 place-items-center rounded-full bg-white text-black transition hover:scale-105 disabled:opacity-50"
                        aria-label={`Add ${song.title} to ${playlist.title}`}
                      >
                        <Plus className="h-4 w-4" />
                      </button>
                    }
                  />
                ))}
              </div>
            )}
          </section>
        ) : (
          <p className="mt-6 text-[13.5px] text-ink-faint">Only the owner can change this playlist.</p>
        )}
      </div>
    </div>
  );
}
