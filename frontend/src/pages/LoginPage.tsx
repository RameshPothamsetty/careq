import { useState, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Activity, LogIn } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useI18n } from '../i18n';
import Button from '../components/ui/Button';
import LanguageSwitcher from '../components/LanguageSwitcher';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const { t } = useI18n();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      await login(email, password);
      const role = localStorage.getItem('careq_user')
        ? JSON.parse(localStorage.getItem('careq_user')!).role
        : null;
      navigate(`/${role?.toLowerCase() || 'patient'}/dashboard`, { replace: true });
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError(t('auth.loginFailed'));
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-gray-50 px-4 py-10">
      {/* Soft decorative blobs */}
      <div className="pointer-events-none absolute -left-24 -top-24 h-72 w-72 rounded-full bg-brand-200/40 blur-3xl" />
      <div className="pointer-events-none absolute -bottom-28 -right-20 h-80 w-80 rounded-full bg-sky-200/30 blur-3xl" />

      {/* Language switcher — always reachable, even before sign-in */}
      <div className="absolute right-4 top-4 z-10 flex flex-wrap justify-end gap-2">
        <LanguageSwitcher />
      </div>

      <div className="card w-full max-w-md animate-fade-in-up p-8 sm:p-10">
        <div className="text-center">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-gradient-to-br from-brand-500 to-brand-700 shadow-lift">
            <Activity className="h-8 w-8 text-white" />
          </div>
          <h1 className="mt-5 text-3xl font-extrabold tracking-tight text-slate-800">CareQ</h1>
          <p className="mt-1.5 text-sm text-slate-500">{t('auth.signInToContinue')}</p>
        </div>

        {error && (
          <div className="mt-6 flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            ⚠ {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-7 space-y-5">
          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700">{t('auth.email')}</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder={t('auth.emailPlaceholder')}
              required
              autoComplete="email"
              className="input-field"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-semibold text-slate-700">{t('auth.password')}</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={t('auth.passwordPlaceholder')}
              required
              autoComplete="current-password"
              className="input-field"
            />
          </div>

          <Button type="submit" loading={isSubmitting} className="w-full py-3 text-base">
            {!isSubmitting && <LogIn className="h-4 w-4" />}
            {isSubmitting ? t('auth.signingIn') : t('auth.signIn')}
          </Button>
        </form>

        <p className="mt-7 text-center text-sm text-slate-500">
          {t('auth.noAccount')}{' '}
          <Link to="/signup" className="font-semibold text-brand-600 hover:text-brand-700 hover:underline">
            {t('auth.createOne')}
          </Link>
        </p>
      </div>
    </div>
  );
}
