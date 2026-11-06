import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '../../context/AuthContext';
import { Loading } from '../common/Loading';
import { tr } from '../../i18n/translate';

export function ProtectedRoute() {
  const { isAuthenticated, sessionExpired, restoring } = useAuth();
  const location = useLocation();

  // V10: the access token is renewed from the stored session as the app
  // opens; wait for it rather than sending a signed-in user to the login page.
  if (restoring) {
    return <Loading />;
  }

  if (!isAuthenticated) {
    // After an expired session the login page says why, and signing in again
    // returns to the page the user was on.
    const notice = sessionExpired ? tr('Your session has expired. Please sign in again.') : undefined;
    return <Navigate to="/login" replace state={{ from: location, notice, sessionExpired }} />;
  }

  return <Outlet />;
}
