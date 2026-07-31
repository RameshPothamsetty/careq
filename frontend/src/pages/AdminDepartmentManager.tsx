import { useState, useEffect, type FormEvent } from 'react';
import { Building2, Pencil, Plus, Trash2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { api, type DepartmentResponse } from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';
import Button from '../components/ui/Button';
import StatusTag from '../components/ui/StatusTag';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';

export default function AdminDepartmentManager() {
  const { user } = useAuth();
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Form state
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    fetchDepartments();
  }, []);

  const fetchDepartments = async () => {
    setIsLoading(true);
    setError('');
    try {
      const data = await api.getDepartments();
      setDepartments(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load departments');
    } finally {
      setIsLoading(false);
    }
  };

  const handleEdit = (dept: DepartmentResponse) => {
    setEditingId(dept.id);
    setName(dept.name);
    setDescription(dept.description || '');
    setShowForm(true);
    setError('');
    setSuccess('');
  };

  const handleAdd = () => {
    setEditingId(null);
    setName('');
    setDescription('');
    setShowForm(true);
    setError('');
    setSuccess('');
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingId(null);
    setName('');
    setDescription('');
    setError('');
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setIsSaving(true);

    try {
      if (editingId) {
        await api.updateDepartment(editingId, { name, description });
        setSuccess('Department updated successfully');
      } else {
        await api.createDepartment({ name, description });
        setSuccess('Department created successfully');
      }
      setShowForm(false);
      setEditingId(null);
      setName('');
      setDescription('');
      await fetchDepartments();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to save department');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this department?')) return;
    setError('');
    setSuccess('');
    try {
      await api.deleteDepartment(id);
      setSuccess('Department deleted successfully');
      await fetchDepartments();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to delete department');
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-5xl space-y-6">
        <QueuePageHeader
          icon="🏥"
          title="Manage Departments"
          subtitle={`Admin — ${user?.fullName || 'Admin'}`}
          dashboardPath="/admin"
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
            <h2 className="mb-5 text-lg font-bold text-slate-800">
              {editingId ? 'Edit Department' : 'New Department'}
            </h2>
            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label className="mb-1.5 block text-sm font-semibold text-slate-700">Name *</label>
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
                <label className="mb-1.5 block text-sm font-semibold text-slate-700">Description</label>
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
        ) : error ? (
          <ErrorState message={error} onRetry={fetchDepartments} />
        ) : departments.length === 0 ? (
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
                  <tr className="border-b border-slate-100 bg-slate-50/60 text-left">
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">ID</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Name</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Description</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Status</th>
                    <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-slate-400">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {departments.map((dept) => (
                    <tr key={dept.id} className="border-b border-slate-50 transition-colors hover:bg-brand-50/40">
                      <td className="px-5 py-3.5 text-slate-400">{dept.id}</td>
                      <td className="px-5 py-3.5 font-semibold text-slate-800">{dept.name}</td>
                      <td className="px-5 py-3.5 text-slate-500">{dept.description || '—'}</td>
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

        <footer className="pt-4 text-center text-xs text-slate-400">
          CareQ — SmartOPD AI | Intelligent Patient Flow Platform
        </footer>
      </div>
    </div>
  );
}
