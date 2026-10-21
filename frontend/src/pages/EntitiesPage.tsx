import DeleteIcon from "@mui/icons-material/Delete";
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
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
import { entitiesApi } from "../api/endpoints";
import type { RecipientEntity } from "../api/types";
import { PERMISSIONS } from "../api/types";
import { useAuthStore } from "../store/auth";

const EMPTY: RecipientEntity = {
  entityId: "", name: "", nit: "", address: "", city: "",
  phone: "", email: "", legalRep: "", entityType: "", notes: "",
};

export default function EntitiesPage() {
  const qc = useQueryClient();
  const canManage = useAuthStore((s) => s.hasPermission(PERMISSIONS.MANAGE_ENTITIES));
  const { data } = useQuery({ queryKey: ["entities"], queryFn: entitiesApi.list });
  const [editing, setEditing] = useState<RecipientEntity | null>(null);

  const save = useMutation({
    mutationFn: (e: RecipientEntity) => entitiesApi.save(e),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["entities"] }); setEditing(null); },
  });
  const remove = useMutation({
    mutationFn: (id: string) => entitiesApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["entities"] }),
  });

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
        <Typography variant="h4">Entidades destinatarias</Typography>
        {canManage && <Button variant="contained" onClick={() => setEditing({ ...EMPTY })}>Nueva entidad</Button>}
      </Box>

      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Nombre</TableCell>
            <TableCell>Tipo</TableCell>
            <TableCell>Ciudad</TableCell>
            <TableCell align="right"></TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {data?.map((e) => (
            <TableRow key={e.entityId} hover onClick={() => canManage && setEditing(e)} sx={{ cursor: canManage ? "pointer" : "default" }}>
              <TableCell>{e.name}</TableCell>
              <TableCell>{e.entityType}</TableCell>
              <TableCell>{e.city}</TableCell>
              <TableCell align="right">
                {canManage && (
                  <IconButton onClick={(ev) => { ev.stopPropagation(); remove.mutate(e.entityId); }}>
                    <DeleteIcon />
                  </IconButton>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Dialog open={editing !== null} onClose={() => setEditing(null)} fullWidth>
        <DialogTitle>{editing?.entityId ? "Editar entidad" : "Nueva entidad"}</DialogTitle>
        <DialogContent>
          {editing && (["entityId", "name", "entityType", "nit", "city", "address", "phone", "email", "legalRep"] as const).map((f) => (
            <TextField
              key={f}
              label={f}
              fullWidth
              margin="dense"
              disabled={f === "entityId" && !!editing.entityId}
              value={editing[f]}
              onChange={(ev) => setEditing({ ...editing, [f]: ev.target.value })}
            />
          ))}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancelar</Button>
          <Button variant="contained" onClick={() => editing && save.mutate(editing)}>Guardar</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
