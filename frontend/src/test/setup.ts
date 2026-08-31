import "@testing-library/jest-dom";

// Mock DOM measurements for virtualization libraries in jsdom.
// @tanstack/react-virtual (and others) rely on ResizeObserver +
// getBoundingClientRect to calculate viewport and item sizes.
Object.defineProperty(HTMLElement.prototype, "getBoundingClientRect", {
  configurable: true,
  value: () => ({
    width: 1200,
    height: 800,
    top: 0,
    left: 0,
    bottom: 800,
    right: 1200,
    x: 0,
    y: 0,
    toJSON: () => {},
  }),
});

// Functional ResizeObserver mock that immediately reports a non-zero size
// so virtualizers initialise their viewport on the first render.
global.ResizeObserver = class ResizeObserverMock {
  callback: ResizeObserverCallback;

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
  }

  observe(target: Element) {
    const entry: ResizeObserverEntry = {
      target,
      contentRect: {
        x: 0,
        y: 0,
        width: 1200,
        height: 800,
        top: 0,
        left: 0,
        bottom: 800,
        right: 1200,
      } as DOMRectReadOnly,
      borderBoxSize: [
        { inlineSize: 1200, blockSize: 800 } as ResizeObserverSize,
      ],
      contentBoxSize: [
        { inlineSize: 1200, blockSize: 800 } as ResizeObserverSize,
      ],
      devicePixelContentBoxSize: [
        { inlineSize: 1200, blockSize: 800 } as ResizeObserverSize,
      ],
    };

    this.callback([entry], this);
  }

  unobserve() {}
  disconnect() {}
};
