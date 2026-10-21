import { createTheme } from "@mui/material/styles";

// Tema base. El color primario se podrá sobreescribir con el branding de la firma.
export const theme = createTheme({
  palette: {
    primary: { main: "#1a237e" },
    background: { default: "#f5f6fa" },
  },
  typography: {
    fontFamily: "Roboto, Arial, sans-serif",
  },
  shape: { borderRadius: 10 },
});
