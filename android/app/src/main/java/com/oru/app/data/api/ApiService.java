package com.oru.app.data.api;

import com.oru.app.data.model.*;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Retrofit interface covering the ORU API surface. Path constants match
 * app/api/*.py route signatures.
 */
public interface ApiService {

    // ---- Auth ----

    // The API answers auth calls with a bare object ({"user": .., "access_token": ..}),
    // not the {"data": ..} envelope, so these are parsed as-is.
    @POST("api/v1/auth/register")
    Call<TokenResponse> register(@Body RegisterRequest body);

    @POST("api/v1/auth/login")
    Call<TokenResponse> login(@Body LoginRequest body);

    @POST("api/v1/auth/google")
    Call<TokenResponse> googleLogin(@Body GoogleLoginRequest body);

    // Google sign-in finishes in the browser, which hands the app a one-time
    // code through oru://auth. This trades that code for the session.
    @POST("api/v1/auth/handoff/exchange")
    Call<TokenResponse> exchangeHandoff(@Body HandoffRequest body);

    @GET("api/v1/auth/config")
    Call<AuthConfig> authConfig();

    @GET("api/v1/auth/me")
    Call<UserEnvelope> getCurrentUser();

    @PATCH("api/v1/listeners/me")
    Call<ApiResponse<User>> updateProfile(@Body ProfileUpdate body);

    // Pictures arrive as a file part named "file". The name belongs in the
    // MultipartBody.Part itself (see Uploads.file); naming it here as well makes
    // Retrofit refuse the call with "@Part parameters using the
    // MultipartBody.Part must not include a part name in the annotation".
    @Multipart
    @POST("api/v1/listeners/me/avatar")
    Call<ApiResponse<User>> uploadAvatar(@Part MultipartBody.Part file);

    @DELETE("api/v1/listeners/me/avatar")
    Call<ApiResponse<User>> deleteAvatar();

    @GET("api/v1/users/{userId}/avatar")
    Call<android.graphics.Bitmap> getUserAvatar(
            @Path("userId") int userId); // image; handled specially

    // The wide banner behind a profile. Every account has one, not just artists.
    @Multipart
    @POST("api/v1/listeners/me/banner")
    Call<ApiResponse<User>> uploadBanner(@Part MultipartBody.Part file);

    @DELETE("api/v1/listeners/me/banner")
    Call<ApiResponse<User>> deleteBanner();

    // ---- Artists ----

    @POST("api/v1/artists/me")
    Call<ApiResponse<Artist>> createArtistProfile(@Body ArtistProfileCreate body);

    @GET("api/v1/artists")
    Call<ApiResponse<List<Artist>>> listArtists(
            @Query("q") String query,
            @Query("limit") int limit);

    @GET("api/v1/artists/{artistId}")
    Call<ApiResponse<Artist>> getArtist(@Path("artistId") int artistId);

    @GET("api/v1/artists/{artistId}/songs")
    Call<ApiResponse<List<Song>>> getArtistSongs(
            @Path("artistId") int artistId,
            @Query("limit") int limit,
            @Query("offset") int offset);

    @GET("api/v1/artists/{artistId}/releases")
    Call<ApiResponse<List<Release>>> getArtistReleases(
            @Path("artistId") int artistId,
            @Query("limit") int limit,
            @Query("offset") int offset);

    // A feature credit is reported as the song it appears on.
    @GET("api/v1/artists/{artistId}/appears-on")
    Call<ApiResponse<List<Song>>> getArtistAppearsOn(
            @Path("artistId") int artistId);

    @GET("api/v1/artists/me/stats")
    Call<ApiResponse<ArtistStats>> getArtistStats();

    @GET("api/v1/artists/{artistId}/posts")
    Call<ApiResponse<List<ArtistPost>>> getArtistPosts(
            @Path("artistId") int artistId);

    @GET("api/v1/artists/me/posts")
    Call<ApiResponse<List<ArtistPost>>> getMyPosts();

    @POST("api/v1/artists/me/posts")
    Call<ApiResponse<ArtistPost>> createArtistPost(@Body ArtistPostCreate body);

    // ---- Songs ----

    @GET("api/v1/songs")
    Call<ApiResponse<List<Song>>> listSongs(
            @Query("q") String q,
            @Query("limit") int limit,
            @Query("offset") int offset);

    @GET("api/v1/songs/{songId}")
    Call<ApiResponse<Song>> getSong(@Path("songId") int songId);

