import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, Github, Globe } from "lucide-react";
import { OruLogo, OruMark } from "../components/OruMark";

const PORTRAIT = "https://res.cloudinary.com/dejkqmt3q/image/upload/v1791147133/linkforge/file_amiw7z.jpg";
const PORTFOLIO = "https://ayo-portal.netlify.app";
const GITHUB = "https://github.com/ayoistooslick";

export function About() {
  const [portraitBroken, setPortraitBroken] = useState(false);

  return (
    <div className="min-h-[100dvh] bg-black text-white">
      <header className="sticky top-0 z-40 bg-black/85 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-4xl items-center justify-between px-5 sm:px-8">
          <Link to="/" aria-label="ORU home">
            <OruLogo />
          </Link>
          <Link
            to="/"
            className="inline-flex h-10 items-center gap-2 rounded-full border border-white/30 px-4 text-[14.5px] font-bold transition hover:bg-white/10"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to ORU
          </Link>
        </div>
      </header>

      <main className="mx-auto w-full max-w-3xl px-5 pb-20 pt-8 sm:px-8">
        <div className="flex items-center gap-5">
          {portraitBroken ? (
            <span className="grid h-28 w-28 shrink-0 place-items-center rounded-full bg-[#181818]">
              <OruMark className="h-12 w-12" />
            </span>
          ) : (
            <img
              src={PORTRAIT}
              alt="Portrait of Ayodele Ayokunle David"
              width={112}
              height={112}
              loading="lazy"
              onError={() => setPortraitBroken(true)}
              className="h-28 w-28 shrink-0 rounded-full border border-white/10 object-cover"
            />
          )}
          <div className="min-w-0">
            <p className="label-caps">About the developer</p>
            <h1 className="mt-1.5 text-[clamp(1.7rem,6vw,2.6rem)] font-extrabold leading-[1.05] tracking-[-0.035em]">
              Ayodele Ayokunle David
            </h1>
          </div>
        </div>

        <p className="mt-6 text-[18px] leading-relaxed text-white">
          I am a teenager who builds software and experiments with useful ideas.
        </p>

        <div className="mt-4 space-y-4 text-[16px] leading-relaxed text-ink-soft">
          <p>
            ORU is one of those ideas. It started small: a place where new artists could put a song up and be heard
            without waiting for anybody to pick them. I built the whole thing myself, the interface, the server, and the
            parts that keep the music playing.
          </p>
          <p>
            There is no big company behind it and no invented story. Just code I am happy to put my name on.
          </p>
          <p>If you want to see the other things I am working on, have a look at my portfolio.</p>
        </div>

        <div className="mt-7 flex flex-wrap gap-3">
          <a
            href={PORTFOLIO}
            target="_blank"
            rel="noreferrer noopener"
            className="inline-flex h-11 items-center gap-2 rounded-full bg-[#1ed760] px-5 text-[15px] font-bold text-black transition press hover:scale-105"
          >
            <Globe className="h-4 w-4" />
            ayo-portal.netlify.app
          </a>
          <a
            href={GITHUB}
            target="_blank"
            rel="noreferrer noopener"
            className="inline-flex h-11 items-center gap-2 rounded-full border border-white/30 px-5 text-[15px] font-bold transition hover:bg-white/10"
          >
            <Github className="h-4 w-4" />
            ayoistooslick on GitHub
          </a>
          <Link
            to="/listen"
            className="inline-flex h-11 items-center rounded-full border border-white/30 px-5 text-[15px] font-bold transition hover:bg-white/10"
          >
            Open ORU
          </Link>
        </div>

        <p className="mt-10 border-t border-white/10 pt-6 text-[13.5px] text-ink-faint">
          ORU 1.0 · Made for upcoming musicians · Free on any phone
        </p>
      </main>
    </div>
  );
}
