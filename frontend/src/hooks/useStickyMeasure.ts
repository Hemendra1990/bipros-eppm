import { useCallback, useRef, useState } from "react";

export function useStickyMeasure<T extends HTMLElement>() {
  const [height, setHeight] = useState(0);
  const elRef = useRef<T | null>(null);
  const roRef = useRef<ResizeObserver | null>(null);

  const ref = useCallback((el: T | null) => {
    if (roRef.current) {
      roRef.current.disconnect();
      roRef.current = null;
    }
    elRef.current = el;
    if (!el) {
      setHeight(0);
      return;
    }
    setHeight(el.offsetHeight);
    const ro = new ResizeObserver((entries) => {
      const h = entries[0]?.contentRect.height ?? el.offsetHeight;
      setHeight(Math.ceil(h));
    });
    ro.observe(el);
    roRef.current = ro;
  }, []);

  return { ref, height };
}
