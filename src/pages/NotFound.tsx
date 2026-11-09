import { Link, useLocation } from "react-router-dom";

export function NotFound() {
  const location = useLocation();

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-16 text-center sm:px-6">
      <p className="text-[72px] font-extrabold leading-none tracking-[-0.05em] text-ink/10 select-none">404</p>
      <h1 className="mt-4 text-[28px] font-extrabold tracking-[-0.03em]">Page not found</h1>
      <p className="mx-auto mt-3 max-w-md text-[15.5px] leading-relaxed text-ink-soft">
        Nothing lives at <span className="font-semibold text-ink">{location.pathname}</span>.
      </p>
      <div className="mt-7 flex flex-wrap justify-center gap-3">
        <Link
          to="/listen"
          className="h-11 rounded-full bg-flame px-6 text-[15px] font-semibold leading-[44px] text-cream shadow-ember press"
        >
          Go to Home
        </Link>
        <Link
          to="/search"
          className="h-11 rounded-full border border-hair px-6 text-[15px] font-bold leading-[42px] text-ink press hover:bg-ink/8"
        >
          Search
        </Link>
      </div>
    </div>
  );
}
