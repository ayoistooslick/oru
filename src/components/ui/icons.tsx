import type { ReactNode, SVGProps } from "react";

type IconProps = Omit<SVGProps<SVGSVGElement>, "ref"> & {
  strokeWidth?: number | string;
};

/** One hand-built icon. Outline by default; pass fill="currentColor" to solid. */
function makeIcon(name: string, body: ReactNode) {
  function Icon({ className = "h-4 w-4", strokeWidth = 2, ...rest }: IconProps) {
    return (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        aria-hidden="true"
        focusable="false"
        {...rest}
      >
        {body}
      </svg>
    );
  }
  Icon.displayName = name;
  return Icon;
}

export const ArrowLeft = makeIcon(
  "ArrowLeft",
  <>
    <path d="M19 12H5" />
    <path d="m12 19-7-7 7-7" />
  </>,
);

export const ArrowRight = makeIcon(
  "ArrowRight",
  <>
    <path d="M5 12h14" />
    <path d="m12 5 7 7-7 7" />
  </>,
);

export const Check = makeIcon("Check", <path d="M20 6 9 17l-5-5" />);

export const ChevronDown = makeIcon("ChevronDown", <path d="m6 9 6 6 6-6" />);
export const ChevronLeft = makeIcon("ChevronLeft", <path d="m15 18-6-6 6-6" />);
export const ChevronRight = makeIcon("ChevronRight", <path d="m9 18 6-6-6-6" />);

export const Download = makeIcon(
  "Download",
  <>
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <path d="m7 10 5 5 5-5" />
    <path d="M12 15V3" />
  </>,
);

export const Upload = makeIcon(
  "Upload",
  <>
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <path d="m17 8-5-5-5 5" />
    <path d="M12 3v12" />
  </>,
);

export const UploadCloud = makeIcon(
  "UploadCloud",
  <>
    <path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242" />
    <path d="M12 12v9" />
    <path d="m16 16-4-4-4 4" />
  </>,
);

export const EyeOff = makeIcon(
  "EyeOff",
  <>
    <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68" />
    <path d="M6.61 6.61A13.5 13.5 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61" />
    <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24" />
    <path d="m2 2 20 20" />
  </>,
);

export const Flag = makeIcon(
  "Flag",
  <>
    <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
    <path d="M4 22V4" />
  </>,
);

export const Github = makeIcon(
  "Github",
  <>
    <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.4 5.4 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
    <path d="M9 18c-4.51 2-5-2-7-2" />
  </>,
);

export const Globe = makeIcon(
  "Globe",
  <>
    <path d="M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z" />
    <path d="M2 12h20" />
    <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
  </>,
);

export const Globe2 = makeIcon(
  "Globe2",
  <>
    <path d="M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z" />
    <path d="M2 12h20" />
    <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
  </>,
);

export const Headphones = makeIcon(
  "Headphones",
  <path d="M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3" />,
);

export const Heart = makeIcon(
  "Heart",
  <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />,
);

export const House = makeIcon(
  "House",
  <>
    <path d="M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8" />
    <path d="M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
  </>,
);

export const ImagePlus = makeIcon(
  "ImagePlus",
  <>
    <path d="M16 5h6" />
    <path d="M19 2v6" />
    <path d="M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5" />
    <path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21" />
    <circle cx="9" cy="9" r="2" />
  </>,
);

export const Inbox = makeIcon(
  "Inbox",
  <>
    <path d="M22 12h-6l-2 3h-4l-2-3H2" />
    <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
  </>,
);

export const Library = makeIcon(
  "Library",
  <>
    <path d="m16 6 4 14" />
    <path d="M12 6v14" />
    <path d="M8 8v12" />
    <path d="M4 4v16" />
  </>,
);

export const Loader2 = makeIcon("Loader2", <path d="M21 12a9 9 0 1 1-6.219-8.56" />);

export const Lock = makeIcon(
  "Lock",
  <>
    <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
  </>,
);

export const LogOut = makeIcon(
  "LogOut",
  <>
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <path d="m16 17 5-5-5-5" />
    <path d="M21 12H9" />
  </>,
);

export const Mic = makeIcon(
  "Mic",
  <>
    <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" />
    <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
    <path d="M12 19v3" />
  </>,
);

export const Mic2 = makeIcon(
  "Mic2",
  <>
    <path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" />
    <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
    <path d="M12 19v3" />
  </>,
);

export const MoreHorizontal = makeIcon(
  "MoreHorizontal",
  <>
    <path d="M5 12h.01" />
    <path d="M12 12h.01" />
    <path d="M19 12h.01" />
  </>,
);

export const Music4 = makeIcon(
  "Music4",
  <>
    <path d="M9 18V5l12-2v13" />
    <path d="M6 15a3 3 0 1 0 0 6 3 3 0 0 0 0-6z" />
    <path d="M18 15a3 3 0 1 0 0 6 3 3 0 0 0 0-6z" />
  </>,
);

export const Pause = makeIcon(
  "Pause",
  <>
    <path d="M6 4h4v16H6z" />
    <path d="M14 4h4v16h-4z" />
  </>,
);

