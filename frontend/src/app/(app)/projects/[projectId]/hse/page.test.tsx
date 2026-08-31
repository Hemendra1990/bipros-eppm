import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import HsePage from "./page";
import type { HseStatisticsResponse } from "@/lib/api/hseApi";

vi.mock("next/navigation", () => ({
  useParams: () => ({ projectId: "p1" }),
}));

const hasPermission = vi.fn((_code: string) => true);
vi.mock("@/lib/state/store", () => ({
  useAuthStore: (sel: (s: { hasPermission: (c: string) => boolean }) => unknown) =>
    sel({ hasPermission }),
}));

vi.mock("@/lib/api/hseApi", () => ({
  hseApi: { statistics: vi.fn(), getMetrics: vi.fn(), putMetrics: vi.fn() },
}));

import { hseApi } from "@/lib/api/hseApi";
const statistics = hseApi.statistics as unknown as ReturnType<typeof vi.fn>;
const getMetrics = hseApi.getMetrics as unknown as ReturnType<typeof vi.fn>;

const zeroStats: HseStatisticsResponse = {
  manHoursWorked: 0,
  manHoursWithoutLti: 0,
  projectDaysWorked: 0,
  projectDaysWithoutLti: 0,
  kmDistanceDriven: 0,
  mtcCount: 0,
  propertyDamageCount: 0,
  nearMissCount: 0,
  fatalityCount: 0,
  lastLtiDate: null,
  calendarHoursPerDay: 8,
  directManHours: 0,
  indirectManHours: 0,
};

const fullStats: HseStatisticsResponse = {
  ...zeroStats,
  manHoursWorked: 1234567,
  manHoursWithoutLti: 234567,
  projectDaysWorked: 120,
  projectDaysWithoutLti: 30,
  kmDistanceDriven: 15460000,
  mtcCount: 3,
  propertyDamageCount: 2,
  nearMissCount: 7,
  fatalityCount: 1,
  lastLtiDate: "2026-05-01",
  directManHours: 1204567,
  indirectManHours: 30000,
};

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <HsePage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  hasPermission.mockReturnValue(true);
  statistics.mockReset();
  getMetrics.mockReset();
});
afterEach(cleanup);

describe("HsePage", () => {
  it("renders the nine statistic rows with thousands-separated values", async () => {
    statistics.mockResolvedValue({ data: fullStats });
    renderPage();
    expect(await screen.findByText("Total Man Hours Worked")).toBeInTheDocument();
    expect(screen.getByText("Total Man Hours without LTI")).toBeInTheDocument();
    expect(screen.getByText("Total Project Days Worked")).toBeInTheDocument();
    expect(screen.getByText("Total Project Days without LTI")).toBeInTheDocument();
    expect(screen.getByText("KM distance Driven")).toBeInTheDocument();
    expect(screen.getByText("Medical Treatment Case (MTC)")).toBeInTheDocument();
    expect(screen.getByText("Property/Asset Damage")).toBeInTheDocument();
    expect(screen.getByText("Near Miss Case (NMC)")).toBeInTheDocument();
    expect(screen.getByText("Fatality")).toBeInTheDocument();
    expect(screen.getByText("1,234,567")).toBeInTheDocument();
    expect(screen.getByText("15,460,000")).toBeInTheDocument();
  });

  it("renders the rows with zeros in the zero state", async () => {
    statistics.mockResolvedValue({ data: zeroStats });
    renderPage();
    expect(await screen.findByText("Total Man Hours Worked")).toBeInTheDocument();
    expect(screen.getAllByText("0").length).toBeGreaterThanOrEqual(9);
  });

  it("shows the Edit HSE inputs action with DPR.UPDATE permission", async () => {
    statistics.mockResolvedValue({ data: zeroStats });
    renderPage();
    await screen.findByText("Total Man Hours Worked");
    expect(
      screen.getByRole("button", { name: /edit hse inputs/i }),
    ).toBeInTheDocument();
  });

  it("hides the Edit HSE inputs action without permission", async () => {
    hasPermission.mockReturnValue(false);
    statistics.mockResolvedValue({ data: zeroStats });
    renderPage();
    await screen.findByText("Total Man Hours Worked");
    expect(
      screen.queryByRole("button", { name: /edit hse inputs/i }),
    ).toBeNull();
  });

  it("shows the direct/indirect split caption when indirect man-hours are present", async () => {
    statistics.mockResolvedValue({ data: fullStats });
    renderPage();
    await screen.findByText("Total Man Hours Worked");
    expect(screen.getByText(/Direct \(site DPR\)/)).toBeInTheDocument();
    expect(screen.getByText(/Indirect \(office\)/)).toBeInTheDocument();
    expect(screen.getByText(/1,204,567/)).toBeInTheDocument();
    expect(screen.getByText(/30,000/)).toBeInTheDocument();
  });

  it("hides the split caption when there are no indirect man-hours", async () => {
    statistics.mockResolvedValue({ data: zeroStats });
    renderPage();
    await screen.findByText("Total Man Hours Worked");
    expect(screen.queryByText(/Direct \(site DPR\)/)).toBeNull();
  });

  it("shows the indirect man-hours input in the edit drawer", async () => {
    statistics.mockResolvedValue({ data: zeroStats });
    getMetrics.mockResolvedValue({ data: { kmDistanceDriven: 0, indirectManHours: 5000 } });
    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /edit hse inputs/i }));
    expect(await screen.findByLabelText(/indirect man-hours/i)).toBeInTheDocument();
    expect(await screen.findByLabelText(/indirect man-hours/i)).toHaveValue(5000);
  });

  it("disables Save while the metrics query is still loading", async () => {
    statistics.mockResolvedValue({ data: zeroStats });
    getMetrics.mockReturnValue(new Promise(() => {}));
    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /edit hse inputs/i }));
    expect(await screen.findByRole("button", { name: /^save$/i })).toBeDisabled();
  });

  it("enables Save once the metrics query has resolved", async () => {
    statistics.mockResolvedValue({ data: zeroStats });
    getMetrics.mockResolvedValue({ data: { kmDistanceDriven: 0, indirectManHours: 5000 } });
    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: /edit hse inputs/i }));
    const indirectInput = await screen.findByLabelText(/indirect man-hours/i);
    await waitFor(() => expect(indirectInput).toHaveValue(5000));
    expect(screen.getByRole("button", { name: /^save$/i })).not.toBeDisabled();
  });
});
