import { ThemeProvider } from "@mui/material/styles";
import { useQuery } from "@tanstack/react-query";
import { useMemo, type ReactNode } from "react";
import { companyApi } from "../api/endpoints";
import { PERMISSIONS } from "../api/types";
import { useAuthStore } from "../store/auth";
import { buildTheme } from "../theme";

/**
 * Envuelve la app con un tema MUI cuyo color primario proviene del branding de
 * la firma (tenant). Carga la configuración solo cuando el usuario está
 * autenticado y puede ver los datos de la empresa; mientras tanto usa el tema
 * por defecto. Así la interfaz adopta la identidad visual de cada firma.
 */
export default function BrandedThemeProvider({ children }: { children: ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const canViewCompany = useAuthStore((s) => s.hasPermission(PERMISSIONS.VIEW_COMPANY));

  const { data } = useQuery({
    queryKey: ["company"],
    queryFn: companyApi.get,
    enabled: isAuthenticated && canViewCompany,
    staleTime: 5 * 60 * 1000,
  });

  const theme = useMemo(
    () => buildTheme(data?.branding?.primaryColor),
    [data?.branding?.primaryColor],
  );

  return <ThemeProvider theme={theme}>{children}</ThemeProvider>;
}
