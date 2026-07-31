import { useState, useEffect, type FormEvent } from 'react';
import { Pencil, Plus, Stethoscope, Trash2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { api, type DoctorCatalogResponse, type DepartmentResponse } from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';
import Button from '../components/ui/Button';
import StatusTag from '../components/ui/StatusTag';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';

export default function AdminDoctorManager() {
  const { user } = useAuth();
  const [doctors, setDoctors] = useState<DoctorCatalogResponse[]>([]);
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Form state
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [userId, setUserId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [specialization, setSpecialization] = useState('');
  const [qualification, setQualification] = useState('');
  const [experienceYears, setExperienceYears] = useState('');
  const [consultationFee, setConsultationFee] = useState('');
  const [avgConsultationTimeMinutes, setAvgConsultationTimeMinutes] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    Promise.all([fetchDoctors(), fetchDepartments()]);
  }, []);

  const fetchDoctors = async () => {
    try {
      const data = await api.getDoctors();
      setDoctors(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load doctors');
    } finally {
      setIsLoading(false);
    }
  };

  const fetchDepartments = async () => {
    try {
      const data = await api.getDepartments();
      setDepartments(data);
    } catch {
      // Silently fail - departments might load separately
    }
  };

  const resetForm = () => {
    setEditingId(null);
    setUserId('');
    setDepartmentId('');
    setSpecialization('');
    setQualification('');
    setExperienceYears('');
    setConsultationFee('');
    setAvgConsultationTimeMinutes('');
    setShowForm(false);
    setError('');
    setSuccess('');
  };

  const handleEdit = (doc: DoctorCatalogResponse) => {
    setEditingId(doc.id);
    setUserId(doc.userId);
    setDepartmentId(String(doc.departmentId));
    setSpecialization(doc.specialization);
    setQualification(doc.qualification);
    setExperienceYears(String(doc.experienceYears));
    setConsultationFee(String(doc.consultationFee));
    setAvgConsultationTimeMinutes(String(doc.avgConsultationTimeMinutes));
    setShowForm(true);
    setError('');
    setSuccess('');
  };

  const handleAdd = () => {
    resetForm();
    setShowForm(true);
    setError('');
    setSuccess('');
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setIsSaving(true);

    try {
      const payload = {
        userId,
        departmentId: Number(departmentId),
        specialization,
        qualification,
        experienceYears: Number(experienceYears),
        consultationFee: Number(consultationFee),
        avgConsultationTimeMinutes: Number(avgConsultationTimeMinutes),
      };

      if (editingId) {
        await api.updateDoctor(editingId, payload);
        setSuccess('Doctor catalog entry updated successfully');
      } else {
        await api.createDoctor(payload);
        setSuccess('Doctor catalog entry created successfully');
      }

      resetForm();
      await fetchDoctors();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to save doctor');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Are you sure you want to delete this doctor catalog entry?')) return;
    setError('');
    setSuccess('');
    try {
      await api.deleteDoctor(id);
      setSuccess('Doctor catalog entry deleted successfully');
      await fetchDoctors();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to delete doctor');
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-6xl space-y-6">
        <QueuePageHeader
          icon="🩺"
          title="Manage Doctor Catalog"
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
              Add Doctor Entry
            </Button>
          </div>
        )}

        {showForm && (
          <div className="card animate-fade-in-up p-6">
            <h2 className="mb-5 text-lg font-bold text-slate-800">
              {editingId ? 'Edit Doctor Entry' : 'New Doctor Catalog Entry'}
            </h2>
            <form onSubmit={handleSubmit} className="space-y-5">
              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-700">User ID *</label>
                  <input
                    type="text"
                    value={userId}
                    onChange={(e) => setUserId(e.target.value)}
                    placeholder="UUID from auth-service"
                    required
                    disabled={!!editingId}
                    className="input-field disabled:bg-gray-50"
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-700">Department *</label>
                  <select
                    value={departmentId}
                    onChange={(e) => setDepartmentId(e.target.value)}
                    required
                    className="select-field"
                  >
                    <option value="">Select department</option>
                    {departments.map((dept) => (
                      <option key={dept.id} value={dept.id}>{dept.name}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-700">Specialization *</label>
                  <input
                    type="text"
                    value={specialization}
                    onChange={(e) => setSpecialization(e.target.value)}
                    placeholder="e.g., Interventional Cardiology"
                    required
                    className="input-field"
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-700">Qualification *</label>
                  <input
                    type="text"
                    value={qualification}
                    onChange={(e) => setQualification(e.target.value)}
                    placeholder="e.g., MD, DM Cardiology"
                    required
                    className="input-field"
                  />
                </div>
              </div>

              <div className="grid gap-5 sm:grid-cols-3">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-700">Experience (Years) *</label>
                  <input
                    type="number"
                    value={experienceYears}
                    onChange={(e) => setExperienceYears(e.target.value)}
                    min="0"
                    required
                    className="input-field"
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-700">Consultation Fee *</label>
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
                  <label className="mb-1.5 block text-sm font-semibold text-slate-700">Avg Consult Time (min) *</label>
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
        ) : error ? (
          <ErrorState message={error} onRetry={fetchDoctors} />
        ) : doctors.length === 0 ? (
          <EmptyState
            icon={<Stethoscope className="h-8 w-8 text-brand-400" />}
            title="No doctor catalog entries found"
            message='Click "Add Doctor Entry" to create the first one.'
          />
        ) : (
          <div className="card overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50/60 text-left">
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">ID</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">User ID</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Department</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Specialization</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Fee</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Avg Time</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Status</th>
                    <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-slate-400">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {doctors.map((doc) => (
                    <tr key={doc.id} className="border-b border-slate-50 transition-colors hover:bg-brand-50/40">
                      <td className="px-5 py-3.5 text-slate-400">{doc.id}</td>
                      <td className="px-5 py-3.5 font-mono text-xs text-slate-400">
                        {doc.userId.substring(0, 8)}…
                      </td>
                      <td className="px-5 py-3.5 text-slate-800">{doc.departmentName}</td>
                      <td className="px-5 py-3.5 font-semibold text-slate-800">{doc.specialization}</td>
                      <td className="px-5 py-3.5 text-slate-800">₹{doc.consultationFee}</td>
                      <td className="px-5 py-3.5 text-slate-800">{doc.avgConsultationTimeMinutes} min</td>
                      <td className="px-5 py-3.5">
                        <StatusTag status={doc.isAvailable ? 'ONLINE' : 'OFFLINE'} />
                      </td>
                      <td className="px-5 py-3.5 text-right">
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
          </div>
        )}

        <footer className="pt-4 text-center text-xs text-slate-400">
          CareQ — SmartOPD AI | Intelligent Patient Flow Platform
        </footer>
      </div>
    </div>
  );
}
