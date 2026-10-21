import DeleteIcon from "@mui/icons-material/Delete";
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { usersApi } from "../api/endpoints";

const ROLES = ["admin", "abogado", "pasante", "consulta"];

interface EditUser {
  userId?: string;
  username: string;
  fullName: string;
  email: string;
  role: string;
  password: string;
  active: boolean;
}

const EMPTY: EditUser = { username: "", fullName: "", email: "", role: "pasante", password: "", active: true };

export default function UsersPage() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ["users"], queryFn: usersApi.list });
  const [editing, setEditing] = useState<EditUser | null>(null);

  const save = useMutation({
    mutationFn: (u: EditUser) =>
      u.userId
        ? usersApi.update(u.userId, { fullName: u.fullName, email: u.email, role: u.role, active: u.active, password: u.password })
        : usersApi.create({ username: u.username, fullName: u.fullName, email: u.email, role: u.role, password: u.password }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["users"] }); setEditing(null); },
  });
  const remove = useMutation({
    mutationFn: (id: string) => usersApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["users"] }),
  });

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
        <Typography variant="h4">Usuarios</Typography>
        <Button variant="contained" onClick={() => setEditing({ ...EMPTY })}>Nuevo usuario</Button>
      </Box>

      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Usuario</TableCell>
            <TableCell>Nombre</TableCell>
            <TableCell>Rol</TableCell>
            <TableCell>Activo</TableCell>
            <TableCell align="right"></TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {data?.map((u) => (
            <TableRow key={u.userId} hover onClick={() => setEditing({ ...u, password: "" })} sx={{ cursor: "pointer" }}>
              <TableCell>{u.username}</TableCell>
              <TableCell>{u.fullName}</TableCell>
              <TableCell>{u.role}</TableCell>
              <TableCell>{u.active ? "Sí" : "No"}</TableCell>
              <TableCell align="right">
                <IconButton onClick={(ev) => { ev.stopPropagation(); remove.mutate(u.userId); }}>
                  <DeleteIcon />
                </IconButton>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Dialog open={editing !== null} onClose={() => setEditing(null)} fullWidth>
        <DialogTitle>{editing?.userId ? "Editar usuario" : "Nuevo usuario"}</DialogTitle>
        <DialogContent>
          {editing && (
            <>
              <TextField label="Usuario" fullWidth margin="dense" disabled={!!editing.userId}
                value={editing.username} onChange={(e) => setEditing({ ...editing, username: e.target.value })} />
              <TextField label="Nombre completo" fullWidth margin="dense"
                value={editing.fullName} onChange={(e) => setEditing({ ...editing, fullName: e.target.value })} />
              <TextField label="Email" fullWidth margin="dense"
                value={editing.email} onChange={(e) => setEditing({ ...editing, email: e.target.value })} />
              <TextField select label="Rol" fullWidth margin="dense"
                value={editing.role} onChange={(e) => setEditing({ ...editing, role: e.target.value })}>
                {ROLES.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
              </TextField>
              <TextField label={editing.userId ? "Nueva contraseña (opcional)" : "Contraseña"} type="password"
                fullWidth margin="dense"
                value={editing.password} onChange={(e) => setEditing({ ...editing, password: e.target.value })} />
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancelar</Button>
          <Button variant="contained" onClick={() => editing && save.mutate(editing)}>Guardar</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
