import BlockIcon from "@mui/icons-material/Block";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import DescriptionIcon from "@mui/icons-material/Description";
import DownloadIcon from "@mui/icons-material/Download";
import EditIcon from "@mui/icons-material/Edit";
import RateReviewIcon from "@mui/icons-material/RateReview";
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
import ConfirmDialog from "../components/ConfirmDialog";
import { DeadlineChip } from "../components/DeadlineChip";
import { useNotify } from "../components/NotificationProvider";
import PageHeader from "../components/PageHeader";
import RowActions from "../components/RowActions";
import { EmptyState, LoadingState } from "../components/states";
import { useAuthStore } from "../store/auth";

const STATUS_COLOR: Record<DocumentStatus, "warning" | "info" | "success" | "default"> = {
  DRAFT: "warning", REVIEWED: "info", APPROVED: "success", VOID: "default",
};
const STATUS_LABEL: Record<DocumentStatus, string> = {
  DRAFT: "Borrador", REVIEWED: "Revisado", APPROVED: "Aprobado", VOID: "Anulado",
};

// Transición de estado pendiente de confirmación.
type StatusChange = {
  id: string;
  clientName: string;
  next: "REVIEWED" | "APPROVED" | "VOID";
};

export default function AllDocumentsPage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const role = useAuthStore((s) => s.role);
  const canReview = role === "abogado" || role === "admin";

  const [search, setSearch]             = useState("");
  const [filterStatus, setFilterStatus] = useState("ALL");
  const [filterTemplate, setFilterTemplate] = useState("ALL");
  const [docketDialog, setDocketDialog] = useState<{ id: string; current: string } | null>(null);
  const [docketValue, setDocketValue]   = useState("");
  const [statusChange, setStatusChange] = useState<StatusChange | null>(null);

  const { data, isLoading } = useQuery({ queryKey: ["allDocs"], queryFn: documentsApi.all });
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
        || (d.authorUserId ?? "").toLowerCase().includes(q)
        || templateName(d.templateId).toLowerCase().includes(q);
      const matchStatus = filterStatus === "ALL" || d.status === filterStatus;
      const matchTpl = filterTemplate === "ALL" || d.templateId === filterTemplate;
      return matchSearch && matchStatus && matchTpl;
    });
  }, [data, search, filterStatus, filterTemplate, templateName]);

  const review = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => documentsApi.review(id, status),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ["allDocs"] });
      qc.invalidateQueries({ queryKey: ["stats"] });
      notify.success(
        vars.status === "APPROVED" ? "Documento aprobado"
          : vars.status === "REVIEWED" ? "Documento marcado como revisado"
          : vars.status === "VOID" ? "Documento anulado"
          : "Estado actualizado",
      );
      setStatusChange(null);
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo actualizar el estado")),
  });

  // Texto del diálogo de confirmación según la transición solicitada.
  const CONFIRM_COPY: Record<StatusChange["next"], { title: string; confirmLabel: string; color: "success" | "primary" | "error"; warning: string }> = {
    REVIEWED: {
      title: "Marcar como revisado",
      confirmLabel: "Marcar revisado",
      color: "primary",
      warning: "Indica que leyó el documento. Aún deberá aprobarlo para darlo por definitivo.",
    },
    APPROVED: {
      title: "Aprobar documento",
      confirmLabel: "Aprobar",
      color: "success",
      warning: "La aprobación queda registrada a su nombre y no se puede revertir.",
    },
    VOID: {
      title: "Anular documento",
      confirmLabel: "Anular",
      color: "error",
      warning: "El documento quedará marcado como anulado y no debería enviarse. Esta acción no se puede revertir.",
    },
  };

  const saveDocket = useMutation({
    mutationFn: ({ id, docket }: { id: string; docket: string }) => documentsApi.updateDocket(id, docket),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["allDocs"] });
      qc.invalidateQueries({ queryKey: ["stats"] });
      notify.success("Radicado guardado");
      setDocketDialog(null);
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo guardar el radicado")),
  });

  return (
    <Box>
      <PageHeader
        title="Todos los documentos"
        subtitle="Documentos generados por todo el equipo."
        action={
          <Typography variant="body2" color="text.secondary">
            {filtered?.length ?? 0} resultado{filtered?.length !== 1 ? "s" : ""}
            {data && filtered.length !== data.length ? ` de ${data.length}` : ""}
          </Typography>
        }
      />


      {/* Búsqueda y filtros */}
      <Box sx={{ display: "flex", gap: 1.5, mb: 2, flexWrap: "wrap" }}>
        <TextField
          size="small" placeholder="Buscar por cliente, radicado, autor o tipo…"
          value={search} onChange={(e) => setSearch(e.target.value)}
          sx={{ flex: 1, minWidth: 240 }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
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
          title={data?.length === 0 ? "No hay documentos generados" : "Sin resultados"}
          description={data?.length === 0
            ? "Los documentos generados por el equipo aparecerán aquí."
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
              <TableCell>Autor</TableCell>
              <TableCell>Fecha</TableCell>
              <TableCell>Estado</TableCell>
              <TableCell>Vence</TableCell>
              <TableCell>Revisado por</TableCell>
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
                <TableCell sx={{ maxWidth: 120 }}>
                  <Typography variant="body2" noWrap>{d.clientName ?? "—"}</Typography>
                </TableCell>
                <TableCell sx={{ maxWidth: 140 }}>
                  <Tooltip title={d.entityName ?? "—"}>
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
                <TableCell>
                  <Typography variant="body2" noWrap>{d.authorUserId ?? "—"}</Typography>
                </TableCell>
                <TableCell sx={{ whiteSpace: "nowrap" }}>
                  {new Date(d.createdAt).toLocaleDateString("es-CO")}
                </TableCell>
                <TableCell>
                  <Chip label={STATUS_LABEL[d.status]} color={STATUS_COLOR[d.status]} size="small" />
                </TableCell>
                <TableCell><DeadlineChip deadline={d.deadline} /></TableCell>
                <TableCell sx={{ maxWidth: 120 }}>
                  {d.reviewedBy ? (
                    <Tooltip title={d.reviewedAt ? new Date(d.reviewedAt).toLocaleString("es-CO") : d.reviewedBy}>
                      <Typography variant="body2" noWrap>{d.reviewedBy}</Typography>
                    </Tooltip>
                  ) : (
                    <Typography variant="body2" color="text.disabled">—</Typography>
                  )}
                </TableCell>
                <TableCell align="right">
                  <RowActions
                    actions={[
                      {
                        label: "Descargar Word",
                        icon: <DownloadIcon fontSize="small" />,
                        onClick: () => documentsApi.download(d.transactionId, d.fileName, "docx"),
                      },
                      {
                        label: "Descargar PDF",
                        icon: <DownloadIcon fontSize="small" />,
                        onClick: () => documentsApi.download(d.transactionId, undefined, "pdf"),
                      },
                      ...(canReview && d.status === "DRAFT"
                        ? [{
                            label: "Marcar revisado",
                            icon: <RateReviewIcon fontSize="small" />,
                            color: "primary" as const,
                            onClick: () => setStatusChange({ id: d.transactionId, clientName: d.clientName ?? "este documento", next: "REVIEWED" }),
                            divider: true,
                          }]
                        : []),
                      ...(canReview && (d.status === "DRAFT" || d.status === "REVIEWED")
                        ? [{
                            label: "Aprobar",
                            icon: <CheckCircleIcon fontSize="small" />,
                            color: "success" as const,
                            onClick: () => setStatusChange({ id: d.transactionId, clientName: d.clientName ?? "este documento", next: "APPROVED" }),
                            divider: d.status === "REVIEWED",
                          }]
                        : []),
                      ...(canReview && (d.status === "DRAFT" || d.status === "REVIEWED")
                        ? [{
                            label: "Anular",
                            icon: <BlockIcon fontSize="small" />,
                            color: "error" as const,
                            onClick: () => setStatusChange({ id: d.transactionId, clientName: d.clientName ?? "este documento", next: "VOID" }),
                          }]
                        : []),
                    ]}
                  />
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

      {/* Confirmación de cambio de estado (revisar / aprobar / anular) */}
      <ConfirmDialog
        open={!!statusChange}
        title={statusChange ? CONFIRM_COPY[statusChange.next].title : ""}
        message={
          statusChange ? (
            <>
              ¿Confirma esta acción sobre el documento de <strong>{statusChange.clientName}</strong>?
            </>
          ) : ""
        }
        confirmLabel={statusChange ? CONFIRM_COPY[statusChange.next].confirmLabel : "Confirmar"}
        confirmColor={statusChange ? CONFIRM_COPY[statusChange.next].color : "primary"}
        warning={statusChange ? CONFIRM_COPY[statusChange.next].warning : undefined}
        pending={review.isPending}
        onClose={() => setStatusChange(null)}
        onConfirm={() => statusChange && review.mutate({ id: statusChange.id, status: statusChange.next })}
      />
    </Box>
  );
}
