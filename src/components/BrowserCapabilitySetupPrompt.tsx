import { useCallback, useEffect, useState } from "react";
import { Button } from "./ui";
import {
  readBrowserCapabilityStatus,
  requestNotificationPermission,
  requestPersistentStorage,
  type BrowserCapabilityStatus,
} from "../lib/browserCapabilities";

interface BrowserCapabilitySetupPromptProps {
  /** Called when the user skips or dismisses this prompt. */
  onDismiss: () => void;
}

type PendingAction = "notifications" | "storage" | null;

function notificationStatusLabel(status: BrowserCapabilityStatus["notifications"] | undefined): string {
  if (!status) return "Checking status";
  if (!status.available) return "Unavailable in this browser";
  if (status.permission === "granted") return "Allowed";
  if (status.permission === "denied") return "Blocked in browser settings";
  return "Not enabled";
}

function storageStatusLabel(status: BrowserCapabilityStatus["storage"] | undefined): string {
  if (!status) return "Checking status";
  if (!status.available) return "Unavailable in this browser";
  if (status.persisted === true) return "Persistent storage is enabled";
  if (status.persisted === false) return "Not currently persistent";
  return "Current status unavailable";
}

export function BrowserCapabilitySetupPrompt({ onDismiss }: BrowserCapabilitySetupPromptProps) {
  const [status, setStatus] = useState<BrowserCapabilityStatus>();
  const [pending, setPending] = useState<PendingAction>(null);
  const [message, setMessage] = useState("");

  const refreshStatus = useCallback(async () => {
    try {
      setStatus(await readBrowserCapabilityStatus());
    } catch {
      setMessage("Could not read browser capability status. You can still try an available action.");
    }
  }, []);

  useEffect(() => {
    let active = true;
    void readBrowserCapabilityStatus()
      .then((nextStatus) => {
        if (active) setStatus(nextStatus);
      })
      .catch(() => {
        if (active) setMessage("Could not read browser capability status. You can still try an available action.");
      });
    return () => {
      active = false;
    };
  }, []);

  // Each browser request starts directly from its corresponding button click.
  const enableNotifications = async () => {
    setPending("notifications");
    setMessage("");
    try {
      const permission = await requestNotificationPermission();
      setMessage(
        permission === "granted"
          ? "Notifications are enabled."
          : permission === "denied"
            ? "Notifications were blocked. You can change this in your browser settings."
            : "Notification permission was not changed.",
      );
      await refreshStatus();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not request notification permission.");
    } finally {
      setPending(null);
    }
  };

  const enablePersistentStorage = async () => {
    setPending("storage");
    setMessage("");
    try {
      const persisted = await requestPersistentStorage();
      setMessage(
        persisted
          ? "The browser granted persistent storage."
          : "The browser did not grant persistent storage; the app will continue to work normally.",
      );
      await refreshStatus();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not request persistent storage.");
    } finally {
      setPending(null);
    }
  };

  const notifications = status?.notifications;
  const storage = status?.storage;
  const notificationsGranted = notifications?.permission === "granted";
  const notificationsBlocked = notifications?.permission === "denied";
  const storagePersisted = storage?.persisted === true;

  return (
    <section
      aria-labelledby="browser-capability-setup-title"
      className="mx-auto w-full max-w-2xl rounded-2xl border border-white/10 bg-[#181818] p-5 shadow-xl sm:p-7"
    >
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-[#1ed760]">Quick setup</p>
          <h2
            id="browser-capability-setup-title"
            className="mt-1 text-[24px] font-extrabold tracking-[-0.025em]"
          >
            Set up ORU on this device
          </h2>
          <p className="mt-2 max-w-xl text-[14.5px] leading-relaxed text-ink-soft">
            Choose whether ORU may send notifications and ask your browser to protect its stored app data from
            automatic cleanup. Both are optional.
          </p>
        </div>
        <button
          type="button"
          onClick={onDismiss}
          className="rounded-full px-3 py-2 text-[13.5px] font-semibold text-ink-soft transition hover:bg-white/10 hover:text-white"
          aria-label="Dismiss setup prompt"
        >
          Skip for now
        </button>
      </div>

      <div className="mt-6 divide-y divide-white/10 rounded-xl bg-white/[0.035]">
        <div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="min-w-0">
            <h3 className="text-[15px] font-bold">Notifications</h3>
            <p className="mt-1 text-[13.5px] text-ink-soft">
              Available: {notifications?.available ? "Yes" : status ? "No" : "Checking"} · Current: {notificationStatusLabel(notifications)}
            </p>
          </div>
          <Button
            size="sm"
            variant="primary"
            onClick={() => void enableNotifications()}
            disabled={!notifications?.available || notificationsGranted || notificationsBlocked || pending !== null}
            className="shrink-0"
          >
            {pending === "notifications"
              ? "Requesting…"
              : notificationsGranted
                ? "Enabled"
                : notificationsBlocked
                  ? "Check browser settings"
                  : "Allow notifications"}
          </Button>
        </div>

        <div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="min-w-0">
            <h3 className="text-[15px] font-bold">Persistent browser storage</h3>
            <p className="mt-1 text-[13.5px] text-ink-soft">
              Available: {storage?.available ? "Yes" : status ? "No" : "Checking"} · Current: {storageStatusLabel(storage)}
            </p>
          </div>
          <Button
            size="sm"
            variant="outline"
            onClick={() => void enablePersistentStorage()}
            disabled={!storage?.available || storagePersisted || pending !== null}
            className="shrink-0"
          >
            {pending === "storage"
              ? "Requesting…"
              : storagePersisted
                ? "Already enabled"
                : "Request persistence"}
          </Button>
        </div>
      </div>

      <p className="mt-4 text-[13px] leading-relaxed text-ink-faint">
        Android music-player role: ORU is web content (a PWA/TWA) and cannot claim or request Android’s default
        music-player role. That operating-system role is not available to this web app.
      </p>
      {message ? (
        <p className="mt-3 text-[13.5px] text-ink-soft" role="status" aria-live="polite">
          {message}
        </p>
      ) : null}
      <div className="mt-5 flex justify-end">
        <Button variant="ghost" size="sm" onClick={onDismiss}>
          Done
        </Button>
      </div>
    </section>
  );
}
