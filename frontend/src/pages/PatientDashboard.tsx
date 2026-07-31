import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import { Search, Clock, ArrowRight } from 'lucide-react';
import QueuePageHeader from '../components/QueuePageHeader';

const NAV_CARDS = [
  {
    to: '/patient/doctors',
    icon: <Search className="h-6 w-6 text-white" />,
    title: 'Browse Doctors',
    desc: 'Find and filter doctors by department and specialization',
    tint: 'from-brand-500 to-brand-700',
  },
  {
    to: '/patient/queue',
    icon: <Clock className="h-6 w-6 text-white" />,
    title: 'My Queue',
    desc: 'Join a queue, see your live position and AI-estimated wait time',
    tint: 'from-sky-500 to-sky-700',
  },
] as const;

export default function PatientDashboard() {
  const { user } = useAuth();

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="👤"
          title="Patient Dashboard"
          subtitle={`Welcome, ${user?.fullName || 'Patient'}`}
          dashboardPath="/patient"
          showDashboard={false}
        />

        <div className="grid gap-5 sm:grid-cols-2">
          {NAV_CARDS.map((card) => (
            <Link
              key={card.to}
              to={card.to}
              className="card group p-6 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lift"
            >
              <div className="flex items-start justify-between">
                <div className={`flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br ${card.tint} shadow-lift`}>
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
            You are logged in as <strong className="text-brand-700">Patient</strong>. Browse doctors and
            join a queue to get started — your position and AI-estimated wait time will update live.
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
              <p className="mt-1 font-semibold text-slate-800">Patient</p>
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
