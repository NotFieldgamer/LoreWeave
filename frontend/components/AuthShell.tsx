import Link from "next/link";

/** Split-screen frame for the custom auth pages: glass hero on the left, the form on the right. */
export function AuthShell({ title, subtitle, children }:
  { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <main className="min-h-screen grid lg:grid-cols-2">
      {/* Left — brand hero */}
      <section className="relative hidden lg:flex flex-col justify-between p-12 overflow-hidden">
        <div className="glass glass-strong absolute inset-6 -z-0" aria-hidden="true" />
        <div className="relative z-10">
          <Link href="/" className="font-display text-2xl">Loreweave</Link>
        </div>
        <div className="relative z-10 max-w-md">
          <p className="label-eyebrow mb-4">The world remembers</p>
          <h1 className="text-4xl leading-tight mb-4">A story that never forgets a single choice.</h1>
          <p className="text-muted">Every character, item, and promise is woven into a living memory — so your adventure always stays true to itself.</p>
        </div>
        {/* faux memory constellation */}
        <svg className="relative z-10 opacity-70" width="220" height="90" viewBox="0 0 220 90" aria-hidden="true">
          <g stroke="rgba(124,107,245,0.5)" strokeWidth="1">
            <line x1="20" y1="60" x2="80" y2="30" /><line x1="80" y1="30" x2="150" y2="55" />
            <line x1="150" y1="55" x2="200" y2="25" /><line x1="80" y1="30" x2="120" y2="75" />
          </g>
          <g fill="#7C6BF5">
            <circle cx="20" cy="60" r="4" /><circle cx="80" cy="30" r="5" />
            <circle cx="150" cy="55" r="4" /><circle cx="200" cy="25" r="4" /><circle cx="120" cy="75" r="3" fill="#F5B056" />
          </g>
        </svg>
      </section>

      {/* Right — the form */}
      <section className="flex items-center justify-center p-6 sm:p-12">
        <div className="w-full max-w-sm">
          <div className="lg:hidden mb-8"><Link href="/" className="font-display text-2xl">Loreweave</Link></div>
          <h2 className="text-2xl mb-1">{title}</h2>
          <p className="text-muted text-sm mb-8">{subtitle}</p>
          <div className="glass p-6">{children}</div>
        </div>
      </section>
    </main>
  );
}
