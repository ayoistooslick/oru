import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Page } from "../components/layout/Page";
import { Button, Spinner, TextArea } from "../components/ui/primitives";
import { request } from "../lib/api";
import { useAuth } from "../state/AuthContext";
import { useToasts } from "../state/ToastContext";

/**
 * The beta line.
 *
 * Two builds ship from this project: the release everyone installs, and a beta
 * that carries work which is not finished. The beta is deliberately not a public
 * download — a listener asks, a moderator reads the request, and only an
 * approved account is handed the build. With an app this small, the first people
 * to run half-finished features should be chosen, not whoever clicks fastest.
 */

type BetaStatus = {
  status: "none" | "pending" | "approved" | "declined";
  note: string;
  created_at: string | null;
  reviewed_at: string | null;
  review_note: string;
  download_available: boolean;
};

const STATUS_COPY: Record<BetaStatus["status"], { title: string; body: string }> = {
  none: {
    title: "Not in the beta yet",
    body: "Ask for a place and say how you listen — device, and what you would be willing to try.",
  },
  pending: {
    title: "In the queue",
    body: "A person reads every request. You will see the decision here, on this page.",
  },
  approved: {
    title: "You are in",
    body: "Install the beta next to the release build. It has its own icon and its own updates.",
  },
  declined: {
    title: "Not this time",
    body: "You can ask again with more to go on. Nothing is held against a second request.",
  },
};

export default function Beta() {
  const { user } = useAuth();
  const toasts = useToasts();
  const [state, setState] = useState<BetaStatus | null>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    request<BetaStatus>("/beta/me")
      .then((value) => {
        if (cancelled) return;
        setState(value);
        setNote(value.status === "declined" ? value.note : "");
      })
      .catch(() => {
        if (!cancelled) setState(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [user?.id]);

  const apply = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      const value = await request<BetaStatus & { applied: boolean }>("/beta/apply", {
        method: "POST",
        body: { note },
      });
      setState(value);
      toasts.saved(
        value.applied ? "Request sent" : "Already in the beta",
        value.applied
          ? "A moderator will read it. The decision shows up here."
          : "You already have a place.",
      );
    } catch (error) {
      toasts.error(
        "Could not send that",
        error instanceof Error ? error.message : "Try again in a moment.",
      );
    } finally {
      setBusy(false);
    }
  };

  const download = async () => {
    setBusy(true);
    try {
      const { url } = await request<{ url: string }>("/beta/link");
      window.location.href = url;
    } catch (error) {
      toasts.error(
        "The build is not open yet",
        error instanceof Error ? error.message : "Try again in a moment.",
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <Page
      eyebrow="Two lines, one catalogue"
      title="Beta testers"
      subtitle="The release build is the one to judge ORU by. The beta is for the people who want to see what is coming, rough edges included — and to tell us before everyone else finds out."
    >
      <div className="grid gap-5 lg:grid-cols-[1.15fr_0.85fr]">
        <section className="rounded-2xl border border-hair bg-ink/4 p-5 sm:p-6">
          {loading ? (
            <div className="flex items-center gap-3 text-ink-soft">
              <Spinner /> Reading your request…
            </div>
          ) : !state ? (
            <div>
              <h2 className="text-[20px] font-extrabold">The beta could not be read</h2>
              <p className="mt-2 text-[15px] leading-relaxed text-ink-soft">
                Nothing is lost. Reload this page, or ask for a place again below.
              </p>
              <BetaForm note={note} setNote={setNote} busy={busy} onSubmit={apply} />
            </div>
          ) : (
            <div>
              <p className="label-caps">{STATUS_COPY[state.status].title}</p>
              <p className="mt-2 text-[15px] leading-relaxed text-ink-soft">
                {STATUS_COPY[state.status].body}
              </p>

              {state.status === "approved" ? (
                <div className="mt-5 flex flex-wrap items-center gap-3">
                  <Button onClick={download} disabled={busy}>
                    Download the beta build
                  </Button>
                  <span className="text-[13px] text-ink-soft">
                    Installs beside ORU — look for the second icon.
                  </span>
                </div>
              ) : null}

              {state.status === "pending" ? (
                <p className="mt-4 rounded-xl border border-hair bg-ink/6 px-4 py-3 text-[14px] leading-relaxed text-ink-soft">
                  “{state.note || "No note — that is fine."}”
                </p>
              ) : null}

              {state.status === "declined" && state.review_note ? (
                <p className="mt-4 rounded-xl border border-hair bg-ink/6 px-4 py-3 text-[14px] leading-relaxed text-ink-soft">
                  A moderator said: “{state.review_note}”
                </p>
              ) : null}

              {state.status === "none" ||
              state.status === "declined" ||
              state.status === "pending" ? (
                <BetaForm
                  note={note}
                  setNote={setNote}
                  busy={busy}
                  onSubmit={apply}
                  resend={state.status !== "none"}
                />
              ) : null}
            </div>
          )}
        </section>

        <aside className="space-y-4">
          <div className="rounded-2xl border border-hair bg-ink/4 p-5">
            <h2 className="text-[17px] font-extrabold">What the beta means</h2>
            <ul className="mt-3 space-y-2.5 text-[14.5px] leading-relaxed text-ink-soft">
              <li>• Features arrive here first, unfinished and occasionally broken.</li>
              <li>• It installs as its own app, so the release stays untouched.</li>
              <li>• It updates on the beta channel, not the release channel.</li>
              <li>• Every request is read by a person, not a script.</li>
            </ul>
          </div>
          <div className="rounded-2xl border border-hair bg-ink/4 p-5">
            <h2 className="text-[17px] font-extrabold">Rather make music?</h2>
            <p className="mt-2 text-[14.5px] leading-relaxed text-ink-soft">
              The studio is in the release build. Nothing about the beta changes
              how uploads, releases or your artist page work.
            </p>
            <Link
              className="mt-3 inline-flex text-[14px] font-bold text-flame-ink underline-offset-4 hover:underline"
              to="/studio"
            >
              Open the studio
            </Link>
          </div>
        </aside>
      </div>
    </Page>
  );
}

function BetaForm({
  note,
  setNote,
  busy,
  onSubmit,
  resend,
}: {
  note: string;
  setNote: (value: string) => void;
  busy: boolean;
  onSubmit: (event: FormEvent) => void;
  resend?: boolean;
}) {
  return (
    <form className="mt-5 space-y-4" onSubmit={onSubmit}>
      <TextArea
        label={resend ? "Say a little more" : "Why the beta?"}
        hint="Phone model, what you listen on, what you would test. A sentence is enough."
        rows={4}
        maxLength={1000}
        value={note}
        onChange={(event) => setNote(event.target.value)}
        placeholder="Pixel 6, wired headphones, mostly new Afrobeats."
      />
      <Button type="submit" disabled={busy}>
        {busy ? "Sending…" : resend ? "Ask again" : "Request a place"}
      </Button>
    </form>
  );
}
