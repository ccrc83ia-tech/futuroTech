import DeleteIcon from "@mui/icons-material/Delete";
import GroupIcon from "@mui/icons-material/Group";
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
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
import { EmptyState, LoadingState } from "../components/states";
import { useNotify } from "../components/NotificationProvider";

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
  consulta: "Consulta",
};

interface EditUser {
  userId?: string;
  username: string;
  fullName: string;
  email: string;
  role: string;
  password: string;
  active: boolean;
}

const EMPTY: EditUser = {
  username: "", fullName: "", email: "", role: "pasante", password: "", active: true,
};

export default function UsersPage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const { data, isLoading } = useQuery({ queryKey: ["users"], queryFn: usersApi.list });
  const [editing, setEditing] = useState<EditUser | null>(null);

  const save = useMutation({
    mutationFn: (u: EditUser) =>
      u.userId
        ? usersApi.update(u.userId, {
            fullName: u.fullName, email: u.email, role: u.role,
            active: u.active, password: u.password,
          })
        : usersApi.create({
            username: u.username, fullName: u.fullName,
            email: u.email, role: u.role, password: u.password,
          }),
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
    setEditing({ ...u, password: "", active: u.active ?? true });
  }

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Typography variant="h4">Usuarios</Typography>
        <Button variant="contained" onClick={() => setEditing({ ...EMPTY })}>Nuevo usuario</Button>
      </Box>

      {isLoading ? (
        <LoadingState label="Cargando usuarios…" />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon={<GroupIcon sx={{ fontSize: 48 }} />}
          title="Aún no hay usuarios"
          description="Cree el primer usuario para dar acceso al equipo."
          action={<Button variant="contained" onClick={() => setEditing({ ...EMPTY })}>Nuevo usuario</Button>}
        />
      ) : (
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Usuario</TableCell>
              <TableCell>Nombre</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Rol</TableCell>
              <TableCell>Activo</TableCell>
              <TableCell align="right"></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.map((u) => (
              <TableRow key={u.userId} hover onClick={() => openEdit({ ...u })} sx={{ cursor: "pointer" }}>
                <TableCell>{u.username}</TableCell>
                <TableCell>{u.fullName}</TableCell>
                <TableCell>{u.email}</TableCell>
                <TableCell>
                  <Chip
                    label={ROLE_LABEL[u.role as Role] ?? u.role}
                    color={ROLE_COLOR[u.role as Role] ?? "default"}
                    size="small"
                  />
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
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={editing !== null} onClose={() => setEditing(null)} fullWidth>
        <DialogTitle>{editing?.userId ? "Editar usuario" : "Nuevo usuario"}</DialogTitle>
        <DialogContent>
          {editing && (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 0.5, pt: 0.5 }}>
              <TextField
                label="Usuario"
                fullWidth
                margin="dense"
                disabled={!!editing.userId}
                value={editing.username}
                onChange={(e) => setEditing({ ...editing, username: e.target.value })}
              />
              <TextField
                label="Nombre completo"
                fullWidth
                margin="dense"
                required
                value={editing.fullName}
                onChange={(e) => setEditing({ ...editing, fullName: e.target.value })}
              />
              <TextField
                label="Email"
                type="email"
                fullWidth
                margin="dense"
                value={editing.email}
                onChange={(e) => setEditing({ ...editing, email: e.target.value })}
              />
              <TextField
                select
                label="Rol"
                fullWidth
                margin="dense"
                value={editing.role}
                onChange={(e) => setEditing({ ...editing, role: e.target.value })}
                helperText="Define los permisos del usuario en el sistema"
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
                label={editing.userId ? "Nueva contraseña (dejar vacío para no cambiar)" : "Contraseña"}
                type="password"
                fullWidth
                margin="dense"
                required={!editing.userId}
                value={editing.password}
                onChange={(e) => setEditing({ ...editing, password: e.target.value })}
              />
              {/* Solo visible al editar un usuario existente */}
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
