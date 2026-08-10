import { useEffect, useRef, useState } from 'react';

/**
 * CountUp — eases a number from its previous value to the new one over
 * `duration` ms (eased cubic-out, rAF-driven). Lets dashboard stats
 * "roll" into place instead of snapping — the 2026 micro-interaction
 * staple for live data.
 */
export default function CountUp({
  value,
  duration = 700,
  className = '',
}: {
  value: number;
  duration?: number;
  className?: string;
}) {
  const [display, setDisplay] = useState(0);
  const fromRef = useRef(0);
  const rafRef = useRef(0);

  useEffect(() => {
    const from = fromRef.current;
    const delta = value - from;
    if (delta === 0) return;

    const start = performance.now();
    const tick = (now: number) => {
      const p = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - p, 3);
      const current = Math.round(from + delta * eased);
      setDisplay(current);
      fromRef.current = current;
      if (p < 1) {
        rafRef.current = requestAnimationFrame(tick);
      }
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [value, duration]);

  return <span className={className}>{display}</span>;
}
