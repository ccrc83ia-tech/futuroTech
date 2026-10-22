import ArticleIcon from "@mui/icons-material/Article";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import DeleteIcon from "@mui/icons-material/Delete";
import MenuBookIcon from "@mui/icons-material/MenuBook";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { errorMessage } from "../api/client";
import { templatesApi } from "../api/endpoints";
import type { Template } from "../api/types";
import { PERMISSIONS } from "../api/types";
import { EmptyState, LoadingState } from "../components/states";
import { useNotify } from "../components/NotificationProvider";
import PageHeader from "../components/PageHeader";
import { useAuthStore } from "../store/auth";
import RagDocumentsDialog from "./RagDocumentsDialog";

interface EditTemplate {
  templateId: string;
  name: string;
  description: string;
  fieldsText: string;
  systemPrompt: string;
  legalRulesText: string;
  ragStrict: boolean;
  isNew: boolean;
}

function toEdit(t?: Template): EditTemplate {
  return {
    templateId: t?.templateId ?? "",
    name: t?.name ?? "",
    description: t?.description ?? "",
    fieldsText: (t?.fields ?? []).join(", "),
    systemPrompt: t?.systemPrompt ?? "",
    legalRulesText: (t?.legalRules ?? []).join("\n"),
    ragStrict: t?.ragStrict ?? true,
    isNew: !t,
  };
}

export default function TemplatesPage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const canManage = useAuthStore((s) => s.hasPermission(PERMISSIONS.MANAGE_TEMPLATES));
  const { data, isLoading } = useQuery({ queryKey: ["templates"], queryFn: templatesApi.list });
  const [editing, setEditing] = useState<EditTemplate | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [uploadTarget, setUploadTarget] = useState<string | null>(null);
  const [ragTarget, setRagTarget] = useState<Template | null>(null);

  const save = useMutation({
    mutationFn: (e: EditTemplate) =>
      templatesApi.save({
        templateId: e.templateId,
        name: e.name,
        description: e.description,
        fields: e.fieldsText.split(",").map((s) => s.trim()).filter(Boolean),
        systemPrompt: e.systemPrompt,
        legalRules: e.legalRulesText.split("\n").map((s) => s.trim()).filter(Boolean),
        ragStrict: e.ragStrict,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["templates"] });
      notify.success("Plantilla guardada correctamente");
      setEditing(null);
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo guardar la plantilla")),
  });
  const remove = useMutation({
    mutationFn: (id: string) => templatesApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["templates"] });
      notify.success("Plantilla eliminada");
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo eliminar la plantilla")),
  });
  const upload = useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => templatesApi.uploadFormatDoc(id, file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["templates"] });
      notify.success("Documento modelo cargado");
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudo cargar el documento modelo")),
  });

  function onFileChosen(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file && uploadTarget) {
      upload.mutate({ id: uploadTarget, file });
    }
    setUploadTarget(null);
    if (fileRef.current) {
      fileRef.current.value = "";
    }
  }

  const valid = editing ? editing.templateId.trim().length > 0 && editing.name.trim().length > 0 : false;

  return (
    <Box>
      <PageHeader
        title="Plantillas"
        subtitle="Tipos de documento, su formato modelo y las fuentes normativas."
        action={canManage && <Button variant="contained" onClick={() => setEditing(toEdit())}>Nueva plantilla</Button>}
      />

      <input ref={fileRef} type="file" accept=".docx" hidden onChange={onFileChosen} />

      {isLoading ? (
        <LoadingState label="Cargando plantillas…" />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon={<ArticleIcon sx={{ fontSize: 48 }} />}
          title="Aún no hay plantillas"
          description="Las plantillas definen el tipo de documento jurídico y las reglas que sigue la IA."
          action={canManage ? <Button variant="contained" onClick={() => setEditing(toEdit())}>Nueva plantilla</Button> : undefined}
        />
      ) : (
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Nombre</TableCell>
              <TableCell>Modo estricto</TableCell>
              <TableCell>Documento modelo</TableCell>
              <TableCell align="right"></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.map((t) => (
              <TableRow key={t.templateId} hover>
                <TableCell onClick={() => canManage && setEditing(toEdit(t))} sx={{ cursor: canManage ? "pointer" : "default" }}>
                  {t.name}
                </TableCell>
                <TableCell>{t.ragStrict ? "Sí" : "No"}</TableCell>
                <TableCell>
                  {t.hasFormatDoc
                    ? <Chip icon={<CheckCircleIcon />} label={t.formatDocName ?? "Cargado"} color="success" size="small" />
                    : <Chip label="Sin formato" size="small" />}
                </TableCell>
                <TableCell align="right">
                  <Tooltip title="Fuentes normativas (corpus RAG)">
                    <IconButton
                      aria-label={`Fuentes normativas de ${t.name}`}
                      onClick={() => setRagTarget(t)}
                    >
                      <MenuBookIcon />
                    </IconButton>
                  </Tooltip>
                  {canManage && (
                    <>
                      <Tooltip title="Subir documento modelo (.docx)">
                        <IconButton
                          aria-label={`Subir documento modelo para ${t.name}`}
                          onClick={() => { setUploadTarget(t.templateId); fileRef.current?.click(); }}
                        >
                          <UploadFileIcon />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Eliminar plantilla">
                        <IconButton aria-label={`Eliminar ${t.name}`} onClick={() => remove.mutate(t.templateId)}>
                          <DeleteIcon />
                        </IconButton>
                      </Tooltip>
                    </>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={editing !== null} onClose={() => setEditing(null)} fullWidth maxWidth="md">
        <DialogTitle>{editing?.isNew ? "Nueva plantilla" : "Editar plantilla"}</DialogTitle>
        <DialogContent>
          {editing && (
            <>
              <TextField label="ID (ej. ACCION-TUTELA-V1)" fullWidth margin="dense" required disabled={!editing.isNew}
                value={editing.templateId} onChange={(e) => setEditing({ ...editing, templateId: e.target.value })} />
              <TextField label="Nombre" fullWidth margin="dense" required
                value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} />
              <TextField label="Descripción" fullWidth margin="dense"
                value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} />
              <TextField label="Campos del caso (separados por coma)" fullWidth margin="dense"
                helperText="Ej: cliente_nombre, cedula, hechos_crudos"
                value={editing.fieldsText} onChange={(e) => setEditing({ ...editing, fieldsText: e.target.value })} />
              <TextField label="Prompt del sistema (instrucciones jurídicas)" fullWidth margin="dense"
                multiline minRows={4}
                value={editing.systemPrompt} onChange={(e) => setEditing({ ...editing, systemPrompt: e.target.value })} />
              <TextField label="Reglas jurídicas (una por línea)" fullWidth margin="dense" multiline minRows={3}
                value={editing.legalRulesText} onChange={(e) => setEditing({ ...editing, legalRulesText: e.target.value })} />
              <FormControlLabel
                control={<Switch checked={editing.ragStrict}
                  onChange={(e) => setEditing({ ...editing, ragStrict: e.target.checked })} />}
                label="Modo RAG estricto (solo cita fuentes cargadas, previene alucinaciones)"
              />
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancelar</Button>
          <Button variant="contained" disabled={!valid || save.isPending}
            onClick={() => editing && save.mutate(editing)}>
            {save.isPending ? "Guardando…" : "Guardar"}
          </Button>
        </DialogActions>
      </Dialog>

      <RagDocumentsDialog
        templateId={ragTarget?.templateId ?? null}
        templateName={ragTarget?.name}
        canManage={canManage}
        onClose={() => setRagTarget(null)}
      />
    </Box>
  );
}
