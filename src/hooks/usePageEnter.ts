import { useLayoutEffect, useRef } from "react";
import gsap from "gsap";
import { motionSafe } from "../lib/motion";

/**
 * Entrance choreography for a route: children marked with `data-enter` rise into
 * place once, then hand control back to normal CSS transitions.
 */
export function usePageEnter<T extends HTMLElement>() {
  const ref = useRef<T>(null);

  useLayoutEffect(() => {
    const element = ref.current;
    if (!element || motionSafe()) return;
    const ctx = gsap.context(() => {
      const items = gsap.utils.toArray<HTMLElement>("[data-enter]", element).slice(0, 14);
      gsap.from(items, {
        y: 18,
        opacity: 0,
        duration: 0.5,
        stagger: 0.04,
        ease: "power3.out",
        clearProps: "transform,opacity",
      });
    }, element);
    return () => ctx.revert();
  }, []);

  return ref;
}
