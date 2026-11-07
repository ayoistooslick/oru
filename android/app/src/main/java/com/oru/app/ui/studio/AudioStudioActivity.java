package com.oru.app.ui.studio;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.slider.Slider;
import com.oru.app.R;
import com.oru.app.databinding.ActivityAudioStudioBinding;
import com.oru.app.util.AudioClip;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The phone's side of the web studio's record-and-edit doors: record a take
 * with the microphone, then cut it down, fade it and hand the upload form a
 * WAV — the same shape the browser produces, so a song made on either one
 * arrives the same way.
 */
public class AudioStudioActivity extends AppCompatActivity {

    /** The clip to edit, when the caller already has one. */
    public static final String EXTRA_AUDIO_PATH = "audioPath";
    public static final String EXTRA_AUDIO_NAME = "audioName";

    public static final String RESULT_PATH = "path";
    public static final String RESULT_NAME = "name";
    public static final String RESULT_MIME = "mime";
    public static final String RESULT_DURATION_MS = "durationMs";

    private static final double MIN_CLIP_SECONDS = 3;
    private static final double FADE_IN_SECONDS = 1.5;
    private static final double FADE_OUT_SECONDS = 2;

    private ActivityAudioStudioBinding binding;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private MediaRecorder recorder;
    private MediaPlayer preview;
    private AudioClip clip;

    private File source;
    private File recorded;
    private boolean ownsSource;
    private boolean recording;
    private long startedAt;
    private boolean syncing;

