import { FolderTree, Users, Calendar, BarChart3 } from "lucide-react";
import Link from "next/link";

const cards = [
  {
    title: "Projects",
    description: "Manage enterprise project structure and schedules",
    href: "/projects",
    icon: FolderTree,
    color: "bg-blue-50 text-blue-700",
  },
  {
    title: "Resources",
    description: "Allocate and level resources across projects",
    href: "/resources",
    icon: Users,
    color: "bg-green-50 text-green-700",
  },
  {
    title: "Calendars",
    description: "Configure working time and holidays",
    href: "/admin/calendars",
    icon: Calendar,
    color: "bg-purple-50 text-purple-700",
  },
  {
    title: "Reports",
    description: "S-curves, EVM dashboards, and custom reports",
    href: "/reports",
    icon: BarChart3,
    color: "bg-amber-50 text-amber-700",
  },
];

export default function DashboardPage() {
  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-gray-900">Dashboard</h1>
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
        {cards.map((card) => (
          <Link
            key={card.title}
            href={card.href}
            className="group rounded-lg border border-gray-200 bg-white p-6 shadow-sm transition-shadow hover:shadow-md"
          >
            <div className={`mb-4 inline-flex rounded-lg p-3 ${card.color}`}>
              <card.icon size={24} />
            </div>
            <h2 className="text-lg font-semibold text-gray-900 group-hover:text-blue-600">
              {card.title}
            </h2>
            <p className="mt-1 text-sm text-gray-500">{card.description}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
