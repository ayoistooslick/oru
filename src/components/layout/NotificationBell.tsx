import { Link } from "react-router-dom";
import { Bell } from "../ui/icons";
import { useNotifications } from "../../state/NotificationsContext";

/** The bell in the header, wearing its unread count. */
export function NotificationBell({ className = "" }: { className?: string }) {
  const { unread } = useNotifications();
  const label = unread > 0 ? `Notifications, ${unread} new` : "Notifications";

  return (
    <Link
      to="/notifications"
      aria-label={label}
      className={`relative grid h-9 w-9 shrink-0 place-items-center rounded-full bg-paper-deep/80 text-ink transition hover:bg-paper-deep press ${className}`}
    >
      <Bell className="h-[18px] w-[18px]" />
      {unread > 0 ? (
        <span className="absolute -right-0.5 -top-0.5 grid h-4 min-w-4 place-items-center rounded-full bg-flame px-1 text-[10px] font-black leading-none text-night">
          {unread > 9 ? "9+" : unread}
        </span>
      ) : null}
    </Link>
  );
}
