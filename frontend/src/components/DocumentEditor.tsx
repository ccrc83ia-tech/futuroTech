import FormatAlignCenterIcon from "@mui/icons-material/FormatAlignCenter";
import FormatAlignJustifyIcon from "@mui/icons-material/FormatAlignJustify";
import FormatAlignLeftIcon from "@mui/icons-material/FormatAlignLeft";
import FormatAlignRightIcon from "@mui/icons-material/FormatAlignRight";
import FormatBoldIcon from "@mui/icons-material/FormatBold";
import FormatItalicIcon from "@mui/icons-material/FormatItalic";
import FormatListBulletedIcon from "@mui/icons-material/FormatListBulleted";
import FormatListNumberedIcon from "@mui/icons-material/FormatListNumbered";
import FormatUnderlinedIcon from "@mui/icons-material/FormatUnderlined";
import RedoIcon from "@mui/icons-material/Redo";
import UndoIcon from "@mui/icons-material/Undo";
import {
  Box,
  Divider,
  MenuItem,
  Select,
  Tooltip,
  Typography,
  useTheme,
} from "@mui/material";
import CharacterCount from "@tiptap/extension-character-count";
import Placeholder from "@tiptap/extension-placeholder";
import TextAlign from "@tiptap/extension-text-align";
import Underline from "@tiptap/extension-underline";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { useEffect } from "react";

/* ─── Tipos ─────────────────────────────────────────────────────────────── */
interface DocumentEditorProps {
  /** HTML inicial (vacío = placeholder). */
  content: string;
  /** Se llama con el HTML actualizado en cada cambio. */
  onChange: (html: string) => void;
  /** Deshabilita la edición (solo lectura). */
  readOnly?: boolean;
  /** Texto que aparece cuando el editor está vacío. */
  placeholder?: string;
}

/* ─── Botón de la barra de herramientas ─────────────────────────────────── */
interface ToolbarBtnProps {
  label: string;
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}

function ToolbarBtn({ label, active, disabled, onClick, children }: ToolbarBtnProps) {
  const theme = useTheme();
  return (
    <Tooltip title={label} arrow>
      <Box
        component="button"
        aria-label={label}
        aria-pressed={active}
        disabled={disabled}
        onClick={onClick}
        sx={{
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          width: 32,
          height: 32,
          border: "none",
          borderRadius: 1,
          cursor: disabled ? "default" : "pointer",
          color: active
            ? theme.palette.primary.main
            : theme.palette.text.secondary,
          bgcolor: active
            ? theme.palette.action.selected
            : "transparent",
          transition: "background 0.15s, color 0.15s",
          "&:hover:not(:disabled)": {
            bgcolor: theme.palette.action.hover,
            color: theme.palette.text.primary,
          },
          "&:disabled": { opacity: 0.35 },
          p: 0,
        }}
      >
        {children}
      </Box>
    </Tooltip>
  );
}

/* ─── Separador de grupo ────────────────────────────────────────────────── */
function ToolbarDivider() {
  return (
    <Divider
      orientation="vertical"
      flexItem
      sx={{ mx: 0.5, height: 24, alignSelf: "center" }}
    />
  );
}

/* ─── Selector de estilo de párrafo ─────────────────────────────────────── */
const HEADING_OPTS = [
  { value: "paragraph", label: "Párrafo" },
  { value: "h1", label: "Título 1" },
  { value: "h2", label: "Título 2" },
  { value: "h3", label: "Título 3" },
];

/* ═══════════════════════════════════════════════════════════════════════════
   DocumentEditor
   ══════════════════════════════════════════════════════════════════════════ */
