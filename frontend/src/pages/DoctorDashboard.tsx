import { useMemo, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';
import { Clock, ArrowRight, PhoneCall, Users, UserCheck, Activity } from 'lucide-react';
import { skipToken } from '@reduxjs/toolkit/query/react';
import { useGetDoctorsQuery, useToggleAvailabilityMutation } from '../services/rtk/doctorApi';
import { useGetDoctorQueueQuery, useCallNextMutation } from '../services/rtk/queueApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import type { QueueEntryResponse } from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';
import { StatusTag, Button, StatCard, LiveBadge, AvatarInitials } from '../components/ui';
import { LoadingState } from '../components/ui/States';

const POLL_INTERVAL_MS = 10_000;

function displayName(entry: QueueEntryResponse) {
  return entry.patientName || `#${entry.patientId.slice(0, 4).toUpperCase()}`;
}

export default function DoctorDashboard() {
  const { user } = useAuth();
  // A single large page guarantees the doctor's own catalog entry is in the
  // cache even for a big catalog (listing is server-side paginated since Day 7a).
  const { data: doctors, isLoading, error: queryError } = useGetDoctorsQuery({ size: 1000 });
  const [toggleAvailability, { isLoading: isToggling }] = useToggleAvailabilityMutation();
  const [callNext] = useCallNextMutation();
  const [actionError, setActionError] = useState('');
  const [success, setSuccess] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);

  const doctorEntry = useMemo(
    () => doctors?.content.find((doc) => doc.userId === user?.id) ?? null,
    [doctors, user?.id],
  );

  // Live queue snapshot — same endpoint the queue page polls; the dashboard
  // just subscribes to its own copy (RTK dedupes cache keys, so this is one
  // request, not two).
  const {
    data: queue,
    isLoading: queueLoading,
    error: queueError,
    fulfilledTimeStamp,
  } = useGetDoctorQueueQuery(
    doctorEntry ? { doctorCatalogEntryId: doctorEntry.id } : skipToken,
    { pollingInterval: POLL_INTERVAL_MS },
  );

  const error = queryError ? getErrorMessage(queryError) : actionError;
  const queueErrorMessage = queueError ? getErrorMessage(queueError) : '';

  const entries = queue ?? [];
  const waitingEntries = entries.filter((e) => e.status === 'WAITING');
  const waitingCount = waitingEntries.length;
  const inProgressCount = entries.filter((e) => e.status === 'IN_PROGRESS').length;
  const longestWait = waitingEntries.reduce(
    (max, e) => Math.max(max, e.predictedWaitMinutes ?? 0),
    0,
  );
  const firstWaiting = waitingEntries[0] ?? null;
  const secondsAgo = Math.max(
    0,
    Math.round((Date.now() - (fulfilledTimeStamp ?? Date.now())) / 1000),
  );
  const showQueue = !!doctorEntry && !queryError;

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

  const handleCallNext = async () => {
    if (!firstWaiting) return;
    setActionError('');
    setSuccess('');
    setBusyId(firstWaiting.id);
    try {
      await callNext(firstWaiting.id).unwrap();
      setSuccess(`Called ${displayName(firstWaiting)} — consultation started`);
      setTimeout(() => setSuccess(''), 3000);
    } catch (err: unknown) {
      setActionError(getErrorMessage(err));
    } finally {
      setBusyId(null);
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
                  <h2 className="text-lg font-bold text-slate-800">{doctorEntry.name || doctorEntry.specialization}</h2>
                  <StatusTag status={doctorEntry.isAvailable ? 'ONLINE' : 'OFFLINE'} />
                </div>
                <p className="mt-1 text-sm text-slate-500">
                  {doctorEntry.specialization} · {doctorEntry.departmentName} · {doctorEntry.qualification} · {doctorEntry.experienceYears} years exp
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

        {/* Live queue snapshot — stats + next patient, right on the dashboard */}
        {showQueue && (
          <div className="space-y-3">
            <div className="flex items-center justify-between px-1">
              <LiveBadge lastUpdatedSeconds={secondsAgo} />
              <Link
                to="/doctor/queue"
                className="text-xs font-semibold text-brand-600 transition-colors hover:text-brand-700"
              >
                Open full queue →
              </Link>
            </div>

            {queueLoading && !queue ? (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="h-24 animate-pulse rounded-xl bg-gray-100" />
                ))}
              </div>
            ) : queueErrorMessage ? (
              <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-700">
                ⚠ Couldn't load live queue: {queueErrorMessage}
              </div>
            ) : (
              <>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
                  <StatCard
                    label="Waiting"
                    value={waitingCount}
                    icon={<Users className="h-5 w-5" />}
                    accent="bg-brand-50 text-brand-700"
                  />
                  <StatCard
                    label="In consultation"
                    value={inProgressCount}
                    icon={<UserCheck className="h-5 w-5" />}
                    accent="bg-violet-50 text-violet-700"
                  />
                  <StatCard
                    label="Longest wait"
                    value={`${longestWait} min`}
                    icon={<Clock className="h-5 w-5" />}
                    accent="bg-amber-50 text-amber-700"
                  />
                </div>

                {firstWaiting ? (
                  <div className="card flex flex-col items-center justify-between gap-4 border-brand-100 bg-gradient-to-r from-brand-50 to-white p-5 sm:flex-row">
                    <div className="flex items-center gap-3.5">
                      <AvatarInitials name={displayName(firstWaiting)} size="lg" />
                      <div>
                        <div className="flex items-center gap-2">
                          <p className="text-base font-bold text-slate-800">{displayName(firstWaiting)}</p>
                          <StatusTag status={firstWaiting.effectiveTriage} />
                        </div>
                        <p className="mt-1 text-sm text-slate-500">Next patient ready</p>
                        <p className="mt-0.5 max-w-md truncate text-xs text-slate-400">{firstWaiting.symptomText}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-xs text-slate-400">
                        Wait ≈ {firstWaiting.predictedWaitMinutes ?? 0} min
                      </span>
                      <Button
                        onClick={handleCallNext}
                        loading={busyId === firstWaiting.id}
                        className="shrink-0 px-6"
                      >
                        {!busyId && <PhoneCall className="h-4 w-4" />}
                        Call Next
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="card flex items-center gap-4 p-5">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-emerald-50">
                      <Activity className="h-5 w-5 text-emerald-600" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-bold text-slate-800">Queue is clear</p>
                      <p className="text-xs text-slate-500">
                        No patients waiting right now — new joins appear here automatically.
                      </p>
                    </div>
                  </div>
                )}
              </>
            )}
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
