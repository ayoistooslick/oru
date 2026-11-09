import { useEffect, useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { ApiError, auth, setToken } from "../lib/api";
import { useAuth } from "../state/AuthContext";
import { OruLogo, OruMark } from "../components/OruMark";
import { Check, Download } from "../components/icons";
import { Spinner } from "../components/ui";

/**
 * Sign-in for both clients.
 *
 * In a browser this is the ordinary account page: email and password, or
 * Google, then back to wherever you were going.
 *
 * The Android app sends people here with `?client=android` because Google only
 * signs people in inside a browser. In that mode the page signs in and hands
 * the app a single-use code through `oru://auth?code=…`, so the app gets a
 * session without a token ever sitting in browser history.
 */

function deepLink(code: string): string {
  return `oru://auth?code=${encodeURIComponent(code)}`;
}

interface GoogleIdApi {
  initialize: (options: Record<string, unknown>) => void;
  renderButton: (element: HTMLElement, options: Record<string, unknown>) => void;
}

function useGoogleScript(enabled: boolean): boolean {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    if (!enabled) return;
    if (document.getElementById("gsi-script")) {
      setReady(true);
      return;
    }
    const script = document.createElement("script");
    script.id = "gsi-script";
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.defer = true;
    script.onload = () => setReady(true);
    document.head.appendChild(script);
  }, [enabled]);
  return ready;
}

type Tab = "signin" | "signup";