export default function DocumentEditor({
  content,
  onChange,
  readOnly = false,
  placeholder = "El documento aparecerá aquí. Puede editarlo libremente.",
}: DocumentEditorProps) {
  const theme = useTheme();

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        // Deshabilitar atajos de historia que ya manejamos con botones
        history: { depth: 100 },
      }),
      Underline,
      TextAlign.configure({ types: ["heading", "paragraph"] }),
      Placeholder.configure({ placeholder }),
      CharacterCount,
    ],
    content,
    editable: !readOnly,
    onUpdate: ({ editor: e }) => onChange(e.getHTML()),
    editorProps: {
      attributes: {
        // Clase para CSS de la hoja A4
        class: "document-sheet",
        spellcheck: "true",
        lang: "es",
      },
    },
  });

  /* Sincronizar contenido externo (p.ej. al cargar el texto del API) */
  useEffect(() => {
    if (!editor) return;
    // Evitar bucle: solo actualizar si el HTML actual es distinto
    if (editor.getHTML() !== content) {
      editor.commands.setContent(content, false);
    }
  }, [content, editor]);

  /* Valor del selector de heading */
  const headingValue = (() => {
    if (!editor) return "paragraph";
    if (editor.isActive("heading", { level: 1 })) return "h1";
    if (editor.isActive("heading", { level: 2 })) return "h2";
    if (editor.isActive("heading", { level: 3 })) return "h3";
    return "paragraph";
  })();

  function applyHeading(val: string) {
    if (!editor) return;
    if (val === "paragraph") editor.chain().focus().setParagraph().run();
    else
      editor
        .chain()
        .focus()
        .setHeading({ level: Number(val.replace("h", "")) as 1 | 2 | 3 })
        .run();
  }

  const wordCount = editor?.storage.characterCount.words() ?? 0;
  const charCount = editor?.storage.characterCount.characters() ?? 0;

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: 2,
        overflow: "hidden",
        boxShadow: theme.shadows[1],
        bgcolor: theme.palette.background.paper,
      }}
    >
      {/* ── Barra de herramientas ── */}
      {!readOnly && (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            flexWrap: "wrap",
            gap: 0.25,
            px: 1.5,
            py: 0.75,
            bgcolor:
              theme.palette.mode === "dark"
                ? theme.palette.grey[900]
                : theme.palette.grey[100],
            borderBottom: `1px solid ${theme.palette.divider}`,
          }}
        >
          {/* Estilo de párrafo */}
          <Select
            size="small"
            value={headingValue}
            onChange={(e) => applyHeading(e.target.value)}
            aria-label="Estilo de párrafo"
            sx={{
              fontSize: 13,
              height: 32,
              minWidth: 110,
              mr: 0.5,
              "& .MuiSelect-select": { py: "4px" },
            }}
          >
            {HEADING_OPTS.map((o) => (
              <MenuItem key={o.value} value={o.value} sx={{ fontSize: 13 }}>
                {o.label}
              </MenuItem>
            ))}
          </Select>

          <ToolbarDivider />

          {/* Negrita / cursiva / subrayado */}
          <ToolbarBtn
            label="Negrita (Ctrl+B)"
            active={editor?.isActive("bold")}
            onClick={() => editor?.chain().focus().toggleBold().run()}
          >
            <FormatBoldIcon fontSize="small" />
          </ToolbarBtn>
          <ToolbarBtn
            label="Cursiva (Ctrl+I)"
            active={editor?.isActive("italic")}
            onClick={() => editor?.chain().focus().toggleItalic().run()}
          >
            <FormatItalicIcon fontSize="small" />
          </ToolbarBtn>
          <ToolbarBtn
            label="Subrayado (Ctrl+U)"
            active={editor?.isActive("underline")}
            onClick={() => editor?.chain().focus().toggleUnderline().run()}
          >
            <FormatUnderlinedIcon fontSize="small" />
          </ToolbarBtn>

          <ToolbarDivider />

          {/* Alineación */}
          <ToolbarBtn
            label="Alinear a la izquierda"
            active={editor?.isActive({ textAlign: "left" })}
            onClick={() => editor?.chain().focus().setTextAlign("left").run()}
          >
            <FormatAlignLeftIcon fontSize="small" />
          </ToolbarBtn>
          <ToolbarBtn
            label="Centrar"
            active={editor?.isActive({ textAlign: "center" })}
            onClick={() => editor?.chain().focus().setTextAlign("center").run()}
          >
            <FormatAlignCenterIcon fontSize="small" />
          </ToolbarBtn>
          <ToolbarBtn
            label="Alinear a la derecha"
            active={editor?.isActive({ textAlign: "right" })}
            onClick={() => editor?.chain().focus().setTextAlign("right").run()}
          >
            <FormatAlignRightIcon fontSize="small" />
          </ToolbarBtn>
          <ToolbarBtn
            label="Justificar"
            active={editor?.isActive({ textAlign: "justify" })}
            onClick={() => editor?.chain().focus().setTextAlign("justify").run()}
          >
            <FormatAlignJustifyIcon fontSize="small" />
          </ToolbarBtn>

          <ToolbarDivider />

          {/* Listas */}
          <ToolbarBtn
            label="Lista con viñetas"
            active={editor?.isActive("bulletList")}
            onClick={() => editor?.chain().focus().toggleBulletList().run()}
          >
            <FormatListBulletedIcon fontSize="small" />
          </ToolbarBtn>
          <ToolbarBtn
            label="Lista numerada"
            active={editor?.isActive("orderedList")}
            onClick={() => editor?.chain().focus().toggleOrderedList().run()}
          >
            <FormatListNumberedIcon fontSize="small" />
          </ToolbarBtn>

          <ToolbarDivider />

          {/* Deshacer / Rehacer */}
          <ToolbarBtn
            label="Deshacer (Ctrl+Z)"
            disabled={!editor?.can().undo()}
            onClick={() => editor?.chain().focus().undo().run()}
          >
            <UndoIcon fontSize="small" />
          </ToolbarBtn>
          <ToolbarBtn
            label="Rehacer (Ctrl+Y)"
            disabled={!editor?.can().redo()}
            onClick={() => editor?.chain().focus().redo().run()}
          >
            <RedoIcon fontSize="small" />
          </ToolbarBtn>
        </Box>
      )}

      {/* ── Área de la hoja A4 ── */}
      <Box
        sx={{
          overflowY: "auto",
          maxHeight: 680,
          bgcolor:
            theme.palette.mode === "dark"
              ? theme.palette.grey[800]
              : "#e8e8e8",
          py: 3,
          px: { xs: 1, sm: 3 },
        }}
      >
        {/* Hoja A4 con sombra */}
        <Box
          sx={{
            maxWidth: 794, // A4 a 96 dpi
            mx: "auto",
            bgcolor: theme.palette.mode === "dark" ? "#1e1e1e" : "#fff",
            boxShadow: "0 4px 20px rgba(0,0,0,0.18), 0 1px 4px rgba(0,0,0,0.08)",
            borderRadius: 0.5,
            // Estilos del contenido Tiptap
            "& .document-sheet": {
              outline: "none",
              minHeight: 500,
              px: { xs: 3, sm: "72px" },
              py: "64px",
              fontFamily: '"Times New Roman", Times, serif',
              fontSize: "12pt",
              lineHeight: 1.8,
              color: theme.palette.mode === "dark" ? "#e0e0e0" : "#1a1a1a",
              // Placeholder
              "& p.is-editor-empty:first-of-type::before": {
                content: "attr(data-placeholder)",
                color: theme.palette.text.disabled,
                float: "left",
                pointerEvents: "none",
                height: 0,
                fontStyle: "italic",
                fontFamily: "inherit",
              },
              // Tipografía de headings tipo documento legal
              "& h1": {
                fontSize: "16pt",
                fontWeight: 700,
                textAlign: "center",
                textTransform: "uppercase",
                letterSpacing: "0.04em",
                mt: 0,
                mb: "12pt",
                lineHeight: 1.4,
              },
              "& h2": {
                fontSize: "13pt",
                fontWeight: 700,
                textDecoration: "underline",
                mt: "18pt",
                mb: "8pt",
                lineHeight: 1.4,
              },
              "& h3": {
                fontSize: "12pt",
                fontWeight: 700,
                fontStyle: "italic",
                mt: "14pt",
                mb: "6pt",
                lineHeight: 1.4,
              },
              "& p": {
                mt: 0,
                mb: "8pt",
                textAlign: "justify",
              },
              "& ul, & ol": {
                pl: "28pt",
                mb: "8pt",
              },
              "& li": {
                mb: "4pt",
              },
              "& strong": { fontWeight: 700 },
              "& em": { fontStyle: "italic" },
              "& u": { textDecoration: "underline" },
            },
          }}
        >
          <EditorContent editor={editor} />
        </Box>
      </Box>

      {/* ── Pie: contador de palabras ── */}
      <Box
        sx={{
          display: "flex",
          justifyContent: "flex-end",
          alignItems: "center",
          gap: 2,
          px: 2,
          py: 0.5,
          bgcolor:
            theme.palette.mode === "dark"
              ? theme.palette.grey[900]
              : theme.palette.grey[100],
          borderTop: `1px solid ${theme.palette.divider}`,
        }}
      >
        <Typography variant="caption" color="text.secondary">
          {wordCount} {wordCount === 1 ? "palabra" : "palabras"}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {charCount} {charCount === 1 ? "carácter" : "caracteres"}
        </Typography>
      </Box>
    </Box>
  );
}
