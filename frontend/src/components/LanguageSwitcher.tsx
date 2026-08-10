import { Languages } from 'lucide-react';
import { useI18n } from '../i18n';
import type { Lang } from '../i18n/translations';

const LANGUAGES: { code: Lang; label: string; native: string }[] = [
  { code: 'en', label: 'English', native: 'English' },
  { code: 'hi', label: 'Hindi', native: 'हिन्दी' },
  { code: 'te', label: 'Telugu', native: 'తెలుగు' },
];

/**
 * Compact language switcher for the header and auth screens.
 * Renders the current language's native name plus the full picker on
 * focus — styled to match the app's `btn-secondary` chrome.
 */
export default function LanguageSwitcher() {
  const { lang, setLang, t } = useI18n();

  return (
    <div
      className="flex h-10 items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-2 text-slate-600 transition-colors hover:bg-slate-50"
      title={t('lang.switcher')}
    >
      <Languages className="h-4 w-4 shrink-0 text-slate-400" />
      <select
        value={lang}
        onChange={(e) => setLang(e.target.value as Lang)}
        aria-label={t('lang.selectLanguage')}
        className="cursor-pointer bg-transparent text-sm font-semibold text-slate-700 outline-none"
      >
        {LANGUAGES.map((l) => (
          <option key={l.code} value={l.code}>
            {l.native}
          </option>
        ))}
      </select>
    </div>
  );
}
