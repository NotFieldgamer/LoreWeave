"use client";
import Link from "next/link";
import { GlassCard } from "@/components/glass/GlassCard";

export default function Error({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main className="min-h-screen grid place-items-center px-6">
      <GlassCard className="max-w-md text-center">
        <h1 className="font-display text-2xl mb-2">The weave frayed</h1>
        <p className="text-muted text-sm mb-6">Something went wrong on our end. Your worlds are safe — try again.</p>
        <div className="flex gap-3 justify-center">
          <button className="btn btn-primary" onClick={() => reset()}>Try again</button>
          <Link href="/dashboard" className="btn btn-secondary">Dashboard</Link>
        </div>
      </GlassCard>
    </main>
  );
}
