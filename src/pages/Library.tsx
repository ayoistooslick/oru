import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Globe2, Heart, Lock, Play, Plus, Shuffle, X } from "lucide-react";
import { request } from "../lib/api";
import { useResource } from "../hooks/useResource";
import type { Artist, Paginated, Playlist } from "../lib/types";
import { TrackCard } from "../components/TrackCard";
import { TrackRow } from "../components/TrackRow";
import { Cover } from "../components/Cover";
import { Button, Chip, EmptyState, Field, Spinner } from "../components/ui";
import { useToasts } from "../state/ToastContext";
import { usePlayer } from "../state/PlayerContext";
import type { Song } from "../lib/types";

type Filter = "all" | "playlists" | "liked" | "artists";

function Loader({ label }: { label: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-ink-soft">
      <Spinner />
      <span className="text-[15px] font-semibold">{label}</span>
    </div>
  );
}

export function Library() {
  const toasts = useToasts();
  const { playQueue } = usePlayer();
  const [filter, setFilter] = useState<Filter>("all");
  const [composing, setComposing] = useState(false);
  const [title, setTitle] = useState("");
  const [isPublic, setIsPublic] = useState(true);
  const [saving, setSaving] = useState(false);

  const playlists = useResource((signal) => request<Paginated<Playlist>>("/playlists", { signal }), []);
  const likes = useResource((signal) => request<Paginated<Song>>("/listeners/me/likes?limit=50", { signal }), []);
  const follows = useResource((signal) => request<Paginated<Artist>>("/listeners/me/following?limit=50", { signal }), []);

  const playlistItems = playlists.data?.items ?? [];
  const liked = likes.data?.items ?? [];
  const followed = follows.data?.items ?? [];
  const loading = playlists.loading || likes.loading || follows.loading;
  const nothingYet =
    !loading && playlistItems.length === 0 && liked.length === 0 && followed.length === 0;

  const createPlaylist = async (event: FormEvent) => {
    event.preventDefault();
    const name = title.trim();
    if (!name) return;
    setSaving(true);
    try {
      await request("/playlists", { method: "POST", body: { title: name, is_public: isPublic } });
      toasts.saved("Playlist created", name);
      setTitle("");
      setComposing(false);
      playlists.reload();
    } catch (error) {
      toasts.error("Could not create it", error instanceof Error ? error.message : "Try again.");
    } finally {
      setSaving(false);
    }
  };

  const showPlaylists = filter === "all" || filter === "playlists";
  const showLiked = filter === "all" || filter === "liked";
  const showArtists = filter === "all" || filter === "artists";
  const playShuffled = () => {
    const order = [...liked];
    for (let at = order.length - 1; at > 0; at -= 1) {
      const swapAt = Math.floor(Math.random() * (at + 1));
      [order[at], order[swapAt]] = [order[swapAt], order[at]];
    }
    if (order.length) playQueue(order, 0);
  };

  return (
    <div className="mx-auto w-full max-w-7xl px-4 pb-8 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-center justify-between gap-3 py-5">
        <h1 className="text-[26px] font-extrabold tracking-[-0.03em] sm:text-[32px]">Your Library</h1>
        <Button
          onClick={() => setComposing((value) => !value)}
          variant={composing ? "soft" : "primary"}
          size="sm"
          className="gap-2"
        >
          {composing ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
          {composing ? "Close" : "Create playlist"}
        </Button>
      </header>

      <div className="mb-5 flex flex-wrap gap-2">
        <Chip active={filter === "all"} onClick={() => setFilter("all")}>
          All
        </Chip>
        <Chip active={filter === "playlists"} onClick={() => setFilter("playlists")}>
          Playlists
        </Chip>
        <Chip active={filter === "liked"} onClick={() => setFilter("liked")}>
          Liked songs
        </Chip>
        <Chip active={filter === "artists"} onClick={() => setFilter("artists")}>
          Artists
        </Chip>
      </div>

      {composing ? (
        <form onSubmit={createPlaylist} className="mb-6 rounded-lg bg-[#181818] p-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
            <div className="flex-1">
              <Field
                label="Name"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder="My playlist"
                maxLength={100}
                autoFocus
              />
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
            <Button type="submit" disabled={saving || !title.trim()} className="sm:w-36">
              {saving ? "Creating" : "Create"}
            </Button>
          </div>
        </form>
      ) : null}

      {nothingYet ? (
        <EmptyState
          title="Your library is empty"
          message="Tap the heart on any song to save it here. You can also make your own playlist."
          action={
            <Link
              to="/listen"
              className="rounded-full bg-[#1ed760] px-5 py-2.5 text-[14.5px] font-bold text-black press"
            >
              Go to Home
            </Link>
          }
        />
      ) : null}

      {showLiked ? (
        <section className="mb-8">
          <div className="mb-3 flex items-baseline justify-between gap-3">
            <h2 className="text-[22px] font-bold tracking-[-0.02em]">Liked songs</h2>
            {filter !== "liked" ? (
              <button type="button" onClick={() => setFilter("liked")} className="text-[13px] font-bold text-ink-soft hover:text-white">
                Open playlist
              </button>
            ) : null}
          </div>
          {loading && !likes.data ? (
            <Loader label="Loading your likes" />
          ) : liked.length === 0 ? (
            <div className="flex items-center gap-4 rounded-lg bg-[#181818] p-4">
              <span className="grid h-14 w-14 shrink-0 place-items-center rounded-md bg-[#1ed760] text-black">
                <Heart className="h-7 w-7" fill="currentColor" />
              </span>
              <p className="text-[14.5px] leading-relaxed text-ink-soft">
                Songs you like will show up here. Tap the heart on any song.
              </p>
            </div>
          ) : filter === "liked" ? (
            <>
              <div className="mb-5 flex flex-col gap-5 rounded-lg bg-gradient-to-br from-[#5536a7] via-[#362477] to-[#181818] p-5 sm:flex-row sm:items-end sm:p-7">
                <div className="grid aspect-square w-40 shrink-0 grid-cols-2 grid-rows-2 overflow-hidden rounded-md bg-white/10 shadow-xl sm:w-52">
                  {liked.slice(0, 4).map((song) => (
                    <Cover key={song.id} src={song.cover_url} alt={song.title} seed={song.id} className="h-full w-full" rounded="rounded-none" />
                  ))}
                  {liked.length < 2 ? <Heart className="m-auto h-12 w-12 text-white/70" fill="currentColor" /> : null}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-[12px] font-bold uppercase tracking-[0.12em] text-white/80">Playlist</p>
                  <h2 className="mt-1 break-words text-[34px] font-extrabold leading-tight tracking-[-0.04em] sm:text-[52px]">Liked Songs</h2>
                  <p className="mt-2 text-[14px] text-white/75">Your saved music · {liked.length} {liked.length === 1 ? "song" : "songs"}</p>
                  <div className="mt-5 flex flex-wrap gap-3">
                    <button type="button" onClick={() => playQueue(liked, 0)} className="inline-flex h-11 items-center gap-2 rounded-full bg-[#1ed760] px-5 text-[14px] font-bold text-black press">
                      <Play className="h-4 w-4" fill="currentColor" /> Play all
                    </button>
                    <button type="button" onClick={playShuffled} className="inline-flex h-11 items-center gap-2 rounded-full border border-white/35 px-5 text-[14px] font-bold text-white press hover:bg-white/10">
                      <Shuffle className="h-4 w-4" /> Shuffle
                    </button>
                  </div>
                </div>
              </div>
              <div className="rounded-lg bg-[#181818] p-2">
                {liked.map((song, index) => <TrackRow key={song.id} song={song} queue={liked} position={index + 1} />)}
              </div>
            </>
          ) : (
            <div className="flex gap-4 overflow-x-auto pb-1 no-scrollbar">
              {liked.slice(0, 12).map((song) => (
                <TrackCard key={song.id} song={song} queue={liked} />
              ))}
            </div>
          )}
        </section>
      ) : null}

      {showPlaylists ? (
        <section className="mb-8">
          <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">Playlists</h2>
          {loading && !playlists.data ? (
            <Loader label="Loading playlists" />
          ) : playlists.error ? (
            <EmptyState title="Could not load playlists" message={playlists.error} />
          ) : playlistItems.length === 0 ? (
            <EmptyState
              title="No playlists yet"
              message="Make a playlist to keep songs together in one place."
              action={
                <button
                  type="button"
                  onClick={() => setComposing(true)}
                  className="rounded-full bg-[#1ed760] px-5 py-2.5 text-[14.5px] font-bold text-black press"
                >
                  Create playlist
                </button>
              }
            />
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
              {playlistItems.map((playlist) => (
                <Link
                  key={playlist.id}
                  to={`/library/${playlist.id}`}
                  className="rounded-lg bg-[#181818] p-3 transition hover:bg-[#282828]"
                >
                  <Cover
                    src={null}
                    alt={playlist.title}
                    seed={`pl-${playlist.id}`}
                    className="aspect-square w-full"
                    rounded="rounded-md"
                  />
                  <p className="mt-3 truncate text-[15px] font-bold">{playlist.title}</p>
                  <p className="mt-1 flex items-center gap-1.5 text-[13px] text-ink-soft">
                    {playlist.is_public ? <Globe2 className="h-3.5 w-3.5" /> : <Lock className="h-3.5 w-3.5" />}
                    {playlist.song_count ?? 0} songs
                  </p>
                </Link>
              ))}
            </div>
          )}
        </section>
      ) : null}

      {showArtists ? (
        <section className="mb-8">
          <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">Artists you follow</h2>
          {loading && !follows.data ? (
            <Loader label="Loading artists" />
          ) : followed.length === 0 ? (
            <EmptyState
              title="Not following anyone yet"
              message="Follow an artist and their new songs will show up on Home."
              action={
                <Link
                  to="/search"
                  className="rounded-full bg-[#1ed760] px-5 py-2.5 text-[14.5px] font-bold text-black press"
                >
                  Find artists
                </Link>
              }
            />
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
              {followed.map((artist) => (
                <Link
                  key={artist.user_id}
                  to={`/artist/${artist.user_id}`}
                  className="rounded-lg bg-[#181818] p-4 text-center transition hover:bg-[#282828]"
                >
                  <Cover
                    src={null}
                    alt={artist.stage_name}
                    seed={artist.user_id}
                    className="mx-auto aspect-square w-full"
                    rounded="rounded-full"
                  />
                  <p className="mt-3 truncate text-[15px] font-bold">{artist.stage_name}</p>
                  <p className="truncate text-[13px] text-ink-soft">Artist</p>
                </Link>
              ))}
            </div>
          )}
        </section>
      ) : null}

    </div>
  );
}
