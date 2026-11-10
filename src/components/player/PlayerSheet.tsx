import type { ReactNode } from "react";
import { X } from "../ui/icons";

/**
 * The sheet the player's extra actions open into.
 *
 * Bottom sheet on phones, small card in the middle on anything wider, which is
 * how the Android player presents its queue and timer too.
 */
export function PlayerSheet({
  title,
  subtitle,
  onClose,
  children,
}: {
  title: string;
  subtitle?: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <div className="fixed inset-0 z-[95] flex items-end justify-center sm:items-center" role="dialog" aria-modal="true" aria-label={title}>
      <button type="button" onClick={onClose} className="absolute inset-0 bg-night/45" aria-label="Close" />
      <div className="relative w-full max-w-md rounded-t-3xl border border-hair bg-paper p-5 shadow-float sm:rounded-3xl">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-[17px] font-extrabold tracking-[-0.01em]">{title}</p>
            {subtitle ? <p className="mt-0.5 text-[13px] text-ink-soft">{subtitle}</p> : null}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="-mr-1 -mt-1 grid h-9 w-9 shrink-0 place-items-center rounded-full text-ink-soft transition hover:bg-ink/10 hover:text-ink"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="mt-3 max-h-[60vh] overflow-y-auto overscroll-contain">{children}</div>
      </div>
    </div>
  );
}
