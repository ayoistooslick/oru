import { useLayoutEffect, useRef, type ReactNode } from "react";
import { Link } from "react-router-dom";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { ArrowRight, Download, Headphones, Mic, Pause, Play, Upload } from "../components/icons";
import { OruLogo } from "../components/OruMark";
import { motionSafe } from "../lib/motion";
import { useInstallPrompt } from "../hooks/useInstallPrompt";

gsap.registerPlugin(ScrollTrigger);

const STEPS = [
  { icon: Upload, title: "Upload", body: "Put your song and a cover picture into the studio." },
  { icon: Mic, title: "Publish", body: "It stays private until you press publish." },
  { icon: Headphones, title: "Get heard", body: "Your song shows up for people who are listening." },
];

const PROOF = [
  { stat: "Free", label: "No subscription, ever" },
  { stat: "Any phone", label: "Works in the browser" },
  { stat: "You keep it", label: "Your music stays yours" },
];

function PlayerMock() {
  return (
    <div className="relative mx-auto w-full max-w-sm" aria-hidden="true">
      <div className="rounded-2xl bg-card p-4 shadow-float ring-1 ring-hair">
        <div className="flex items-center gap-3">
          <span className="grid h-14 w-14 shrink-0 place-items-center rounded-lg bg-[#3a2a1e] text-[20px] font-extrabold text-cream">
            F
          </span>
          <div className="min-w-0">
            <p className="truncate text-[15px] font-bold">First Light</p>
            <p className="truncate text-[13px] text-ink-soft">Ade Omolara</p>
          </div>
          <span className="ml-auto grid h-9 w-9 place-items-center rounded-full bg-flame text-cream">
            <Play className="h-4 w-4 translate-x-[1px]" fill="currentColor" />
          </span>
        </div>
        <div className="mt-4 rounded-xl bg-paper-deep p-3 ring-1 ring-hair">
          <div className="flex items-center gap-3">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-[#2e4034] text-[14px] font-extrabold text-cream">
              N
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-[13.5px] font-bold">Night Bus to Yaba</span>
              <span className="block truncate text-[12px] text-ink-soft">Tobi Kestrel</span>
            </span>
            <Pause className="h-5 w-5 text-ink" fill="currentColor" />
          </div>
          <div className="mt-3 h-1.5 w-full rounded-full bg-hair">
            <div className="h-full w-[38%] rounded-full bg-flame" />
          </div>
          <div className="mt-1.5 flex justify-between text-[10.5px] tabular-nums text-ink-soft">
            <span>1:12</span>
            <span>3:04</span>
          </div>
        </div>
      </div>

      <div className="absolute -right-3 -bottom-7 flex items-center gap-2 rounded-xl bg-card-hi px-3 py-2.5 shadow-float ring-1 ring-hair sm:-right-8">
        <span className="h-2 w-2 rounded-full bg-flame-ink" />
        <span className="text-[12.5px] font-bold">1 new play</span>
      </div>
    </div>
  );
}

function Section({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <section data-reveal className={`px-5 py-14 sm:px-8 lg:px-14 ${className}`}>
      <div className="mx-auto max-w-6xl">{children}</div>
    </section>
  );
}

export function Landing() {
  const root = useRef<HTMLDivElement>(null);
  const { canInstall, install } = useInstallPrompt();

  useLayoutEffect(() => {
    const element = root.current;
    if (!element) return;
    const reduce = motionSafe();

    const ctx = gsap.context(() => {
      if (reduce) return;

      gsap
        .timeline({ defaults: { ease: "power3.out" } })
        .from("[data-hero-copy] > *", { y: 24, opacity: 0, duration: 0.65, stagger: 0.08 }, 0)
        .from("[data-hero-art]", { y: 36, opacity: 0, scale: 0.96, duration: 0.8 }, 0.15);

      gsap.utils.toArray<HTMLElement>("[data-reveal]").forEach((block) => {
        gsap.from(block.children, {
          scrollTrigger: { trigger: block, start: "top 85%", once: true },
          y: 28,
          opacity: 0,
          duration: 0.6,
          stagger: 0.07,
          ease: "power3.out",
          clearProps: "transform,opacity",
        });
      });
    }, element);

    return () => ctx.revert();
  }, []);

  return (
    <div ref={root} className="min-h-[100dvh] bg-paper text-ink">
      <header className="sticky top-0 z-40 border-b border-hair bg-paper/85 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5 sm:px-8">
          <Link to="/" aria-label="ORU home">
            <OruLogo />
          </Link>
          <nav className="flex items-center gap-3">
            <Link to="/auth?mode=signin" className="text-[14.5px] font-semibold text-ink-soft transition hover:text-ink">
              Log in
            </Link>
            <Link
              to="/auth"
              className="h-10 rounded-full bg-flame px-5 text-[14.5px] font-semibold leading-10 text-cream transition hover:bg-flame-ink press"
            >
              Sign up free
            </Link>
          </nav>
        </div>
      </header>

      <main>
        <section className="relative overflow-hidden px-5 pt-12 pb-20 sm:px-8 lg:px-14 lg:pt-20">
          <div className="mx-auto grid max-w-6xl items-center gap-12 lg:grid-cols-[1.05fr_0.95fr]">
            <div data-hero-copy className="max-w-xl">
              <span className="mb-6 inline-flex items-center gap-2 rounded-full bg-flame-soft px-3.5 py-1.5 text-[13px] font-semibold text-flame-ink ring-1 ring-flame/30">
                <span className="h-1.5 w-1.5 rounded-full bg-flame-ink" />
                For artists who are just starting
              </span>
              <h1 className="font-display text-[clamp(2.5rem,7vw,4.2rem)] font-extrabold leading-[1.02] tracking-[-0.03em]">
                Your song deserves its <span className="text-flame-ink">first listener</span>.
              </h1>
              <p className="mt-5 max-w-lg text-[17px] leading-relaxed text-ink-soft sm:text-[18px]">
                Upload your music, publish it, and reach people who actually listen. No label. No ad budget. No gatekeeper.
              </p>
              <div className="mt-8 flex flex-wrap items-center gap-3">
                <Link
                  to="/auth"
                  className="h-12 rounded-full bg-flame px-7 text-[15.5px] font-semibold leading-[48px] text-cream transition hover:bg-flame-ink press"
                >
                  Create free account
                </Link>
                <Link
                  to="/auth?mode=signin"
                  className="h-12 rounded-full border border-hair px-7 text-[15.5px] font-semibold leading-[46px] text-ink transition hover:border-ink-soft hover:bg-ink/8"
                >
                  I have an account
                </Link>
              </div>
              <p className="mt-5 text-[14px] text-ink-faint">Free · Works on any phone</p>
              {canInstall ? (
                <button
                  type="button"
                  onClick={() => void install()}
                  className="mt-4 inline-flex h-11 items-center gap-2 rounded-full border border-hair px-5 text-[14.5px] font-semibold text-ink-soft transition hover:bg-ink/8 hover:text-ink"
                >
                  <Download className="h-4 w-4" />
                  Install the app
                </button>
              ) : null}
            </div>

            <div data-hero-art className="relative">
              <div className="absolute -inset-8 rounded-full bg-flame/12 blur-3xl" aria-hidden="true" />
              <PlayerMock />
            </div>
          </div>
        </section>

        <Section className="border-t border-hair">
          <div className="grid gap-4 sm:grid-cols-3">
            {PROOF.map((item) => (
              <div key={item.stat} className="rounded-2xl bg-card p-6 ring-1 ring-hair">
                <p className="font-display text-[26px] font-extrabold tracking-[-0.02em]">{item.stat}</p>
                <p className="mt-1 text-[14.5px] text-ink-soft">{item.label}</p>
              </div>
            ))}
          </div>
        </Section>

        <Section className="border-t border-hair">
          <h2 className="font-display text-[28px] font-extrabold tracking-[-0.02em] sm:text-[34px]">Three steps</h2>
          <div className="mt-7 grid gap-4 sm:grid-cols-3">
            {STEPS.map((step, index) => {
              const Icon = step.icon;
              return (
                <div key={step.title} className="rounded-2xl bg-card p-6 ring-1 ring-hair">
                  <span className="grid h-11 w-11 place-items-center rounded-full bg-flame text-cream">
                    <Icon className="h-5 w-5" />
                  </span>
                  <p className="mt-4 label-caps">Step {index + 1}</p>
                  <h3 className="mt-1 font-display text-[21px] font-extrabold">{step.title}</h3>
                  <p className="mt-2 text-[15px] leading-relaxed text-ink-soft">{step.body}</p>
                </div>
              );
            })}
          </div>
        </Section>

        <Section className="border-t border-hair">
          <div className="overflow-hidden rounded-3xl bg-gradient-to-br from-flame to-[#c2431f] px-6 py-12 text-center text-cream shadow-ember sm:px-10">
            <h2 className="font-display text-[30px] font-semibold tracking-[-0.02em] sm:text-[40px]">
              Put your first song up today.
            </h2>
            <p className="mx-auto mt-3 max-w-xl text-[16.5px] leading-relaxed text-cream/85">
              It takes about a minute to make an account. Your uploads stay private until you say otherwise.
            </p>
            <Link
              to="/auth"
              className="mt-7 inline-flex items-center gap-2 rounded-full bg-paper px-8 text-[15.5px] font-semibold text-cream shadow-float transition hover:scale-105 press"
            >
              Get started
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </Section>
      </main>

      <footer className="border-t border-hair px-5 py-10 sm:px-8 lg:px-14">
        <div className="mx-auto flex max-w-6xl flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <OruLogo />
          <p className="text-[13.5px] text-ink-soft">
            Built by <span className="font-bold text-ink">Ayodele Ayokunle David</span>
            <span className="mx-1.5 opacity-50">·</span>
            <Link to="/about" className="font-semibold text-ink hover:underline">
              About the developer
            </Link>
          </p>
          <p className="text-[13.5px] text-ink-faint">ORU 1.0 · Made for upcoming musicians</p>
        </div>
      </footer>
    </div>
  );
}
