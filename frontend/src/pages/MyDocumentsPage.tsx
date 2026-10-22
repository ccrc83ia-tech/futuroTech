import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import DescriptionIcon from "@mui/icons-material/Description";
import DownloadIcon from "@mui/icons-material/Download";
import {
  Box,
  Button,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo } from "react";
import { errorMessage } from "../api/client";
import { documentsApi, templatesApi } from "../api/endpoints";
import type { DocumentStatus } from "../api/types";
import { EmptyState, LoadingState } from "../components/states";
import { useNotify } from "../components/NotificationProvider";
import { useAuthStore } from "../store/auth";

const STATUS_COLOR: Record<DocumentStatus, "warning" | "info" | "success" | "default"> = {
  DRAFT: "warning",
  REVIEWED: "info",
  APPROVED: "success",
  VOID: "default",
};

const STATUS_LABEL: Record<DocumentStatus, string> = {
  DRAFT: "Borrador",
  REVIEWED: "Revisado",
  APPROVED: "Aprobado",
  VOID: "Anulado",
};

export default function MyDocumentsPage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const role = useAuthStore((s) => s.role);
  const canReview = role === "abogado" || role === "admin";

  const { data, isLoading } = useQuery({ queryKey: ["myDocs"], queryFn: documentsApi.mine });
  const templatesQuery = useQuery({ queryKey: ["templates"], queryFn: templatesApi.list });

  const templateName = useMemo(() => {
    const map = new Map<string, string>();
    (templatesQuery.data ?? []).forEach((t) => map.set(t.templateId, t.name));
    return (id: string) => map.get(id) ?? id;
  }, [templatesQuery.data]);

  const review = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => documentsApi.review(id, status),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["myDocs"] });
      notify.success("Documento aprobado");
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo actualizar el estado")),
  });

  return (
    <Box>
      <Typography variant="h4" gutterBottom>Mis documentos</Typography>

      {isLoading ? (
        <LoadingState label="Cargando documentos…" />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon={<DescriptionIcon sx={{ fontSize: 48 }} />}
          title="Aún no ha generado documentos"
          description="Los documentos que genere aparecerán aquí para descargarlos y revisarlos."
        />
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Tipo</TableCell>
              <TableCell>Cliente</TableCell>
              <TableCell>Dirigido a</TableCell>
              <TableCell>Generado por</TableCell>
              <TableCell>Fecha</TableCell>
              <TableCell>Estado</TableCell>
              <TableCell>Revisado por</TableCell>
              <TableCell align="right">Acciones</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.map((d) => (
              <TableRow key={d.transactionId} hover>
                {/* Tipo de documento */}
                <TableCell sx={{ maxWidth: 160 }}>
                  <Tooltip title={templateName(d.templateId)}>
                    <span style={{ display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {templateName(d.templateId)}
                    </span>
                  </Tooltip>
                </TableCell>

                {/* Cliente del caso */}
                <TableCell sx={{ maxWidth: 140 }}>
                  <Tooltip title={d.clientName ?? "—"}>
                    <span style={{ display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {d.clientName ?? <Typography component="span" variant="body2" color="text.disabled">—</Typography>}
                    </span>
                  </Tooltip>
                </TableCell>

                {/* Entidad destinataria */}
                <TableCell sx={{ maxWidth: 160 }}>
                  <Tooltip title={d.entityName ?? d.entityId ?? "Sin entidad"}>
                    <span style={{ display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {d.entityName
                        ? d.entityName
                        : <Typography component="span" variant="body2" color="text.disabled">Sin entidad</Typography>}
                    </span>
                  </Tooltip>
                </TableCell>

                {/* Autor */}
                <TableCell sx={{ maxWidth: 120 }}>
                  <Typography variant="body2" noWrap>
                    {d.authorUserId ?? "—"}
                  </Typography>
                </TableCell>

                {/* Fecha de creación */}
                <TableCell sx={{ whiteSpace: "nowrap" }}>
                  {new Date(d.createdAt).toLocaleString("es-CO", {
                    day: "2-digit", month: "2-digit", year: "numeric",
                    hour: "2-digit", minute: "2-digit",
                  })}
                </TableCell>

                {/* Estado */}
                <TableCell>
                  <Chip
                    label={STATUS_LABEL[d.status]}
                    color={STATUS_COLOR[d.status]}
                    size="small"
                  />
                </TableCell>

                {/* Revisado por + fecha */}
                <TableCell sx={{ maxWidth: 160 }}>
                  {d.reviewedBy ? (
                    <Tooltip title={d.reviewedAt
                      ? new Date(d.reviewedAt).toLocaleString("es-CO")
                      : d.reviewedBy}>
                      <Typography variant="body2" noWrap>{d.reviewedBy}</Typography>
                    </Tooltip>
                  ) : (
                    <Typography variant="body2" color="text.disabled">—</Typography>
                  )}
                </TableCell>

                {/* Acciones */}
                <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                  <Button
                    size="small"
                    startIcon={<DownloadIcon />}
                    onClick={() => documentsApi.download(d.transactionId, d.fileName, "docx")}
                  >
                    Word
                  </Button>
                  <Button
                    size="small"
                    startIcon={<DownloadIcon />}
                    onClick={() => documentsApi.download(d.transactionId, undefined, "pdf")}
                  >
                    PDF
                  </Button>
                  {canReview && d.status === "DRAFT" && (
                    <Button
                      size="small"
                      color="success"
                      startIcon={<CheckCircleIcon />}
                      disabled={review.isPending}
                      onClick={() => review.mutate({ id: d.transactionId, status: "APPROVED" })}
                    >
                      Aprobar
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Box>
  );
}
