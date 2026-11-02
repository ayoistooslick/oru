package com.oru.app.ui.search;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.oru.app.R;
import com.oru.app.databinding.ActivitySearchBinding;

/**
 * Standalone search destination used for deep links (e.g. from the web share
 * handoff) and when the user taps into search from outside the main flow.
 * Reuses SearchFragment for the UI.
 */
public class SearchActivity extends AppCompatActivity {

    private ActivitySearchBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // SearchFragment handles the rest; it reads query from intent if present.
        com.oru.app.ui.search.SearchFragment fragment =
                (com.oru.app.ui.search.SearchFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.searchFragment);
        if (fragment != null && getIntent() != null) {
            String query = getIntent().getStringExtra("query");
            if (query != null && !query.isEmpty()) {
                // Fragment exposes a search method; inject when available.
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
