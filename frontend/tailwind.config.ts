import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        void: "var(--bg-void)",
        deep: "var(--bg-deep)",
        aurora1: "var(--aurora-1)",
        aurora2: "var(--aurora-2)",
        ember: "var(--ember)",
        ink: "var(--text)",
        muted: "var(--text-muted)",
        ok: "var(--success)",
        warn: "var(--warning)",
        danger: "var(--danger)",
      },
      fontFamily: {
        display: ["var(--font-display)", "serif"],
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
        mono: ["var(--font-mono)", "monospace"],
      },
      borderRadius: { xl2: "20px" },
    },
  },
  plugins: [],
};
export default config;
