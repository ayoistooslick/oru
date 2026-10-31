import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Check, ImagePlus, Mic, Music4, Scissors, Send, Trash2, UploadCloud, Users, X } from "lucide-react";
import { ApiError, auth, request, upload } from "../lib/api";
import { useResource } from "../hooks/useResource";
import { formatTimeAgo } from "../lib/format";
import type { Artist, ArtistPost, ArtistStats, Paginated, Release, Song } from "../lib/types";
import { TrackRow } from "../components/TrackRow";
import { Cover } from "../components/Cover";
import { AudioEditor } from "../components/AudioEditor";
import { RecorderSheet } from "../components/AudioRecorder";
import { Button, EmptyState, Field, Spinner, TextArea } from "../components/ui";
import { useAuth } from "../state/AuthContext";
import { useToasts } from "../state/ToastContext";
import { LAUNCH_FILES_EVENT, notify, takeLaunchFiles } from "../lib/pwa";

const MAX_AUDIO = 100 * 1024 * 1024;
const MAX_IMAGE = 10 * 1024 * 1024;

function Block({ label }: { label: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-ink-soft">
      <Spinner />
      <span className="text-[15px] font-semibold">{label}</span>
    </div>
  );
}

function Stat({ value, label }: { value: number | string; label: string }) {
  return (
    <div className="rounded-lg bg-[#181818] px-4 py-3.5">
      <p className="text-[26px] font-extrabold leading-none tracking-[-0.04em]">{value}</p>
      <p className="label-caps mt-2">{label}</p>
    </div>
  );
}

