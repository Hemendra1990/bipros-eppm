import { Sidebar } from "@/components/common/Sidebar";
import { Header } from "@/components/common/Header";

export default function AppLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex h-full">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="relative flex-1 overflow-y-auto bg-background bg-grid p-6">
          {/* Subtle gradient overlay at top */}
          <div className="pointer-events-none absolute top-0 left-0 right-0 h-32 bg-gradient-to-b from-accent-glow to-transparent opacity-20"></div>
          <div className="relative z-0">{children}</div>
        </main>
      </div>
    </div>
  );
}
