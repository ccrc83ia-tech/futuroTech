import DeleteIcon from "@mui/icons-material/Delete";
import MenuBookIcon from "@mui/icons-material/MenuBook";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Tooltip,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef } from "react";
import { errorMessage } from "../api/client";
import { templatesApi } from "../api/endpoints";
import { EmptyState, LoadingState } from "../components/states";
import { useNotify } from "../components/NotificationProvider";

interface Props {
  templateId: string | null;
  templateName?: string;
  canManage: boolean;
  onClose: () => void;
}

/**
 * Gestiona el corpus RAG (fuentes normativas autorizadas) de una plantilla:
 * listar, subir (PDF/DOCX/TXT) y eliminar. Estas fuentes alimentan el modo
 * estricto, que solo permite citar normativa presente en ellas.
 */
export default function RagDocumentsDialog({ templateId, templateName, canManage, onClose }: Props) {
  const qc = useQueryClient();
  const notify = useNotify();
  const fileRef = useRef<HTMLInputElement>(null);
  const open = templateId !== null;

  const { data, isLoading } = useQuery({
    queryKey: ["ragDocs", templateId],
    queryFn: () => templatesApi.listRagDocuments(templateId as string),
    enabled: open,
  });

  const upload = useMutation({
    mutationFn: (file: File) => templatesApi.uploadRagDocument(templateId as string, file),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ["ragDocs", templateId] });
      notify.success(`"${res.docName}" indexado en ${res.chunks} fragmento(s)`);
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo indexar el documento")),
  });

  const remove = useMutation({
    mutationFn: (docName: string) => templatesApi.deleteRagDocument(templateId as string, docName),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ragDocs", templateId] });
      notify.success("Documento eliminado del corpus");
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo eliminar el documento")),
  });

  function onFileChosen(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) {
      upload.mutate(file);
    }
    if (fileRef.current) {
      fileRef.current.value = "";
    }
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Fuentes normativas{templateName ? ` — ${templateName}` : ""}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Documentos (leyes, decretos, jurisprudencia) que la IA puede citar. En modo estricto,
          solo se cita normativa presente en estas fuentes. Formatos: PDF, DOCX, TXT.
        </Typography>

        <input ref={fileRef} type="file" accept=".pdf,.docx,.txt" hidden onChange={onFileChosen} />

        {canManage && (
          <Button
            variant="outlined"
            startIcon={<UploadFileIcon />}
            disabled={upload.isPending}
            onClick={() => fileRef.current?.click()}
            sx={{ mb: 2 }}
          >
            {upload.isPending ? "Indexando…" : "Subir documento"}
          </Button>
        )}

        {isLoading ? (
          <LoadingState label="Cargando fuentes…" />
        ) : !data || data.length === 0 ? (
          <EmptyState
            icon={<MenuBookIcon sx={{ fontSize: 48 }} />}
            title="Sin fuentes cargadas"
            description="Sin fuentes, el modo estricto impide citar texto normativo. Suba al menos un documento."
          />
        ) : (
          <List dense>
            {data.map((name) => (
              <ListItem
                key={name}
                secondaryAction={
                  canManage ? (
                    <Tooltip title="Eliminar del corpus">
                      <IconButton
                        edge="end"
                        aria-label={`Eliminar ${name}`}
                        disabled={remove.isPending}
                        onClick={() => remove.mutate(name)}
                      >
                        <DeleteIcon />
                      </IconButton>
                    </Tooltip>
                  ) : undefined
                }
              >
                <ListItemIcon sx={{ minWidth: 36 }}>
                  <MenuBookIcon fontSize="small" />
                </ListItemIcon>
                <ListItemText primary={name} />
              </ListItem>
            ))}
          </List>
        )}

        {!canManage && (
          <Box sx={{ mt: 1 }}>
            <Alert severity="info">Solo lectura: no tiene permisos para modificar las fuentes.</Alert>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cerrar</Button>
      </DialogActions>
    </Dialog>
  );
}
