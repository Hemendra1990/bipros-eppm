import { Info } from "lucide-react";

interface TabTipProps {
  title: string;
  description: string;
  steps?: string[];
}

export function TabTip({ title, description, steps }: TabTipProps) {
  return (
    <div className="mb-6 rounded-xl border border-hairline bg-paper p-5 border-l-[3px] border-l-gold">
      <div className="flex items-start gap-3">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gold-tint text-gold-deep">
          <Info size={16} strokeWidth={1.5} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-1">
            Tip · {title}
          </div>
          <p className="text-sm text-slate leading-relaxed">{description}</p>
          {steps && steps.length > 0 && (
            <ol className="mt-2.5 space-y-1 pl-4 text-sm text-slate list-decimal marker:text-gold-deep marker:font-mono marker:text-xs">
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
