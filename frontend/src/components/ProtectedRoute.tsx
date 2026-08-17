import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const KNOWN_ROLES = ['PATIENT', 'DOCTOR', 'ADMIN', 'RECEPTIONIST'] as const;

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: string[];
}

/**
 * Route guard:
 *  - still restoring session (isLoading)  → loading placeholder
 *  - not authenticated                     → /login
 *  - authenticated but role not allowed    → the user's own dashboard
 *    (unknown/missing role falls back to PATIENT so we never redirect into
 *     a dead-end loop)
 */
export default function ProtectedRoute({ children, allowedRoles }: ProtectedRouteProps) {
  const { isAuthenticated, isLoading, user } = useAuth();

  if (isLoading) {
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh',
          fontFamily: 'system-ui, sans-serif',
          color: '#4a5568',
        }}
      >
        Loading...
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && user && !allowedRoles.includes(user.role)) {
    const safeRole = KNOWN_ROLES.includes(user.role as (typeof KNOWN_ROLES)[number])
      ? user.role
      : 'PATIENT';
    const dashboardPath = `/${safeRole.toLowerCase()}/dashboard`;
    return <Navigate to={dashboardPath} replace />;
  }

  return <>{children}</>;
}
