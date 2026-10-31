import { useEffect, useRef, useState } from "react";
import { Mic, Square, X } from "lucide-react";

function clock(seconds: number): string {
  const whole = Math.floor(seconds);
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, "0")}`;
}

/**
 * Record straight into the studio with the device microphone. The result goes
 * to the same editor as an uploaded file, so trimming and fades work the same
 * way whichever door the sound came through.
 */
export function RecorderSheet({
  onRecorded,
  onClose,
}: {
  onRecorded: (blob: Blob, name: string) => void;
  onClose: () => void;
}) {
  const [state, setState] = useState<"idle" | "asking" | "recording" | "error">("idle");
  const [seconds, setSeconds] = useState(0);
  const [message, setMessage] = useState("");
  const recorder = useRef<MediaRecorder | null>(null);
  const chunks = useRef<Blob[]>([]);
  const stream = useRef<MediaStream | null>(null);
  const timer = useRef<number | null>(null);

  const stopEverything = () => {
    if (timer.current !== null) window.clearInterval(timer.current);
    timer.current = null;
    stream.current?.getTracks().forEach((track) => track.stop());
    stream.current = null;
  };

  useEffect(() => stopEverything, []);

  const start = async () => {
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
      setState("error");
      setMessage("This browser cannot record sound. Upload a file instead.");
      return;
    }
    setState("asking");
    try {
      const captured = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.current = captured;
      const mime = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
        ? "audio/webm;codecs=opus"
        : MediaRecorder.isTypeSupported("audio/mp4")
          ? "audio/mp4"
          : "";
      const recorderInstance = new MediaRecorder(captured, mime ? { mimeType: mime } : undefined);
      chunks.current = [];
      recorderInstance.ondataavailable = (event) => {
        if (event.data.size > 0) chunks.current.push(event.data);
      };
      recorderInstance.onstop = () => {
        const blob = new Blob(chunks.current, { type: recorderInstance.mimeType || "audio/webm" });
        stopEverything();
        onRecorded(blob, `recording-${Date.now()}`);
      };
      recorder.current = recorderInstance;
      recorderInstance.start();
      setSeconds(0);
      setState("recording");
      timer.current = window.setInterval(() => setSeconds((value) => value + 1), 1000);
    } catch {
      setState("error");
      setMessage("The microphone was blocked. Allow it in your browser, or upload a file instead.");
    }
  };

  const stop = () => recorder.current?.stop();

  return (
    <div
      className="fixed inset-0 z-[95] grid place-items-center bg-black/80 px-5 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label="Record a song"
    >
      <div className="w-full max-w-sm rounded-2xl bg-[#181818] p-6 text-center shadow-2xl">
        <div className="flex items-start justify-between">
          <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-ink-faint">Record</p>
          <button
            type="button"
            onClick={() => {
              if (state === "recording") stop();
              stopEverything();
              onClose();
            }}
            className="grid h-8 w-8 place-items-center rounded-full text-ink-soft transition hover:bg-white/10 hover:text-white"
            aria-label="Close the recorder"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="mx-auto mt-5 grid h-24 w-24 place-items-center rounded-full bg-white/5">
          <span
            className={`grid h-16 w-16 place-items-center rounded-full transition ${
              state === "recording" ? "bg-[#f0463c] text-white" : "bg-[#1ed760] text-black"
            }`}
          >
            <Mic className="h-7 w-7" />
          </span>
        </div>

        <p className="mt-4 text-[26px] font-extrabold tabular-nums tracking-[-0.02em]">{clock(seconds)}</p>
        <p className="mt-1 text-[13.5px] text-ink-soft">
          {state === "recording"
            ? "Recording. Sing, hum or play."
            : state === "asking"
              ? "Waiting for the microphone"
              : "Sing it in one take. You can trim it after."}
        </p>

        {state === "error" ? <p className="mt-3 text-[13px] font-semibold text-[#ff9c92]">{message}</p> : null}

        <div className="mt-5 flex items-center justify-center gap-2">
          {state !== "recording" ? (
            <button
              type="button"
              onClick={() => void start()}
              disabled={state === "asking"}
              className="inline-flex h-11 items-center gap-2 rounded-full bg-[#1ed760] px-5 text-[14.5px] font-bold text-black transition press disabled:opacity-50"
            >
              <Mic className="h-4 w-4" /> Start recording
            </button>
          ) : (
            <button
              type="button"
              onClick={stop}
              className="inline-flex h-11 items-center gap-2 rounded-full bg-white px-5 text-[14.5px] font-bold text-black transition press"
            >
              <Square className="h-4 w-4" fill="currentColor" /> Stop and edit
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
