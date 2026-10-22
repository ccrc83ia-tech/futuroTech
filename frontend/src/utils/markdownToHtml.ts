/**
 * Convierte el texto en formato Markdown simple (tal como lo devuelve
 * GET /documents/{id}/text) a HTML compatible con Tiptap.
 *
 * Soporta:
 *   - Líneas que empiezan con #, ##, ### → h1/h2/h3
 *   - **negrita**, *cursiva*, ***negrita+cursiva***
 *   - Líneas en blanco → separador de párrafo
 *   - El aviso ⚠️ BORRADOR se preserva como primer párrafo
 */
export function markdownToHtml(md: string): string {
  // Separar en bloques por línea en blanco
  const rawLines = md.split("\n");
  const blocks: string[] = [];
  let current: string[] = [];

  for (const line of rawLines) {
    if (line.trim() === "") {
      if (current.length > 0) {
        blocks.push(current.join(" "));
        current = [];
      } else {
        // Línea en blanco extra → párrafo vacío para espaciado
        blocks.push("");
      }
    } else {
      current.push(line);
    }
  }
  if (current.length > 0) blocks.push(current.join(" "));

  const htmlBlocks = blocks.map((block) => {
    if (block === "") return "<p></p>";

    // Headings
    const h3 = block.match(/^###\s+(.*)/);
    if (h3) return `<h3>${inlineFormat(h3[1])}</h3>`;
    const h2 = block.match(/^##\s+(.*)/);
    if (h2) return `<h2>${inlineFormat(h2[1])}</h2>`;
    const h1 = block.match(/^#\s+(.*)/);
    if (h1) return `<h1>${inlineFormat(h1[1])}</h1>`;

    return `<p>${inlineFormat(block)}</p>`;
  });

  return htmlBlocks.join("");
}

/** Aplica formato inline: negrita, cursiva. */
function inlineFormat(text: string): string {
  // Escapar caracteres HTML primero
  let t = text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");

  // ***negrita+cursiva***
  t = t.replace(/\*\*\*(.+?)\*\*\*/g, "<strong><em>$1</em></strong>");
  // **negrita**
  t = t.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
  // *cursiva*
  t = t.replace(/\*(.+?)\*/g, "<em>$1</em>");

  return t;
}

/** Extrae el contenido de texto plano del HTML del editor Tiptap. */
export function htmlToPlainText(html: string): string {
  // Reemplazar etiquetas de bloque por saltos de línea antes de quitar tags
  return html
    .replace(/<\/p>/gi, "\n")
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/h[123456]>/gi, "\n")
    .replace(/<li>/gi, "• ")
    .replace(/<\/li>/gi, "\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&nbsp;/g, " ")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}
