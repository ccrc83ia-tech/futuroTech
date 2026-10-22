import DeleteIcon from "@mui/icons-material/Delete";
import GroupIcon from "@mui/icons-material/Group";
import LockResetIcon from "@mui/icons-material/LockReset";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  FormGroup,
  IconButton,
  MenuItem,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { errorMessage } from "../api/client";
import { usersApi } from "../api/endpoints";
import type { UserResponse } from "../api/types";
import { PERMISSIONS } from "../api/types";
import { useNotify } from "../components/NotificationProvider";
import { EmptyState, LoadingState } from "../components/states";

// ─── Constantes de rol ───────────────────────────────────────────────────────

const ROLES = ["admin", "abogado", "pasante", "consulta"] as const;
type Role = typeof ROLES[number];

const ROLE_COLOR: Record<Role, "error" | "primary" | "warning" | "default"> = {
  admin:    "error",
  abogado:  "primary",
  pasante:  "warning",
  consulta: "default",
};

const ROLE_LABEL: Record<Role, string> = {
  admin:    "Admin",
  abogado:  "Abogado",
  pasante:  "Pasante",
  consulta: "Solo consulta",
};

// ─── Permisos ─────────────────────────────────────────────────────────────────

const ALL_PERMS = Object.values(PERMISSIONS) as string[];

const PERM_LABEL: Record<string, string> = {
  [PERMISSIONS.GENERATE_DOCUMENT]:  "Generar documentos",
  [PERMISSIONS.VIEW_TEMPLATES]:     "Ver plantillas",
  [PERMISSIONS.MANAGE_TEMPLATES]:   "Gestionar plantillas",
  [PERMISSIONS.VIEW_ENTITIES]:      "Ver entidades",
  [PERMISSIONS.MANAGE_ENTITIES]:    "Gestionar entidades",
  [PERMISSIONS.VIEW_COMPANY]:       "Ver datos de la empresa",
  [PERMISSIONS.MANAGE_COMPANY]:     "Gestionar empresa",
  [PERMISSIONS.MANAGE_USERS]:       "Gestionar usuarios",
  [PERMISSIONS.VIEW_ALL_DOCUMENTS]: "Ver todos los documentos",
};

/** Permisos por defecto de cada rol (debe coincidir con Role.defaultPermissions() del backend). */
const ROLE_DEFAULT_PERMS: Record<Role, string[]> = {
  admin:    ALL_PERMS,
  abogado:  [
    PERMISSIONS.GENERATE_DOCUMENT,
    PERMISSIONS.VIEW_TEMPLATES,
    PERMISSIONS.VIEW_ENTITIES,
    PERMISSIONS.MANAGE_ENTITIES,
    PERMISSIONS.VIEW_COMPANY,
    PERMISSIONS.VIEW_ALL_DOCUMENTS,
  ],
  pasante:  [
    PERMISSIONS.GENERATE_DOCUMENT,
    PERMISSIONS.VIEW_TEMPLATES,
    PERMISSIONS.VIEW_ENTITIES,
  ],
  consulta: [
    PERMISSIONS.VIEW_TEMPLATES,
    PERMISSIONS.VIEW_ENTITIES,
    PERMISSIONS.VIEW_COMPANY,
  ],
};

function arraysEqual(a: string[], b: string[]) {
  return a.length === b.length && [...a].sort().join() === [...b].sort().join();
}

// ─── Tipos internos ───────────────────────────────────────────────────────────

interface EditUser {
  userId?: string;
  username: string;
  fullName: string;
  email: string;
  role: Role;
  password: string;
  active: boolean;
  /** Checkboxes de permisos activos en el formulario. */
  permissions: string[];
}

const EMPTY: EditUser = {
  username: "", fullName: "", email: "", role: "pasante",
  password: "", active: true,
  permissions: [...ROLE_DEFAULT_PERMS["pasante"]],
};

// ─── Componente ───────────────────────────────────────────────────────────────

