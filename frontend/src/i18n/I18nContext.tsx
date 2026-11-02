import { createContext, Fragment, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { getUiTextOverrides } from '../api/uiTextApi';
import { currentLanguage, LANGUAGE_STORAGE_KEY, setCurrentLanguage, setOverrides, translate, type Language } from './translate';

export { translate, type Language } from './translate';

type I18nValue = {
  language: Language;
  setLanguage: (language: Language) => void;
  t: (text: string, values?: Record<string, string | number>) => string;
  /** Changes when admin text changes arrive, so consumers re-render. */
  overridesVersion: number;
};

// A usable default rather than null, so a component rendered without the
// provider simply shows English.
const I18nContext = createContext<I18nValue>({
  language: 'en',
  setLanguage: () => undefined,
  t: (text, values) => translate('en', text, values),
  overridesVersion: 0,
});

/** NFR-03: the chosen language, remembered per device. English text is the key. */
export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<Language>(currentLanguage);

  useEffect(() => {
    document.documentElement.lang = language;
  }, [language]);

  // FR-20: fetch the admin's text changes. Components re-render with them now;
  // text built when modules loaded picks them up from the cache on the next load.
  const [overridesVersion, setOverridesVersion] = useState(0);
  useEffect(() => {
    let cancelled = false;
    getUiTextOverrides(language)
      .then((next) => {
        if (!cancelled && setOverrides(next)) {
          setOverridesVersion((version) => version + 1);
        }
      })
      .catch(() => {
        // Keep the cached changes; the defaults are still correct text.
      });
    return () => {
      cancelled = true;
    };
  }, [language]);

  const value = useMemo<I18nValue>(() => ({
    language,
    setLanguage: (next) => {
      if (next === language) {
        return;
      }
      setCurrentLanguage(next);
      setLanguageState(next);
      let stored = false;
      try {
        localStorage.setItem(LANGUAGE_STORAGE_KEY, next);
        stored = true;
      } catch {
        // Private windows can refuse storage; the choice still lasts this page view.
      }
      // Text built when modules load (column headers, option lists) only
      // changes on a fresh load. Without storage a reload would lose the choice.
      if (stored && import.meta.env.MODE !== 'test') {
        window.location.reload();
      }
    },
    t: (text, values) => translate(language, text, values),
    overridesVersion,
  }), [language, overridesVersion]);

  // Text from tr() outside React state only redraws on a remount, so the tree
  // remounts when changes arrive that the cache did not already have.
  return (
    <I18nContext.Provider value={value}>
      <Fragment key={overridesVersion}>{children}</Fragment>
    </I18nContext.Provider>
  );
}

export function useI18n() {
  return useContext(I18nContext);
}
