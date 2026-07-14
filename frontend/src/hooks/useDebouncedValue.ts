import { useEffect, useState } from 'react';

/**
 * Trails `value` by `delayMs`, so a search box that triggers a server request
 * fires once the user stops typing instead of on every keystroke.
 */
export function useDebouncedValue<T>(value: T, delayMs = 300) {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