export default function UsersPage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const { data, isLoading } = useQuery({ queryKey: ["users"], queryFn: usersApi.list });
  const [editing, setEditing] = useState<EditUser | null>(null);

  const save = useMutation({
    mutationFn: (u: EditUser) => {
      // Si los permisos coinciden con el default del rol → enviar null (limpio).
      const defaults = ROLE_DEFAULT_PERMS[u.role];
      const customPerms = arraysEqual(u.permissions, defaults) ? null : u.permissions;

      return u.userId
        ? usersApi.update(u.userId, {
            fullName: u.fullName, email: u.email, role: u.role,
            active: u.active, password: u.password,
            permissions: customPerms,
          })
        : usersApi.create({
            username: u.username, fullName: u.fullName,
            email: u.email, role: u.role, password: u.password,
            permissions: customPerms,
          });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["users"] });
      notify.success("Usuario guardado correctamente");
      setEditing(null);
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo guardar el usuario")),
  });

  const remove = useMutation({
    mutationFn: (id: string) => usersApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["users"] });
      notify.success("Usuario eliminado");
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo eliminar el usuario")),
  });

  const valid = editing
    ? editing.fullName.trim().length > 0 &&
      (editing.userId ? true : editing.username.trim().length > 0 && editing.password.length > 0)
    : false;

  function openEdit(u: UserResponse) {
    const role = (u.role as Role) ?? "pasante";
    setEditing({
      userId: u.userId,
      username: u.username,
      fullName: u.fullName,
      email: u.email,
      role,
      password: "",
      active: u.active ?? true,
      // Si la API devuelve los permisos efectivos, los usamos como estado inicial.
      permissions: u.permissions?.length ? [...u.permissions] : [...ROLE_DEFAULT_PERMS[role]],
    });
  }

  function openNew() {
    setEditing({ ...EMPTY, permissions: [...ROLE_DEFAULT_PERMS["pasante"]] });
  }

  /** Cuando cambia el rol, ofrece resetear los permisos a los del nuevo rol. */
  function changeRole(role: Role) {
    if (!editing) return;
    setEditing({ ...editing, role, permissions: [...ROLE_DEFAULT_PERMS[role]] });
  }

  function togglePerm(perm: string, checked: boolean) {
    if (!editing) return;
    const next = checked
      ? [...editing.permissions, perm]
      : editing.permissions.filter((p) => p !== perm);
    setEditing({ ...editing, permissions: next });
  }

  function resetToRoleDefaults() {
    if (!editing) return;
    setEditing({ ...editing, permissions: [...ROLE_DEFAULT_PERMS[editing.role]] });
  }

  const isCustomPerms = editing
    ? !arraysEqual(editing.permissions, ROLE_DEFAULT_PERMS[editing.role])
    : false;

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Typography variant="h4">Usuarios</Typography>
        <Button variant="contained" onClick={openNew}>Nuevo usuario</Button>
      </Box>

      {isLoading ? (
        <LoadingState label="Cargando usuarios…" />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon={<GroupIcon sx={{ fontSize: 48 }} />}
          title="Aún no hay usuarios"
          description="Cree el primer usuario para dar acceso al equipo."
          action={<Button variant="contained" onClick={openNew}>Nuevo usuario</Button>}
        />
      ) : (
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Usuario</TableCell>
              <TableCell>Nombre</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Rol</TableCell>
              <TableCell>Permisos</TableCell>
              <TableCell>Activo</TableCell>
              <TableCell align="right"></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.map((u) => {
              const role = u.role as Role;
              const defaults = ROLE_DEFAULT_PERMS[role] ?? [];
              const hasCustom = u.permissions?.length
                ? !arraysEqual(u.permissions, defaults)
                : false;
              return (
                <TableRow key={u.userId} hover onClick={() => openEdit(u)} sx={{ cursor: "pointer" }}>
                  <TableCell>{u.username}</TableCell>
                  <TableCell>{u.fullName}</TableCell>
                  <TableCell>{u.email}</TableCell>
                  <TableCell>
                    <Chip
                      label={ROLE_LABEL[role] ?? u.role}
                      color={ROLE_COLOR[role] ?? "default"}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    {hasCustom
                      ? <Chip label="Personalizados" color="warning" size="small" variant="outlined" />
                      : <Chip label="Por rol" color="default" size="small" variant="outlined" />
                    }
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={u.active ? "Activo" : "Inactivo"}
                      color={u.active ? "success" : "default"}
                      size="small"
                      variant={u.active ? "filled" : "outlined"}
                    />
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Eliminar usuario">
                      <IconButton
                        aria-label={`Eliminar ${u.username}`}
                        onClick={(ev) => { ev.stopPropagation(); remove.mutate(u.userId); }}
                      >
                        <DeleteIcon />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}

      {/* ── Diálogo crear / editar ── */}
      <Dialog open={editing !== null} onClose={() => setEditing(null)} fullWidth maxWidth="sm">
        <DialogTitle>{editing?.userId ? "Editar usuario" : "Nuevo usuario"}</DialogTitle>
        <DialogContent>
          {editing && (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 0.5, pt: 0.5 }}>

              {/* Datos básicos */}
              <TextField
                label="Usuario"
                fullWidth margin="dense"
                disabled={!!editing.userId}
                value={editing.username}
                onChange={(e) => setEditing({ ...editing, username: e.target.value })}
              />
              <TextField
                label="Nombre completo"
                fullWidth margin="dense" required
                value={editing.fullName}
                onChange={(e) => setEditing({ ...editing, fullName: e.target.value })}
              />
              <TextField
                label="Email"
                type="email" fullWidth margin="dense"
                value={editing.email}
                onChange={(e) => setEditing({ ...editing, email: e.target.value })}
              />
              <TextField
                select label="Rol"
                fullWidth margin="dense"
                value={editing.role}
                onChange={(e) => changeRole(e.target.value as Role)}
                helperText="Al cambiar el rol se restauran los permisos a los del nuevo rol"
              >
                {ROLES.map((r) => (
                  <MenuItem key={r} value={r}>
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                      <Chip label={ROLE_LABEL[r]} color={ROLE_COLOR[r]} size="small" />
                    </Box>
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label={editing.userId ? "Nueva contraseña (vacío = sin cambiar)" : "Contraseña"}
                type="password" fullWidth margin="dense"
                required={!editing.userId}
                value={editing.password}
                onChange={(e) => setEditing({ ...editing, password: e.target.value })}
              />
              {editing.userId && (
                <FormControlLabel
                  sx={{ mt: 1 }}
                  control={
                    <Switch
                      checked={editing.active}
                      onChange={(e) => setEditing({ ...editing, active: e.target.checked })}
                    />
                  }
                  label={editing.active ? "Usuario activo (puede iniciar sesión)" : "Usuario inactivo (bloqueado)"}
                />
              )}

              {/* ── Sección de permisos ── */}
              <Divider sx={{ my: 2 }} />
              <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 1 }}>
                <Typography variant="subtitle1" fontWeight="medium">
                  🔒 Permisos
                </Typography>
                <Tooltip title="Restaurar los permisos por defecto del rol seleccionado">
                  <Button
                    size="small"
                    startIcon={<LockResetIcon />}
                    onClick={resetToRoleDefaults}
                    disabled={!isCustomPerms}
                  >
                    Restaurar del rol
                  </Button>
                </Tooltip>
              </Box>

              {isCustomPerms && (
                <Alert severity="warning" sx={{ mb: 1 }}>
                  Este usuario tiene permisos personalizados que difieren de los del rol.
                </Alert>
              )}

              <FormGroup>
                {ALL_PERMS.map((perm) => (
                  <FormControlLabel
                    key={perm}
                    control={
                      <Checkbox
                        size="small"
                        checked={editing.permissions.includes(perm)}
                        onChange={(e) => togglePerm(perm, e.target.checked)}
                      />
                    }
                    label={PERM_LABEL[perm] ?? perm}
                  />
                ))}
              </FormGroup>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancelar</Button>
          <Button
            variant="contained"
            disabled={!valid || save.isPending}
            onClick={() => editing && save.mutate(editing)}
          >
            {save.isPending ? "Guardando…" : "Guardar"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
