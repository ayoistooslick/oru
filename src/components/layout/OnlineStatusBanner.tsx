import { useEffect, useState } from "react";

export function OnlineStatusBanner() {
  const [online, setOnline] = useState(() => navigator.onLine);

  useEffect(() => {
    const onOnline = () => setOnline(true);
    const onOffline = () => setOnline(false);
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, []);

  if (online) return null;
  return (
    <div role="status" className="border-b border-amber-200/15 bg-amber-300/10 px-4 py-2.5 text-center text-[13.5px] font-semibold text-amber-100 sm:px-6">
      You’re offline. ORU’s app shell is available; sign-in, library updates, search and music streaming need a connection.
    </div>
  );
}
