import type { LucideIcon } from "lucide-react";
import {
  BarChart3, Briefcase, Calendar, CheckCircle, FolderTree, Settings,
  Shield, ShoppingBag, TrendingUp, Users,
} from "lucide-react";

export type Module = { n: string; title: string; blurb: string; Icon: LucideIcon };
export const modules: Module[] = [
  { n: "M01", title: "Portfolio",   blurb: "Programmes, baselines, funding.",           Icon: Briefcase },
  { n: "M02", title: "Schedule",    blurb: "CPM, levelling, baselines.",                Icon: Calendar },
  { n: "M03", title: "Cost",        blurb: "Budget, actuals, commitments.",             Icon: TrendingUp },
  { n: "M04", title: "Risk",        blurb: "Register, analysis, mitigation.",           Icon: Shield },
  { n: "M05", title: "Procurement", blurb: "Contracts, POs, awards.",                   Icon: ShoppingBag },
  { n: "M06", title: "Field",       blurb: "Daily progress, quantities.",               Icon: FolderTree },
  { n: "M07", title: "Quality",     blurb: "Inspections, NCRs, punch lists.",           Icon: CheckCircle },
  { n: "M08", title: "HSE",         blurb: "Incidents, audits, compliance.",            Icon: BarChart3 },
  { n: "M09", title: "Resources",   blurb: "People, equipment, rate cards.",            Icon: Users },
];

export type Pillar = { roman: string; title: string; blurb: string; link: string; Icon: LucideIcon };
export const pillars: Pillar[] = [
  {
    roman: "I · PLAN", title: "Plan",
    blurb: "Portfolios, enterprise project structures, baselines — model programmes the way they're actually funded and reported.",
    link: "Portfolio, EPS, baselines →",
    Icon: Briefcase,
  },
  {
    roman: "II · EXECUTE", title: "Execute",
    blurb: "Scheduling, resource levelling, field operations. The only CPM engine built for multi-project portfolios at programme scale.",
    link: "Scheduling, resources, field →",
    Icon: Calendar,
  },
  {
    roman: "III · CONTROL", title: "Control",
    blurb: "Cost, earned-value, risk, variance — one source of truth for the numbers your sponsors and auditors ask for.",
    link: "Cost, EVM, risk →",
    Icon: Settings,
  },
];

export type Stat = { v: string; l: string };
export const stats: Stat[] = [
  { v: "$42B",    l: "Portfolio value under management" },
  { v: "1,800+",  l: "Projects delivered" },
  { v: "32%",     l: "Schedule improvement" },
  { v: "99.95%",  l: "Platform uptime" },
];

export type Step = { roman: string; title: string; blurb: string };
export const steps: Step[] = [
  { roman: "I",   title: "Configure", blurb: "WBS, codes, calendars, cost structures." },
  { roman: "II",  title: "Migrate",   blurb: "Import from P6, MSP, or spreadsheets." },
  { roman: "III", title: "Roll out",  blurb: "Train programme managers & controllers." },
  { roman: "IV",  title: "Operate",   blurb: "Weekly updates, monthly baselines, quarterly reviews." },
];

export type GanttRow = { name: string; tone: "default" | "success" | "warning" | "danger"; leftPct: number; widthPct: number };
export const ganttRows: GanttRow[] = [
  { name: "Earthworks",                   tone: "success", leftPct: 2,  widthPct: 28 },
  { name: "Bridge sections",              tone: "success", leftPct: 12, widthPct: 34 },
  { name: "Track laying",                 tone: "default", leftPct: 32, widthPct: 38 },
  { name: "Signalling",                   tone: "warning", leftPct: 48, widthPct: 24 },
  { name: "Station fit-out",              tone: "default", leftPct: 58, widthPct: 30 },
  { name: "Testing & commissioning",      tone: "danger",  leftPct: 76, widthPct: 22 },
];
