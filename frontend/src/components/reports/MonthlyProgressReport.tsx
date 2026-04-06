"use client";

import { useMemo } from "react";
import { CheckCircle, Clock, AlertCircle } from "lucide-react";
import { Progress } from "@/components/ui/progress";
import type { MonthlyProgressData } from "@/lib/api/reportDataApi";

interface MonthlyProgressReportProps {
  data: MonthlyProgressData;
}

export function MonthlyProgressReport({ data }: MonthlyProgressReportProps) {
  const costVariance = useMemo(() => {
    const variance = data.budgetAmount - data.actualCost;
    const percentage = data.budgetAmount > 0 ? (variance / data.budgetAmount) * 100 : 0;
    return { amount: variance, percentage };
  }, [data.budgetAmount, data.actualCost]);

  const scheduleStatus = useMemo(() => {
    if (data.overallPercentComplete >= 100) return "completed";
    if (data.overallPercentComplete >= 50) return "progressing";
    return "at-risk";
  }, [data.overallPercentComplete]);

  return (
    <div className="space-y-6">
      {/* Header Info */}
      <div className="bg-gradient-to-r from-blue-50 to-indigo-50 p-4 rounded-lg border border-blue-200">
        <div className="flex justify-between items-start">
          <div>
            <h3 className="font-semibold text-lg text-white">{data.projectName}</h3>
            <p className="text-sm text-slate-400">Code: {data.projectCode} | Period: {data.period}</p>
          </div>
          <div className={`px-3 py-1 rounded-full text-sm font-semibold ${
            scheduleStatus === "completed" ? "bg-emerald-500/10 text-emerald-400" :
            scheduleStatus === "progressing" ? "bg-blue-500/10 text-blue-700" :
            "bg-red-500/10 text-red-400"
          }`}>
            {scheduleStatus === "completed" ? "Completed" :
             scheduleStatus === "progressing" ? "In Progress" :
             "At Risk"}
          </div>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs text-slate-500 uppercase tracking-wider">Total Activities</p>
              <p className="text-2xl font-bold text-white">{data.totalActivities}</p>
            </div>
            <Clock className="text-slate-500" size={24} />
          </div>
        </div>

        <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs text-slate-500 uppercase tracking-wider">Completed</p>
              <p className="text-2xl font-bold text-emerald-400">{data.completedActivities}</p>
            </div>
            <CheckCircle className="text-green-400" size={24} />
          </div>
        </div>

        <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs text-slate-500 uppercase tracking-wider">In Progress</p>
              <p className="text-2xl font-bold text-blue-400">{data.inProgressActivities}</p>
            </div>
            <Clock className="text-blue-400" size={24} />
          </div>
        </div>

        <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs text-slate-500 uppercase tracking-wider">% Complete</p>
              <p className="text-2xl font-bold text-indigo-600">{data.overallPercentComplete.toFixed(1)}%</p>
            </div>
            <CheckCircle className="text-indigo-400" size={24} />
          </div>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
        <h4 className="font-semibold text-white mb-2">Overall Progress</h4>
        <Progress value={data.overallPercentComplete} className="h-3" />
        <div className="flex justify-between text-xs text-slate-400 mt-2">
          <span>0%</span>
          <span>{data.overallPercentComplete.toFixed(1)}%</span>
          <span>100%</span>
        </div>
      </div>

      {/* Budget vs Actual */}
      <div className="grid md:grid-cols-2 gap-4">
        <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
          <h4 className="font-semibold text-white mb-4">Cost Status</h4>
          <div className="space-y-4">
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-slate-400">Budget</span>
                <span className="font-semibold">${data.budgetAmount.toFixed(2)}</span>
              </div>
            </div>
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-slate-400">Actual Cost</span>
                <span className="font-semibold">${data.actualCost.toFixed(2)}</span>
              </div>
            </div>
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-slate-400">Forecast</span>
                <span className="font-semibold">${data.forecastCost.toFixed(2)}</span>
              </div>
            </div>
            <div className="border-t pt-4">
              <div className="flex justify-between text-sm">
                <span className={costVariance.percentage >= 0 ? "text-emerald-400" : "text-red-400"}>
                  Variance
                </span>
                <span className={`font-semibold ${costVariance.percentage >= 0 ? "text-emerald-400" : "text-red-400"}`}>
                  ${costVariance.amount.toFixed(2)} ({costVariance.percentage.toFixed(1)}%)
                </span>
              </div>
            </div>
          </div>
        </div>

        <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
          <h4 className="font-semibold text-white mb-4">Milestone Status</h4>
          <div className="space-y-4">
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-slate-400">Total Milestones</span>
                <span className="font-semibold">{data.totalMilestones}</span>
              </div>
            </div>
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-slate-400">Achieved</span>
                <span className="font-semibold text-emerald-400">{data.achievedMilestones}</span>
              </div>
            </div>
            <div>
              <div className="flex justify-between text-sm mb-1">
                <span className="text-slate-400">Pending</span>
                <span className="font-semibold text-orange-600">
                  {data.totalMilestones - data.achievedMilestones}
                </span>
              </div>
            </div>
            <div className="border-t pt-4">
              <div className="flex items-center justify-between">
                <span className="text-slate-400">Open Risks</span>
                <div className="flex gap-2">
                  <span className="px-2 py-1 bg-red-500/10 text-red-400 text-xs rounded-full font-semibold">
                    High: {data.highRisks}
                  </span>
                  <span className="px-2 py-1 bg-orange-500/10 text-orange-400 text-xs rounded-full font-semibold">
                    Total: {data.openRisks}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Top Delayed Activities */}
      {data.topDelayedActivities.length > 0 && (
        <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
          <h4 className="font-semibold text-white mb-4 flex items-center gap-2">
            <AlertCircle className="text-orange-500" size={20} />
            Top Delayed Activities
          </h4>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-800">
                  <th className="text-left py-2 px-2 text-slate-400 font-semibold">Code</th>
                  <th className="text-left py-2 px-2 text-slate-400 font-semibold">Name</th>
                  <th className="text-left py-2 px-2 text-slate-400 font-semibold">Status</th>
                  <th className="text-right py-2 px-2 text-slate-400 font-semibold">Days Delayed</th>
                  <th className="text-left py-2 px-2 text-slate-400 font-semibold">Planned Finish</th>
                </tr>
              </thead>
              <tbody>
                {data.topDelayedActivities.map((activity, idx) => (
                  <tr key={idx} className="border-b border-slate-800/50 hover:bg-slate-900/80">
                    <td className="py-3 px-2 font-mono text-white">{activity.code}</td>
                    <td className="py-3 px-2 text-slate-300">{activity.name}</td>
                    <td className="py-3 px-2">
                      <span className="px-2 py-1 bg-orange-500/10 text-orange-400 rounded text-xs font-semibold">
                        {activity.status}
                      </span>
                    </td>
                    <td className="py-3 px-2 text-right text-red-400 font-semibold">
                      {Math.abs(Math.round(activity.totalFloat))} days
                    </td>
                    <td className="py-3 px-2 text-slate-400">
                      {new Date(activity.plannedFinish).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
