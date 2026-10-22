import InboxIcon from "@mui/icons-material/Inbox";
import { Box, CircularProgress, Paper, Typography } from "@mui/material";
import { ReactNode } from "react";

/** Indicador de carga centrado, para usar mientras se resuelven queries. */
export function LoadingState({ label = "Cargando…" }: { label?: string }) {
  return (
    <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2, py: 6 }}>
      <CircularProgress />
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    </Box>
  );
}

/** Estado vacío consistente para listas y tablas sin datos. */
export function EmptyState({
  title,
  description,
  icon,
  action,
}: {
  title: string;
  description?: string;
  icon?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <Paper
      variant="outlined"
      sx={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        textAlign: "center",
        gap: 1,
        py: 6,
        px: 3,
        bgcolor: "transparent",
      }}
    >
      <Box sx={{ color: "text.disabled", mb: 1 }}>
        {icon ?? <InboxIcon sx={{ fontSize: 48 }} />}
      </Box>
      <Typography variant="h6" color="text.secondary">
        {title}
      </Typography>
      {description && (
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 420 }}>
          {description}
        </Typography>
      )}
      {action && <Box sx={{ mt: 2 }}>{action}</Box>}
    </Paper>
  );
}
