import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { VirtualDataTable } from "../VirtualDataTable";
import type { ColumnDef } from "@tanstack/react-table";

interface TestRow {
  id: string;
  name: string;
  age: number;
}

const columns: ColumnDef<TestRow, unknown>[] = [
  { accessorKey: "name", header: "Name" },
  { accessorKey: "age", header: "Age" },
];

const data: TestRow[] = [
  { id: "1", name: "Alice", age: 30 },
  { id: "2", name: "Bob", age: 25 },
  { id: "3", name: "Charlie", age: 35 },
];

describe("VirtualDataTable", () => {
  it("renders column headers", () => {
    render(<VirtualDataTable data={data} columns={columns} maxHeight={400} />);

    expect(screen.getByText("Name")).toBeInTheDocument();
    expect(screen.getByText("Age")).toBeInTheDocument();
  });

  it("renders row data", () => {
    render(<VirtualDataTable data={data} columns={columns} maxHeight={400} />);

    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
    expect(screen.getByText("Charlie")).toBeInTheDocument();
  });

  it("shows empty message when no data", () => {
    render(
      <VirtualDataTable
        data={[]}
        columns={columns}
        maxHeight={400}
        emptyMessage="Nothing here"
      />
    );

    expect(screen.getByText("Nothing here")).toBeInTheDocument();
  });

  it("calls onRowClick when a row is clicked", () => {
    const handleClick = vi.fn();
    render(
      <VirtualDataTable
        data={data}
        columns={columns}
        maxHeight={400}
        onRowClick={handleClick}
      />
    );

    fireEvent.click(screen.getByText("Alice"));
    expect(handleClick).toHaveBeenCalledTimes(1);
    expect(handleClick).toHaveBeenCalledWith(data[0]);
  });

  it("filters rows when searching", () => {
    render(<VirtualDataTable data={data} columns={columns} maxHeight={400} />);

    const searchInput = screen.getByPlaceholderText("Search...");
    fireEvent.change(searchInput, { target: { value: "Bob" } });

    expect(screen.getByText("Bob")).toBeInTheDocument();
    expect(screen.queryByText("Alice")).not.toBeInTheDocument();
  });

  it("shows loading skeleton when isLoading is true", () => {
    const { container } = render(
      <VirtualDataTable data={[]} columns={columns} maxHeight={400} isLoading />
    );

    expect(container.querySelector(".animate-pulse")).toBeInTheDocument();
  });
});
