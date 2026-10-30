import { Link, useParams } from "react-router-dom";
import { Check, Pause, Play, UserPlus } from "lucide-react";
import { request } from "../lib/api";
import { useResource } from "../hooks/useResource";
import { formatTimeAgo } from "../lib/format";
import type { Artist, ArtistPost, Paginated, Release, Song } from "../lib/types";
import { TrackRow } from "../components/TrackRow";
import { Cover } from "../components/Cover";
import { ReportButton } from "../components/ReportButton";
import { Button, EmptyState, Spinner } from "../components/ui";
import { useAuth } from "../state/AuthContext";
import { useLibrary } from "../state/LibraryContext";
import { usePlayer } from "../state/PlayerContext";
import { useToasts } from "../state/ToastContext";

function Block({ label }: { label: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-ink-soft">
      <Spinner />
      <span className="text-[15px] font-semibold">{label}</span>
    </div>
  );
}

export function ArtistPage() {
  const params = useParams();
  const artistId = Number(params.artistId);
  const { user } = useAuth();
  const { followedIds, toggleFollow } = useLibrary();
  const toasts = useToasts();
  const { playQueue, current, isPlaying, toggle } = usePlayer();

  const artist = useResource<Artist>((signal) => request(`/artists/${artistId}`, { signal }), [artistId]);
  const songs = useResource<Paginated<Song>>(
    (signal) => request(`/artists/${artistId}/songs?limit=50`, { signal }),
    [artistId],
  );
  const releases = useResource<Paginated<Release>>(
    (signal) => request(`/artists/${artistId}/releases?limit=20`, { signal }),
    [artistId],
  );
  const posts = useResource<Paginated<ArtistPost>>(
    (signal) => request(`/artists/${artistId}/posts?limit=10`, { signal }),
    [artistId],
  );

  const catalogue = songs.data?.items ?? [];
  const following = followedIds.has(artistId);
  const isSelf = user?.id === artistId;
  const playingThisArtist = catalogue.some((song) => current?.id === song.id);

  const toggleArtist = () => {
    toggleFollow(artistId)
      .then((next) => {
        if (next) toasts.saved("Following", artist.data?.stage_name ?? "Artist");
        else toasts.info("Unfollowed", artist.data?.stage_name ?? "Artist");
      })
      .catch((error: Error) => toasts.error("Could not do that", error.message));
  };

  if (artist.error) {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
        <EmptyState
          title="Artist not found"
          message="This profile may be gone or hidden."
          action={
            <Link to="/search" className="rounded-full bg-white px-5 py-2.5 text-[14.5px] font-bold text-black press">
              Search again
            </Link>
          }
        />
      </div>
    );
  }

  if (!artist.data) {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 sm:px-6 lg:px-8">
        <Block label="Loading artist" />
      </div>
    );
  }

  const profile = artist.data;

  return (
    <div className="pb-8">
      <header className="relative bg-gradient-to-b from-[#2b5c46]/85 to-[#121212] px-4 pb-6 pt-8 sm:px-6 lg:px-8">
        <div className="flex flex-col items-start gap-5 sm:flex-row sm:items-end">
          <Cover
            src={null}
            alt={profile.stage_name}
            seed={profile.user_id}
            className="h-32 w-32 shrink-0 sm:h-48 sm:w-48"
            rounded="rounded-full"
          />
          <div className="min-w-0 flex-1">
            <p className="text-[13px] font-bold text-white/70">Artist</p>
            <h1 className="mt-1 truncate text-[36px] font-extrabold leading-none tracking-[-0.035em] sm:text-[56px] lg:text-[72px]">
              {profile.stage_name}
            </h1>
            <p className="mt-3 text-[14.5px] text-white/80">
              <strong className="font-bold text-white">{profile.published_song_count ?? 0}</strong> songs
              <span className="mx-2 opacity-50">·</span>
              <strong className="font-bold text-white">{profile.follower_count ?? 0}</strong> followers
            </p>
          </div>
        </div>
      </header>

      <div className="px-4 sm:px-6 lg:px-8">
        <div className="flex items-center gap-4 py-5">
          <button
            type="button"
            onClick={() => {
              if (!catalogue.length) return;
              if (playingThisArtist) toggle();
              else playQueue(catalogue, 0);
            }}
            disabled={!catalogue.length}
            className={`grid h-14 w-14 place-items-center rounded-full transition press ${
              catalogue.length ? "bg-[#1ed760] text-black hover:scale-105" : "bg-white/10 text-white/40"
            }`}
            aria-label={playingThisArtist && isPlaying ? "Pause" : "Play all songs"}
          >
            {playingThisArtist && isPlaying ? (
              <Pause className="h-6 w-6" fill="currentColor" />
            ) : (
              <Play className="h-6 w-6 translate-x-[2px]" fill="currentColor" />
            )}
          </button>

          {!isSelf ? (
            <Button variant="outline" onClick={toggleArtist} className="gap-2" size="md">
              {following ? <Check className="h-4 w-4" /> : <UserPlus className="h-4 w-4" />}
              {following ? "Following" : "Follow"}
            </Button>
          ) : (
            <Link
              to="/studio"
              className="inline-flex h-11 items-center rounded-full border border-white/30 px-5 text-[15px] font-bold transition press hover:border-white"
            >
              Open studio
            </Link>
          )}

          {!isSelf ? (
            <ReportButton targetType="artist" targetId={profile.user_id} targetLabel={profile.stage_name} />
          ) : null}
        </div>

        {profile.bio ? (
          <p className="mb-6 max-w-2xl text-[15px] leading-relaxed text-ink-soft">{profile.bio}</p>
        ) : null}

        <section className="mb-8">
          <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">Popular songs</h2>
          {songs.loading && !songs.data ? (
            <Block label="Loading songs" />
          ) : songs.error ? (
            <EmptyState title="Could not load songs" message={songs.error} />
          ) : catalogue.length === 0 ? (
            <EmptyState
              title="No songs yet"
              message={
                isSelf
                  ? "Upload a song in the studio, then publish it so people can hear it."
                  : `${profile.stage_name} has not published anything yet.`
              }
              action={
                isSelf ? (
                  <Link
                    to="/studio"
                    className="rounded-full bg-[#1ed760] px-5 py-2.5 text-[14.5px] font-bold text-black press"
                  >
                    Open studio
                  </Link>
                ) : undefined
              }
            />
          ) : (
            <div className="rounded-lg bg-[#181818] p-2">
              {catalogue.slice(0, 8).map((song, index) => (
                <TrackRow key={song.id} song={song} queue={catalogue} position={index + 1} />
              ))}
            </div>
          )}
        </section>

        {(releases.data?.items.length ?? 0) > 0 ? (
          <section className="mb-8">
            <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">Releases</h2>
            <div className="rail -mx-4 flex gap-4 overflow-x-auto px-4 pb-2 no-scrollbar sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8">
              {releases.data?.items.map((release) => {
                const art = catalogue.find((song) => release.song_ids.includes(song.id));
                return (
                  <Link
                    key={release.id}
                    to={`/release/${release.id}`}
                    className="w-[168px] shrink-0 rounded-lg bg-[#181818] p-3 transition hover:bg-[#282828]"
                  >
                    <Cover
                      src={art?.cover_url ?? null}
                      alt={release.title}
                      seed={`r-${release.id}`}
                      className="aspect-square w-full"
                      rounded="rounded-md"
                    />
                    <p className="mt-3 truncate text-[15px] font-bold">{release.title}</p>
                    <p className="mt-0.5 truncate text-[13px] uppercase text-ink-soft">
                      {release.kind} · {formatTimeAgo(release.published_at)}
                    </p>
                  </Link>
                );
              })}
            </div>
          </section>
        ) : null}

        {(posts.data?.items.length ?? 0) > 0 ? (
          <section>
            <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">About the artist</h2>
            <div className="space-y-3">
              {posts.data?.items.map((post) => (
                <article key={post.id} className="rounded-lg bg-[#181818] p-5">
                  <p className="whitespace-pre-wrap text-[15.5px] leading-relaxed">{post.body}</p>
                  <p className="mt-3 text-[13px] text-ink-faint">{formatTimeAgo(post.created_at)}</p>
                </article>
              ))}
            </div>
          </section>
        ) : null}
      </div>
    </div>
  );
}
