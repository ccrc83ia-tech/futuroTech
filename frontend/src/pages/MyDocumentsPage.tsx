import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import DescriptionIcon from "@mui/icons-material/Description";
import DownloadIcon from "@mui/icons-material/Download";
import EditIcon from "@mui/icons-material/Edit";
import SearchIcon from "@mui/icons-material/Search";
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  InputAdornment,
  MenuItem,
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
import { useMemo, useState } from "react";
import { errorMessage } from "../api/client";
import { documentsApi, templatesApi } from "../api/endpoints";
import type { DocumentStatus } from "../api/types";
import { DeadlineChip } from "../components/DeadlineChip";
import { useNotify } from "../components/NotificationProvider";
import { EmptyState, LoadingState } from "../components/states";
import { useAuthStore } from "../store/auth";

const STATUS_COLOR: Record<DocumentStatus, "warning" | "info" | "success" | "default"> = {
  DRAFT: "warning", REVIEWED: "info", APPROVED: "success", VOID: "default",
};
const STATUS_LABEL: Record<DocumentStatus, string> = {
  DRAFT: "Borrador", REVIEWED: "Revisado", APPROVED: "Aprobado", VOID: "Anulado",
};

export default function MyDocumentsPage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const role = useAuthStore((s) => s.role);
  const canReview = role === "abogado" || role === "admin";

  const [search, setSearch]         = useState("");
  const [filterStatus, setFilterStatus] = useState("ALL");
  const [filterTemplate, setFilterTemplate] = useState("ALL");
  const [docketDialog, setDocketDialog] = useState<{ id: string; current: string } | null>(null);
  const [docketValue, setDocketValue] = useState("");

  const { data, isLoading } = useQuery({ queryKey: ["myDocs"], queryFn: documentsApi.mine });
  const templatesQuery = useQuery({ queryKey: ["templates"], queryFn: templatesApi.list });

  const templateName = useMemo(() => {
    const map = new Map<string, string>();
    (templatesQuery.data ?? []).forEach((t) => map.set(t.templateId, t.name));
    return (id: string) => map.get(id) ?? id;
  }, [templatesQuery.data]);

  const templateOptions = useMemo(() =>
    [...new Set((data ?? []).map((d) => d.templateId))], [data]);

  const filtered = useMemo(() => {
    const q = search.toLowerCase();
    return (data ?? []).filter((d) => {
      const matchSearch = !q
        || (d.clientName ?? "").toLowerCase().includes(q)
        || (d.internalDocket ?? "").toLowerCase().includes(q)
        || templateName(d.templateId).toLowerCase().includes(q);
      const matchStatus = filterStatus === "ALL" || d.status === filterStatus;
      const matchTpl = filterTemplate === "ALL" || d.templateId === filterTemplate;
      return matchSearch && matchStatus && matchTpl;
    });
  }, [data, search, filterStatus, filterTemplate, templateName]);

  const review = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => documentsApi.review(id, status),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["myDocs"] }); notify.success("Documento aprobado"); },
    onError: (e) => notify.error(errorMessage(e, "No se pudo actualizar el estado")),
  });

  const saveDocket = useMutation({
    mutationFn: ({ id, docket }: { id: string; docket: string }) => documentsApi.updateDocket(id, docket),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["myDocs"] });
      notify.success("Radicado guardado");
      setDocketDialog(null);
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo guardar el radicado")),
  });

  return (
    <Box>
      <Typography variant="h4" gutterBottom>Mis documentos</Typography>

      {/* Barra de búsqueda y filtros */}
      <Box sx={{ display: "flex", gap: 1.5, mb: 2, flexWrap: "wrap" }}>
        <TextField
          size="small" placeholder="Buscar por cliente, radicado o tipo…"
          value={search} onChange={(e) => setSearch(e.target.value)}
          sx={{ flex: 1, minWidth: 220 }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
        />
        <TextField select size="small" label="Estado" value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)} sx={{ minWidth: 130 }}>
          <MenuItem value="ALL">Todos</MenuItem>
          <MenuItem value="DRAFT">Borrador</MenuItem>
          <MenuItem value="REVIEWED">Revisado</MenuItem>
          <MenuItem value="APPROVED">Aprobado</MenuItem>
          <MenuItem value="VOID">Anulado</MenuItem>
        </TextField>
        <TextField select size="small" label="Tipo de documento" value={filterTemplate}
          onChange={(e) => setFilterTemplate(e.target.value)} sx={{ minWidth: 180 }}>
          <MenuItem value="ALL">Todos los tipos</MenuItem>
          {templateOptions.map((id) => (
            <MenuItem key={id} value={id}>{templateName(id)}</MenuItem>
          ))}
        </TextField>
        {(search || filterStatus !== "ALL" || filterTemplate !== "ALL") && (
          <Button size="small" onClick={() => { setSearch(""); setFilterStatus("ALL"); setFilterTemplate("ALL"); }}>
            Limpiar
          </Button>
        )}
      </Box>

      {isLoading ? (
        <LoadingState label="Cargando documentos…" />
      ) : !filtered || filtered.length === 0 ? (
        <EmptyState
          icon={<DescriptionIcon sx={{ fontSize: 48 }} />}
          title={data?.length === 0 ? "Aún no ha generado documentos" : "Sin resultados"}
          description={data?.length === 0
            ? "Los documentos que genere aparecerán aquí."
            : "Pruebe con otros filtros de búsqueda."}
        />
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Tipo</TableCell>
              <TableCell>Cliente</TableCell>
              <TableCell>Dirigido a</TableCell>
              <TableCell>Radicado</TableCell>
              <TableCell>Fecha</TableCell>
              <TableCell>Estado</TableCell>
              <TableCell>Vence</TableCell>
              <TableCell align="right">Acciones</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filtered.map((d) => (
              <TableRow key={d.transactionId} hover>
                <TableCell sx={{ maxWidth: 140 }}>
                  <Tooltip title={templateName(d.templateId)}>
                    <Typography variant="body2" noWrap>{templateName(d.templateId)}</Typography>
                  </Tooltip>
                </TableCell>
                <TableCell sx={{ maxWidth: 130 }}>
                  <Typography variant="body2" noWrap>{d.clientName ?? "—"}</Typography>
                </TableCell>
                <TableCell sx={{ maxWidth: 140 }}>
                  <Tooltip title={d.entityName ?? d.entityId ?? "—"}>
                    <Typography variant="body2" noWrap>{d.entityName ?? "—"}</Typography>
                  </Tooltip>
                </TableCell>
                {/* Radicado inline */}
                <TableCell>
                  <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                    <Typography variant="body2" color={d.internalDocket ? "text.primary" : "text.disabled"}>
                      {d.internalDocket ?? "Sin radicar"}
                    </Typography>
                    <Tooltip title="Registrar radicado">
                      <IconButton size="small" onClick={() => {
                        setDocketDialog({ id: d.transactionId, current: d.internalDocket ?? "" });
                        setDocketValue(d.internalDocket ?? "");
                      }}>
                        <EditIcon sx={{ fontSize: 14 }} />
                      </IconButton>
                    </Tooltip>
                  </Box>
                </TableCell>
                <TableCell sx={{ whiteSpace: "nowrap" }}>
                  {new Date(d.createdAt).toLocaleDateString("es-CO")}
                </TableCell>
                <TableCell>
                  <Chip label={STATUS_LABEL[d.status]} color={STATUS_COLOR[d.status]} size="small" />
                </TableCell>
                <TableCell><DeadlineChip deadline={d.deadline} /></TableCell>
                <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                  <Button size="small" startIcon={<DownloadIcon />}
                    onClick={() => documentsApi.download(d.transactionId, d.fileName, "docx")}>
                    Word
                  </Button>
                  <Button size="small" startIcon={<DownloadIcon />}
                    onClick={() => documentsApi.download(d.transactionId, undefined, "pdf")}>
                    PDF
                  </Button>
                  {canReview && d.status === "DRAFT" && (
                    <Button size="small" color="success" startIcon={<CheckCircleIcon />}
                      disabled={review.isPending}
                      onClick={() => review.mutate({ id: d.transactionId, status: "APPROVED" })}>
                      Aprobar
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {/* Dialog radicado */}
      <Dialog open={!!docketDialog} onClose={() => setDocketDialog(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Registrar radicado</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus fullWidth margin="dense"
            label="Número de radicado"
            placeholder="Ej: 2025-123456"
            value={docketValue}
            onChange={(e) => setDocketValue(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && docketDialog && saveDocket.mutate({ id: docketDialog.id, docket: docketValue })}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDocketDialog(null)}>Cancelar</Button>
          <Button variant="contained" disabled={!docketValue.trim() || saveDocket.isPending}
            onClick={() => docketDialog && saveDocket.mutate({ id: docketDialog.id, docket: docketValue })}>
            {saveDocket.isPending ? "Guardando…" : "Guardar"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
