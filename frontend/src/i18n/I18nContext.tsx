import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { km } from './km';

export type Language = 'en' | 'km';

const STORAGE_KEY = 'vms.language';

function storedLanguage(): Language {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'km' ? 'km' : 'en';
  } catch {
    return 'en';
  }
}

export function translate(language: Language, text: string, values?: Record<string, string | number>) {
  const template = language === 'km' ? km[text] ?? text : text;
  return values
    ? template.replace(/\{(\w+)\}/g, (match, key: string) => (key in values ? String(values[key]) : match))
    : template;
}

type I18nValue = {
  language: Language;
  setLanguage: (language: Language) => void;
  t: (text: string, values?: Record<string, string | number>) => string;
};

// A usable default rather than null, so a component rendered without the
// provider simply shows English.
const I18nContext = createContext<I18nValue>({
  language: 'en',
  setLanguage: () => undefined,
  t: (text, values) => translate('en', text, values),
});

/** NFR-03: the chosen language, remembered per device. English text is the key. */
export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<Language>(storedLanguage);

  useEffect(() => {
    document.documentElement.lang = language;
  }, [language]);

  const value = useMemo<I18nValue>(() => ({
    language,
    setLanguage: (next) => {
      setLanguageState(next);
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch {
        // Private windows can refuse storage; the choice still lasts this session.
      }
    },
    t: (text, values) => translate(language, text, values),
  }), [language]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  return useContext(I18nContext);
}
