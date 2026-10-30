import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Download, Github, LogOut, Mic2, User } from "lucide-react";
import { ApiError, request } from "../lib/api";
import { Page } from "../components/Page";
import { Button, Field, SectionHeader, TextArea } from "../components/ui";
import { useAuth } from "../state/AuthContext";
import { useToasts } from "../state/ToastContext";
import { useInstallPrompt } from "../hooks/useInstallPrompt";

function roleLabel(role: string): string {
  if (role === "admin") return "Moderator";
  if (role === "artist") return "Artist";
  return "Listener";
}

export function SettingsPage() {
  const { user, updateUser, signOut, isArtist, isAdmin } = useAuth();
  const toasts = useToasts();
  const navigate = useNavigate();

  const [displayName, setDisplayName] = useState(user?.display_name ?? "");
  const [bio, setBio] = useState(user?.bio ?? "");
  const [saving, setSaving] = useState(false);
  const [hasStudio, setHasStudio] = useState<boolean | null>(null);
  const { canInstall, installed, install } = useInstallPrompt();

  useEffect(() => {
    if (!user) return;
    if (!isArtist) {
      setHasStudio(false);
      return;
    }
    let alive = true;
    request("/artists/me/songs?limit=1")
      .then(() => alive && setHasStudio(true))
      .catch((error: unknown) => {
        if (!alive) return;
        setHasStudio(!(error instanceof ApiError && error.status === 403));
      });
    return () => {
      alive = false;
    };
  }, [user?.id, isArtist]);

  const save = async (event: FormEvent) => {
    event.preventDefault();
    if (!displayName.trim()) return;
    setSaving(true);
    try {
      const response = await request<{ user: NonNullable<typeof user> }>("/listeners/me", {
        method: "PATCH",
        body: { display_name: displayName.trim(), bio: bio.trim() },
      });
      updateUser(response.user);
      toasts.saved("Saved", "Your profile is updated");
    } catch (error) {
      toasts.error("Could not save it", error instanceof Error ? error.message : "Try again.");
    } finally {
      setSaving(false);
    }
  };

  const leave = () => {
    signOut();
    navigate("/");
  };

  if (!user) return null;

  const dirty = displayName.trim() !== user.display_name || bio.trim() !== user.bio;

  return (
    <Page
      title="Settings"
      actions={
        <Button variant="outline" onClick={leave} className="gap-2">
          <LogOut className="h-4 w-4" /> Log out
        </Button>
      }
    >
      <section className="mb-8">
        <SectionHeader title="Your profile" />
        <form onSubmit={save} className="rounded-lg bg-[#181818] p-5 sm:p-6">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field
              label="Name"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              maxLength={80}
            />
            <Field label="Email" value={user.email} readOnly disabled />
          </div>
          <div className="mt-4">
            <TextArea
              label="About you"
              value={bio}
              onChange={(event) => setBio(event.target.value)}
              rows={2}
              maxLength={1000}
              placeholder="A line about you"
            />
          </div>
          <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
            <p className="text-[13.5px] text-ink-faint">
              @{user.handle} · {roleLabel(user.role)}
            </p>
            <Button type="submit" disabled={saving || !dirty || !displayName.trim()}>
              {saving ? "Saving" : "Save"}
            </Button>
          </div>
        </form>
      </section>

      <section className="mb-8">
        <SectionHeader title="Music" />
        <Link
          to="/studio"
          className="flex flex-wrap items-center justify-between gap-4 rounded-lg bg-[#181818] p-5 transition hover:bg-[#282828]"
        >
          <span className="min-w-0">
            <span className="flex items-center gap-2 text-[16px] font-bold">
              <Mic2 className="h-4 w-4 text-[#1ed760]" />
              {hasStudio === null ? "Checking" : hasStudio ? "Your studio is open" : "Open a studio"}
            </span>
            <span className="mt-1 block max-w-xl text-[14.5px] text-ink-soft">
              {hasStudio
                ? "Upload songs, publish them and make releases."
                : "Upload your own songs and publish them."}
            </span>
          </span>
          <span className="shrink-0 text-[14px] font-bold text-[#1ed760]">Open</span>
        </Link>

        {isAdmin ? (
          <Link
            to="/moderation"
            className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-lg bg-[#181818] p-5 transition hover:bg-[#282828]"
          >
            <span className="min-w-0">
              <span className="block text-[16px] font-bold">Reports</span>
              <span className="mt-1 block text-[14.5px] text-ink-soft">Songs and profiles people have reported.</span>
            </span>
            <span className="shrink-0 text-[14px] font-bold text-[#1ed760]">Open</span>
          </Link>
        ) : null}
      </section>

      <section>
        <SectionHeader title="App" />
        {canInstall ? (
          <button
            type="button"
            onClick={() => void install()}
            className="flex w-full flex-wrap items-center justify-between gap-4 rounded-lg bg-[#181818] p-5 text-left transition hover:bg-[#282828]"
          >
            <span className="min-w-0">
              <span className="flex items-center gap-2 text-[16px] font-bold">
                <Download className="h-4 w-4 text-[#1ed760]" />
                Install ORU on this device
              </span>
              <span className="mt-1 block max-w-xl text-[14.5px] text-ink-soft">
                It opens like any other app on your phone, with no browser bar.
              </span>
            </span>
            <span className="shrink-0 text-[14px] font-bold text-[#1ed760]">Install</span>
          </button>
        ) : installed ? (
          <p className="rounded-lg bg-[#181818] px-5 py-4 text-[14.5px] text-ink-soft">
            ORU is already installed on this device.
          </p>
        ) : null}

        <Link
          to="/about"
          className="mt-3 flex flex-wrap items-center justify-between gap-4 rounded-lg bg-[#181818] p-5 transition hover:bg-[#282828]"
        >
          <span className="min-w-0">
            <span className="flex items-center gap-2 text-[16px] font-bold">
              <User className="h-4 w-4 text-[#1ed760]" />
              About the developer
            </span>
            <span className="mt-1 block max-w-xl text-[14.5px] text-ink-soft">
              Who built ORU, and where to see the rest of his work.
            </span>
          </span>
          <span className="shrink-0 text-[14px] font-bold text-[#1ed760]">Open</span>
        </Link>
      </section>

      <section>
        <SectionHeader title="About" />
        <div className="rounded-lg bg-[#181818] p-5">
          <p className="max-w-2xl text-[15px] leading-relaxed text-ink-soft">
            ORU is for musicians who are just starting out. One person builds it, and the ranking rules are written
            down in the docs where anyone can read them.
          </p>
          <p className="mt-3 flex items-center gap-3 text-[13px] text-ink-faint">
            Version 1.0
            <a
              href="https://github.com/ayoistooslick"
              target="_blank"
              rel="noreferrer noopener"
              className="inline-flex items-center gap-1.5 font-bold text-ink-soft hover:text-white"
            >
              <Github className="h-3.5 w-3.5" /> ayoistooslick
            </a>
          </p>
        </div>
      </section>
    </Page>
  );
}
