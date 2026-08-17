import AdminQueueOverview from './AdminQueueOverview';

/**
 * Receptionist front-desk dashboard. The receptionist's core job is managing
 * the floor, so the dashboard IS the hospital-wide live queue overview
 * (patients waiting, delays, doctor load) with the back link pointing at the
 * receptionist landing page. Queue actions (call-next / complete / cancel)
 * are available to the RECEPTIONIST role on the queue endpoints.
 */
export default function ReceptionistDashboard() {
  return <AdminQueueOverview dashboardPath="/receptionist" />;
}
