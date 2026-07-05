"use client";
import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence, useReducedMotion } from "framer-motion";
import { Sparkles, Maximize2, X } from "lucide-react";
import { GlassCard } from "@/components/glass/GlassCard";
import { WorldGraph } from "./WorldGraph";
import type { WorldState } from "@/lib/types";

const LEGEND: { type: string; label: string; color: string }[] = [
  { type: "CHARACTER", label: "Character", color: "var(--aurora-1)" },
  { type: "PLACE", label: "Place", color: "var(--aurora-2)" },
  { type: "ITEM", label: "Item", color: "var(--ember)" },
  { type: "FACTION", label: "Faction", color: "var(--danger)" },
  { type: "EVENT", label: "Event", color: "var(--success)" },
];

function Legend({ world }: { world: WorldState }) {
  const present = new Set(world.entities.map((e) => e.type));
  const items = LEGEND.filter((l) => present.has(l.type));
  if (!items.length) return null;
  return (
    <div className="flex flex-wrap gap-x-3 gap-y-1 mt-3">
      {items.map((l) => (
        <span key={l.type} className="mono text-[11px] text-muted flex items-center gap-1.5">
          <span className="inline-block w-2 h-2 rounded-full" style={{ background: l.color }} /> {l.label}
        </span>
      ))}
    </div>
  );
}

/** The signature constellation, in a glass card, with an expand-to-fullscreen view (M5). */
export function ConstellationCard({ world }: { world: WorldState }) {
  const [open, setOpen] = useState(false);
  const rm = useReducedMotion();
  const closeRef = useRef<HTMLButtonElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const trigger = triggerRef.current;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
      else if (e.key === "Tab") { e.preventDefault(); closeRef.current?.focus(); } // trap on the one control
    };
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    const t = window.setTimeout(() => closeRef.current?.focus(), 0); // move focus into the dialog
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      window.clearTimeout(t);
      trigger?.focus(); // restore focus to the trigger
    };
  }, [open]);

  const count = world.entities.length;

  return (
    <>
      <GlassCard>
        <div className="flex items-center justify-between mb-3">
          <p className="flex items-center gap-2 text-[15px]">
            <Sparkles size={17} style={{ color: "var(--aurora-1)" }} /> Memory constellation
          </p>
          <button ref={triggerRef} className="btn btn-ghost !h-8 !px-2.5 text-xs" onClick={() => setOpen(true)} disabled={!count}>
            <Maximize2 size={13} /> Expand
          </button>
        </div>
        <WorldGraph world={world} height={300} />
        <Legend world={world} />
      </GlassCard>

      <AnimatePresence>
        {open && (
          <motion.div
            className="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-8"
            style={{ background: "rgba(4,4,14,0.72)", backdropFilter: "blur(6px)" }}
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={() => setOpen(false)}
          >
            <motion.div
              role="dialog"
              aria-modal="true"
              aria-label="The world constellation"
              className="glass glass-strong w-full max-w-5xl overflow-hidden"
              style={{ padding: 0 }}
              initial={rm ? { opacity: 0 } : { scale: 0.96, opacity: 0 }}
              animate={rm ? { opacity: 1 } : { scale: 1, opacity: 1 }}
              exit={rm ? { opacity: 0 } : { scale: 0.96, opacity: 0 }}
              transition={rm ? { duration: 0.15 } : { type: "spring", stiffness: 260, damping: 26 }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between px-6 pt-5 pb-3">
                <div>
                  <p className="label-eyebrow">Living memory</p>
                  <h3 className="font-display text-2xl mt-1">The world constellation</h3>
                </div>
                <button ref={closeRef} className="btn btn-ghost !h-9 !w-9 !p-0" onClick={() => setOpen(false)} aria-label="Close">
                  <X size={18} />
                </button>
              </div>
              <div className="px-4 pb-5">
                <WorldGraph world={world} height={520} />
                <div className="px-2"><Legend world={world} /></div>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
