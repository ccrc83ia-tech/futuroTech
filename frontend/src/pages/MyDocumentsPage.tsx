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
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Plantilla</TableCell>
              <TableCell>Fecha</TableCell>
              <TableCell>Estado</TableCell>
              <TableCell align="right">Acciones</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.map((d) => (
              <TableRow key={d.transactionId} hover>
                <TableCell>{templateName(d.templateId)}</TableCell>
                <TableCell>{new Date(d.createdAt).toLocaleString("es-CO")}</TableCell>
                <TableCell>
                  <Chip label={STATUS_LABEL[d.status]} color={STATUS_COLOR[d.status]} size="small" />
                </TableCell>
                <TableCell align="right">
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