export const Play = makeIcon("Play", <path d="m6 3 14 9-14 9V3z" />);

export const PlaylistAdd = makeIcon(
  "PlaylistAdd",
  <>
    <path d="M4 7h10" />
    <path d="M4 12h10" />
    <path d="M4 17h7" />
    <path d="M17 13v8" />
    <path d="M13 17h8" />
  </>,
);

export const Queue = makeIcon(
  "Queue",
  <>
    <path d="M4 7h11" />
    <path d="M4 12h11" />
    <path d="M4 17h7" />
    <path d="M20 7v8" />
    <circle cx="17.5" cy="17" r="2.5" />
  </>,
);

export const Timer = makeIcon(
  "Timer",
  <>
    <circle cx="12" cy="13.5" r="7.5" />
    <path d="M12 10v4l2.5 2" />
    <path d="M9 2.5h6" />
    <path d="M12 2.5v2" />
  </>,
);

export const Plus = makeIcon(
  "Plus",
  <>
    <path d="M5 12h14" />
    <path d="M12 5v14" />
  </>,
);

export const Repeat = makeIcon(
  "Repeat",
  <>
    <path d="m17 2 4 4-4 4" />
    <path d="M3 11v-1a4 4 0 0 1 4-4h14" />
    <path d="m7 22-4-4 4-4" />
    <path d="M21 13v1a4 4 0 0 1-4 4H3" />
  </>,
);

export const Scissors = makeIcon(
  "Scissors",
  <>
    <circle cx="6" cy="6" r="3" />
    <circle cx="6" cy="18" r="3" />
    <path d="M20 4 8.12 15.88" />
    <path d="M14.47 14.48 20 20" />
    <path d="M8.12 8.12 12 12" />
  </>,
);

export const Search = makeIcon(
  "Search",
  <>
    <circle cx="11" cy="11" r="8" />
    <path d="m21 21-4.3-4.3" />
  </>,
);

export const Send = makeIcon(
  "Send",
  <>
    <path d="m22 2-7 20-4-9-9-4Z" />
    <path d="M22 2 11 13" />
  </>,
);

export const Share2 = makeIcon(
  "Share2",
  <>
    <circle cx="18" cy="5" r="3" />
    <circle cx="6" cy="12" r="3" />
    <circle cx="18" cy="19" r="3" />
    <path d="m8.59 13.51 6.83 3.98" />
    <path d="m15.41 6.51-6.82 3.98" />
  </>,
);

export const ShieldCheck = makeIcon(
  "ShieldCheck",
  <>
    <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
    <path d="m9 12 2 2 4-4" />
  </>,
);

export const Shuffle = makeIcon(
  "Shuffle",
  <>
    <path d="m18 14 4 4-4 4" />
    <path d="m18 2 4 4-4 4" />
    <path d="M2 18h1.973a4 4 0 0 0 3.3-1.7l5.454-8.6a4 4 0 0 1 3.3-1.7H22" />
    <path d="M2 6h1.972a4 4 0 0 1 3.6 2.2" />
    <path d="M22 18h-6.041a4 4 0 0 1-3.3-1.8l-.359-.534" />
  </>,
);

export const SkipBack = makeIcon(
  "SkipBack",
  <>
    <path d="M19 20 9 12l10-8v16z" />
    <path d="M5 19V5" />
  </>,
);

export const SkipForward = makeIcon(
  "SkipForward",
  <>
    <path d="m5 4 10 8-10 8V4z" />
    <path d="M19 5v14" />
  </>,
);

export const Square = makeIcon("Square", <path d="M6 6h12v12H6z" />);

export const Trash2 = makeIcon(
  "Trash2",
  <>
    <path d="M3 6h18" />
    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
    <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
    <path d="M10 11v6" />
    <path d="M14 11v6" />
  </>,
);

export const Undo2 = makeIcon(
  "Undo2",
  <>
    <path d="M9 14 4 9l5-5" />
    <path d="M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5 5.5 5.5 0 0 1-5.5 5.5H11" />
  </>,
);

export const User = makeIcon(
  "User",
  <>
    <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
    <circle cx="12" cy="7" r="4" />
  </>,
);

export const UserPlus = makeIcon(
  "UserPlus",
  <>
    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
    <circle cx="9" cy="7" r="4" />
    <path d="M19 8v6" />
    <path d="M22 11h-6" />
  </>,
);

export const Users = makeIcon(
  "Users",
  <>
    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
    <circle cx="9" cy="7" r="4" />
    <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
    <path d="M16 3.13a4 4 0 0 1 0 7.75" />
  </>,
);

export const Volume2 = makeIcon(
  "Volume2",
  <>
    <path d="M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z" />
    <path d="M16 9a5 5 0 0 1 0 6" />
    <path d="M19.364 18.364a9 9 0 0 0 0-12.728" />
  </>,
);

export const X = makeIcon(
  "X",
  <>
    <path d="M18 6 6 18" />
    <path d="m6 6 12 12" />
  </>,
);

export const XCircle = makeIcon(
  "XCircle",
  <>
    <circle cx="12" cy="12" r="10" />
    <path d="m15 9-6 6" />
    <path d="m9 9 6 6" />
  </>,
);
