"use client";
import type { StorySegment } from "@/lib/types";

export function StoryStream({ segments }: { segments: StorySegment[] }) {
  return (
    <div className="flex flex-col gap-4">
      {segments.map((s, i) =>
        s.role === "gm" ? (
          <p key={i} className="text-ink/90 leading-relaxed">{s.text}</p>
        ) : (
          <div key={i} className="self-end max-w-[82%] rounded-2xl px-3 py-2 text-sm"
               style={{ background: "rgba(124,107,245,0.16)", color: "#CFC7FF" }}>
            {s.text}
          </div>
        ),
      )}
    </div>
  );
}
