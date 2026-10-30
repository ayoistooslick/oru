import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Play } from "lucide-react";
import { request } from "../lib/api";
import { useResource } from "../hooks/useResource";
import { formatTimeAgo } from "../lib/format";
import type { Artist, ReleaseDetail, Song } from "../lib/types";
import { TrackRow } from "../components/TrackRow";
import { Cover } from "../components/Cover";
import { ReportButton } from "../components/ReportButton";
import { ShareButton } from "../components/ShareButton";
import { EmptyState, Spinner } from "../components/ui";
import { usePlayer } from "../state/PlayerContext";

function Block({ label }: { label: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-20 text-ink-soft">
      <Spinner />
      <span className="text-[15px] font-semibold">{label}</span>
    </div>
  );
}

export function ReleasePage() {
  const params = useParams();
  const releaseId = Number(params.releaseId);
  const { playQueue } = usePlayer();

  const release = useResource<ReleaseDetail>((signal) => request(`/releases/${releaseId}`, { signal }), [releaseId]);
  const artistId = release.data?.release.artist_id;
  const artist = useResource<Artist>((signal) => request(`/artists/${artistId}`, { signal }), [artistId]);

  if (release.error) {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
        <EmptyState
          title="Release not found"
          message={release.error}
          action={
            <Link to="/listen" className="rounded-full bg-white px-5 py-2.5 text-[14.5px] font-bold text-black press">
              Back to Home
            </Link>
          }
        />
      </div>
    );
  }

  if (!release.data) {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 sm:px-6 lg:px-8">
        <Block label="Loading release" />
      </div>
    );
  }

  const { release: data } = release.data;
  const songs = (data.songs ?? []) as Song[];
  const art = songs.find((song) => song.cover_url)?.cover_url ?? null;
  const kindLabel = data.kind === "ep" ? "EP" : data.kind === "album" ? "Album" : "Single";

  return (
    <div className="pb-8">
      <header className="bg-gradient-to-b from-[#3f3f3f]/85 to-[#121212] px-4 pb-6 pt-6 sm:px-6 lg:px-8">
        <Link
          to={artist.data ? `/artist/${artist.data.user_id}` : "/listen"}
          className="mb-4 inline-flex items-center gap-1.5 text-[13.5px] font-semibold text-white/70 transition hover:text-white"
        >
          <ArrowLeft className="h-4 w-4" /> {artist.data ? artist.data.stage_name : "Back"}
        </Link>

        <div className="flex flex-col items-start gap-5 sm:flex-row sm:items-end">
          <Cover
            src={art}
            alt={data.title}
            seed={`release-${data.id}`}
            className="h-40 w-40 shrink-0 sm:h-52 sm:w-52"
            rounded="rounded-md"
          />
          <div className="min-w-0">
            <p className="text-[13px] font-bold text-white/70">{kindLabel}</p>
            <h1 className="mt-1 break-words text-[34px] font-extrabold leading-none tracking-[-0.035em] sm:text-[52px]">
              {data.title}
            </h1>
            <p className="mt-3 text-[14.5px] text-white/80">
              {artist.data ? (
                <Link to={`/artist/${artist.data.user_id}`} className="font-bold text-white hover:underline">
                  {artist.data.stage_name}
                </Link>
              ) : (
                "Loading artist"
              )}
              <span className="mx-2 opacity-50">·</span>
              {songs.length} {songs.length === 1 ? "song" : "songs"}
              <span className="mx-2 opacity-50">·</span>
              {formatTimeAgo(data.published_at)}
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
            aria-label="Play this release"
          >
            <Play className="h-6 w-6 translate-x-[2px]" fill="currentColor" />
          </button>
          <ReportButton targetType="artist" targetId={data.artist_id} targetLabel={data.title} />
          <ShareButton
            title={`${data.title} by ${artist.data?.stage_name ?? "an ORU artist"}`}
            text={`Listen to ${data.title} on ORU`}
            className="grid h-11 w-11 place-items-center rounded-full bg-white/10 text-white transition press hover:bg-white/20"
          />
        </div>

        <div className="rounded-lg bg-[#181818] p-2">
          {songs.length === 0 ? (
            <p className="px-4 py-8 text-center text-[15px] text-ink-soft">This release has no songs yet.</p>
          ) : (
            songs.map((song, index) => <TrackRow key={song.id} song={song} queue={songs} position={index + 1} />)
          )}
        </div>
      </div>
    </div>
  );
}
