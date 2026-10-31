import { Link } from "react-router-dom";
import { Play, Pause, Upload } from "lucide-react";
import { request } from "../lib/api";
import { useResource } from "../hooks/useResource";
import type { Paginated, Song, WeeklyRanking } from "../lib/types";
import { TrackCard } from "../components/TrackCard";
import { TrackRow } from "../components/TrackRow";
import { EmptyState, Spinner } from "../components/ui";
import { Cover } from "../components/Cover";
import { usePlayer } from "../state/PlayerContext";

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}

function QuickTile({ song, queue }: { song: Song; queue: Song[] }) {
  const { current, isPlaying, playSong, toggle } = usePlayer();
  const active = current?.id === song.id;

  const play = () => {
    if (active) toggle();
    else playSong(song, queue);
  };

  return (
    <button
      type="button"
      onClick={play}
      className="group flex items-center gap-3 rounded bg-white/10 p-2 text-left transition hover:bg-white/20"
    >
      <Cover src={song.cover_url} alt={song.title} seed={song.id} className="h-11 w-11 shrink-0" rounded="rounded-none" />
      <span className="min-w-0 flex-1 truncate text-[14px] font-bold">{song.title}</span>
      <span
        className={`grid h-9 w-9 shrink-0 place-items-center rounded-full bg-[#1ed760] text-black shadow-lg transition ${
          active ? "opacity-100" : "opacity-0 group-hover:opacity-100"
        }`}
      >
        {active && isPlaying ? (
          <Pause className="h-4 w-4" fill="currentColor" />
        ) : (
          <Play className="h-4 w-4 translate-x-[1px]" fill="currentColor" />
        )}
      </span>
    </button>
  );
}

export function Listen() {
  const feed = useResource((signal) => request<Paginated<Song>>("/discover?limit=30", { signal }), []);
  const follows = useResource((signal) => request<Paginated<Song>>("/feed/following?limit=12", { signal }), []);
  const weekly = useResource((signal) => request<WeeklyRanking>("/rankings/weekly?limit=5", { signal }), []);
  const songs = feed.data?.items ?? [];
  const fromFollows = follows.data?.items ?? [];
  const top = weekly.data?.items ?? [];

  return (
    <div className="mx-auto w-full max-w-7xl px-4 pb-8 sm:px-6 lg:px-8">
      <header className="mb-6 rounded-lg bg-gradient-to-b from-[#1e5c3f]/80 via-[#173d2c]/35 to-transparent px-5 py-6 sm:px-7">
        <p className="text-[12.5px] font-bold uppercase tracking-[0.12em] text-white/70">{greeting()}</p>
        <h1 className="mt-1 text-[30px] font-extrabold tracking-[-0.03em] sm:text-[36px]">Home</h1>
      </header>

      {feed.loading && !feed.data ? (
        <div className="flex items-center justify-center gap-3 py-24 text-ink-soft">
          <Spinner />
          <span className="text-[15px] font-semibold">Loading songs</span>
        </div>
      ) : feed.error ? (
        <EmptyState
          title="Could not load songs"
          message="Check your internet connection, then try again."
          action={
            <button
              type="button"
              onClick={feed.reload}
              className="rounded-full bg-white px-5 py-2.5 text-[14.5px] font-bold text-black press"
            >
              Try again
            </button>
          }
        />
      ) : songs.length === 0 ? (
        <EmptyState
          title="No songs yet"
          message="Nothing has been published so far. You can be the first."
          action={
            <Link
              to="/studio"
              className="inline-flex items-center gap-2 rounded-full bg-[#1ed760] px-5 py-2.5 text-[14.5px] font-bold text-black press"
            >
              <Upload className="h-4 w-4" /> Open the studio
            </Link>
          }
        />
      ) : (
        <>
          <section className="mb-8 grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-3">
            {songs.slice(0, 6).map((song) => (
              <QuickTile key={song.id} song={song} queue={songs} />
            ))}
          </section>

          {top.length > 0 ? (
            <section className="mb-8">
              <div className="mb-3 flex items-baseline justify-between gap-4">
                <h2 className="text-[22px] font-bold tracking-[-0.02em]">This week</h2>
                <span className="text-[12px] font-bold uppercase tracking-wide text-ink-faint">Most played</span>
              </div>
              <ol className="overflow-hidden rounded-lg bg-[#181818]">
                {top.map((entry) => (
                  <li key={entry.song.id} className="border-b border-white/5 last:border-0">
                    <div className="flex items-center gap-3 pr-2">
                      <span className="w-7 shrink-0 text-center text-[15px] font-extrabold tabular-nums text-ink-faint">
                        {entry.rank}
                      </span>
                      <div className="min-w-0 flex-1">
                        <TrackRow song={entry.song} queue={top.map((item) => item.song)} showArtist />
                      </div>
                      <span className="shrink-0 text-[12.5px] tabular-nums text-ink-faint">
                        {entry.plays} {entry.plays === 1 ? "play" : "plays"}
                      </span>
                    </div>
                  </li>
                ))}
              </ol>
            </section>
          ) : null}

          <section className="mb-8">
            <div className="mb-3 flex items-baseline justify-between gap-4">
              <h2 className="text-[22px] font-bold tracking-[-0.02em]">New songs</h2>
              <Link to="/search" className="text-[13px] font-bold uppercase tracking-wide text-ink-soft hover:text-white">
                Show all
              </Link>
            </div>
            <div className="rail -mx-4 flex gap-4 overflow-x-auto px-4 pb-2 no-scrollbar sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8">
              {songs.slice(0, 10).map((song) => (
                <TrackCard key={song.id} song={song} queue={songs} />
              ))}
            </div>
          </section>

          {fromFollows.length > 0 ? (
            <section className="mb-8">
              <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">From artists you follow</h2>
              <div className="rounded-lg bg-[#181818] p-2">
                {fromFollows.map((song) => (
                  <TrackRow key={song.id} song={song} queue={fromFollows} />
                ))}
              </div>
            </section>
          ) : null}

          <section>
            <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">All songs</h2>
            <div className="rounded-lg bg-[#181818] p-2">
              {songs.map((song, index) => (
                <TrackRow key={song.id} song={song} queue={songs} position={index + 1} />
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  );
}