/* Opening: nothing to publish until there is an artist name. */
function Opening({ onReady }: { onReady: () => void }) {
  const { updateUser, user } = useAuth();
  const toasts = useToasts();
  const [stageName, setStageName] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const name = stageName.trim();
    if (!name) return;
    setBusy(true);
    try {
      await request("/artists/me", { method: "POST", body: { stage_name: name } });
      const me = await auth.me();
      updateUser(me.user);
      toasts.saved("Studio ready", `Publishing as ${name}`);
      onReady();
    } catch (error) {
      toasts.error("Could not open it", error instanceof Error ? error.message : "Try again.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-10 sm:px-6 lg:px-8">
      <p className="text-[13px] font-bold text-ink-soft">Studio</p>
      <h1 className="mt-1 text-[32px] font-extrabold tracking-[-0.03em] sm:text-[42px]">What name do you perform under?</h1>
      <p className="mt-3 text-[15.5px] leading-relaxed text-ink-soft">
        This is the name listeners will see on every song you publish.
      </p>

      <form onSubmit={submit} className="mt-7 rounded-lg bg-[#181818] p-6">
        <Field
          label="Artist name"
          value={stageName}
          onChange={(event) => setStageName(event.target.value)}
          placeholder="Your artist name"
          maxLength={100}
          autoFocus
          hint="Check the spelling. This is what people will search for."
        />
        <div className="mt-6 flex flex-wrap items-center gap-4">
          <Button type="submit" disabled={busy || !stageName.trim()}>
            {busy ? "Opening" : "Open my studio"}
          </Button>
          <Link to="/listen" className="text-[14.5px] font-semibold text-ink-soft hover:text-white">
            Not now
          </Link>
        </div>
      </form>
      <p className="mt-4 text-[13.5px] text-ink-faint">Signed in as @{user?.handle}</p>
    </div>
  );
}

/* The desk: upload, publish, release, post. */
function Desk({ onLeave }: { onLeave: () => void }) {
  const toasts = useToasts();

  const stats = useResource<ArtistStats>((signal) => request("/artists/me/stats", { signal }), []);
  const songs = useResource<Paginated<Song>>((signal) => request("/artists/me/songs?limit=50", { signal }), []);
  const releases = useResource<Paginated<Release>>((signal) => request("/artists/me/releases?limit=50", { signal }), []);
  const posts = useResource<Paginated<ArtistPost>>((signal) => request("/artists/me/posts?limit=10", { signal }), []);

  const audioRef = useRef<HTMLInputElement>(null);
  const coverRef = useRef<HTMLInputElement>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [genres, setGenres] = useState("");
  const [audioFile, setAudioFile] = useState<File | null>(null);
  const [coverName, setCoverName] = useState("");
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState(0);
  const [recording, setRecording] = useState(false);
  const [editing, setEditing] = useState<File | null>(null);
  const [featuredIds, setFeaturedIds] = useState<number[]>([]);
  const [guestQuery, setGuestQuery] = useState("");
  const [guestResults, setGuestResults] = useState<Artist[]>([]);
  const audioName = audioFile?.name ?? "";
  const { user } = useAuth();
  const selfId = user?.id;

  // Look up other artists to credit on the song. Two letters is enough to
  // start, and the list closes as soon as one is picked.
  useEffect(() => {
    const query = guestQuery.trim();
    if (query.length < 2) {
      setGuestResults([]);
      return;
    }
    let alive = true;
    const timer = window.setTimeout(() => {
      request<Paginated<Artist>>(`/artists?q=${encodeURIComponent(query)}&limit=6`)
        .then((result) => alive && setGuestResults(result.items.filter((artist) => artist.user_id !== selfId)))
        .catch(() => alive && setGuestResults([]));
    }, 220);
    return () => {
      alive = false;
      window.clearTimeout(timer);
    };
  }, [guestQuery, selfId]);

  const takeFile = (file: File) => {
    setAudioFile(file);
    if (audioRef.current) {
      const transfer = new DataTransfer();
      transfer.items.add(file);
      audioRef.current.files = transfer.files;
    }
  };

  const [releasing, setReleasing] = useState(false);
  const [releaseTitle, setReleaseTitle] = useState("");
  const [kind, setKind] = useState<"single" | "ep" | "album">("single");
  const [picked, setPicked] = useState<number[]>([]);

  const [post, setPost] = useState("");
  const [posting, setPosting] = useState(false);
  const [confirming, setConfirming] = useState<number | null>(null);

  const catalogue = songs.data?.items ?? [];

  // Files handed over by the phone's file picker ("Open with ORU") land here.
  useEffect(() => {
    const onFiles = (event: Event) => {
      const files = (event as CustomEvent<File[]>).detail ?? [];
      const audio = files.find((file) => file.type.startsWith("audio/"));
      const picture = files.find((file) => file.type.startsWith("image/"));
      if (audio) takeFile(audio);
      if (picture && coverRef.current) {
        const transfer = new DataTransfer();
        transfer.items.add(picture);
        coverRef.current.files = transfer.files;
        setCoverName(picture.name);
      }
      if (files.length) toasts.info("File ready", "Give it a title, then save it as a draft.");
    };
    window.addEventListener(LAUNCH_FILES_EVENT, onFiles);
    const waiting = takeLaunchFiles();
    if (waiting.length) {
      window.dispatchEvent(new CustomEvent<File[]>(LAUNCH_FILES_EVENT, { detail: waiting }));
    }
    return () => window.removeEventListener(LAUNCH_FILES_EVENT, onFiles);
  }, []);

  const submitUpload = async (event: FormEvent) => {
    event.preventDefault();
    const chosen = audioFile ?? audioRef.current?.files?.[0];
    if (!chosen) {
      toasts.error("Choose a song file", "MP3, WAV, M4A, OGG or FLAC. Up to 100 MB.");
      return;
    }
    if (chosen.size > MAX_AUDIO) {
      toasts.error("File too big", "Keep it under 100 MB.");
      return;
    }
    const form = new FormData();
    form.append("title", title.trim());
    form.append("description", description.trim());
    genres
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean)
      .forEach((value) => form.append("genres", value));
    form.append("audio", chosen);
    featuredIds.forEach((id) => form.append("featured", String(id)));
    const art = coverRef.current?.files?.[0];
    if (art) {
      if (art.size > MAX_IMAGE) {
        toasts.error("Image too big", "Keep artwork under 10 MB.");
        return;
      }
      form.append("cover", art);
    }

    setBusy(true);
    setProgress(0);
    try {
      const result = await upload<{ song: Song }>("/artists/me/songs", form, setProgress);
      toasts.upload("Saved as draft", result.song.title);
      setTitle("");
      setDescription("");
      setGenres("");
      setAudioFile(null);
      setFeaturedIds([]);
      setGuestQuery("");
      setCoverName("");
      if (audioRef.current) audioRef.current.value = "";
      if (coverRef.current) coverRef.current.value = "";
      songs.reload();
      stats.reload();
    } catch (error) {
      toasts.error("Upload failed", error instanceof Error ? error.message : "Try again.");
    } finally {
      setBusy(false);
      setProgress(0);
    }
  };

  const publish = async (song: Song) => {
    try {
      await request(`/artists/me/songs/${song.id}/publish`, { method: "POST" });
      toasts.saved("Published", `${song.title} is live`);
      void notify("Your song is live", `${song.title} is on ORU now.`);
      songs.reload();
      stats.reload();
      releases.reload();
    } catch (error) {
      toasts.error("Could not publish it", error instanceof Error ? error.message : "Try again.");
    }
  };

  const unpublish = async (song: Song) => {
    try {
      await request(`/artists/me/songs/${song.id}/unpublish`, { method: "POST" });
      toasts.info("Back to drafts", song.title);
      songs.reload();
      stats.reload();
      releases.reload();
    } catch (error) {
      toasts.error("Could not unpublish it", error instanceof Error ? error.message : "Try again.");
    }
  };

  const remove = async (song: Song) => {
    try {
      await request(`/artists/me/songs/${song.id}`, { method: "DELETE" });
      toasts.info("Song deleted", song.title);
      setConfirming(null);
      songs.reload();
      stats.reload();
      releases.reload();
    } catch (error) {
      toasts.error("Could not delete it", error instanceof Error ? error.message : "Try again.");
    }
  };

  const togglePick = (id: number) =>
    setPicked((current) => (current.includes(id) ? current.filter((value) => value !== id) : [...current, id]));

  const limit = kind === "single" ? 1 : kind === "ep" ? 6 : 40;

  const submitRelease = async (event: FormEvent) => {
    event.preventDefault();
    if (!releaseTitle.trim() || picked.length === 0) return;
    try {
      await request("/artists/me/releases", {
        method: "POST",
        body: { title: releaseTitle.trim(), kind, song_ids: picked },
      });
      toasts.saved("Release created", releaseTitle.trim());
      setReleaseTitle("");
      setPicked([]);
      setKind("single");
      setReleasing(false);
      releases.reload();
    } catch (error) {
      toasts.error("Could not create it", error instanceof Error ? error.message : "Try again.");
    }
  };

  const publishRelease = async (release: Release) => {
    try {
      await request(`/artists/me/releases/${release.id}/publish`, { method: "POST" });
      toasts.saved("Release is live", release.title);
      void notify("Your release is live", `${release.title} is on ORU now.`);
      releases.reload();
      songs.reload();
      stats.reload();
    } catch (error) {
      toasts.error("Could not publish it", error instanceof Error ? error.message : "Try again.");
    }
  };

  const submitPost = async (event: FormEvent) => {
    event.preventDefault();
    if (!post.trim()) return;
    setPosting(true);
    try {
      await request("/artists/me/posts", { method: "POST", body: { body: post.trim() } });
      toasts.saved("Posted", "It shows on your artist page");
      setPost("");
      posts.reload();
    } catch (error) {
      toasts.error("Could not post it", error instanceof Error ? error.message : "Try again.");
    } finally {
      setPosting(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-7xl px-4 pb-8 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-end justify-between gap-4 bg-gradient-to-b from-[#1e5c3f]/70 to-transparent px-5 py-6 sm:px-7">
        <div>
          <p className="text-[13px] font-bold text-white/70">Studio</p>
          <h1 className="mt-1 text-[30px] font-extrabold tracking-[-0.03em] sm:text-[38px]">Your songs</h1>
        </div>
        <button
          type="button"
          onClick={onLeave}
          className="text-[13.5px] font-semibold text-ink-soft underline underline-offset-4 hover:text-white"
        >
          Re-check access
        </button>
      </header>

      <section className="mb-8">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Stat value={stats.data?.weekly_plays ?? 0} label="Plays this week" />
          <Stat value={stats.data?.weekly_likes ?? 0} label="Likes this week" />
          <Stat value={stats.data?.qualified_listens ?? 0} label="Plays all time" />
          <Stat value={stats.data?.published_songs ?? 0} label="Songs live" />
        </div>
        {stats.data?.weekly_top_song ? (
          <p className="mt-2 text-[13.5px] text-ink-soft">
            Top this week: <strong className="font-bold text-white">{stats.data.weekly_top_song.title}</strong>
            <span className="mx-1.5 opacity-50">·</span>
            {stats.data.weekly_top_song.plays} plays
          </p>
        ) : null}
      </section>

      <section className="mb-9">
        <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">Upload a song</h2>
        <form onSubmit={submitUpload} className="rounded-lg bg-[#181818] p-5 sm:p-6">
          <div className="grid gap-5 lg:grid-cols-2">
            <div className="space-y-4">
              <Field
                label="Song title"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder="Song title"
                maxLength={120}
              />
              <TextArea
                label="About the song"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                placeholder="What is it about?"
                rows={2}
                maxLength={2000}
                hint="Optional"
              />
              <Field
                label="Genre"
                value={genres}
                onChange={(event) => setGenres(event.target.value)}
                placeholder="afrobeats, rap, gospel"
                hint="Separate with commas"
              />

              <div>
                <span className="label-caps">Featuring</span>
                {featuredIds.length ? (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {featuredIds.map((id) => {
                      const guest = guestResults.find((artist) => artist.user_id === id);
                      return (
                        <span
                          key={id}
                          className="inline-flex h-8 items-center gap-1.5 rounded-full bg-[#1ed760]/15 px-3 text-[13px] font-bold text-[#1ed760]"
                        >
                          {guest?.stage_name ?? `Artist ${id}`}
                          <button
                            type="button"
                            onClick={() => setFeaturedIds((current) => current.filter((value) => value !== id))}
                            aria-label={`Remove ${guest?.stage_name ?? "artist"}`}
                            className="grid h-4 w-4 place-items-center rounded-full hover:bg-white/15"
                          >
                            <X className="h-3 w-3" />
                          </button>
                        </span>
                      );
                    })}
                  </div>
                ) : null}
                <div className="relative mt-2">
                  <input
                    value={guestQuery}
                    onChange={(event) => setGuestQuery(event.target.value)}
                    placeholder="Add another artist"
                    className="h-10 w-full rounded-lg border border-transparent bg-white/10 px-3.5 text-[14.5px] outline-none transition placeholder:text-ink-faint focus:border-white"
                    aria-label="Search artists to feature"
                  />
                  {guestQuery.trim().length >= 2 && guestResults.length > 0 ? (
                    <ul className="absolute left-0 right-0 top-11 z-20 max-h-56 overflow-y-auto rounded-lg border border-white/10 bg-[#282828] p-1.5 shadow-xl">
                      {guestResults.map((artist) => {
                        const picked = featuredIds.includes(artist.user_id);
                        const full = !picked && featuredIds.length >= 5;
                        return (
                          <li key={artist.user_id}>
                            <button
                              type="button"
                              disabled={full}
                              onClick={() => {
                                if (picked) setFeaturedIds((current) => current.filter((value) => value !== artist.user_id));
                                else setFeaturedIds((current) => [...current, artist.user_id]);
                                setGuestQuery("");
                              }}
                              className="flex w-full items-center gap-2.5 rounded px-2 py-2 text-left transition hover:bg-white/10 disabled:opacity-40"
                            >
                              <Users className="h-3.5 w-3.5 text-ink-soft" />
                              <span className="min-w-0 flex-1 truncate text-[14px] font-semibold">{artist.stage_name}</span>
                              {picked ? <Check className="h-3.5 w-3.5 text-[#1ed760]" /> : null}
                            </button>
                          </li>
                        );
                      })}
                    </ul>
                  ) : null}
                </div>
                <p className="mt-1.5 text-[12.5px] text-ink-soft">Optional. They keep their own profile and get the credit.</p>
              </div>
            </div>

            <div className="flex flex-col gap-3">
              <label className="flex cursor-pointer items-center gap-3 rounded-lg bg-white/5 px-4 py-4 transition hover:bg-white/10">
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded bg-[#1ed760] text-black">
                  <Music4 className="h-5 w-5" />
                </span>
                <span className="min-w-0">
                  <span className="block text-[14.5px] font-bold">Song file</span>
                  <span className="block truncate text-[13px] text-ink-soft">
                    {audioName || "MP3, WAV, M4A, OGG or FLAC"}
                  </span>
                </span>
                <input
                  ref={audioRef}
                  type="file"
                  accept="audio/*,.mp3,.wav,.m4a,.ogg,.flac,.opus"
                  className="sr-only"
                  onChange={(event) => {
                    const file = event.target.files?.[0];
                    if (file) takeFile(file);
                  }}
                />
              </label>

              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => setRecording(true)}
                  className="flex items-center justify-center gap-2 rounded-lg bg-white/5 px-3 py-3 text-[13.5px] font-bold transition hover:bg-white/10"
                >
                  <Mic className="h-4 w-4 text-[#1ed760]" /> Record
                </button>
                <button
                  type="button"
                  onClick={() => audioFile && setEditing(audioFile)}
                  disabled={!audioFile}
                  className="flex items-center justify-center gap-2 rounded-lg bg-white/5 px-3 py-3 text-[13.5px] font-bold transition hover:bg-white/10 disabled:opacity-40"
                >
                  <Scissors className="h-4 w-4 text-[#1ed760]" /> Trim and fade
                </button>
              </div>
              {audioFile ? (
                <p className="text-[12.5px] text-ink-soft">
                  {(audioFile.size / (1024 * 1024)).toFixed(1)} MB ready. Trim it, or save it as a draft.
                </p>
              ) : null}

              <label className="flex cursor-pointer items-center gap-3 rounded-lg bg-white/5 px-4 py-4 transition hover:bg-white/10">
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded bg-white/10 text-white">
                  {coverName ? <Check className="h-5 w-5 text-[#1ed760]" /> : <ImagePlus className="h-5 w-5" />}
                </span>
                <span className="min-w-0">
                  <span className="block text-[14.5px] font-bold">Cover picture</span>
                  <span className="block truncate text-[13px] text-ink-soft">
                    {coverName || "Optional. Square image."}
                  </span>
                </span>
                <input
                  ref={coverRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  className="sr-only"
                  onChange={(event) => setCoverName(event.target.files?.[0]?.name ?? "")}
                />
              </label>

              {busy ? (
                <div>
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-white/15">
                    <div
                      className="h-full rounded-full bg-[#1ed760] transition-[width] duration-200"
                      style={{ width: `${Math.max(4, Math.round(progress * 100))}%` }}
                    />
                  </div>
                  <p className="mt-2 text-[13px] font-semibold text-ink-soft">
                    {progress < 1 ? `Uploading ${Math.round(progress * 100)}%` : "Finishing"}
                  </p>
                </div>
              ) : null}

              <Button type="submit" disabled={busy || !title.trim()} className="w-full" size="lg">
                <UploadCloud className="h-4 w-4" />
                {busy ? "Uploading" : "Save as draft"}
              </Button>
            </div>
          </div>
        </form>
      </section>

      <section className="mb-9">
        <div className="mb-3 flex items-baseline justify-between gap-4">
          <h2 className="text-[22px] font-bold tracking-[-0.02em]">Your songs</h2>
          <button
            type="button"
            onClick={() => setReleasing((value) => !value)}
            className="text-[13px] font-bold uppercase tracking-wide text-ink-soft hover:text-white"
          >
            {releasing ? "Close" : "New release"}
          </button>
        </div>

        {songs.loading && !songs.data ? (
          <Block label="Loading songs" />
        ) : songs.error ? (
          <EmptyState title="Could not load songs" message={songs.error} />
        ) : catalogue.length === 0 ? (
          <EmptyState title="No songs yet" message="Upload your first song above. It stays private until you publish it." />
        ) : (
          <div className="rounded-lg bg-[#181818] p-2">
            {catalogue.map((song) => (
              <div key={song.id}>
                <TrackRow
                  song={song}
                  queue={catalogue}
                  showStatus
                  actions={
                    <div className="flex items-center gap-1.5">
                      {song.status === "published" ? (
                        <button
                          type="button"
                          onClick={() => unpublish(song)}
                          className="hidden h-9 items-center rounded-full border border-white/25 px-3.5 text-[13px] font-bold transition hover:border-white sm:inline-flex"
                        >
                          Unpublish
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => publish(song)}
                          className="inline-flex h-9 items-center rounded-full bg-[#1ed760] px-3.5 text-[13px] font-bold text-black transition press"
                        >
                          Publish
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => setConfirming(confirming === song.id ? null : song.id)}
                        className="grid h-9 w-9 place-items-center rounded-full text-ink-soft transition hover:bg-white/10 hover:text-white"
                        aria-label={`Delete ${song.title}`}
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  }
                />
                {confirming === song.id ? (
                  <div className="mx-2 mb-2 flex flex-wrap items-center justify-between gap-3 rounded-lg bg-[#3d1f1c] px-4 py-3">
                    <p className="text-[14px] font-semibold text-[#ff9c92]">
                      Delete "{song.title}"? The file will be gone.
                    </p>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => setConfirming(null)}
                        className="h-9 rounded-full px-4 text-[13.5px] font-semibold text-white/80 hover:bg-white/10"
                      >
                        Keep
                      </button>
                      <button
                        type="button"
                        onClick={() => remove(song)}
                        className="h-9 rounded-full bg-[#f0463c] px-4 text-[13.5px] font-bold text-white press"
                      >
                        Delete
                      </button>
                    </div>
                  </div>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </section>

      {releasing ? (
        <section className="mb-9 rounded-lg bg-[#181818] p-5 sm:p-6">
          <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">New release</h2>
          <form onSubmit={submitRelease}>
            <div className="flex flex-wrap gap-2">
              {(["single", "ep", "album"] as const).map((option) => (
                <button
                  key={option}
                  type="button"
                  onClick={() => {
                    setKind(option);
                    setPicked((current) => current.slice(0, option === "single" ? 1 : option === "ep" ? 6 : 40));
                  }}
                  className={`h-9 rounded-full px-4 text-[13.5px] font-bold uppercase tracking-wide transition press ${
                    kind === option ? "bg-white text-black" : "bg-white/10 text-white hover:bg-white/20"
                  }`}
                >
                  {option}
                </button>
              ))}
            </div>

            <div className="mt-4">
              <Field
                label="Release name"
                value={releaseTitle}
                onChange={(event) => setReleaseTitle(event.target.value)}
                placeholder="Name it"
                maxLength={120}
              />
            </div>

            <p className="label-caps mt-5 mb-2">Pick the songs</p>
            {catalogue.length === 0 ? (
              <p className="text-[14.5px] text-ink-soft">Upload a song first.</p>
            ) : (
              <ul className="max-h-64 space-y-1.5 overflow-y-auto pr-1">
                {catalogue.map((song) => {
                  const on = picked.includes(song.id);
                  const full = !on && picked.length >= limit;
                  return (
                    <li key={song.id}>
                      <button
                        type="button"
                        onClick={() => !full && togglePick(song.id)}
                        className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition ${
                          on ? "bg-white/15" : "hover:bg-white/5"
                        } ${full ? "opacity-40" : ""}`}
                      >
                        <span
                          className={`grid h-5 w-5 shrink-0 place-items-center rounded border ${
                            on ? "border-[#1ed760] bg-[#1ed760] text-black" : "border-white/40"
                          }`}
                        >
                          {on ? <Check className="h-3.5 w-3.5" /> : null}
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-[14.5px] font-semibold">{song.title}</span>
                          <span className="block truncate text-[13px] text-ink-soft">
                            {song.status === "published" ? "Published" : "Draft"}
                          </span>
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}

            <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
              <p className="text-[13.5px] text-ink-soft">
                {kind === "single" ? "A single is 1 song." : kind === "ep" ? "An EP is up to 6 songs." : "An album is up to 40 songs."}
                {picked.length ? ` ${picked.length} picked.` : ""}
              </p>
              <Button type="submit" disabled={!releaseTitle.trim() || picked.length === 0}>
                Create release
              </Button>
            </div>
          </form>
        </section>
      ) : null}

      <section className="mb-9">
        <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">Releases</h2>
        {releases.loading && !releases.data ? (
          <Block label="Loading releases" />
        ) : (releases.data?.items.length ?? 0) === 0 ? (
          <EmptyState
            title="No releases yet"
            message="Group songs into a single, EP or album so they show as one project."
            action={
              <button
                type="button"
                onClick={() => setReleasing(true)}
                className="rounded-full bg-[#1ed760] px-5 py-2.5 text-[14.5px] font-bold text-black press"
              >
                New release
              </button>
            }
          />
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {releases.data?.items.map((release) => {
              const art = catalogue.find((song) => release.song_ids.includes(song.id));
              const live = release.status === "published";
              return (
                <li key={release.id} className="flex items-center gap-3.5 rounded-lg bg-[#181818] p-3.5">
                  <Cover
                    src={art?.cover_url ?? null}
                    alt={release.title}
                    seed={`rel-${release.id}`}
                    className="h-16 w-16 shrink-0"
                    rounded="rounded"
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[15.5px] font-bold">{release.title}</p>
                    <p className="mt-0.5 truncate text-[13px] text-ink-soft">
                      <span className="uppercase">{release.kind}</span>
                      <span className="mx-1.5 opacity-50">·</span>
                      {release.song_ids.length} songs
                      <span className="mx-1.5 opacity-50">·</span>
                      {live ? formatTimeAgo(release.published_at) : "not published"}
                    </p>
                  </div>
                  {live ? (
                    <Link
                      to={`/release/${release.id}`}
                      className="h-9 shrink-0 rounded-full border border-white/25 px-4 text-[13px] font-bold leading-9 transition hover:border-white"
                    >
                      View
                    </Link>
                  ) : (
                    <button
                      type="button"
                      onClick={() => publishRelease(release)}
                      className="h-9 shrink-0 rounded-full bg-[#1ed760] px-4 text-[13px] font-bold text-black press"
                    >
                      Publish
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section>
        <h2 className="mb-3 text-[22px] font-bold tracking-[-0.02em]">Post an update</h2>
        <form onSubmit={submitPost} className="rounded-lg bg-[#181818] p-5">
          <TextArea
            label="Message"
            value={post}
            onChange={(event) => setPost(event.target.value)}
            placeholder="New song, show date, thank you"
            rows={2}
            maxLength={3000}
            hint="Shows on your artist page"
          />
          <div className="mt-4 flex justify-end">
            <Button type="submit" disabled={posting || !post.trim()} className="gap-2">
              <Send className="h-4 w-4" />
              {posting ? "Posting" : "Post"}
            </Button>
          </div>
        </form>

        {(posts.data?.items.length ?? 0) > 0 ? (
          <ul className="mt-4 space-y-3">
            {posts.data?.items.map((item) => (
              <li key={item.id} className="rounded-lg bg-[#181818] p-5">
                <p className="whitespace-pre-wrap text-[15.5px] leading-relaxed">{item.body}</p>
                <p className="mt-3 flex items-center gap-2 text-[13px] text-ink-faint">
                  {formatTimeAgo(item.created_at)}
                  {item.is_hidden ? (
                    <span className="rounded bg-white/15 px-2 py-px font-bold uppercase">hidden</span>
                  ) : null}
                </p>
              </li>
            ))}
          </ul>
        ) : null}
      </section>

      {editing ? (
        <AudioEditor
          file={editing}
          onClose={() => setEditing(null)}
          onApply={(next) => {
            takeFile(next);
            setEditing(null);
            toasts.saved("Clip ready", "Trimmed and saved to this draft");
          }}
        />
      ) : null}

      {recording ? (
        <RecorderSheet
          onClose={() => setRecording(false)}
          onRecorded={(blob, name) => {
            const file = new File([blob], `${name}.webm`, { type: blob.type || "audio/webm" });
            setRecording(false);
            setEditing(file);
          }}
        />
      ) : null}
    </div>
  );
}

export function Studio() {
  const { user } = useAuth();
  const [stage, setStage] = useState<"checking" | "opening" | "open">("checking");

  useEffect(() => {
    if (!user) return;
    if (user.role === "listener") {
      setStage("opening");
      return;
    }
    let alive = true;
    request("/artists/me/songs?limit=1")
      .then(() => alive && setStage("open"))
      .catch((error: unknown) => {
        if (!alive) return;
        setStage(error instanceof ApiError && error.status === 403 ? "opening" : "open");
      });
    return () => {
      alive = false;
    };
  }, [user?.id, user?.role]);

  if (!user) return null;

  if (stage === "checking") {
    return (
      <div className="mx-auto w-full max-w-7xl px-4 sm:px-6 lg:px-8">
        <Block label="Opening studio" />
      </div>
    );
  }

  if (stage === "opening") return <Opening onReady={() => setStage("open")} />;

  return <Desk onLeave={() => setStage("checking")} />;
}
