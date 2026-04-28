"use client";

import { useState, type ReactNode } from "react";
import { Stepper, type StepperStep } from "./Stepper";

export interface WizardShellProps {
  steps: StepperStep[];
  /** Per-step renderers. Index aligns with {@link steps}. */
  children: ReactNode[];
  /** Returns true if the current step is valid and the wizard may advance. */
  validateStep: (stepIndex: number) => Promise<boolean> | boolean;
  /** Called on the final step's submit. */
  onSubmit: () => void | Promise<void>;
  submitting?: boolean;
  submitLabel?: string;
}

/**
 * Generic 4-step wizard shell. Owns navigation state, footer buttons, and validation gating.
 * Forms inside each step manage their own RHF state; the shell only asks "is this step valid?"
 * before advancing.
 */
export function WizardShell({
  steps,
  children,
  validateStep,
  onSubmit,
  submitting,
  submitLabel = "Submit Permit",
}: WizardShellProps) {
  const [active, setActive] = useState(0);
  const [completed, setCompleted] = useState(-1);
  const [advancing, setAdvancing] = useState(false);

  const isLast = active === steps.length - 1;

  const handleNext = async () => {
    if (advancing) return;
    setAdvancing(true);
    try {
      const ok = await Promise.resolve(validateStep(active));
      if (!ok) return;
      if (isLast) {
        await onSubmit();
      } else {
        setCompleted((c) => Math.max(c, active));
        setActive((a) => Math.min(a + 1, steps.length - 1));
      }
    } finally {
      setAdvancing(false);
    }
  };

  return (
    <div className="space-y-6">
      <Stepper
        steps={steps}
        activeIndex={active}
        completedIndex={completed}
        onStepClick={(i) => i <= Math.max(completed + 1, active) && setActive(i)}
      />
      <div className="rounded-xl border border-hairline bg-paper p-6 shadow-sm">
        {children[active]}
      </div>
      <div className="flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={() => setActive((a) => Math.max(0, a - 1))}
          disabled={active === 0 || advancing}
          className="rounded-md border border-divider bg-paper px-4 py-2 text-sm font-medium text-charcoal transition hover:bg-ivory disabled:opacity-40"
        >
          ← Previous
        </button>
        <button
          type="button"
          onClick={handleNext}
          disabled={advancing || submitting}
          className="rounded-md bg-gold px-4 py-2 text-sm font-semibold text-charcoal shadow-sm transition hover:bg-gold-deep disabled:opacity-40"
        >
          {isLast ? (submitting || advancing ? "Submitting…" : submitLabel) : "Next Step →"}
        </button>
      </div>
    </div>
  );
}
