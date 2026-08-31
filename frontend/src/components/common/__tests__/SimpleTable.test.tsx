import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SimpleTable } from "../SimpleTable";
import type { ColumnDef } from "@tanstack/react-table";

interface TestRow {
  id: string;
  name: string;
}

const columns: ColumnDef<TestRow, unknown>[] = [
  { accessorKey: "name", header: "Name" },
];

const data: TestRow[] = [
  { id: "1", name: "Alice" },
  { id: "2", name: "Bob" },
];

describe("SimpleTable", () => {
  it("renders headers and rows without virtualization", () => {
    render(<SimpleTable data={data} columns={columns} />);

    expect(screen.getByText("Name")).toBeInTheDocument();
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
  });

  it("shows empty message for empty data", () => {
    render(
      <SimpleTable
        data={[]}
        columns={columns}
        emptyMessage="Empty table"
      />
    );

    expect(screen.getByText("Empty table")).toBeInTheDocument();
  });
});
