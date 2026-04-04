"use client";

import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import {
  BarChart3,
  LineChart as LineChartIcon,
  TrendingUp,
  Zap,
} from "lucide-react";

interface DashboardTier {
  id: string;
  title: string;
  description: string;
  icon: React.ReactNode;
  color: string;
}

const dashboardTiers: DashboardTier[] = [
  {
    id: "EXECUTIVE",
    title: "Executive Dashboard",
    description: "Corridor-level overview with key metrics and top risks",
    icon: <Zap size={32} />,
    color: "from-purple-500 to-purple-600",
  },
  {
    id: "PROGRAMME",
    title: "Programme Dashboard",
    description: "EVM metrics, milestones, and contractor performance",
    icon: <BarChart3 size={32} />,
    color: "from-blue-500 to-blue-600",
  },
  {
    id: "OPERATIONAL",
    title: "Operational Dashboard",
    description: "RA bills, resources, and activity progress by WBS",
    icon: <LineChartIcon size={32} />,
    color: "from-green-500 to-green-600",
  },
  {
    id: "FIELD",
    title: "Field Dashboard",
    description: "Site-level activities and real-time work progress",
    icon: <TrendingUp size={32} />,
    color: "from-orange-500 to-orange-600",
  },
];

export default function DashboardsPage() {
  const router = useRouter();

  const handleSelectTier = (tierId: string) => {
    router.push(`/dashboards/${tierId.toLowerCase()}`);
  };

  return (
    <div className="space-y-8 p-6">
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Dashboards</h1>
        <p className="mt-2 text-gray-600">
          Select a dashboard tier to view project insights and KPIs
        </p>
      </div>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-4">
        {dashboardTiers.map((tier) => (
          <div
            key={tier.id}
            className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm transition-all hover:shadow-md"
          >
            <div className={`bg-gradient-to-br ${tier.color} mb-4 inline-block rounded-lg p-3 text-white`}>
              {tier.icon}
            </div>
            <h3 className="mb-2 text-lg font-semibold text-gray-900">
              {tier.title}
            </h3>
            <p className="mb-4 text-sm text-gray-600">{tier.description}</p>
            <button
              onClick={() => handleSelectTier(tier.id)}
              className="w-full rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              View Dashboard
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
