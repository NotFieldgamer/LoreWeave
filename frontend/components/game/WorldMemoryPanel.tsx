"use client";
import type { WorldState } from "@/lib/types";
import { Brain, MapPin } from "lucide-react";

const roleChip: Record<string, string> = { ally: "chip-ally", caution: "chip-caution", hostile: "chip-hostile" };

export function WorldMemoryPanel({ world }: { world: WorldState }) {
  const characters = world.entities.filter((e) => e.type === "CHARACTER");
  const items = world.entities.filter((e) => e.type === "ITEM");
  const place = world.entities.find((e) => e.type === "PLACE");

  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <p className="flex items-center gap-2 text-[15px]"><Brain size={17} style={{ color: "var(--aurora-1)" }} /> World memory</p>
        <span className="chip mono">{world.facts.length} facts</span>
      </div>
      <p className="text-muted text-xs mb-4">Every choice is remembered, so the story stays consistent.</p>

      <p className="mono text-xs text-muted mb-2">characters</p>
      <div className="flex flex-col gap-2 mb-4">
        {characters.length === 0 && <span className="text-muted text-sm">No one met yet.</span>}
        {characters.map((c) => (
          <span key={c.id} className={`chip ${roleChip["ally"]}`}>{c.name}</span>
        ))}
      </div>

      <p className="mono text-xs text-muted mb-2">inventory</p>
      <div className="flex flex-wrap gap-2 mb-4">
        {items.length === 0 && <span className="text-muted text-sm">Empty.</span>}
        {items.map((it) => <span key={it.id} className="chip">{it.name}</span>)}
      </div>

      <p className="mono text-xs text-muted mb-2">location</p>
      <span className="chip"><MapPin size={13} /> {place?.name ?? "Unknown"}</span>
    </div>
  );
}
