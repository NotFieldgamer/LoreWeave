"use client";
import { useEffect, useState } from "react";
import { useAuth } from "@clerk/nextjs";
import { GlassCard } from "@/components/glass/GlassCard";
import { GlassPanel } from "@/components/glass/GlassPanel";
import { StoryStream } from "@/components/game/StoryStream";
import { TurnComposer } from "@/components/game/TurnComposer";
import { WorldMemoryPanel } from "@/components/game/WorldMemoryPanel";
import { ConstellationCard } from "@/components/game/ConstellationCard";
import { getSession, getWorld, streamTurn } from "@/lib/api";
import type { StorySegment, TurnDelta, WorldState } from "@/lib/types";
import { ShieldCheck } from "lucide-react";

const EMPTY: WorldState = { entities: [], relations: [], facts: [] };

export default function PlayPage({ params }: { params: { sessionId: string } }) {
  const { getToken } = useAuth();
  const [title, setTitle] = useState("Your adventure");
  const [segments, setSegments] = useState<StorySegment[]>([]);
  const [live, setLive] = useState("");
  const [world, setWorld] = useState<WorldState>(EMPTY);
  const [streaming, setStreaming] = useState(false);
  const [loading, setLoading] = useState(true);
  const [guardNote, setGuardNote] = useState("");

  // On load, rebuild the story so far from the graph: opening scene + every committed turn.
  useEffect(() => {
    (async () => {
      const token = (await getToken()) ?? "";
      try {
        const s = await getSession(token, params.sessionId);
        setTitle(s.title);
        const restored: StorySegment[] = [];
        if (s.openingScene?.trim()) restored.push({ role: "gm", text: s.openingScene });
        for (const t of s.turns) {
          restored.push({ role: "player", text: t.action });
          restored.push({ role: "gm", text: t.narration });
        }
        setSegments(restored);
      } catch { /* couldn't load — leave the transcript empty rather than showing a fake opening */ }
      try { setWorld(await getWorld(token, params.sessionId)); } catch { /* world panel is optional */ }
      setLoading(false);
    })();
  }, [params.sessionId, getToken]);

  async function handleTurn(action: string) {
    setSegments((s) => [...s, { role: "player", text: action }]);
    setStreaming(true);
    setLive("");
    setGuardNote("");
    let buffer = "";
    try {
      const token = (await getToken()) ?? "";
      await streamTurn(token, params.sessionId, action, {
        onToken: (t) => { buffer += t; setLive(buffer); },
        onDelta: (d) => {
          const superseded = (d as TurnDelta)?.superseded ?? [];
          if (superseded.length) {
            const n = superseded.length;
            setGuardNote(`The consistency guard reconciled ${n} outdated fact${n > 1 ? "s" : ""}.`);
          }
          getToken().then((tk) => getWorld(tk ?? "", params.sessionId).then(setWorld).catch(() => {}));
        },
        onDone: () => {
          setSegments((s) => [...s, { role: "gm", text: buffer }]);
          setLive(""); setStreaming(false);
        },
        onError: (message) => {
          setSegments((s) => [...s, { role: "gm", text: message }]);
          setLive(""); setStreaming(false);
        },
      });
    } catch {
      setSegments((s) => [...s, { role: "gm", text: "The connection to the game master dropped. Try again." }]);
      setLive(""); setStreaming(false);
    }
  }

  return (
    <div className="grid lg:grid-cols-[1.6fr_1fr] gap-4">
      <GlassPanel className="flex flex-col min-h-[70vh]">
        <p className="label-eyebrow mb-4">{title}</p>
        <div className="flex-1 overflow-y-auto pr-1">
          {loading && segments.length === 0
            ? <p className="text-ink/50">Summoning your world…</p>
            : <StoryStream segments={segments} />}
          {live && <p className="text-ink/90 leading-relaxed mt-4">{live}<span className="animate-pulse">▍</span></p>}
        </div>
        <div className="mt-4">
          <TurnComposer onSubmit={handleTurn} disabled={streaming || loading} />
        </div>
      </GlassPanel>

      <div className="space-y-4">
        <ConstellationCard world={world} />
        <GlassCard className="h-fit">
          <WorldMemoryPanel world={world} />
          {guardNote && (
            <p className="mt-4 flex items-center gap-2 text-xs text-ink/70">
              <ShieldCheck size={14} className="text-ok/80 shrink-0" /> {guardNote}
            </p>
          )}
        </GlassCard>
      </div>
    </div>
  );
}
