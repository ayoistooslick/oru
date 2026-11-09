import { useEffect, useRef } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  ChevronLeft,
  ChevronRight,
  House,
  Inbox,
  Library,
  LogOut,
  Mic,
  Plus,
  Search as SearchIcon,
  Share2,
  ShieldCheck,
} from "./icons";
import { request } from "../lib/api";
import { useResource } from "../hooks/useResource";
import type { Paginated, Playlist } from "../lib/types";
import { useAuth } from "../state/AuthContext";
import { Avatar } from "./Avatar";
import { OruLogo } from "./OruMark";
import { PlayerBar } from "./PlayerBar";
import { NowPlaying } from "./NowPlaying";
import { OnlineStatusBanner } from "./OnlineStatusBanner";
import { Tutorial } from "./Tutorial";

interface NavItem {
  to: string;
  label: string;
  icon: typeof House;
}

const PRIMARY: NavItem[] = [
  { to: "/listen", label: "Home", icon: House },
  { to: "/search", label: "Search", icon: SearchIcon },
  { to: "/library", label: "Your Library", icon: Library },
];

function NavItemRow({ to, label, icon: Icon }: NavItem) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `flex items-center gap-4 rounded-xl px-3 py-2.5 text-[14.5px] font-semibold transition ${
          isActive ? "bg-flame-soft text-ink" : "text-ink-soft hover:text-ink"
        }`
      }
    >
      {({ isActive }) => (
        <>
          <Icon className={`h-[22px] w-[22px] ${isActive ? "text-flame-ink" : "text-ink-soft"}`} strokeWidth={2} />
          {label}
        </>
      )}
    </NavLink>
  );
}



