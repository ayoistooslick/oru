import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";

export type ToastKind = "saved" | "playing" | "upload" | "error" | "info";

export interface ToastItem {
  id: number;
  kind: ToastKind;
  title: string;
  message?: string;
}

interface ToastApi {
  items: ToastItem[];
  show: (kind: ToastKind, title: string, message?: string) => void;
  saved: (title: string, message?: string) => void;
  playing: (title: string, message?: string) => void;
  upload: (title: string, message?: string) => void;
  info: (title: string, message?: string) => void;
  error: (title: string, message?: string) => void;
  dismiss: (id: number) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const counter = useRef(0);

  const dismiss = useCallback((id: number) => {
    setItems((current) => current.filter((item) => item.id !== id));
  }, []);

  const show = useCallback(
    (kind: ToastKind, title: string, message?: string) => {
      counter.current += 1;
      const id = counter.current;
      // The viewport owns the lifetime of a toast so it can animate the exit.
      setItems((current) => [...current.slice(-2), { id, kind, title, ...(message ? { message } : {}) }]);
    },
    [dismiss],
  );

  const api = useMemo<ToastApi>(
    () => ({
      items,
      show,
      dismiss,
      saved: (title, message) => show("saved", title, message),
      playing: (title, message) => show("playing", title, message),
      upload: (title, message) => show("upload", title, message),
      info: (title, message) => show("info", title, message),
      error: (title, message) => show("error", title, message),
    }),
    [items, show, dismiss],
  );

  return <ToastContext.Provider value={api}>{children}</ToastContext.Provider>;
}

export function useToasts(): ToastApi {
  const value = useContext(ToastContext);
  if (!value) throw new Error("useToasts must be used inside ToastProvider");
  return value;
}
