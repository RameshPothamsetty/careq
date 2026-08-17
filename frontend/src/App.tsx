import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { NotificationProvider } from './context/NotificationContext';
import { ThemeProvider } from './context/ThemeContext';
import ToastHost from './components/ToastHost';
import ProtectedRoute from './components/ProtectedRoute';
import { useNotificationSocket } from './hooks/useNotificationSocket';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import VerifyEmailPage from './pages/VerifyEmailPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import PatientDashboard from './pages/PatientDashboard';
import DoctorDashboard from './pages/DoctorDashboard';
import AdminDashboard from './pages/AdminDashboard';
import ProfilePage from './pages/ProfilePage';
import PatientDoctorBrowser from './pages/PatientDoctorBrowser';
import PatientQueuePage from './pages/PatientQueuePage';
import ChatPage from './pages/ChatPage';
import DoctorQueuePage from './pages/DoctorQueuePage';
import AdminQueueOverview from './pages/AdminQueueOverview';
import AdminAnalytics from './pages/AdminAnalytics';
import AdminDepartmentManager from './pages/AdminDepartmentManager';
import AdminDoctorManager from './pages/AdminDoctorManager';
import AdminUserManager from './pages/AdminUserManager';
import ReceptionistDashboard from './pages/ReceptionistDashboard';

/**
 * Null-rendering bridge that keeps the real-time notification socket alive
 * for the whole authenticated session (inside Auth + Notification providers).
 */
function NotificationSocketBridge() {
  useNotificationSocket();
  return null;
}

function App() {
  return (
    <AuthProvider>
      <NotificationProvider>
        <ThemeProvider>
        <div className="app">
          <Routes>
          {/* Public routes */}
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          {/* Email link targets (public, no session needed) */}
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />

          {/* Profile — accessible to all authenticated users */}
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <ProfilePage />
              </ProtectedRoute>
            }
          />

          {/* Protected role-based routes */}
          <Route
            path="/patient/*"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/patient/doctors"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientDoctorBrowser />
              </ProtectedRoute>
            }
          />
          <Route
            path="/patient/queue"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <PatientQueuePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/patient/chat"
            element={
              <ProtectedRoute allowedRoles={['PATIENT']}>
                <ChatPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/doctor/queue"
            element={
              <ProtectedRoute allowedRoles={['DOCTOR']}>
                <DoctorQueuePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/queue"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AdminQueueOverview />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/analytics"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AdminAnalytics />
              </ProtectedRoute>
            }
          />
          <Route
            path="/doctor/*"
            element={
              <ProtectedRoute allowedRoles={['DOCTOR']}>
                <DoctorDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/*"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AdminDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/receptionist/*"
            element={
              <ProtectedRoute allowedRoles={['RECEPTIONIST']}>
                <ReceptionistDashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/departments"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AdminDepartmentManager />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/doctors"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AdminDoctorManager />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/users"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AdminUserManager />
              </ProtectedRoute>
            }
          />

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </div>
        <ToastHost />
        <NotificationSocketBridge />
        </ThemeProvider>
      </NotificationProvider>
    </AuthProvider>
  );
}

export default App;
