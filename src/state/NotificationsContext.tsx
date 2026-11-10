import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { request } from "../lib/api";
import { useAuth } from "./AuthContext";

interface NotificationsValue {
  /** How many drops are waiting behind the bell. */
  unread: number;
  /** Re-read the count, after marking something read or on returning to the tab. */
  refresh: () => void;
  /** The page already knows the new count; skip the round trip. */
  setUnread: (count: number) => void;
}

const NotificationsContext = createContext<NotificationsValue | null>(null);

/**
 * One poll for the whole app, not one per bell: the header, the page and the
 * settings switch all read the same number.
 */
export function NotificationsProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [unread, setUnread] = useState(0);
  const userId = user?.id ?? null;

  const refresh = useCallback(() => {
    if (userId === null) {
      setUnread(0);
      return;
    }
    request<{ unread: number }>("/notifications/unread")
      .then((response) => setUnread(response.unread))
      .catch(() => {
        // A quiet bell is better than an error banner over the whole app.
      });
  }, [userId]);

  useEffect(() => {
    refresh();
    if (userId === null) return;
    const timer = window.setInterval(refresh, 60_000);
    // A phone that comes back from the background should not wait a minute.
    const onVisible = () => {
      if (document.visibilityState === "visible") refresh();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [refresh, userId]);

  const value = useMemo(() => ({ unread, refresh, setUnread }), [unread, refresh]);
  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>;
}

export function useNotifications(): NotificationsValue {
  const value = useContext(NotificationsContext);
  if (!value) throw new Error("useNotifications must be used inside NotificationsProvider");
  return value;
}
