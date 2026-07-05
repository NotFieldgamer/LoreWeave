"use client";
import { useState } from "react";
import { ArrowUp } from "lucide-react";

export function TurnComposer({ onSubmit, disabled }: { onSubmit: (action: string) => void; disabled?: boolean }) {
  const [value, setValue] = useState("");
  function send() {
    const v = value.trim();
    if (!v || disabled) return;
    onSubmit(v);
    setValue("");
  }
  return (
    <div className="flex gap-2 items-center border-t border-white/10 pt-3">
      <input
        className="glass-input"
        placeholder="What do you do?"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && send()}
        disabled={disabled}
      />
      <button className="btn btn-primary !w-11 !px-0" onClick={send} aria-label="Take turn" disabled={disabled}>
        <ArrowUp size={18} />
      </button>
    </div>
  );
}
