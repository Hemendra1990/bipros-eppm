"use client";

import React, { createContext, useContext, useId, useState } from "react";
import { cn } from "@/lib/utils/cn";

type TabsContextValue = {
  value: string;
  setValue: (v: string) => void;
  baseId: string;
};

const TabsContext = createContext<TabsContextValue | null>(null);

function useTabsContext(component: string): TabsContextValue {
  const ctx = useContext(TabsContext);
  if (!ctx) {
    throw new Error(`<${component}> must be used inside <Tabs>`);
  }
  return ctx;
}

export interface TabsProps extends Omit<React.HTMLAttributes<HTMLDivElement>, "onChange"> {
  defaultValue: string;
  value?: string;
  onValueChange?: (value: string) => void;
}

export function Tabs({
  defaultValue,
  value: controlledValue,
  onValueChange,
  className,
  children,
  ...rest
}: TabsProps) {
  const [internal, setInternal] = useState(defaultValue);
  const value = controlledValue ?? internal;
  const baseId = useId();

  const setValue = (v: string) => {
    if (controlledValue === undefined) setInternal(v);
    onValueChange?.(v);
  };

  return (
    <TabsContext.Provider value={{ value, setValue, baseId }}>
      <div className={cn("space-y-4", className)} {...rest}>
        {children}
      </div>
    </TabsContext.Provider>
  );
}

export function TabsList({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      role="tablist"
      className={cn(
        "inline-flex items-center gap-1 rounded-lg border border-hairline bg-paper p-1",
        className
      )}
      {...props}
    />
  );
}

export interface TabsTriggerProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  value: string;
}

export function TabsTrigger({
  value,
  className,
  children,
  ...rest
}: TabsTriggerProps) {
  const ctx = useTabsContext("TabsTrigger");
  const active = ctx.value === value;
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      aria-controls={`${ctx.baseId}-panel-${value}`}
      id={`${ctx.baseId}-trigger-${value}`}
      onClick={() => ctx.setValue(value)}
      className={cn(
        "px-3.5 py-1.5 text-sm font-medium rounded-md transition-colors",
        active
          ? "bg-charcoal text-paper"
          : "text-slate hover:text-charcoal hover:bg-hairline/40",
        className
      )}
      {...rest}
    >
      {children}
    </button>
  );
}

export interface TabsContentProps extends React.HTMLAttributes<HTMLDivElement> {
  value: string;
}

export function TabsContent({
  value,
  className,
  children,
  ...rest
}: TabsContentProps) {
  const ctx = useTabsContext("TabsContent");
  if (ctx.value !== value) return null;
  return (
    <div
      role="tabpanel"
      id={`${ctx.baseId}-panel-${value}`}
      aria-labelledby={`${ctx.baseId}-trigger-${value}`}
      className={cn("focus:outline-none", className)}
      {...rest}
    >
      {children}
    </div>
  );
}
