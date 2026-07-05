import { clsx } from "clsx";

type Variant = "primary" | "secondary" | "ghost";

export function GlassButton(
  { variant = "secondary", className, children, ...rest }:
  { variant?: Variant } & React.ButtonHTMLAttributes<HTMLButtonElement>,
) {
  return (
    <button className={clsx("btn", `btn-${variant}`, className)} {...rest}>
      {children}
    </button>
  );
}
