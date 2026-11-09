import { useLayoutEffect, useRef, type ReactNode } from "react";
import { Link } from "react-router-dom";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import {
  Check,
  Download,
  Headphones,
  Heart,
  ImagePlus,
  Library,
  Play,
  Share2,
  Upload,
} from "../components/icons";
import { OruLogo } from "../components/OruMark";
import { motionSafe } from "../lib/motion";
import { APK_URL } from "../lib/links";

gsap.registerPlugin(ScrollTrigger);

const FEATURES = [
  {
    icon: Upload,
    title: "Publish your own music",
    body: "Record a take in the studio or upload a finished file, give it a cover, and publish it when you are ready.",
  },
  {
    icon: Headphones,
    title: "A player built for long sessions",
    body: "Queue, shuffle, repeat, a sleep timer that keeps counting with the screen off, and controls on the lock screen.",
  },
  {
    icon: ImagePlus,
    title: "A profile that looks like you",
    body: "Put a face on your account and a wide banner behind it. Artists get a stage name and a page of their own.",
  },
  {
    icon: Library,
    title: "Playlists with their own artwork",
    body: "Build playlists, give each one a cover and a banner, and like the songs you keep coming back to.",
  },
  {
    icon: Download,
    title: "Downloads that work without signal",
    body: "Save a song to your phone and it plays from storage, on the bus or in a dead spot.",
  },
  {
    icon: Share2,
    title: "Send a song to a friend",
    body: "Share a track to WhatsApp or anywhere else. The link plays in the browser, and opens straight into the app for whoever has it installed.",
  },
];

const STEPS = [
  {
    title: "Download the file",
    body: "Tap the download button. The browser saves a zip holding the ORU installer.",
  },
  {
    title: "Open it and allow the install",
    body: "Your phone asks to allow installs from this browser once. Allow it, then tap Install.",
  },
  {
    title: "Sign in and start listening",
    body: "Open ORU and continue with Google. Sign-in finishes in your browser and comes straight back.",
  },
];

const FAQ = [
  {
    q: "Android says the file cannot be installed",
    a: "Open Settings, find Apps, then Special access, then Install unknown apps, and allow your browser. That permission is what the system needs to install an app that did not come from the Play Store.",
  },
  {
    q: "How does Google sign-in work?",
    a: "Google will only sign you in inside a browser, so ORU opens its own page for the sign-in and then returns you to the app already signed in. You only do this once per account.",
  },
  {
    q: "Will an update keep my music?",
    a: "Your account, uploads and playlists live on ORU's servers, not on the phone, so they are untouched by an update. Because each build is signed fresh, uninstall the old copy before installing a new one.",
  },
  {
    q: "I am on an iPhone",
    a: "Use ORU in your browser. The whole player, the studio and your library work there, on Safari too. Nothing to install, and the same account works everywhere.",
  },
  {
    q: "What happens when I open a shared song link?",
    a: "On a phone with the app installed, the link opens ORU and plays the song there. Anywhere else, the song plays on a page in your browser, so the person you sent it to hears it first and signs up later if they want.",
  },
];

