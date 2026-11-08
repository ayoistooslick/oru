import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError, auth, setToken } from "../lib/api";
import { OruLogo, OruMark } from "../components/OruMark";
import { Check, Download } from "../components/icons";
import { Spinner } from "../components/ui";

/**
 * The one page the site still does real work on: finishing a Google sign-in
 * for the Android app.
 *
 * Google only signs people in inside a browser. The app opens this page in the
 * browser, the page signs in, and the browser hands the app a single-use code
 * through `oru://auth?code=…`. The code is traded for the session by the app,
 * so a URL left in browser history cannot be replayed.
 */

const APP_SCHEME = "oru://auth";

function deepLink(code: string): string {
  return `${APP_SCHEME}?code=${encodeURIComponent(code)}`;
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

type Phase = "checking" | "ready" | "finishing" | "handing" | "handed" | "unavailable";

export function AuthPage() {
  const [phase, setPhase] = useState<Phase>("checking");
  const [config, setConfig] = useState<{ enabled: boolean; clientId: string | null }>({
    enabled: false,
    clientId: null,
  });
  const [error, setError] = useState<string | null>(null);
  const [code, setCode] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    auth
      .config()
      .then((result) => {
        if (!alive) return;
        const enabled = Boolean(result.google_enabled && result.google_client_id);
        setConfig({ enabled, clientId: result.google_client_id });
        setPhase(enabled ? "ready" : "unavailable");
      })
      .catch((cause: unknown) => {
        if (!alive) return;
        setPhase("unavailable");
        setError(cause instanceof Error ? cause.message : "The server could not be reached.");
      });
    return () => {
      alive = false;
    };
  }, []);

  const scriptReady = useGoogleScript(phase === "ready" && config.enabled);

  useEffect(() => {
    if (!scriptReady || !config.clientId) return;
    const googleApi = (window as unknown as { google?: { accounts?: { id?: GoogleIdApi } } }).google?.accounts?.id;
    if (!googleApi) return;

    const finish = async (credential: string) => {
      setError(null);
      setPhase("finishing");
      try {
        const session = await auth.google(credential);
        setToken(session.access_token);
        // The browser session is only a means to an end; the code is what the
        // app needs, and it is good for a few minutes and one use.
        const handoff = await auth.handoff();
        setCode(handoff.code);
        setPhase("handed");
        window.location.href = deepLink(handoff.code);
      } catch (cause) {
        setPhase("ready");
        setError(
          cause instanceof ApiError
            ? cause.message
            : cause instanceof Error
              ? cause.message
              : "Sign-in did not finish. Try again.",
        );
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
        theme: "filled_black",
        size: "large",
        width: 300,
        text: "continue_with",
        shape: "pill",
        logo_alignment: "left",
      });
    }
  }, [scriptReady, config.clientId]);

  return (
    <div className="flex min-h-[100dvh] flex-col bg-paper px-5 py-8">
      <Link to="/" className="mx-auto" aria-label="ORU home">
        <OruLogo />
      </Link>

      <main className="mx-auto flex w-full max-w-[460px] flex-1 flex-col justify-center py-10">
        <div className="overflow-hidden rounded-3xl border border-hair bg-card shadow-float">
          <div className="border-b border-hair bg-gradient-to-br from-flame/20 to-transparent px-7 py-7">
            <span className="grid h-11 w-11 place-items-center rounded-full bg-flame/15 ring-1 ring-flame/30">
              <OruMark className="h-6 w-6" />
            </span>
            <h1 className="mt-4 font-display text-[27px] font-extrabold leading-tight tracking-[-0.02em] text-ink">
              {phase === "handed" ? "You're in." : "Sign in to ORU"}
            </h1>
            <p className="mt-2 text-[14.5px] leading-relaxed text-ink-soft">
              {phase === "handed"
                ? "Handing you back to the app now. It stays open in the background."
                : "This page finishes Google sign-in and sends you straight back to the ORU app."}
            </p>
          </div>

          <div className="px-7 py-7">
            {phase === "checking" ? (
              <div className="flex items-center gap-3 text-[14.5px] font-semibold text-ink-soft">
                <Spinner className="h-5 w-5 text-flame-ink" />
                Checking how you can sign in
              </div>
            ) : null}

            {phase === "ready" || phase === "finishing" ? (
              <>
                <div id="google-button" className="flex min-h-[46px] justify-center" />
                {!scriptReady ? (
                  <p className="mt-3 text-center text-[13px] text-ink-faint">
                    Loading Google sign-in…
                  </p>
                ) : null}
                {phase === "finishing" ? (
                  <p className="mt-4 flex items-center justify-center gap-2 text-[14px] font-semibold text-ink-soft">
                    <Spinner className="h-4 w-4" />
                    Signing you in
                  </p>
                ) : null}
                <p className="mt-5 text-center text-[13px] leading-relaxed text-ink-faint">
                  ORU uses your Google name and picture for your profile. Nothing else.
                </p>
              </>
            ) : null}

            {phase === "handed" ? (
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
                {code ? (
                  <a
                    href={deepLink(code)}
                    className="inline-flex h-12 w-full items-center justify-center rounded-full bg-flame text-[15px] font-bold text-cream shadow-ember transition hover:bg-flame-ink press"
                  >
                    Open the ORU app
                  </a>
                ) : null}
                <p className="text-center text-[13px] leading-relaxed text-ink-soft">
                  Nothing happened? Open ORU yourself and you will already be signed in.
                </p>
              </div>
            ) : null}

            {phase === "unavailable" ? (
              <div className="space-y-4">
                <p className="rounded-xl border border-hair bg-paper-deep px-4 py-3 text-[14px] leading-relaxed text-ink-soft">
                  {error ??
                    "Google sign-in is switched off right now. You can still sign in with your email and password inside the app."}
                </p>
                <Link
                  to="/"
                  className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-full border border-hair text-[14.5px] font-semibold text-ink transition hover:bg-ink/8"
                >
                  <Download className="h-4 w-4" />
                  Download ORU for Android
                </Link>
              </div>
            ) : null}

            {error && phase === "ready" ? (
              <p className="mt-4 rounded-xl border border-[#e5484d]/40 bg-[#e5484d]/10 px-4 py-3 text-[13.5px] font-semibold leading-relaxed text-[#ffb0ab]">
                {error}
              </p>
            ) : null}
          </div>
        </div>

        <p className="mt-6 text-center text-[13px] text-ink-faint">
          ORU runs on Android.{" "}
          <Link to="/" className="font-semibold text-ink-soft underline underline-offset-4 hover:text-ink">
            Download the app
          </Link>
        </p>
      </main>
    </div>
  );
}
