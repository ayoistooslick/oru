import { useMemo, useState, type FormEvent, type InputHTMLAttributes } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { ApiError } from "../lib/api";
import { useAuth } from "../state/AuthContext";
import { useToasts } from "../state/ToastContext";
import { OruLogo } from "../components/OruMark";

type Mode = "signin" | "signup";

function LightField({
  label,
  hint,
  error,
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }) {
  return (
    <label className="block">
      <span className="text-[13px] font-bold text-[#121212]">{label}</span>
      <input
        className={`mt-1.5 h-12 w-full rounded border-2 bg-white px-4 text-[15px] text-black outline-none transition placeholder:text-[#757575] ${
          error ? "border-[#d31c2a]" : "border-[#757575] focus:border-black"
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

export function AuthPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { user, loading, signIn, signUp } = useAuth();
  const toasts = useToasts();

  const returnTo = params.get("returnTo") || "/listen";
  const mode: Mode = useMemo(() => (params.get("mode") === "signin" ? "signin" : "signup"), [params]);

  const [tab, setTab] = useState<Mode>(mode);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [values, setValues] = useState({ email: "", password: "", handle: "", display_name: "" });

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
        await signUp({
          email: values.email.trim(),
          handle: values.handle.trim(),
          display_name: values.display_name.trim(),
          password: values.password,
        });
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

  return (
    <div className="flex min-h-[100dvh] flex-col bg-black px-4 py-8">
      <Link to="/" className="mx-auto" aria-label="ORU home">
        <OruLogo />
      </Link>

      <main className="mx-auto flex w-full max-w-[480px] flex-1 flex-col justify-center py-10">
        <div className="rounded-lg bg-white p-8 shadow-[0_16px_48px_rgba(0,0,0,0.5)] sm:p-10">
          <h1 className="text-[30px] font-extrabold tracking-[-0.02em] text-black">
            {tab === "signin" ? "Log in to ORU" : "Create your account"}
          </h1>
          <p className="mt-2 text-[14.5px] text-[#4d4d4d]">
            {tab === "signin" ? "Welcome back." : "Free to join. One minute to finish."}
          </p>

          <form onSubmit={submit} className="mt-6 space-y-4">
            {tab === "signup" ? (
              <>
                <LightField
                  label="Name"
                  name="display_name"
                  placeholder="Your name"
                  autoComplete="name"
                  value={values.display_name}
                  onChange={update("display_name")}
                  error={fieldErrors.display_name}
                  required
                />
                <LightField
                  label="Username"
                  name="handle"
                  placeholder="yourname"
                  autoComplete="username"
                  hint="Letters, numbers and underscores only."
                  value={values.handle}
                  onChange={update("handle")}
                  error={fieldErrors.handle}
                  required
                />
              </>
            ) : null}

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
              className="h-12 w-full rounded-full bg-[#1ed760] text-[16px] font-bold text-black transition hover:bg-[#3be477] disabled:opacity-60"
            >
              {busy ? "One moment" : tab === "signin" ? "Log in" : "Sign up free"}
            </button>
          </form>

          <div className="mt-6 border-t border-[#e0e0e0] pt-5 text-center text-[15px] text-[#4d4d4d]">
            {tab === "signin" ? "Don't have an account? " : "Already have an account? "}
            <button
              type="button"
              onClick={() => switchTab(tab === "signin" ? "signup" : "signin")}
              className="font-bold text-black underline underline-offset-4"
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
