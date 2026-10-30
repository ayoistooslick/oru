import { BrowserRouter, Navigate, Route, Routes, useLocation, useSearchParams } from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { Spinner } from "./components/ui";
import { ToastViewport } from "./components/ToastViewport";
import { StartupCapabilityGate } from "./components/StartupCapabilityGate";
import { OruMark } from "./components/OruMark";
import { AuthProvider, useAuth } from "./state/AuthContext";
import { LibraryProvider } from "./state/LibraryContext";
import { PlayerProvider } from "./state/PlayerContext";
import { ToastProvider } from "./state/ToastContext";
import { Landing } from "./pages/Landing";
import { AuthPage } from "./pages/Auth";
import { Listen } from "./pages/Listen";
import { SearchPage } from "./pages/Search";
import { Library } from "./pages/Library";
import { PlaylistPage } from "./pages/Playlist";
import { ArtistPage } from "./pages/Artist";
import { ReleasePage } from "./pages/Release";
import { Studio } from "./pages/Studio";
import { SettingsPage } from "./pages/Settings";
import { Moderation } from "./pages/Moderation";
import { SongSharing } from "./pages/SongSharing";
import { About } from "./pages/About";
import { NotFound } from "./pages/NotFound";

function FullScreenLoader({ label }: { label: string }) {
  return (
    <div className="grid h-[100dvh] place-items-center bg-paper">
      <div className="flex flex-col items-center gap-4">
        <OruMark className="h-9 w-9" />
        <Spinner className="h-5 w-5 text-ink-faint" />
        <p className="text-[13px] font-semibold text-ink-faint">{label}</p>
      </div>
    </div>
  );
}

/** Where the OS share sheet and web+oru links land: straight into search. */
function ShareHandoff() {
  const [params] = useSearchParams();
  const term = params.get("url") || params.get("text") || params.get("title") || "";
  const target = term.trim() ? `/search?q=${encodeURIComponent(term.trim())}` : "/search";
  return <Navigate to={target} replace />;
}

function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) return <FullScreenLoader label="Opening your account" />;
  if (!user) {
    const returnTo = `${location.pathname}${location.search}`;
    return <Navigate to={`/auth?returnTo=${encodeURIComponent(returnTo)}`} replace />;
  }
  return (
    <LibraryProvider userId={user.id}>
      <AppShell />
    </LibraryProvider>
  );
}

export default function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <PlayerProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/" element={<Landing />} />
              <Route path="/auth" element={<AuthPage />} />
              <Route path="/about" element={<About />} />
              <Route path="/share" element={<ShareHandoff />} />
              <Route element={<RequireAuth />}>
                <Route path="/listen" element={<Listen />} />
                <Route path="/search" element={<SearchPage />} />
                <Route path="/library" element={<Library />} />
                <Route path="/library/:playlistId" element={<PlaylistPage />} />
                <Route path="/artist/:artistId" element={<ArtistPage />} />
                <Route path="/release/:releaseId" element={<ReleasePage />} />
                <Route path="/studio" element={<Studio />} />
                <Route path="/settings" element={<SettingsPage />} />
                <Route path="/moderation" element={<Moderation />} />
                <Route path="/shares" element={<SongSharing />} />
                <Route path="*" element={<NotFound />} />
              </Route>
            </Routes>
          </BrowserRouter>
          <ToastViewport />
          <StartupCapabilityGate />
        </PlayerProvider>
      </AuthProvider>
    </ToastProvider>
  );
}