function PhoneMock() {
  return (
    <div className="relative mx-auto w-[264px]" aria-hidden="true">
      <div className="absolute -inset-10 rounded-full bg-flame/18 blur-3xl" />
      <div className="relative rounded-[2.4rem] border border-hair bg-paper-deep p-2.5 shadow-float">
        <div className="relative overflow-hidden rounded-[1.9rem] bg-[#1d1712]">
          <div className="flex items-center justify-between px-5 pt-3 text-[10px] font-semibold text-ink-faint">
            <span>9:41</span>
            <span className="h-1 w-10 rounded-full bg-ink/25" />
            <span>100%</span>
          </div>

          <div className="px-5 pt-5">
            <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-flame-ink">Now playing</p>
            <div className="mt-3 aspect-square w-full overflow-hidden rounded-2xl bg-gradient-to-br from-[#3a2a1e] to-[#22180f] ring-1 ring-white/5">
              <div className="grid h-full place-items-center">
                <span className="font-display text-[52px] font-extrabold text-cream/90">F</span>
              </div>
            </div>
            <div className="mt-4 flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate text-[15px] font-bold text-cream">First Light</p>
                <p className="truncate text-[12px] text-ink-soft">Ade Omolara</p>
              </div>
              <Heart className="mt-0.5 h-4 w-4 text-flame-ink" fill="currentColor" />
            </div>
            <div className="mt-3 h-1.5 w-full rounded-full bg-white/10">
              <div className="h-full w-[46%] rounded-full bg-flame" />
            </div>
            <div className="mt-1.5 flex justify-between text-[9.5px] tabular-nums text-ink-faint">
              <span>1:12</span>
              <span>3:04</span>
            </div>
            <div className="mt-4 flex items-center justify-center gap-6 pb-5">
              <span className="text-[11px] font-semibold text-ink-faint">1×</span>
              <span className="grid h-11 w-11 place-items-center rounded-full bg-flame text-cream">
                <Play className="h-4 w-4 translate-x-[1px]" fill="currentColor" />
              </span>
              <span className="text-[11px] font-semibold text-ink-faint">⋮</span>
            </div>
          </div>
        </div>
      </div>

      <div className="absolute -right-4 bottom-16 flex items-center gap-2 rounded-xl bg-card-hi px-3 py-2 shadow-float ring-1 ring-hair">
        <span className="h-2 w-2 rounded-full bg-flame-ink" />
        <span className="text-[11.5px] font-bold text-ink">Sleep timer · 30m</span>
      </div>
      <div className="absolute -left-6 top-24 rounded-xl bg-card-hi px-3 py-2 shadow-float ring-1 ring-hair">
        <span className="text-[11.5px] font-bold text-ink">1 new play</span>
      </div>
    </div>
  );
}

function Section({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <section data-reveal className={`px-5 py-16 sm:px-8 lg:px-14 ${className}`}>
      <div className="mx-auto max-w-6xl">{children}</div>
    </section>
  );
}

