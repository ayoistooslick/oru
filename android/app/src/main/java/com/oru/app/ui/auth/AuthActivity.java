package com.oru.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
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
 *  - Google sign-in goes through Credential Manager with the web client ID the
 *    server publishes; the button only appears when the server has it enabled.
 */
public class AuthActivity extends AppCompatActivity {

    private ActivityAuthBinding binding;
    private SessionManager session;
    private AuthRepository authRepo;
    private boolean isSignUp = false;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private String googleClientId = null;
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
                        googleClientId = config.googleClientId;
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
        binding.primaryButton.setOnClickListener(v -> submit());
        binding.secondaryButton.setOnClickListener(v -> toggleMode());
        binding.googleButton.setOnClickListener(v -> signInWithGoogle());
    }

    private void setupButtons() {
        binding.primaryButton.setOnClickListener(v -> submit());
        binding.secondaryButton.setOnClickListener(v -> toggleMode());
        binding.googleButton.setOnClickListener(v -> signInWithGoogle());
    }

    /**
     * Asks Google for an ID token through Credential Manager. The server's web
     * client ID is the audience, which is what the backend verifies.
     */
    private void signInWithGoogle() {
        if (googleClientId == null) {
            Snackbar.make(binding.getRoot(),
                    "Google sign-in is not available right now",
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        binding.progressBar.setVisibility(View.VISIBLE);
        androidx.credentials.CredentialManager manager =
                androidx.credentials.CredentialManager.create(this);
        com.google.android.libraries.identity.googleid.GetGoogleIdOption option =
                new com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setAutoSelectEnabled(false)
                        .setServerClientId(googleClientId)
                        .build();
        androidx.credentials.GetCredentialRequest request =
                new androidx.credentials.GetCredentialRequest.Builder()
                        .addCredentialOption(option)
                        .build();

        manager.getCredentialAsync(this, request, null,
                androidx.core.content.ContextCompat.getMainExecutor(this),
                new androidx.credentials.CredentialManagerCallback<androidx.credentials.GetCredentialResponse,
                        androidx.credentials.exceptions.GetCredentialException>() {
                    @Override
                    public void onResult(androidx.credentials.GetCredentialResponse result) {
                        String idToken = googleIdToken(result);
                        if (idToken == null) {
                            binding.progressBar.setVisibility(View.GONE);
                            setError("Google did not return a usable account");
                            return;
                        }
                        sendGoogleToken(idToken);
                    }

                    @Override
                    public void onError(@NonNull androidx.credentials.exceptions.GetCredentialException error) {
                        binding.progressBar.setVisibility(View.GONE);
                        // A dismissed sheet is not an error worth shouting about.
                        String message = error.getMessage();
                        if (message != null && message.contains("cancel")) {
                            return;
                        }
                        setError("Google sign-in failed. Try another way.");
                    }
                });
    }

    private String googleIdToken(androidx.credentials.GetCredentialResponse result) {
        try {
            com.google.android.libraries.identity.googleid.GoogleIdTokenCredential credential =
                    com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
                            .createFrom(result.getCredential().getData());
            return credential.getIdToken();
        } catch (RuntimeException error) {
            // A credential that is not a Google ID token is not usable here.
            return null;
        }
    }

    private void sendGoogleToken(String idToken) {
        if (authRepo == null) {
            authRepo = new AuthRepository(LazySession.getApiService(), session,
                    LazySession.getGson());
        }
        authRepo.googleLogin(idToken, new Callback<TokenResponse>() {
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
