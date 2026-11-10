import { Link, useNavigate } from "react-router-dom";
import { useState } from "react";
import { Bell, CheckCheck, ChevronRight } from "../components/ui/icons";
import { Cover } from "../components/media/Cover";
import { Avatar } from "../components/media/Avatar";
import { Page } from "../components/layout/Page";
import { Button, EmptyState, Spinner } from "../components/ui/primitives";
import { request } from "../lib/api";
import { formatTimeAgo } from "../lib/format";
import { useResource } from "../hooks/useResource";
import type { DropNotification, NotificationsPage as Inbox } from "../lib/types";
import { useAuth } from "../state/AuthContext";
import { useNotifications } from "../state/NotificationsContext";
import { useToasts } from "../state/ToastContext";

function kindLabel(note: DropNotification): string {
  if (note.kind === "new_release") {
    const kind = note.release?.kind;
    return kind === "album" ? "New album" : kind === "ep" ? "New EP" : "New release";
  }
  return "New song";
}

function titleOf(note: DropNotification): string {
  return note.song?.title ?? note.release?.title ?? "Something new";
}

/** New drops from the artists someone follows, newest first. */
export function NotificationsPage() {
  const { user } = useAuth();
  const { unread, setUnread: setBadge } = useNotifications();
  const toasts = useToasts();
  const navigate = useNavigate();
  const inbox = useResource((signal) => request<Inbox>("/notifications?limit=50", { signal }), []);
  const [markingAll, setMarkingAll] = useState(false);

  const items = inbox.data?.items ?? [];
  const unreadCount = inbox.data?.unread_count ?? unread;
  const notificationsOn = user?.notify_new_drops !== false;

  const open = async (note: DropNotification) => {
    if (note.read_at === null) {
      // Mark it read in the background; the row should not wait to open.
      request<{ unread_count: number }>(`/notifications/${note.id}/read`, { method: "POST" })
        .then((response) => setBadge(response.unread_count))
        .catch(() => {});
    }
    if (note.href) navigate(note.href);
  };

  const markAll = async () => {
    setMarkingAll(true);
    try {
      await request("/notifications/read-all", { method: "POST" });
      setBadge(0);
      inbox.reload();
      toasts.saved("All caught up", "Every drop is marked read.");
    } catch (error) {
      toasts.error("Could not mark them read", error instanceof Error ? error.message : "Try again.");
    } finally {
      setMarkingAll(false);
    }
  };

  return (
    <Page
      title="Notifications"
      subtitle="Songs and releases from the artists you follow."
      actions={
        unreadCount > 0 ? (
          <Button variant="outline" size="sm" onClick={() => void markAll()} disabled={markingAll} className="gap-2">
            <CheckCheck className="h-4 w-4" /> {markingAll ? "Marking" : "Mark all read"}
          </Button>
        ) : null
      }
    >
      {!notificationsOn ? (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-hair bg-flame-soft px-4 py-3">
          <p className="text-[14px] font-semibold text-ink">
            New-drop notices are off, so nothing new will arrive here.
          </p>
          <Link to="/settings#notifications" className="text-[14px] font-bold text-flame-ink underline underline-offset-4">
            Turn them back on
          </Link>
        </div>
      ) : null}

      {inbox.loading && !inbox.data ? (
        <div className="flex items-center justify-center gap-3 py-16 text-ink-soft">
          <Spinner />
          <span className="text-[15px] font-semibold">Loading your notices</span>
        </div>
      ) : inbox.error ? (
        <EmptyState
          title="Could not load notifications"
          message={inbox.error}
          action={<Button onClick={inbox.reload}>Try again</Button>}
        />
      ) : items.length === 0 ? (
        <EmptyState
          title="Quiet in here"
          message="Follow the artists you like and their new songs and releases will land here the moment they drop."
          action={
            <Link
              to="/search"
              className="h-11 rounded-full bg-flame px-6 text-[15px] font-semibold leading-[44px] text-night shadow-ember press hover:scale-105"
            >
              Find artists
            </Link>
          }
        />
      ) : (
        <ul className="overflow-hidden rounded-xl border border-hair bg-card">
          {items.map((note, position) => {
            const unreadRow = note.read_at === null;
            const art = note.song?.cover_url ?? note.release?.cover_url ?? null;
            const seed = note.song?.id ?? note.release?.id ?? note.id;
            return (
              <li key={note.id} className={position > 0 ? "border-t border-hair" : ""}>
                <button
                  type="button"
                  onClick={() => void open(note)}
                  disabled={!note.href}
                  className={`flex w-full items-center gap-3.5 px-4 py-3.5 text-left transition ${
                    note.href ? "hover:bg-card-hi" : "cursor-default"
                  } ${unreadRow ? "bg-flame-soft/40" : ""}`}
                >
                  <span className="relative shrink-0">
                    <Cover src={art} alt={titleOf(note)} seed={seed} className="h-14 w-14" rounded="rounded-lg" />
                    {note.artist ? (
                      <span className="absolute -bottom-1 -right-1 rounded-full ring-2 ring-card">
                        <Avatar name={note.artist.name} src={note.artist.avatar_url} size="h-6 w-6" />
                      </span>
                    ) : null}
                  </span>

                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.14em] text-ink-faint">
                      <Bell className={`h-3 w-3 ${unreadRow ? "text-flame-ink" : ""}`} />
                      {kindLabel(note)}
                      <span className="font-semibold normal-case tracking-normal text-ink-faint">
                        · {formatTimeAgo(note.created_at)}
                      </span>
                    </span>
                    <span className="mt-1 block truncate text-[15.5px] font-bold">{titleOf(note)}</span>
                    <span className="mt-0.5 block truncate text-[13.5px] text-ink-soft">
                      {note.artist?.name ?? "An artist"}
                      {note.href ? "" : " · no longer available"}
                    </span>
                  </span>

                  {unreadRow ? <span className="h-2 w-2 shrink-0 rounded-full bg-flame" aria-label="Unread" /> : null}
                  {note.href ? <ChevronRight className="h-4 w-4 shrink-0 text-ink-faint" /> : null}
                </button>
              </li>
            );
          })}
        </ul>
      )}

      <p className="mt-4 text-[13px] text-ink-faint">
        Only artists you follow can send these.{" "}
        <Link to="/settings#notifications" className="font-semibold text-ink-soft underline underline-offset-4 hover:text-ink">
          Change this setting
        </Link>
        .
      </p>
    </Page>
  );
}
