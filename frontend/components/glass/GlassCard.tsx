import { clsx } from "clsx";

export function GlassCard(
  { className, children, ...rest }:
  { className?: string; children: React.ReactNode } & React.HTMLAttributes<HTMLDivElement>,
) {
  return <div className={clsx("glass p-5", className)} {...rest}>{children}</div>;
}
