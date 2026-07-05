import { clsx } from "clsx";

export function GlassPanel({ className, children }: { className?: string; children: React.ReactNode }) {
  return <div className={clsx("glass glass-strong p-6 md:p-8", className)}>{children}</div>;
}
