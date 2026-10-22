import AccessTimeIcon from "@mui/icons-material/AccessTime";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import { Chip, Typography } from "@mui/material";

export function deadlineDaysLeft(deadline: string | null): number | null {
  if (!deadline) return null;
  return Math.ceil((new Date(deadline).getTime() - Date.now()) / (1000 * 60 * 60 * 24));
}

export function DeadlineChip({ deadline }: { deadline: string | null }) {
  const days = deadlineDaysLeft(deadline);
  if (days === null) return <Typography variant="body2" color="text.disabled">—</Typography>;
  if (days < 0)  return <Chip label={`Vencido hace ${Math.abs(days)}d`} color="error"   size="small" icon={<WarningAmberIcon />} />;
  if (days <= 3) return <Chip label={`${days}d`} color="error"   size="small" icon={<WarningAmberIcon />} />;
  if (days <= 7) return <Chip label={`${days}d`} color="warning" size="small" icon={<AccessTimeIcon />} />;
  return             <Chip label={`${days}d`} color="success" size="small" icon={<AccessTimeIcon />} />;
}
