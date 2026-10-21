import { Navigate, Route, Routes } from "react-router-dom";
import AppLayout from "./components/AppLayout";
import ProtectedRoute from "./components/ProtectedRoute";
import { PERMISSIONS } from "./api/types";
import CompanyPage from "./pages/CompanyPage";
import EntitiesPage from "./pages/EntitiesPage";
import GenerateWizard from "./pages/GenerateWizard";
import LoginPage from "./pages/LoginPage";
import MyDocumentsPage from "./pages/MyDocumentsPage";
import TemplatesPage from "./pages/TemplatesPage";
import UsersPage from "./pages/UsersPage";

function Protected({ permission, element }: { permission?: string; element: React.ReactNode }) {
  return (
    <ProtectedRoute permission={permission}>
      <AppLayout>{element}</AppLayout>
    </ProtectedRoute>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/generar" element={<Protected permission={PERMISSIONS.GENERATE_DOCUMENT} element={<GenerateWizard />} />} />
      <Route path="/documentos" element={<Protected permission={PERMISSIONS.GENERATE_DOCUMENT} element={<MyDocumentsPage />} />} />
      <Route path="/plantillas" element={<Protected permission={PERMISSIONS.VIEW_TEMPLATES} element={<TemplatesPage />} />} />
      <Route path="/entidades" element={<Protected permission={PERMISSIONS.VIEW_ENTITIES} element={<EntitiesPage />} />} />
      <Route path="/empresa" element={<Protected permission={PERMISSIONS.VIEW_COMPANY} element={<CompanyPage />} />} />
      <Route path="/usuarios" element={<Protected permission={PERMISSIONS.MANAGE_USERS} element={<UsersPage />} />} />
      <Route path="/" element={<Navigate to="/generar" replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
