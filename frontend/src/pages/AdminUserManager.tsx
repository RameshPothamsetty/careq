import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, Search, Users } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useGetUsersQuery } from '../services/rtk/userApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import QueuePageHeader from '../components/QueuePageHeader';
import { AvatarInitials, Button } from '../components/ui';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';

const PAGE_SIZE = 20;

const ROLE_TINTS: Record<string, string> = {
  ADMIN: 'bg-violet-100 text-violet-700',
  DOCTOR: 'bg-sky-100 text-sky-700',
  PATIENT: 'bg-emerald-100 text-emerald-700',
};

export default function AdminUserManager() {
  const { user } = useAuth();

  const [page, setPage] = useState(0);
  const [searchInput, setSearchInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  // Debounce the search box — typing fires one query per pause, then jumps
  // back to the first page so the result set is always visible from the top.
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearchQuery(searchInput.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const {
    data: pageData,
    isLoading,
    isError,
    error,
    refetch,
  } = useGetUsersQuery({ page, size: PAGE_SIZE, search: searchQuery || undefined });

  const profiles = pageData?.content ?? [];
  const totalPages = pageData?.totalPages ?? 1;
  const totalElements = pageData?.totalElements ?? 0;

  return (
    <div className="min-h-screen bg-gray-50 px-4 py-6 sm:px-6">
      <div className="mx-auto max-w-6xl space-y-6">
        <QueuePageHeader
          icon="👥"
          title="Manage Users"
          subtitle="All registered users — search by name or email"
          dashboardPath="/admin"
        />

        {/* Search */}
        <div className="relative sm:max-w-md">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search by name or email…"
            className="input-field !pl-10"
          />
        </div>

        {isLoading ? (
          <LoadingState label="Loading users…" />
        ) : isError ? (
          <ErrorState message={getErrorMessage(error)} onRetry={refetch} />
        ) : profiles.length === 0 ? (
          <EmptyState
            icon={<Users className="h-8 w-8 text-brand-400" />}
            title={searchQuery ? 'No users match your search' : 'No users found'}
            message={
              searchQuery
                ? `Nothing found for “${searchQuery}”.`
                : 'User profiles are created the first time a user opens their profile.'
            }
          />
        ) : (
          <div className="card overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50/60 text-left">
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">User</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Email</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Role</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Phone</th>
                    <th className="px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Gender</th>
                  </tr>
                </thead>
                <tbody>
                  {profiles.map((p) => (
                    <tr key={p.id} className="border-b border-slate-50 transition-colors last:border-0 hover:bg-brand-50/40">
                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-3">
                          <AvatarInitials
                            name={p.fullName || p.userId.slice(0, 8)}
                            size="sm"
                            imgUrl={p.profilePictureUrl}
                          />
                          <div>
                            <p className="font-semibold text-slate-800">{p.fullName || '—'}</p>
                            <p className="font-mono text-[11px] text-slate-400">{p.userId.slice(0, 8)}…</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-3.5 text-slate-600">{p.email || '—'}</td>
                      <td className="px-5 py-3.5">
                        <span
                          className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                            ROLE_TINTS[p.role] ?? 'bg-gray-100 text-gray-600'
                          }`}
                        >
                          {p.role}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-slate-600">{p.phone || '—'}</td>
                      <td className="px-5 py-3.5 text-slate-600">{p.gender || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination footer */}
            <div className="flex flex-col items-center justify-between gap-3 border-t border-slate-100 px-5 py-3.5 sm:flex-row">
              <p className="text-xs text-slate-400">
                {totalElements} user{totalElements === 1 ? '' : 's'} · Page {pageData ? pageData.number + 1 : 1} of {Math.max(totalPages, 1)}
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

        <footer className="pt-4 text-center text-xs text-slate-400">
          CareQ — SmartOPD AI | Admin user directory · {user?.fullName}
        </footer>
      </div>
    </div>
  );
}
