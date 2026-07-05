import Link from "next/link";
import { SignedIn, SignedOut } from "@clerk/nextjs";
import { GlassCard } from "@/components/glass/GlassCard";
import { LandingMotion } from "@/components/landing/LandingMotion";
import { Brain, Wand2, Network, Sparkles } from "lucide-react";

const features = [
  { icon: Brain,    title: "It never forgets",     body: "Every choice is written into a living memory graph, so the story stays consistent forever." },
  { icon: Network,  title: "A world you can see",  body: "Watch characters, places, and secrets connect in a constellation that grows as you play." },
  { icon: Wand2,    title: "You write the story",  body: "Type anything. The game master responds — grounded in everything that's happened." },
];

export default function Landing() {
  return (
    <main className="mx-auto max-w-6xl px-6">
      <LandingMotion />
      {/* Nav */}
      <nav className="flex items-center justify-between py-6">
        <span className="font-display text-2xl">Loreweave</span>
        <div className="flex items-center gap-2">
          <SignedOut>
            <Link href="/sign-in" className="btn btn-ghost">Sign in</Link>
            <Link href="/sign-up" className="btn btn-primary">Start playing</Link>
          </SignedOut>
          <SignedIn>
            <Link href="/dashboard" className="btn btn-primary">Open dashboard</Link>
          </SignedIn>
        </div>
      </nav>

      {/* Hero */}
      <section className="grid lg:grid-cols-2 gap-10 items-center py-16">
        <div data-reveal>
          <p className="label-eyebrow mb-5">AI game master · never forgets</p>
          <h1 className="text-5xl sm:text-6xl leading-[1.05] mb-6">
            Play a world that <span className="italic" style={{ color: "var(--ember)" }}>remembers everything</span>.
          </h1>
          <p className="text-muted text-lg mb-8 max-w-lg">
            Loreweave is an adventure you play by typing. Behind it, a knowledge graph keeps every
            character, item, and promise — so the story never contradicts itself.
          </p>
          <div className="flex gap-3">
            <Link href="/sign-up" className="btn btn-primary"><Sparkles size={18} /> Weave your first world</Link>
            <Link href="/sign-in" className="btn btn-secondary">I have an account</Link>
          </div>
        </div>

        {/* Live-demo teaser (static preview of a turn + memory) */}
        <GlassCard className="!p-0 overflow-hidden" data-reveal>
          <div className="grid sm:grid-cols-[1.4fr_1fr]">
            <div className="p-5 border-b sm:border-b-0 sm:border-r border-white/10">
              <p className="text-muted text-sm leading-relaxed">
                The merchant Kael recognises the ring you took from the docks. “You’re with the Ashguard,”
                he whispers, sliding a wrapped blade across the counter.
              </p>
              <div className="mt-3 inline-block rounded-2xl px-3 py-2 text-sm"
                   style={{ background: "rgba(124,107,245,0.16)", color: "#CFC7FF" }}>
                Ask Kael who betrayed the Ashguard.
              </div>
            </div>
            <div className="p-5">
              <p className="label-eyebrow mb-3">World memory</p>
              <p className="mono text-xs text-muted mb-2">characters</p>
              <div className="flex flex-col gap-2">
                <span className="chip chip-ally">Kael · ally</span>
                <span className="chip chip-caution">Mira · owed a favour</span>
                <span className="chip chip-hostile">Captain · hostile</span>
              </div>
            </div>
          </div>
        </GlassCard>
      </section>

      {/* Features bento */}
      <section className="grid md:grid-cols-3 gap-4 py-10">
        {features.map((f) => (
          <GlassCard key={f.title} data-reveal>
            <f.icon size={22} style={{ color: "var(--aurora-1)" }} />
            <h3 className="text-xl mt-4 mb-2">{f.title}</h3>
            <p className="text-muted text-sm leading-relaxed">{f.body}</p>
          </GlassCard>
        ))}
      </section>

      <footer className="py-16 text-center">
        <h2 className="text-3xl mb-6" data-reveal>Your story is waiting to be remembered.</h2>
        <Link href="/sign-up" className="btn btn-primary">Start playing — it’s free</Link>
        <p className="text-muted text-xs mt-10">Loreweave · built with Spring Boot, Neo4j, and Gemini.</p>
      </footer>
    </main>
  );
}
