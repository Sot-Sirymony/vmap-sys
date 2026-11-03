import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '../../context/AuthContext';
import { tr } from '../../i18n/translate';

export function ProtectedRoute() {
  const { isAuthenticated, sessionExpired } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    // After an expired session the login page says why, and signing in again
    // returns to the page the user was on.
    const notice = sessionExpired ? tr('Your session has expired. Please sign in again.') : undefined;
    return <Navigate to="/login" replace state={{ from: location, notice, sessionExpired }} />;
  }

  return <Outlet />;
}
