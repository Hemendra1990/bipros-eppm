import { describe, it, expect } from "vitest";
import { renderHook } from "@testing-library/react";
import { useVirtualTable } from "../hooks/useVirtualTable";
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

describe("useVirtualTable", () => {
  it("returns a table instance with rows", () => {
    const { result } = renderHook(() =>
      useVirtualTable({ data, columns, estimateRowHeight: 48, overscan: 5 })
    );

    expect(result.current.table).toBeDefined();
    expect(result.current.rows).toHaveLength(3);
    expect(result.current.virtualRows).toBeDefined();
    expect(result.current.parentRef).toBeDefined();
    expect(result.current.totalSize).toBeGreaterThan(0);
  });

  it("returns zero rows for empty data", () => {
    const { result } = renderHook(() =>
      useVirtualTable({ data: [], columns, estimateRowHeight: 48, overscan: 5 })
    );

    expect(result.current.rows).toHaveLength(0);
    expect(result.current.totalSize).toBe(0);
  });
});
