import { Box, Container, Paper, Typography } from "@mui/material";

/**
 * Placeholder de Fase 0. El enrutamiento real (login + wizard de generación
 * + pantallas de administración) se construye en la Fase 5.
 */
export default function App() {
  return (
    <Container maxWidth="md">
      <Box sx={{ mt: 8, textAlign: "center" }}>
        <Paper elevation={2} sx={{ p: 5 }}>
          <Typography variant="h4" gutterBottom color="primary">
            FuturoTech AI
          </Typography>
          <Typography variant="h6" color="text.secondary">
            Motor Jurídico Universal
          </Typography>
          <Typography variant="body2" sx={{ mt: 3 }} color="text.secondary">
            Scaffolding Fase 0 — frontend operativo. El flujo de generación de
            documentos se implementa en fases posteriores.
          </Typography>
        </Paper>
      </Box>
    </Container>
  );
}
