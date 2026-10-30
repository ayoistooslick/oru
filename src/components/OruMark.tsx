interface MarkProps {
  className?: string;
}

/** The ORU record mark: a green disc with a play notch. */
export function OruMark({ className = "h-6 w-6" }: MarkProps) {
  return (
    <svg viewBox="0 0 64 64" className={className} role="img" aria-label="ORU">
      <circle cx="32" cy="32" r="30" fill="var(--color-flame)" />
      <circle cx="32" cy="32" r="30" fill="none" stroke="var(--color-ink)" strokeWidth="4" />
      <path
        d="M27 23 L45 32 L27 41 Z"
        fill="var(--color-paper)"
        stroke="var(--color-paper)"
        strokeWidth="5"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** Mark plus wordmark, used in the sidebar and on the landing page. */
export function OruLogo({ className = "" }: MarkProps) {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      <OruMark className="h-7 w-7" />
      <span className="text-[19px] font-extrabold tracking-[-0.04em]">ORU</span>
    </span>
  );
}
