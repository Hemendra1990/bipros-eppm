import { LandingNav } from "./_components/LandingNav";
import { LandingHero } from "./_components/LandingHero";
import { LandingPillars } from "./_components/LandingPillars";
import { LandingModules } from "./_components/LandingModules";
import { LandingIndustries } from "./_components/LandingIndustries";
import { LandingStats } from "./_components/LandingStats";
import { LandingShowcase } from "./_components/LandingShowcase";
import { LandingWorkflow } from "./_components/LandingWorkflow";
import { LandingCTA } from "./_components/LandingCTA";
import { LandingFooter } from "./_components/LandingFooter";

export default function LoginLandingPage() {
  return (
    <main className="min-h-screen bg-paper text-charcoal font-sans">
      <LandingNav />
      <LandingHero />
      <LandingPillars />
      <LandingModules />
      <LandingIndustries />
      <LandingStats />
      <LandingShowcase />
      <LandingWorkflow />
      <LandingCTA />
      <LandingFooter />
    </main>
  );
}
