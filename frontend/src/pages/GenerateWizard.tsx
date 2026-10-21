import DescriptionIcon from "@mui/icons-material/Description";
import DownloadIcon from "@mui/icons-material/Download";
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
  LinearProgress,
  Paper,
  Step,
  StepLabel,
  Stepper,
  TextField,
  Typography,
} from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { errorMessage } from "../api/client";
import { documentsApi, entitiesApi, templatesApi } from "../api/endpoints";
import type { GeneratedDoc, Template } from "../api/types";

const STEPS = ["Tipo de documento", "Datos del caso", "Generar"];

export default function GenerateWizard() {
  const [activeStep, setActiveStep] = useState(0);
  const [selected, setSelected] = useState<Template | null>(null);
  const [caseFields, setCaseFields] = useState<Record<string, string>>({});
  const [entityId, setEntityId] = useState<string>("");
  const [generating, setGenerating] = useState(false);
  const [result, setResult] = useState<GeneratedDoc | null>(null);
  const [error, setError] = useState<string | null>(null);

  const templatesQuery = useQuery({ queryKey: ["templates"], queryFn: templatesApi.list });
  const entitiesQuery = useQuery({ queryKey: ["entities"], queryFn: entitiesApi.list });

  const canContinue = useMemo(() => {
    if (activeStep === 0) {
      return selected !== null;
    }
    if (activeStep === 1) {
      return (selected?.fields ?? []).every((f) => (caseFields[f] ?? "").trim().length > 0);
    }
    return true;
  }, [activeStep, selected, caseFields]);

  function pickTemplate(t: Template) {
    setSelected(t);
    setCaseFields({});
    setActiveStep(1);
  }

  async function generate() {
    if (!selected) {
      return;
    }
    setGenerating(true);
    setError(null);
    setResult(null);
    try {
      const doc = await documentsApi.generate({
        templateId: selected.templateId,
        entityId: entityId || undefined,
        caseFields,
        ragStrict: selected.ragStrict,
      });
      setResult(doc);
    } catch (e) {
      setError(errorMessage(e, "No se pudo generar el documento"));
    } finally {
      setGenerating(false);
    }
  }

  function reset() {
    setActiveStep(0);
    setSelected(null);
    setCaseFields({});
    setEntityId("");
    setResult(null);
    setError(null);
  }

  return (
    <Box sx={{ maxWidth: 900, mx: "auto" }}>
      <Typography variant="h4" gutterBottom>Generar documento</Typography>

      <Stepper activeStep={activeStep} sx={{ my: 3 }}>
        {STEPS.map((label) => (
          <Step key={label}><StepLabel>{label}</StepLabel></Step>
        ))}
      </Stepper>

      {/* Paso 1: elegir plantilla */}
      {activeStep === 0 && (
        <Box>
          {templatesQuery.isLoading && <CircularProgress />}
          {templatesQuery.data?.length === 0 && (
            <Alert severity="info">
              Aún no hay plantillas. Pida a un administrador que cree la primera.
            </Alert>
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

      {/* Paso 2: datos del caso */}
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
          <Box sx={{ mt: 2, display: "flex", gap: 1 }}>
            <Button onClick={() => setActiveStep(0)}>Atrás</Button>
            <Button variant="contained" disabled={!canContinue} onClick={() => setActiveStep(2)}>
              Continuar
            </Button>
          </Box>
        </Paper>
      )}

      {/* Paso 3: generar */}
      {activeStep === 2 && selected && (
        <Paper sx={{ p: 3 }}>
          {!result && !generating && (
            <Box sx={{ textAlign: "center" }}>
              <Typography sx={{ mb: 2 }}>
                Todo listo para generar <strong>{selected.name}</strong>.
              </Typography>
              <Button variant="contained" size="large" onClick={generate}>
                Generar documento
              </Button>
              <Button sx={{ ml: 1 }} onClick={() => setActiveStep(1)}>Atrás</Button>
            </Box>
          )}

          {generating && (
            <Box sx={{ textAlign: "center", py: 4 }}>
              <Typography sx={{ mb: 2 }}>
                Generando el documento… esto puede tardar hasta un par de minutos.
              </Typography>
              <LinearProgress />
            </Box>
          )}

          {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}

          {result && (
            <Box sx={{ textAlign: "center" }}>
              <Alert severity="warning" sx={{ mb: 2, textAlign: "left" }}>
                Documento generado por IA en estado <strong>BORRADOR</strong>. Requiere revisión y
                validación de un abogado antes de su uso. Verifique las citas normativas contra la
                fuente oficial.
              </Alert>
              <Button
                variant="contained"
                startIcon={<DownloadIcon />}
                href={documentsApi.downloadUrl(result.transactionId)}
              >
                Descargar documento (.docx)
              </Button>
              <Button sx={{ ml: 1 }} onClick={reset}>Generar otro</Button>
            </Box>
          )}
        </Paper>
      )}
    </Box>
  );
}

/** Convierte "cliente_nombre" -> "Cliente nombre" para etiquetas legibles. */
function humanize(field: string): string {
  const clean = field.replace(/_/g, " ").trim();
  return clean.charAt(0).toUpperCase() + clean.slice(1);
}
