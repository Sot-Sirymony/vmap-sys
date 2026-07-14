import { useSearchParams } from 'react-router-dom';

/**
 * A filter whose value lives in the URL rather than in component state.
 *
 * This is what lets the dashboard link straight into a filtered view — a tile
 * that reads "Overdue Tasks: 7" can send the user to /tasks?overdue=true and
 * land on exactly those seven. It also makes a filtered list bookmarkable and
 * shareable, which local state can never be.
 *
 * An empty value drops the param entirely, so a cleared filter leaves a clean
 * URL. Navigation replaces rather than pushes, so changing four filters doesn't
 * bury the previous page under four back-button steps.
 */
export function useUrlFilter(key: string): [string, (value: string) => void] {
  const [searchParams, setSearchParams] = useSearchParams();

  const value = searchParams.get(key) ?? '';

  const setValue = (next: string) => {
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current);
        if (next) {
          params.set(key, next);
        } else {
          params.delete(key);
        }
        return params;
      },
      { replace: true },
    );
  };

  return [value, setValue];
}

/** The checkbox variant — present and "true" means on. */
export function useUrlFlag(key: string): [boolean, (value: boolean) => void] {
  const [value, setValue] = useUrlFilter(key);
  return [value === 'true', (next: boolean) => setValue(next ? 'true' : '')];
}
