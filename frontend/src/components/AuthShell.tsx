import type { ReactNode } from 'react';
import { Activity, BrainCircuit, Languages, Timer } from 'lucide-react';
import { useI18n } from '../i18n';
import LanguageSwitcher from './LanguageSwitcher';

/**
 * AuthShell — the split-screen auth layout (2026 redesign):
 * a left brand panel with animated aurora mesh + a decorative live-queue
 * mockup (hidden below lg), and the form card on the right. The whole
 * page floats over the ambient gradient-mesh background.
 *
 * NOTE: the brand name here is deliberately NOT a heading — the form
 * card keeps the single <h1>CareQ</h1> (locked by LoginPage.test.tsx).
 */
export default function AuthShell({ children }: { children: ReactNode }) {
  const { t } = useI18n();

  const features = [
    {
      icon: <BrainCircuit className="h-5 w-5 text-brand-600" />,
      title: t('auth.featTriageTitle'),
      desc: t('auth.featTriageDesc'),
    },
    {
      icon: <Timer className="h-5 w-5 text-sky-600" />,
      title: t('auth.featLiveTitle'),
      desc: t('auth.featLiveDesc'),
    },
    {
      icon: <Languages className="h-5 w-5 text-emerald-600" />,
      title: t('auth.featLangTitle'),
      desc: t('auth.featLangDesc'),
    },
  ];

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-mesh-light px-4 py-12">
      {/* Ambient aurora blobs */}
      <div className="aurora-blob -left-28 top-8 h-80 w-80 bg-brand-300/40" />
      <div className="aurora-blob -right-24 bottom-6 h-96 w-96 bg-sky-300/35" style={{ animationDelay: '-6s' }} />
      <div className="aurora-blob left-1/3 -top-28 h-72 w-72 bg-emerald-200/40" style={{ animationDelay: '-12s' }} />

      {/* Language switcher — always reachable, even before sign-in */}
      <div className="absolute right-4 top-4 z-20 flex flex-wrap justify-end gap-2">
        <LanguageSwitcher />
      </div>

      <div className="relative z-10 grid w-full max-w-5xl items-center gap-10 lg:grid-cols-2">
        {/* ── Brand panel ─────────────────────────────────────── */}
        <div className="hidden lg:block">
          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-800 shadow-lift">
              <Activity className="h-6 w-6 text-white" />
            </div>
            <span className="font-display text-2xl font-extrabold tracking-tight text-slate-800">
              CareQ
              <span className="ml-2 rounded-full bg-brand-100 px-2 py-0.5 align-middle text-[10px] font-bold uppercase tracking-wider text-brand-700">
                SmartOPD AI
              </span>
            </span>
          </div>

          <p className="mt-5 max-w-md font-display text-3xl font-bold leading-snug tracking-tight text-slate-800">
            {t('auth.brandTagline')}
          </p>

          <ul className="mt-8 space-y-4">
            {features.map((f) => (
              <li key={f.title} className="flex items-start gap-3.5">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-slate-100 bg-white shadow-card">
                  {f.icon}
                </div>
                <div>
                  <p className="text-sm font-bold text-slate-800">{f.title}</p>
                  <p className="mt-0.5 text-sm text-slate-500">{f.desc}</p>
                </div>
              </li>
            ))}
          </ul>

          {/* Decorative live-queue mockup */}
          <div className="relative mt-10 max-w-sm overflow-hidden rounded-3xl border border-white/60 bg-white/70 p-5 shadow-lift backdrop-blur-sm">
            <div className="pointer-events-none absolute -right-8 -top-8 h-28 w-28 rounded-full bg-brand-200/40 blur-2xl" />
            <div className="flex items-center justify-between">
              <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Cardiology · Live</p>
              <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-700">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-500" />
                LIVE
              </span>
            </div>
            <div className="mt-4 flex items-end justify-between">
              <div>
                <div className="text-4xl font-extrabold leading-none tracking-tight text-slate-800 tabular-nums">#24</div>
                <p className="mt-1.5 text-[11px] font-semibold text-slate-500">{t('queue.positionInQueue')}</p>
              </div>
              <div className="text-right">
                <div className="text-2xl font-extrabold leading-none tracking-tight text-brand-600 tabular-nums">≈8 min</div>
                <p className="mt-1.5 text-[11px] font-semibold text-slate-500">{t('queue.estimatedWait')}</p>
              </div>
            </div>
            <div className="mt-4 flex items-center gap-1.5">
              {[t('queue.joined'), t('queue.called'), t('queue.completed')].map((label, i) => (
                <div key={label} className="flex-1">
                  <div className={`h-1 rounded-full ${i === 0 ? 'bg-brand-500' : 'bg-slate-200'}`} />
                  <p className={`mt-1 text-[9px] font-semibold uppercase tracking-wide ${i === 0 ? 'text-brand-600' : 'text-slate-400'}`}>
                    {label}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* ── Form ────────────────────────────────────────────── */}
        <div className="w-full animate-fade-in-up">{children}</div>
      </div>
    </div>
  );
}