export function Landing() {
  const root = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const element = root.current;
    if (!element) return;
    const reduce = motionSafe();

    const ctx = gsap.context(() => {
      if (reduce) return;
      gsap
        .timeline({ defaults: { ease: "power3.out" } })
        .from("[data-hero-copy] > *", { y: 24, opacity: 0, duration: 0.6, stagger: 0.07 }, 0)
        .from("[data-hero-art]", { y: 40, opacity: 0, scale: 0.96, duration: 0.85 }, 0.12);

      gsap.utils.toArray<HTMLElement>("[data-reveal]").forEach((block) => {
        gsap.from(block.children, {
          scrollTrigger: { trigger: block, start: "top 85%", once: true },
          y: 26,
          opacity: 0,
          duration: 0.55,
          stagger: 0.06,
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
          <nav className="flex items-center gap-1.5 sm:gap-3">
            <a
              href="#inside"
              className="hidden rounded-full px-3 py-2 text-[14px] font-semibold text-ink-soft transition hover:text-ink sm:inline"
            >
              What's inside
            </a>
            <a
              href="#install"
              className="hidden rounded-full px-3 py-2 text-[14px] font-semibold text-ink-soft transition hover:text-ink sm:inline"
            >
              How to install
            </a>
            <Link to="/about" className="rounded-full px-3 py-2 text-[14px] font-semibold text-ink-soft transition hover:text-ink">
              Developer
            </Link>
            <a
              href={APK_URL}
              className="hidden text-[14px] font-semibold text-ink-soft transition hover:text-ink sm:inline"
            >
              Android app
            </a>
            <Link
              to="/listen"
              className="h-9 rounded-full bg-flame px-4 text-[13.5px] font-semibold leading-9 text-cream transition hover:bg-flame-ink press"
            >
              Listen free
            </Link>
          </nav>
        </div>
      </header>

      <main>
        <section className="relative overflow-hidden px-5 pb-20 pt-14 sm:px-8 lg:px-14 lg:pt-20">
          <div className="mx-auto grid max-w-6xl items-center gap-14 lg:grid-cols-[1.08fr_0.92fr]">
            <div data-hero-copy className="max-w-xl">
              <span className="mb-6 inline-flex items-center gap-2 rounded-full bg-flame-soft px-3.5 py-1.5 text-[12.5px] font-semibold text-flame-ink ring-1 ring-flame/30">
                <span className="h-1.5 w-1.5 rounded-full bg-flame-ink" />
                On the web, and on Android
              </span>
              <h1 className="font-display text-[clamp(2.5rem,7vw,4.1rem)] font-extrabold leading-[1.02] tracking-[-0.03em]">
                New music, <span className="text-flame-ink">wherever you are</span>.
              </h1>
              <p className="mt-5 max-w-lg text-[17px] leading-relaxed text-ink-soft sm:text-[18px]">
                Upload a song, publish it, and listen to what everyone else is making. ORU runs in any
                browser, on an iPhone or a laptop, and the Android app adds downloads and controls that
                keep going with the screen off.
              </p>

              <div className="mt-8 flex flex-wrap items-center gap-3">
                <Link
                  to="/listen"
                  className="inline-flex h-14 items-center gap-3 rounded-full bg-flame px-7 text-[16px] font-bold text-cream shadow-ember transition hover:bg-flame-ink press"
                >
                  <Play className="h-5 w-5" fill="currentColor" />
                  Listen in your browser
                </Link>
                <a
                  href={APK_URL}
                  className="inline-flex h-14 items-center gap-2 rounded-full border border-hair px-6 text-[15.5px] font-semibold text-ink transition hover:border-ink-soft hover:bg-ink/8"
                >
                  <Download className="h-4 w-4" />
                  Get the Android app
                </a>
              </div>

              <ul className="mt-6 flex flex-wrap items-center gap-x-5 gap-y-2 text-[13.5px] text-ink-faint">
                {["iPhone, laptop or Android", "Free, no ads", "Sign in with Google"].map((item) => (
                  <li key={item} className="inline-flex items-center gap-1.5">
                    <Check className="h-3.5 w-3.5 text-flame-ink" />
                    {item}
                  </li>
                ))}
              </ul>
            </div>

            <div data-hero-art>
              <PhoneMock />
            </div>
          </div>
        </section>

        <Section className="border-t border-hair">
          <div id="inside" className="scroll-mt-24" />
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="label-caps">What's inside</p>
              <h2 className="mt-2 font-display text-[clamp(1.8rem,4.5vw,2.6rem)] font-extrabold tracking-[-0.025em]">
                The same ORU, whichever screen you open it on.
              </h2>
            </div>
            <p className="max-w-sm text-[15px] leading-relaxed text-ink-soft">
              One account, one library, one look. These are the parts you will use most.
            </p>
          </div>

          <div className="mt-9 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((feature) => {
              const Icon = feature.icon;
              return (
                <div
                  key={feature.title}
                  className="group rounded-2xl border border-hair bg-card p-6 transition hover:border-flame/40 hover:bg-card-hi"
                >
                  <span className="grid h-11 w-11 place-items-center rounded-full bg-flame-soft text-flame-ink ring-1 ring-flame/25 transition group-hover:bg-flame group-hover:text-cream">
                    <Icon className="h-5 w-5" />
                  </span>
                  <h3 className="mt-5 font-display text-[19px] font-extrabold tracking-[-0.01em]">
                    {feature.title}
                  </h3>
                  <p className="mt-2 text-[14.5px] leading-relaxed text-ink-soft">{feature.body}</p>
                </div>
              );
            })}
          </div>
        </Section>

        <Section className="border-t border-hair">
          <div id="install" className="scroll-mt-24" />
          <p className="label-caps">How to install</p>
          <h2 className="mt-2 max-w-2xl font-display text-[clamp(1.8rem,4.5vw,2.6rem)] font-extrabold tracking-[-0.025em]">                Three taps and you are listening, on Android.
              </h2>
              <p className="mt-3 max-w-xl text-[15px] leading-relaxed text-ink-soft">
                No phone? No problem. Everything below also works in a browser, so an iPhone or a laptop
                needs no install at all.
              </p>

          <ol className="mt-9 grid gap-4 sm:grid-cols-3">
            {STEPS.map((step, index) => (
              <li key={step.title} className="rounded-2xl border border-hair bg-card p-6">
                <span className="grid h-9 w-9 place-items-center rounded-full bg-flame text-[14px] font-extrabold text-cream">
                  {index + 1}
                </span>
                <h3 className="mt-4 font-display text-[19px] font-extrabold">{step.title}</h3>
                <p className="mt-2 text-[14.5px] leading-relaxed text-ink-soft">{step.body}</p>
              </li>
            ))}
          </ol>

          <div className="mt-6 flex flex-col gap-3 rounded-2xl border border-flame/25 bg-flame-soft px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-[14.5px] font-semibold text-ink">
              The download is a zip. Your phone's Files app unpacks it when you tap it.
            </p>
            <a
              href={APK_URL}
              className="inline-flex h-10 shrink-0 items-center gap-2 rounded-full bg-flame px-5 text-[14px] font-bold text-cream transition hover:bg-flame-ink press"
            >
              <Download className="h-4 w-4" />
              Get the file
            </a>
          </div>
        </Section>

        <Section className="border-t border-hair">
          <div className="grid gap-10 rounded-3xl border border-hair bg-card p-7 sm:p-10 lg:grid-cols-[1fr_0.9fr] lg:items-center">
            <div>
              <p className="label-caps">Signing in</p>
              <h2 className="mt-2 font-display text-[clamp(1.6rem,4vw,2.3rem)] font-extrabold tracking-[-0.02em]">
                Google signs you in, the app keeps you signed in.
              </h2>
              <p className="mt-4 text-[16px] leading-relaxed text-ink-soft">
                Google only signs people in inside a browser, so ORU opens its own page for it and hands the
                finished sign-in back to the app. You do it once; the app remembers you after that.
              </p>
            </div>
            <div className="rounded-2xl border border-hair bg-paper-deep p-5">
              <ol className="space-y-4">
                {[
                  "Tap continue with Google in the app",
                  "Pick your account in the browser",
                  "You land back in ORU, signed in",
                ].map((line, index) => (
                  <li key={line} className="flex items-start gap-3 text-[14.5px] font-semibold text-ink">
                    <span className="mt-0.5 grid h-6 w-6 shrink-0 place-items-center rounded-full border border-flame/40 text-[11.5px] font-extrabold text-flame-ink">
                      {index + 1}
                    </span>
                    {line}
                  </li>
                ))}
              </ol>
            </div>
          </div>
        </Section>

        <Section className="border-t border-hair">
          <p className="label-caps">Questions</p>
          <h2 className="mt-2 font-display text-[clamp(1.8rem,4.5vw,2.6rem)] font-extrabold tracking-[-0.025em]">
            The things people ask first.
          </h2>
          <div className="mt-8 grid gap-3 sm:grid-cols-2">
            {FAQ.map((item) => (
              <details
                key={item.q}
                className="group rounded-2xl border border-hair bg-card px-5 py-4 open:border-flame/35"
              >
                <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-[15.5px] font-bold text-ink [&::-webkit-details-marker]:hidden">
                  {item.q}
                  <span className="text-flame-ink transition group-open:rotate-45">+</span>
                </summary>
                <p className="mt-3 text-[14.5px] leading-relaxed text-ink-soft">{item.a}</p>
              </details>
            ))}
          </div>
        </Section>

        <Section className="border-t border-hair">
          <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-flame to-[#c2431f] px-6 py-14 text-center text-cream shadow-ember sm:px-10">
            <h2 className="font-display text-[clamp(1.9rem,5vw,2.9rem)] font-semibold tracking-[-0.02em]">
              Put your first song up today.
            </h2>
            <p className="mx-auto mt-3 max-w-xl text-[16.5px] leading-relaxed text-cream/85">
              It takes about a minute to make an account. Your uploads stay private until you say otherwise.
            </p>
            <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
              <Link
                to="/listen"
                className="inline-flex items-center gap-2 rounded-full bg-cream px-8 py-3.5 text-[15.5px] font-bold text-ink shadow-float transition hover:scale-[1.03] press"
              >
                <Play className="h-4 w-4" fill="currentColor" />
                Start listening
              </Link>
              <a
                href={APK_URL}
                className="inline-flex items-center gap-2 rounded-full border border-cream/40 px-7 py-3.5 text-[15px] font-semibold text-cream transition hover:border-cream/70 press"
              >
                <Download className="h-4 w-4" />
                Download for Android
              </a>
            </div>
            <p className="mt-4 text-[13px] text-cream/70">Free · Web, iPhone and Android 7 and newer</p>
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
