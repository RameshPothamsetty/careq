import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function QueuePageHeader({
  icon,
  title,
  subtitle,
  dashboardPath,
}: {
  icon: string;
  title: string;
  subtitle: string;
  dashboardPath: string;
}) {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <header className="card flex flex-col gap-4 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-3.5">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 text-2xl shadow-lift">
          {icon}
        </div>
        <div>
          <h1 className="text-lg font-bold tracking-tight text-ink sm:text-xl">{title}</h1>
          <p className="text-sm text-ink-muted">{subtitle}</p>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Link to={dashboardPath} className="btn-secondary">
          Dashboard
        </Link>
        <Link to="/profile" className="btn-secondary">
          Profile
        </Link>
        <button onClick={handleLogout} className="btn-danger">
          Logout
        </button>
      </div>
    </header>
  );
}
