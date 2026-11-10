import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useLocation } from "react-router-dom";
import { Check, ImagePlus, Music4 } from "../components/ui/icons";
import { request, upload } from "../lib/api";
import { useResource } from "../hooks/useResource";
import { audioDuration } from "../lib/audio";
import type { Artist, Paginated, Song } from "../lib/types";
import { Button, EmptyState, Field, Spinner, TextArea } from "../components/ui/primitives";
import { TrackRow } from "../components/media/TrackRow";
import { Page } from "../components/layout/Page";
import { useAuth } from "../state/AuthContext";
import { useToasts } from "../state/ToastContext";

const MAX_AUDIO = 100 * 1024 * 1024;
const MAX_IMAGE = 10 * 1024 * 1024;

type Inbox = { items: Song[] };

interface ExternalShare {
  id: number;
  title: string;
  artist_name: string;
  url: string;
  note: string;
  created_at: string;
  sharer: { handle: string; display_name: string };
}

export function SongSharing() {
  const { user, isArtist, isAdmin } = useAuth();
  const canReview = isArtist || isAdmin;
  const { hash } = useLocation();
  const toasts = useToasts();
  const audioRef = useRef<HTMLInputElement>(null);
  const coverRef = useRef<HTMLInputElement>(null);
  const [artistQuery, setArtistQuery] = useState("");
  const [debouncedArtistQuery, setDebouncedArtistQuery] = useState("");
  const [selectedArtist, setSelectedArtist] = useState<Artist | null>(null);
  const [title, setTitle] = useState("");
  const [sourceUrl, setSourceUrl] = useState("");
  const [description, setDescription] = useState("");
  const [audioName, setAudioName] = useState("");
  const [coverName, setCoverName] = useState("");
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState(0);
  const [withdrawing, setWithdrawing] = useState<number | null>(null);
  const [deciding, setDeciding] = useState<number | null>(null);
  // A song that lives somewhere else, for an artist who is not on ORU yet.
  const [extTitle, setExtTitle] = useState("");
  const [extArtist, setExtArtist] = useState("");
  const [extUrl, setExtUrl] = useState("");
  const [extNote, setExtNote] = useState("");
  const [extBusy, setExtBusy] = useState(false);
  const [withdrawingExt, setWithdrawingExt] = useState<number | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedArtistQuery(artistQuery.trim()), 220);
    return () => window.clearTimeout(timer);
  }, [artistQuery]);

  useEffect(() => {
    if (hash !== "#share-inbox" || !canReview) return;
    const timer = window.setTimeout(() => {
      document.getElementById("share-inbox")?.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 0);
    return () => window.clearTimeout(timer);
  }, [hash, canReview]);

  const externalShares = useResource<Paginated<ExternalShare>>(
    (signal) => request("/shared/external?limit=50", { signal }),
    [],
  );
  const artists = useResource<Paginated<Artist>>(
    (signal) => debouncedArtistQuery
      ? request(`/artists?limit=8&q=${encodeURIComponent(debouncedArtistQuery)}`, { signal })
      : Promise.resolve({ items: [], limit: 8, offset: 0 }),
    [debouncedArtistQuery],
  );
  const ownShares = useResource<Paginated<Song>>((signal) => request("/shared/songs?limit=50", { signal }), []);
  const inbox = useResource<Inbox>(
    (signal) => canReview ? request("/songs/pending-shares", { signal }) : Promise.resolve({ items: [] }),
    [canReview],
  );

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const audio = audioRef.current?.files?.[0];
    const cover = coverRef.current?.files?.[0];
    if (!selectedArtist) {
      toasts.error("Choose an artist", "Search for an ORU artist and select their name.");
      return;
    }
    if (!audio) {
      toasts.error("Choose an audio file", "Add the song audio file before sending it.");
      return;
    }
    if (audio.size > MAX_AUDIO) {
      toasts.error("Audio file too big", "Keep it under 100 MB.");
      return;
    }
    if (cover && cover.size > MAX_IMAGE) {
      toasts.error("Cover picture too big", "Keep artwork under 10 MB.");
      return;
    }

    const form = new FormData();
    form.append("artist_id", String(selectedArtist.user_id));
    form.append("title", title.trim());
    form.append("source_url", sourceUrl.trim());
    form.append("description", description.trim());
    form.append("audio", audio);
    const durationMs = await audioDuration(audio);
    if (durationMs) form.append("duration_ms", String(durationMs));
    if (cover) form.append("cover", cover);

    setBusy(true);
    setProgress(0);
    try {
      const result = await upload<{ song: Song }>("/songs/share", form, setProgress);
      toasts.saved("Sent for review", `${result.song.title} is waiting for the artist or a moderator.`);
      setTitle("");
      setSourceUrl("");
      setDescription("");
      setArtistQuery("");
      setDebouncedArtistQuery("");
      setSelectedArtist(null);
      setAudioName("");
      setCoverName("");
      if (audioRef.current) audioRef.current.value = "";
      if (coverRef.current) coverRef.current.value = "";
      ownShares.reload();
    } catch (error) {
      toasts.error("Could not share the song", error instanceof Error ? error.message : "Try again.");
    } finally {
      setBusy(false);
      setProgress(0);
    }
  };

  const withdraw = async (song: Song) => {
    setWithdrawing(song.id);
    try {
      await request(`/shared/songs/${song.id}`, { method: "DELETE" });
      toasts.info("Share withdrawn", `${song.title} is no longer waiting for review.`);
      ownShares.reload();
    } catch (error) {
      toasts.error("Could not withdraw it", error instanceof Error ? error.message : "Try again.");
    } finally {
      setWithdrawing(null);
    }
  };

  const decide = async (song: Song, action: "approve" | "reject") => {
    setDeciding(song.id);
    try {
      await request(`/songs/${song.id}/share-decision`, { method: "POST", body: { action } });
      toasts.saved(action === "approve" ? "Song approved" : "Share rejected", song.title);
      inbox.reload();
      ownShares.reload();
    } catch (error) {
      toasts.error("Could not save the decision", error instanceof Error ? error.message : "Try again.");
    } finally {
      setDeciding(null);
    }
  };

  const submitExternal = async (event: FormEvent) => {
    event.preventDefault();
    if (!extTitle.trim() || !extArtist.trim() || !extUrl.trim()) return;
    setExtBusy(true);
    try {
      await request("/shared/external", {
        method: "POST",
        body: { title: extTitle.trim(), artist_name: extArtist.trim(), url: extUrl.trim(), note: extNote.trim() },
      });
      toasts.saved("Shared", `${extTitle.trim()} is now in the list for others to hear.`);
      setExtTitle("");
      setExtArtist("");
      setExtUrl("");
      setExtNote("");
      externalShares.reload();
    } catch (error) {
      toasts.error("Could not share the song", error instanceof Error ? error.message : "Try again.");
    } finally {
      setExtBusy(false);
    }
  };

  const withdrawExternal = async (share: ExternalShare) => {
    setWithdrawingExt(share.id);
    try {
      await request(`/shared/external/${share.id}`, { method: "DELETE" });
      toasts.info("Share removed", share.title);
      externalShares.reload();
    } catch (error) {
      toasts.error("Could not remove it", error instanceof Error ? error.message : "Try again.");
    } finally {
      setWithdrawingExt(null);
    }
  };

  const artistMatches = artists.data?.items ?? [];
  const myShares = ownShares.data?.items ?? [];
  const pendingShares = inbox.data?.items ?? [];
  const outside = externalShares.data?.items ?? [];

  return (
    <Page
      title="Share a song"
      subtitle="Help listeners find music from ORU artists. The artist keeps the credit, and nothing goes public until the artist or a moderator approves it."
      actions={canReview ? <a href="#share-inbox" className="text-[14px] font-bold text-ink underline underline-offset-4">Review pending shares</a> : undefined}
    >
      <section className="mb-9 rounded-xl bg-card p-5 sm:p-6">
        <h2 className="text-[21px] font-bold tracking-[-0.02em]">Send a song for approval</h2>
        <p className="mt-1 text-[14px] leading-relaxed text-ink-soft">Find its ORU artist, add the source link and upload the audio. You can withdraw it while it is pending.</p>

        <form onSubmit={submit} className="mt-5 space-y-5">
          <div className="relative">
            <label className="block">
              <span className="label-caps">Artist on ORU <span className="text-[#ff6b60]">Required</span></span>
              <input
                type="search"
                value={artistQuery}
                onChange={(event) => {
                  setArtistQuery(event.target.value);
                  setSelectedArtist(null);
                }}
                placeholder="Search artist name"
                autoComplete="off"
                aria-label="Search for an artist on ORU"
                aria-expanded={!selectedArtist && artistQuery.trim().length > 0 && artistMatches.length > 0}
                className="mt-2 h-12 w-full rounded-lg border border-hair bg-card px-4 text-[16px] text-ink outline-none transition placeholder:text-ink-faint focus:border-flame-ink"
              />
            </label>
            {selectedArtist ? (
              <div className="mt-2 flex items-center justify-between gap-3 rounded-lg bg-ink/4 px-4 py-3 text-[14px]">
                <span className="min-w-0 truncate"><strong>{selectedArtist.stage_name}</strong> <span className="text-ink-soft">@{selectedArtist.handle}</span></span>
                <button type="button" onClick={() => { setSelectedArtist(null); setArtistQuery(""); }} className="shrink-0 text-ink-soft underline underline-offset-4 hover:text-ink">Change</button>
              </div>
            ) : null}
            {!selectedArtist && debouncedArtistQuery ? (
              <div className="mt-2 overflow-hidden rounded-lg border border-hair bg-card-hi">
                {artists.loading ? <p className="px-4 py-3 text-[13.5px] text-ink-soft">Searching artists…</p> : null}
                {!artists.loading && artistMatches.length === 0 ? <p className="px-4 py-3 text-[13.5px] text-ink-soft">No ORU artist found. Try another name.</p> : null}
                {artistMatches.map((artist) => (
                  <button
                    key={artist.user_id}
                    type="button"
                    onClick={() => { setSelectedArtist(artist); setArtistQuery(artist.stage_name); }}
                    className="block w-full border-b border-hair px-4 py-3 text-left last:border-0 hover:bg-ink/6"
                  >
                    <span className="block truncate text-[14.5px] font-bold">{artist.stage_name}</span>
                    <span className="block truncate text-[12.5px] text-ink-soft">@{artist.handle}</span>
                  </button>
                ))}
                {artists.error ? <p className="px-4 py-3 text-[13.5px] text-[#ff6b60]">{artists.error}</p> : null}
              </div>
            ) : null}
          </div>

          <div className="grid gap-5 lg:grid-cols-2">
            <div className="space-y-4">
              <Field label="Song title · Required" value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Song title" maxLength={120} required />
              <Field label="Source URL · Required" type="url" value={sourceUrl} onChange={(event) => setSourceUrl(event.target.value)} placeholder="https://…" maxLength={500} required />
              <TextArea label="Description" value={description} onChange={(event) => setDescription(event.target.value)} placeholder="A little about this song" rows={3} maxLength={2000} hint="Optional" />
            </div>

            <div className="flex flex-col gap-3">
              <label className="flex cursor-pointer items-center gap-3 rounded-lg bg-card px-4 py-4 transition hover:bg-card-hi">
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded bg-flame text-cream"><Music4 className="h-5 w-5" /></span>
                <span className="min-w-0 flex-1">
                  <span className="block text-[14.5px] font-bold">Audio file · Required</span>
                  <span className="block truncate text-[13px] text-ink-soft">{audioName || "MP3, WAV, M4A, OGG, FLAC or OPUS · Up to 100 MB"}</span>
                </span>
                <input ref={audioRef} type="file" accept="audio/*,.mp3,.wav,.m4a,.ogg,.flac,.opus" required className="sr-only" onChange={(event) => setAudioName(event.target.files?.[0]?.name ?? "")} />
              </label>
              <label className="flex cursor-pointer items-center gap-3 rounded-lg bg-card px-4 py-4 transition hover:bg-card-hi">
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded bg-ink/10 text-ink">{coverName ? <Check className="h-5 w-5 text-flame-ink" /> : <ImagePlus className="h-5 w-5" />}</span>
                <span className="min-w-0 flex-1">
                  <span className="block text-[14.5px] font-bold">Cover picture</span>
                  <span className="block truncate text-[13px] text-ink-soft">{coverName || "Optional · JPEG, PNG or WebP · Up to 10 MB"}</span>
                </span>
                <input ref={coverRef} type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" onChange={(event) => setCoverName(event.target.files?.[0]?.name ?? "")} />
              </label>
              <div className="mt-auto flex flex-wrap items-center gap-3 pt-2">
                <Button type="submit" disabled={busy || !selectedArtist || !title.trim() || !sourceUrl.trim() || !audioName}>
                  {busy ? `Uploading ${Math.round(progress * 100)}%` : "Send for approval"}
                </Button>
                <span className="text-[12.5px] text-ink-faint">Your submission stays private until approved.</span>
              </div>
            </div>
          </div>
        </form>
      </section>

      <section className="mb-9 rounded-xl bg-card p-5 sm:p-6">
        <h2 className="text-[19px] font-bold tracking-[-0.02em]">Share a song not on ORU yet</h2>
        <p className="mt-1 text-[14px] leading-relaxed text-ink-soft">
          Heard something good by an artist who has not joined ORU? Paste the link. Anyone can open it from the list below.
        </p>
        <form onSubmit={submitExternal} className="mt-4 grid gap-4 lg:grid-cols-2">
          <div className="space-y-4">
            <Field label="Song title · Required" value={extTitle} onChange={(event) => setExtTitle(event.target.value)} placeholder="Song title" maxLength={120} required />
            <Field label="Artist name · Required" value={extArtist} onChange={(event) => setExtArtist(event.target.value)} placeholder="Who sings it" maxLength={120} required />
          </div>
          <div className="space-y-4">
            <Field label="Link to the song · Required" type="url" value={extUrl} onChange={(event) => setExtUrl(event.target.value)} placeholder="https://…" maxLength={500} required />
            <TextArea label="Why you like it" value={extNote} onChange={(event) => setExtNote(event.target.value)} placeholder="A short note for other listeners" rows={2} maxLength={1000} hint="Optional" />
          </div>
          <div className="lg:col-span-2">
            <Button type="submit" disabled={extBusy || !extTitle.trim() || !extArtist.trim() || !extUrl.trim()}>
              {extBusy ? "Sharing" : "Share this song"}
            </Button>
          </div>
        </form>
      </section>

      <section className="mb-9" aria-labelledby="outside-heading">
        <div className="mb-3 flex flex-wrap items-baseline justify-between gap-3">
          <h2 id="outside-heading" className="text-[19px] font-bold tracking-[-0.02em]">Shared from outside ORU</h2>
          {externalShares.loading ? <Spinner className="h-4 w-4 text-ink-faint" /> : null}
        </div>
        {externalShares.error ? <EmptyState title="Could not load these shares" message={externalShares.error} /> : null}
        {!externalShares.loading && outside.length === 0 ? (
          <p className="rounded-xl bg-card px-5 py-6 text-[14px] text-ink-soft">No songs shared from outside ORU yet.</p>
        ) : null}
        {outside.length > 0 ? (
          <ul className="space-y-2">
            {outside.map((share) => {
              const mine = share.sharer.handle === user?.handle;
              return (
                <li key={share.id} className="rounded-xl bg-card p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[15.5px] font-bold">{share.title}</p>
                      <p className="truncate text-[13.5px] text-ink-soft">{share.artist_name}</p>
                      {share.note ? <p className="mt-1.5 text-[14px] leading-relaxed text-ink-soft">{share.note}</p> : null}
                      <p className="mt-1 text-[12.5px] text-ink-faint">Shared by {share.sharer.display_name}</p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      {mine ? (
                        <Button type="button" variant="outline" size="sm" disabled={withdrawingExt === share.id} onClick={() => void withdrawExternal(share)}>
                          {withdrawingExt === share.id ? "Removing" : "Remove"}
                        </Button>
                      ) : null}
                      <a
                        href={share.url}
                        target="_blank"
                        rel="noreferrer noopener"
                        className="inline-flex h-9 items-center rounded-full bg-flame px-4 text-[13px] font-semibold text-cream press"
                      >
                        Listen
                      </a>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        ) : null}
      </section>

      <section className="mb-9" aria-labelledby="my-shares-heading">
        <div className="mb-3 flex flex-wrap items-baseline justify-between gap-3">
          <h2 id="my-shares-heading" className="text-[21px] font-bold tracking-[-0.02em]">Your shared songs</h2>
          {ownShares.loading ? <Spinner className="h-4 w-4 text-ink-faint" /> : null}
        </div>
        {ownShares.error ? <EmptyState title="Could not load your submissions" message={ownShares.error} /> : null}          {!ownShares.loading && myShares.length === 0 ? <p className="rounded-xl bg-card px-5 py-6 text-[14px] text-ink-soft">You have not shared a song yet.</p> : null}
        {myShares.length > 0 ? (
          <ul className="space-y-2 rounded-xl bg-card p-2">
            {myShares.map((song) => (
              <li key={song.id}>
                <TrackRow
                  song={song}
                  showStatus
                  actions={song.status === "pending" ? (
                    <Button type="button" variant="outline" size="sm" disabled={withdrawing === song.id} onClick={() => void withdraw(song)}>
                      {withdrawing === song.id ? "Withdrawing" : "Withdraw"}
                    </Button>
                  ) : null}
                />
              </li>
            ))}
          </ul>
        ) : null}
      </section>

      {canReview ? (
        <section id="share-inbox" className="scroll-mt-5" aria-labelledby="share-inbox-heading">
          <div className="mb-3 flex flex-wrap items-baseline justify-between gap-3">
            <div>
              <h2 id="share-inbox-heading" className="text-[21px] font-bold tracking-[-0.02em]">Pending shares</h2>
              <p className="mt-1 text-[13.5px] text-ink-soft">Artist submissions for your review. Moderators can review all artists’ queues.</p>
            </div>
            {inbox.loading ? <Spinner className="h-4 w-4 text-ink-faint" /> : null}
          </div>
          {inbox.error ? <EmptyState title="Could not load the share inbox" message={inbox.error} /> : null}
          {!inbox.loading && pendingShares.length === 0 ? <p className="rounded-xl bg-card px-5 py-6 text-[14px] text-ink-soft">There are no songs waiting for review.</p> : null}
          {pendingShares.length > 0 ? (
            <ul className="space-y-3">
              {pendingShares.map((song) => (
                <li key={song.id} className="rounded-xl bg-card p-3 sm:p-4">
                  <TrackRow song={song} showStatus />
                  <div className="ml-[52px] mt-2 space-y-2">
                    {song.shared_by ? <p className="text-[13px] text-ink-soft">Shared by <strong className="text-ink">{song.shared_by.display_name}</strong> (@{song.shared_by.handle})</p> : null}
                    {song.description ? <p className="whitespace-pre-wrap text-[14px] leading-relaxed text-ink-soft">{song.description}</p> : null}
                    {song.source_url ? <a href={song.source_url} target="_blank" rel="noreferrer" className="inline-block max-w-full truncate text-[13px] font-semibold text-ink underline underline-offset-4">Open source link</a> : null}
                    <div className="flex flex-wrap gap-2 pt-1">
                      <Button type="button" disabled={deciding === song.id} onClick={() => void decide(song, "approve")}>{deciding === song.id ? "Saving" : "Approve"}</Button>
                      <Button type="button" variant="danger" disabled={deciding === song.id} onClick={() => void decide(song, "reject")}>Reject</Button>
                      {isAdmin ? <Link to={`/artist/${song.artist_id}`} className="inline-flex h-11 items-center px-3 text-[13.5px] font-semibold text-ink-soft underline underline-offset-4 hover:text-ink">View artist</Link> : null}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          ) : null}
        </section>
      ) : null}
    </Page>
  );
}
