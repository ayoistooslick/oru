import type { SleepTimer } from "../../state/PlayerContext";
import { useToasts } from "../../state/ToastContext";
import { Check } from "../ui/icons";
import { PlayerSheet } from "./PlayerSheet";

const OPTIONS: { minutes: number | "song" | null; label: string }[] = [
  { minutes: null, label: "Off" },
  { minutes: 5, label: "5 minutes" },
  { minutes: 15, label: "15 minutes" },
  { minutes: 30, label: "30 minutes" },
  { minutes: 45, label: "45 minutes" },
  { minutes: 60, label: "60 minutes" },
  { minutes: "song", label: "End of this song" },
];

/** Stops playback later, so the last song does not play all night. */
export function SleepTimerSheet({
  sleepTimer,
  sleepRemaining,
  setSleepTimer,
  onClose,
}: {
  sleepTimer: SleepTimer | null;
  sleepRemaining: number;
  setSleepTimer: (minutes: number | "song" | null) => void;
  onClose: () => void;
}) {
  const toasts = useToasts();
  const active: number | "song" | null = !sleepTimer ? null : sleepTimer.atSongEnd ? "song" : sleepTimer.minutes;

  const choose = (minutes: number | "song" | null) => {
    setSleepTimer(minutes);
    if (minutes === null) toasts.saved("Sleep timer off", "Nothing will stop on its own.");
    else if (minutes === "song") toasts.saved("Sleep timer set", "Playback stops when this song ends.");
    else toasts.saved("Sleep timer set", `Playback stops in ${minutes} minutes.`);
    onClose();
  };

  const status = !sleepTimer
    ? "Playback runs until you stop it."
    : sleepTimer.atSongEnd
      ? "Playback stops when this song ends."
      : `${Math.max(1, Math.ceil(sleepRemaining / 60))} minutes left.`;

  return (
    <PlayerSheet title="Sleep timer" subtitle={status} onClose={onClose}>
      <ul className="space-y-1">
        {OPTIONS.map((option) => {
          const isActive = option.minutes === active;
          return (
            <li key={String(option.minutes)}>
              <button
                type="button"
                onClick={() => choose(option.minutes)}
                className="flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2.5 text-left text-[14.5px] font-semibold transition hover:bg-ink/6"
              >
                {option.label}
                {isActive ? <Check className="h-4 w-4 text-flame-ink" /> : null}
              </button>
            </li>
          );
        })}
      </ul>
    </PlayerSheet>
  );
}
