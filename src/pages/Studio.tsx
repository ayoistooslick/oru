import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Check, ImagePlus, Music4, Send, Trash2, UploadCloud } from "lucide-react";
import { ApiError, auth, request, upload } from "../lib/api";
import { useResource } from "../hooks/useResource";
import { formatTimeAgo } from "../lib/format";
import type { ArtistPost, ArtistStats, Paginated, Release, Song } from "../lib/types";
import { TrackRow } from "../components/TrackRow";
import { Cover } from "../components/Cover";
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

function Stat({ value, label }: { value: number; label: string }) {
  return (
    <div className="rounded-lg bg-[#181818] px-4 py-4">
      <p className="text-[28px] font-extrabold leading-none tracking-[-0.04em]">{value}</p>
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
  const [audioName, setAudioName] = useState("");
  const [coverName, setCoverName] = useState("");
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState(0);

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
      if (audio && audioRef.current) {
        const transfer = new DataTransfer();
        transfer.items.add(audio);
        audioRef.current.files = transfer.files;
        setAudioName(audio.name);
      }
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
    const chosen = audioRef.current?.files?.[0];
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
      setAudioName("");
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

      <section className="mb-8 grid grid-cols-3 gap-3">
        <Stat value={stats.data?.published_songs ?? 0} label="Published" />
        <Stat value={stats.data?.likes ?? 0} label="Likes" />
        <Stat value={stats.data?.qualified_listens ?? 0} label="Plays" />
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
                  onChange={(event) => setAudioName(event.target.files?.[0]?.name ?? "")}
                />
              </label>

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
