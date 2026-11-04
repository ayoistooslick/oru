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
 * The production host publishes one function route at /api, so a request carries
 * the path it wants in a __path query parameter: /api?__path=/api/v1/auth/login.
 * HostPathInterceptor rewrites the clean REST paths from ApiService into that
 * shape before the request leaves the device.
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
                .registerTypeAdapterFactory(new ApiResponseAdapterFactory())
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

    /** The host without a trailing slash, for building media addresses. */
    public static String origin() {
        return BASE_URL.endsWith("/")
                ? BASE_URL.substring(0, BASE_URL.length() - 1)
                : BASE_URL;
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
     * The host publishes a single route at /api, so /api/v1/... becomes
     * /api?__path=/api/v1/... before it leaves the device. The query parameter
     * must be added as a query parameter: encodedPath() percent-encodes the "?"
     * in a string that carries one, which turns the request into /api%3F__path=…
     * and the host answers 405 instead of routing it.
     */
    private static class HostPathInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            String path = request.url().encodedPath();
            if (path.startsWith("/api/v1")) {
                // Keep any query the call already carries (page, limit) and
                // append the routed path next to it.
                okhttp3.HttpUrl newUrl = request.url().newBuilder()
                        .encodedPath("/api")
                        .addQueryParameter("__path", path)
                        .build();
                return chain.proceed(request.newBuilder().url(newUrl).build());
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
            // Log the path the API will route, not the /api wrapper it travels in.
            String routed = request.url().queryParameter("__path");
            android.util.Log.d("OruApi", "→ " + request.method() + " "
                    + (routed != null ? routed : request.url().encodedPath()));
            Response response = chain.proceed(request);
            long took = System.currentTimeMillis() - start;
            android.util.Log.d("OruApi", "← " + response.code() + " " + took + "ms");
            return response;
        }
    }
}
