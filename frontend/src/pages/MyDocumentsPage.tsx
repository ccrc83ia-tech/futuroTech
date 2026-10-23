import BlockIcon from "@mui/icons-material/Block";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import DescriptionIcon from "@mui/icons-material/Description";
import DownloadIcon from "@mui/icons-material/Download";
import EditIcon from "@mui/icons-material/Edit";
import SearchIcon from "@mui/icons-material/Search";
import VisibilityIcon from "@mui/icons-material/Visibility";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
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
import { useEffect, useMemo, useState } from "react";
import { errorMessage } from "../api/client";
import { documentsApi, templatesApi } from "../api/endpoints";
import type { DocumentStatus } from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog";
import { DeadlineChip } from "../components/DeadlineChip";
import DocumentEditor from "../components/DocumentEditor";
import { useNotify } from "../components/NotificationProvider";
import PageHeader from "../components/PageHeader";
import RowActions from "../components/RowActions";
import { EmptyState, LoadingState } from "../components/states";
import { useAuthStore } from "../store/auth";
import { htmlToPlainText, markdownToHtml } from "../utils/markdownToHtml";

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
  next: "APPROVED" | "VOID";
};

const CONFIRM_COPY: Record<StatusChange["next"], { title: string; confirmLabel: string; color: "success" | "error"; warning: string }> = {
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

  const [statusChange, setStatusChange] = useState<StatusChange | null>(null);

  // Visor / editor de contenido del documento (HTML para el editor enriquecido)
  const [viewDoc, setViewDoc] = useState<{ id: string; title: string; isDraft: boolean } | null>(null);
  const [editMode, setEditMode] = useState(false);
  const [docHtml, setDocHtml] = useState("");
  // Confirmación de descartar cambios sin guardar del editor.
  const [confirmDiscard, setConfirmDiscard] = useState(false);

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
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ["myDocs"] });
      notify.success(vars.status === "APPROVED" ? "Documento aprobado" : "Documento anulado");
      setStatusChange(null);
    },
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

  // Carga el texto del documento cuando se abre el visor.
  const contentQuery = useQuery({
    queryKey: ["docText", viewDoc?.id],
    queryFn: () => documentsApi.text(viewDoc!.id),
    enabled: !!viewDoc,
  });

  // El endpoint /text antepone una línea de aviso "⚠️ BORRADOR — …" para los
  // borradores. Se quita para el editor para no guardarla dentro del .docx.
  const cleanBody = (raw: string) =>
    raw.replace(/^⚠️ BORRADOR[^\n]*\n\n?/u, "");

  const bodyText = cleanBody(contentQuery.data?.text ?? "");
  const bodyHtml = useMemo(() => markdownToHtml(bodyText), [bodyText]);

  // Sincroniza el HTML del editor cuando se carga el texto del documento.
  useEffect(() => {
    setDocHtml(bodyHtml);
  }, [bodyHtml]);

  const saveContent = useMutation({
    mutationFn: ({ id, html }: { id: string; html: string }) =>
      documentsApi.updateContent(id, htmlToPlainText(html)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["myDocs"] });
      qc.invalidateQueries({ queryKey: ["docText", viewDoc?.id] });
      notify.success("Borrador actualizado");
      setEditMode(false);
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo guardar el borrador")),
  });

  const openViewer = (id: string, title: string, isDraft: boolean) => {
    setViewDoc({ id, title, isDraft });
    setEditMode(false);
  };

  const startEditing = () => {
    setEditMode(true);
  };

  // ¿Hay cambios sin guardar en el editor?
  const isEdited = editMode && docHtml !== bodyHtml;

  // Cierra el visor pidiendo confirmación si hay cambios sin guardar.
  const requestCloseViewer = () => {
    if (isEdited) {
      setConfirmDiscard(true);
    } else {
      setViewDoc(null);
      setEditMode(false);
    }
  };

  // Cancela la edición pidiendo confirmación si hay cambios sin guardar.
  const requestCancelEdit = () => {
    if (isEdited) {
      setConfirmDiscard(true);
    } else {
      setEditMode(false);
      setDocHtml(bodyHtml);
    }
  };

  return (
    <Box>
      <PageHeader title="Mis documentos" subtitle="Documentos que ha generado, con su estado y plazos." />

      {/* Barra de búsqueda y filtros */}
      <Box sx={{ display: "flex", gap: 1.5, mb: 2, flexWrap: "wrap" }}>
        <TextField
          size="small" placeholder="Buscar por cliente, radicado o tipo…"
          value={search} onChange={(e) => setSearch(e.target.value)}
          sx={{ flex: 1, minWidth: 220 }}
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
                <TableCell align="right">
                  <RowActions
                    actions={[
                      {
                        label: d.status === "DRAFT" ? "Ver / Editar" : "Ver",
                        icon: d.status === "DRAFT" ? <EditIcon fontSize="small" /> : <VisibilityIcon fontSize="small" />,
                        onClick: () => openViewer(d.transactionId, templateName(d.templateId), d.status === "DRAFT"),
                      },
                      {
                        label: "Descargar Word",
                        icon: <DownloadIcon fontSize="small" />,
                        onClick: () => documentsApi.download(d.transactionId, d.fileName, "docx"),
                        divider: true,
                      },
                      {
                        label: "Descargar PDF",
                        icon: <DownloadIcon fontSize="small" />,
                        onClick: () => documentsApi.download(d.transactionId, undefined, "pdf"),
                      },
                      ...(canReview && (d.status === "DRAFT" || d.status === "REVIEWED")
                        ? [{
                            label: "Aprobar",
                            icon: <CheckCircleIcon fontSize="small" />,
                            color: "success" as const,
                            onClick: () => setStatusChange({ id: d.transactionId, clientName: d.clientName ?? "este documento", next: "APPROVED" }),
                            divider: true,
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

      {/* Visor / editor de contenido */}
      <Dialog open={!!viewDoc} onClose={requestCloseViewer} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <span>{editMode ? "Editar borrador" : "Vista del documento"} — {viewDoc?.title}</span>
          {viewDoc?.isDraft && (
            <Chip label="Borrador" color="warning" size="small" />
          )}
        </DialogTitle>
        <DialogContent dividers>
          {contentQuery.isLoading ? (
            <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
              <CircularProgress />
            </Box>
          ) : contentQuery.isError ? (
            <Alert severity="error">No se pudo cargar el contenido del documento.</Alert>
          ) : (
            <>
              {viewDoc?.isDraft && (
                <Alert severity="warning" sx={{ mb: 2 }}>
                  Este documento es un borrador y requiere revisión de un abogado antes de su uso.
                </Alert>
              )}
              {editMode && (
                <Alert severity="info" sx={{ mb: 2 }}>
                  Edite el documento con la barra de formato. Al guardar se regenera
                  conservando el membrete y el formato de la plantilla.
                </Alert>
              )}
              {/* Mismo control de visualización que el paso 3 del asistente */}
              <DocumentEditor
                content={docHtml}
                onChange={setDocHtml}
                readOnly={!editMode}
              />
            </>
          )}
        </DialogContent>
        <DialogActions>
          {editMode ? (
            <>
              <Button onClick={requestCancelEdit} disabled={saveContent.isPending}>Cancelar</Button>
              <Button variant="contained" disabled={!docHtml.trim() || !isEdited || saveContent.isPending}
                onClick={() => viewDoc && saveContent.mutate({ id: viewDoc.id, html: docHtml })}>
                {saveContent.isPending ? "Guardando…" : "Guardar cambios"}
              </Button>
            </>
          ) : (
            <>
              <Button onClick={requestCloseViewer}>Cerrar</Button>
              {viewDoc?.isDraft && !contentQuery.isLoading && !contentQuery.isError && (
                <Button variant="contained" startIcon={<EditIcon />} onClick={startEditing}>
                  Editar
                </Button>
              )}
            </>
          )}
        </DialogActions>
      </Dialog>

      {/* Confirmación de cambio de estado (aprobar / anular) */}
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

      {/* Confirmación de descartar cambios sin guardar del editor */}
      <ConfirmDialog
        open={confirmDiscard}
        title="Descartar cambios"
        message="Tiene cambios sin guardar en el documento. Si continúa, se perderán."
        confirmLabel="Descartar"
        confirmColor="error"
        onClose={() => setConfirmDiscard(false)}
        onConfirm={() => {
          setConfirmDiscard(false);
          setEditMode(false);
          setDocHtml(bodyHtml);
          setViewDoc(null);
        }}
      />
    </Box>
  );
}
