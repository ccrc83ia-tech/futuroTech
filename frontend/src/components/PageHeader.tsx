import { Box, Typography } from "@mui/material";
import type { ReactNode } from "react";

interface PageHeaderProps {
  /** Título principal de la página. */
  title: string;
  /** Texto secundario opcional bajo el título. */
  subtitle?: string;
  /** Acción(es) alineadas a la derecha (p. ej. un botón "Nuevo"). */
  action?: ReactNode;
}

/**
 * Encabezado de página estandarizado: título (h4), subtítulo opcional y un slot
 * de acción a la derecha. Unifica el patrón que cada página repetía a mano.
 */
export default function PageHeader({ title, subtitle, action }: PageHeaderProps) {
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: { xs: "flex-start", sm: "center" },
        justifyContent: "space-between",
        flexDirection: { xs: "column", sm: "row" },
        gap: 1.5,
        mb: 3,
      }}
    >
      <Box>
        <Typography variant="h4" component="h1">{title}</Typography>
        {subtitle && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {subtitle}
          </Typography>
        )}
      </Box>
      {action && <Box sx={{ flexShrink: 0 }}>{action}</Box>}
    </Box>
  );
}
