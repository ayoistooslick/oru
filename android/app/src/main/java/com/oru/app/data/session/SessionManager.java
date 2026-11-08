package com.oru.app.data.session;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Persists the signed-in user session (JWT bearer token + user id) in
 * SharedPreferences using EncryptedSharedPreferences-style protection at rest.
 * For simplicity we use app-private SharedPreferences (not world-readable).
 */
public class SessionManager {

    private static final String PREFS = "oru_session";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_HANDLE = "handle";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_AVATAR = "avatar_url";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, int userId, String handle,
            String displayName, @Nullable String avatarUrl) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putInt(KEY_USER_ID, userId)
                .putString(KEY_HANDLE, handle)
                .putString(KEY_DISPLAY_NAME, displayName)
                .putString(KEY_AVATAR, avatarUrl)
                .apply();
    }

    /** Keeps the cached name and face in step after an edit or a new picture. */
    public void updateIdentity(@Nullable String displayName,
            @Nullable String avatarUrl) {
        prefs.edit()
                .putString(KEY_DISPLAY_NAME, displayName)
                .putString(KEY_AVATAR, avatarUrl)
                .apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    public boolean isSignedIn() {
        return prefs.getString(KEY_TOKEN, null) != null;
    }

    @Nullable
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    @Nullable
    public String getHandle() {
        return prefs.getString(KEY_HANDLE, null);
    }

    @Nullable
    public String getDisplayName() {
        return prefs.getString(KEY_DISPLAY_NAME, null);
    }

    @Nullable
    public String getAvatarUrl() {
        return prefs.getString(KEY_AVATAR, null);
    }
}
