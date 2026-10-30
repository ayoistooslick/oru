import { Link, useLocation } from "react-router-dom";

export function NotFound() {
  const location = useLocation();

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-16 text-center sm:px-6">
      <p className="text-[72px] font-extrabold leading-none tracking-[-0.05em] text-white/15 select-none">404</p>
      <h1 className="mt-4 text-[28px] font-extrabold tracking-[-0.03em]">Page not found</h1>
      <p className="mx-auto mt-3 max-w-md text-[15.5px] leading-relaxed text-ink-soft">
        Nothing lives at <span className="font-semibold text-white">{location.pathname}</span>.
      </p>
      <div className="mt-7 flex flex-wrap justify-center gap-3">
        <Link
          to="/listen"
          className="h-11 rounded-full bg-[#1ed760] px-6 text-[15px] font-bold leading-[44px] text-black press"
        >
          Go to Home
        </Link>
        <Link
          to="/search"
          className="h-11 rounded-full border border-white/30 px-6 text-[15px] font-bold leading-[42px] text-white press hover:bg-white/10"
        >
          Search
        </Link>
      </div>
    </div>
  );
}
