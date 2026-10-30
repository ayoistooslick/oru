import { useState, useEffect, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { EyeOff, Undo2, XCircle } from "lucide-react";
import { request } from "../lib/api";
import { useResource } from "../hooks/useResource";
import { formatTimeAgo } from "../lib/format";
import type { Paginated, Report } from "../lib/types";
import { Page } from "../components/Page";
import { Button, Chip, EmptyState, Spinner } from "../components/ui";
import { useAuth } from "../state/AuthContext";
import { setAppBadge } from "../lib/pwa";
import { useToasts } from "../state/ToastContext";

type Status = "open" | "reviewed" | "actioned";

const TABS: { value: Status; label: string }[] = [
  { value: "open", label: "New" },
  { value: "reviewed", label: "Closed" },
  { value: "actioned", label: "Taken down" },
];

const ACTIONS = [
  {
    value: "hide",
    label: "Take it down",
    icon: EyeOff,
    note: "Nobody can see it anymore. The file and account are kept.",
  },
  { value: "restore", label: "Put it back", icon: Undo2, note: "People can see it again." },
  { value: "dismiss", label: "Nothing wrong", icon: XCircle, note: "Close the report with no punishment." },
] as const;

const TARGET_LABEL: Record<Report["target_type"], string> = {
  song: "Song",
  artist: "Artist",
  post: "Post",
};

function Decision({ report, onDone }: { report: Report; onDone: () => void }) {
  const toasts = useToasts();
  const [action, setAction] = useState<(typeof ACTIONS)[number]["value"]>("hide");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (reason.trim().length < 5) {
      toasts.error("Write a reason", "Type at least a few words.");
      return;
    }
    setBusy(true);
    try {
      await request(`/moderation/reports/${report.id}/decision`, {
        method: "POST",
        body: { action, reason: reason.trim() },
      });
      toasts.saved("Decision saved", `${TARGET_LABEL[report.target_type]} #${report.target_id}`);
      setReason("");
      onDone();
    } catch (error) {
      toasts.error("Could not save it", error instanceof Error ? error.message : "Try again.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="mt-4 rounded-lg bg-white/5 p-4">
      <fieldset>
        <legend className="label-caps mb-2">What should happen</legend>
        <div className="grid gap-2 sm:grid-cols-3">
          {ACTIONS.map((option) => {
            const Icon = option.icon;
            const active = action === option.value;
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => setAction(option.value)}
                className={`flex items-center gap-2 rounded-lg px-3.5 py-3 text-left text-[14px] font-bold transition ${
                  active ? "bg-white text-black" : "bg-white/10 text-white hover:bg-white/20"
                }`}
              >
                <Icon className={`h-4 w-4 shrink-0 ${active ? "text-black" : "text-ink-soft"}`} />
                {option.label}
              </button>
            );
          })}
        </div>
        <p className="mt-2.5 text-[13.5px] leading-relaxed text-ink-soft">
          {ACTIONS.find((option) => option.value === action)?.note}
        </p>
      </fieldset>

      <label className="mt-4 block">
        <span className="label-caps">Why</span>
        <textarea
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          rows={2}
          maxLength={1000}
          placeholder="Write a short reason"
          className="mt-2 w-full rounded-lg border border-transparent bg-white/10 px-4 py-3 text-[15px] leading-relaxed text-white outline-none transition placeholder:text-ink-faint focus:border-white"
        />
      </label>

      <div className="mt-3 flex justify-end">
        <Button type="submit" disabled={busy || reason.trim().length < 5}>
          {busy ? "Saving" : "Save decision"}
        </Button>
      </div>
    </form>
  );
}

export function Moderation() {
  const { isAdmin } = useAuth();
  const [tab, setTab] = useState<Status>("open");
  const [expanded, setExpanded] = useState<number | null>(null);

  const reports = useResource<Paginated<Report>>(
    (signal) => request(`/moderation/reports?status=${tab}`, { signal }),
    [tab],
  );

  // Badge on the app icon: how many reports are still waiting.
  useEffect(() => {
    if (!isAdmin || tab !== "open") return;
    setAppBadge((reports.data?.items ?? []).length || undefined);
  }, [isAdmin, tab, reports.data]);

  if (!isAdmin) {
    return (
      <Page title="Reports">
        <EmptyState
          title="Only moderators can open this"
          message="You can still report anything you see from a song or an artist page."
          action={
            <Link to="/listen" className="rounded-full bg-white px-5 py-2.5 text-[14.5px] font-bold text-black press">
              Go to Home
            </Link>
          }
        />
      </Page>
    );
  }

  const items = reports.data?.items ?? [];

  return (
    <Page title="Reports" subtitle="Things listeners asked a moderator to look at.">
      <div className="mb-5 flex flex-wrap gap-2">
        {TABS.map((item) => (
          <Chip key={item.value} active={tab === item.value} onClick={() => setTab(item.value)}>
            {item.label}
          </Chip>
        ))}
        {reports.loading ? <Spinner className="h-4 w-4 text-ink-faint" /> : null}
      </div>

      {reports.loading && !reports.data ? (
        <div className="flex items-center justify-center gap-3 py-16 text-ink-soft">
          <Spinner />
          <span className="text-[15px] font-semibold">Loading reports</span>
        </div>
      ) : reports.error ? (
        <EmptyState title="Could not load reports" message={reports.error} />
      ) : items.length === 0 ? (
        <EmptyState
          title={tab === "open" ? "Nothing new" : "Nothing here"}
          message={tab === "open" ? "All reports have been handled." : "Try another tab."}
        />
      ) : (
        <ul className="space-y-3">
          {items.map((report) => (
            <li key={report.id} className="rounded-lg bg-[#181818] p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="label-caps">
                    {TARGET_LABEL[report.target_type]} #{report.target_id}
                  </p>
                  <p className="mt-1.5 text-[17px] font-bold capitalize">{report.reason.replace("_", " ")}</p>
                </div>
                <span className="shrink-0 rounded-full bg-white/10 px-3 py-1 text-[12px] font-bold uppercase">
                  {report.status}
                </span>
              </div>

              <p className="mt-3 max-w-3xl whitespace-pre-wrap text-[15px] leading-relaxed text-ink-soft">
                {report.details || "No extra detail given."}
              </p>

              <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                <p className="text-[13px] text-ink-faint">
                  Reported by listener #{report.reporter_id} · {formatTimeAgo(report.created_at)}
                </p>
                {report.status === "open" ? (
                  <button
                    type="button"
                    onClick={() => setExpanded(expanded === report.id ? null : report.id)}
                    className="h-9 rounded-full bg-white px-4 text-[13.5px] font-bold text-black press"
                  >
                    {expanded === report.id ? "Close" : "Review"}
                  </button>
                ) : null}
              </div>

              {expanded === report.id ? (
                <Decision
                  report={report}
                  onDone={() => {
                    setExpanded(null);
                    reports.reload();
                  }}
                />
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </Page>
  );
}