    @GET("api/v1/songs/{songId}/audio")
    Call<ResponseBody> getSongAudio(@Path("songId") int songId);

    @GET("api/v1/songs/{songId}/cover")
    Call<ResponseBody> getSongCover(@Path("songId") int songId);

    // Uploads are multipart forms: the audio is a file, the rest are fields.
    @Multipart
    @POST("api/v1/artists/me/songs")
    Call<ApiResponse<Song>> createSong(
            @Part("title") RequestBody title,
            @Part("description") RequestBody description,
            @Part("genres") List<RequestBody> genres,
            @Part("featured") List<RequestBody> featured,
            @Part("duration_ms") RequestBody durationMs,
            @Part MultipartBody.Part audio,
            @Part MultipartBody.Part cover);

    @POST("api/v1/artists/me/songs/{songId}/publish")
    Call<ApiResponse<Song>> publishSong(@Path("songId") int songId);

    @POST("api/v1/artists/me/songs/{songId}/unpublish")
    Call<ApiResponse<Song>> unpublishSong(@Path("songId") int songId);

    @GET("api/v1/artists/me/songs")
    Call<ApiResponse<List<Song>>> getMySongs();

    @DELETE("api/v1/artists/me/songs/{songId}")
    Call<ApiResponse<Void>> deleteSong(@Path("songId") int songId);

    @POST("api/v1/songs/{songId}/listen")
    Call<ApiResponse<ListenRecord>> recordListen(
            @Path("songId") int songId, @Body ListeningCreate body);

    @GET("api/v1/discover")
    Call<ApiResponse<DiscoverResult>> discover();

    @GET("api/v1/genres")
    Call<ApiResponse<List<Genre>>> listGenres();

    @GET("api/v1/rankings/weekly")
    Call<ApiResponse<List<RankingEntry>>> weeklyRankings();

    // ---- Releases ----

    @POST("api/v1/artists/me/releases")
    Call<ApiResponse<Release>> createRelease(@Body ReleaseCreate body);

    @POST("api/v1/artists/me/releases/{releaseId}/publish")
    Call<ApiResponse<Release>> publishRelease(@Path("releaseId") int releaseId);

    @GET("api/v1/artists/me/releases")
    Call<ApiResponse<List<Release>>> getMyReleases();

    @GET("api/v1/releases/{releaseId}")
    Call<ApiResponse<Release>> getRelease(@Path("releaseId") int releaseId);


    // ---- Community ----

    @POST("api/v1/artists/{artistId}/follow")
    Call<ApiResponse<FollowResult>> followArtist(@Path("artistId") int artistId);

    @DELETE("api/v1/artists/{artistId}/follow")
    Call<ApiResponse<FollowResult>> unfollowArtist(@Path("artistId") int artistId);

    @POST("api/v1/songs/{songId}/likes")
    Call<ApiResponse<LikeResult>> likeSong(@Path("songId") int songId);

    @DELETE("api/v1/songs/{songId}/likes")
    Call<ApiResponse<LikeResult>> unlikeSong(@Path("songId") int songId);

    @GET("api/v1/listeners/me/likes")
    Call<ApiResponse<List<Song>>> getMyLikes();

    // Recently played, newest first — the home screen's "Pick up where you left off".
    @GET("api/v1/listeners/me/history")
    Call<ApiResponse<List<Song>>> getListeningHistory(
            @Query("limit") int limit);

    @GET("api/v1/listeners/me/following")
    Call<ApiResponse<List<Artist>>> getFollowing();

    @POST("api/v1/playlists")
    Call<ApiResponse<Playlist>> createPlaylist(@Body PlaylistCreate body);

    @GET("api/v1/playlists")
    Call<ApiResponse<List<Playlist>>> listPlaylists();

    @GET("api/v1/playlists/{playlistId}")
    Call<ApiResponse<Playlist>> getPlaylist(@Path("playlistId") int playlistId);

    @PATCH("api/v1/playlists/{playlistId}")
    Call<ApiResponse<Playlist>> updatePlaylist(
            @Path("playlistId") int playlistId, @Body PlaylistUpdate body);

    @DELETE("api/v1/playlists/{playlistId}")
    Call<ApiResponse<Void>> deletePlaylist(
            @Path("playlistId") int playlistId);

