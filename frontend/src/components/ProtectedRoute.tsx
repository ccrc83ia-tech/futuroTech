import { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuthStore } from "../store/auth";

interface Props {
  children: ReactNode;
  permission?: string;
}

/** Protege rutas: exige autenticación y, opcionalmente, un permiso. */
export default function ProtectedRoute({ children, permission }: Props) {
  const { isAuthenticated, hasPermission } = useAuthStore();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (permission && !hasPermission(permission)) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
