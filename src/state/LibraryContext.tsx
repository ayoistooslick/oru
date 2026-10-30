import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { request } from "../lib/api";
import type { Song } from "../lib/types";

interface LibraryValue {
  likedIds: Set<number>;
  followedIds: Set<number>;
  ready: boolean;
  refresh: () => Promise<void>;
  toggleLike: (song: Song) => Promise<boolean>;
  toggleFollow: (artistId: number) => Promise<boolean>;
}

const LibraryContext = createContext<LibraryValue | null>(null);

export function LibraryProvider({ userId, children }: { userId: number | null; children: ReactNode }) {
  const [likedIds, setLikedIds] = useState<Set<number>>(() => new Set());
  const [followedIds, setFollowedIds] = useState<Set<number>>(() => new Set());
  const [ready, setReady] = useState(false);

  const refresh = useCallback(async () => {
    if (!userId) {
      setLikedIds(new Set());
      setFollowedIds(new Set());
      setReady(true);
      return;
    }
    try {
      const [likes, following] = await Promise.all([
        request<{ liked_song_ids: number[] }>("/listeners/me/likes?limit=50"),
        request<{ followed_artist_ids: number[] }>("/listeners/me/following?limit=50"),
      ]);
      setLikedIds(new Set(likes.liked_song_ids));
      setFollowedIds(new Set(following.followed_artist_ids));
    } catch {
      // A failed lookup should never block playback; the lists just stay empty.
    } finally {
      setReady(true);
    }
  }, [userId]);

  useEffect(() => {
    setReady(false);
    void refresh();
  }, [refresh]);

  const toggleLike = useCallback(async (song: Song) => {
    let liked = false;
    setLikedIds((current) => {
      liked = !current.has(song.id);
      const next = new Set(current);
      if (liked) next.add(song.id);
      else next.delete(song.id);
      return next;
    });
    try {
      await request(`/songs/${song.id}/likes`, { method: liked ? "POST" : "DELETE" });
    } catch (error) {
      setLikedIds((current) => {
        const next = new Set(current);
        if (liked) next.delete(song.id);
        else next.add(song.id);
        return next;
      });
      throw error;
    }
    return liked;
  }, []);

  const toggleFollow = useCallback(async (artistId: number) => {
    let following = false;
    setFollowedIds((current) => {
      following = !current.has(artistId);
      const next = new Set(current);
      if (following) next.add(artistId);
      else next.delete(artistId);
      return next;
    });
    try {
      await request(`/artists/${artistId}/follow`, { method: following ? "POST" : "DELETE" });
    } catch (error) {
      setFollowedIds((current) => {
        const next = new Set(current);
        if (following) next.delete(artistId);
        else next.add(artistId);
        return next;
      });
      throw error;
    }
    return following;
  }, []);

  const value = useMemo<LibraryValue>(
    () => ({ likedIds, followedIds, ready, refresh, toggleLike, toggleFollow }),
    [likedIds, followedIds, ready, refresh, toggleLike, toggleFollow],
  );

  return <LibraryContext.Provider value={value}>{children}</LibraryContext.Provider>;
}

export function useLibrary(): LibraryValue {
  const value = useContext(LibraryContext);
  if (!value) throw new Error("useLibrary must be used inside LibraryProvider");
  return value;
}
