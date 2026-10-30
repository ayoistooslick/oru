import { useCallback, useEffect, useRef, useState, type DependencyList } from "react";
import { ApiError } from "../lib/api";

interface Resource<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => void;
}

/**
 * A small fetch hook: aborts on unmount, keeps the previous data while the next
 * request is in flight, and exposes one way to retry.
 */
export function useResource<T>(fetcher: (signal: AbortSignal) => Promise<T>, deps: DependencyList): Resource<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);
  const latest = useRef(fetcher);
  latest.current = fetcher;

  useEffect(() => {
    const controller = new AbortController();
    let alive = true;
    setLoading(true);
    setError(null);

    latest
      .current(controller.signal)
      .then((value) => {
        if (!alive) return;
        setData(value);
        setLoading(false);
      })
      .catch((reason: unknown) => {
        if (!alive || (reason instanceof DOMException && reason.name === "AbortError")) return;
        setError(reason instanceof ApiError ? reason.message : "Could not reach ORU. Check your connection.");
        setLoading(false);
      });

    return () => {
      alive = false;
      controller.abort();
    };
    // The caller decides what invalidates the request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce]);

  const reload = useCallback(() => setNonce((value) => value + 1), []);
  return { data, loading, error, reload };
}
