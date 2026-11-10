export type Role = "listener" | "artist" | "admin";

export interface User {
  id: number;
  email: string;
  handle: string;
  display_name: string;
  bio: string;
  role: Role;
  created_at: string;
  avatar_url: string | null;
  /** Artists only: the wide picture behind their name. */
  banner_url: string | null;
  /** New-drop notices from the artists they follow. Off only if they choose. */
  notify_new_drops?: boolean;
}

export interface AuthResponse {
  user: User;
  access_token: string;
  token_type: "bearer";
}

export type SongStatus = "draft" | "pending" | "published" | "hidden";

export interface SongSharer {
  handle: string;
  display_name: string;
}

export interface SongFeature {
  artist_id: number;
  handle: string;
  name: string;
}

export interface Song {
  id: number;
  artist_id: number;
  artist_handle: string;
  artist_name: string;
  title: string;
  description: string;
  genres: string[];
  status: SongStatus;
  /** Length of the audio, reported by whoever uploaded it. */
  duration_ms: number | null;
  likes_count: number;
  /** Plays long enough to count toward discovery. */
  plays_count: number;
  /** Whether the signed-in listener has liked this song. */
  is_liked: boolean;
  created_at: string;
  published_at: string | null;
  audio_url: string | null;
  cover_url: string | null;
  source_url: string | null;
  artist_avatar_url?: string | null;
  featuring?: SongFeature[];
  shared_by: SongSharer | null;
}

export interface Artist {
  user_id: number;
  handle: string;
  stage_name: string;
  bio: string;
  avatar_url?: string | null;
  banner_url?: string | null;
  published_song_count?: number;
  follower_count?: number;
  plays_count?: number;
  likes_count?: number;
  /** The signed-in listener follows this artist. */
  is_followed?: boolean;
}

export interface Genre {
  name: string;
  song_count: number;
}

export interface Release {
  id: number;
  artist_id: number;
  title: string;
  kind: "single" | "ep" | "album";
  status?: "draft" | "published";
  published_at: string | null;
  song_ids: number[];
  songs?: Song[];
  song_count?: number;
  /** The first track's artwork; a release has none of its own. */
  cover_url?: string | null;
}

export interface ReleaseDetail {
  release: {
    id: number;
    artist_id: number;
    title: string;
    kind: "single" | "ep" | "album";
    published_at: string | null;
    songs: Song[];
  };
}

export interface Playlist {
  id: number;
  title: string;
  is_public: boolean;
  owner_id: number;
  song_count?: number;
  created_at?: string;
  cover_url?: string | null;
  song_ids?: number[];
  owner?: { handle: string; display_name: string } | null;
  songs?: (Song & { position: number })[];
}

export interface ArtistPost {
  id: number;
  artist_id: number;
  body: string;
  is_hidden?: boolean;
  created_at: string;
}

export interface ArtistStats {
  artist_id: number;
  published_songs: number;
  likes: number;
  qualified_listens: number;
  weekly_plays?: number;
  weekly_likes?: number;
  weekly_top_song?: { id: number; title: string; plays: number } | null;
  window_days?: number;
}

export interface WeeklyRanking {
  items: { rank: number; plays: number; song: Song }[];
  window_days: number;
}

export interface Report {
  id: number;
  reporter_id: number;
  target_type: "song" | "artist" | "post";
  target_id: number;
  reason: string;
  details: string;
  status: "open" | "reviewed" | "actioned";
  created_at: string;
}

export interface DropNotification {
  id: number;
  kind: "new_song" | "new_release";
  created_at: string;
  read_at: string | null;
  artist: { id: number; name: string; handle: string; avatar_url: string | null } | null;
  song: { id: number; title: string; cover_url: string | null; is_available: boolean } | null;
  release: { id: number; title: string; kind: string; cover_url: string | null; is_available: boolean } | null;
  /** Where the row goes, or null when the song has since come down. */
  href: string | null;
}

export interface NotificationsPage {
  items: DropNotification[];
  unread_count: number;
  limit: number;
  offset: number;
}

export interface Paginated<T> {
  items: T[];
  limit: number;
  offset: number;
}
