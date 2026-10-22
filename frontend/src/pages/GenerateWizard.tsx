import AttachFileIcon from "@mui/icons-material/AttachFile";
import DeleteIcon from "@mui/icons-material/Delete";
import DescriptionIcon from "@mui/icons-material/Description";
import DownloadIcon from "@mui/icons-material/Download";
import RestoreIcon from "@mui/icons-material/Restore";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  CircularProgress,
  Grid2 as Grid,
  IconButton,
  LinearProgress,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Paper,
  Step,
  StepLabel,
  Stepper,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { errorMessage } from "../api/client";
import { documentsApi, entitiesApi, templatesApi } from "../api/endpoints";
import type { GeneratedDoc, Template } from "../api/types";
import { useNotify } from "../components/NotificationProvider";

const STEPS = ["Tipo de documento", "Datos del caso", "Generar y revisar"];

export default function GenerateWizard() {
  const notify = useNotify();
  const [activeStep, setActiveStep] = useState(0);
  const [selected, setSelected] = useState<Template | null>(null);
  const [caseFields, setCaseFields] = useState<Record<string, string>>({});
  const [entityId, setEntityId] = useState<string>("");
  const [attachments, setAttachments] = useState<File[]>([]);
  const [generating, setGenerating] = useState(false);
  const [result, setResult] = useState<GeneratedDoc | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Editor de texto del documento generado
  const [docText, setDocText] = useState<string>("");
  const [originalText, setOriginalText] = useState<string>("");
  const [loadingText, setLoadingText] = useState(false);

  const templatesQuery = useQuery({ queryKey: ["templates"], queryFn: templatesApi.list });
  const entitiesQuery = useQuery({ queryKey: ["entities"], queryFn: entitiesApi.list });

  const canContinue = useMemo(() => {
    if (activeStep === 0) return selected !== null;
    if (activeStep === 1)
      return (selected?.fields ?? []).every((f) => (caseFields[f] ?? "").trim().length > 0);
    return true;
  }, [activeStep, selected, caseFields]);

  function pickTemplate(t: Template) {
    setSelected(t);
    setCaseFields({});
    setActiveStep(1);
  }

  async function generate() {
    if (!selected) return;
    setGenerating(true);
    setError(null);
    setResult(null);
    setDocText("");
    setOriginalText("");
    try {
      const doc = await documentsApi.generate(
        { templateId: selected.templateId, entityId: entityId || undefined, caseFields, ragStrict: selected.ragStrict },
        attachments,
      );
      setResult(doc);
      notify.success("Documento generado en estado borrador");
      // Cargar el texto para el editor
      setLoadingText(true);
      try {
        const { text } = await documentsApi.text(doc.transactionId);
        setDocText(text);
        setOriginalText(text);
      } catch {
        // Si falla la extracción de texto el usuario igual puede descargar
      } finally {
        setLoadingText(false);
      }
    } catch (e) {
      const msg = errorMessage(e, "No se pudo generar el documento");
      setError(msg);
      notify.error(msg);
    } finally {
      setGenerating(false);
    }
  }

  /** Descarga el texto editado como .txt para que el usuario lo use como base. */
  function downloadEditedText() {
    const blob = new Blob([docText], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `borrador-${result?.transactionId?.slice(0, 8) ?? "doc"}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }

  function reset() {
    setActiveStep(0);
    setSelected(null);
    setCaseFields({});
    setEntityId("");
    setAttachments([]);
    setResult(null);
    setError(null);
    setDocText("");
    setOriginalText("");
  }

  const isEdited = docText !== originalText;

  return (
    <Box sx={{ maxWidth: 900, mx: "auto" }}>
      <Typography variant="h4" gutterBottom>Generar documento</Typography>

      <Stepper activeStep={activeStep} sx={{ my: 3 }}>
        {STEPS.map((label) => (
          <Step key={label}><StepLabel>{label}</StepLabel></Step>
        ))}
      </Stepper>

      {/* ── Paso 1: elegir plantilla ── */}
      {activeStep === 0 && (
        <Box>
          {templatesQuery.isLoading && <CircularProgress />}
          {templatesQuery.data?.length === 0 && (
            <Alert severity="info">Aún no hay plantillas. Pida a un administrador que cree la primera.</Alert>
          )}
          <Grid container spacing={2}>
            {templatesQuery.data?.map((t) => (
              <Grid key={t.templateId} size={{ xs: 12, sm: 6, md: 4 }}>
                <Card variant="outlined" sx={{ height: "100%" }}>
                  <CardActionArea onClick={() => pickTemplate(t)} sx={{ height: "100%" }}>
                    <CardContent>
                      <DescriptionIcon color="primary" sx={{ fontSize: 40 }} />
                      <Typography variant="h6">{t.name}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {t.description || "Documento jurídico"}
                      </Typography>
                    </CardContent>
                  </CardActionArea>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Box>
      )}

      {/* ── Paso 2: datos del caso ── */}
      {activeStep === 1 && selected && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>{selected.name}</Typography>
          <Autocomplete
            options={entitiesQuery.data ?? []}
            getOptionLabel={(e) => e.name}
            onChange={(_, v) => setEntityId(v?.entityId ?? "")}
            renderInput={(params) => (
              <TextField {...params} label="Entidad a la que se dirige" margin="normal" />
            )}
          />
          {selected.fields.map((field) => (
            <TextField
              key={field}
              label={humanize(field)}
              fullWidth
              margin="normal"
              multiline={field.toLowerCase().includes("hecho")}
              minRows={field.toLowerCase().includes("hecho") ? 4 : 1}
              value={caseFields[field] ?? ""}
              onChange={(e) => setCaseFields((prev) => ({ ...prev, [field]: e.target.value }))}
            />
          ))}

          {/* Adjuntos del caso */}
          <Box sx={{ mt: 3 }}>
            <Typography variant="subtitle1" gutterBottom>
              <AttachFileIcon fontSize="small" sx={{ verticalAlign: "middle", mr: 0.5 }} />
              Documentos del caso (opcional)
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Adjunte documentos de respaldo (p. ej. el derecho de petición negado). La IA los
              analizará como hechos. Formatos: PDF, DOCX, TXT.
            </Typography>
            <Button component="label" variant="outlined" startIcon={<UploadFileIcon />}>
              Adjuntar archivos
              <input
                type="file" hidden multiple accept=".pdf,.docx,.txt"
                onChange={(e) => {
                  setAttachments((prev) => [...prev, ...Array.from(e.target.files ?? [])]);
                  e.target.value = "";
                }}
              />
            </Button>
            {attachments.length > 0 && (
              <List dense>
                {attachments.map((file, idx) => (
                  <ListItem
                    key={`${file.name}-${idx}`}
                    secondaryAction={
                      <IconButton
                        edge="end"
                        aria-label={`Quitar ${file.name}`}
                        onClick={() => setAttachments((prev) => prev.filter((_, i) => i !== idx))}
                      >
                        <DeleteIcon />
                      </IconButton>
                    }
                  >
                    <ListItemIcon sx={{ minWidth: 36 }}>
                      <DescriptionIcon fontSize="small" />
                    </ListItemIcon>
                    <ListItemText primary={file.name} secondary={`${(file.size / 1024).toFixed(0)} KB`} />
                  </ListItem>
                ))}
              </List>
            )}
          </Box>

          <Box sx={{ mt: 2, display: "flex", gap: 1 }}>
            <Button onClick={() => setActiveStep(0)}>Atrás</Button>
            <Button variant="contained" disabled={!canContinue} onClick={() => setActiveStep(2)}>
              Continuar
            </Button>
          </Box>
        </Paper>
      )}

      {/* ── Paso 3: generar + previsualizar + editar ── */}
      {activeStep === 2 && selected && (
        <Paper sx={{ p: 3 }}>
          {/* Estado: esperando acción */}
          {!result && !generating && (
            <Box sx={{ textAlign: "center" }}>
              <Typography sx={{ mb: 2 }}>
                Todo listo para generar <strong>{selected.name}</strong>.
              </Typography>
              {attachments.length > 0 && (
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Se analizarán {attachments.length} documento(s) adjunto(s) como hechos del caso.
                </Typography>
              )}
              <Button variant="contained" size="large" onClick={generate}>
                Generar documento
              </Button>
              <Button sx={{ ml: 1 }} onClick={() => setActiveStep(1)}>Atrás</Button>
            </Box>
          )}

          {/* Estado: generando */}
          {generating && (
            <Box sx={{ textAlign: "center", py: 4 }}>
              <Typography sx={{ mb: 2 }}>
                Generando el documento… esto puede tardar hasta un par de minutos.
              </Typography>
              <LinearProgress />
            </Box>
          )}

          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}

          {/* Estado: generado — editor + descargas */}
          {result && !generating && (
            <Box>
              <Alert severity="warning" sx={{ mb: 3 }}>
                Documento generado por IA en estado <strong>BORRADOR</strong>. Requiere revisión y
                validación de un abogado antes de su uso. Verifique las citas normativas contra la
                fuente oficial.
              </Alert>

              {/* Editor de texto */}
              <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 1 }}>
                <Typography variant="subtitle1" fontWeight="medium">
                  Previsualizar y editar
                </Typography>
                <Tooltip title="Restaurar el texto original generado por la IA">
                  <span>
                    <Button
                      size="small"
                      startIcon={<RestoreIcon />}
                      onClick={() => setDocText(originalText)}
                      disabled={!isEdited}
                    >
                      Restaurar original
                    </Button>
                  </span>
                </Tooltip>
              </Box>

              {loadingText ? (
                <Box sx={{ py: 2 }}>
                  <LinearProgress />
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    Cargando texto del documento…
                  </Typography>
                </Box>
              ) : (
                <TextField
                  multiline
                  fullWidth
                  minRows={16}
                  maxRows={32}
                  value={docText}
                  onChange={(e) => setDocText(e.target.value)}
                  placeholder="El texto del documento aparecerá aquí para que pueda revisarlo y editarlo."
                  helperText={
                    isEdited
                      ? "Texto modificado. Use 'Descargar texto editado' para obtener su versión."
                      : "Puede editar este texto antes de descargar. Use **texto** para negrilla, *texto* para cursiva."
                  }
                  sx={{ fontFamily: "monospace", "& textarea": { fontFamily: "inherit", fontSize: 13 } }}
                />
              )}

              {/* Botones de descarga */}
              <Box sx={{ mt: 2, display: "flex", gap: 1, flexWrap: "wrap" }}>
                <Button
                  variant="contained"
                  startIcon={<DownloadIcon />}
                  onClick={() => documentsApi.download(result.transactionId, result.fileName, "docx")}
                >
                  Descargar Word
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<DownloadIcon />}
                  onClick={() => documentsApi.download(result.transactionId, undefined, "pdf")}
                >
                  Descargar PDF
                </Button>
                {docText && (
                  <Tooltip title={isEdited ? "Descarga tu versión editada como .txt" : "Descarga el texto como .txt"}>
                    <Button
                      variant="outlined"
                      startIcon={<DownloadIcon />}
                      onClick={downloadEditedText}
                      color={isEdited ? "warning" : "inherit"}
                    >
                      {isEdited ? "Descargar texto editado" : "Descargar texto"}
                    </Button>
                  </Tooltip>
                )}
                <Button onClick={reset} sx={{ ml: "auto" }}>
                  Generar otro
                </Button>
              </Box>
            </Box>
          )}
        </Paper>
      )}
    </Box>
  );
}

function humanize(field: string): string {
  const clean = field.replace(/_/g, " ").trim();
  return clean.charAt(0).toUpperCase() + clean.slice(1);
}
