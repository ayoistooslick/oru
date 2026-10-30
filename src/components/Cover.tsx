import { useState } from "react";
import { mediaUrl } from "../lib/api";

const PALETTE = ["#1ed760", "#509bf5", "#af2896", "#f59b23", "#e8115b", "#0d73ec", "#8d67ab", "#148a08"];

function hash(value: string): number {
  let total = 0;
  for (let index = 0; index < value.length; index += 1) total = (total * 31 + value.charCodeAt(index)) % 100000;
  return total;
}

interface CoverProps {
  src?: string | null;
  alt: string;
  seed: string | number;
  className?: string;
  rounded?: string;
}

/**
 * Artwork with a designed fallback: plenty of tracks arrive without a cover, so
 * the empty state is a record-style plate instead of a grey box.
 */
export function Cover({ src, alt, seed, className = "h-14 w-14", rounded = "rounded-lg" }: CoverProps) {
  const [failed, setFailed] = useState(false);
  const resolved = failed ? undefined : mediaUrl(src);
  const color = PALETTE[hash(String(seed)) % PALETTE.length];

  if (resolved) {
    return (
      <img
        src={resolved}
        alt={alt}
        loading="lazy"
        className={`${className} ${rounded} object-cover bg-paper-deep`}
        onError={() => setFailed(true)}
      />
    );
  }

  return (
    <div
      className={`${className} ${rounded} relative grid place-items-center overflow-hidden`}
      style={{ backgroundColor: color }}
      role="img"
      aria-label={alt}
    >
      <svg viewBox="0 0 100 100" className="absolute inset-0 h-full w-full opacity-40" aria-hidden="true">
        <circle cx="50" cy="50" r="34" fill="none" stroke="rgba(255,255,255,0.35)" strokeWidth="1.5" />
        <circle cx="50" cy="50" r="24" fill="none" stroke="rgba(255,255,255,0.3)" strokeWidth="1.5" />
        <circle cx="50" cy="50" r="14" fill="none" stroke="rgba(255,255,255,0.25)" strokeWidth="1.5" />
      </svg>
      <span className="relative text-[15px] font-extrabold uppercase tracking-[-0.02em] text-white/90">
        {alt.trim().slice(0, 1) || "O"}
      </span>
    </div>
  );
}
