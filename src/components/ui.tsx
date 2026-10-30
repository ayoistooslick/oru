import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from "react";

type Variant = "primary" | "soft" | "ink" | "ghost" | "outline" | "danger";
type Size = "sm" | "md" | "lg";

const variants: Record<Variant, string> = {
  primary: "bg-[#1ed760] text-black hover:bg-[#3be477] font-bold",
  soft: "bg-white/12 text-white hover:bg-white/20",
  ink: "bg-white text-black hover:bg-white/85 font-bold",
  ghost: "text-ink-soft hover:text-ink hover:bg-white/10",
  outline: "border border-white/25 text-white hover:border-white hover:bg-white/10",
  danger: "bg-[#f0463c] text-white hover:bg-[#ff5c52] font-bold",
};

const sizes: Record<Size, string> = {
  sm: "h-9 px-3.5 text-[13.5px] rounded-full gap-1.5",
  md: "h-11 px-5 text-[15px] rounded-full gap-2",
  lg: "h-12 px-7 text-[16px] rounded-full gap-2",
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
      className={`h-9 shrink-0 rounded-full px-4 text-[14px] font-bold transition press ${
        active ? "bg-white text-black" : "bg-white/10 text-white hover:bg-white/20"
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
      <h2 className="text-[22px] font-bold tracking-[-0.02em]">{title}</h2>
      {action ? (
        <button
          type="button"
          onClick={onAction}
          className="text-[13px] font-bold uppercase tracking-wide text-ink-soft hover:text-ink"
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
    <div className="rounded-2xl bg-card px-6 py-12 text-center">
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
        className={`mt-2 h-12 w-full rounded-lg border border-transparent bg-white/10 px-4 text-[16px] text-white outline-none transition placeholder:text-ink-faint focus:border-white ${
          error ? "border-[#f0463c]" : ""
        }`}
        {...rest}
      />
      {error ? (
        <span className="mt-1.5 block text-[13px] font-semibold text-[#ff6b60]">{error}</span>
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
        className="mt-2 w-full rounded-lg border border-transparent bg-white/10 px-4 py-3 text-[16px] leading-relaxed text-white outline-none transition placeholder:text-ink-faint focus:border-white"
        {...rest}
      />
      {hint ? <span className="mt-1.5 block text-[13px] text-ink-soft">{hint}</span> : null}
    </label>
  );
}
