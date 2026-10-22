import { Alert, Box, Button, Grid2 as Grid, InputAdornment, Paper, TextField, Typography } from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { errorMessage } from "../api/client";
import { companyApi } from "../api/endpoints";
import type { Company } from "../api/types";
import { PERMISSIONS } from "../api/types";
import { LoadingState } from "../components/states";
import { useNotify } from "../components/NotificationProvider";
import { useAuthStore } from "../store/auth";

const HEX_RE = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/;

export default function CompanyPage() {
  const qc = useQueryClient();
  const notify = useNotify();
  const canManage = useAuthStore((s) => s.hasPermission(PERMISSIONS.MANAGE_COMPANY));
  const { data, isLoading } = useQuery({ queryKey: ["company"], queryFn: companyApi.get });
  const [form, setForm] = useState<Company | null>(null);

  useEffect(() => {
    if (data) {
      setForm(data);
    }
  }, [data]);

  const save = useMutation({
    mutationFn: (c: Company) => companyApi.update(c),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["company"] });
      notify.success("Cambios guardados correctamente");
    },
    onError: (e) => notify.error(errorMessage(e, "No se pudieron guardar los cambios")),
  });

  if (isLoading) {
    return <LoadingState label="Cargando datos de la firma…" />;
  }

  if (!form) {
    return <Alert severity="info">No hay una firma configurada todavía.</Alert>;
  }

  const b = form.branding;
  const setBranding = (k: keyof Company["branding"], v: string) =>
    setForm({ ...form, branding: { ...b, [k]: v } });

  const colorValid = HEX_RE.test(b.primaryColor);

  return (
    <Box sx={{ maxWidth: 800 }}>
      <Typography variant="h4" gutterBottom>Empresa</Typography>

      <Paper sx={{ p: 3 }}>
        <TextField label="Nombre de la firma" fullWidth margin="dense" disabled={!canManage}
          value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />

        <Typography variant="h6" sx={{ mt: 3, mb: 1 }}>Membrete</Typography>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12 }}>
            <TextField label="Texto del encabezado" fullWidth disabled={!canManage}
              value={b.headerText ?? ""} onChange={(e) => setBranding("headerText", e.target.value)} />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField label="Texto del pie de página" fullWidth disabled={!canManage}
              value={b.footerText ?? ""} onChange={(e) => setBranding("footerText", e.target.value)} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField label="NIT" fullWidth disabled={!canManage}
              value={b.nit ?? ""} onChange={(e) => setBranding("nit", e.target.value)} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              label="Color primario"
              fullWidth
              disabled={!canManage}
              error={!colorValid}
              helperText={colorValid ? "Color de la firma en documentos" : "Formato inválido (use #RRGGBB)"}
              value={b.primaryColor}
              onChange={(e) => setBranding("primaryColor", e.target.value)}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <Box
                        sx={{
                          width: 20,
                          height: 20,
                          borderRadius: "4px",
                          border: "1px solid rgba(0,0,0,0.2)",
                          bgcolor: colorValid ? b.primaryColor : "transparent",
                        }}
                      />
                    </InputAdornment>
                  ),
                },
              }}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField label="Dirección" fullWidth disabled={!canManage}
              value={b.address ?? ""} onChange={(e) => setBranding("address", e.target.value)} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField label="Teléfono" fullWidth disabled={!canManage}
              value={b.phone ?? ""} onChange={(e) => setBranding("phone", e.target.value)} />
          </Grid>
        </Grid>

        {canManage && (
          <Button variant="contained" sx={{ mt: 3 }} disabled={!colorValid || save.isPending}
            onClick={() => save.mutate(form)}>
            {save.isPending ? "Guardando…" : "Guardar cambios"}
          </Button>
        )}
      </Paper>
    </Box>
  );
}
