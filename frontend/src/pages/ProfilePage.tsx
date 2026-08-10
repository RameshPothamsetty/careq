import { useState, useRef, type FormEvent } from 'react';
import { useAuth } from '../context/AuthContext';
import {
  useGetProfileQuery,
  useUpdateProfileMutation,
  useUploadProfilePictureMutation,
} from '../services/rtk/userApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import type { UpdateProfilePayload, UserProfileResponse } from '../services/api';
import { useI18n } from '../i18n';
import QueuePageHeader from '../components/QueuePageHeader';
import AvatarInitials from '../components/ui/AvatarInitials';
import Button from '../components/ui/Button';
import { LoadingState, ErrorState } from '../components/ui/States';

export default function ProfilePage() {
  const { user } = useAuth();
  const { t } = useI18n();
  // Dark for staff, light for patients — profile is shared by all roles.
  const isStaff = user?.role === 'DOCTOR' || user?.role === 'ADMIN';
  const rootClass = `${isStaff ? 'dark' : ''} min-h-screen bg-mesh-light px-4 py-6 sm:px-6`;

  const {
    data: profile,
    isLoading,
    isError,
    error: queryError,
    refetch,
  } = useGetProfileQuery(undefined);
  const [updateProfile, { isLoading: isUpdating }] = useUpdateProfileMutation();
  const [uploadProfilePicture, { isLoading: isUploading }] =
    useUploadProfilePictureMutation();

  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [gender, setGender] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  const fillForm = (data: UserProfileResponse) => {
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

    try {
      // Picture upload first — the returned profile carries the new URL.
      if (selectedFile) {
        await uploadProfilePicture(selectedFile).unwrap();
      }

      const payload: UpdateProfilePayload = {};
      if (phone) payload.phone = phone;
      if (address) payload.address = address;
      if (dateOfBirth) payload.dateOfBirth = dateOfBirth;
      if (gender) payload.gender = gender;

      if (Object.keys(payload).length > 0) {
        await updateProfile(payload).unwrap();
      }

      // Both mutations invalidate the 'Profile' tag — the query refetches
      // automatically and the view updates with the fresh data.
      setSuccess(t('profile.updated'));
      setSelectedFile(null);
      setPreviewUrl(null);
      setIsEditing(false);
    } catch (err: unknown) {
      setError(getErrorMessage(err));
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
      <div className={rootClass}>
        <div className="mx-auto max-w-4xl">
          <LoadingState label={t('profile.loading')} />
        </div>
      </div>
    );
  }

  if (isError && !profile) {
    return (
      <div className={rootClass}>
        <div className="mx-auto max-w-4xl">
          <ErrorState message={getErrorMessage(queryError)} onRetry={refetch} />
        </div>
      </div>
    );
  }

  const displayUrl = previewUrl || profile?.profilePictureUrl;
  const isSaving = isUpdating || isUploading;
  const rolePath = user?.role.toLowerCase() || 'patient';

  const viewRows = [
    { label: t('profile.phone'), value: profile?.phone || t('common.notSet') },
    { label: t('auth.email'), value: user?.email || '' },
    { label: t('profile.gender'), value: profile?.gender || t('common.notSet') },
    { label: t('profile.dateOfBirth'), value: profile?.dateOfBirth || t('common.notSet') },
    { label: t('profile.address'), value: profile?.address || t('common.notSet') },
    { label: t('profile.profilePicture'), value: profile?.profilePictureUrl || t('common.notSet') },
  ];

  return (
    <div className={rootClass}>
      <div className="mx-auto max-w-4xl space-y-6">
        <QueuePageHeader
          icon="👤"
          title={t('profile.title')}
          subtitle={t('profile.subtitle', { name: user?.fullName || 'User' })}
          dashboardPath={`/${rolePath}`}
          showDashboard={false}
          backTo={`/${rolePath}`}
        />

        {isError && (
          <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
            ⚠ {t('profile.staleWarning', { error: getErrorMessage(queryError) })}
          </div>
        )}
        {error && !isError && (
          <div className="flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
            ⚠ {error}
          </div>
        )}
        {success && (
          <div className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300">
            ✓ {success}
          </div>
        )}

        <div className="card overflow-hidden">
          {/* Avatar header */}
          <div className="flex flex-col items-center gap-4 border-b border-slate-100 bg-gradient-to-br from-brand-50 to-sky-50 px-6 py-8 dark:border-slate-700/50 dark:from-brand-500/15 dark:to-sky-500/15 sm:flex-row">
            <AvatarInitials name={user?.fullName || 'U'} size="lg" imgUrl={displayUrl} />
            <div className="text-center sm:text-left">
              <h2 className="font-display text-xl font-bold text-slate-800 dark:text-slate-100">{user?.fullName}</h2>
              <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">{user?.email}</p>
              <span className="mt-2 inline-flex items-center rounded-full border border-brand-200 bg-brand-100 px-3 py-0.5 text-xs font-semibold text-brand-700 dark:border-brand-500/25 dark:bg-brand-500/15 dark:text-brand-300">
                {user?.role}
              </span>
            </div>
          </div>

          {/* Body */}
          {!isEditing ? (
            <div className="space-y-0 p-6">
              {viewRows.map((row, i) => (
                <div
                  key={row.label}
                  className={`flex items-center justify-between gap-4 py-3.5 ${i < viewRows.length - 1 ? 'border-b border-slate-100 dark:border-slate-700/40' : ''}`}
                >
                  <span className="text-sm font-medium text-slate-500 dark:text-slate-400">{row.label}</span>
                  <span
                    className={`text-sm font-semibold ${row.value === t('common.notSet') ? 'text-slate-400 dark:text-slate-500' : 'text-slate-800 dark:text-slate-100'}`}
                  >
                    {row.value}
                  </span>
                </div>
              ))}
              <div className="pt-4">
                <Button onClick={handleEdit}>{t('common.editProfile')}</Button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-5 p-6">
              <div>
                <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{t('profile.phone')}</label>
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
                  <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{t('profile.gender')}</label>
                  <select value={gender} onChange={(e) => setGender(e.target.value)} className="select-field">
                    <option value="">{t('profile.selectGender')}</option>
                    <option value="MALE">{t('profile.male')}</option>
                    <option value="FEMALE">{t('profile.female')}</option>
                    <option value="OTHER">{t('profile.other')}</option>
                  </select>
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{t('profile.dateOfBirth')}</label>
                  <input
                    type="date"
                    value={dateOfBirth}
                    onChange={(e) => setDateOfBirth(e.target.value)}
                    className="input-field"
                  />
                </div>
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{t('profile.address')}</label>
                <textarea
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder={t('profile.enterAddress')}
                  rows={3}
                  className="input-field resize-none"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-semibold text-slate-700 dark:text-slate-300">{t('profile.profilePicture')}</label>
                <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 dark:border-slate-600 dark:bg-night-800/60">
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
                    {t('auth.chooseImage')}
                  </Button>
                  <span className="truncate text-sm text-slate-400 dark:text-slate-500">
                    {selectedFile ? selectedFile.name : profile?.profilePictureUrl || t('profile.noFileSelected')}
                  </span>
                </div>
              </div>

              <div className="flex gap-3 pt-1">
                <Button type="submit" loading={isSaving}>
                  {isSaving ? t('common.saving') : t('common.saveChanges')}
                </Button>
                <Button type="button" variant="secondary" onClick={handleCancel}>
                  {t('common.cancel')}
                </Button>
              </div>
            </form>
          )}
        </div>

        <footer className="pt-4 text-center text-xs text-slate-400 dark:text-slate-600">
          {t('patient.footer')}
        </footer>
      </div>
    </div>
  );
}
