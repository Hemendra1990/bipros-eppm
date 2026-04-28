"use client";

import { Check } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface StepperStep {
  key: string;
  label: string;
}

export interface StepperProps {
  steps: StepperStep[];
  activeIndex: number;
  completedIndex: number; // index of last completed step (-1 if none)
  onStepClick?: (index: number) => void;
}

/** Visual horizontal stepper with active / completed / disabled states. */
export function Stepper({ steps, activeIndex, completedIndex, onStepClick }: StepperProps) {
  return (
    <ol className="flex items-center gap-2 text-sm">
      {steps.map((step, i) => {
        const completed = i <= completedIndex;
        const active = i === activeIndex;
        const clickable = onStepClick && i <= Math.max(completedIndex + 1, activeIndex);
        return (
          <li key={step.key} className="flex flex-1 items-center gap-2">
            <button
              type="button"
              disabled={!clickable}
              onClick={() => clickable && onStepClick?.(i)}
              className={cn(
                "flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 text-xs font-semibold transition",
                completed && "border-emerald bg-emerald text-white",
                active && !completed && "border-gold bg-gold text-charcoal shadow-sm",
                !active && !completed && "border-divider bg-paper text-slate"
              )}
              aria-current={active ? "step" : undefined}
            >
              {completed ? <Check size={14} strokeWidth={3} /> : i + 1}
            </button>
            <span
              className={cn(
                "truncate text-sm font-medium",
                active && "text-charcoal",
                completed && "text-emerald",
                !active && !completed && "text-slate"
              )}
            >
              {step.label}
            </span>
            {i < steps.length - 1 && (
              <div
                className={cn(
                  "ml-2 h-px flex-1",
                  i <= completedIndex ? "bg-emerald" : "bg-divider"
                )}
              />
            )}
          </li>
        );
      })}
    </ol>
  );
}
