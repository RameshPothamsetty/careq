import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { translations, en, type Lang, type TranslationKey } from './translations';

/**
 * CareQ lightweight i18n (no external library — matches the project's
 * dependency-light convention). The selected language persists in
 * localStorage and is applied to <html lang> for accessibility.
 */

const STORAGE_KEY = 'careq_lang';

interface I18nContextType {
  lang: Lang;
  setLang: (lang: Lang) => void;
  /** Translate a key, substituting {param} placeholders. Falls back to English. */
  t: (key: TranslationKey, params?: Record<string, string | number>) => string;
}

/** The `t` function type — useful for refs/helpers outside components. */
export type I18nT = I18nContextType['t'];

function interpolate(text: string, params?: Record<string, string | number>): string {
  if (!params) return text;
  for (const [name, value] of Object.entries(params)) {
    text = text.split(`{${name}}`).join(String(value));
  }
  return text;
}

/**
 * English-only fallback used when no LanguageProvider wraps the tree (e.g.
 * component tests). The provider overrides this with the real implementation.
 */
const fallbackValue: I18nContextType = {
  lang: 'en',
  setLang: () => {},
  t: (key, params) => interpolate(en[key] ?? key, params),
};

const I18nContext = createContext<I18nContextType>(fallbackValue);

function initialLang(): Lang {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'hi' || stored === 'te' || stored === 'en' ? stored : 'en';
  } catch {
    return 'en';
  }
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(initialLang);

  const setLang = useCallback((next: Lang) => {
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // localStorage can be unavailable (private mode) — the choice just
      // won't persist across reloads.
    }
    setLangState(next);
  }, []);

  const t = useCallback<I18nContextType['t']>(
    (key, params) => {
      const text = translations[lang][key] ?? translations.en[key] ?? key;
      return interpolate(text, params);
    },
    [lang],
  );

  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  const value = useMemo<I18nContextType>(() => ({ lang, setLang, t }), [lang, setLang, t]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextType {
  return useContext(I18nContext);
}
