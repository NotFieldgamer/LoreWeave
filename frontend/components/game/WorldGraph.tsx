"use client";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { clsx } from "clsx";
import type { WorldState } from "@/lib/types";

// Layout-effect on the client (write transforms before the browser paints), plain effect on the
// server so Next.js SSR doesn't warn.
const useIsoLayoutEffect = typeof window !== "undefined" ? useLayoutEffect : useEffect;

/**
 * The living memory constellation (M5) — the signature visual. A hand-rolled force-directed graph:
 * entities are glowing nodes (coloured by type), relationships are links (coloured by kind). The
 * physics run in a requestAnimationFrame loop and are written straight to the SVG imperatively, so
 * React never re-renders per frame. New nodes spring in as the world grows during play. The loop
 * stops once the layout settles (and when the tab is hidden), and honours prefers-reduced-motion by
 * settling synchronously with no motion at all.
 */

type GNode = {
  id: string; name: string; type: string;
  x: number; y: number; vx: number; vy: number; r: number; born: number;
  fx?: number; fy?: number;   // fixed coords while dragging
};
type GLink = { key: string; source: string; target: string; type: string };

const TYPE_COLOR: Record<string, string> = {
  CHARACTER: "var(--aurora-1)",
  PLACE: "var(--aurora-2)",
  ITEM: "var(--ember)",
  FACTION: "var(--danger)",
  EVENT: "var(--success)",
};
const REL_COLOR: Record<string, string> = {
  ALLY_OF: "var(--success)",
  ENEMY_OF: "var(--danger)",
  KNOWS: "var(--aurora-1)",
  LOCATED_IN: "var(--aurora-2)",
  OWNS: "var(--ember)",
  MEMBER_OF: "var(--aurora-2)",
};
const color = (type: string) => TYPE_COLOR[type] ?? "var(--text-muted)";
const ENTRY_MS = 520;
const easeOutBack = (t: number) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * (t - 1) ** 3 + c1 * (t - 1) ** 2; };

