import MoreVertIcon from "@mui/icons-material/MoreVert";
import {
  Divider,
  IconButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Tooltip,
} from "@mui/material";
import { useState, type ReactNode } from "react";

export interface RowAction {
  /** Texto de la acción. */
  label: string;
  /** Icono a la izquierda. */
  icon?: ReactNode;
  /** Handler al seleccionar. */
  onClick: () => void;
  /** Resalta la acción (p. ej. "Aprobar"). */
  color?: "inherit" | "primary" | "success" | "error" | "warning";
  /** Deshabilita la acción. */
  disabled?: boolean;
  /** Dibuja un separador por encima de esta acción. */
  divider?: boolean;
}

interface RowActionsProps {
  actions: RowAction[];
  /** Etiqueta accesible del disparador. */
  ariaLabel?: string;
}

/**
 * Agrupa las acciones de una fila de tabla en un único menú "⋮", evitando tiras
 * de botones desordenadas. Ofrece una superficie de clic consistente en todas
 * las tablas de la aplicación.
 */
export default function RowActions({ actions, ariaLabel = "Acciones" }: RowActionsProps) {
  const [anchor, setAnchor] = useState<null | HTMLElement>(null);
  const open = Boolean(anchor);

  if (actions.length === 0) return null;

  return (
    <>
      <Tooltip title={ariaLabel}>
        <IconButton
          size="small"
          aria-label={ariaLabel}
          aria-haspopup="menu"
          aria-expanded={open}
          onClick={(e) => setAnchor(e.currentTarget)}
        >
          <MoreVertIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <Menu
        anchorEl={anchor}
        open={open}
        onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
      >
        {actions.map((a, i) => [
          a.divider && i > 0 ? <Divider key={`div-${i}`} component="li" /> : null,
          <MenuItem
            key={a.label}
            disabled={a.disabled}
            onClick={() => {
              setAnchor(null);
              a.onClick();
            }}
            sx={a.color && a.color !== "inherit" ? { color: `${a.color}.main` } : undefined}
          >
            {a.icon && (
              <ListItemIcon sx={a.color && a.color !== "inherit" ? { color: `${a.color}.main` } : undefined}>
                {a.icon}
              </ListItemIcon>
            )}
            <ListItemText>{a.label}</ListItemText>
          </MenuItem>,
        ])}
      </Menu>
    </>
  );
}
