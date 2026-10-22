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
import { useNotify } from "../components/NotificationProvider";
import { EmptyState, LoadingState } from "../components/states";
import { useAuthStore } from "../store/auth";

const STATUS_COLOR: Record<DocumentStatus, "warning" | "info" | "success" | "default"> = {
  DRAFT: "warning", REVIEWED: "info", APPROVED: "success", VOID: "default",
};
const STATUS_LABEL: Record<DocumentStatus, string> = {
  DRAFT: "Borrador", REVIEWED: "Revisado", APPROVED: "Aprobado", VOID: "Anulado",
};

export default function AllDocumentsPage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const role = useAuthStore((s) => s.role);
  const canReview = role === "abogado" || role === "admin";

  const { data, isLoading } = useQuery({ queryKey: ["allDocs"], queryFn: documentsApi.all });
  const templatesQuery = useQuery({ queryKey: ["templates"], queryFn: templatesApi.list });

  const templateName = useMemo(() => {
    const map = new Map<string, string>();
    (templatesQuery.data ?? []).forEach((t) => map.set(t.templateId, t.name));
    return (id: string) => map.get(id) ?? id;
  }, [templatesQuery.data]);

  const review = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      documentsApi.review(id, status),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["allDocs"] });
      notify.success("Estado actualizado");
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo actualizar el estado")),
  });

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Typography variant="h4">Todos los documentos</Typography>
        <Typography variant="body2" color="text.secondary">
          {data ? `${data.length} documento${data.length !== 1 ? "s" : ""}` : ""}
        </Typography>
      </Box>

      {isLoading ? (
        <LoadingState label="Cargando documentos…" />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon={<DescriptionIcon sx={{ fontSize: 48 }} />}
          title="No hay documentos generados"
          description="Los documentos generados por el equipo aparecerán aquí."
        />
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Tipo</TableCell>
              <TableCell>Cliente</TableCell>
              <TableCell>Dirigido a</TableCell>
              <TableCell>Autor</TableCell>
              <TableCell>Fecha</TableCell>
              <TableCell>Estado</TableCell>
              <TableCell>Revisado por</TableCell>
              <TableCell align="right">Acciones</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.map((d) => (
              <TableRow key={d.transactionId} hover>
                <TableCell sx={{ maxWidth: 160 }}>
                  <Tooltip title={templateName(d.templateId)}>
                    <span style={{ display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {templateName(d.templateId)}
                    </span>
                  </Tooltip>
                </TableCell>

                <TableCell sx={{ maxWidth: 140 }}>
                  <Tooltip title={d.clientName ?? "—"}>
                    <span style={{ display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {d.clientName ?? <Typography component="span" variant="body2" color="text.disabled">—</Typography>}
                    </span>
                  </Tooltip>
                </TableCell>

                <TableCell sx={{ maxWidth: 160 }}>
                  <Tooltip title={d.entityName ?? d.entityId ?? "Sin entidad"}>
                    <span style={{ display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {d.entityName ?? <Typography component="span" variant="body2" color="text.disabled">Sin entidad</Typography>}
                    </span>
                  </Tooltip>
                </TableCell>

                <TableCell>
                  <Typography variant="body2" noWrap>{d.authorUserId ?? "—"}</Typography>
                </TableCell>

                <TableCell sx={{ whiteSpace: "nowrap" }}>
                  {new Date(d.createdAt).toLocaleString("es-CO", {
                    day: "2-digit", month: "2-digit", year: "numeric",
                    hour: "2-digit", minute: "2-digit",
                  })}
                </TableCell>

                <TableCell>
                  <Chip label={STATUS_LABEL[d.status]} color={STATUS_COLOR[d.status]} size="small" />
                </TableCell>

                <TableCell sx={{ maxWidth: 160 }}>
                  {d.reviewedBy ? (
                    <Tooltip title={d.reviewedAt ? new Date(d.reviewedAt).toLocaleString("es-CO") : d.reviewedBy}>
                      <Typography variant="body2" noWrap>{d.reviewedBy}</Typography>
                    </Tooltip>
                  ) : (
                    <Typography variant="body2" color="text.disabled">—</Typography>
                  )}
                </TableCell>

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
                    <Button
                      size="small" color="success" startIcon={<CheckCircleIcon />}
                      disabled={review.isPending}
                      onClick={() => review.mutate({ id: d.transactionId, status: "APPROVED" })}
                    >
                      Aprobar
                    </Button>
                  )}
                  {canReview && d.status === "REVIEWED" && (
                    <Button
                      size="small" color="success" variant="contained" startIcon={<CheckCircleIcon />}
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
