package com.oru.app.ui.shares;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.oru.app.data.model.Artist;
import com.oru.app.data.model.ExternalShare;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.data.repository.SharingRepository;
import com.oru.app.databinding.ActivityShareBinding;
import com.oru.app.ui.components.UiErrors;
import com.oru.app.util.LazySession;
import com.oru.app.util.PickedFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sharing, both ways the API supports it: attach the audio of a song you have
 * and credit its artist, or point us at a link by an artist we don't carry yet.
 */
public class ShareActivity extends AppCompatActivity {

    private ActivityShareBinding binding;
    private MusicRepository musicRepo;
    private SharingRepository sharingRepo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private Artist artist;
    private PickedFile audio;
    private PickedFile cover;

    private final ActivityResultLauncher<String> pickAudio =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            read(uri, true);
                        }
                    });

    private final ActivityResultLauncher<String> pickCover =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            read(uri, false);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        musicRepo = new MusicRepository(LazySession.getApiService(),
                LazySession.getGson());
        sharingRepo = new SharingRepository(LazySession.getApiService(),
                LazySession.getGson());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.artistButton.setOnClickListener(v -> pickArtist());
        binding.audioButton.setOnClickListener(v -> pickAudio.launch("audio/*"));
        binding.coverButton.setOnClickListener(v -> pickCover.launch("image/*"));
        binding.shareButton.setOnClickListener(v -> shareSong());
        binding.linkButton.setOnClickListener(v -> shareLink());
    }

    private void pickArtist() {
        final EditText input = new EditText(this);
        input.setHint("Artist or @handle");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Credit an artist")
                .setView(input)
                .setNegativeButton(com.oru.app.R.string.cancel, null)
                .setPositiveButton("Search",
                        (dialog, which) -> searchArtists(input.getText().toString()))
                .show();
    }

    private void searchArtists(String query) {
        setBusy(true, "Searching…");
        musicRepo.searchArtists(query.trim(), new Callback<List<Artist>>() {
            @Override
            public void onSuccess(List<Artist> artists) {
                setBusy(false, null);
                if (artists == null || artists.isEmpty()) {
                    setStatus("No artist matched that name");
                    return;
                }
                showArtistChoices(artists);
            }

            @Override
            public void onFailure(Throwable error) {
                setBusy(false, UiErrors.message(ShareActivity.this, error));
            }
        });
    }

    private void showArtistChoices(List<Artist> artists) {
        List<String> labels = new ArrayList<>();
        for (Artist each : artists) {
            String name = each.stageName != null && !each.stageName.isEmpty()
                    ? each.stageName
                    : (each.handle != null ? each.handle : "Artist");
            labels.add(each.handle != null ? name + " · @" + each.handle : name);
        }
        new AlertDialog.Builder(this)
                .setTitle("Credit an artist")
                .setItems(labels.toArray(new String[0]),
                        (dialog, which) -> {
                            artist = artists.get(which);
                            binding.artistText.setText(labels.get(which));
                        })
                .setNegativeButton(com.oru.app.R.string.cancel, null)
                .show();
    }

    private void read(Uri uri, boolean isAudio) {
        setBusy(true, "Reading the file…");
        io.execute(() -> {
            try {
                PickedFile picked = PickedFile.copy(this, uri);
                runOnUiThread(() -> {
                    setBusy(false, null);
                    if (isAudio) {
                        audio = picked;
                        binding.audioText.setText(picked.name);
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
                    setBusy(false, null);
                    Toast.makeText(this, "Couldn't read that file",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void shareSong() {
        String title = text(binding.titleEditText.getText());
        if (artist == null) {
            setStatus("Choose the artist this belongs to");
            return;
        }
        if (title.isEmpty()) {
            binding.titleLayout.setError("Give it a title");
            return;
        }
        binding.titleLayout.setError(null);
        if (audio == null) {
            setStatus("Attach the audio first");
            return;
        }
        String sourceUrl = text(binding.sourceEditText.getText());
        if (!sourceUrl.startsWith("http://") && !sourceUrl.startsWith("https://")) {
            binding.sourceLayout.setError("Add the link it came from");
            return;
        }
        binding.sourceLayout.setError(null);
        List<String> genres = new ArrayList<>();
        setBusy(true, "Sharing…");
        sharingRepo.shareSong(artist.artistId(), title,
                text(binding.descriptionEditText.getText()), genres,
                sourceUrl, audio.durationMs,
                audio.file, audio.mime,
                cover != null ? cover.file : null,
                cover != null ? cover.mime : null,
                new Callback<Song>() {
                    @Override
                    public void onSuccess(Song song) {
                        finishWith("Shared — it's in review");
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        setBusy(false,
                                UiErrors.message(ShareActivity.this, error));
                    }
                });
    }

    private void shareLink() {
        String title = text(binding.linkTitleEditText.getText());
        String artistName = text(binding.linkArtistEditText.getText());
        String url = text(binding.linkUrlEditText.getText());
        if (title.isEmpty()) {
            binding.linkTitleLayout.setError("Give it a title");
            return;
        }
        binding.linkTitleLayout.setError(null);
        if (artistName.isEmpty()) {
            binding.linkArtistLayout.setError("Who made it?");
            return;
        }
        binding.linkArtistLayout.setError(null);
        if (url.isEmpty()) {
            binding.linkUrlLayout.setError("Paste the link");
            return;
        }
        binding.linkUrlLayout.setError(null);

        setBusy(true, "Sharing…");
        sharingRepo.createExternalShare(title, artistName, url,
                text(binding.linkNoteEditText.getText()),
                new Callback<ExternalShare>() {
                    @Override
                    public void onSuccess(ExternalShare share) {
                        finishWith("Shared — thanks for the tip");
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        setBusy(false,
                                UiErrors.message(ShareActivity.this, error));
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
        binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.shareButton.setEnabled(!busy);
        binding.linkButton.setEnabled(!busy);
        setStatus(message);
    }

    private void setStatus(String message) {
        binding.statusText.setText(message != null ? message : "");
        binding.statusText.setVisibility(
                message != null && !message.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String text(CharSequence value) {
        return value != null ? value.toString().trim() : "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
        binding = null;
    }
}
