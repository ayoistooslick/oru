package com.oru.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.oru.app.R;
import com.oru.app.data.model.ApiResponse;
import com.oru.app.data.model.TokenResponse;
import com.oru.app.data.repository.AuthRepository;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.session.SessionManager;
import com.oru.app.databinding.ActivityAuthBinding;
import com.oru.app.ui.MainActivity;
import com.oru.app.util.LazySession;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sign-in / sign-up screen. The flow:
 *  - When signed out, show sign-in form.
 *  - Tapping "No account? Sign up" swaps to the registration form
 *    (adds handle + display name fields).
 *  - Google leaves for the site in a browser, comes back through oru://auth
 *    and finishes in {@link AuthCallbackActivity}.
 */
public class AuthActivity extends AppCompatActivity {

    private ActivityAuthBinding binding;
    private SessionManager session;
    private AuthRepository authRepo;
    private boolean isSignUp = false;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private boolean googleAvailable = false;
    private int minPasswordLength = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        session = new SessionManager(this);

        // If already signed in (shouldn't happen on this activity), go home.
        if (session.isSignedIn()) {
            goHome();
            return;
        }

        setupFields();
        setupButtons();
        updateUiForMode();
        loadConfig();
    }

    /** The server decides whether Google sign-in is offered at all. */
    private void loadConfig() {
        binding.googleButton.setVisibility(View.GONE);
        if (authRepo == null) {
            authRepo = new AuthRepository(LazySession.getApiService(), session,
                    LazySession.getGson());
        }
        authRepo.getAuthConfig(new Callback<com.oru.app.data.model.AuthConfig>() {
            @Override
            public void onSuccess(com.oru.app.data.model.AuthConfig config) {
                runOnUiThread(() -> {
                    if (config == null) {
                        return;
                    }
                    if (config.minPasswordLength > 0) {
                        minPasswordLength = config.minPasswordLength;
                    }
                    if (config.googleEnabled && config.googleClientId != null
                            && !config.googleClientId.isEmpty()) {
                        googleAvailable = true;
                        binding.googleButton.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onFailure(Throwable error) {
                // Email and password still work without the config.
                runOnUiThread(() -> binding.googleButton.setVisibility(View.GONE));
            }
        });
    }

    private void setupFields() {
        binding.emailEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start,
                    int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start,
                    int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                clearError();
            }
        });
        binding.passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start,
                    int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start,
                    int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                clearError();
            }
        });
        if (binding.handleEditText != null) {
            binding.handleEditText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start,
                        int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start,
                        int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    clearError();
                }
            });
        }
        if (binding.displayNameEditText != null) {
            binding.displayNameEditText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start,
                        int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start,
                        int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    clearError();
                }
            });
        }
    }

    private void setupButtons() {
        binding.primaryButton.setOnClickListener(v -> submit());
        binding.secondaryButton.setOnClickListener(v -> toggleMode());
        binding.googleButton.setOnClickListener(v -> signInWithGoogle());
    }

    /**
     * Google only signs people in inside a browser, so the button leaves for
     * ORU's own page. That page finishes the sign-in and returns through
     * oru://auth with a one-time code, which AuthCallbackActivity trades for
     * the session.
     */
    private void signInWithGoogle() {
        if (!googleAvailable) {
            Snackbar.make(binding.getRoot(),
                    "Google sign-in is not available right now",
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        clearError();
        String url = com.oru.app.data.api.ApiClient.origin()
                + "/auth?mode=signin&client=android";
        Intent browser = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(browser);
            Snackbar.make(binding.getRoot(),
                    "Finish signing in with Google in the browser, then come back to ORU.",
                    Snackbar.LENGTH_LONG).show();
        } catch (android.content.ActivityNotFoundException error) {
            setError("This phone has no browser to finish Google sign-in. "
                    + "Use your email and password instead.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from the browser: the callback activity has saved the
        // session, so this screen is no longer needed.
        if (session != null && session.isSignedIn() && !isFinishing() && binding != null) {
            goHome();
        }
    }

    private void toggleMode() {
        isSignUp = !isSignUp;
        updateUiForMode();
    }

    private void updateUiForMode() {
        if (isSignUp) {
            // Registration form.
            binding.authTitle.setText(getString(R.string.sign_up));
            binding.primaryButton.setText(getString(R.string.sign_up));
            binding.secondaryButton.setText(getString(R.string.have_account));
            showField(binding.handleLayout, binding.handleEditText);
            showField(binding.displayNameLayout, binding.displayNameEditText);
        } else {
            // Sign-in form.
            binding.authTitle.setText(getString(R.string.sign_in));
            binding.primaryButton.setText(getString(R.string.sign_in));
            binding.secondaryButton.setText(getString(R.string.no_account));
            hideField(binding.handleLayout, binding.handleEditText);
            hideField(binding.displayNameLayout, binding.displayNameEditText);
        }
    }

    private void showField(View layout, View editText) {
        layout.setVisibility(View.VISIBLE);
        editText.setVisibility(View.VISIBLE);
    }

    private void hideField(View layout, View editText) {
        layout.setVisibility(View.GONE);
        if (editText != null) editText.setVisibility(View.GONE);
    }

    private void clearError() {
        binding.errorText.setVisibility(View.GONE);
        binding.emailLayout.setError(null);
        binding.passwordLayout.setError(null);
        if (binding.handleLayout != null) binding.handleLayout.setError(null);
        if (binding.displayNameLayout != null) binding.displayNameLayout.setError(null);
    }

    private void setError(CharSequence message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private void submit() {
        if (busy.get()) return;
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString();

        if (email == null || !email.matches("[^@]+@[^@]+\\.[^@]+")) {
            binding.emailLayout.setError("Enter a valid email");
            return;
        }
        if (password.length() < 1) {
            binding.passwordLayout.setError("Enter your password");
            return;
        }
        // The API refuses a shorter password on sign-up (min_length=10), so say
        // so here rather than after a round trip.
        if (isSignUp && password.length() < minPasswordLength) {
            binding.passwordLayout.setError(
                    "Use at least " + minPasswordLength + " characters");
            return;
        }

        busy.set(true);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.primaryButton.setEnabled(false);

        if (isSignUp) {
            String handle = binding.handleEditText.getText().toString().trim();
            String displayName = binding.displayNameEditText.getText().toString().trim();
            doRegister(email, password, handle, displayName);
        } else {
            doLogin(email, password);
        }
    }

    private void doLogin(String email, String password) {
        if (authRepo == null) {
            authRepo = new AuthRepository(
                    LazySession.getApiService(),
                    session,
                    LazySession.getGson());
        }
        authRepo.login(email, password, new Callback<TokenResponse>() {
            @Override
            public void onSuccess(TokenResponse token) {
                finishAuth(token);
            }

            @Override
            public void onFailure(Throwable error) {
                onAuthFailure(error);
            }
        });
    }

    private void doRegister(String email, String password, String handle,
            String displayName) {
        if (authRepo == null) {
            authRepo = new AuthRepository(
                    LazySession.getApiService(),
                    session,
                    LazySession.getGson());
        }
        authRepo.register(email, password, handle, displayName,
                new Callback<TokenResponse>() {
                    @Override
                    public void onSuccess(TokenResponse token) {
                        // Sign-up answers with a session token, so there is no
                        // second trip through the sign-in form.
                        finishAuth(token);
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        onAuthFailure(error);
                    }
                });
    }

    private void finishAuth(TokenResponse token) {
        authRepo.saveSessionFromToken(token);
        binding.progressBar.setVisibility(View.GONE);
        binding.primaryButton.setEnabled(true);
        busy.set(false);
        goHome();
    }

    private void onAuthFailure(Throwable error) {
        binding.progressBar.setVisibility(View.GONE);
        binding.primaryButton.setEnabled(true);
        busy.set(false);
        setError(describe(error));
    }

    private String describe(Throwable error) {
        // The repository turns API replies into an ApiException that carries the
        // backend's own wording, so a rejected password says so instead of
        // blaming the connection.
        if (error instanceof ApiResponse.ApiException) {
            String message = error.getMessage();
            if (message != null && !message.isEmpty()) {
                return message;
            }
        }
        if (error instanceof java.io.IOException) {
            return getString(R.string.error_network);
        }
        return getString(R.string.error_unknown);
    }

    private void goHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
