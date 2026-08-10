import { Link, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { LayoutDashboard, LogOut, User } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useI18n } from '../i18n';
import AvatarInitials from './ui/AvatarInitials';
import Button from './ui/Button';
import NotificationBell from './NotificationBell';
import LanguageSwitcher from './LanguageSwitcher';
import ThemeToggle from './ThemeToggle';

export default function QueuePageHeader({
  icon,
  title,
  subtitle,
  dashboardPath,
  showDashboard = true,
  backTo,
}: {
  icon: string | ReactNode;
  title: string;
  subtitle: string;
  dashboardPath: string;
  showDashboard?: boolean;
  backTo?: string;
}) {
  const { user, logout } = useAuth();
  const { t } = useI18n();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <header className="card flex flex-col gap-4 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-3.5">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 text-2xl shadow-lift">
          {typeof icon === 'string' ? <span className="leading-none">{icon}</span> : icon}
        </div>
        <div>
          <h1 className="font-display text-lg font-bold tracking-tight text-slate-800 dark:text-slate-100 sm:text-xl">{title}</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <NotificationBell />
        <ThemeToggle />
        <LanguageSwitcher />
        {backTo ? (
          <Link to={backTo} className="btn-secondary">
            {t('common.back')}
          </Link>
        ) : showDashboard ? (
          <Link to={dashboardPath} className="btn-secondary">
            <LayoutDashboard className="h-4 w-4" />
            {t('common.dashboard')}
          </Link>
        ) : null}
        <Link to="/profile" className="btn-secondary">
          <User className="h-4 w-4" />
          {t('common.profile')}
        </Link>
        <div className="ml-1 flex items-center gap-2 rounded-xl border border-slate-200 bg-white py-1 pl-1 pr-3 dark:border-slate-600 dark:bg-night-800/70">
          <AvatarInitials name={user?.fullName || 'U'} size="sm" />
          <span className="hidden text-sm font-semibold text-slate-700 dark:text-slate-200 sm:inline">
            {user?.fullName}
          </span>
        </div>
        <Button variant="danger" onClick={handleLogout}>
          <LogOut className="h-4 w-4" />
          {t('common.logout')}
        </Button>
      </div>
    </header>
  );
}
