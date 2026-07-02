const formatters = new Map<number, Intl.NumberFormat>();

export function formatRounded(
  value: number,
  maximumFractionDigits: number,
): string {
  let formatter = formatters.get(maximumFractionDigits);
  if (!formatter) {
    formatter = new Intl.NumberFormat("fi-FI", { maximumFractionDigits });
    formatters.set(maximumFractionDigits, formatter);
  }
  return formatter.format(value);
}
