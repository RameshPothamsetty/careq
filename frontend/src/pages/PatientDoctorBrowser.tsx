import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Activity, FilterX, Stethoscope, Wallet, Clock, Award, MapPin } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useGetDepartmentsQuery, useGetDoctorsQuery } from '../services/rtk/doctorApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import QueuePageHeader from '../components/QueuePageHeader';
import { StatCard, StatusTag, AvatarInitials, Button } from '../components/ui';
import { LoadingState, EmptyState } from '../components/ui/States';

export default function PatientDoctorBrowser() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [selectedDeptId, setSelectedDeptId] = useState<string>('');
  const [searchInput, setSearchInput] = useState('');
  const [searchSpecialization, setSearchSpecialization] = useState('');

  // Debounce the free-text search so typing fires at most one doctor query per
  // 300ms pause instead of one per keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setSearchSpecialization(searchInput.trim()), 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const { data: allDepartments } = useGetDepartmentsQuery();
  const departments = allDepartments?.filter((d) => d.isActive) ?? [];

  // RTK Query refetches automatically whenever a filter changes.
  const {
    data: doctors,
    isFetching,
    error,
  } = useGetDoctorsQuery({
    departmentId: selectedDeptId ? Number(selectedDeptId) : undefined,
    specialization: searchSpecialization || undefined,
  });

  const isLoading = isFetching;
  const total = doctors?.length ?? 0;
  const stats = {
    total,
    available: doctors?.filter((d) => d.isAvailable).length ?? 0,
    departments: new Set(doctors?.map((d) => d.departmentName) ?? []).size,
  };

  const clearFilters = () => {
    setSelectedDeptId('');
    setSearchInput('');
    setSearchSpecialization('');
  };

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="🔍"
          title="Browse Doctors"
          subtitle="Find the right specialist for your care"
          dashboardPath="/patient"
        />

        {/* Stats */}
        {!isLoading && total > 0 && (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
            <StatCard label="Total Doctors" value={stats.total} icon={<Stethoscope className="h-5 w-5" />} />
            <StatCard
              label="Available Now"
              value={stats.available}
              icon={<Activity className="h-5 w-5" />}
              accent="bg-emerald-50 text-emerald-700"
            />
            <StatCard
              label="Departments"
              value={stats.departments}
              icon={<MapPin className="h-5 w-5" />}
              accent="bg-amber-50 text-amber-700"
            />
          </div>
        )}

        {/* Filters */}
        <div className="card p-5">
          <div className="flex flex-wrap items-end gap-4">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">
                🏥 Department
              </label>
              <select
                value={selectedDeptId}
                onChange={(e) => setSelectedDeptId(e.target.value)}
                className="select-field !min-w-[200px]"
              >
                <option value="">All Departments</option>
                {departments.map((dept) => (
                  <option key={dept.id} value={dept.id}>{dept.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">
                🔎 Specialization
              </label>
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="e.g. Cardiology"
                className="input-field !min-w-[220px]"
              />
            </div>
            <Button variant="secondary" onClick={clearFilters}>
              <FilterX className="h-4 w-4" />
              Clear
            </Button>
          </div>
        </div>

        {error && (
          <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            ⚠ {getErrorMessage(error)}
          </div>
        )}

        {isLoading ? (
          <LoadingState label="Finding doctors for you…" />
        ) : total === 0 ? (
          <EmptyState
            icon={<Stethoscope className="h-8 w-8 text-brand-400" />}
            title="No doctors found"
            message={
              selectedDeptId || searchSpecialization
                ? 'Try adjusting your filters to see more results'
                : 'Doctor catalog entries will appear here once added by an Admin'
            }
            action={
              (selectedDeptId || searchSpecialization) ? (
                <Button onClick={clearFilters}>Clear All Filters</Button>
              ) : undefined
            }
          />
        ) : (
          /* Doctor cards */
          <div className="space-y-3">
            {doctors?.map((doc) => (
              <div
                key={doc.id}
                className="card flex flex-col gap-4 p-5 transition-all duration-200 hover:shadow-lift sm:flex-row sm:items-center"
              >
                {/* Identity */}
                <div className="flex min-w-0 flex-1 items-center gap-4">
                  <AvatarInitials name={doc.specialization} size="lg" />
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-base font-bold text-slate-800">{doc.specialization}</h3>
                      <StatusTag status={doc.isAvailable ? 'ONLINE' : 'OFFLINE'} />
                    </div>
                    <p className="mt-1 text-sm text-slate-500">
                      <span className="inline-flex items-center rounded-md bg-brand-50 px-1.5 py-0.5 text-xs font-semibold text-brand-700">
                        {doc.departmentName}
                      </span>{' '}
                      {doc.qualification}
                    </p>
                    <div className="mt-2 flex flex-wrap gap-4 text-xs text-slate-500">
                      <span className="inline-flex items-center gap-1">
                        <Award className="h-3.5 w-3.5 text-slate-400" />
                        {doc.experienceYears} years exp
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <Wallet className="h-3.5 w-3.5 text-slate-400" />
                        ₹{doc.consultationFee}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <Clock className="h-3.5 w-3.5 text-slate-400" />
                        ≈{doc.avgConsultationTimeMinutes} min
                      </span>
                    </div>
                  </div>
                </div>

                {/* CTA */}
                <div className="shrink-0 sm:ml-4">
                  <Button
                    disabled={!doc.isAvailable}
                    onClick={() => navigate(`/patient/queue?doctor=${doc.id}`)}
                    className="w-full sm:w-auto"
                  >
                    {doc.isAvailable ? 'Join Queue →' : 'Unavailable'}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}

        <footer className="pt-4 text-center text-xs text-slate-400">
          CareQ — SmartOPD AI | Find the right specialist · {user?.fullName}
        </footer>
      </div>
    </div>
  );
}
