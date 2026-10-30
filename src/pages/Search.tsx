import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Search as SearchIcon, X } from "lucide-react";
import { request } from "../lib/api";
import { useResource } from "../hooks/useResource";
import type { Artist, Genre, Paginated, Song } from "../lib/types";
import { TrackRow } from "../components/TrackRow";
import { Chip, EmptyState, Spinner } from "../components/ui";
import { Cover } from "../components/Cover";

type Results = { songs: Paginated<Song>; artists: Paginated<Artist> };

export function SearchPage() {
  // A shared link or the system share sheet can land here with ?q=.
  const [query, setQuery] = useState(() => new URLSearchParams(window.location.search).get("q") ?? "");
  const [debounced, setDebounced] = useState("");

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(query.trim()), 280);
    return () => window.clearTimeout(timer);
  }, [query]);

  const genres = useResource((signal) => request<Paginated<Genre>>("/genres", { signal }), []);

  const results = useResource<Results>(
    (signal) => {
      const term = debounced ? `&q=${encodeURIComponent(debounced)}` : "";
      return Promise.all([
        request<Paginated<Song>>(`/songs?limit=24${term}`, { signal }),
        request<Paginated<Artist>>(`/artists?limit=12${term}`, { signal }),
      ]).then(([songs, artists]) => ({ songs, artists }));
    },
    [debounced],
  );

  const songs = results.data?.songs.items ?? [];
  const artists = results.data?.artists.items ?? [];
  const hasQuery = debounced.length > 0;

  return (
    <div className="mx-auto w-full max-w-7xl px-4 pb-8 sm:px-6 lg:px-8">
      <div className="sticky top-0 z-10 -mx-4 bg-[#121212]/95 px-4 py-4 backdrop-blur sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8">
        <div className="relative max-w-xl">
          <SearchIcon className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-ink-faint" />
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="What do you want to listen to?"
            autoFocus
            className="h-12 w-full rounded-full border border-[#3e3e3e] bg-[#242424] pl-12 pr-11 text-[15px] text-white outline-none transition placeholder:text-ink-faint focus:border-white"
            aria-label="Search for songs or artists"
          />
          {query ? (
            <button
              type="button"
              onClick={() => setQuery("")}
              className="absolute right-3 top-1/2 grid h-7 w-7 -translate-y-1/2 place-items-center rounded-full bg-white/10 text-white hover:bg-white/20"
              aria-label="Clear the search"
            >
              <X className="h-4 w-4" />
            </button>
          ) : null}
        </div>

        {(genres.data?.items.length ?? 0) > 0 && !hasQuery ? (
          <div className="mt-4 flex flex-wrap gap-2">
            {(genres.data?.items ?? []).map((genre) => (
              <Chip
                key={genre.name}
                onClick={() => setQuery(genre.name)}
              >
                <span className="capitalize">{genre.name}</span>
              </Chip>
            ))}
          </div>
        ) : null}
      </div>

      {results.loading && !results.data ? (
        <div className="flex items-center justify-center gap-3 py-24 text-ink-soft">
          <Spinner />
          <span className="text-[15px] font-semibold">Searching</span>
        </div>
      ) : null}

      {results.error ? <EmptyState title="Search did not work" message={results.error} /> : null}

      {results.data && artists.length === 0 && songs.length === 0 && hasQuery ? (
        <EmptyState
          title={`No results for "${debounced}"`}
          message="Check the spelling, or clear the box to see everything."
        />
      ) : null}

      {artists.length > 0 ? (
        <section className="mb-8">
          <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">Artists</h2>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
            {artists.map((artist) => (
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
        </section>
      ) : null}

      {songs.length > 0 ? (
        <section>
          <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">Songs</h2>
          <div className="rounded-lg bg-[#181818] p-2">
            {songs.map((song) => (
              <TrackRow key={song.id} song={song} queue={songs} />
            ))}
          </div>
        </section>
      ) : null}
    </div>
  );
}
