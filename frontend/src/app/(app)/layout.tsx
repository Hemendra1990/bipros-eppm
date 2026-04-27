import { Sidebar } from "@/components/common/Sidebar";
import { Header } from "@/components/common/Header";
import { AccessProvider } from "@/lib/auth/AccessProvider";

export default function AppLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AccessProvider>
      <div className="flex h-full">
        <Sidebar />
        <div className="flex flex-1 flex-col overflow-hidden">
          <Header />
          <main className="flex-1 overflow-y-auto bg-ivory">
            <div className="px-6 py-6">
              {children}
            </div>
          </main>
        </div>
      </div>
    </AccessProvider>
  );
}
