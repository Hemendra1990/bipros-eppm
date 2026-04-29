import Link from "next/link";

export default function LabourMasterLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="space-y-4">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Labour Master</h1>
        <nav className="flex gap-2 text-sm">
          <Link className="px-3 py-1.5 rounded bg-slate-100" href="/labour-master">Dashboard</Link>
          <Link className="px-3 py-1.5 rounded bg-slate-100" href="/labour-master/cards">Cards</Link>
          <Link className="px-3 py-1.5 rounded bg-slate-100" href="/labour-master/table">Table</Link>
          <Link className="px-3 py-1.5 rounded bg-slate-100" href="/labour-master/reference">Reference</Link>
          <Link className="px-3 py-1.5 rounded bg-slate-900 text-white" href="/labour-master/new">+ Add</Link>
        </nav>
      </header>
      {children}
    </div>
  );
}