    private final ActivityResultLauncher<String> microphone =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            startRecording();
                        } else {
                            recordError("The microphone is blocked for ORU. "
                                    + "Allow it in Android settings, or upload a file instead.");
                        }
                    });

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!recording || binding == null) {
                return;
            }
            binding.clockText.setText(clock((SystemClock.elapsedRealtime() - startedAt) / 1000.0));
            main.postDelayed(this, 250);
        }
    };

    private final Runnable previewTick = new Runnable() {
        @Override
        public void run() {
            if (preview == null || binding == null) {
                return;
            }
            double end = binding.endSlider.getValue();
            if (preview.getCurrentPosition() >= Math.round(end * 1000)) {
                stopPreview();
                return;
            }
            main.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAudioStudioBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.recordButton.setOnClickListener(v -> {
            if (recording) {
                stopRecording();
            } else {
                askForMicrophone();
            }
        });
        binding.cancelButton.setOnClickListener(v -> finish());
        binding.applyButton.setOnClickListener(v -> apply());
        binding.previewButton.setOnClickListener(v -> togglePreview());
        binding.chipFadeIn.setOnCheckedChangeListener((button, checked) -> updateSelection());
        binding.chipFadeOut.setOnCheckedChangeListener((button, checked) -> updateSelection());
        binding.chipEven.setOnCheckedChangeListener((button, checked) -> updateSelection());
        binding.startSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (syncing || !fromUser || clip == null) {
                return;
            }
            double end = binding.endSlider.getValue();
            if (value > end - 0.1) {
                syncing = true;
                binding.endSlider.setValue((float) Math.min(clip.duration(), value + 0.1));
                syncing = false;
            }
            updateSelection();
        });
        binding.endSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (syncing || !fromUser || clip == null) {
                return;
            }
            double start = binding.startSlider.getValue();
            if (value < start + 0.1) {
                syncing = true;
                binding.startSlider.setValue((float) Math.max(0, value - 0.1));
                syncing = false;
            }
            updateSelection();
        });

        String path = getIntent().getStringExtra(EXTRA_AUDIO_PATH);
        if (path != null && new File(path).isFile()) {
            String name = getIntent().getStringExtra(EXTRA_AUDIO_NAME);
            openEditor(new File(path),
                    name != null && !name.isEmpty() ? name : new File(path).getName(),
                    false);
        }
    }

    // ---------------------------------------------------------------- record

    private void askForMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startRecording();
            return;
        }
        microphone.launch(Manifest.permission.RECORD_AUDIO);
    }

    @SuppressWarnings("deprecation")
    private MediaRecorder newRecorder() {
        return Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
    }

    private void startRecording() {
        binding.recordError.setVisibility(View.GONE);
        File dir = new File(getCacheDir(), "recordings");
        if (!dir.exists() && !dir.mkdirs()) {
            recordError("Couldn't make a place to keep the recording");
            return;
        }
        File target = new File(dir, "take-" + System.currentTimeMillis() + ".m4a");
        try {
            recorder = newRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128_000);
            recorder.setOutputFile(target.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (IOException | RuntimeException error) {
            releaseRecorder();
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            recordError("The microphone couldn't start: " + reason(error));
            return;
        }
        recorded = target;
        recording = true;
        startedAt = SystemClock.elapsedRealtime();
        binding.recordButton.setText("Stop and edit");
        binding.recordButton.setIconResource(R.drawable.ic_stop);
        binding.recordHint.setText("Recording. Sing, hum or play.");
        binding.clockText.setText("0:00");
        main.post(tick);
    }

    private void stopRecording() {
        if (!recording) {
            return;
        }
        recording = false;
        main.removeCallbacks(tick);
        boolean kept = true;
        try {
            recorder.stop();
        } catch (RuntimeException error) {
            // The encoder refuses a take with no frames in it.
            kept = false;
        }
        releaseRecorder();
        File take = recorded;
        recorded = null;
        resetRecordButton();
        if (!kept || take == null || take.length() == 0) {
            if (take != null) {
                //noinspection ResultOfMethodCallIgnored
                take.delete();
            }
            recordError("That take was too short to keep. Record for a second or more.");
            return;
        }
        openEditor(take, "recording-" + System.currentTimeMillis() + ".m4a", true);
    }

    private void resetRecordButton() {
        binding.recordButton.setText("Start recording");
        binding.recordButton.setIconResource(R.drawable.ic_mic);
        binding.recordHint.setText("Sing it in one take. You can trim it after.");
        binding.clockText.setText("0:00");
    }

    private void recordError(String message) {
        binding.recordError.setText(message);
        binding.recordError.setVisibility(View.VISIBLE);
    }

    private void releaseRecorder() {
        if (recorder == null) {
            return;
        }
        try {
            recorder.reset();
        } catch (RuntimeException ignored) {
            // Reset only fails once the recorder has already let go.
        }
        try {
            recorder.release();
        } catch (RuntimeException ignored) {
            // Nothing left to release.
        }
        recorder = null;
    }

    // ------------------------------------------------------------------ edit

    private File scratchDir() {
        File dir = new File(getCacheDir(), "audio-studio");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private File scratch(String name) {
        return new File(scratchDir(), name);
    }

    private void openEditor(File audio, String name, boolean owned) {
        source = audio;
        ownsSource = owned;
        binding.recordPanel.setVisibility(View.GONE);
        binding.editPanel.setVisibility(View.VISIBLE);
        binding.toolbar.setTitle("Edit audio");
        binding.editName.setText(name);
        setStatus("Reading the sound", false);
        setBusy(true, null);
        io.execute(() -> {
            AudioClip decoded = null;
            float[] peaks = null;
            String failure = null;
            try {
                decoded = AudioClip.decode(audio, scratchDir());
                peaks = decoded.peaks();
            } catch (IOException | RuntimeException error) {
                failure = reason(error);
            }
            AudioClip ready = decoded;
            float[] drawn = peaks;
            String problem = failure;
            runOnUiThread(() -> {
                if (binding == null) {
                    if (ready != null) {
                        ready.close();
                    }
                    return;
                }
                if (problem != null || ready == null) {
                    setBusy(false, null);
                    String message = problem != null ? problem
                            : "That sound couldn't be read here";
                    if (ownsSource) {
                        // The take can't be edited, so hand the microphone back.
                        binding.recordPanel.setVisibility(View.VISIBLE);
                        binding.editPanel.setVisibility(View.GONE);
                        binding.toolbar.setTitle("Record a song");
                        recordError(message);
                    } else {
                        setStatus(message, true);
                    }
                    return;
                }
                if (clip != null) {
                    ready.close();
                    return;
                }
                clip = ready;
                showClip(drawn);
            });
        });
    }

    private void showClip(float[] peaks) {
        double total = clip.duration();
        if (total < 0.2) {
            setBusy(false, null);
            setStatus("That clip has no length to edit. Record for a few seconds.",
                    true);
            return;
        }
        syncing = true;
        binding.startSlider.setValueFrom(0f);
        binding.startSlider.setValueTo((float) total);
        binding.startSlider.setValue(0f);
        binding.endSlider.setValueFrom(0f);
        binding.endSlider.setValueTo((float) total);
        binding.endSlider.setValue((float) total);
        syncing = false;
        binding.waveform.setPeaks(peaks);
        setStatus(null, false);
        setBusy(false, null);
        updateSelection();
    }

    private void updateSelection() {
        if (clip == null) {
            return;
        }
        double total = clip.duration();
        double start = binding.startSlider.getValue();
        double end = binding.endSlider.getValue();
        double fadeIn = binding.chipFadeIn.isChecked() ? FADE_IN_SECONDS : 0;
        double fadeOut = binding.chipFadeOut.isChecked() ? FADE_OUT_SECONDS : 0;
        binding.waveform.setSelection(total, start, end, fadeIn, fadeOut);
        binding.startLabel.setText("Starts at " + clock(start));
        binding.endLabel.setText("Ends at " + clock(end));
        binding.keepText.setText("Keeping " + clock(Math.max(end - start, 0)));
        binding.applyButton.setEnabled(end - start >= MIN_CLIP_SECONDS);
        if (end - start < MIN_CLIP_SECONDS) {
            setStatus("Keep at least three seconds", false);
        } else {
            setStatus(null, false);
        }
    }

    private void togglePreview() {
        if (preview != null) {
            stopPreview();
            return;
        }
        if (source == null || clip == null) {
            return;
        }
        try {
            preview = new MediaPlayer();
            preview.setDataSource(source.getAbsolutePath());
            preview.prepare();
            preview.seekTo((int) Math.round(binding.startSlider.getValue() * 1000));
            preview.start();
        } catch (IOException | RuntimeException error) {
            stopPreview();
            setStatus("Couldn't play it back: " + reason(error), true);
            return;
        }
        binding.previewButton.setText("Stop");
        binding.previewButton.setIconResource(R.drawable.ic_pause);
        main.postDelayed(previewTick, 100);
    }

    private void stopPreview() {
        main.removeCallbacks(previewTick);
        if (preview != null) {
            try {
                preview.stop();
            } catch (RuntimeException ignored) {
                // It was never started, or already finished.
            }
            preview.release();
            preview = null;
        }
        if (binding != null) {
            binding.previewButton.setText("Hear it");
            binding.previewButton.setIconResource(R.drawable.ic_play);
        }
    }

    private void apply() {
        if (clip == null || source == null) {
            return;
        }
        final double start = binding.startSlider.getValue();
        final double end = binding.endSlider.getValue();
        if (end - start < MIN_CLIP_SECONDS) {
            setStatus("Keep at least three seconds", true);
            return;
        }
        final double fadeIn = binding.chipFadeIn.isChecked() ? FADE_IN_SECONDS : 0;
        final double fadeOut = binding.chipFadeOut.isChecked() ? FADE_OUT_SECONDS : 0;
        final boolean even = binding.chipEven.isChecked();
        final String name = baseName(binding.editName.getText().toString()) + ".wav";
        final File target = scratch("clip-" + System.currentTimeMillis() + ".wav");
        final AudioClip cutting = clip;
        stopPreview();
        setBusy(true, "Cutting the clip");
        io.execute(() -> {
            try {
                long duration = cutting.render(target, start, end, fadeIn, fadeOut, even);
                Intent data = new Intent();
                data.putExtra(RESULT_PATH, target.getAbsolutePath());
                data.putExtra(RESULT_NAME, name);
                data.putExtra(RESULT_MIME, "audio/wav");
                data.putExtra(RESULT_DURATION_MS, duration);
                runOnUiThread(() -> {
                    setResult(RESULT_OK, data);
                    finish();
                });
            } catch (IOException | RuntimeException error) {
                String problem = reason(error);
                runOnUiThread(() -> {
                    setBusy(false, null);
                    setStatus(problem, true);
                });
            }
        });
    }

    // ---------------------------------------------------------------- chrome

    private void setBusy(boolean busy, String message) {
        if (binding == null) {
            return;
        }
        binding.editProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.applyButton.setEnabled(!busy && clip != null
                && binding.endSlider.getValue() - binding.startSlider.getValue()
                >= MIN_CLIP_SECONDS);
        binding.previewButton.setEnabled(!busy);
        binding.startSlider.setEnabled(!busy);
        binding.endSlider.setEnabled(!busy);
        if (message != null) {
            setStatus(message, false);
        }
    }

    private void setStatus(String message, boolean bad) {
        if (binding == null) {
            return;
        }
        if (message == null || message.isEmpty()) {
            binding.editStatus.setVisibility(View.GONE);
            return;
        }
        binding.editStatus.setText(message);
        binding.editStatus.setTextColor(ContextCompat.getColor(this,
                bad ? R.color.flame_ink : R.color.ink_soft));
        binding.editStatus.setVisibility(View.VISIBLE);
    }

    private static String clock(double seconds) {
        int whole = (int) Math.floor(Math.max(seconds, 0));
        return String.format(Locale.US, "%d:%02d", whole / 60, whole % 60);
    }

    private static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base.isEmpty() ? "clip" : base;
    }

    private static String reason(Throwable error) {
        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return error.getClass().getSimpleName();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recording = false;
        main.removeCallbacks(tick);
        main.removeCallbacks(previewTick);
        releaseRecorder();
        stopPreview();
        if (clip != null) {
            clip.close();
            clip = null;
        }
        if (ownsSource && source != null) {
            //noinspection ResultOfMethodCallIgnored
            source.delete();
        }
        io.shutdown();
        binding = null;
    }
}
