import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { Drawer } from "../Drawer";

afterEach(() => {
  cleanup();
  document.body.style.overflow = "";
});

describe("Drawer", () => {
  it("renders title and children when open", () => {
    render(
      <Drawer open onClose={() => {}} title="Add Activity">
        <p>Form body</p>
      </Drawer>
    );
    expect(screen.getByText("Add Activity")).toBeInTheDocument();
    expect(screen.getByText("Form body")).toBeInTheDocument();
  });

  it("renders title and children even when closed (stays mounted for slide-out)", () => {
    render(
      <Drawer open={false} onClose={() => {}} title="Add Activity">
        <p>Form body</p>
      </Drawer>
    );
    expect(screen.getByText("Add Activity")).toBeInTheDocument();
    expect(screen.getByText("Form body")).toBeInTheDocument();
  });

  it("applies translate-x-0 when open and translate-x-full when closed", () => {
    const { rerender } = render(
      <Drawer open onClose={() => {}} title="T">
        <p>x</p>
      </Drawer>
    );
    const panelOpen = screen.getByRole("dialog");
    expect(panelOpen.className).toContain("translate-x-0");
    expect(panelOpen.className).not.toContain("translate-x-full");

    rerender(
      <Drawer open={false} onClose={() => {}} title="T">
        <p>x</p>
      </Drawer>
    );
    const panelClosed = screen.getByRole("dialog");
    expect(panelClosed.className).toContain("translate-x-full");
  });

  it("calls onClose when the X close button is clicked", () => {
    const onClose = vi.fn();
    render(
      <Drawer open onClose={onClose} title="T">
        <p>x</p>
      </Drawer>
    );
    fireEvent.click(screen.getByRole("button", { name: /close/i }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("does NOT call onClose when the backdrop is clicked (strict close)", () => {
    const onClose = vi.fn();
    render(
      <Drawer open onClose={onClose} title="T">
        <p>x</p>
      </Drawer>
    );
    fireEvent.click(screen.getByTestId("drawer-backdrop"));
    expect(onClose).not.toHaveBeenCalled();
  });

  it("does NOT call onClose when Escape is pressed (strict close)", () => {
    const onClose = vi.fn();
    render(
      <Drawer open onClose={onClose} title="T">
        <p>x</p>
      </Drawer>
    );
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).not.toHaveBeenCalled();
  });

  it("locks body scroll while open and restores it on close", () => {
    document.body.style.overflow = "auto";
    const { rerender } = render(
      <Drawer open onClose={() => {}} title="T">
        <p>x</p>
      </Drawer>
    );
    expect(document.body.style.overflow).toBe("hidden");

    rerender(
      <Drawer open={false} onClose={() => {}} title="T">
        <p>x</p>
      </Drawer>
    );
    expect(document.body.style.overflow).toBe("auto");
  });

  it("restores body scroll on unmount", () => {
    document.body.style.overflow = "auto";
    const { unmount } = render(
      <Drawer open onClose={() => {}} title="T">
        <p>x</p>
      </Drawer>
    );
    expect(document.body.style.overflow).toBe("hidden");
    unmount();
    expect(document.body.style.overflow).toBe("auto");
  });

  it("respects custom widthClass", () => {
    render(
      <Drawer open onClose={() => {}} title="T" widthClass="max-w-3xl">
        <p>x</p>
      </Drawer>
    );
    expect(screen.getByRole("dialog").className).toContain("max-w-3xl");
  });
});
