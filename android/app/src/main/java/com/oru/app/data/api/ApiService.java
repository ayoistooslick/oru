package com.oru.app.data.api;

import com.oru.app.data.model.*;

import java.util.List;
import java.util.Map;

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

    @GET("api/v1/auth/config")
    Call<AuthConfig> authConfig();

    @GET("api/v1/auth/me")
    Call<UserEnvelope> getCurrentUser();

    @PATCH("api/v1/listeners/me")
    Call<ApiResponse<User>> updateProfile(@Body ProfileUpdate body);

    @POST("api/v1/listeners/me/avatar")
    Call<ApiResponse<User>> uploadAvatar(@Body Map<String, String> body);

    @DELETE("api/v1/listeners/me/avatar")
    Call<ApiResponse<User>> deleteAvatar();

    @GET("api/v1/users/{userId}/avatar")
    Call<android.graphics.Bitmap> getUserAvatar(
            @Path("userId") int userId); // image; handled specially

    // ---- Artists ----

    @POST("api/v1/artists/me")
    Call<ApiResponse<Artist>> createArtistProfile(@Body ArtistProfileCreate body);

    @POST("api/v1/artists/me/banner")
    Call<ApiResponse<Artist>> uploadBanner(@Body Map<String, String> body);

    @DELETE("api/v1/artists/me/banner")
    Call<ApiResponse<Artist>> deleteBanner();

    @GET("api/v1/artists/{artistId}/banner")
    Call<android.graphics.Bitmap> getArtistBanner(
            @Path("artistId") int artistId);

    @GET("api/v1/artists")
    Call<ApiResponse<List<Artist>>> listArtists();

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

    @POST("api/v1/artists/me/songs")
    Call<ApiResponse<Song>> createSong(@Body SongCreate body);

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

    @POST("api/v1/playlists/{playlistId}/songs/{songId}")
    Call<ApiResponse<PlaylistSong>> addSongToPlaylist(
            @Path("playlistId") int playlistId,
            @Path("songId") int songId);

    @DELETE("api/v1/playlists/{playlistId}/songs/{songId}")
    Call<ApiResponse<Void>> removeSongFromPlaylist(
            @Path("playlistId") int playlistId,
            @Path("songId") int songId);

    // The following feed is made of songs.
    @GET("api/v1/feed/following")
    Call<ApiResponse<List<Song>>> feedFollowing();

    // ---- Sharing ----

    @POST("api/v1/songs/share")
    Call<ApiResponse<ShareLink>> createShareLink(@Body ShareCreate body);

    @GET("api/v1/shared/songs")
    Call<ApiResponse<List<Song>>> listSharedSongs();

    @DELETE("api/v1/shared/songs/{songId}")
    Call<ApiResponse<Void>> revokeShare(@Path("songId") int songId);

    @GET("api/v1/songs/pending-shares")
    Call<ApiResponse<List<Song>>> listPendingShares();

    @POST("api/v1/shared/external")
    Call<ApiResponse<ExternalShare>> createExternalShare(@Body ExternalShareCreate body);

    @GET("api/v1/shared/external")
    Call<ApiResponse<List<ExternalShare>>> listExternalShares();

    @DELETE("api/v1/shared/external/{shareId}")
    Call<ApiResponse<Void>> revokeExternalShare(@Path("shareId") int shareId);

    @POST("api/v1/songs/{songId}/share-decision")
    Call<ApiResponse<Void>> shareDecision(
            @Path("songId") int songId, @Body ShareDecision body);

    // ---- Moderation ----

    @POST("api/v1/reports")
    Call<ApiResponse<Report>> createReport(@Body ReportCreate body);

    @GET("api/v1/moderation/reports")
    Call<ApiResponse<List<Report>>> listReports();

    @POST("api/v1/moderation/reports/{reportId}/decision")
    Call<ApiResponse<Void>> moderationDecision(
            @Path("reportId") int reportId, @Body ModerationDecision body);
}
