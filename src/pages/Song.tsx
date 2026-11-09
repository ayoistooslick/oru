import { useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Download, Pause, Play, Share2 } from "../components/icons";
import { mediaUrl, request } from "../lib/api";
import { songWebUrl, APK_URL } from "../lib/links";
import { useResource } from "../hooks/useResource";
import { Cover } from "../components/Cover";
import { OruLogo } from "../components/OruMark";
import { Spinner } from "../components/ui";
import { useToasts } from "../state/ToastContext";
import type { Song } from "../lib/types";

function clock(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const whole = Math.floor(seconds);
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, "0")}`;
}

/**
 * A song someone shared, opened by whoever got the link.
 *
 * It is public so the song plays straight away on an iPhone, a laptop, or a
 * phone without ORU installed. On Android the app takes over instead, through
 * the hand-off every page gets from OpenInApp.
 */
export function SongLink() {
  const params = useParams();
  const songId = Number(params.songId);
  const toasts = useToasts();
  const audioRef = useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = useState(false);
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(0);

  const result = useResource<{ song: Song }>(
    (signal) => request(`/songs/${songId}`, { signal }),
    [songId],
  );
  const song = result.data?.song;
  const src = mediaUrl(song?.audio_url);

  const toggle = () => {
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.paused) void audio.play();
    else audio.pause();
  };

  const share = async () => {
    if (!song) return;
    const url = songWebUrl(song.id);
    if (navigator.share) {
      try {
        await navigator.share({ title: `${song.title} by ${song.artist_name}`, url });
        return;
      } catch {
        /* The sheet was dismissed, or the browser refused it. Copy instead. */
      }
    }
    try {
      await navigator.clipboard.writeText(url);
      toasts.saved("Link copied", "Paste it anywhere to share this song.");
    } catch {
      toasts.error("Could not copy the link", "Copy the address from the bar instead.");
    }
  };

  if (result.error) {
    return (
      <div className="grid min-h-[100dvh] place-items-center bg-paper px-5 text-center">
        <div className="max-w-sm">
          <OruLogo />
          <h1 className="mt-6 font-display text-[26px] font-extrabold tracking-[-0.02em]">
            That song is not here
          </h1>
          <p className="mt-2 text-[15px] leading-relaxed text-ink-soft">
            It may have been taken down, or the link lost a digit on the way.
          </p>
          <Link
            to="/"
            className="mt-6 inline-flex h-11 items-center gap-2 rounded-full bg-flame px-6 text-[14.5px] font-bold text-cream press"
          >
            <ArrowLeft className="h-4 w-4" /> Back to ORU
          </Link>
        </div>
      </div>
    );
  }

  if (!song) {
    return (
      <div className="grid min-h-[100dvh] place-items-center bg-paper">
        <div className="flex items-center gap-3 text-ink-soft">
          <Spinner /> <span className="text-[15px] font-semibold">Opening the song</span>
        </div>
      </div>
    );
  }

  const progress = duration > 0 ? (position / duration) * 100 : 0;

  return (
    <div className="min-h-[100dvh] bg-paper text-ink">
      <header className="mx-auto flex h-16 max-w-4xl items-center justify-between px-5">
        <Link to="/" aria-label="ORU home">
          <OruLogo />
        </Link>
        <Link
          to="/listen"
          className="text-[13.5px] font-semibold text-ink-soft transition hover:text-ink"
        >
          Open the player
        </Link>
      </header>

      <main className="mx-auto max-w-4xl px-5 pb-16">
        <div className="grid gap-8 pt-4 sm:grid-cols-[220px_1fr] sm:items-end">
          <Cover
            src={song.cover_url}
            alt={song.title}
            seed={`song-${song.id}`}
            className="h-[220px] w-[220px]"
          />
          <div className="min-w-0">
            <p className="label-caps">Song on ORU</p>
            <h1 className="mt-2 break-words font-display text-[clamp(2rem,6vw,3rem)] font-extrabold leading-[1.05] tracking-[-0.03em]">
              {song.title}
            </h1>
            <Link
              to={`/artist/${song.artist_id}`}
              className="mt-2 inline-block text-[16px] font-semibold text-ink-soft transition hover:text-flame-ink"
            >
              {song.artist_name}
            </Link>
            <p className="mt-1 text-[13.5px] text-ink-faint">
              {song.plays_count.toLocaleString()} {song.plays_count === 1 ? "play" : "plays"}
              <span className="mx-1.5 opacity-50">·</span>
              {song.likes_count.toLocaleString()} {song.likes_count === 1 ? "like" : "likes"}
            </p>
          </div>
        </div>

        <div className="mt-8 rounded-2xl border border-hair bg-card p-5">
          <div className="flex items-center gap-4">
            <button
              type="button"
              onClick={toggle}
              disabled={!src}
              aria-label={playing ? "Pause" : "Play"}
              className="grid h-14 w-14 shrink-0 place-items-center rounded-full bg-flame text-cream shadow-ember transition hover:bg-flame-ink press disabled:opacity-50"
            >
              {playing ? (
                <Pause className="h-5 w-5" fill="currentColor" />
              ) : (
                <Play className="h-5 w-5 translate-x-[2px]" fill="currentColor" />
              )}
            </button>
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between text-[12px] font-semibold tabular-nums text-ink-faint">
                <span>{clock(position)}</span>
                <span>{clock(duration)}</span>
              </div>
              <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-ink/10">
                <div className="h-full rounded-full bg-flame" style={{ width: `${progress}%` }} />
              </div>
            </div>
            <button
              type="button"
              onClick={() => void share()}
              aria-label="Share this song"
              className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-ink/8 text-ink transition hover:bg-ink/14 press"
            >
              <Share2 className="h-5 w-5" />
            </button>
          </div>

          {src ? (
            <audio
              ref={audioRef}
              src={src}
              preload="metadata"
              className="hidden"
              onPlay={() => setPlaying(true)}
              onPause={() => setPlaying(false)}
              onEnded={() => {
                setPlaying(false);
                setPosition(0);
              }}
              onTimeUpdate={(event) => setPosition(event.currentTarget.currentTime)}
              onLoadedMetadata={(event) => setDuration(event.currentTarget.duration)}
            />
          ) : (
            <p className="mt-4 text-[13.5px] text-ink-faint">This song has no audio yet.</p>
          )}

          {song.description ? (
            <p className="mt-4 whitespace-pre-line text-[14.5px] leading-relaxed text-ink-soft">
              {song.description}
            </p>
          ) : null}
        </div>

        <div className="mt-6 flex flex-col gap-3 rounded-2xl border border-hair bg-card-hi px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-[14.5px] font-bold text-ink">Keep the whole of ORU with you</p>
            <p className="mt-0.5 text-[13.5px] text-ink-soft">
              The browser does everything. The Android app also plays offline.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <a
              href={APK_URL}
              className="inline-flex h-10 items-center gap-2 rounded-full border border-hair px-5 text-[14px] font-semibold text-ink transition hover:border-ink-soft hover:bg-ink/8"
            >
              <Download className="h-4 w-4" /> Android app
            </a>
          </div>
        </div>
      </main>
    </div>
  );
}
