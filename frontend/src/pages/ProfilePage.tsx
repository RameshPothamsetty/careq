import { useState, useEffect, useRef, type FormEvent } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import QueuePageHeader from '../components/QueuePageHeader';
import AvatarInitials from '../components/ui/AvatarInitials';
import Button from '../components/ui/Button';
import { LoadingState } from '../components/ui/States';

interface UserProfile {
  id: number;
  userId: string;
  phone: string | null;
  address: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  profilePictureUrl: string | null;
  role: string;
}

export default function ProfilePage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [gender, setGender] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  useEffect(() => {
    fetchProfile();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchProfile = async () => {
    setIsLoading(true);
    setError('');
    try {
      const data = await api.getProfile();
      setProfile(data);
      fillForm(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load profile');
    } finally {
      setIsLoading(false);
    }
  };

  const fillForm = (data: UserProfile) => {
    setPhone(data.phone || '');
    setAddress(data.address || '');
    setDateOfBirth(data.dateOfBirth || '');
    setGender(data.gender || '');
  };

  const handleEdit = () => {
    if (profile) fillForm(profile);
    setSelectedFile(null);
    setPreviewUrl(null);
    setIsEditing(true);
    setError('');
    setSuccess('');
  };

  const handleCancel = () => {
    if (profile) fillForm(profile);
    setSelectedFile(null);
    setPreviewUrl(null);
    setIsEditing(false);
    setError('');
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setIsSaving(true);

    try {
      let updatedProfile = profile;

      if (selectedFile) {
        try {
          updatedProfile = await api.uploadProfilePicture(selectedFile);
        } catch (uploadErr) {
          setError(uploadErr instanceof Error ? uploadErr.message : 'Failed to upload picture');
          setIsSaving(false);
          return;
        }
      }

      const payload: Record<string, string> = {};
      if (phone) payload.phone = phone;
      if (address) payload.address = address;
      if (dateOfBirth) payload.dateOfBirth = dateOfBirth;
      if (gender) payload.gender = gender;

      if (Object.keys(payload).length > 0) {
        updatedProfile = await api.updateProfile(payload);
      }

      setProfile(updatedProfile);
      setSuccess('Profile updated successfully');
      setSelectedFile(null);
      setPreviewUrl(null);
      setIsEditing(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update profile');
    } finally {
      setIsSaving(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
        <div className="mx-auto max-w-4xl">
          <LoadingState label="Loading profile…" />
        </div>
      </div>
    );
  }

  const displayUrl = previewUrl || profile?.profilePictureUrl;

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-4xl space-y-6">
        <QueuePageHeader
          icon="👤"
          title="My Profile"
          subtitle={`Welcome, ${user?.fullName || 'User'}`}
          dashboardPath={`/${user?.role.toLowerCase() || 'patient'}`}
          showDashboard={false}
          backTo={`/${user?.role.toLowerCase() || 'patient'}`}
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

        <div className="card overflow-hidden">
          {/* Avatar header */}
          <div className="flex flex-col items-center gap-4 border-b border-slate-100 bg-gradient-to-br from-brand-50 to-sky-50 px-6 py-8 sm:flex-row">
            <AvatarInitials name={user?.fullName || 'U'} size="lg" imgUrl={displayUrl} />
            <div className="text-center sm:text-left">
              <h2 className="text-xl font-bold text-slate-800">{user?.fullName}</h2>
              <p className="mt-0.5 text-sm text-slate-500">{user?.email}</p>
              <span className="mt-2 inline-flex items-center rounded-full border border-brand-200 bg-brand-100 px-3 py-0.5 text-xs font-semibold text-brand-700">
                {user?.role}
              </span>
            </div>
          </div>

          {/* Body */}
          {!isEditing ? (
            <div className="space-y-0 p-6">
              {[
                { label: 'Phone', value: profile?.phone || 'Not set' },
                { label: 'Email', value: user?.email || '' },
                { label: 'Gender', value: profile?.gender || 'Not set' },
                { label: 'Date of Birth', value: profile?.dateOfBirth || 'Not set' },
                { label: 'Address', value: profile?.address || 'Not set' },
                { label: 'Profile Picture', value: profile?.profilePictureUrl || 'Not set' },
              ].map((row, i) => (
                <div
                  key={row.label}
                  className={`flex items-center justify-between gap-4 py-3.5 ${i < 5 ? 'border-b border-slate-100' : ''}`}
                >
                  <span className="text-sm font-medium text-slate-500">{row.label}</span>
                  <span
                    className={`text-sm font-semibold ${row.value === 'Not set' ? 'text-slate-400' : 'text-slate-800'}`}
                  >
                    {row.value}
                  </span>
                </div>
              ))}
              <div className="pt-4">
                <Button onClick={handleEdit}>Edit Profile</Button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-5 p-6">
              <div>
                <label className="mb-1.5 block text-sm font-semibold text-slate-700">Phone</label>
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="+1234567890"
                  className="input-field"
                  autoFocus
                />
              </div>

              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-700">Gender</label>
                  <select value={gender} onChange={(e) => setGender(e.target.value)} className="select-field">
                    <option value="">Select gender</option>
                    <option value="MALE">Male</option>
                    <option value="FEMALE">Female</option>
                    <option value="OTHER">Other</option>
                  </select>
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-700">Date of Birth</label>
                  <input
                    type="date"
                    value={dateOfBirth}
                    onChange={(e) => setDateOfBirth(e.target.value)}
                    className="input-field"
                  />
                </div>
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-semibold text-slate-700">Address</label>
                <textarea
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="Enter your address"
                  rows={3}
                  className="input-field resize-none"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-semibold text-slate-700">Profile Picture</label>
                <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3">
                  <input
                    type="file"
                    accept="image/*"
                    ref={fileInputRef}
                    onChange={handleFileChange}
                    className="hidden"
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => fileInputRef.current?.click()}
                    className="!py-2 text-xs"
                  >
                    Choose Image
                  </Button>
                  <span className="truncate text-sm text-slate-400">
                    {selectedFile ? selectedFile.name : profile?.profilePictureUrl || 'No file selected'}
                  </span>
                </div>
              </div>

              <div className="flex gap-3 pt-1">
                <Button type="submit" loading={isSaving}>
                  {isSaving ? 'Saving…' : 'Save Changes'}
                </Button>
                <Button type="button" variant="secondary" onClick={handleCancel}>
                  Cancel
                </Button>
              </div>
            </form>
          )}
        </div>

        <footer className="pt-4 text-center text-xs text-slate-400">
          CareQ — SmartOPD AI | Intelligent Patient Flow Platform
        </footer>
      </div>
    </div>
  );
}
