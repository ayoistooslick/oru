import { Share2 } from "lucide-react";
import { sharePage } from "../lib/pwa";
import { useToasts } from "../state/ToastContext";

interface ShareButtonProps {
  title: string;
  text?: string;
  url?: string;
  className?: string;
}

/** Hands the link to whatever share sheet the phone has, then falls back to copy. */
export function ShareButton({ title, text, url, className }: ShareButtonProps) {
  const toasts = useToasts();

  const share = async () => {
    const outcome = await sharePage({ title, text, url });
    if (outcome === "copied") toasts.saved("Link copied", "Paste it anywhere to share.");
    if (outcome === "failed") toasts.error("Could not share that", "Copy the address from the bar instead.");
  };

  return (
    <button
      type="button"
      onClick={() => void share()}
      aria-label="Share"
      className={
        className ??
        "grid h-11 w-11 place-items-center rounded-full bg-white/10 text-white transition press hover:bg-white/20"
      }
    >
      <Share2 className="h-5 w-5" />
    </button>
  );
}
