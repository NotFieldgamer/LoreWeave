import type { Dashboard, SessionDetail, SessionDto, WorldState } from "./types";

const BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

function auth(token: string) {
  return { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
}

export async function listSessions(token: string): Promise<SessionDto[]> {
  const r = await fetch(`${BASE}/api/sessions`, { headers: auth(token) });
  if (!r.ok) throw new Error("Could not load your adventures");
  return r.json();
}

export async function createSession(token: string, body: { genre: string; premise: string }): Promise<SessionDto> {
  const r = await fetch(`${BASE}/api/sessions`, { method: "POST", headers: auth(token), body: JSON.stringify(body) });
  if (!r.ok) throw new Error("Could not create the adventure");
  return r.json();
}

export async function getDashboard(token: string): Promise<Dashboard> {
  const r = await fetch(`${BASE}/api/sessions/stats`, { headers: auth(token) });
  if (!r.ok) throw new Error("Could not load your dashboard");
  return r.json();
}

export async function getSession(token: string, id: string): Promise<SessionDetail> {
  const r = await fetch(`${BASE}/api/sessions/${id}`, { headers: auth(token) });
  if (!r.ok) throw new Error("Could not load the adventure");
  return r.json();
}

export async function getWorld(token: string, id: string): Promise<WorldState> {
  const r = await fetch(`${BASE}/api/sessions/${id}/world`, { headers: auth(token) });
  if (!r.ok) throw new Error("Could not load the world");
  return r.json();
}

/**
 * Take a turn. The backend streams Server-Sent Events; we read the fetch stream directly
 * (EventSource can't send the Authorization header, so we parse SSE ourselves).
 */
export async function streamTurn(
  token: string, id: string, action: string,
  handlers: {
    onToken?: (t: string) => void; onDelta?: (d: unknown) => void;
    onDone?: () => void; onError?: (message: string) => void;
  },
) {
  const res = await fetch(`${BASE}/api/sessions/${id}/turns`, {
    method: "POST",
    headers: { ...auth(token), Accept: "text/event-stream" },
    body: JSON.stringify({ action }),
  });
  if (!res.body) throw new Error("No stream");
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split("\n\n");
    buffer = events.pop() ?? "";
    for (const evt of events) {
      const nameLine = evt.split("\n").find((l) => l.startsWith("event:"));
      const dataLine = evt.split("\n").find((l) => l.startsWith("data:"));
      const name = nameLine?.slice(6).trim();
      const data = dataLine?.slice(5).trim() ?? "";
      if (name === "token") handlers.onToken?.(safeText(data));
      else if (name === "delta") handlers.onDelta?.(safeJson(data));
      else if (name === "done") handlers.onDone?.();
      else if (name === "error") handlers.onError?.(errorMessage(safeJson(data)));
    }
  }
}

// The backend emits { error: "..." } as the SSE `error` event's payload; pull out a readable message.
function errorMessage(d: unknown): string {
  const m = (d as { error?: unknown })?.error;
  return typeof m === "string" && m ? m : "The game master ran into a problem. Try again.";
}

// Tokens are sent JSON-encoded so spaces/newlines survive SSE framing; decode back to the raw text.
function safeText(s: string): string { try { const v = JSON.parse(s); return typeof v === "string" ? v : s; } catch { return s; } }
function safeJson(s: string): unknown { try { return JSON.parse(s); } catch { return {}; } }
