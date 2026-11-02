package com.oru.app.util;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.oru.app.data.api.ApiClient;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.repository.AuthRepository;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.data.repository.ModerationRepository;
import com.oru.app.data.repository.SharingRepository;
import com.oru.app.data.session.SessionManager;

/**
 * Central place to initialise the data layer once the session manager is ready.
 * Call init() from Application.onCreate or after auth is established.
 */
public class LazySession {

    private static volatile boolean initialised = false;

    public static synchronized void init(Context context, SessionManager session) {
        if (initialised) {
            return;
        }
        ApiClient.init(context, session);

        Gson gson = new GsonBuilder().setLenient().create();
        ApiService api = ApiClient.getClient().create(ApiService.class);

        // Repositories are created lazily by feature modules as needed.
        // We expose factories to keep this class light.
        initialised = true;
    }

    public static boolean isInitialised() {
        return initialised;
    }

    /**
     * Must be called after ApiClient.init() to build the service.
     */
    public static ApiService getApiService() {
        if (!initialised) {
            throw new IllegalStateException("LazySession.init() not called");
        }
        return ApiClient.getClient().create(ApiService.class);
    }

    public static Gson getGson() {
        return new GsonBuilder().setLenient().create();
    }
}
