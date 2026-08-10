import { useState, type FormEvent } from 'react';
import { Building2, Pencil, Plus, Trash2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import {
  useGetDepartmentsQuery,
  useCreateDepartmentMutation,
  useUpdateDepartmentMutation,
  useDeleteDepartmentMutation,
} from '../services/rtk/doctorApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import QueuePageHeader from '../components/QueuePageHeader';
import Button from '../components/ui/Button';
import StatusTag from '../components/ui/StatusTag';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';

export default function AdminDepartmentManager() {
  const { user } = useAuth();
  const { isDark } = useTheme();
  const pageClass = isDark
    ? 'dark min-h-screen bg-mesh-dark px-4 py-6 sm:px-6'
    : 'min-h-screen bg-mesh-light px-4 py-6 sm:px-6';
  const { data: departments, isLoading, error: queryError, refetch } = useGetDepartmentsQuery();
  const [createDepartment, { isLoading: isCreating }] = useCreateDepartmentMutation();
  const [updateDepartment, { isLoading: isUpdating }] = useUpdateDepartmentMutation();
  const [deleteDepartment] = useDeleteDepartmentMutation();

  const [formError, setFormError] = useState('');
  const [success, setSuccess] = useState('');

  // Form state
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const isSaving = isCreating || isUpdating;
  const error = queryError ? getErrorMessage(queryError) : formError;

  const handleEdit = (dept: NonNullable<typeof departments>[number]) => {
    setEditingId(dept.id);
    setName(dept.name);
    setDescription(dept.description || '');
    setShowForm(true);
    setFormError('');
    setSuccess('');
  };

  const handleAdd = () => {
    setEditingId(null);
    setName('');
    setDescription('');
    setShowForm(true);
    setFormError('');
    setSuccess('');
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingId(null);
    setName('');
    setDescription('');
    setFormError('');
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError('');
    setSuccess('');

    try {
      if (editingId) {
        await updateDepartment({ id: editingId, body: { name, description } }).unwrap();
        setSuccess('Department updated successfully');
      } else {
        await createDepartment({ name, description }).unwrap();
        setSuccess('Department created successfully');
      }
      // Mutations invalidate the 'Department' tag — the table refetches automatically.
      setShowForm(false);
      setEditingId(null);
      setName('');
      setDescription('');
    } catch (err: unknown) {
      setFormError(getErrorMessage(err));
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this department?')) return;
    setFormError('');
    setSuccess('');
    try {
      await deleteDepartment(id).unwrap();
      setSuccess('Department deleted successfully');
    } catch (err: unknown) {
      setFormError(getErrorMessage(err));
    }
  };

  return (
    <div className={pageClass}>
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="🏥"
          title="Manage Departments"
          subtitle={`Admin — ${user?.fullName || 'Admin'}`}
          dashboardPath="/admin"
        />

        {error && (
          <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
            ⚠ {error}
          </div>
        )}
        {success && (
          <div className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300">
            ✓ {success}
          </div>
        )}

        {!showForm && (
          <div>
            <Button onClick={handleAdd}>
              <Plus className="h-4 w-4" />
              Add Department
            </Button>
          </div>
        )}

        {showForm && (
          <div className="card animate-fade-in-up p-6">
            <h2 className="mb-5 font-display text-lg font-bold text-slate-800 dark:text-slate-100">
              {editingId ? 'Edit Department' : 'New Department'}
            </h2>
            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">Name *</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g., Cardiology"
                  required
                  className="input-field"
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">Description</label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Brief description of the department"
                  rows={3}
                  className="input-field resize-none"
                />
              </div>
              <div className="flex gap-3">
                <Button type="submit" loading={isSaving}>
                  {editingId ? 'Update' : 'Create'}
                </Button>
                <Button type="button" variant="secondary" onClick={handleCancel}>
                  Cancel
                </Button>
              </div>
            </form>
          </div>
        )}

        {isLoading ? (
          <LoadingState label="Loading departments…" />
        ) : queryError ? (
          <ErrorState message={getErrorMessage(queryError)} onRetry={refetch} />
        ) : !departments || departments.length === 0 ? (
          <EmptyState
            icon={<Building2 className="h-8 w-8 text-brand-400" />}
            title="No departments found"
            message='Click "Add Department" to create the first one.'
          />
        ) : (
          <div className="card overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50/60 text-left dark:border-slate-700/50 dark:bg-night-700/40">
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">ID</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">Name</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">Description</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">Status</th>
                    <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {departments.map((dept) => (
                    <tr key={dept.id} className="border-b border-slate-100 transition-colors hover:bg-brand-50/40 dark:border-slate-700/40 dark:hover:bg-brand-500/5">
                      <td className="px-5 py-3.5 text-slate-400 tabular-nums dark:text-slate-500">{dept.id}</td>
                      <td className="px-5 py-3.5 font-semibold text-slate-800 dark:text-slate-100">{dept.name}</td>
                      <td className="px-5 py-3.5 text-slate-500 dark:text-slate-400">{dept.description || '—'}</td>
                      <td className="px-5 py-3.5">
                        <StatusTag status={dept.isActive ? 'ACTIVE' : 'INACTIVE'} />
                      </td>
                      <td className="px-5 py-3.5 text-right">
                        <Button variant="secondary" onClick={() => handleEdit(dept)} className="!px-3 !py-1.5 text-xs">
                          <Pencil className="h-3.5 w-3.5" />
                          Edit
                        </Button>{' '}
                        <Button variant="danger" onClick={() => handleDelete(dept.id)} className="!px-3 !py-1.5 text-xs">
                          <Trash2 className="h-3.5 w-3.5" />
                          Delete
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <footer className="pt-4 text-center text-xs text-slate-400 dark:text-slate-600">
          CareQ — SmartOPD AI | Intelligent Patient Flow Platform
        </footer>
      </div>
    </div>
  );
}
