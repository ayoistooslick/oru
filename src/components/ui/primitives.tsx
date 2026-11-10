import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from "react";

type Variant = "primary" | "soft" | "ink" | "ghost" | "outline" | "danger";
type Size = "sm" | "md" | "lg";

const variants: Record<Variant, string> = {
  primary: "bg-flame text-cream hover:bg-flame-ink font-bold shadow-ember",
  soft: "bg-ink/10 text-ink hover:bg-ink/18",
  ink: "bg-ink text-paper font-bold hover:bg-ink/90",
  ghost: "text-ink-soft hover:text-ink hover:bg-ink/10",
  outline: "border border-hair text-ink hover:border-ink-soft hover:bg-ink/8",
  danger: "bg-[#e5484d] text-white hover:bg-[#f2555a] font-bold",
};

const sizes: Record<Size, string> = {
  sm: "h-8 px-3.5 text-[13px] rounded-xl gap-1.5",
  md: "h-10 px-5 text-[14.5px] rounded-xl gap-2",
  lg: "h-12 px-7 text-[15.5px] rounded-xl gap-2",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

export function Button({ variant = "primary", size = "md", className = "", ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      className={`inline-flex items-center justify-center font-semibold transition disabled:opacity-45 disabled:pointer-events-none press ${variants[variant]} ${sizes[size]} ${className}`}
      {...rest}
    />
  );
}

export function Chip({
  active = false,
  children,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { active?: boolean }) {
  return (
    <button
      type="button"
      className={`h-8 shrink-0 rounded-lg px-3.5 text-[13.5px] font-semibold transition press ${
        active ? "bg-flame text-cream" : "bg-ink/8 text-ink-soft hover:bg-ink/14 hover:text-ink"
      }`}
      {...rest}
    >
      {children}
    </button>
  );
}

export function Spinner({ className = "h-5 w-5" }: { className?: string }) {
  return (
    <svg className={`animate-spin ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.22" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

export function SectionHeader({
  title,
  action,
  onAction,
}: {
  title: string;
  action?: string;
  onAction?: () => void;
}) {
  return (
    <div className="mb-3 flex items-baseline justify-between gap-4">
      <h2 className="font-display text-[21px] font-semibold tracking-[-0.01em] text-ink">{title}</h2>
      {action ? (
        <button
          type="button"
          onClick={onAction}
          className="text-[12.5px] font-semibold uppercase tracking-wide text-ink-faint hover:text-flame-ink"
        >
          {action}
        </button>
      ) : null}
    </div>
  );
}

export function EmptyState({
  title,
  message,
  action,
}: {
  title: string;
  message: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-2xl border border-hair bg-card px-6 py-12 text-center">
      <p className="text-[18px] font-bold tracking-[-0.01em]">{title}</p>
      <p className="mx-auto mt-2 max-w-md text-[15px] leading-relaxed text-ink-soft">{message}</p>
      {action ? <div className="mt-5 flex justify-center">{action}</div> : null}
    </div>
  );
}

export function Field({
  label,
  hint,
  error,
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }) {
  return (
    <label className="block">
      <span className="label-caps">{label}</span>
      <input
        className={`mt-2 h-11 w-full rounded-xl border bg-ink/6 px-4 text-[15.5px] text-ink outline-none transition placeholder:text-ink-faint focus:border-flame-ink focus:bg-ink/10 ${
          error ? "border-[#e5484d]" : "border-hair"
        }`}
        {...rest}
      />
      {error ? (
        <span className="mt-1.5 block text-[13px] font-semibold text-[#ff8a80]">{error}</span>
      ) : hint ? (
        <span className="mt-1.5 block text-[13px] text-ink-soft">{hint}</span>
      ) : null}
    </label>
  );
}

export function TextArea({
  label,
  hint,
  ...rest
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; hint?: string }) {
  return (
    <label className="block">
      <span className="label-caps">{label}</span>
      <textarea
        className="mt-2 w-full rounded-xl border border-hair bg-ink/6 px-4 py-3 text-[15.5px] leading-relaxed text-ink outline-none transition placeholder:text-ink-faint focus:border-flame-ink focus:bg-ink/10"
        {...rest}
      />
      {hint ? <span className="mt-1.5 block text-[13px] text-ink-soft">{hint}</span> : null}
    </label>
  );
}
