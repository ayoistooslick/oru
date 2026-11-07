package com.oru.app.ui.studio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.app.AlertDialog;

import com.oru.app.R;
import com.oru.app.data.model.Artist;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.ActivityUploadBinding;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.util.LazySession;
import com.oru.app.util.PickedFile;

import java.io.File;
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

    private static final int MAX_FEATURED = 5;

    private PickedFile audio;
    private PickedFile cover;
    /** Other artists credited on this song, the way the web form allows. */
    private final List<Artist> featured = new ArrayList<>();

    private final ActivityResultLauncher<String> pickAudio =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    this::onAudioPicked);

    private final ActivityResultLauncher<String> pickCover =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    this::onCoverPicked);

    /** Back from the recorder or the clip editor, with a WAV to upload. */
    private final ActivityResultLauncher<Intent> clip =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                            return;
                        }
                        takeClip(result.getData());
                    });

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
        binding.recordButton.setOnClickListener(v ->
                clip.launch(new Intent(this, AudioStudioActivity.class)));
        binding.editAudioButton.setOnClickListener(v -> editClip());
        binding.featuredButton.setOnClickListener(v -> pickFeatured());
        binding.submitButton.setOnClickListener(v -> submit());
    }

    /** Searches ORU artists and credits the ones picked, up to five. */
    private void pickFeatured() {
        final android.widget.EditText query = new android.widget.EditText(this);
        query.setHint("Artist name");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, (int) (8 * getResources().getDisplayMetrics().density),
                pad, 0);
        box.addView(query);
        new AlertDialog.Builder(this)
                .setTitle("Feature other artists")
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Search", (dialog, which) ->
                        findFeatured(query.getText().toString().trim()))
                .show();
    }

    private void findFeatured(String query) {
        if (query.isEmpty()) {
            return;
        }
        setBusy(true, "Looking for \"" + query + "\"…");
        musicRepo.searchArtists(query, new Callback<List<Artist>>() {
            @Override
            public void onSuccess(List<Artist> artists) {
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    setBusy(false, null);
                    if (artists == null || artists.isEmpty()) {
                        setBusy(false, "No artist on ORU matches \"" + query + "\"");
                        return;
                    }
                    offerFeatured(artists);
                });
            }

            @Override
            public void onFailure(Throwable error) {
                runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    setBusy(false, UiErrors.message(UploadActivity.this, error));
                });
            }
        });
    }

    private void offerFeatured(List<Artist> artists) {
        int me = new com.oru.app.data.session.SessionManager(this).getUserId();
        List<Artist> shown = new ArrayList<>();
        for (Artist artist : artists) {
            if (artist.artistId() != me) {
                shown.add(artist);
            }
        }
        if (shown.isEmpty()) {
            setBusy(false, "That is you — credit someone else on this song.");
            return;
        }
        String[] labels = new String[shown.size()];
        final boolean[] picked = new boolean[shown.size()];
        for (int i = 0; i < shown.size(); i++) {
            Artist artist = shown.get(i);
            labels[i] = artist.stageName != null && !artist.stageName.isEmpty()
                    ? artist.stageName
                    : artist.handle;
            for (Artist already : featured) {
                if (already.artistId() == artist.artistId()) {
                    picked[i] = true;
                    break;
                }
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Feature on this song")
                .setMultiChoiceItems(labels, picked,
                        (dialog, which, isChecked) -> picked[which] = isChecked)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Done", (dialog, which) -> {
                    List<Artist> chosen = new ArrayList<>();
                    for (int i = 0; i < shown.size(); i++) {
                        if (picked[i]) {
                            chosen.add(shown.get(i));
                        }
                    }
                    if (chosen.size() > MAX_FEATURED) {
                        setBusy(false, "A song can credit " + MAX_FEATURED
                                + " other artists at most");
                        chosen = chosen.subList(0, MAX_FEATURED);
                    }
                    featured.clear();
                    featured.addAll(chosen);
                    updateFeaturedText();
                })
                .show();
    }

    private void updateFeaturedText() {
        if (featured.isEmpty()) {
            binding.featuredText.setText("Feature other artists (optional)");
            return;
        }
        StringBuilder names = new StringBuilder();
        for (Artist artist : featured) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(artist.stageName != null ? artist.stageName
                    : artist.handle);
        }
        binding.featuredText.setText("Featuring " + names);
    }

    /** Opens the same editor the web studio uses, on whatever audio is picked. */
    private void editClip() {
        if (audio == null) {
            Toast.makeText(this, "Choose or record the audio first",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, AudioStudioActivity.class);
        intent.putExtra(AudioStudioActivity.EXTRA_AUDIO_PATH,
                audio.file.getAbsolutePath());
        intent.putExtra(AudioStudioActivity.EXTRA_AUDIO_NAME, audio.name);
        clip.launch(intent);
    }

    private void takeClip(Intent data) {
        String path = data.getStringExtra(AudioStudioActivity.RESULT_PATH);
        if (path == null) {
            return;
        }
        File file = new File(path);
        if (!file.isFile() || file.length() == 0) {
            Toast.makeText(this, "That clip came out empty. Try recording again.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String name = data.getStringExtra(AudioStudioActivity.RESULT_NAME);
        String mime = data.getStringExtra(AudioStudioActivity.RESULT_MIME);
        long duration = data.getLongExtra(
                AudioStudioActivity.RESULT_DURATION_MS, 0);
        audio = PickedFile.fromFile(file, name, mime, duration);
        binding.audioText.setText(audio.name
                + (duration > 0 ? " · " + length(duration) : ""));
        binding.audioText.setTextColor(getColor(com.oru.app.R.color.ink));
        binding.editAudioButton.setVisibility(View.VISIBLE);
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
                        binding.editAudioButton.setVisibility(View.VISIBLE);
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
        String problem = PickedFile.audioProblem(audio);
        if (problem != null) {
            setBusy(false, problem);
            return;
        }
        String description = text(binding.descriptionEditText.getText());
        List<String> genres = new ArrayList<>();
        for (String genre : text(binding.genresEditText.getText()).split(",")) {
            if (!genre.trim().isEmpty()) {
                genres.add(genre.trim());
            }
        }

        List<Integer> featuredIds = new ArrayList<>();
        for (Artist artist : featured) {
            featuredIds.add(artist.artistId());
        }

        setBusy(true, "Uploading…");
        musicRepo.uploadSong(title, description, genres, featuredIds,
                audio.durationMs, audio.file, audio.uploadMime(),
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
