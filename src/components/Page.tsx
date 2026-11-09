import type { ReactNode } from "react";
import { usePageEnter } from "../hooks/usePageEnter";

interface PageProps {
  title: string;
  eyebrow?: ReactNode;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
}

/** Every signed-in route opens the same way: a large title, then the content. */
export function Page({ title, eyebrow, subtitle, actions, children }: PageProps) {
  const ref = usePageEnter<HTMLDivElement>();

  return (
    <div ref={ref} className="mx-auto w-full max-w-7xl px-4 pb-8 sm:px-6 lg:px-8">
      <header data-enter className="flex flex-wrap items-end justify-between gap-4 pb-4">
        <div>
          {eyebrow ? <p className="mb-1.5 text-[13px] font-bold text-ink-soft">{eyebrow}</p> : null}
          <h1 className="text-[30px] font-extrabold leading-none tracking-[-0.03em] sm:text-[38px]">{title}</h1>
          {subtitle ? <p className="mt-2 max-w-2xl text-[15px] leading-relaxed text-ink-soft">{subtitle}</p> : null}
        </div>
        {actions ? <div className="flex flex-wrap items-center gap-2.5">{actions}</div> : null}
      </header>
      <div>{children}</div>
    </div>
  );
}
