import { useEffect, useMemo, useState, type FormEvent, type InputHTMLAttributes } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { ApiError, auth } from "../lib/api";
import { useAuth } from "../state/AuthContext";
import { useToasts } from "../state/ToastContext";
import { OruLogo } from "../components/OruMark";
import { Spinner } from "../components/ui";

type Mode = "signin" | "signup";

function LightField({
  label,
  hint,
  error,
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }) {
  return (
    <label className="block">
      <span className="text-[13px] font-bold text-paper">{label}</span>
      <input
        className={`mt-1.5 h-12 w-full rounded-lg border-2 bg-white px-4 text-[15px] text-paper outline-none transition placeholder:text-[#8a8175] ${
          error ? "border-[#d31c2a]" : "border-hair focus:border-flame"
        }`}
        {...rest}
      />
      {error ? (
        <span className="mt-1 block text-[12.5px] font-semibold text-[#d31c2a]">{error}</span>
      ) : hint ? (
        <span className="mt-1 block text-[12.5px] text-[#4d4d4d]">{hint}</span>
      ) : null}
    </label>
  );
}

/** Loads Google's sign-in script once and reports when it is ready to use. */
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

export function AuthPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { user, loading, signIn, signUp, signInWithGoogle } = useAuth();
  const toasts = useToasts();

  const returnTo = params.get("returnTo") || "/listen";
  const mode: Mode = useMemo(() => (params.get("mode") === "signin" ? "signin" : "signup"), [params]);

  const [tab, setTab] = useState<Mode>(mode);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [values, setValues] = useState({ email: "", password: "" });
  const [google, setGoogle] = useState<{ enabled: boolean; client_id: string | null }>({ enabled: false, client_id: null });

  // Only offer Google when the server confirms it is configured. A dead button
  // is worse than no button.
  useEffect(() => {
    let alive = true;
    auth
      .config()
      .then((config) => {
        if (alive) setGoogle({ enabled: config.google_enabled, client_id: config.google_client_id });
      })
      .catch(() => {
        /* leave Google hidden */
      });
    return () => {
      alive = false;
    };
  }, []);

  const scriptReady = useGoogleScript(google.enabled && Boolean(google.client_id));

  useEffect(() => {
    if (!scriptReady || !google.client_id) return;
    interface GoogleIdApi {
      initialize: (options: Record<string, unknown>) => void;
      renderButton: (element: HTMLElement, options: Record<string, unknown>) => void;
    }
    const g = (window as unknown as { google?: { accounts?: { id?: GoogleIdApi } } }).google;
    const googleApi = g?.accounts?.id;
    if (!googleApi) return;
    googleApi.initialize({
      client_id: google.client_id,
      callback: (response: { credential?: string }) => {
        if (!response.credential) return;
        signInWithGoogle(response.credential)
          .then(() => navigate(returnTo, { replace: true }))
          .catch((error: unknown) => setFormError(error instanceof Error ? error.message : "Google sign-in failed. Try again."));
      },
    });
    const holder = document.getElementById("google-button");
    if (holder) {
      holder.innerHTML = "";
      googleApi.renderButton(holder, { theme: "outline", size: "large", width: 320, text: "continue_with" });
    }
  }, [scriptReady, google.client_id, signInWithGoogle, navigate, returnTo]);

  if (!loading && user) return <Navigate to={returnTo} replace />;

  const update = (key: keyof typeof values) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));

  const handleError = (error: unknown) => {
    if (error instanceof ApiError) {
      setFormError(error.details.length === 0 ? error.message : null);
      const mapped: Record<string, string> = {};
      for (const detail of error.details) {
        const field = detail.field?.split(".").pop() ?? "";
        if (field) mapped[field] = detail.message;
      }
      setFieldErrors(mapped);
      if (error.details.length > 0 && !Object.keys(mapped).length) setFormError(error.message);
      return;
    }
    setFormError(error instanceof Error ? error.message : "Something went wrong. Try again.");
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setFormError(null);
    setFieldErrors({});
    try {
      if (tab === "signin") {
        const signedIn = await signIn(values.email.trim(), values.password);
        toasts.saved(`Welcome back, ${signedIn.display_name.split(" ")[0]}`);
      } else {
        // Email and password is all we ask for. The name and handle are worked
        // out on the server so sign-up stays to one short step.
        await signUp({ email: values.email.trim(), password: values.password });
        toasts.saved("Account created", "You can start listening right away.");
      }
      navigate(returnTo, { replace: true });
    } catch (error) {
      handleError(error);
    } finally {
      setBusy(false);
    }
  };

  const switchTab = (next: Mode) => {
    setTab(next);
    setFormError(null);
    setFieldErrors({});
  };

  const showGoogle = google.enabled && Boolean(google.client_id);

  return (
    <div className="flex min-h-[100dvh] flex-col bg-paper px-4 py-8">
      <Link to="/" className="mx-auto" aria-label="ORU home">
        <OruLogo />
      </Link>

      <main className="mx-auto flex w-full max-w-[480px] flex-1 flex-col justify-center py-10">
        <div className="rounded-2xl bg-white p-8 shadow-float sm:p-10">
          <h1 className="font-display text-[30px] font-semibold tracking-[-0.01em] text-paper">
            {tab === "signin" ? "Log in to ORU" : "Create your account"}
          </h1>
          <p className="mt-2 text-[14.5px] text-[#4d4d4d]">
            {tab === "signin" ? "Welcome back." : "Free to join. One minute to finish."}
          </p>

          {showGoogle ? (
            <>
              <div id="google-button" className="mt-6 flex min-h-[44px] justify-center" />
              <div className="my-5 flex items-center gap-3 text-[13px] font-semibold text-[#8a8175]">
                <span className="h-px flex-1 bg-[#e0e0e0]" />
                or
                <span className="h-px flex-1 bg-[#e0e0e0]" />
              </div>
            </>
          ) : null}

          <form onSubmit={submit} className={showGoogle ? "space-y-4" : "mt-6 space-y-4"}>
            <LightField
              label="Email"
              name="email"
              type="email"
              placeholder="you@example.com"
              autoComplete="email"
              value={values.email}
              onChange={update("email")}
              error={fieldErrors.email}
              required
            />
            <LightField
              label="Password"
              name="password"
              type="password"
              placeholder={tab === "signup" ? "10 characters or more" : "Your password"}
              autoComplete={tab === "signin" ? "current-password" : "new-password"}
              hint={tab === "signup" ? "Use at least 10 characters." : undefined}
              value={values.password}
              onChange={update("password")}
              error={fieldErrors.password}
              required
            />

            {formError ? (
              <p className="rounded border border-[#d31c2a] bg-[#fdeaea] px-4 py-3 text-[14px] font-semibold text-[#a41b25]">
                {formError}
              </p>
            ) : null}

            <button
              type="submit"
              disabled={busy}
              className="h-12 w-full rounded-full bg-flame text-[16px] font-semibold text-cream shadow-ember transition hover:scale-[1.01] disabled:opacity-60 press"
            >
              {busy ? (
                <span className="inline-flex items-center gap-2">
                  <Spinner /> One moment
                </span>
              ) : tab === "signin" ? (
                "Log in"
              ) : (
                "Sign up free"
              )}
            </button>
          </form>

          <div className="mt-6 border-t border-[#e0e0e0] pt-5 text-center text-[15px] text-[#4d4d4d]">
            {tab === "signin" ? "Don't have an account? " : "Already have an account? "}
            <button
              type="button"
              onClick={() => switchTab(tab === "signin" ? "signup" : "signin")}
              className="font-bold text-paper underline underline-offset-4"
            >
              {tab === "signin" ? "Sign up for free" : "Log in"}
            </button>
          </div>
        </div>

        <p className="mt-6 text-center text-[13px] text-[#7a7a7a]">
          By continuing you agree to keep other people's music your own.
        </p>
      </main>
    </div>
  );
}
