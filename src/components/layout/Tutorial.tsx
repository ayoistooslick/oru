import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { useAuth } from "../../state/AuthContext";

interface Step {
  /** CSS selector for the real element this step points at. */
  target: string;
  title: string;
  body: string;
  /** Where the card sits relative to the target. */
  place?: "top" | "bottom";
}

const KEY = "oru.tutorial.v1";

const STEPS: Step[] = [
  {
    target: '[data-tour="tab-home"]',
    title: "This is Home",
    body: "New songs show up here. Tap any song to start listening.",
  },
  {
    target: '[data-tour="tab-search"]',
    title: "Find music",
    body: "Search for a song or an artist by name.",
  },
  {
    target: '[data-tour="tab-library"]',
    title: "Your saved music",
    body: "Songs you like and playlists you make live here.",
  },
  {
    target: '[data-tour="tab-studio"]',
    title: "Put your song up",
    body: "Open the studio to record or upload a song and publish it.",
  },
  {
    target: '[data-tour="player"]',
    title: "The player",
    body: "The song playing stays at the bottom. Tap it to see more controls.",
    place: "top",
  },
];

interface Rect {
  top: number;
  left: number;
  width: number;
  height: number;
}

/** A live walkthrough that points at real parts of the screen. New members see
    it once; it can be skipped at any point. */
export function Tutorial() {
  const { user } = useAuth();
  const location = useLocation();
  const [index, setIndex] = useState(0);
  const [active, setActive] = useState(false);
  const [rect, setRect] = useState<Rect | null>(null);
  const cardRef = useRef<HTMLDivElement>(null);
  const [cardPos, setCardPos] = useState<{ top: number; left: number } | null>(null);

  // Start only for someone who has not finished it before, and only on a page
  // that carries a tagged target so we never point at nothing.
  useEffect(() => {
    if (!user) return;
    let seen = false;
    try {
      seen = localStorage.getItem(KEY) === "done";
    } catch {
      seen = true;
    }
    if (!seen) setActive(true);
  }, [user?.id]);

  const done = useCallback(() => {
    try {
      localStorage.setItem(KEY, "done");
    } catch {
      /* private mode: not being able to save is fine */
    }
    setActive(false);
  }, []);

  const step = STEPS[index];

  // Measure the target for the current step. Re-measure on resize and on route
  // change so the spotlight never drifts off the element.
  const measure = useCallback(() => {
    if (!active) return;
    const current = STEPS[index];
    if (!current) return;
    const element = document.querySelector(current.target);
    if (!element) {
      setRect(null);
      return;
    }
    const box = element.getBoundingClientRect();
    setRect({ top: box.top, left: box.left, width: box.width, height: box.height });
  }, [active, index]);

  useLayoutEffect(() => {
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, [measure, location.pathname]);

  // Keep the card beside the spotlight once we know both boxes.
  useLayoutEffect(() => {
    if (!rect || !cardRef.current || !step) return;
    const card = cardRef.current.getBoundingClientRect();
    const gap = 14;
    const margin = 12;
    const placeBottom = step.place !== "top";

    let top = placeBottom ? rect.top + rect.height + gap : rect.top - card.height - gap;
    let left = rect.left + rect.width / 2 - card.width / 2;
    left = Math.min(Math.max(margin, left), window.innerWidth - card.width - margin);
    top = Math.min(Math.max(margin, top), window.innerHeight - card.height - margin);
    setCardPos({ top, left });
  }, [rect, step]);

  const next = () => {
    if (index >= STEPS.length - 1) done();
    else setIndex((value) => value + 1);
  };

  const steps = useMemo(() => STEPS.length, []);

  if (!active || !step) return null;

  return (
    <div className="fixed inset-0 z-[120]" aria-live="polite">
      {/* A soft page dim with a hole cut around the element being shown. */}
      <div
        className="absolute inset-0 bg-night/70 transition-all duration-200"
        style={
          rect
            ? {
                clipPath: `polygon(evenodd, 0% 0%, 100% 0%, 100% 100%, 0% 100%, 0% 0%, ${rect.left - 8}px ${rect.top - 8}px, ${rect.left - 8}px ${rect.top + rect.height + 8}px, ${rect.left + rect.width + 8}px ${rect.top + rect.height + 8}px, ${rect.left + rect.width + 8}px ${rect.top - 8}px, ${rect.left - 8}px ${rect.top - 8}px)`,
              }
            : undefined
        }
        onClick={done}
      />

      {/* A bright ring on the element itself. */}
      {rect ? (
        <div
          className="pointer-events-none absolute rounded-xl ring-2 ring-flame transition-all duration-200"
          style={{ top: rect.top - 6, left: rect.left - 6, width: rect.width + 12, height: rect.height + 12 }}
        />
      ) : null}

      <div
        ref={cardRef}
        className="absolute w-[min(320px,calc(100vw-24px))] rounded-2xl border border-hair bg-card p-5 shadow-float"
        style={cardPos ? { top: cardPos.top, left: cardPos.left } : { top: "50%", left: "50%", transform: "translate(-50%,-50%)" }}
        role="dialog"
        aria-modal="true"
        aria-label="Welcome tour"
      >
        <p className="label-caps text-flame-ink">
          Step {index + 1} of {steps}
        </p>
        <h2 className="mt-1.5 font-display text-[21px] font-semibold leading-tight text-ink">{step.title}</h2>
        <p className="mt-2 text-[14.5px] leading-relaxed text-ink-soft">{step.body}</p>

        <div className="mt-5 flex items-center justify-between gap-3">
          <button
            type="button"
            onClick={done}
            className="text-[13.5px] font-semibold text-ink-faint transition hover:text-ink"
          >
            Skip
          </button>
          <div className="flex items-center gap-3">
            <div className="flex gap-1.5" aria-hidden="true">
              {STEPS.map((_, dot) => (
                <span
                  key={dot}
                  className={`h-1.5 w-1.5 rounded-full transition ${dot === index ? "bg-flame-ink" : "bg-hair"}`}
                />
              ))}
            </div>
            <button
              type="button"
              onClick={next}
              className="h-9 rounded-full bg-flame px-4 text-[13.5px] font-semibold text-cream shadow-ember press"
            >
              {index >= STEPS.length - 1 ? "Done" : "Next"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