export function WorldGraph({ world, height = 320, className }: { world: WorldState; height?: number; className?: string }) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const nodesRef = useRef<GNode[]>([]);
  const linksRef = useRef<GLink[]>([]);
  const gEls = useRef<Map<string, SVGGElement | null>>(new Map());
  const lineEls = useRef<Map<string, SVGLineElement | null>>(new Map());
  const dims = useRef({ w: 600, h: height });
  const raf = useRef<number | null>(null);
  const running = useRef(false);
  const drag = useRef<string | null>(null);

  // React only owns the element SET (created/removed as the world grows); positions are imperative.
  const [render, setRender] = useState<{ nodes: GNode[]; links: GLink[] }>({ nodes: [], links: [] });
  const [hover, setHover] = useState<string | null>(null);
  const [vw, setVw] = useState(600);   // measured width, mirrored to the viewBox so drag coords are 1:1

  const reduceMotion = () =>
    typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // ── build/merge graph when the world changes (preserve positions of nodes we already have) ──
  useEffect(() => {
    // Measure NOW (the mount effect below may not have run yet on first mount) so seeding and the
    // reduced-motion settle lay out against the real container width, not the 600px default.
    const measured = wrapRef.current ? Math.max(240, wrapRef.current.clientWidth) : dims.current.w;
    dims.current = { w: measured, h: height };
    setVw(measured);
    const { w, h } = dims.current;
    const prev = new Map(nodesRef.current.map((n) => [n.id, n]));
    const names = new Set(world.entities.map((e) => e.name));
    const now = typeof performance !== "undefined" ? performance.now() : 0;

    const deg = new Map<string, number>();
    const links: GLink[] = world.relations
      .filter((r) => names.has(r.from) && names.has(r.to) && r.from !== r.to)
      .map((r, i) => {
        deg.set(r.from, (deg.get(r.from) ?? 0) + 1);
        deg.set(r.to, (deg.get(r.to) ?? 0) + 1);
        return { key: `${r.from}→${r.to}#${r.type}#${i}`, source: r.from, target: r.to, type: r.type };
      });

    const nodes: GNode[] = world.entities.map((e, i) => {
      const existing = prev.get(e.name);
      const r = 9 + Math.min(9, (deg.get(e.name) ?? 0) * 2);
      if (existing) return { ...existing, type: e.type, r };
      // seed new nodes on a small golden-angle spiral near centre so the sim can fan them out
      const a = i * 2.39996, rad = 26 + i * 2;
      return {
        id: e.name, name: e.name, type: e.type, r, born: now,
        x: w / 2 + Math.cos(a) * rad, y: h / 2 + Math.sin(a) * rad, vx: 0, vy: 0,
      };
    });

    nodesRef.current = nodes;
    linksRef.current = links;
    setRender({ nodes, links });

    if (reduceMotion()) { settle(nodes, links, dims.current, 320); nodes.forEach((n) => (n.born = 0)); }
    else start();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [world]);

  // Write positions synchronously after the elements commit but BEFORE the browser paints, so new
  // nodes never flash at the SVG origin (0,0). Also covers the reduced-motion static path (no rAF).
  useIsoLayoutEffect(() => { paint(true); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [render]);

  // ── one persistent rAF loop + a resize observer, mounted once ──
  useEffect(() => {
    const measure = () => {
      const el = wrapRef.current; if (!el) return;
      const w = Math.max(240, el.clientWidth);
      dims.current = { w, h: height };
      setVw(w);
      start();   // re-settle after a resize (and covers a live height change re-mounting this effect)
    };
    measure();
    const ro = typeof ResizeObserver !== "undefined" ? new ResizeObserver(measure) : null;
    if (ro && wrapRef.current) ro.observe(wrapRef.current);

    const onVis = () => { if (document.hidden) stop(); else if (!reduceMotion()) start(); };
    document.addEventListener("visibilitychange", onVis);

    return () => { stop(); ro?.disconnect(); document.removeEventListener("visibilitychange", onVis); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [height]);

  function start() {
    if (running.current || reduceMotion()) return;
    running.current = true;
    let calm = 0;   // consecutive low-energy frames — avoids stopping at an oscillation turning point
    const loop = () => {
      const settled = step(nodesRef.current, linksRef.current, dims.current, drag.current);
      const anyYoung = paint(false);
      calm = settled ? calm + 1 : 0;
      if (calm >= 4 && !anyYoung && !drag.current) { running.current = false; raf.current = null; return; }
      raf.current = requestAnimationFrame(loop);
    };
    raf.current = requestAnimationFrame(loop);
  }
  function stop() { if (raf.current != null) cancelAnimationFrame(raf.current); raf.current = null; running.current = false; }

  /** Write current positions + entry scale to the SVG. Returns true if any node is still animating in. */
  function paint(force: boolean): boolean {
    const now = typeof performance !== "undefined" ? performance.now() : 0;
    let young = false;
    for (const n of nodesRef.current) {
      const g = gEls.current.get(n.id); if (!g) continue;
      g.setAttribute("transform", `translate(${n.x.toFixed(2)} ${n.y.toFixed(2)})`);
      const age = now - n.born;
      const s = n.born === 0 || age >= ENTRY_MS ? 1 : Math.max(0, easeOutBack(age / ENTRY_MS));
      if (age < ENTRY_MS && n.born !== 0) young = true;
      g.style.setProperty("--s", s.toFixed(3));
      g.style.setProperty("--glow", (age < ENTRY_MS && n.born !== 0 ? 1 - age / ENTRY_MS : 0).toFixed(3));
    }
    for (const l of linksRef.current) {
      const el = lineEls.current.get(l.key); if (!el) continue;
      const a = byId(l.source), b = byId(l.target); if (!a || !b) continue;
      el.setAttribute("x1", a.x.toFixed(2)); el.setAttribute("y1", a.y.toFixed(2));
      el.setAttribute("x2", b.x.toFixed(2)); el.setAttribute("y2", b.y.toFixed(2));
    }
    return young || force;
  }
  const byId = (id: string) => nodesRef.current.find((n) => n.id === id);

  // ── drag: pin a node under the pointer, then let it float again on release ──
  // Map a screen pointer into SVG userspace via the CTM, so drag stays correct under any viewBox
  // scaling/letterboxing (e.g. a container narrower than the 240 clamp).
  function svgPoint(e: React.PointerEvent) {
    const svg = svgRef.current!;
    const ctm = svg.getScreenCTM();
    if (ctm && typeof DOMPointReadOnly !== "undefined") {
      const p = new DOMPointReadOnly(e.clientX, e.clientY).matrixTransform(ctm.inverse());
      return { x: p.x, y: p.y };
    }
    const rect = svg.getBoundingClientRect();
    return { x: e.clientX - rect.left, y: e.clientY - rect.top };
  }
  function onPointerDown(id: string) {
    return (e: React.PointerEvent) => {
      e.preventDefault();
      (e.target as Element).setPointerCapture?.(e.pointerId);
      drag.current = id;
      const p = svgPoint(e), n = byId(id); if (n) { n.fx = p.x; n.fy = p.y; }
      start();
    };
  }
  function onPointerMove(e: React.PointerEvent) {
    if (!drag.current) return;
    const p = svgPoint(e), n = byId(drag.current);
    if (n) { n.fx = p.x; n.fy = p.y; }
  }
  function onPointerUp() {
    if (!drag.current) return;
    const n = byId(drag.current); if (n) { n.fx = undefined; n.fy = undefined; }
    drag.current = null; start();
  }

  const neighbors = (() => {
    if (!hover) return null;
    const set = new Set<string>([hover]);
    for (const l of render.links) { if (l.source === hover) set.add(l.target); if (l.target === hover) set.add(l.source); }
    return set;
  })();

  return (
    <div ref={wrapRef} className={clsx("wg-wrap", className)} style={{ height }}>
      {render.nodes.length === 0 ? (
        <div className="wg-empty mono">The constellation forms as your world grows.</div>
      ) : (
        <svg
          ref={svgRef}
          className="wg-svg"
          width="100%"
          height={height}
          viewBox={`0 0 ${vw} ${height}`}
          preserveAspectRatio="xMidYMid meet"
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerLeave={onPointerUp}
        >
          <g className="wg-links">
            {render.links.map((l) => (
              <line
                key={l.key}
                ref={(el) => { lineEls.current.set(l.key, el); }}
                className="wg-link"
                stroke={REL_COLOR[l.type] ?? "var(--glass-stroke)"}
                style={{ opacity: neighbors && !(neighbors.has(l.source) && neighbors.has(l.target)) ? 0.08 : 0.32 }}
              />
            ))}
          </g>
          <g className="wg-nodes">
            {render.nodes.map((n) => (
              <g
                key={n.id}
                ref={(el) => { gEls.current.set(n.id, el); }}
                className={clsx("wg-node", neighbors && !neighbors.has(n.id) && "wg-dim")}
                onPointerDown={onPointerDown(n.id)}
                onPointerEnter={() => setHover(n.id)}
                onPointerLeave={() => setHover((h) => (h === n.id ? null : h))}
              >
                <g className="wg-scale">
                  <circle className="wg-halo" r={n.r * 2} fill={color(n.type)} />
                  <circle className="wg-core" r={n.r} fill={color(n.type)} />
                </g>
                <text className="wg-label mono" y={n.r + 14} textAnchor="middle" fill="var(--text-muted)">
                  {n.name.length > 16 ? n.name.slice(0, 15) + "…" : n.name}
                </text>
              </g>
            ))}
          </g>
        </svg>
      )}
    </div>
  );
}

// ── physics: one integration step. Returns true when the whole graph has effectively settled. ──
function step(nodes: GNode[], links: GLink[], d: { w: number; h: number }, dragging: string | null): boolean {
  const cx = d.w / 2, cy = d.h / 2;
  const CHARGE = 1500, SPRING = 0.035, REST = 82, CENTER = 0.006, DAMP = 0.86, MAXV = 6;
  const idx = new Map(nodes.map((n) => [n.id, n]));

  for (let i = 0; i < nodes.length; i++) {
    const a = nodes[i];
    if (a.fx != null) { a.x = a.fx; a.y = a.fy!; a.vx = 0; a.vy = 0; continue; }
    a.vx += (cx - a.x) * CENTER;
    a.vy += (cy - a.y) * CENTER;
    for (let j = 0; j < nodes.length; j++) {
      if (i === j) continue;
      const b = nodes[j];
      let dx = a.x - b.x, dy = a.y - b.y, d2 = dx * dx + dy * dy;
      if (d2 < 0.01) { dx = (i - j) || 1; dy = (j - i) || 1; d2 = dx * dx + dy * dy + 0.01; }
      const dist = Math.sqrt(d2), f = CHARGE / d2;
      a.vx += (dx / dist) * f;
      a.vy += (dy / dist) * f;
      const minD = a.r + b.r + 6;
      if (dist < minD) { const push = ((minD - dist) * 0.5) / dist; a.vx += dx * push; a.vy += dy * push; }
    }
  }
  for (const l of links) {
    const a = idx.get(l.source), b = idx.get(l.target); if (!a || !b) continue;
    const dx = b.x - a.x, dy = b.y - a.y, dist = Math.hypot(dx, dy) || 0.01;
    const f = (dist - REST) * SPRING, ux = dx / dist, uy = dy / dist;
    if (a.fx == null) { a.vx += ux * f; a.vy += uy * f; }
    if (b.fx == null) { b.vx -= ux * f; b.vy -= uy * f; }
  }
  let energy = 0;
  for (const a of nodes) {
    if (a.fx != null) continue;
    a.vx *= DAMP; a.vy *= DAMP;
    const sp = Math.hypot(a.vx, a.vy);
    if (sp > MAXV) { a.vx = (a.vx / sp) * MAXV; a.vy = (a.vy / sp) * MAXV; }
    a.x += a.vx; a.y += a.vy;
    const pad = a.r + 6;
    a.x = Math.max(pad, Math.min(d.w - pad, a.x));
    a.y = Math.max(pad, Math.min(d.h - pad, a.y));
    energy += a.vx * a.vx + a.vy * a.vy;
  }
  return energy < 0.05 && dragging == null;
}

/** Run the sim to rest synchronously (reduced-motion path — no visible animation). */
function settle(nodes: GNode[], links: GLink[], d: { w: number; h: number }, iterations: number) {
  for (let i = 0; i < iterations; i++) if (step(nodes, links, d, null)) break;
}
