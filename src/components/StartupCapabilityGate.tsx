import { useEffect, useRef, useState } from "react";
import { BrowserCapabilitySetupPrompt } from "./BrowserCapabilitySetupPrompt";

const DISMISSED_KEY = "oru.device-setup-dismissed";

/**
 * Shows an in-app first-launch checklist. Browser permission requests must be
 * initiated by a user gesture, so this introduces the options rather than
 * opening system dialogs automatically.
 */
export function StartupCapabilityGate() {
  const dialogRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(() => {
    try {
      return localStorage.getItem(DISMISSED_KEY) !== "1";
    } catch {
      return false;
    }
  });

  const dismiss = () => {
    try {
      localStorage.setItem(DISMISSED_KEY, "1");
    } catch {
      // The prompt can still be dismissed for the current session.
    }
    setOpen(false);
  };

  useEffect(() => {
    if (open) dialogRef.current?.querySelector<HTMLElement>("button:not([disabled])")?.focus();
  }, [open]);

  if (!open) return null;
  return (
    <div
      ref={dialogRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby="browser-capability-setup-title"
      tabIndex={-1}
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          dismiss();
          return;
        }
        if (event.key === "Tab" && dialogRef.current) {
          const focusable = Array.from(
            dialogRef.current.querySelectorAll<HTMLElement>(
              'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
            ),
          );
          const first = focusable[0];
          const last = focusable[focusable.length - 1];
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last?.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first?.focus();
          }
        }
      }}
      className="fixed inset-0 z-[100] overflow-y-auto bg-black/80 p-4 backdrop-blur-sm sm:grid sm:place-items-center"
    >
      <div className="my-6 w-full sm:my-0">
        <BrowserCapabilitySetupPrompt onDismiss={dismiss} />
      </div>
    </div>
  );
}
