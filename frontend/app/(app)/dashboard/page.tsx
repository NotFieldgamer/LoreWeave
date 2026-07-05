"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "@clerk/nextjs";
import { GlassCard } from "@/components/glass/GlassCard";
import { createSession, getDashboard } from "@/lib/api";
import type { Dashboard } from "@/lib/types";
import { Plus, Sparkles, Layers, MessageSquare, Activity } from "lucide-react";

const STAT_ICON = { worlds: Layers, turns: MessageSquare, facts: Sparkles } as const;

export default function DashboardPage() {
  const { getToken } = useAuth();
  const [data, setData] = useState<Dashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    (async () => {
      try { setData(await getDashboard((await getToken()) ?? "")); }
      catch { /* leave null → empty state */ }
      finally { setLoading(false); }
    })();
  }, [getToken]);

  async function newAdventure() {
    setBusy(true);
    try {
      const token = (await getToken()) ?? "";
      const s = await createSession(token, { genre: "Dark fantasy", premise: "A stranger arrives at a rain-soaked port." });
      window.location.href = `/play/${s.id}`;
    } catch {
      setBusy(false);
      alert("Could not create the adventure. Check the backend is running.");
    }
  }

  const stats = [
    { key: "worlds" as const, label: "Worlds woven", value: data?.worlds ?? 0 },
    { key: "turns" as const, label: "Turns played", value: data?.turns ?? 0 },
    { key: "facts" as const, label: "Facts remembered", value: data?.facts ?? 0 },
  ];
  const adventures = data?.adventures ?? [];
  const recent = data?.recent ?? [];

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl">Your adventures</h1>
          <p className="text-muted text-sm mt-1">Worlds that remember every choice you make.</p>
        </div>
        <button className="btn btn-primary" onClick={newAdventure} disabled={busy}>
          <Plus size={18} /> {busy ? "Weaving…" : "New adventure"}
        </button>
      </header>

      {/* Stat cards (finance-analytics genre) — real aggregates */}
      <div className="grid sm:grid-cols-3 gap-4">
        {stats.map((s) => {
          const Icon = STAT_ICON[s.key];
          return (
            <GlassCard key={s.key}>
              <Icon size={18} style={{ color: "var(--aurora-1)" }} />
              <p className="text-muted text-sm mt-3">{s.label}</p>
              <p className="text-3xl mt-1">{loading ? "—" : s.value}</p>
            </GlassCard>
          );
        })}
      </div>

      <div className="grid lg:grid-cols-[1.6fr_1fr] gap-4">
        {/* Adventure cards — real per-adventure stats */}
        <div className="grid sm:grid-cols-2 gap-4 content-start">
          {loading && (
            <GlassCard className="sm:col-span-2"><p className="text-muted text-sm">Loading your worlds…</p></GlassCard>
          )}
          {!loading && adventures.length === 0 && (
            <GlassCard className="sm:col-span-2 flex flex-col items-start">
              <h3 className="font-display text-2xl mb-2">No worlds yet</h3>
              <p className="text-muted text-sm mb-4">Weave your first adventure — it will remember everything from here on.</p>
              <button className="btn btn-primary" onClick={newAdventure} disabled={busy}>
                <Plus size={18} /> Begin an adventure
              </button>
            </GlassCard>
          )}
          {adventures.map((a) => (
            <GlassCard key={a.id} className="flex flex-col">
              <p className="label-eyebrow">{a.genre || "Adventure"}</p>
              <h3 className="font-display text-2xl mt-2 mb-3">{a.title}</h3>
              <p className="text-muted text-xs mono mb-4">
                turn {a.turnCount} · {a.entityCount} entities · {a.factCount} facts
              </p>
              <Link href={`/play/${a.id}`} className="btn btn-secondary mt-auto">Continue</Link>
            </GlassCard>
          ))}
        </div>

        {/* Live world monitor (admin "Live Monitor" genre) — real recent facts */}
        <GlassCard className="h-fit">
          <p className="flex items-center gap-2 text-[15px] mb-4">
            <Activity size={17} style={{ color: "var(--aurora-1)" }} /> Live world monitor
          </p>
          {recent.length === 0 ? (
            <p className="text-muted text-sm">{loading ? "Listening…" : "No activity yet — take a turn and it appears here."}</p>
          ) : (
            <ul className="space-y-3">
              {recent.map((f, i) => (
                <li key={i} className="mono text-xs text-muted flex gap-2">
                  <span style={{ color: "var(--ember)" }}>+</span>
                  <span className="min-w-0">
                    <span className="text-ink/90">{f.text}</span>
                    {f.title && <span className="text-muted"> — {f.title}</span>}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </GlassCard>
      </div>
    </div>
  );
}
