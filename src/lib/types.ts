export type Role = "listener" | "artist" | "admin";

export interface User {
  id: number;
  email: string;
  handle: string;
  display_name: string;
  bio: string;
  role: Role;
  created_at: string;
}

export interface AuthResponse {
  user: User;
  access_token: string;
  token_type: "bearer";
}

export type SongStatus = "draft" | "published" | "hidden";

export interface Song {
  id: number;
  artist_id: number;
  artist_handle: string;
  artist_name: string;
  title: string;
  description: string;
  genres: string[];
  status: SongStatus;
  created_at: string;
  published_at: string | null;
  audio_url: string | null;
  cover_url: string | null;
}

export interface Artist {
  user_id: number;
  handle: string;
  stage_name: string;
  bio: string;
  published_song_count?: number;
  follower_count?: number;
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

export interface Paginated<T> {
  items: T[];
  limit: number;
  offset: number;
}
