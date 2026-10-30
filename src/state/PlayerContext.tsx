import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { getToken, mediaUrl, request } from "../lib/api";
import type { Song } from "../lib/types";
import { useToasts } from "./ToastContext";

interface PlayerValue {
  queue: Song[];
  current: Song | null;
  index: number;
  shuffle: boolean;
  repeatMode: "off" | "all" | "one";
  isPlaying: boolean;
  isLoading: boolean;
  position: number;
  duration: number;
  volume: number;
  expanded: boolean;
  playQueue: (songs: Song[], startIndex?: number) => void;
  playSong: (song: Song, queue?: Song[]) => void;
  toggle: () => void;
  next: () => void;
  previous: () => void;
  toggleShuffle: () => void;
  cycleRepeat: () => void;
  seek: (seconds: number) => void;
  setVolume: (value: number) => void;
  setExpanded: (value: boolean) => void;
}

const PlayerContext = createContext<PlayerValue | null>(null);

export function PlayerProvider({ children }: { children: ReactNode }) {
  const toasts = useToasts();
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const reportedRef = useRef(false);
  const historyRef = useRef<number[]>([]);
  const repeatModeRef = useRef<"off" | "all" | "one">("off");
  const shuffleRef = useRef(false);
  const [queue, setQueue] = useState<Song[]>([]);
  const [index, setIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolumeState] = useState(1);
  const [shuffle, setShuffle] = useState(false);
  const [repeatMode, setRepeatMode] = useState<"off" | "all" | "one">("off");
  const [expanded, setExpanded] = useState(false);
  repeatModeRef.current = repeatMode;
  shuffleRef.current = shuffle;

  const current = queue[index] ?? null;

  const audio = useCallback(() => {
    if (!audioRef.current) {
      const element = new Audio();
      element.preload = "metadata";
      audioRef.current = element;
    }
    return audioRef.current;
  }, []);

  // Report real playback progress. The server only counts a listen after 30
  // seconds, and ignores repeats from the same account within 30 minutes.
  const reportListen = useCallback((song: Song, listenedMs: number) => {
    if (!localStorage.getItem("oru.token")) return;
    request(`/songs/${song.id}/listen`, { method: "POST", body: { listened_ms: Math.round(listenedMs) } }).catch(
      () => undefined,
    );
  }, []);

  const next = useCallback(() => {
    if (queue.length > 1 && shuffleRef.current) {
      const unplayed = queue
        .map((_, at) => at)
        .filter((at) => at !== index && !historyRef.current.includes(at));
      if (unplayed.length > 0) {
        historyRef.current.push(index);
        setIndex(unplayed[Math.floor(Math.random() * unplayed.length)]);
        return;
      }
      if (repeatModeRef.current === "all") {
        const candidates = queue.map((_, at) => at).filter((at) => at !== index);
        historyRef.current = [index];
        setIndex(candidates[Math.floor(Math.random() * candidates.length)]);
        return;
      }
      const element = audioRef.current;
      if (element) {
        element.pause();
        element.currentTime = 0;
      }
      setIsPlaying(false);
      return;
    }
    if (index + 1 < queue.length) {
      historyRef.current.push(index);
      setIndex(index + 1);
      return;
    }
    if (repeatModeRef.current === "all" && queue.length > 0) {
      if (queue.length === 1) {
        const element = audioRef.current;
        if (element) {
          element.currentTime = 0;
          element.play().catch(() => setIsPlaying(false));
        }
        return;
      }
      historyRef.current.push(index);
      setIndex(0);
      return;
    }
    const element = audioRef.current;
    if (element) {
      element.pause();
      element.currentTime = 0;
    }
    setIsPlaying(false);
  }, [index, queue]);

  const previous = useCallback(() => {
    const element = audioRef.current;
    if (element && element.currentTime > 4) {
      element.currentTime = 0;
      setPosition(0);
      return;
    }
    const previousIndex = historyRef.current.pop();
    if (previousIndex !== undefined) setIndex(previousIndex);
    else if (index > 0) setIndex(index - 1);
    else if (element) {
      element.currentTime = 0;
      setPosition(0);
    }
  }, [index]);

  // Load the selected track whenever the selection changes.
  useEffect(() => {
    const element = audio();
    reportedRef.current = false;
    setPosition(0);
    setDuration(0);

    const source = current ? mediaUrl(current.audio_url) : undefined;
    if (!source) {
      element.pause();
      element.removeAttribute("src");
      setIsPlaying(false);
      setIsLoading(false);
      return;
    }

    const onLoaded = () => {
      setDuration(Number.isFinite(element.duration) ? element.duration : 0);
      setIsLoading(false);
    };
    const onTime = () => {
      setPosition(element.currentTime);
      if (!reportedRef.current && element.currentTime >= 30) {
        reportedRef.current = true;
        reportListen(current, element.currentTime * 1000);
      }
    };
    const onPlay = () => setIsPlaying(true);
    const onPause = () => setIsPlaying(false);
    const onWaiting = () => setIsLoading(true);
    const onPlaying = () => setIsLoading(false);
    const onEnded = () => {
      if (!reportedRef.current) {
        reportedRef.current = true;
        reportListen(current, (element.duration || 0) * 1000);
      }
      if (repeatModeRef.current === "one") {
        element.currentTime = 0;
        element.play().catch(() => setIsPlaying(false));
      } else {
        next();
      }
    };

    element.addEventListener("loadedmetadata", onLoaded);
    element.addEventListener("timeupdate", onTime);
    element.addEventListener("play", onPlay);
    element.addEventListener("pause", onPause);
    element.addEventListener("waiting", onWaiting);
    element.addEventListener("playing", onPlaying);
    element.addEventListener("ended", onEnded);

    let cancelled = false;
    let objectUrl: string | null = null;

    const start = (src: string) => {
      if (cancelled) return;
      element.src = src;
      element.volume = volume;
      element.load();
      setIsLoading(true);
      element.play().catch(() => setIsPlaying(false));
    };

    if (current.status === "published") {
      start(source);
    } else {
      // A draft is private, and an <audio> element cannot carry an Authorization
      // header, so the file is fetched with the token and played from memory.
      fetch(source, { headers: { Authorization: `Bearer ${getToken() ?? ""}` } })
        .then((response) => {
          if (!response.ok) throw new Error("This track is not available to you right now.");
          return response.blob();
        })
        .then((blob) => {
          objectUrl = URL.createObjectURL(blob);
          start(objectUrl);
        })
        .catch((error: Error) => {
          if (cancelled) return;
          setIsLoading(false);
          setIsPlaying(false);
          toasts.error("Could not play that", error.message);
        });
    }

    if ("mediaSession" in navigator && typeof MediaMetadata !== "undefined") {
      const artwork = current.cover_url
        ? [96, 192, 512].map((size) => ({
            src: mediaUrl(current.cover_url) ?? "",
            sizes: `${size}x${size}`,
            type: "image/png",
          }))
        : [];
      navigator.mediaSession.metadata = new MediaMetadata({
        title: current.title,
        artist: current.artist_name,
        album: "ORU",
        artwork,
      });
    }

    return () => {
      cancelled = true;
      element.removeEventListener("loadedmetadata", onLoaded);
      element.removeEventListener("timeupdate", onTime);
      element.removeEventListener("play", onPlay);
      element.removeEventListener("pause", onPause);
      element.removeEventListener("waiting", onWaiting);
      element.removeEventListener("playing", onPlaying);
      element.removeEventListener("ended", onEnded);
      element.pause();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [current?.id, current?.audio_url, reportListen, next]);

  const toggle = useCallback(() => {
    if (!current?.audio_url) return;
    const element = audio();
    if (element.paused) {
      element.play().catch(() => setIsPlaying(false));
    } else {
      element.pause();
    }
    if ("vibrate" in navigator) navigator.vibrate(12);
  }, [audio, current]);

  const playQueue = useCallback(
    (songs: Song[], startIndex = 0) => {
      if (!songs.length) return;
      historyRef.current = [];
      const sameTrack = current && songs[startIndex]?.id === current.id;
      setQueue(songs);
      setIndex(startIndex);
      if (sameTrack) {
        audio().play().catch(() => setIsPlaying(false));
      }
    },
    [audio, current],
  );

  const playSong = useCallback(
    (song: Song, others?: Song[]) => {
      if (current?.id === song.id) {
        toggle();
        return;
      }
      const list = others ?? [song];
      const at = list.findIndex((item) => item.id === song.id);
      playQueue(list, at >= 0 ? at : 0);
    },
    [current, toggle, playQueue],
  );

  const seek = useCallback((seconds: number) => {
    const element = audioRef.current;
    if (!element) return;
    element.currentTime = seconds;
    setPosition(seconds);
  }, []);

  const toggleShuffle = useCallback(() => setShuffle((value) => !value), []);
  const cycleRepeat = useCallback(
    () => setRepeatMode((value) => (value === "off" ? "all" : value === "all" ? "one" : "off")),
    [],
  );

  const setVolume = useCallback((value: number) => {
    setVolumeState(value);
    if (audioRef.current) audioRef.current.volume = value;
  }, []);

  useEffect(() => {
    if (!("mediaSession" in navigator)) return;
    const handlers: [MediaSessionAction, (details?: MediaSessionActionDetails) => void][] = [
      ["play", () => audioRef.current?.play().catch(() => undefined)],
      ["pause", () => audioRef.current?.pause()],
      ["stop", () => audioRef.current?.pause()],
      ["previoustrack", previous],
      ["nexttrack", next],
      ["seekbackward", (details) => {
        const element = audioRef.current;
        if (element) element.currentTime = Math.max(0, element.currentTime - (details?.seekOffset ?? 10));
      }],
      ["seekforward", (details) => {
        const element = audioRef.current;
        if (element) {
          const duration = Number.isFinite(element.duration) ? element.duration : element.currentTime + 1e6;
          element.currentTime = Math.min(duration, element.currentTime + (details?.seekOffset ?? 10));
        }
      }],
      ["seekto", (details) => {
        if (details?.seekTime !== undefined && audioRef.current) audioRef.current.currentTime = details.seekTime;
      }],
    ];
    for (const [action, handler] of handlers) {
      try {
        navigator.mediaSession.setActionHandler(action, handler);
      } catch {
        // Some browsers do not implement every action.
      }
    }
  }, [next, previous]);

  // Lock-screen controls: what is playing, and where in the song we are.
  useEffect(() => {
    if (!("mediaSession" in navigator)) return;
    navigator.mediaSession.playbackState = isPlaying ? "playing" : "paused";
  }, [isPlaying]);

  useEffect(() => {
    if (!("mediaSession" in navigator) || typeof navigator.mediaSession.setPositionState !== "function") return;
    if (!current || duration <= 0) {
      try {
        navigator.mediaSession.setPositionState();
      } catch {
        // Nothing playing to describe.
      }
      return;
    }
    try {
      navigator.mediaSession.setPositionState({
        duration,
        playbackRate: 1,
        position: Math.min(Math.max(position, 0), duration),
      });
    } catch {
      // Some browsers reject a position outside the duration.
    }
  }, [current?.id, position, duration, isPlaying]);

  const value = useMemo<PlayerValue>(
    () => ({
      queue,
      current,
      index,
      shuffle,
      repeatMode,
      isPlaying,
      isLoading,
      position,
      duration,
      volume,
      expanded,
      playQueue,
      playSong,
      toggle,
      next,
      previous,
      toggleShuffle,
      cycleRepeat,
      seek,
      setVolume,
      setExpanded,
    }),
    [
      queue,
      current,
      index,
      shuffle,
      repeatMode,
      isPlaying,
      isLoading,
      position,
      duration,
      volume,
      expanded,
      playQueue,
      playSong,
      toggle,
      next,
      previous,
      toggleShuffle,
      cycleRepeat,
      seek,
      setVolume,
    ],
  );

  return <PlayerContext.Provider value={value}>{children}</PlayerContext.Provider>;
}

export function usePlayer(): PlayerValue {
  const value = useContext(PlayerContext);
  if (!value) throw new Error("usePlayer must be used inside PlayerProvider");
  return value;
}
