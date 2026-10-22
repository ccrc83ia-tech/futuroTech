import ContactsIcon from "@mui/icons-material/Contacts";
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
  Tooltip,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { errorMessage } from "../api/client";
import { entitiesApi } from "../api/endpoints";
import type { RecipientEntity } from "../api/types";
import { PERMISSIONS } from "../api/types";
import { EmptyState, LoadingState } from "../components/states";
import { useNotify } from "../components/NotificationProvider";
import { useAuthStore } from "../store/auth";

const EMPTY: RecipientEntity = {
  entityId: "", name: "", nit: "", address: "", city: "",
  phone: "", email: "", legalRep: "", entityType: "", notes: "",
};

const FIELD_LABELS: Record<string, string> = {
  entityId: "ID",
  name: "Nombre",
  entityType: "Tipo",
  nit: "NIT",
  city: "Ciudad",
  address: "Dirección",
  phone: "Teléfono",
  email: "Email",
  legalRep: "Representante legal",
};

export default function EntitiesPage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const canManage = useAuthStore((s) => s.hasPermission(PERMISSIONS.MANAGE_ENTITIES));
  const { data, isLoading } = useQuery({ queryKey: ["entities"], queryFn: entitiesApi.list });
  const [editing, setEditing] = useState<RecipientEntity | null>(null);

  const save = useMutation({
    mutationFn: (e: RecipientEntity) => entitiesApi.save(e),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["entities"] });
      notify.success("Entidad guardada correctamente");
      setEditing(null);
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo guardar la entidad")),
  });
  const remove = useMutation({
    mutationFn: (id: string) => entitiesApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["entities"] });
      notify.success("Entidad eliminada");
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo eliminar la entidad")),
  });

  const valid = editing ? editing.name.trim().length > 0 : false;

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Typography variant="h4">Entidades destinatarias</Typography>
        {canManage && <Button variant="contained" onClick={() => setEditing({ ...EMPTY })}>Nueva entidad</Button>}
      </Box>

      {isLoading ? (
        <LoadingState label="Cargando entidades…" />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon={<ContactsIcon sx={{ fontSize: 48 }} />}
          title="Aún no hay entidades"
          description="Registre las entidades a las que se dirigen los documentos (juzgados, EPS, empresas, etc.)."
          action={canManage ? <Button variant="contained" onClick={() => setEditing({ ...EMPTY })}>Nueva entidad</Button> : undefined}
        />
      ) : (
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
            {data.map((e) => (
              <TableRow key={e.entityId} hover onClick={() => canManage && setEditing(e)} sx={{ cursor: canManage ? "pointer" : "default" }}>
                <TableCell>{e.name}</TableCell>
                <TableCell>{e.entityType}</TableCell>
                <TableCell>{e.city}</TableCell>
                <TableCell align="right">
                  {canManage && (
                    <Tooltip title="Eliminar entidad">
                      <IconButton
                        aria-label={`Eliminar ${e.name}`}
                        onClick={(ev) => { ev.stopPropagation(); remove.mutate(e.entityId); }}
                      >
                        <DeleteIcon />
                      </IconButton>
                    </Tooltip>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={editing !== null} onClose={() => setEditing(null)} fullWidth>
        <DialogTitle>{editing?.entityId ? "Editar entidad" : "Nueva entidad"}</DialogTitle>
        <DialogContent>
          {editing && (["entityId", "name", "entityType", "nit", "city", "address", "phone", "email", "legalRep"] as const).map((f) => (
            <TextField
              key={f}
              label={FIELD_LABELS[f] ?? f}
              fullWidth
              margin="dense"
              required={f === "name"}
              disabled={f === "entityId" && !!editing.entityId}
              value={editing[f]}
              onChange={(ev) => setEditing({ ...editing, [f]: ev.target.value })}
            />
          ))}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancelar</Button>
          <Button variant="contained" disabled={!valid || save.isPending}
            onClick={() => editing && save.mutate(editing)}>
            {save.isPending ? "Guardando…" : "Guardar"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
