package com.oru.app.ui.studio;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.ActivityUploadBinding;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.util.LazySession;
import com.oru.app.util.PickedFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The upload form: pick the audio, name it, tag it, put it out. This is the
 * screen the studio tab and the API's multipart upload were missing.
 */
public class UploadActivity extends AppCompatActivity {

    private ActivityUploadBinding binding;
    private MusicRepository musicRepo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private PickedFile audio;
    private PickedFile cover;

    private final ActivityResultLauncher<String> pickAudio =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    this::onAudioPicked);

    private final ActivityResultLauncher<String> pickCover =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    this::onCoverPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUploadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        musicRepo = new MusicRepository(LazySession.getApiService(),
                LazySession.getGson());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.audioButton.setOnClickListener(v -> pickAudio.launch("audio/*"));
        binding.coverButton.setOnClickListener(v -> pickCover.launch("image/*"));
        binding.submitButton.setOnClickListener(v -> submit());
    }

    private void onAudioPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        read(uri, true);
    }

    private void onCoverPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        read(uri, false);
    }

    private void read(Uri uri, boolean isAudio) {
        setBusy(true, "Reading the file…");
        io.execute(() -> {
            try {
                PickedFile picked = isAudio
                        ? PickedFile.copy(this, uri)
                        : PickedFile.copyImage(this, uri);
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    setBusy(false, null);
                    if (isAudio) {
                        audio = picked;
                        binding.audioText.setText(picked.name
                                + (picked.durationMs > 0
                                        ? " · " + length(picked.durationMs)
                                        : ""));
                        binding.audioText.setTextColor(
                                getColor(com.oru.app.R.color.ink));
                    } else {
                        cover = picked;
                        binding.coverText.setText(picked.name);
                        binding.coverText.setTextColor(
                                getColor(com.oru.app.R.color.ink));
                    }
                });
            } catch (IOException | RuntimeException error) {
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    setBusy(false, null);
                    String reason = error.getMessage();
                    Toast.makeText(this,
                            reason != null && !reason.isEmpty() ? reason
                                    : "Couldn't read that file",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void submit() {
        String title = text(binding.titleEditText.getText());
        if (title.isEmpty()) {
            binding.titleLayout.setError("Give it a title");
            return;
        }
        binding.titleLayout.setError(null);
        if (audio == null) {
            setBusy(false, "Choose the audio file first");
            return;
        }
        String description = text(binding.descriptionEditText.getText());
        List<String> genres = new ArrayList<>();
        for (String genre : text(binding.genresEditText.getText()).split(",")) {
            if (!genre.trim().isEmpty()) {
                genres.add(genre.trim());
            }
        }

        setBusy(true, "Uploading…");
        musicRepo.uploadSong(title, description, genres, audio.durationMs,
                audio.file, audio.mime,
                cover != null ? cover.file : null,
                cover != null ? cover.mime : null,
                new Callback<Song>() {
                    @Override
                    public void onSuccess(Song song) {
                        if (binding.publishSwitch.isChecked()) {
                            publish(song);
                        } else {
                            finishWith("Saved as a draft");
                        }
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        if (binding == null) {
                            return;
                        }
                        setBusy(false, UiErrors.message(UploadActivity.this, error));
                    }
                });
    }

    private void publish(Song song) {
        setBusy(true, "Publishing…");
        musicRepo.publishSong(song.id, new Callback<Song>() {
            @Override
            public void onSuccess(Song published) {
                finishWith("It's out there");
            }

            @Override
            public void onFailure(Throwable error) {
                finishWith("Uploaded, but publishing failed: "
                        + UiErrors.message(UploadActivity.this, error));
            }
        });
    }

    private void finishWith(String message) {
        runOnUiThread(() -> {
            setBusy(false, null);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
        });
    }

    private void setBusy(boolean busy, String message) {
        if (binding == null) {
            return;
        }
        binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.submitButton.setEnabled(!busy);
        binding.statusText.setText(message != null ? message : "");
        binding.statusText.setVisibility(
                message != null && !message.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String text(CharSequence value) {
        return value != null ? value.toString().trim() : "";
    }

    private static String length(long durationMs) {
        long seconds = durationMs / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60,
                seconds % 60);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
        binding = null;
    }
}
