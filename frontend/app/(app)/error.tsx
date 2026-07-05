"use client";
import { GlassCard } from "@/components/glass/GlassCard";

export default function AppError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="grid place-items-center min-h-[60vh]">
      <GlassCard className="max-w-md text-center">
        <h1 className="font-display text-2xl mb-2">This world hit a snag</h1>
        <p className="text-muted text-sm mb-6">We couldn&apos;t load that just now. Your progress is saved.</p>
        <button className="btn btn-primary" onClick={() => reset()}>Retry</button>
      </GlassCard>
    </div>
  );
}
