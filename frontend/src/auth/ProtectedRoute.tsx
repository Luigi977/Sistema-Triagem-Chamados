import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './context'
export function ProtectedRoute() {
  const { tecnico } = useAuth()
  const location = useLocation()
  return tecnico ? (
    <Outlet />
  ) : (
    <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />
  )
}
