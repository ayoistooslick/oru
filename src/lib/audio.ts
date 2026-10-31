/**
 * Sound handling in the browser: decode whatever the artist picked, show it as
 * a waveform, cut it down and hand back a file the API accepts.
 *
 * Everything runs locally, so nothing is uploaded until the artist saves it.
 */

export interface AudioEdit {
  start: number;
  end: number;
  fadeIn: number;
  fadeOut: number;
  gain: number;
  even: boolean;
}

let context: AudioContext | null = null;

function audioContext(): AudioContext {
  if (!context) {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    context = new Ctor();
  }
  return context;
}

export async function decodeAudio(blob: Blob): Promise<AudioBuffer> {
  const data = await blob.arrayBuffer();
  return audioContext().decodeAudioData(data);
}

/** Peak levels for drawing. `buckets` is how many bars the picture gets. */
export function waveformPeaks(buffer: AudioBuffer, buckets = 220): number[] {
  const channel = buffer.getChannelData(0);
  const step = Math.max(1, Math.floor(channel.length / buckets));
  const peaks: number[] = [];
  for (let bucket = 0; bucket < buckets; bucket += 1) {
    let peak = 0;
    const from = bucket * step;
    const to = Math.min(from + step, channel.length);
    for (let index = from; index < to; index += 1) {
      const value = Math.abs(channel[index]);
      if (value > peak) peak = value;
    }
    peaks.push(peak);
  }
  return peaks;
}

export function defaultEdit(buffer: AudioBuffer): AudioEdit {
  return { start: 0, end: buffer.duration, fadeIn: 0, fadeOut: 0, gain: 1, even: false };
}

/**
 * Cut, fade and level the selection, then write a 16 bit WAV. WAV keeps the
 * conversion lossless and needs no encoder, and the API already accepts it.
 */
export function renderEdit(buffer: AudioBuffer, edit: AudioEdit): Blob {
  const rate = buffer.sampleRate;
  const from = Math.max(0, Math.min(edit.start, buffer.duration));
  const to = Math.max(from + 0.05, Math.min(edit.end, buffer.duration));
  const startSample = Math.floor(from * rate);
  const endSample = Math.min(Math.floor(to * rate), buffer.length);
  const channels = Math.min(buffer.numberOfChannels, 2);

  const data: Float32Array[] = [];
  for (let channel = 0; channel < channels; channel += 1) {
    data.push(buffer.getChannelData(channel).slice(startSample, endSample));
  }

  let ceiling = 1;
  if (edit.even) {
    let peak = 0;
    for (const track of data) {
      for (let index = 0; index < track.length; index += 1) {
        const value = Math.abs(track[index]);
        if (value > peak) peak = value;
      }
    }
    ceiling = peak > 0 ? 0.98 / peak : 1;
  }
  const scale = edit.gain * ceiling;
  const fadeInSamples = Math.floor(edit.fadeIn * rate);
  const fadeOutSamples = Math.floor(edit.fadeOut * rate);

  for (const track of data) {
    for (let index = 0; index < track.length; index += 1) {
      let value = track[index] * scale;
      if (fadeInSamples > 0 && index < fadeInSamples) value *= index / fadeInSamples;
      const fromEnd = track.length - index;
      if (fadeOutSamples > 0 && fromEnd < fadeOutSamples) value *= fromEnd / fadeOutSamples;
      // Clamp so a boosted track does not wrap around into noise.
      track[index] = Math.max(-1, Math.min(1, value));
    }
  }

  return new Blob([encodeWav(data, rate)], { type: "audio/wav" });
}

function encodeWav(tracks: Float32Array[], sampleRate: number): ArrayBuffer {
  const channels = tracks.length;
  const frames = tracks[0].length;
  const buffer = new ArrayBuffer(44 + frames * channels * 2);
  const view = new DataView(buffer);

  const text = (offset: number, value: string) => {
    for (let index = 0; index < value.length; index += 1) view.setUint8(offset + index, value.charCodeAt(index));
  };

  text(0, "RIFF");
  view.setUint32(4, 36 + frames * channels * 2, true);
  text(8, "WAVE");
  text(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, channels, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * channels * 2, true);
  view.setUint16(32, channels * 2, true);
  view.setUint16(34, 16, true);
  text(36, "data");
  view.setUint32(40, frames * channels * 2, true);

  let offset = 44;
  for (let frame = 0; frame < frames; frame += 1) {
    for (let channel = 0; channel < channels; channel += 1) {
      const sample = Math.max(-1, Math.min(1, tracks[channel][frame]));
      view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
      offset += 2;
    }
  }
  return buffer;
}

/** A file the studio can upload, named after the song rather than "recording". */
export function toFile(blob: Blob, name: string): File {
  return new File([blob], name.endsWith(".wav") ? name : `${name}.wav`, { type: "audio/wav" });
}