export function AuthPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { user, loading, signIn, signUp, signInWithGoogle } = useAuth();

  const forApp = params.get("client") === "android";
  const returnTo = params.get("returnTo") || "/listen";
  const initialMode = params.get("mode") === "signup" ? "signup" : "signin";

  const [tab, setTab] = useState<Tab>(initialMode);
  const [config, setConfig] = useState<{ enabled: boolean; clientId: string | null }>({
    enabled: false,
    clientId: null,
  });
  const [checking, setChecking] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [values, setValues] = useState({ email: "", password: "", handle: "", displayName: "" });
  const [handedCode, setHandedCode] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    auth
      .config()
      .then((result) => {
        if (!alive) return;
        setConfig({
          enabled: Boolean(result.google_enabled && result.google_client_id),
          clientId: result.google_client_id,
        });
      })
      .catch(() => undefined)
      .finally(() => {
        if (alive) setChecking(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const scriptReady = useGoogleScript(config.enabled);

  useEffect(() => {
    if (!scriptReady || !config.clientId) return;
    const googleApi = (window as unknown as { google?: { accounts?: { id?: GoogleIdApi } } }).google?.accounts?.id;
    if (!googleApi) return;

    const finish = async (credential: string) => {
      setError(null);
      setBusy(true);
      try {
        if (!forApp) {
          await signInWithGoogle(credential);
          navigate(returnTo, { replace: true });
          return;
        }
        const session = await auth.google(credential);
        setToken(session.access_token);
        // The browser session is only a means to an end; the code is what the
        // app needs, and it is good for a few minutes and one use.
        const handoff = await auth.handoff();
        setHandedCode(handoff.code);
        window.location.href = deepLink(handoff.code);
      } catch (cause) {
        setError(
          cause instanceof ApiError
            ? cause.message
            : cause instanceof Error
              ? cause.message
              : "Sign-in did not finish. Try again.",
        );
      } finally {
        setBusy(false);
      }
    };

    googleApi.initialize({
      client_id: config.clientId,
      callback: (response: { credential?: string }) => {
        if (response.credential) void finish(response.credential);
      },
    });

    const holder = document.getElementById("google-button");
    if (holder) {
      holder.innerHTML = "";
      googleApi.renderButton(holder, {
        theme: "outline",
        size: "large",
        width: 320,
        text: "continue_with",
        shape: "pill",
        logo_alignment: "left",
      });
    }
  }, [scriptReady, config.clientId, forApp, signInWithGoogle, navigate, returnTo]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (tab === "signup") {
        await signUp({
          email: values.email.trim(),
          password: values.password,
          handle: values.handle.trim() || undefined,
          display_name: values.displayName.trim() || undefined,
        });
      } else {
        await signIn(values.email.trim(), values.password);
      }
      navigate(returnTo, { replace: true });
    } catch (cause) {
      setError(
        cause instanceof ApiError
          ? cause.message
          : cause instanceof Error
            ? cause.message
            : "That did not work. Try again.",
      );
    } finally {
      setBusy(false);
    }
  };

  if (!loading && user && !forApp) return <Navigate to={returnTo} replace />;

  return (
    <div className="flex min-h-[100dvh] flex-col bg-paper px-5 py-8">
      <Link to="/" className="mx-auto" aria-label="ORU home">
        <OruLogo />
      </Link>

      <main className="mx-auto flex w-full max-w-[460px] flex-1 flex-col justify-center py-10">
        <div className="overflow-hidden rounded-3xl border border-hair bg-card shadow-float">
          <div className="border-b border-hair bg-gradient-to-br from-flame/15 to-transparent px-7 py-7">
            <span className="grid h-11 w-11 place-items-center rounded-full bg-flame/15 ring-1 ring-flame/30">
              <OruMark className="h-6 w-6" />
            </span>
            <h1 className="mt-4 font-display text-[27px] font-extrabold leading-tight tracking-[-0.02em] text-ink">
              {handedCode ? "You're in." : forApp ? "Continue in the app" : "Welcome to ORU"}
            </h1>
            <p className="mt-2 text-[14.5px] leading-relaxed text-ink-soft">
              {handedCode
                ? "Handing you back to the app now. It stays open in the background."
                : forApp
                  ? "Sign in with Google and the app picks up right where this page leaves off."
                  : "Listen free, follow artists and put your own songs up. One account for the web and the app."}
            </p>
          </div>

          <div className="px-7 py-7">
            {checking ? (
              <div className="flex items-center gap-3 text-[14.5px] font-semibold text-ink-soft">
                <Spinner className="h-5 w-5 text-flame-ink" />
                Checking how you can sign in
              </div>
            ) : null}

            {!checking && handedCode ? (
              <div className="space-y-4">
                <ol className="space-y-3">
                  {["Signed in", "Sent back to the app"].map((line) => (
                    <li key={line} className="flex items-center gap-3 text-[14.5px] font-semibold text-ink">
                      <span className="grid h-6 w-6 place-items-center rounded-full bg-flame text-cream">
                        <Check className="h-3.5 w-3.5" />
                      </span>
                      {line}
                    </li>
                  ))}
                </ol>
                <a
                  href={deepLink(handedCode)}
                  className="inline-flex h-12 w-full items-center justify-center rounded-full bg-flame text-[15px] font-bold text-cream shadow-ember transition hover:bg-flame-ink press"
                >
                  Open the ORU app
                </a>
              </div>
            ) : null}

            {!checking && !handedCode ? (
              <>
                {!forApp ? (
                  <div className="mb-6 grid grid-cols-2 gap-1 rounded-full bg-ink/5 p-1">
                    {(["signin", "signup"] as Tab[]).map((option) => (
                      <button
                        key={option}
                        type="button"
                        onClick={() => {
                          setTab(option);
                          setError(null);
                        }}
                        className={`h-9 rounded-full text-[13.5px] font-bold transition ${
                          tab === option ? "bg-card text-ink shadow-card" : "text-ink-soft hover:text-ink"
                        }`}
                      >
                        {option === "signin" ? "Sign in" : "Create account"}
                      </button>
                    ))}
                  </div>
                ) : null}

                {config.enabled ? (
                  <div className="flex justify-center">
                    <div id="google-button" className="flex min-h-[46px] w-full justify-center" />
                  </div>
                ) : null}

                {!scriptReady && config.enabled ? (
                  <p className="mt-3 text-center text-[13px] text-ink-faint">Loading Google sign-in…</p>
                ) : null}

                {!forApp ? (
                  <>
                    <div className="my-6 flex items-center gap-3 text-[12px] font-bold uppercase tracking-[0.12em] text-ink-faint">
                      <span className="h-px flex-1 bg-hair" />
                      or email
                      <span className="h-px flex-1 bg-hair" />
                    </div>

                    <form onSubmit={(event) => void submit(event)} className="space-y-4">
                      <label className="block">
                        <span className="label-caps">Email</span>
                        <input
                          type="email"
                          autoComplete="email"
                          required
                          value={values.email}
                          onChange={(event) => setValues((current) => ({ ...current, email: event.target.value }))}
                          className="mt-2 h-11 w-full rounded-xl border border-hair bg-paper px-4 text-[15.5px] text-ink outline-none transition placeholder:text-ink-faint focus:border-flame-ink"
                          placeholder="you@example.com"
                        />
                      </label>

                      {tab === "signup" ? (
                        <div className="grid gap-4 sm:grid-cols-2">
                          <label className="block">
                            <span className="label-caps">Name</span>
                            <input
                              value={values.displayName}
                              onChange={(event) =>
                                setValues((current) => ({ ...current, displayName: event.target.value }))
                              }
                              className="mt-2 h-11 w-full rounded-xl border border-hair bg-paper px-4 text-[15.5px] text-ink outline-none transition placeholder:text-ink-faint focus:border-flame-ink"
                              placeholder="Your name"
                            />
                          </label>
                          <label className="block">
                            <span className="label-caps">Handle</span>
                            <input
                              value={values.handle}
                              onChange={(event) =>
                                setValues((current) => ({ ...current, handle: event.target.value }))
                              }
                              className="mt-2 h-11 w-full rounded-xl border border-hair bg-paper px-4 text-[15.5px] text-ink outline-none transition placeholder:text-ink-faint focus:border-flame-ink"
                              placeholder="your_handle"
                            />
                          </label>
                        </div>
                      ) : null}

                      <label className="block">
                        <span className="label-caps">Password</span>
                        <input
                          type="password"
                          autoComplete={tab === "signup" ? "new-password" : "current-password"}
                          required
                          minLength={tab === "signup" ? 10 : undefined}
                          value={values.password}
                          onChange={(event) => setValues((current) => ({ ...current, password: event.target.value }))}
                          className="mt-2 h-11 w-full rounded-xl border border-hair bg-paper px-4 text-[15.5px] text-ink outline-none transition placeholder:text-ink-faint focus:border-flame-ink"
                          placeholder={tab === "signup" ? "At least 10 characters" : "Your password"}
                        />
                      </label>

                      <button
                        type="submit"
                        disabled={busy}
                        className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-full bg-flame text-[15px] font-bold text-cream shadow-ember transition hover:bg-flame-ink press disabled:opacity-60"
                      >
                        {busy ? <Spinner className="h-4 w-4" /> : null}
                        {tab === "signup" ? "Create my account" : "Sign in"}
                      </button>
                    </form>
                  </>
                ) : null}

                {error ? (
                  <p className="mt-4 rounded-xl border border-[#c2411d]/30 bg-flame-soft px-4 py-3 text-[13.5px] font-semibold leading-relaxed text-flame-ink">
                    {error}
                  </p>
                ) : null}

                {forApp && !config.enabled ? (
                  <p className="mt-4 rounded-xl border border-hair bg-paper-deep px-4 py-3 text-[14px] leading-relaxed text-ink-soft">
                    Google sign-in is switched off right now. You can still sign in with your email and
                    password inside the app.
                  </p>
                ) : null}

                {!forApp ? (
                  <p className="mt-5 text-center text-[13px] leading-relaxed text-ink-faint">
                    On Android?{" "}
                    <a href="/" className="font-semibold text-ink-soft underline underline-offset-4 hover:text-ink">
                      Get the app
                    </a>{" "}
                    for downloads and lock-screen controls.
                  </p>
                ) : (
                  <p className="mt-5 text-center text-[13px] leading-relaxed text-ink-faint">
                    Nothing happens? Open ORU yourself and you will already be signed in.
                  </p>
                )}
              </>
            ) : null}
          </div>
        </div>

        <p className="mt-6 flex items-center justify-center gap-1.5 text-center text-[13px] text-ink-faint">
          <Download className="h-3.5 w-3.5" />
          <a href="/" className="font-semibold text-ink-soft underline underline-offset-4 hover:text-ink">
            ORU on Android
          </a>
          <span className="opacity-50">·</span>
          <span>the browser needs nothing</span>
        </p>
      </main>
    </div>
  );
}
