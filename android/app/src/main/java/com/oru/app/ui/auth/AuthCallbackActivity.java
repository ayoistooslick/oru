package com.oru.app.ui.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.oru.app.R;
import com.oru.app.data.api.ApiResponse;
import com.oru.app.data.model.TokenResponse;
import com.oru.app.data.repository.AuthRepository;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.session.SessionManager;
import com.oru.app.ui.MainActivity;
import com.oru.app.util.LazySession;

/**
 * Where the browser lands after Google signs someone in.
 *
 * The site sends a code through {@code oru://auth?code=…}. The code is good for
 * one exchange, which is what keeps a link left in browser history from being
 * replayable. On success the app opens signed in; on failure the person lands
 * on the sign-in screen with the reason.
 */
public class AuthCallbackActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_callback);

        // A rotation would otherwise try the same single-use code twice.
        if (savedInstanceState != null) {
            finish();
            return;
        }

        Uri data = getIntent() != null ? getIntent().getData() : null;
        String code = data != null ? data.getQueryParameter("code") : null;
        if (code == null || code.isEmpty()) {
            fail("That sign-in link was incomplete. Try signing in again.");
            return;
        }

        SessionManager session = new SessionManager(this);
        AuthRepository repo = new AuthRepository(LazySession.getApiService(), session,
                LazySession.getGson());
        repo.exchangeHandoff(code, new Callback<TokenResponse>() {
            @Override
            public void onSuccess(TokenResponse token) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                repo.saveSessionFromToken(token);
                Intent home = new Intent(AuthCallbackActivity.this, MainActivity.class);
                home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(home);
                finish();
            }

            @Override
            public void onFailure(Throwable error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                String message = error.getMessage();
                if (error instanceof ApiResponse.ApiException && message != null
                        && !message.isEmpty()) {
                    fail(message);
                    return;
                }
                fail("Signing in did not finish. Try again.");
            }
        });
    }

    private void fail(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent auth = new Intent(this, AuthActivity.class);
        auth.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(auth);
        finish();
    }
}
