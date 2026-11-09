import { useCallback, useEffect, useState } from "react";
import { captureInstallPrompt, isStandalone, runInstallPrompt } from "../lib/pwa";

/** The browser's own "install this app" prompt, exposed as plain state. */
export function useInstallPrompt() {
  const [installable, setInstallable] = useState(false);
  const [installed, setInstalled] = useState<boolean>(() => isStandalone());

  useEffect(() => {
    captureInstallPrompt();
    const refresh = () => {
      setInstallable(Boolean(window.deferredPrompt));
      setInstalled(isStandalone());
    };
    refresh();
    window.addEventListener("oru:installable", refresh);
    window.addEventListener("appinstalled", refresh);
    window.matchMedia("(display-mode: standalone)").addEventListener("change", refresh);
    return () => {
      window.removeEventListener("oru:installable", refresh);
      window.removeEventListener("appinstalled", refresh);
      window.matchMedia("(display-mode: standalone)").removeEventListener("change", refresh);
    };
  }, []);

  const install = useCallback(async () => {
    const accepted = await runInstallPrompt();
    if (accepted) {
      setInstallable(false);
      setInstalled(true);
    }
    return accepted;
  }, []);

  return { canInstall: installable && !installed, installed, install };
}
