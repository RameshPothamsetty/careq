import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import { Building2, Stethoscope, BarChart3, Users, ArrowRight } from 'lucide-react';
import QueuePageHeader from '../components/QueuePageHeader';

const NAV_CARDS = [
  {
    to: '/admin/departments',
    icon: <Building2 className="h-6 w-6 text-white" />,
    title: 'Manage Departments',
    desc: 'Create, edit, and delete hospital departments',
    tint: 'from-brand-500 to-brand-700',
    wide: false,
  },
  {
    to: '/admin/doctors',
    icon: <Stethoscope className="h-6 w-6 text-white" />,
    title: 'Manage Doctors',
    desc: 'Add, edit, and manage doctor catalog entries',
    tint: 'from-sky-500 to-sky-700',
    wide: false,
  },
  {
    to: '/admin/queue',
    icon: <BarChart3 className="h-6 w-6 text-white" />,
    title: 'Live Queue Overview',
    desc: 'Hospital-wide waiting counts, delays & doctor load in real time',
    tint: 'from-teal-500 to-teal-700',
    wide: true,
  },
  {
    to: '/admin/users',
    icon: <Users className="h-6 w-6 text-white" />,
    title: 'Manage Users',
    desc: 'Search the user directory by name or email',
    tint: 'from-violet-500 to-violet-700',
    wide: false,
  },
] as const;

export default function AdminDashboard() {
  const { user } = useAuth();

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="⚙️"
          title="Admin Dashboard"
          subtitle={`Welcome, ${user?.fullName || 'Admin'}`}
          dashboardPath="/admin"
          showDashboard={false}
        />

        <div className="grid gap-5 sm:grid-cols-2">
          {NAV_CARDS.map((card) => (
            <Link
              key={card.to}
              to={card.to}
              className={`card group p-6 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lift ${
                card.wide ? 'sm:col-span-2' : ''
              }`}
            >
              <div className="flex items-start justify-between">
                <div className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br ${card.tint} shadow-lift`}>
                  {card.icon}
                </div>
                <ArrowRight className="h-5 w-5 text-slate-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-brand-600" />
              </div>
              <h2 className="mt-4 text-lg font-bold text-slate-800">{card.title}</h2>
              <p className="mt-1 text-sm text-slate-500">{card.desc}</p>
            </Link>
          ))}
        </div>

        <div className="card p-6">
          <h2 className="text-base font-bold text-slate-800">Welcome, {user?.fullName}!</h2>
          <p className="mt-1 text-sm leading-relaxed text-slate-500">
            You are logged in as an <strong className="text-brand-700">Admin</strong>. Manage departments,
            doctors, and monitor queue operations across the hospital.
          </p>
        </div>

        <div className="card p-6">
          <h2 className="mb-4 text-base font-bold text-slate-800">Account Info</h2>
          <div className="grid gap-3 text-sm sm:grid-cols-3">
            <div className="rounded-xl bg-gray-50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Name</p>
              <p className="mt-1 font-semibold text-slate-800">{user?.fullName}</p>
            </div>
            <div className="rounded-xl bg-gray-50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Email</p>
              <p className="mt-1 break-all font-semibold text-slate-800">{user?.email}</p>
            </div>
            <div className="rounded-xl bg-gray-50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Role</p>
              <p className="mt-1 font-semibold text-slate-800">Admin</p>
            </div>
          </div>
        </div>

        <footer className="pt-4 text-center text-xs text-slate-400">
          CareQ — SmartOPD AI | Intelligent Patient Flow Platform
        </footer>
      </div>
    </div>
  );
}
