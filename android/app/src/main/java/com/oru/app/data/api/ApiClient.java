package com.oru.app.data.api;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.oru.app.data.session.SessionManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Builds the Retrofit client pointing at the ORU API.
 *
 * When running against the production host, the single function route carries the
 * real path in a __path query parameter. This client targets the production host
 * base URL directly (e.g. https://oru.freebuff.app) so that the HostPathMiddleware
 * receives each request through /api?__path=..., but we use the clean REST paths
 * from the backend. In practice the backend is served at the same base URL, so
 * requests to /api/v1/... resolve correctly.
 *
 * For local development you can override BASE_URL via an environment-like config
 * or by setting a build variant. The network security config permits localhost
 * and 10.0.2.2 (Android emulator host) for debug.
 */
public class ApiClient {

    // Production base URL (no trailing slash). Update for staging if needed.
    private static final String BASE_URL =
            "https://oru.freebuff.app/";

    // For local dev against the host dev server, switch to:
    // private static final String BASE_URL = "http://10.0.2.2:5173/";

    private static volatile Retrofit retrofit;
    private static volatile OkHttpClient okHttpClient;
    private static volatile SessionManager sessionManager;

    public static void init(Context context, SessionManager sessionManager) {
        if (ApiClient.sessionManager != null) {
            return;
        }
        ApiClient.sessionManager = sessionManager;

        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor())
                .addInterceptor(new HostPathInterceptor())
                .addInterceptor(new LoggerInterceptor())
                .build();

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    public static Retrofit getClient() {
        if (retrofit == null) {
            throw new IllegalStateException(
                    "ApiClient.init() must be called before getClient()");
        }
        return retrofit;
    }

    public static OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    /**
     * Attaches the Bearer token when a session exists.
     */
    private static class AuthInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            String token = sessionManager.getToken();
            Request.Builder builder = request.newBuilder();
            if (token != null) {
                builder.addHeader("Authorization", "Bearer " + token);
            }
            // Tell the backend we are a native app so it can tailor responses.
            builder.addHeader("X-Client", "oru-android");
            builder.addHeader("Accept", "application/json");
            return chain.proceed(builder.build());
        }
    }

    /**
     * Production single-function route rewrites /api/v1/... to /api?__path=/api/v1/...
     * so we remap the path before it leaves the device.
     */
    private static class HostPathInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            String path = request.url().encodedPath();
            // If the path already starts with /api?__path= (e.g. direct call),
            // leave it. Otherwise rewrite /api/v1/... requests.
            if (path.startsWith("/api/v1/") || path.startsWith("/api/v1")
                    || path.equals("/api")) {
                String newPath = "/api?__path=" + java.net.URLEncoder.encode(path, "UTF-8");
                okhttp3.HttpUrl newUrl = request.url().newBuilder()
                        .encodedPath(newPath)
                        .build();
                Request newRequest = request.newBuilder()
                        .url(newUrl)
                        .build();
                return chain.proceed(newRequest);
            }
            return chain.proceed(request);
        }
    }

    /**
     * Lightweight logging for development. Keep concise.
     */
    private static class LoggerInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            long start = System.currentTimeMillis();
            android.util.Log.d("OruApi", "→ " + request.method() + " "
                    + request.url().encodedPath());
            Response response = chain.proceed(request);
            long took = System.currentTimeMillis() - start;
            android.util.Log.d("OruApi", "← " + response.code() + " " + took + "ms");
            return response;
        }
    }
}
