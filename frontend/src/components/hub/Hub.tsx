"use client";

import { HubGreeting } from "./HubGreeting";
import { HubHero } from "./HubHero";
import { HubToolsGrid } from "./HubToolsGrid";

export function Hub() {
  return (
    <div data-testid="hub-root" className="mx-auto max-w-[1280px]">
      <HubGreeting />
      <HubHero />
      <HubToolsGrid />
    </div>
  );
}
