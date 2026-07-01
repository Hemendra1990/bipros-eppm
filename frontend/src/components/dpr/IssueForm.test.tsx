import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { IssueForm } from "./IssueForm";
import type { DprIssueRow, IssueCategory } from "@/lib/types/dpr";

vi.mock("@/lib/api/activityApi", () => ({
  activityApi: {
    listActivities: vi.fn().mockResolvedValue({ data: { content: [] } }),
  },
}));
vi.mock("@/lib/api/dprIssueApi", () => ({
  dprIssueApi: {
    create: vi.fn(),
    patch: vi.fn(),
    history: vi.fn().mockResolvedValue({ data: [] }),
  },
}));
vi.mock("@/components/dpr/useIssueAssignees", () => ({
  useIssueAssignees: () => ({
    options: [],
    nameByUserId: new Map<string, string>(),
    isLoading: false,
  }),
}));

afterEach(cleanup);

function issueWith(category: IssueCategory): DprIssueRow {
  return {
    id: "i1",
    title: "Scaffold collapse",
    category,
    severity: "HIGH",
    status: "OPEN",
    reportDate: "2026-06-01",
  };
}

function renderForm(issue: DprIssueRow) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <IssueForm projectId="p1" issue={issue} onSaved={() => {}} onCancel={() => {}} />
    </QueryClientProvider>,
  );
}

describe("IssueForm — HSE incident type", () => {
  it("shows the HSE incident type dropdown for a SAFETY issue", () => {
    renderForm(issueWith("SAFETY"));
    expect(screen.getByText("HSE incident type")).toBeInTheDocument();
  });

  it("shows the HSE incident type dropdown for an ENVIRONMENTAL issue", () => {
    renderForm(issueWith("ENVIRONMENTAL"));
    expect(screen.getByText("HSE incident type")).toBeInTheDocument();
  });

  it("hides the HSE incident type dropdown for a QUALITY issue", () => {
    renderForm(issueWith("QUALITY"));
    expect(screen.queryByText("HSE incident type")).toBeNull();
  });
});
