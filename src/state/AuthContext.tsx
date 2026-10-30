import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { ApiError, auth, setToken } from "../lib/api";
import type { AuthResponse, User } from "../lib/types";

interface SignUpInput {
  email: string;
  handle: string;
  display_name: string;
  password: string;
}

interface AuthValue {
  user: User | null;
  loading: boolean;
  isArtist: boolean;
  isAdmin: boolean;
  signIn: (email: string, password: string) => Promise<User>;
  signUp: (input: SignUpInput) => Promise<User>;
  signOut: () => void;
  updateUser: (user: User) => void;
}

const AuthContext = createContext<AuthValue | null>(null);
const USER_KEY = "oru.user";

function readStoredUser(): User | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as User) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => readStoredUser());
  const [loading, setLoading] = useState(true);

  const persist = useCallback((next: User | null) => {
    setUser(next);
    if (next) localStorage.setItem(USER_KEY, JSON.stringify(next));
    else localStorage.removeItem(USER_KEY);
  }, []);

  useEffect(() => {
    let cancelled = false;
    if (!localStorage.getItem("oru.token")) {
      setLoading(false);
      return;
    }
    auth
      .me()
      .then((response) => {
        if (!cancelled) persist(response.user);
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          // A transient network/server failure must not sign a listener out.
          // Keep the cached account available offline; clear credentials only
          // when the API confirms the token is no longer valid.
          if (error instanceof ApiError && error.status === 401) {
            setToken(null);
            persist(null);
          }
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [persist]);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const response = await auth.login({ email, password });
      setToken(response.access_token);
      persist(response.user);
      return response.user;
    },
    [persist],
  );

  const signUp = useCallback(
    async (input: SignUpInput) => {
      const response: AuthResponse = await auth.register(input);
      setToken(response.access_token);
      persist(response.user);
      return response.user;
    },
    [persist],
  );

  const signOut = useCallback(() => {
    setToken(null);
    persist(null);
  }, [persist]);

  const value = useMemo<AuthValue>(
    () => ({
      user,
      loading,
      isArtist: user?.role === "artist" || user?.role === "admin",
      isAdmin: user?.role === "admin",
      signIn,
      signUp,
      signOut,
      updateUser: persist,
    }),
    [user, loading, signIn, signUp, signOut, persist],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
