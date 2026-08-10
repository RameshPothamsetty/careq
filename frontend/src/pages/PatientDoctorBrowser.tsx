import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Activity, FilterX, Stethoscope, Wallet, Clock, Award, MapPin } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { useGetDepartmentsQuery, useGetDoctorsQuery } from '../services/rtk/doctorApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import { useI18n } from '../i18n';
import QueuePageHeader from '../components/QueuePageHeader';
import { StatCard, StatusTag, AvatarInitials, Button, CountUp } from '../components/ui';
import { LoadingState, EmptyState } from '../components/ui/States';

export default function PatientDoctorBrowser() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { isDark } = useTheme();
  const { t } = useI18n();
  const pageClass = isDark
    ? 'dark min-h-screen bg-mesh-dark px-4 py-6 sm:px-6'
    : 'min-h-screen bg-mesh-light px-4 py-6 sm:px-6';
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

  // RTK Query refetches automatically whenever a filter changes. The doctor
  // listing is server-side paginated since Day 7a — the browse screen pulls a
  // generous page (50) so the card grid still feels like a full list, and uses
  // totalElements for the "Total Doctors" stat.
  const {
    data: doctors,
    isFetching,
    error,
  } = useGetDoctorsQuery({
    departmentId: selectedDeptId ? Number(selectedDeptId) : undefined,
    specialization: searchSpecialization || undefined,
    size: 50,
  });

  const isLoading = isFetching;
  const doctorList = doctors?.content ?? [];
  const total = doctors?.totalElements ?? 0;
  const stats = {
    total,
    available: doctorList.filter((d) => d.isAvailable).length,
    departments: new Set(doctorList.map((d) => d.departmentName)).size,
  };

  const clearFilters = () => {
    setSelectedDeptId('');
    setSearchInput('');
    setSearchSpecialization('');
  };

  const hasFilters = selectedDeptId || searchSpecialization;

  return (
    <div className={pageClass}>
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="🔍"
          title={t('browse.title')}
          subtitle={t('browse.subtitle')}
          dashboardPath="/patient"
        />

        {/* Stats */}
        {!isLoading && total > 0 && (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
            <StatCard label={t('browse.totalDoctors')} value={<CountUp value={stats.total} />} icon={<Stethoscope className="h-5 w-5" />} />
            <StatCard
              label={t('browse.availableNow')}
              value={<CountUp value={stats.available} />}
              icon={<Activity className="h-5 w-5" />}
              accent="bg-emerald-50 text-emerald-700"
            />
            <StatCard
              label={t('browse.departments')}
              value={<CountUp value={stats.departments} />}
              icon={<MapPin className="h-5 w-5" />}
              accent="bg-amber-50 text-amber-700"
            />
          </div>
        )}

        {/* Filters */}
        <div className="card p-5">
          <div className="flex flex-wrap items-end gap-4">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                {t('browse.department')}
              </label>
              <select
                value={selectedDeptId}
                onChange={(e) => setSelectedDeptId(e.target.value)}
                className="select-field !min-w-[200px]"
              >
                <option value="">{t('browse.allDepartments')}</option>
                {departments.map((dept) => (
                  <option key={dept.id} value={dept.id}>{dept.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                {t('browse.specialization')}
              </label>
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder={t('browse.specializationPlaceholder')}
                className="input-field !min-w-[220px]"
              />
            </div>
            <Button variant="secondary" onClick={clearFilters}>
              <FilterX className="h-4 w-4" />
              {t('browse.clear')}
            </Button>
          </div>
        </div>

        {error && (
          <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
            ⚠ {getErrorMessage(error)}
          </div>
        )}

        {isLoading ? (
          <LoadingState label={t('browse.loading')} />
        ) : total === 0 ? (
          <EmptyState
            icon={<Stethoscope className="h-8 w-8 text-brand-400" />}
            title={t('browse.emptyTitle')}
            message={hasFilters ? t('browse.emptyFiltered') : t('browse.emptyNone')}
            action={
              hasFilters ? (
                <Button onClick={clearFilters}>{t('browse.clearAllFilters')}</Button>
              ) : undefined
            }
          />
        ) : (
          /* Doctor cards */
          <div className="space-y-3">
            {doctorList.map((doc) => (
              <div
                key={doc.id}
                className="card flex flex-col gap-4 p-5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lift sm:flex-row sm:items-center"
              >
                {/* Identity */}
                <div className="flex min-w-0 flex-1 items-center gap-4">
                  <AvatarInitials name={doc.name || doc.specialization} size="lg" />
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-base font-bold text-slate-800 dark:text-slate-100">{doc.name || doc.specialization}</h3>
                      <StatusTag status={doc.isAvailable ? 'ONLINE' : 'OFFLINE'} />
                    </div>
                    <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                      <span className="inline-flex items-center rounded-md bg-brand-50 px-1.5 py-0.5 text-xs font-semibold text-brand-700 dark:bg-brand-500/15 dark:text-brand-300">
                        {doc.specialization}
                      </span>{' '}
                      <span className="ml-1">{doc.departmentName}</span>{' '}
                      {doc.qualification}
                    </p>
                    <div className="mt-2 flex flex-wrap gap-4 text-xs text-slate-500 dark:text-slate-400">
                      <span className="inline-flex items-center gap-1">
                        <Award className="h-3.5 w-3.5 text-slate-400 dark:text-slate-500" />
                        {t('browse.yearsExp', { y: doc.experienceYears })}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <Wallet className="h-3.5 w-3.5 text-slate-400 dark:text-slate-500" />
                        ₹{doc.consultationFee}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <Clock className="h-3.5 w-3.5 text-slate-400 dark:text-slate-500" />
                        {t('browse.min', { m: doc.avgConsultationTimeMinutes })}
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
                    {doc.isAvailable ? t('browse.joinQueue') : t('browse.unavailable')}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}

        <footer className="pt-4 text-center text-xs text-slate-400 dark:text-slate-600">
          CareQ — SmartOPD AI | {t('browse.subtitle')} · {user?.fullName}
        </footer>
      </div>
    </div>
  );
}
