package com.oru.app.data.repository;

import com.google.gson.Gson;
import com.oru.app.data.api.ApiClient;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.model.*;
import com.oru.app.data.session.SessionManager;

import java.io.IOException;

import okhttp3.Call;
import com.oru.app.data.repository.Callback;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Auth operations: register, login, google login, profile, artist profile.
 */
public class AuthRepository {

    private final ApiService api;
    private final SessionManager session;
    private final Gson gson;

    public AuthRepository(ApiService api, SessionManager session, Gson gson) {
        this.api = api;
        this.session = session;
        this.gson = gson;
    }

    public void register(String email, String password, String handle,
            String displayName, Callback<User> cb) {
        RegisterRequest body = new RegisterRequest(email, password, handle,
                displayName);
        api.register(body).enqueue(new retrofit2.Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<User>> call,
                    retrofit2.Response<ApiResponse<User>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "register failed: " + response.code()));
                        return;
                    }
                    ApiResponse<User> bodyRes = response.body();
                    if (bodyRes == null || !bodyRes.isSuccess()) {
                        cb.onFailure(new IOException(
                                bodyRes != null ? bodyRes.error.message
                                        : "register failed"));
                        return;
                    }
                    User user = bodyRes.data;
                    cb.onSuccess(user);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<User>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void login(String email, String password, Callback<TokenResponse> cb) {
        LoginRequest body = new LoginRequest(email, password);
        api.login(body).enqueue(new retrofit2.Callback<ApiResponse<TokenResponse>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<TokenResponse>> call,
                    retrofit2.Response<ApiResponse<TokenResponse>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "login failed: " + response.code()));
                        return;
                    }
                    ApiResponse<TokenResponse> bodyRes = response.body();
                    if (bodyRes == null || !bodyRes.isSuccess()) {
                        cb.onFailure(new IOException(
                                bodyRes != null ? bodyRes.error.message
                                        : "login failed"));
                        return;
                    }
                    cb.onSuccess(bodyRes.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<TokenResponse>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void googleLogin(String idToken, Callback<TokenResponse> cb) {
        GoogleLoginRequest body = new GoogleLoginRequest(idToken);
        api.googleLogin(body).enqueue(new retrofit2.Callback<ApiResponse<TokenResponse>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<TokenResponse>> call,
                    retrofit2.Response<ApiResponse<TokenResponse>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "google login failed: " + response.code()));
                        return;
                    }
                    ApiResponse<TokenResponse> bodyRes = response.body();
                    if (bodyRes == null || !bodyRes.isSuccess()) {
                        cb.onFailure(new IOException(
                                bodyRes != null ? bodyRes.error.message
                                        : "google login failed"));
                        return;
                    }
                    cb.onSuccess(bodyRes.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<TokenResponse>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void getCurrentUser(Callback<User> cb) {
        api.getCurrentUser().enqueue(new retrofit2.Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<User>> call,
                    retrofit2.Response<ApiResponse<User>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "get current user failed: " + response.code()));
                        return;
                    }
                    ApiResponse<User> bodyRes = response.body();
                    if (bodyRes == null || !bodyRes.isSuccess()) {
                        cb.onFailure(new IOException(
                                bodyRes != null ? bodyRes.error.message
                                        : "get current user failed"));
                        return;
                    }
                    cb.onSuccess(bodyRes.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<User>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void saveSessionFromToken(TokenResponse token) {
        User u = token.user;
        session.saveSession(token.accessToken, u.id, u.handle, u.displayName,
                u.avatarUrl);
    }

    public void logout() {
        session.clearSession();
    }

    public void updateProfile(String displayName, String bio,
            Callback<User> cb) {
        ProfileUpdate body = new ProfileUpdate(displayName, bio);
        api.updateProfile(body).enqueue(new retrofit2.Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<User>> call,
                    retrofit2.Response<ApiResponse<User>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "update profile failed: " + response.code()));
                        return;
                    }
                    ApiResponse<User> bodyRes = response.body();
                    if (bodyRes == null || !bodyRes.isSuccess()) {
                        cb.onFailure(new IOException(
                                bodyRes != null ? bodyRes.error.message
                                        : "update profile failed"));
                        return;
                    }
                    cb.onSuccess(bodyRes.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<User>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void createArtistProfile(String stageName, Callback<Artist> cb) {
        ArtistProfileCreate body = new ArtistProfileCreate(stageName);
        api.createArtistProfile(body).enqueue(new retrofit2.Callback<ApiResponse<Artist>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Artist>> call,
                    retrofit2.Response<ApiResponse<Artist>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "create artist profile failed: " + response.code()));
                        return;
                    }
                    ApiResponse<Artist> bodyRes = response.body();
                    if (bodyRes == null || !bodyRes.isSuccess()) {
                        cb.onFailure(new IOException(
                                bodyRes != null ? bodyRes.error.message
                                        : "create artist profile failed"));
                        return;
                    }
                    cb.onSuccess(bodyRes.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Artist>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }
}
