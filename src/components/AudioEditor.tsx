import { useEffect, useMemo, useRef, useState } from "react";
import { Pause, Play, Scissors, X } from "lucide-react";
import { decodeAudio, defaultEdit, renderEdit, toFile, waveformPeaks, type AudioEdit } from "../lib/audio";
import { formatDuration } from "../lib/format";

/**
 * The one screen between a recording and a published track. It is deliberately
 * small: a picture of the sound, two cut points, two fades and a level fix.
 */
export function AudioEditor({
  file,
  onApply,
  onClose,
}: {
  file: File;
  onApply: (file: File) => void;
  onClose: () => void;
}) {
  const [buffer, setBuffer] = useState<AudioBuffer | null>(null);
  const [edit, setEdit] = useState<AudioEdit | null>(null);
  const [error, setError] = useState("");
  const [previewing, setPreviewing] = useState(false);
  const [saving, setSaving] = useState(false);
  const canvas = useRef<HTMLCanvasElement>(null);
  const audio = useRef<HTMLAudioElement>(null);
  const url = useMemo(() => URL.createObjectURL(file), [file]);

  useEffect(() => () => URL.revokeObjectURL(url), [url]);

  useEffect(() => {
    let alive = true;
    decodeAudio(file)
      .then((decoded) => {
        if (!alive) return;
        setBuffer(decoded);
        setEdit(defaultEdit(decoded));
      })
      .catch(() => alive && setError("This file could not be read as audio. Try another recording or file."));
    return () => {
      alive = false;
    };
  }, [file]);

  const peaks = useMemo(() => (buffer ? waveformPeaks(buffer) : []), [buffer]);

  useEffect(() => {
    const element = canvas.current;
    if (!element || !peaks.length || !edit) return;
    const ratio = window.devicePixelRatio || 1;
    const width = element.clientWidth;
    const height = element.clientHeight;
    element.width = width * ratio;
    element.height = height * ratio;
    const ctx = element.getContext("2d");
    if (!ctx) return;
    ctx.scale(ratio, ratio);
    ctx.clearRect(0, 0, width, height);

    const span = Math.max(edit.end - edit.start, 0.01);
    const barWidth = width / peaks.length;
    const inSelection = (index: number) => {
      const at = (index / peaks.length) * (buffer?.duration ?? 0);
      return at >= edit.start && at <= edit.end;
    };

    peaks.forEach((peak, index) => {
      const amplitude = Math.max(peak, 0.02) * (height / 2);
      ctx.fillStyle = inSelection(index) ? "#1ed760" : "rgba(255,255,255,0.24)";
      ctx.fillRect(index * barWidth, height / 2 - amplitude, Math.max(barWidth - 1, 1), amplitude * 2);
    });

    const startX = (edit.start / (buffer?.duration || 1)) * width;
    const endX = (edit.end / (buffer?.duration || 1)) * width;
    ctx.fillStyle = "rgba(0,0,0,0.45)";
    ctx.fillRect(0, 0, startX, height);
    ctx.fillRect(endX, 0, width - endX, height);
    ctx.fillStyle = "#1ed760";
    ctx.fillRect(startX - 1, 0, 2, height);
    ctx.fillRect(endX - 1, 0, 2, height);
    if (edit.fadeIn > 0) {
      ctx.fillStyle = "rgba(30,215,96,0.18)";
      const fadeWidth = Math.min((edit.fadeIn / span) * (endX - startX), endX - startX);
      ctx.fillRect(startX, 0, fadeWidth, height);
    }
    if (edit.fadeOut > 0) {
      ctx.fillStyle = "rgba(30,215,96,0.18)";
      const fadeWidth = Math.min((edit.fadeOut / span) * (endX - startX), endX - startX);
      ctx.fillRect(endX - fadeWidth, 0, fadeWidth, height);
    }
  }, [peaks, edit, buffer]);

  useEffect(() => {
    const element = audio.current;
    if (!element || !edit) return;
    const onTime = () => {
      if (element.currentTime >= edit.end) {
        element.pause();
        element.currentTime = edit.start;
        setPreviewing(false);
      }
    };
    element.addEventListener("timeupdate", onTime);
    return () => element.removeEventListener("timeupdate", onTime);
  }, [edit]);

  const preview = () => {
    const element = audio.current;
    if (!element || !edit) return;
    if (previewing) {
      element.pause();
      setPreviewing(false);
      return;
    }
    element.currentTime = edit.start;
    void element.play().then(() => setPreviewing(true)).catch(() => setPreviewing(false));
  };

  const apply = () => {
    if (!buffer || !edit) return;
    setSaving(true);
    const length = Math.max(edit.end - edit.start, 0.05);
    // Keep the name, replace the extension so the API sees a WAV.
    const base = file.name.replace(/\.[^.]+$/, "") || "song";
    const blob = renderEdit(buffer, { ...edit, end: edit.start + length });
    onApply(toFile(blob, base));
    setSaving(false);
  };

  const span = edit ? Math.max(edit.end - edit.start, 0) : 0;

  return (
    <div className="fixed inset-0 z-[90] grid place-items-end bg-black/70 backdrop-blur-sm sm:place-items-center" role="dialog" aria-modal="true" aria-label="Edit audio">
      <div className="w-full max-w-xl rounded-t-2xl bg-[#181818] p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] shadow-2xl sm:rounded-2xl">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-ink-faint">Edit audio</p>
            <p className="mt-1 truncate text-[17px] font-bold">{file.name}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="grid h-9 w-9 place-items-center rounded-full text-ink-soft transition hover:bg-white/10 hover:text-white"
            aria-label="Close the audio editor"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {error ? (
          <p className="mt-6 rounded-lg bg-[#3d1f1c] px-4 py-3 text-[14px] font-semibold text-[#ff9c92]">{error}</p>
        ) : !edit || !buffer ? (
          <div className="mt-6 flex h-32 items-center justify-center gap-3 rounded-lg bg-white/5 text-[14px] font-semibold text-ink-soft">
            <Scissors className="h-4 w-4" /> Reading the sound
          </div>
        ) : (
          <>
            <canvas ref={canvas} className="mt-4 h-28 w-full rounded-lg bg-black/40" aria-hidden="true" />
            <audio ref={audio} src={url} preload="metadata" className="hidden" />

            <div className="mt-4 grid grid-cols-2 gap-3">
              <label className="block">
                <span className="label-caps">Starts at {formatDuration(edit.start)}</span>
                <input
                  type="range"
                  min={0}
                  max={Math.max(buffer.duration - 0.1, 0.1)}
                  step={0.05}
                  value={edit.start}
                  onChange={(event) => {
                    const value = Math.min(Number(event.target.value), edit.end - 0.1);
                    setEdit({ ...edit, start: Math.max(0, value) });
                  }}
                  className="scrub mt-2 h-4 w-full"
                  aria-label="Where the clip starts"
                />
              </label>
              <label className="block">
                <span className="label-caps">Ends at {formatDuration(edit.end)}</span>
                <input
                  type="range"
                  min={0.1}
                  max={buffer.duration}
                  step={0.05}
                  value={edit.end}
                  onChange={(event) => {
                    const value = Math.max(Number(event.target.value), edit.start + 0.1);
                    setEdit({ ...edit, end: Math.min(buffer.duration, value) });
                  }}
                  className="scrub mt-2 h-4 w-full"
                  aria-label="Where the clip ends"
                />
              </label>
            </div>

            <div className="mt-4 flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={preview}
                className="inline-flex h-9 items-center gap-2 rounded-full bg-white/10 px-3.5 text-[13.5px] font-bold transition hover:bg-white/20"
              >
                {previewing ? <Pause className="h-3.5 w-3.5" fill="currentColor" /> : <Play className="h-3.5 w-3.5" fill="currentColor" />}
                {previewing ? "Stop" : "Hear it"}
              </button>
              <Chip
                on={edit.fadeIn > 0}
                onClick={() => setEdit({ ...edit, fadeIn: edit.fadeIn > 0 ? 0 : 1.5 })}
                label="Fade in"
              />
              <Chip
                on={edit.fadeOut > 0}
                onClick={() => setEdit({ ...edit, fadeOut: edit.fadeOut > 0 ? 0 : 2 })}
                label="Fade out"
              />
              <Chip on={edit.even} onClick={() => setEdit({ ...edit, even: !edit.even })} label="Even volume" />
            </div>

            <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
              <p className="text-[13px] text-ink-soft">
                Keeping {formatDuration(span)} of {formatDuration(buffer.duration)}
              </p>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={onClose}
                  className="h-10 rounded-full px-4 text-[14px] font-semibold text-ink-soft transition hover:bg-white/10 hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={apply}
                  disabled={saving || span < 3}
                  className="h-10 rounded-full bg-[#1ed760] px-5 text-[14px] font-bold text-black transition press disabled:opacity-45"
                >
                  Use this clip
                </button>
              </div>
            </div>
            {span < 3 ? <p className="mt-2 text-[12.5px] text-ink-faint">Keep at least three seconds.</p> : null}
          </>
        )}
      </div>
    </div>
  );
}

function Chip({ on, onClick, label }: { on: boolean; onClick: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={on}
      className={`h-9 rounded-full px-3.5 text-[13.5px] font-bold transition press ${
        on ? "bg-[#1ed760] text-black" : "bg-white/10 text-white hover:bg-white/20"
      }`}
    >
      {label}
    </button>
  );
}
