import { createTheme } from "@mui/material/styles";

const DEFAULT_PRIMARY = "#1a237e";

/** Oscurece/aclara un color hex un porcentaje dado (−1..1). */
function shade(hex: string, amount: number): string {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  if (!m) return hex;
  const num = parseInt(m[1], 16);
  const clamp = (v: number) => Math.max(0, Math.min(255, Math.round(v)));
  const r = clamp(((num >> 16) & 0xff) * (1 + amount));
  const g = clamp(((num >> 8) & 0xff) * (1 + amount));
  const b = clamp((num & 0xff) * (1 + amount));
  return `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`;
}

/**
 * Construye el tema central de la aplicación. Define una identidad profesional y
 * sobria (acorde a una firma de abogados) y estandariza tipografía, densidad de
 * tablas y estilos de componentes para que todas las pantallas se vean
 * coherentes sin depender de estilos ad hoc por página.
 *
 * El color primario puede sobreescribirse con el branding de la firma (tenant).
 */
export function buildTheme(primaryColor?: string | null) {
  const main = primaryColor && /^#([0-9a-fA-F]{6})$/.test(primaryColor) ? primaryColor : DEFAULT_PRIMARY;
  return createTheme({
    palette: {
      primary: { main, dark: shade(main, -0.35), light: shade(main, 0.4), contrastText: "#ffffff" },
      secondary: { main: "#9a7b4f", dark: "#6b5433", light: "#cda97b", contrastText: "#ffffff" },
      success: { main: "#2e7d32" },
      warning: { main: "#ed6c02" },
      error: { main: "#d32f2f" },
      info: { main: "#0288d1" },
      background: { default: "#f4f5f8", paper: "#ffffff" },
      text: { primary: "#1f2430", secondary: "#5b6472" },
      divider: "rgba(31, 36, 48, 0.12)",
    },
    typography: {
      fontFamily: "Roboto, Arial, sans-serif",
      h4: { fontSize: "1.6rem", fontWeight: 700, letterSpacing: "-0.01em" },
      h5: { fontSize: "1.3rem", fontWeight: 700 },
      h6: { fontSize: "1.05rem", fontWeight: 600 },
      subtitle1: { fontWeight: 600 },
      button: { textTransform: "none", fontWeight: 600 },
    },
    shape: { borderRadius: 10 },
    components: {
      // Botones: sin mayúsculas forzadas, sin sombra agresiva.
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { borderRadius: 8 },
          sizeSmall: { paddingInline: 10 },
        },
      },
      // Tablas compactas por defecto (densidad uniforme en toda la app).
      MuiTable: { defaultProps: { size: "small" } },
      MuiTableCell: {
        styleOverrides: {
          head: { fontWeight: 700, color: "#5b6472", whiteSpace: "nowrap" },
          root: { borderColor: "rgba(31, 36, 48, 0.08)" },
        },
      },
      MuiTableRow: {
        styleOverrides: {
          root: { "&:last-child td": { borderBottom: 0 } },
        },
      },
      // Tarjetas con borde sutil en vez de sombra fuerte.
      MuiCard: {
        defaultProps: { variant: "outlined" },
        styleOverrides: {
          root: { borderRadius: 12 },
        },
      },
      MuiPaper: {
        styleOverrides: {
          rounded: { borderRadius: 12 },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: { fontWeight: 600 },
        },
      },
      MuiTextField: { defaultProps: { size: "small" } },
      MuiTooltip: {
        defaultProps: { arrow: true },
      },
      // Encabezado de diálogo con jerarquía clara.
      MuiDialogTitle: {
        styleOverrides: { root: { fontSize: "1.15rem", fontWeight: 700 } },
      },
    },
  });
}

/** Tema por defecto (sin branding de tenant). */
export const theme = buildTheme();
