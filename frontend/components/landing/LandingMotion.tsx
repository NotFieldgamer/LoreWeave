"use client";
import { useEffect, useLayoutEffect } from "react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import type Lenis from "lenis";

// Layout-effect on the client so the "hidden" start state is set before the browser paints (no flash);
// plain effect on the server so Next.js SSR doesn't warn.
const useIso = typeof window !== "undefined" ? useLayoutEffect : useEffect;

/**
 * Landing scroll motion (M6): Lenis smooth-scroll + GSAP scroll-reveal on any [data-reveal] element.
 * Fully degradable — with prefers-reduced-motion or no JS, nothing is hidden and the page renders
 * statically. Content is therefore never trapped behind an animation that didn't run.
 */
export function LandingMotion() {
  useIso(() => {
    if (typeof window === "undefined") return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    gsap.registerPlugin(ScrollTrigger);

    let lenis: Lenis | null = null;
    let rafId = 0;
    // Smooth scroll is best-effort; if the module can't load we simply keep native scrolling.
    import("lenis").then(({ default: LenisClass }) => {
      lenis = new LenisClass({ duration: 1.1 });
      lenis.on("scroll", ScrollTrigger.update);
      const raf = (time: number) => { lenis?.raf(time); rafId = requestAnimationFrame(raf); };
      rafId = requestAnimationFrame(raf);
    }).catch(() => {});

    const ctx = gsap.context(() => {
      gsap.utils.toArray<HTMLElement>("[data-reveal]").forEach((el, i) => {
        gsap.fromTo(
          el,
          { y: 26, autoAlpha: 0 },
          {
            y: 0, autoAlpha: 1, duration: 0.7, ease: "power2.out", delay: (i % 3) * 0.06,
            scrollTrigger: { trigger: el, start: "top 88%", once: true },
          },
        );
      });
    });

    return () => {
      if (rafId) cancelAnimationFrame(rafId);
      lenis?.destroy();
      ctx.revert();
    };
  }, []);

  return null;
}
