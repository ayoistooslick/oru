import { useCallback, useLayoutEffect, useRef } from "react";
import gsap from "gsap";
import { useToasts, type ToastItem, type ToastKind } from "../state/ToastContext";
import { OruMark } from "./OruMark";
import { motionSafe } from "../lib/motion";

const META: Record<ToastKind, { label: string; tint: string }> = {
  saved: { label: "Saved", tint: "text-[#1ed760]" },
  playing: { label: "Now playing", tint: "text-ink-soft" },
  upload: { label: "Uploaded", tint: "text-[#1ed760]" },
  error: { label: "Did not work", tint: "text-[#ff6b60]" },
  info: { label: "Note", tint: "text-ink-soft" },
};

function ToastCard({ item, onDone }: { item: ToastItem; onDone: () => void }) {
  const ref = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) return;
    const reduce = motionSafe();
    const ctx = gsap.context(() => {
      if (reduce) return;
      gsap.fromTo(
        element,
        { y: 26, opacity: 0, scale: 0.95 },
        { y: 0, opacity: 1, scale: 1, duration: 0.45, ease: "back.out(1.7)" },
      );
    }, element);

    const timer = window.setTimeout(() => {
      if (reduce) {
        onDone();
        return;
      }
      gsap.to(element, { y: 14, opacity: 0, duration: 0.24, ease: "power2.in", onComplete: onDone });
    }, 3800);

    return () => {
      window.clearTimeout(timer);
      ctx.revert();
    };
  }, [item.id, onDone]);

  const meta = META[item.kind];

  return (
    <div
      ref={ref}
      className="pointer-events-auto flex w-full items-start gap-3 rounded-lg bg-[#2e2e2e] p-3.5 pr-5 shadow-float"
      role="status"
      aria-live="polite"
    >
      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-md bg-black">
        <OruMark className="h-5 w-5" />
      </span>
      <span className="min-w-0 pt-0.5">
        <span className={`block text-[11px] font-bold uppercase tracking-[0.08em] ${meta.tint}`}>{meta.label}</span>
        <span className="mt-0.5 block truncate text-[15px] font-bold">{item.title}</span>
        {item.message ? (
          <span className="mt-0.5 block text-[13.5px] leading-snug text-ink-soft">{item.message}</span>
        ) : null}
      </span>
    </div>
  );
}

export function ToastViewport() {
  const { items, dismiss } = useToasts();
  const handleDone = useCallback((id: number) => dismiss(id), [dismiss]);

  return (
    <div className="pointer-events-none fixed inset-x-4 bottom-[calc(env(safe-area-inset-bottom,0px)+6.5rem)] z-[70] flex flex-col items-center gap-2.5 md:inset-x-auto md:right-6 md:bottom-[7.5rem] md:items-end">
      {items.map((item) => (
        <div key={item.id} className="w-full max-w-sm">
          <ToastCard item={item} onDone={() => handleDone(item.id)} />
        </div>
      ))}
    </div>
  );
}
