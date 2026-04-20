import { useQuery } from "@tanstack/react-query";
import { settingsApi, type CurrencyResponse } from "@/lib/api/settingsApi";

const DEFAULT_CURRENCY: CurrencyResponse = {
  id: "",
  code: "INR",
  name: "Indian Rupee",
  symbol: "\u20B9",
  exchangeRate: 1,
  isBaseCurrency: true,
  decimalPlaces: 2,
};

export function useCurrency() {
  const { data } = useQuery({
    queryKey: ["currencies"],
    queryFn: () => settingsApi.listCurrencies(),
    staleTime: 5 * 60 * 1000,
  });

  const currencies = data?.data ?? [];
  const baseCurrency = currencies.find((c) => c.isBaseCurrency) ?? DEFAULT_CURRENCY;

  const formatCurrency = (amount: number | null | undefined): string => {
    const val = amount ?? 0;
    return `${baseCurrency.symbol}${val.toLocaleString(undefined, {
      minimumFractionDigits: baseCurrency.decimalPlaces,
      maximumFractionDigits: baseCurrency.decimalPlaces,
    })}`;
  };

  return { baseCurrency, currencies, formatCurrency };
}

/**
 * Standalone currency formatter using the default symbol.
 * Use inside components that don't need the full hook (e.g., chart formatters).
 */
export function formatDefaultCurrency(amount: number | null | undefined, symbol = "\u20B9"): string {
  const val = amount ?? 0;
  return `${symbol}${val.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}
