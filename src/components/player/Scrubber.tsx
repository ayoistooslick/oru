import { useState } from "react";
import { formatDuration } from "../../lib/format";

/**
 * The progress bar, with the times either side of it when asked.
 *
 * While a finger or mouse holds the bar the thumb follows the hand, not the
 * audio clock. Otherwise the next progress update snaps it back mid drag.
 */
export function Scrubber({
  position,
  duration,
  onSeek,
  className = "",
  showTimes = true,
}: {
  position: number;
  duration: number;
  onSeek: (seconds: number) => void;
  className?: string;
  showTimes?: boolean;
}) {
  const [dragging, setDragging] = useState<number | null>(null);
  const shown = dragging ?? position;
  const percent = duration > 0 ? Math.min(100, (shown / duration) * 100) : 0;
  const ready = duration > 0;

  const move = (value: number) => {
    const clamped = Math.max(0, Math.min(value, duration || 0));
    setDragging(clamped);
    onSeek(clamped);
  };

  return (
    <div className={`flex items-center gap-2 ${className}`}>
      {showTimes ? <span className="w-9 text-right text-[11.5px] tabular-nums text-ink-soft">{formatDuration(shown)}</span> : null}
      <input
        type="range"
        min={0}
        max={Math.max(duration, 1)}
        step={0.1}
        value={shown}
        disabled={!ready}
        onChange={(event) => move(Number(event.target.value))}
        onPointerUp={() => setDragging(null)}
        onPointerCancel={() => setDragging(null)}
        onLostPointerCapture={() => setDragging(null)}
        onKeyUp={() => setDragging(null)}
        onBlur={() => setDragging(null)}
        aria-label="Move through the song"
        className="scrub min-h-6 flex-1 py-2 disabled:opacity-60"
        style={{
          background: `linear-gradient(to right, var(--color-flame) 0%, var(--color-flame) ${percent}%, var(--color-hair) ${percent}%, var(--color-hair) 100%)`,
        }}
      />
      {showTimes ? <span className="w-9 text-[11.5px] tabular-nums text-ink-soft">{formatDuration(duration)}</span> : null}
    </div>
  );
}
