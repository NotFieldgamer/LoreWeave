import type { Metadata } from "next";
import { Fraunces, Inter, JetBrains_Mono } from "next/font/google";
import { ClerkProvider } from "@clerk/nextjs";
import "./globals.css";

const display = Fraunces({ subsets: ["latin"], variable: "--font-display", display: "swap" });
const sans = Inter({ subsets: ["latin"], variable: "--font-sans", display: "swap" });
const mono = JetBrains_Mono({ subsets: ["latin"], variable: "--font-mono", display: "swap" });

export const metadata: Metadata = {
  title: "Loreweave — the AI game master that never forgets",
  description: "Play an adventure where the world remembers everything.",
};

// Theme Clerk's embedded form to match the liquid glass so the custom auth pages feel native.
const clerkAppearance = {
  variables: {
    colorPrimary: "#7C6BF5",
    colorBackground: "transparent",
    colorText: "#ECEBFA",
    colorInputBackground: "rgba(255,255,255,0.04)",
    colorInputText: "#ECEBFA",
    borderRadius: "12px",
    fontFamily: "var(--font-sans)",
  },
  elements: {
    // Make every Clerk wrapper fill our glass card so nothing overflows its width.
    rootBox: "w-full",
    cardBox: "w-full !shadow-none !border-0",
    card: "w-full bg-transparent shadow-none p-0 gap-5",
    header: "hidden",
    headerTitle: "hidden",
    headerSubtitle: "hidden",
    socialButtons: "gap-3",
    socialButtonsBlockButton: "glass btn-secondary !rounded-xl h-11",
    formFieldInput: "glass-input",
    formButtonPrimary: "btn btn-primary w-full !shadow-none normal-case",
    footer: "hidden",
    dividerLine: "bg-white/10",
    dividerText: "text-[var(--text-muted)]",
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <ClerkProvider appearance={clerkAppearance}>
      <html lang="en" className={`${display.variable} ${sans.variable} ${mono.variable}`}>
        <body>
          <div className="aurora" aria-hidden="true" />
          {children}
        </body>
      </html>
    </ClerkProvider>
  );
}
