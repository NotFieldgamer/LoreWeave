import { Loader2 } from "lucide-react";

export default function Loading() {
  return (
    <div className="grid place-items-center min-h-[60vh]">
      <div className="flex flex-col items-center gap-4 text-muted">
        <Loader2 size={28} className="animate-spin" style={{ color: "var(--aurora-1)" }} />
        <p className="mono text-sm">Summoning your world…</p>
      </div>
    </div>
  );
}
