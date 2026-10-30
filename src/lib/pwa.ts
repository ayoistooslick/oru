/* Everything the browser needs to behave like an installed app.

   Nothing here throws: every capability is optional, and the ones a browser
   does not implement are simply skipped. */

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

interface LaunchParams {
  files?: Array<Promise<File>>;
}

declare global {
  interface Window {
    launchQueue?: { setConsumer: (callback: (params: LaunchParams) => void) => void };
    deferredPrompt?: BeforeInstallPromptEvent | null;
  }
}

export const LAUNCH_FILES_EVENT = "oru:launch-files";

let pendingFiles: File[] = [];

/** Files handed over by an Android file handler or the system share sheet. */
export function takeLaunchFiles(): File[] {
  const files = pendingFiles;
  pendingFiles = [];
  return files;
}

export function registerServiceWorker(): void {
  if (!("serviceWorker" in navigator)) return;
  const register = () => {
    navigator.serviceWorker
      .register("/sw.js", { scope: "/", updateViaCache: "none" })
      .catch(() => undefined);
  };
  if (document.readyState === "complete") register();
  else window.addEventListener("load", register, { once: true });
}

/** Lets other apps hand ORU a web+oru: link (the manifest declares the same url). */
export function registerWebProtocol(): void {
  try {
    const register = (navigator as Navigator & { registerProtocolHandler?: (s: string, u: string, t?: string) => void })
      .registerProtocolHandler;
    if (typeof register !== "function") return;
    const result = register.call(
      navigator,
      "web+oru",
      `${window.location.origin}/search?q=%s`,
      "ORU",
    ) as unknown as Promise<void> | undefined;
    if (result && typeof result.catch === "function") result.catch(() => undefined);
  } catch {
    // The browser may require a click first, or refuse outright.
  }
}

/** File handlers and share_target payloads arrive through the launch queue. */
export function watchLaunchFiles(): void {
  if (typeof window === "undefined" || !window.launchQueue) return;
  window.launchQueue.setConsumer((params: LaunchParams) => {
    const promised = params.files ?? [];
    if (!promised.length) return;
    void Promise.all(promised)
      .then((files) => {
        if (!files.length) return;
        pendingFiles = files;
        window.dispatchEvent(new CustomEvent<File[]>(LAUNCH_FILES_EVENT, { detail: files }));
      })
      .catch(() => undefined);
  });
}

/** Ask the OS to install the app when the browser offers it. */
export function captureInstallPrompt(): void {
  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    window.deferredPrompt = event as BeforeInstallPromptEvent;
    window.dispatchEvent(new Event("oru:installable"));
  });
  window.addEventListener("appinstalled", () => {
    window.deferredPrompt = null;
    window.dispatchEvent(new Event("oru:installable"));
  });
}

export async function runInstallPrompt(): Promise<boolean> {
  const event = window.deferredPrompt;
  if (!event) return false;
  window.deferredPrompt = null;
  try {
    await event.prompt();
    const choice = await event.userChoice;
    return choice.outcome === "accepted";
  } catch {
    return false;
  }
}

export type ShareOutcome = "shared" | "copied" | "failed";

/** Web Share where it exists, clipboard everywhere else. */
export async function sharePage(input: { title: string; text?: string; url?: string }): Promise<ShareOutcome> {
  const url = input.url ?? window.location.href;
  if (typeof navigator.share === "function") {
    try {
      await navigator.share({ title: input.title, text: input.text, url });
      return "shared";
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return "shared";
      // Anything else: fall through and copy the link instead.
    }
  }
  try {
    await navigator.clipboard.writeText(url);
    return "copied";
  } catch {
    return "failed";
  }
}

/** System notification, shown through the service worker where that is required. */
export async function notify(title: string, body: string): Promise<boolean> {
  if (!("Notification" in window)) return false;
  try {
    let permission = Notification.permission;
    if (permission === "default") permission = await Notification.requestPermission();
    if (permission !== "granted") return false;
    const options: NotificationOptions = {
      body,
      icon: "/icons/icon-192.png",
      badge: "/icons/favicon-32.png",
      tag: "oru",
    };
    const registration = "serviceWorker" in navigator ? await navigator.serviceWorker.getRegistration() : undefined;
    if (registration && registration.showNotification) {
      await registration.showNotification(title, options);
    } else {
      new Notification(title, options);
    }
    return true;
  } catch {
    return false;
  }
}

/** Number on the app icon: open work waiting, or nothing. */
export function setAppBadge(count?: number): void {
  try {
    if (count && count > 0) navigator.setAppBadge?.(count);
    else navigator.clearAppBadge?.();
  } catch {
    // Badging is not supported everywhere.
  }
}

export function clearAppBadge(): void {
  try {
    navigator.clearAppBadge?.();
  } catch {
    // Ignore.
  }
}

export function isStandalone(): boolean {
  if (window.matchMedia("(display-mode: standalone)").matches) return true;
  return (window.navigator as Navigator & { standalone?: boolean }).standalone === true;
}
