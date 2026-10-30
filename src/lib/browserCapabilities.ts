export type NotificationCapabilityPermission = NotificationPermission | "unavailable";

export interface BrowserCapabilityStatus {
  notifications: {
    available: boolean;
    permission: NotificationCapabilityPermission;
  };
  storage: {
    available: boolean;
    persisted: boolean | null;
  };
}

/** Read capability support and current status without prompting the user. */
export async function readBrowserCapabilityStatus(): Promise<BrowserCapabilityStatus> {
  const notificationsAvailable = typeof window !== "undefined" && "Notification" in window;
  const storageManager = typeof navigator !== "undefined" ? navigator.storage : undefined;
  const storageAvailable = Boolean(storageManager && typeof storageManager.persist === "function");

  let persisted: boolean | null = null;
  if (storageManager && typeof storageManager.persisted === "function") {
    try {
      persisted = await storageManager.persisted();
    } catch {
      persisted = null;
    }
  }

  return {
    notifications: {
      available: notificationsAvailable,
      permission: notificationsAvailable ? Notification.permission : "unavailable",
    },
    storage: {
      available: storageAvailable,
      persisted,
    },
  };
}

/** Call only from an explicit user action; this invokes the browser permission prompt. */
export function requestNotificationPermission(): Promise<NotificationPermission> {
  if (typeof window === "undefined" || !("Notification" in window)) {
    return Promise.reject(new Error("Notifications are not available in this browser."));
  }
  return Notification.requestPermission();
}

/** Call only from an explicit user action to ask the browser to persist this site's storage. */
export function requestPersistentStorage(): Promise<boolean> {
  const storageManager = typeof navigator !== "undefined" ? navigator.storage : undefined;
  if (!storageManager || typeof storageManager.persist !== "function") {
    return Promise.reject(new Error("Persistent storage is not available in this browser."));
  }
  return storageManager.persist();
}
