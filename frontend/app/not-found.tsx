import Link from "next/link";
import { GlassCard } from "@/components/glass/GlassCard";

export default function NotFound() {
  return (
    <main className="min-h-screen grid place-items-center px-6">
      <GlassCard className="max-w-md text-center">
        <p className="label-eyebrow mb-3">404</p>
        <h1 className="font-display text-3xl mb-2">This path was never written</h1>
        <p className="text-muted text-sm mb-6">The page you&apos;re looking for isn&apos;t part of any story here.</p>
        <Link href="/" className="btn btn-primary">Back to the start</Link>
      </GlassCard>
    </main>
  );
}
