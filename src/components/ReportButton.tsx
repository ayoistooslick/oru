import { useEffect, useState, type FormEvent } from "react";
import { Flag, X } from "lucide-react";
import { request } from "../lib/api";
import { useToasts } from "../state/ToastContext";

type TargetType = "song" | "artist" | "post";

const REASONS: { value: string; label: string; hint: string }[] = [
  { value: "copyright", label: "Copyright", hint: "This uses someone else's work without permission" },
  { value: "harassment", label: "Harassment", hint: "Targeting a person or a group" },
  { value: "spam", label: "Spam", hint: "Repeated, unwanted or bought engagement" },
  { value: "impersonation", label: "Impersonation", hint: "Pretending to be another artist" },
  { value: "other", label: "Something else", hint: "Describe it below and a moderator will read it" },
];

interface ReportButtonProps {
  targetType: TargetType;
  targetId: number;
  targetLabel: string;
  compact?: boolean;
}

/**
 * Reporting is deliberately quiet: one small link that opens a short form, so
 * nobody feels they are pressing a big red button.
 */
export function ReportButton({ targetType, targetId, targetLabel, compact = false }: ReportButtonProps) {
  const toasts = useToasts();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("copyright");
  const [details, setDetails] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      await request("/reports", {
        method: "POST",
        body: { target_type: targetType, target_id: targetId, reason, details: details.trim() },
      });
      toasts.info("Report sent", "A moderator will read it. Thanks for looking out.");
      setOpen(false);
      setDetails("");
    } catch (error) {
      toasts.error("Could not send the report", error instanceof Error ? error.message : "Try again.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={
          compact
            ? "grid h-9 w-9 place-items-center rounded-full text-ink-soft transition hover:bg-white/10 hover:text-white"
            : "inline-flex h-10 items-center gap-2 rounded-full px-4 text-[13.5px] font-semibold text-ink-soft transition hover:bg-white/10 hover:text-white"
        }
        aria-label={`Report ${targetLabel}`}
      >
        <Flag className="h-4 w-4" />
        {compact ? null : "Report"}
      </button>

      {open ? (
        <div
          className="fixed inset-0 z-[80] grid place-items-center bg-black/70 p-4 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-label={`Report ${targetLabel}`}
          onClick={() => setOpen(false)}
        >
          <form
            onSubmit={submit}
            onClick={(event) => event.stopPropagation()}
            className="w-full max-w-md rounded-xl bg-[#282828] p-6 shadow-float"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-[12px] font-bold uppercase tracking-[0.1em] text-ink-faint">Report</p>
                <h2 className="mt-1 text-[21px] font-bold tracking-[-0.02em]">{targetLabel}</h2>
              </div>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="grid h-9 w-9 place-items-center rounded-full text-ink-soft transition hover:bg-white/10 hover:text-white"
                aria-label="Close"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <fieldset className="mt-4">
              <legend className="label-caps mb-2">What is wrong</legend>
              <div className="space-y-1.5">
                {REASONS.map((item) => (
                  <label
                    key={item.value}
                    className={`flex cursor-pointer items-start gap-3 rounded-lg px-3.5 py-2.5 transition ${
                      reason === item.value ? "bg-white/15" : "hover:bg-white/5"
                    }`}
                  >
                    <input
                      type="radio"
                      name="reason"
                      value={item.value}
                      checked={reason === item.value}
                      onChange={() => setReason(item.value)}
                      className="mt-1 h-4 w-4 accent-[#1ed760]"
                    />
                    <span>
                      <span className="block text-[14.5px] font-semibold">{item.label}</span>
                      <span className="block text-[13px] leading-snug text-ink-soft">{item.hint}</span>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>

            <label className="mt-4 block">
              <span className="label-caps">Anything a moderator should know</span>
              <textarea
                value={details}
                onChange={(event) => setDetails(event.target.value)}
                rows={3}
                maxLength={2000}
                placeholder="Optional, but specifics help."
                className="mt-2 w-full rounded-lg bg-white/10 px-4 py-3 text-[15px] leading-relaxed text-white outline-none transition placeholder:text-ink-faint focus:border-white border border-transparent"
              />
            </label>

            <div className="mt-5 flex justify-end gap-2.5">
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="h-11 rounded-full px-5 text-[14.5px] font-semibold text-ink-soft transition hover:bg-white/10"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={busy}
                className="h-11 rounded-full bg-[#1ed760] px-5 text-[14.5px] font-bold text-black transition disabled:opacity-50"
              >
                {busy ? "Sending…" : "Send report"}
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </>
  );
}
