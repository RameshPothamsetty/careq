import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, Eye, Pencil, Plus, Search, Stethoscope, Trash2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import {
  useGetDoctorsQuery,
  useGetDepartmentsQuery,
  useCreateDoctorMutation,
  useUpdateDoctorMutation,
  useDeleteDoctorMutation,
} from '../services/rtk/doctorApi';
import { useGetUsersQuery } from '../services/rtk/userApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import type { DoctorCatalogResponse } from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';
import DoctorDetailDrawer from '../components/DoctorDetailDrawer';
import Button from '../components/ui/Button';
import StatusTag from '../components/ui/StatusTag';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';

const PAGE_SIZE = 10;
type SortBy = 'name' | 'consultationFee' | 'experienceYears';

/** Sortable columns: label → API sortBy property. */
const SORTABLE_COLUMNS: { label: string; sortBy: SortBy; className?: string }[] = [
  { label: 'Name', sortBy: 'name' },
  { label: 'Fee', sortBy: 'consultationFee', className: 'text-right' },
  { label: 'Exp', sortBy: 'experienceYears' },
];

export default function AdminDoctorManager() {
  const { user } = useAuth();

  // Pagination + search + sort state (all applied server-side).
  const [page, setPage] = useState(0);
  const [searchInput, setSearchInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState<SortBy>('name');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

  // Debounce the search box so typing fires one query per pause.
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearchQuery(searchInput.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const { data: pageData, isLoading, error: queryError, refetch } = useGetDoctorsQuery({
    page,
    size: PAGE_SIZE,
    sortBy,
    sortDirection,
    search: searchQuery || undefined,
  });
  const { data: departments } = useGetDepartmentsQuery();
  // Day 13: doctor accounts available to link (admin user directory). Lets the
  // admin pick a real DOCTOR account instead of hand-typing a UUID — a typo
  // here was the #1 cause of the "no catalog entry for your account" dead-end
  // doctors hit on their dashboard/queue pages.
  const { data: usersData } = useGetUsersQuery({ size: 1000 });
  const doctorAccounts = useMemo(
    () => (usersData?.content ?? []).filter((u) => u.role === 'DOCTOR'),
    [usersData],
  );
  const [createDoctor, { isLoading: isCreating }] = useCreateDoctorMutation();
  const [updateDoctor, { isLoading: isUpdating }] = useUpdateDoctorMutation();
  const [deleteDoctor] = useDeleteDoctorMutation();

  const closeDrawer = useCallback(() => setViewing(null), []);

  const [formError, setFormError] = useState('');
  const [success, setSuccess] = useState('');

  // Form state
  // Doctor detail drawer
  const [viewing, setViewing] = useState<DoctorCatalogResponse | null>(null);

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [userId, setUserId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [specialization, setSpecialization] = useState('');
  const [qualification, setQualification] = useState('');
  const [experienceYears, setExperienceYears] = useState('');
  const [consultationFee, setConsultationFee] = useState('');
  const [avgConsultationTimeMinutes, setAvgConsultationTimeMinutes] = useState('');

  const isSaving = isCreating || isUpdating;
  const error = queryError ? getErrorMessage(queryError) : formError;
  const doctors = pageData?.content ?? [];
  const totalPages = pageData?.totalPages ?? 1;
  const totalElements = pageData?.totalElements ?? 0;

  const resetForm = () => {
    setEditingId(null);
    setName('');
    setUserId('');
    setDepartmentId('');
    setSpecialization('');
    setQualification('');
    setExperienceYears('');
    setConsultationFee('');
    setAvgConsultationTimeMinutes('');
    setShowForm(false);
    setFormError('');
    setSuccess('');
  };

  const handleEdit = (doc: DoctorCatalogResponse) => {
    setEditingId(doc.id);
    setName(doc.name);
    setUserId(doc.userId);
    setDepartmentId(String(doc.departmentId));
    setSpecialization(doc.specialization);
    setQualification(doc.qualification);
    setExperienceYears(String(doc.experienceYears));
    setConsultationFee(String(doc.consultationFee));
    setAvgConsultationTimeMinutes(String(doc.avgConsultationTimeMinutes));
    setShowForm(true);
    setFormError('');
    setSuccess('');
  };

  const handleAdd = () => {
    resetForm();
    setShowForm(true);
    setFormError('');
    setSuccess('');
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError('');
    setSuccess('');

    try {
      const payload = {
        name,
        userId,
        departmentId: Number(departmentId),
        specialization,
        qualification,
        experienceYears: Number(experienceYears),
        consultationFee: Number(consultationFee),
        avgConsultationTimeMinutes: Number(avgConsultationTimeMinutes),
      };

      if (editingId) {
        await updateDoctor({ id: editingId, body: payload }).unwrap();
        setSuccess('Doctor catalog entry updated successfully');
      } else {
        await createDoctor(payload).unwrap();
        setSuccess('Doctor catalog entry created successfully');
      }

      // Mutations invalidate the 'Doctor' tag — the table refetches automatically.
      resetForm();
    } catch (err: unknown) {
      setFormError(getErrorMessage(err));
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this doctor catalog entry?')) return;
    setFormError('');
    setSuccess('');
    try {
      await deleteDoctor(id).unwrap();
      setSuccess('Doctor catalog entry deleted successfully');
    } catch (err: unknown) {
      setFormError(getErrorMessage(err));
    }
  };

  const toggleSort = (next: SortBy) => {
    if (sortBy === next) {
      setSortDirection((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortBy(next);
      setSortDirection('asc');
    }
    setPage(0);
  };

  const SortHeader = ({ column }: { column: (typeof SORTABLE_COLUMNS)[number] }) => (
    <button
      type="button"
      onClick={() => toggleSort(column.sortBy)}
      className={`inline-flex items-center gap-1 transition-colors hover:text-brand-400 ${column.className ?? ''}`}
      title={`Sort by ${column.label.toLowerCase()}`}
    >
      {column.label}
      {sortBy === column.sortBy ? (
        sortDirection === 'asc' ? (
          <ArrowUp className="h-3 w-3" />
        ) : (
          <ArrowDown className="h-3 w-3" />
        )
      ) : (
        <ArrowUp className="h-3 w-3 opacity-30" />
      )}
    </button>
  );

  return (
    <div className="dark min-h-screen bg-mesh-dark px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-6xl space-y-6">
        <QueuePageHeader
          icon="🩺"
          title="Manage Doctor Catalog"
          subtitle={`Admin — ${user?.fullName || 'Admin'}`}
          dashboardPath="/admin"
        />

        {error && (
          <div className="flex items-center gap-2 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm font-medium text-red-300">
            ⚠ {error}
          </div>
        )}
        {success && (
          <div className="flex items-center gap-2 rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm font-medium text-emerald-300">
            ✓ {success}
          </div>
        )}

        {/* Toolbar: add button + search */}
        {!showForm && (
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <Button onClick={handleAdd}>
              <Plus className="h-4 w-4" />
              Add Doctor Entry
            </Button>
            <div className="relative sm:w-72">
              <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Search name or specialization…"
                className="input-field !pl-10"
              />
            </div>
          </div>
        )}

        {showForm && (
          <div className="card animate-fade-in-up p-6">
            <h2 className="mb-5 font-display text-lg font-bold text-slate-100">
              {editingId ? 'Edit Doctor Entry' : 'New Doctor Catalog Entry'}
            </h2>
            <form onSubmit={handleSubmit} className="space-y-5">
              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-300">Name *</label>
                  <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="e.g., Dr. Arjun Sharma"
                    required
                    className="input-field"
                  />
                </div>
                <div>
                  {/* Day 13: pick the account from the real DOCTOR user list —
                      fills the User ID below and prevents UUID typos (the #1
                      cause of the doctor dashboard/queue "no catalog entry"
                      dead-end). Hidden while editing (User ID is immutable). */}
                  {!editingId && doctorAccounts.length > 0 && (
                    <div>
                      <label className="mb-1.5 block text-sm font-semibold text-slate-300">
                        Link doctor account{' '}
                        <span className="font-normal text-slate-500">(recommended)</span>
                      </label>
                      <select
                        value={userId}
                        onChange={(e) => setUserId(e.target.value)}
                        className="select-field"
                      >
                        <option value="">— Pick a DOCTOR account —</option>
                        {doctorAccounts.map((u) => (
                          <option key={u.userId} value={u.userId}>
                            {u.fullName || 'Doctor'} · {u.email}
                          </option>
                        ))}
                      </select>
                      <p className="mt-1 text-[11px] text-slate-500">
                        Selecting an account fills the User ID automatically — no copy-pasting UUIDs.
                      </p>
                    </div>
                  )}
                </div>
              </div>                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-300">User ID *</label>
                <input
                  type="text"
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  placeholder="UUID from auth-service (filled by the picker above)"
                  required
                  disabled={!!editingId}
                  className="input-field disabled:opacity-60"
                />
              </div>

              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-300">Department *</label>
                  <select
                    value={departmentId}
                    onChange={(e) => setDepartmentId(e.target.value)}
                    required
                    className="select-field"
                  >
                    <option value="">Select department</option>
                    {(departments ?? []).map((dept) => (
                      <option key={dept.id} value={dept.id}>{dept.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-300">Specialization *</label>
                  <input
                    type="text"
                    value={specialization}
                    onChange={(e) => setSpecialization(e.target.value)}
                    placeholder="e.g., Interventional Cardiology"
                    required
                    className="input-field"
                  />
                </div>
              </div>

              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-300">Qualification *</label>
                  <input
                    type="text"
                    value={qualification}
                    onChange={(e) => setQualification(e.target.value)}
                    placeholder="e.g., MD, DM Cardiology"
                    required
                    className="input-field"
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-300">Experience (Years) *</label>
                  <input
                    type="number"
                    value={experienceYears}
                    onChange={(e) => setExperienceYears(e.target.value)}
                    min="0"
                    required
                    className="input-field"
                  />
                </div>
              </div>

              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-300">Consultation Fee *</label>
                  <input
                    type="number"
                    step="0.01"
                    value={consultationFee}
                    onChange={(e) => setConsultationFee(e.target.value)}
                    min="0"
                    required
                    className="input-field"
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-300">Avg Consult Time (min) *</label>
                  <input
                    type="number"
                    value={avgConsultationTimeMinutes}
                    onChange={(e) => setAvgConsultationTimeMinutes(e.target.value)}
                    min="1"
                    required
                    className="input-field"
                  />
                </div>
              </div>

              <div className="flex gap-3">
                <Button type="submit" loading={isSaving}>
                  {editingId ? 'Update' : 'Create'}
                </Button>
                <Button type="button" variant="secondary" onClick={resetForm}>
                  Cancel
                </Button>
              </div>
            </form>
          </div>
        )}

        {isLoading ? (
          <LoadingState label="Loading doctors…" />
        ) : queryError ? (
          <ErrorState message={getErrorMessage(queryError)} onRetry={refetch} />
        ) : doctors.length === 0 ? (
          <EmptyState
            icon={<Stethoscope className="h-8 w-8 text-brand-400" />}
            title={searchQuery ? 'No doctors match your search' : 'No doctor catalog entries found'}
            message={
              searchQuery
                ? `Nothing found for “${searchQuery}”.`
                : 'Click "Add Doctor Entry" to create the first one.'
            }
          />
        ) : (
          <div className="card overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-700/50 bg-night-700/40 text-left">
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">ID</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">
                      <SortHeader column={SORTABLE_COLUMNS[0]} />
                    </th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">User ID</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Department</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Specialization</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">
                      <SortHeader column={SORTABLE_COLUMNS[2]} />
                    </th>
                    <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-slate-500">
                      <SortHeader column={SORTABLE_COLUMNS[1]} />
                    </th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Avg Time</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Status</th>
                    <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-slate-500">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {doctors.map((doc) => (
                    <tr key={doc.id} className="border-b border-slate-700/40 transition-colors hover:bg-brand-500/5">
                      <td className="px-5 py-3.5 text-slate-500 tabular-nums">{doc.id}</td>
                      <td className="px-5 py-3.5 font-semibold text-slate-100">{doc.name}</td>
                      <td className="px-5 py-3.5 font-mono text-xs text-slate-500">
                        {doc.userId.substring(0, 8)}…
                      </td>
                      <td className="px-5 py-3.5 text-slate-100">{doc.departmentName}</td>
                      <td className="px-5 py-3.5 text-slate-100">{doc.specialization}</td>
                      <td className="px-5 py-3.5 text-slate-100 tabular-nums">{doc.experienceYears} yrs</td>
                      <td className="px-5 py-3.5 text-right text-slate-100 tabular-nums">₹{doc.consultationFee}</td>
                      <td className="px-5 py-3.5 text-slate-100 tabular-nums">{doc.avgConsultationTimeMinutes} min</td>
                      <td className="px-5 py-3.5">
                        <StatusTag status={doc.isAvailable ? 'ONLINE' : 'OFFLINE'} />
                      </td>
                      <td className="px-5 py-3.5 text-right">
                        <Button
                          variant="secondary"
                          onClick={() => setViewing(doc)}
                          className="!px-3 !py-1.5 text-xs"
                          title="View full details and live queue"
                        >
                          <Eye className="h-3.5 w-3.5" />
                          View
                        </Button>{' '}
                        <Button variant="secondary" onClick={() => handleEdit(doc)} className="!px-3 !py-1.5 text-xs">
                          <Pencil className="h-3.5 w-3.5" />
                          Edit
                        </Button>{' '}
                        <Button variant="danger" onClick={() => handleDelete(doc.id)} className="!px-3 !py-1.5 text-xs">
                          <Trash2 className="h-3.5 w-3.5" />
                          Delete
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination footer */}
            <div className="flex flex-col items-center justify-between gap-3 border-t border-slate-700/50 px-5 py-3.5 sm:flex-row">
              <p className="text-xs text-slate-500 tabular-nums">
                {totalElements} doctor{totalElements === 1 ? '' : 's'} · Page {pageData ? pageData.number + 1 : 1} of {Math.max(totalPages, 1)}
              </p>
              <div className="flex items-center gap-2">
                <Button
                  variant="secondary"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="!px-3 !py-1.5 text-xs"
                >
                  <ChevronLeft className="h-3.5 w-3.5" />
                  Prev
                </Button>
                <Button
                  variant="secondary"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  className="!px-3 !py-1.5 text-xs"
                >
                  Next
                  <ChevronRight className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          </div>
        )}

        <footer className="pt-4 text-center text-xs text-slate-600">
          CareQ — SmartOPD AI | Intelligent Patient Flow Platform
        </footer>
      </div>

      {/* Doctor details slide-over with live queue */}
      {viewing && <DoctorDetailDrawer doctor={viewing} onClose={closeDrawer} />}
    </div>
  );
}
