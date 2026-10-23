import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from "@mui/material";
import type { ReactNode } from "react";

export interface ConfirmDialogProps {
  /** Controla la visibilidad del diálogo. */
  open: boolean;
  /** Título del diálogo. */
  title: string;
  /** Mensaje principal (texto o nodo). */
  message: ReactNode;
  /** Texto del botón de confirmación (por defecto "Confirmar"). */
  confirmLabel?: string;
  /** Texto del botón de cancelar (por defecto "Cancelar"). */
  cancelLabel?: string;
  /** Color del botón de confirmación. "error" para acciones destructivas. */
  confirmColor?: "primary" | "success" | "error" | "warning";
  /** Aviso extra destacado (p. ej. "Esta acción no se puede deshacer"). */
  warning?: string;
  /** Deshabilita los botones mientras una operación está en curso. */
  pending?: boolean;
  /** Se llama al confirmar. */
  onConfirm: () => void;
  /** Se llama al cancelar o cerrar. */
  onClose: () => void;
}

/**
 * Diálogo de confirmación reutilizable para acciones sensibles o irreversibles
 * (aprobar, anular, eliminar). Centraliza el patrón para que todas las
 * confirmaciones de la app se vean y se comporten igual.
 */
export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = "Confirmar",
  cancelLabel = "Cancelar",
  confirmColor = "primary",
  warning,
  pending = false,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onClose={pending ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText component="div">{message}</DialogContentText>
        {warning && (
          <Alert severity="warning" sx={{ mt: 2 }}>
            {warning}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={pending}>
          {cancelLabel}
        </Button>
        <Button
          variant="contained"
          color={confirmColor}
          onClick={onConfirm}
          disabled={pending}
          autoFocus
        >
          {pending ? "Procesando…" : confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
