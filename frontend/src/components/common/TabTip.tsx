import { Info } from "lucide-react";

interface TabTipProps {
  title: string;
  description: string;
  steps?: string[];
}

export function TabTip({ title, description, steps }: TabTipProps) {
  return (
    <div className="mb-6 rounded-lg border border-accent/20 bg-blue-500/5 p-4">
      <div className="flex items-start gap-3">
        <Info size={18} className="text-accent mt-0.5 flex-shrink-0" />
        <div>
          <h4 className="text-sm font-semibold text-accent">{title}</h4>
          <p className="mt-1 text-sm text-text-secondary">{description}</p>
          {steps && steps.length > 0 && (
            <ol className="mt-2 list-decimal list-inside text-sm text-text-secondary space-y-1">
              {steps.map((step, i) => (
                <li key={i}>{step}</li>
              ))}
            </ol>
          )}
        </div>
      </div>
    </div>
  );
}
