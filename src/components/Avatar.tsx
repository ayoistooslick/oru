import { mediaUrl } from "../lib/api";
import { initials } from "../lib/format";

/** A profile picture when there is one, initials when there is not. */
export function Avatar({ name, src, size = "h-8 w-8" }: { name: string; src?: string | null; size?: string }) {
  const picture = mediaUrl(src);
  if (picture) {
    return <img src={picture} alt={name} className={`${size} shrink-0 rounded-full object-cover`} loading="lazy" />;
  }
  return (
    <span className={`${size} grid shrink-0 place-items-center rounded-full bg-[#3e3e3e] text-[12px] font-bold text-white`}>
      {initials(name)}
    </span>
  );
}
