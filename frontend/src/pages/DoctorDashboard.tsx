import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import { Clock, ArrowRight } from 'lucide-react';
import { useGetDoctorsQuery, useToggleAvailabilityMutation } from '../services/rtk/doctorApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import QueuePageHeader from '../components/QueuePageHeader';
import { StatusTag, Button } from '../components/ui';
import { LoadingState } from '../components/ui/States';

export default function DoctorDashboard() {
  const { user } = useAuth();
  const { data: doctors, isLoading, error: queryError } = useGetDoctorsQuery();
  const [toggleAvailability, { isLoading: isToggling }] = useToggleAvailabilityMutation();
  const [actionError, setActionError] = useState('');
  const [success, setSuccess] = useState('');

  const doctorEntry = doctors?.find((doc) => doc.userId === user?.id) ?? null;
  const error = queryError ? getErrorMessage(queryError) : actionError;

  const handleToggleAvailability = async () => {
    if (!doctorEntry) return;
    setActionError('');
    setSuccess('');
    try {
      const updated = await toggleAvailability({
        isAvailable: !doctorEntry.isAvailable,
      }).unwrap();
      setSuccess(`You are now ${updated.isAvailable ? 'online' : 'offline'}`);
      setTimeout(() => setSuccess(''), 3000);
    } catch (err: unknown) {
      setActionError(getErrorMessage(err));
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
        <div className="mx-auto max-w-5xl">
          <LoadingState label="Loading your profile…" />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="🩺"
          title="Doctor Dashboard"
          subtitle={`Welcome, Dr. ${user?.fullName || 'Doctor'}`}
          dashboardPath="/doctor"
          showDashboard={false}
        />

        {error && (
          <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            ⚠ {error}
          </div>
        )}
        {success && (
          <div className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
            ✓ {success}
          </div>
        )}

        {/* Availability toggle card */}
        {doctorEntry && (
          <div className="card p-6">
            <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-center">
              <div>
                <div className="flex items-center gap-2.5">
                  <h2 className="text-lg font-bold text-slate-800">{doctorEntry.specialization}</h2>
                  <StatusTag status={doctorEntry.isAvailable ? 'ONLINE' : 'OFFLINE'} />
                </div>
                <p className="mt-1 text-sm text-slate-500">
                  {doctorEntry.departmentName} · {doctorEntry.qualification} · {doctorEntry.experienceYears} years exp
                </p>
                <div className="mt-3 flex flex-wrap gap-5 text-sm text-slate-500">
                  <span>💰 Fee: ₹{doctorEntry.consultationFee}</span>
                  <span>⏱ Avg: {doctorEntry.avgConsultationTimeMinutes} min/patient</span>
                </div>
              </div>
              <Button
                onClick={handleToggleAvailability}
                loading={isToggling}
                className={`shrink-0 ${doctorEntry.isAvailable ? '!bg-red-50 !text-red-700 hover:!bg-red-100' : '!bg-emerald-600 !text-white hover:!bg-emerald-700'}`}
              >
                {doctorEntry.isAvailable ? 'Go Offline' : 'Go Online'}
              </Button>
            </div>
          </div>
        )}

        {/* Queue management card */}
        <Link
          to="/doctor/queue"
          className="card group flex items-center gap-4 p-6 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lift"
        >
          <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 shadow-lift">
            <Clock className="h-6 w-6 text-white" />
          </div>
          <div className="flex-1">
            <h2 className="text-lg font-bold text-slate-800">Live Patient Queue</h2>
            <p className="mt-0.5 text-sm text-slate-500">
              Color-coded AI triage, override controls, call-next & complete
            </p>
          </div>
          <ArrowRight className="h-5 w-5 text-slate-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-brand-600" />
        </Link>

        {/* Account info */}
        <div className="card p-6">
          <h2 className="mb-4 text-base font-bold text-slate-800">Account Info</h2>
          <div className="grid gap-3 text-sm sm:grid-cols-3">
            <div className="rounded-xl bg-gray-50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Name</p>
              <p className="mt-1 font-semibold text-slate-800">Dr. {user?.fullName}</p>
            </div>
            <div className="rounded-xl bg-gray-50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Email</p>
              <p className="mt-1 break-all font-semibold text-slate-800">{user?.email}</p>
            </div>
            <div className="rounded-xl bg-gray-50 p-3.5">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Role</p>
              <p className="mt-1 font-semibold text-slate-800">Doctor</p>
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
