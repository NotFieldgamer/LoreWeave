import Link from "next/link";
import { UserButton } from "@clerk/nextjs";
import { LayoutDashboard, Swords } from "lucide-react";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen mx-auto max-w-7xl px-4 py-4 grid grid-cols-[64px_1fr] md:grid-cols-[224px_1fr] gap-4">
      <aside className="glass p-3 md:p-4 flex flex-col gap-2 h-[calc(100vh-2rem)] sticky top-4">
        <Link href="/dashboard" className="font-display text-xl px-2 py-2 hidden md:block">Loreweave</Link>
        <Link href="/dashboard" className="font-display text-xl px-2 py-2 md:hidden text-center">L</Link>
        <nav className="flex flex-col gap-1 mt-2">
          <Link href="/dashboard" className="btn btn-ghost !justify-start !h-10">
            <LayoutDashboard size={18} /><span className="hidden md:inline">Dashboard</span>
          </Link>
          <Link href="/dashboard" className="btn btn-ghost !justify-start !h-10">
            <Swords size={18} /><span className="hidden md:inline">Adventures</span>
          </Link>
        </nav>
        <div className="mt-auto flex items-center gap-2 px-2">
          <UserButton afterSignOutUrl="/" />
          <span className="text-muted text-sm hidden md:inline">Account</span>
        </div>
      </aside>
      <main className="min-w-0">{children}</main>
    </div>
  );
}
