import { Alert, Box, Button, Grid2 as Grid, Paper, TextField, Typography } from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { errorMessage } from "../api/client";
import { companyApi } from "../api/endpoints";
import type { Company } from "../api/types";
import { PERMISSIONS } from "../api/types";
import { useAuthStore } from "../store/auth";

export default function CompanyPage() {
  const qc = useQueryClient();
  const canManage = useAuthStore((s) => s.hasPermission(PERMISSIONS.MANAGE_COMPANY));
  const { data } = useQuery({ queryKey: ["company"], queryFn: companyApi.get });
  const [form, setForm] = useState<Company | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (data) {
      setForm(data);
    }
  }, [data]);

  const save = useMutation({
    mutationFn: (c: Company) => companyApi.update(c),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["company"] }),
    onError: (e) => setError(errorMessage(e)),
  });

  if (!form) {
    return <Alert severity="info">No hay una firma configurada todavía.</Alert>;
  }

  const b = form.branding;
  const setBranding = (k: keyof Company["branding"], v: string) =>
    setForm({ ...form, branding: { ...b, [k]: v } });

  return (
    <Box sx={{ maxWidth: 800 }}>
      <Typography variant="h4" gutterBottom>Empresa</Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

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
          <Grid size={{ xs: 6 }}>
            <TextField label="NIT" fullWidth disabled={!canManage}
              value={b.nit ?? ""} onChange={(e) => setBranding("nit", e.target.value)} />
          </Grid>
          <Grid size={{ xs: 6 }}>
            <TextField label="Color primario (#hex)" fullWidth disabled={!canManage}
              value={b.primaryColor} onChange={(e) => setBranding("primaryColor", e.target.value)} />
          </Grid>
          <Grid size={{ xs: 6 }}>
            <TextField label="Dirección" fullWidth disabled={!canManage}
              value={b.address ?? ""} onChange={(e) => setBranding("address", e.target.value)} />
          </Grid>
          <Grid size={{ xs: 6 }}>
            <TextField label="Teléfono" fullWidth disabled={!canManage}
              value={b.phone ?? ""} onChange={(e) => setBranding("phone", e.target.value)} />
          </Grid>
        </Grid>

        {canManage && (
          <Button variant="contained" sx={{ mt: 3 }} onClick={() => save.mutate(form)}>
            Guardar cambios
          </Button>
        )}
      </Paper>
    </Box>
  );
}