function Sidebar() {
  const { user, signOut, isAdmin, isArtist } = useAuth();
  const playlists = useResource<Paginated<Playlist>>(
    (signal) => request("/playlists?limit=20", { signal }),
    [],
  );

  if (!user) return null;
  const items = playlists.data?.items ?? [];

  return (
    <aside className="hidden w-[300px] shrink-0 flex-col gap-2 lg:flex">
      <div className="rounded-xl border border-hair bg-paper px-4 pt-4 pb-3">
        <Link to="/listen" aria-label="ORU home" className="mb-4 block">
          <OruLogo />
        </Link>
        <nav>
          <ul className="space-y-1">
            {PRIMARY.map((item) => (
              <li key={item.to}>
                <NavItemRow {...item} />
              </li>
            ))}
            <li>
              <NavItemRow to="/studio" label="Studio" icon={Mic} />
            </li>
            <li>
              <NavItemRow to="/shares" label="Share a song" icon={Share2} />
            </li>
            {isArtist || isAdmin ? (
              <li>
                <NavItemRow to="/shares#share-inbox" label="Share inbox" icon={Inbox} />
              </li>
            ) : null}
            {isAdmin ? (
              <li>
                <NavItemRow to="/moderation" label="Reports" icon={ShieldCheck} />
              </li>
            ) : null}
          </ul>
        </nav>
      </div>

      <div className="flex min-h-0 flex-1 flex-col rounded-xl border border-hair bg-paper p-4">
        <div className="mb-3 flex items-center justify-between">
          <p className="text-[13px] font-bold uppercase tracking-wide text-ink-faint">Your playlists</p>
          <Link
            to="/library"
            className="grid h-8 w-8 place-items-center rounded-full text-ink-soft transition hover:bg-ink/10 hover:text-ink"
            aria-label="Create a playlist"
          >
            <Plus className="h-5 w-5" />
          </Link>
        </div>

        {items.length === 0 ? (
          <div className="rounded-xl bg-ink/5 p-3">
            <p className="text-[13px] leading-relaxed text-ink-soft">No playlists yet.</p>
            <Link
              to="/library"
              className="mt-2 inline-block text-[13px] font-bold text-flame-ink underline underline-offset-4"
            >
              Make one
            </Link>
          </div>
        ) : (
          <ul className="min-h-0 flex-1 space-y-1 overflow-y-auto no-scrollbar">
            {items.map((playlist) => (
              <li key={playlist.id}>
                <Link
                  to={`/library/${playlist.id}`}
                  className="flex items-center justify-between gap-2 rounded-lg px-2 py-2 transition hover:bg-ink/8"
                >
                  <span className="min-w-0">
                    <span className="block truncate text-[14.5px] font-semibold">{playlist.title}</span>
                    <span className="block truncate text-[12.5px] text-ink-soft">
                      {playlist.song_count ?? 0} songs
                    </span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="rounded-xl border border-hair bg-paper p-3">
        <div className="flex items-center gap-3">
          <Link to="/settings" className="flex min-w-0 flex-1 items-center gap-3 press">
            <Avatar name={user.display_name} src={user.avatar_url} />
            <span className="min-w-0">
              <span className="block truncate text-[14px] font-semibold">{user.display_name}</span>
              <span className="block truncate text-[12.5px] text-ink-soft">@{user.handle}</span>
            </span>
          </Link>
          <button
            type="button"
            onClick={signOut}
            className="grid h-9 w-9 place-items-center rounded-full text-ink-soft transition hover:bg-ink/10 hover:text-ink"
            aria-label="Sign out"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </div>
    </aside>
  );
}

function TopBar() {
  const navigate = useNavigate();
  const { user } = useAuth();

  return (
    <div className="sticky top-0 z-20 hidden items-center justify-between gap-3 bg-gradient-to-b from-paper/85 to-transparent px-4 py-3 sm:px-6 lg:flex">
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="grid h-8 w-8 place-items-center rounded-full bg-paper-deep/80 text-ink transition hover:bg-paper-deep"
          aria-label="Go back"
        >
          <ChevronLeft className="h-5 w-5" />
        </button>
        <button
          type="button"
          onClick={() => navigate(1)}
          className="hidden h-8 w-8 place-items-center rounded-full bg-paper-deep/80 text-ink transition hover:bg-paper-deep sm:grid"
          aria-label="Go forward"
        >
          <ChevronRight className="h-5 w-5" />
        </button>
      </div>
      <Link to="/settings" className="rounded-full bg-paper-deep/80 p-1 transition hover:bg-paper-deep" aria-label="Your account">
        <Avatar name="me" src={user?.avatar_url} size="h-7 w-7" />
      </Link>
    </div>
  );
}

function MobileShell() {
  const { user } = useAuth();

  if (!user) return null;

  return (
    <>
      <header className="flex shrink-0 items-center justify-between border-b border-hair bg-paper px-4 py-3 lg:hidden">
        <Link to="/listen" aria-label="ORU home">
          <OruLogo />
        </Link>
        <Link to="/settings" aria-label="Your account" className="flex items-center gap-2">
          <Avatar name={user.display_name} src={user.avatar_url} size="h-8 w-8" />
        </Link>
      </header>
    </>
  );
}

const TABS: (NavItem & { tour?: string })[] = [
  { to: "/listen", label: "Home", icon: House, tour: "tab-home" },
  { to: "/search", label: "Search", icon: SearchIcon, tour: "tab-search" },
  { to: "/library", label: "Library", icon: Library, tour: "tab-library" },
  { to: "/shares", label: "Share", icon: Share2 },
  { to: "/studio", label: "Studio", icon: Mic, tour: "tab-studio" },
];

function BottomTabs() {
  return (
    <nav
      className="flex shrink-0 border-t border-hair bg-paper pt-1.5 lg:hidden"
      style={{ paddingBottom: "env(safe-area-inset-bottom, 0px)" }}
      aria-label="Main"
    >
      {TABS.map(({ to, label, icon: Icon, tour }) => (
        <NavLink
          key={to}
          to={to}
          data-tour={tour}
          className={({ isActive }) =>
            `flex flex-1 flex-col items-center gap-1 py-2 text-[11px] font-bold transition ${
              isActive ? "text-flame-ink" : "text-ink-faint"
            }`
          }
        >
          <Icon className="h-6 w-6" strokeWidth={2} />
          {label}
        </NavLink>
      ))}
    </nav>
  );
}

export function AppShell() {
  const location = useLocation();
  const scrollRegion = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRegion.current?.scrollTo({ top: 0 });
  }, [location.pathname]);

  // #root is scaled by `zoom` (0.93 desktop / 0.85 mobile), which shrinks a
  // plain 100dvh box to ~93%/85% of the real viewport and leaves a gap under
  // the bottom nav. Divide by the zoom factor so the shell fills the screen
  // exactly after scaling: 100/0.93 = 107.527, 100/0.85 = 117.647.
  return (
    <div className="flex h-[107.527dvh] w-full flex-col overflow-hidden bg-paper text-ink max-sm:h-[117.647dvh]">
      <MobileShell />

      <div className="flex min-h-0 flex-1 flex-col lg:flex-row lg:gap-2 lg:p-2">
        <Sidebar />

        <main
          ref={scrollRegion}
          className="relative min-h-0 min-w-0 flex-1 overflow-y-auto overscroll-contain bg-paper lg:rounded-xl lg:border lg:border-hair"
        >
          <TopBar />
          <OnlineStatusBanner />
          <Outlet />
        </main>
      </div>

      <PlayerBar />
      <BottomTabs />
      <NowPlaying />
      <Tutorial />
    </div>
  );
}
