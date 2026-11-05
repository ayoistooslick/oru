package com.oru.app.data.repository;

import com.google.gson.Gson;
import com.oru.app.data.api.ApiCall;
import com.oru.app.data.api.ApiClient;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.api.Uploads;
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

    /**
     * Non-2xx replies carry the API's own error envelope, so keep its message.
     * A bare IOException here made the auth screen report every failure as a
     * connection problem, a rejected password included.
     */
    private Throwable failure(Response<?> response) {
        String message = null;
        String code = "http_" + response.code();
        ResponseBody errorBody = response.errorBody();
        if (errorBody != null) {
            try {
                ApiResponse<?> parsed = gson.fromJson(errorBody.string(), ApiResponse.class);
                if (parsed != null && parsed.error != null) {
                    message = parsed.error.message;
                    if (parsed.error.code != null) {
                        code = parsed.error.code;
                    }
                    // A validation failure explains itself per field, which is
                    // more use to someone filling in the sign-up form.
                    if (parsed.error.details != null && !parsed.error.details.isEmpty()
                            && parsed.error.details.get(0).message != null) {
                        message = parsed.error.details.get(0).message;
                    }
                }
            } catch (Exception ignored) {
                // Fall back to the status code below.
            }
        }
        if (message == null || message.isEmpty()) {
            message = "Server error (" + response.code() + ")";
        }
        return new ApiResponse.ApiException(code, message);
    }

    private Throwable emptyReply() {
        return new ApiResponse.ApiException("empty_response", "Server error (empty reply)");
    }

    /** A 201 from the sign-up call already carries a session token. */
    public void register(String email, String password, String handle,
            String displayName, Callback<TokenResponse> cb) {
        RegisterRequest body = new RegisterRequest(email, password, handle,
                displayName);
        api.register(body).enqueue(new retrofit2.Callback<TokenResponse>() {
            @Override
            public void onResponse(retrofit2.Call<TokenResponse> call,
                    retrofit2.Response<TokenResponse> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(failure(response));
                        return;
                    }
                    TokenResponse token = response.body();
                    if (token == null || token.accessToken == null) {
                        cb.onFailure(emptyReply());
                        return;
                    }
                    cb.onSuccess(token);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<TokenResponse> call, Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void login(String email, String password, Callback<TokenResponse> cb) {
        LoginRequest body = new LoginRequest(email, password);
        api.login(body).enqueue(new retrofit2.Callback<TokenResponse>() {
            @Override
            public void onResponse(retrofit2.Call<TokenResponse> call,
                    retrofit2.Response<TokenResponse> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(failure(response));
                        return;
                    }
                    TokenResponse token = response.body();
                    if (token == null || token.accessToken == null) {
                        cb.onFailure(emptyReply());
                        return;
                    }
                    cb.onSuccess(token);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<TokenResponse> call, Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void googleLogin(String idToken, Callback<TokenResponse> cb) {
        GoogleLoginRequest body = new GoogleLoginRequest(idToken);
        api.googleLogin(body).enqueue(new retrofit2.Callback<TokenResponse>() {
            @Override
            public void onResponse(retrofit2.Call<TokenResponse> call,
                    retrofit2.Response<TokenResponse> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(failure(response));
                        return;
                    }
                    TokenResponse token = response.body();
                    if (token == null || token.accessToken == null) {
                        cb.onFailure(emptyReply());
                        return;
                    }
                    cb.onSuccess(token);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<TokenResponse> call, Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void getCurrentUser(Callback<User> cb) {
        api.getCurrentUser().enqueue(new retrofit2.Callback<UserEnvelope>() {
            @Override
            public void onResponse(retrofit2.Call<UserEnvelope> call,
                    retrofit2.Response<UserEnvelope> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(failure(response));
                        return;
                    }
                    UserEnvelope body = response.body();
                    if (body == null || body.user == null) {
                        cb.onFailure(emptyReply());
                        return;
                    }
                    cb.onSuccess(body.user);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<UserEnvelope> call, Throwable t) {
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

    /** Replaces the profile picture; the API takes it as a file part. */
    public void uploadAvatar(java.io.File file, String mime, Callback<User> cb) {
        ApiCall.enqueue(api.uploadAvatar(
                Uploads.file("file", file.getName(), mime, file)),
                gson, "Couldn't upload that picture", cb);
    }

    public void uploadBanner(java.io.File file, String mime,
            Callback<Artist> cb) {
        ApiCall.enqueue(api.uploadBanner(
                Uploads.file("file", file.getName(), mime, file)),
                gson, "Couldn't upload that banner", cb);
    }

    /** Drops the profile picture, bringing back the initials. */
    public void deleteAvatar(Callback<User> cb) {
        ApiCall.enqueue(api.deleteAvatar(), gson,
                "Couldn't remove your picture", cb);
    }

    public void deleteBanner(Callback<Artist> cb) {
        ApiCall.enqueue(api.deleteBanner(), gson,
                "Couldn't remove that banner", cb);
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