    @POST("api/v1/playlists/{playlistId}/songs/{songId}")
    Call<ApiResponse<PlaylistSong>> addSongToPlaylist(
            @Path("playlistId") int playlistId,
            @Path("songId") int songId);

    @DELETE("api/v1/playlists/{playlistId}/songs/{songId}")
    Call<ApiResponse<Void>> removeSongFromPlaylist(
            @Path("playlistId") int playlistId,
            @Path("songId") int songId);

    // A playlist's own square cover and its wide banner.
    @Multipart
    @POST("api/v1/playlists/{playlistId}/cover")
    Call<ApiResponse<Playlist>> uploadPlaylistCover(
            @Path("playlistId") int playlistId,
            @Part MultipartBody.Part file);

    @DELETE("api/v1/playlists/{playlistId}/cover")
    Call<ApiResponse<Playlist>> deletePlaylistCover(
            @Path("playlistId") int playlistId);

    @Multipart
    @POST("api/v1/playlists/{playlistId}/banner")
    Call<ApiResponse<Playlist>> uploadPlaylistBanner(
            @Path("playlistId") int playlistId,
            @Part MultipartBody.Part file);

    @DELETE("api/v1/playlists/{playlistId}/banner")
    Call<ApiResponse<Playlist>> deletePlaylistBanner(
            @Path("playlistId") int playlistId);

    // The following feed is made of songs.
    @GET("api/v1/feed/following")
    Call<ApiResponse<List<Song>>> feedFollowing();

    // ---- Sharing ----

    @Multipart
    @POST("api/v1/songs/share")
    Call<ApiResponse<Song>> shareSong(
            @Part("artist_id") RequestBody artistId,
            @Part("title") RequestBody title,
            @Part("description") RequestBody description,
            @Part("genres") List<RequestBody> genres,
            @Part("source_url") RequestBody sourceUrl,
            @Part("duration_ms") RequestBody durationMs,
            @Part MultipartBody.Part audio,
            @Part MultipartBody.Part cover);

    @GET("api/v1/shared/songs")
    Call<ApiResponse<List<Song>>> listSharedSongs();

    @DELETE("api/v1/shared/songs/{songId}")
    Call<ApiResponse<Void>> revokeShare(@Path("songId") int songId);

    @GET("api/v1/songs/pending-shares")
    Call<ApiResponse<List<Song>>> listPendingShares();

    @FormUrlEncoded
    @POST("api/v1/shared/external")
    Call<ApiResponse<ExternalShare>> createExternalShare(
            @Field("title") String title,
            @Field("artist_name") String artistName,
            @Field("url") String url,
            @Field("note") String note);

    @GET("api/v1/shared/external")
    Call<ApiResponse<List<ExternalShare>>> listExternalShares();

    @DELETE("api/v1/shared/external/{shareId}")
    Call<ApiResponse<Void>> revokeExternalShare(@Path("shareId") int shareId);

    @POST("api/v1/songs/{songId}/share-decision")
    Call<ApiResponse<Void>> shareDecision(
            @Path("songId") int songId, @Body ShareDecision body);

    // ---- Notifications ----
    // These answer with bare objects ({"items": .., "unread_count": ..}), not
    // the {"data": ..} envelope, so they are read with enqueuePlain.

    @GET("api/v1/notifications")
    Call<com.oru.app.data.model.NotificationsPage> listNotifications(
            @Query("limit") int limit);

    @GET("api/v1/notifications/unread")
    Call<com.oru.app.data.model.NotificationsPage.Unread> unreadNotifications();

    @POST("api/v1/notifications/{notificationId}/read")
    Call<com.oru.app.data.model.NotificationsPage.Read> readNotification(
            @Path("notificationId") int notificationId);

    @POST("api/v1/notifications/read-all")
    Call<com.oru.app.data.model.NotificationsPage.Marked> readAllNotifications();

    @PATCH("api/v1/notifications/settings")
    Call<com.oru.app.data.model.NotificationPreference> setDropNotifications(
            @Body com.oru.app.data.model.NotificationPreference body);

    // ---- Moderation ----

    @POST("api/v1/reports")
    Call<ApiResponse<Report>> createReport(@Body ReportCreate body);

    @GET("api/v1/moderation/reports")
    Call<ApiResponse<List<Report>>> listReports(@Query("status") String status);

    @POST("api/v1/moderation/reports/{reportId}/decision")
    Call<ApiResponse<Void>> moderationDecision(
            @Path("reportId") int reportId, @Body ModerationDecision body);
}
