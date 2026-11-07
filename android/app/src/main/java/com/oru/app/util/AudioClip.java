package com.oru.app.util;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Sound handling on the phone: decode whatever the artist recorded or picked
 * into plain 16 bit samples once, then draw it, cut it and write it back as a
 * WAV the API already accepts.
 *
 * The samples are kept on disk rather than in memory, so a long take does not
 * decide whether the editor opens or the app runs out of heap.
 */
public final class AudioClip implements Closeable {

    /** How many bars the waveform gets, matching the web editor. */
    public static final int BUCKETS = 220;

    private final File pcm;
    public final int channels;
    public final int sampleRate;
    public final long frames;

    private AudioClip(File pcm, int channels, int sampleRate, long frames) {
        this.pcm = pcm;
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.frames = frames;
    }

    public double duration() {
        return sampleRate > 0 ? frames / (double) sampleRate : 0;
    }

    /**
     * Reads the first sound track of {@code source} into a scratch file of
     * interleaved 16 bit samples.
     */
    public static AudioClip decode(File source, File dir) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Couldn't make room for the clip");
        }
        File pcm = File.createTempFile("clip-", ".pcm", dir);
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        DataOutputStream out = null;
        boolean complete = false;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            int track = -1;
            MediaFormat format = null;
            for (int index = 0; index < extractor.getTrackCount(); index++) {
                MediaFormat candidate = extractor.getTrackFormat(index);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = index;
                    format = candidate;
                    break;
                }
            }
            if (track < 0 || format == null) {
                throw new IOException("That file has no sound in it");
            }
            String type = format.getString(MediaFormat.KEY_MIME);
            if (type == null) {
                throw new IOException("That file's sound format couldn't be read");
            }
            int rate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    : 44100;
            int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    : 1;
            int encoding = format.containsKey(MediaFormat.KEY_PCM_ENCODING)
                    ? format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    : AudioFormat.ENCODING_PCM_16BIT;

            extractor.selectTrack(track);
            codec = MediaCodec.createDecoderByType(type);
            codec.configure(format, null, null, 0);
            codec.start();
            out = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(pcm), 64 * 1024));

            // An hour of sound is the ceiling; past that the file should be
            // uploaded untouched instead of edited on the phone.
            final long limitSamples = 3600L * rate * channelCount;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            long samples = 0;
            while (!outputDone) {
                if (!inputDone) {
                    int input = codec.dequeueInputBuffer(10_000);
                    if (input >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(input);
                        int size = buffer == null ? -1 : extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(input, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(input, 0, size,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int output = codec.dequeueOutputBuffer(info, 10_000);
                if (output >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(output);
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);
                        samples += writeSamples(buffer, encoding, out);
                    }
                    codec.releaseOutputBuffer(output, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                } else if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat changed = codec.getOutputFormat();
                    if (samples > 0) {
                        continue;
                    }
                    if (changed.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        rate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (changed.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    if (changed.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        encoding = changed.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                }
                if (encoding != AudioFormat.ENCODING_PCM_16BIT
                        && encoding != AudioFormat.ENCODING_PCM_FLOAT
                        && encoding != AudioFormat.ENCODING_PCM_8BIT) {
                    throw new IOException("That sound is in a format this phone can't edit. "
                            + "Upload the file instead.");
                }
                if (samples > limitSamples) {
                    throw new IOException("That recording is over an hour long. "
                            + "Upload it as a file instead.");
                }
            }
            out.flush();
            out.close();
            out = null;
            if (samples <= 0) {
                throw new IOException("No sound came out of that file");
            }
            AudioClip clip = new AudioClip(pcm, Math.max(1, channelCount),
                    Math.max(1, rate), samples / Math.max(1, channelCount));
            complete = true;
            return clip;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // The reader below is the one that matters.
                }
            }
            if (codec != null) {
                try {
                    codec.stop();
                } catch (RuntimeException ignored) {
                    // Nothing was running by the time we got here.
                }
                try {
                    codec.release();
                } catch (RuntimeException ignored) {
                    // Already released.
                }
            }
            extractor.release();
            if (!complete) {
                //noinspection ResultOfMethodCallIgnored
                pcm.delete();
            }
        }
    }

    private static long writeSamples(ByteBuffer buffer, int encoding,
            DataOutputStream out) throws IOException {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        long written = 0;
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            FloatBuffer floats = buffer.asFloatBuffer();
            while (floats.hasRemaining()) {
                float value = floats.get();
                if (value > 1f) {
                    value = 1f;
                } else if (value < -1f) {
                    value = -1f;
                }
                out.writeShort((short) (value < 0 ? value * 0x8000 : value * 0x7fff));
                written++;
            }
        } else if (encoding == AudioFormat.ENCODING_PCM_8BIT) {
            while (buffer.hasRemaining()) {
                out.writeShort((short) (((buffer.get() & 0xff) - 128) << 8));
                written++;
            }
        } else {
            ShortBuffer shorts = buffer.asShortBuffer();
            while (shorts.hasRemaining()) {
                out.writeShort(shorts.get());
                written++;
            }
        }
        return written;
    }

    /** Peak level per bar, for drawing. */
    public float[] peaks() {
        float[] peaks = new float[BUCKETS];
        if (frames <= 0) {
            return peaks;
        }
        long step = Math.max(1, frames / BUCKETS);
        byte[] chunk = new byte[64 * 1024];
        long shorts = 0;
        try (BufferedInputStream in = new BufferedInputStream(
                new FileInputStream(pcm), 64 * 1024)) {
            int read;
            while ((read = in.read(chunk)) > 0) {
                for (int offset = 0; offset + 1 < read; offset += 2) {
                    short value = (short) ((chunk[offset] & 0xff)
                            | (chunk[offset + 1] << 8));
                    int bucket = (int) Math.min(BUCKETS - 1,
                            (shorts / channels) / step);
                    float amplitude = Math.abs(value) / 32768f;
                    if (amplitude > peaks[bucket]) {
                        peaks[bucket] = amplitude;
                    }
                    shorts++;
                }
            }
        } catch (IOException ignored) {
            // The bars simply stay flat if the scratch file went away.
        }
        return peaks;
    }

    /**
     * Cuts the selection, applies the fades and levels the whole clip, then
     * writes a 16 bit WAV. Returns the length of the clip in milliseconds.
     */
    public long render(File target, double startSeconds, double endSeconds,
            double fadeInSeconds, double fadeOutSeconds, boolean even)
            throws IOException {
        int frameBytes = channels * 2;
        long from = Math.max(0, Math.min(frames - 1, Math.round(startSeconds * sampleRate)));
        long to = Math.max(from + 1, Math.min(frames, Math.round(endSeconds * sampleRate)));
        long count = to - from;
        float ceiling = 1f;
        if (even) {
            long peak = highest(from, to);
            if (peak > 0) {
                ceiling = (float) (0.98 * 32767.0 / peak);
            }
        }
        long fadeIn = Math.max(0, Math.round(fadeInSeconds * sampleRate));
        long fadeOut = Math.max(0, Math.round(fadeOutSeconds * sampleRate));

        FileOutputStream file = new FileOutputStream(target);
        DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(file, 64 * 1024));
        RandomAccessFile source = new RandomAccessFile(pcm, "r");
        try {
            writeHeader(out, channels, sampleRate, count);
            source.seek(from * frameBytes);
            byte[] block = new byte[64 * 1024];
            long done = 0;
            while (done < count) {
                int wanted = (int) Math.min(block.length / frameBytes, count - done);
                int bytes = wanted * frameBytes;
                int read = 0;
                while (read < bytes) {
                    int got = source.read(block, read, bytes - read);
                    if (got < 0) {
                        break;
                    }
                    read += got;
                }
                int held = read / frameBytes;
                if (held == 0) {
                    break;
                }
                for (int frame = 0; frame < held; frame++) {
                    long index = done + frame;
                    float scale = ceiling;
                    if (fadeIn > 0 && index < fadeIn) {
                        scale *= (float) index / fadeIn;
                    }
                    long fromEnd = count - index;
                    if (fadeOut > 0 && fromEnd < fadeOut) {
                        scale *= (float) fromEnd / fadeOut;
                    }
                    for (int channel = 0; channel < channels; channel++) {
                        int offset = frame * frameBytes + channel * 2;
                        short sample = (short) ((block[offset] & 0xff)
                                | (block[offset + 1] << 8));
                        int scaled = Math.round(sample * scale);
                        if (scaled > 32767) {
                            scaled = 32767;
                        } else if (scaled < -32768) {
                            scaled = -32768;
                        }
                        writeShort(out, scaled);
                    }
                }
                done += held;
            }
            out.flush();
        } finally {
            source.close();
            out.close();
        }
        return Math.round(count * 1000.0 / sampleRate);
    }

    private long highest(long from, long to) throws IOException {
        int frameBytes = channels * 2;
        byte[] block = new byte[64 * 1024];
        long highest = 0;
        try (RandomAccessFile source = new RandomAccessFile(pcm, "r")) {
            source.seek(from * frameBytes);
            long done = 0;
            long count = to - from;
            while (done < count) {
                int wanted = (int) Math.min(block.length / frameBytes, count - done);
                int bytes = wanted * frameBytes;
                int read = 0;
                while (read < bytes) {
                    int got = source.read(block, read, bytes - read);
                    if (got < 0) {
                        break;
                    }
                    read += got;
                }
                int held = read / frameBytes;
                if (held == 0) {
                    break;
                }
                for (int offset = 0; offset < held * frameBytes; offset += 2) {
                    short sample = (short) ((block[offset] & 0xff)
                            | (block[offset + 1] << 8));
                    int value = Math.abs(sample);
                    if (value > highest) {
                        highest = value;
                    }
                }
                done += held;
            }
        }
        return highest;
    }

    private static void writeHeader(DataOutputStream out, int channels,
            int sampleRate, long frames) throws IOException {
        long dataBytes = frames * channels * 2L;
        out.writeBytes("RIFF");
        writeInt(out, (int) (36 + dataBytes));
        out.writeBytes("WAVE");
        out.writeBytes("fmt ");
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, channels);
        writeInt(out, sampleRate);
        writeInt(out, sampleRate * channels * 2);
        writeShort(out, channels * 2);
        writeShort(out, 16);
        out.writeBytes("data");
        writeInt(out, (int) dataBytes);
    }

    private static void writeShort(DataOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private static void writeInt(DataOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    @Override
    public void close() {
        //noinspection ResultOfMethodCallIgnored
        pcm.delete();
    }
}
