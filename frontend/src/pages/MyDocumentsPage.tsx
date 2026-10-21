import DownloadIcon from "@mui/icons-material/Download";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { documentsApi } from "../api/endpoints";
import type { DocumentStatus } from "../api/types";
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
  const role = useAuthStore((s) => s.role);
  const canReview = role === "abogado" || role === "admin";

  const { data, isLoading } = useQuery({ queryKey: ["myDocs"], queryFn: documentsApi.mine });

  const review = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => documentsApi.review(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["myDocs"] }),
  });

  if (isLoading) {
    return <CircularProgress />;
  }

  return (
    <Box>
      <Typography variant="h4" gutterBottom>Mis documentos</Typography>

      {data?.length === 0 && (
        <Alert severity="info">Aún no ha generado documentos.</Alert>
      )}

      {data && data.length > 0 && (
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
              <TableRow key={d.transactionId}>
                <TableCell>{d.templateId}</TableCell>
                <TableCell>{new Date(d.createdAt).toLocaleString("es-CO")}</TableCell>
                <TableCell>
                  <Chip label={STATUS_LABEL[d.status]} color={STATUS_COLOR[d.status]} size="small" />
                </TableCell>
                <TableCell align="right">
                  <Button
                    size="small"
                    startIcon={<DownloadIcon />}
                    href={documentsApi.downloadUrl(d.transactionId)}
                  >
                    Descargar
                  </Button>
                  {canReview && d.status === "DRAFT" && (
                    <Button
                      size="small"
                      color="success"
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
