/**
 * Case-insensitive "does any of these fields contain the search term" check.
 * An empty term matches everything, so pages can pass the raw input straight in.
 */
export function matchesSearch(term: string, ...fields: (string | number | null | undefined)[]) {
  const needle = term.trim().toLowerCase();
  if (!needle) {
    return true;
  }
  return fields.some((field) => String(field ?? '').toLowerCase().includes(needle));
}
