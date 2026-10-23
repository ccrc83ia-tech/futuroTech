package com.futurotech.legal.infrastructure.adapter.out.export;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.model.Branding;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.springframework.stereotype.Component;

/**
 * Generación de DOCX. Dos modos:
 *  - Con documento modelo: preservación byte-a-byte — se reemplaza solo el cuerpo
 *    (word/document.xml) y se conserva intacto el resto del paquete OOXML (membrete,
 *    logos, estilos, encabezados, pies).
 *  - Sin documento modelo: construcción con branding de la firma (Apache POI).
 */
@Component
public class DocxExporter {

    private final LogoResolver logoResolver;

    public DocxExporter(LogoResolver logoResolver) {
        this.logoResolver = logoResolver;
    }

    /** Constructor sin dependencias (útil en tests); resuelve el logo por convención. */
    public DocxExporter() {
        this(new LogoResolver());
    }

    private static final String DOC_XML = "word/document.xml";
    private static final Pattern INLINE = Pattern.compile(
            "(\\*\\*\\*(.+?)\\*\\*\\*|\\*\\*(.+?)\\*\\*|\\*(.+?)\\*)");

    /** Estilo de cuerpo de las minutas de la firma: fuente Cambria, 14 pt, texto justificado. */
    private static final String BODY_FONT = "Cambria";
    private static final int BODY_FONT_SIZE = 14;

    public byte[] build(String content, Branding branding, byte[] formatDocBytes) {
        if (formatDocBytes != null && formatDocBytes.length > 0) {
            return buildFromFormat(content, formatDocBytes);
        }
        return buildBranded(content, branding);
    }

    /**
     * Reemplaza el cuerpo del documento modelo conservando todo lo demás.
     * Genera los párrafos nuevos con POI y sustituye el contenido de &lt;w:body&gt;,
     * preservando el &lt;w:sectPr&gt; final (configuración de sección/márgenes).
     */
    private byte[] buildFromFormat(String content, byte[] formatDocBytes) {
        try {
            // 1. Construir el XML del cuerpo nuevo a partir de un doc POI temporal.
            String newBodyInner = buildBodyInnerXml(content);

            // 2. Reempaquetar el .docx original reemplazando solo word/document.xml.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(formatDocBytes));
                 ZipOutputStream zout = new ZipOutputStream(out)) {
                ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    byte[] data = zin.readAllBytes();
                    zout.putNextEntry(new ZipEntry(entry.getName()));
                    if (DOC_XML.equals(entry.getName())) {
                        String original = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                        zout.write(replaceBody(original, newBodyInner)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } else {
                        zout.write(data);
                    }
                    zout.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new DocumentGenerationException("Error al preservar el formato del documento modelo", e);
        }
    }

    /** Extrae el XML interno del &lt;w:body&gt; de un documento POI construido con el contenido. */
    private String buildBodyInnerXml(String content) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            addFormattedBody(doc, content);
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            doc.write(tmp);
            // Leer el document.xml generado por POI y extraer el interior del body (sin sectPr).
            try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(tmp.toByteArray()))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (DOC_XML.equals(e.getName())) {
                        String xml = new String(zin.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        return extractBodyInner(xml, false);
                    }
                }
            }
        }
        return "";
    }

    private static String extractBodyInner(String docXml, boolean keepSectPr) {
        int start = docXml.indexOf("<w:body>");
        int end = docXml.lastIndexOf("</w:body>");
        if (start < 0 || end < 0) {
            return "";
        }
        String inner = docXml.substring(start + "<w:body>".length(), end);
        if (!keepSectPr) {
            int sect = inner.lastIndexOf("<w:sectPr");
            if (sect >= 0) {
                inner = inner.substring(0, sect);
            }
        }
        return inner;
    }

    /** Reemplaza el interior del body del XML original, preservando su sectPr. */
    private static String replaceBody(String originalDocXml, String newBodyInner) {
        int start = originalDocXml.indexOf("<w:body>");
        int end = originalDocXml.lastIndexOf("</w:body>");
        if (start < 0 || end < 0) {
            return originalDocXml; // formato inesperado: no tocar
        }
        String originalInner = originalDocXml.substring(start + "<w:body>".length(), end);
        int sect = originalInner.lastIndexOf("<w:sectPr");
        String sectPr = sect >= 0 ? originalInner.substring(sect) : "";
        return originalDocXml.substring(0, start + "<w:body>".length())
                + newBodyInner + sectPr
                + originalDocXml.substring(end);
    }

    private byte[] buildBranded(String content, Branding branding) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            applyLetterhead(doc, branding);
            addFormattedBody(doc, content);
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new DocumentGenerationException("Error al generar el DOCX", e);
        }
    }

    /**
     * Estampa el membrete de la firma como encabezado y pie de sección reales
     * (se repiten en todas las páginas), usando los datos del branding del
     * tenant: nombre/encabezado, color corporativo, NIT, dirección, teléfono,
     * correo y sitio web; y el pie de página. Si no hay branding no hace nada.
     */
    private void applyLetterhead(XWPFDocument doc, Branding branding) {
        if (branding == null) {
            return;
        }

        String title = branding.headerText();
        byte[] logo = logoResolver.resolve(branding);

        if ((title != null && !title.isBlank()) || logo != null) {
            XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
            if (logo != null) {
                buildHeaderWithLogo(header, title, logo, branding.primaryColor());
            } else {
                buildHeaderTextOnly(header, title, branding.primaryColor());
            }
        }

        // ── Pie de página: direcciones de oficinas, web y línea WhatsApp ──
        String footerText = branding.footerText();
        if (footerText != null && !footerText.isBlank()) {
            XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
            // Cada segmento separado por " | " en el seed va en su propia línea,
            // como en las minutas reales de la firma (pie a tres renglones).
            String[] lines = footerText.split("\\s*\\|\\s*");
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                XWPFParagraph fp = footer.createParagraph();
                fp.setAlignment(ParagraphAlignment.CENTER);
                fp.setSpacingAfter(0);
                XWPFRun fr = fp.createRun();
                fr.setText(line.strip());
                fr.setFontFamily(BODY_FONT);
                fr.setFontSize(8);
            }
        }
    }

    /** Encabezado a dos columnas: logo a la izquierda, nombre de la firma a la derecha. */
    private void buildHeaderWithLogo(XWPFHeader header, String title, byte[] logo, String primaryColor) {
        XWPFTable table = header.createTable(1, 2);
        table.setWidth("100%");
        removeTableBorders(table);

        XWPFTableRow row = table.getRow(0);

        // Celda izquierda: logo.
        XWPFParagraph logoPar = row.getCell(0).getParagraphs().get(0);
        logoPar.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun logoRun = logoPar.createRun();
        try {
            logoRun.addPicture(
                    new ByteArrayInputStream(logo),
                    Document.PICTURE_TYPE_PNG,
                    "logo.png",
                    Units.toEMU(150), Units.toEMU(48));
        } catch (Exception e) {
            // Si el logo no se puede incrustar, se continúa sin él.
        }

        // Celda derecha: nombre de la firma.
        XWPFTableCell right = row.getCell(1);
        XWPFParagraph titlePar = right.getParagraphs().get(0);
        titlePar.setAlignment(ParagraphAlignment.RIGHT);
        if (title != null && !title.isBlank()) {
            XWPFRun tr = titlePar.createRun();
            tr.setText(title);
            tr.setFontFamily(BODY_FONT);
            tr.setBold(true);
            tr.setFontSize(13);
            String color = hexWithoutHash(primaryColor);
            if (color != null) {
                tr.setColor(color);
            }
        }
    }

    /** Encabezado solo texto (cuando no hay logo disponible). */
    private void buildHeaderTextOnly(XWPFHeader header, String title, String primaryColor) {
        if (title == null || title.isBlank()) {
            return;
        }
        XWPFParagraph hp = header.createParagraph();
        hp.setAlignment(ParagraphAlignment.CENTER);
        hp.setSpacingAfter(120);
        XWPFRun hr = hp.createRun();
        hr.setText(title);
        hr.setFontFamily(BODY_FONT);
        hr.setBold(true);
        hr.setFontSize(14);
        String color = hexWithoutHash(primaryColor);
        if (color != null) {
            hr.setColor(color);
        }
    }

    /** Quita los bordes visibles de la tabla del encabezado. */
    private static void removeTableBorders(XWPFTable table) {
        var ctTable = table.getCTTbl();
        var props = ctTable.getTblPr() != null ? ctTable.getTblPr() : ctTable.addNewTblPr();
        var borders = props.isSetTblBorders() ? props.getTblBorders() : props.addNewTblBorders();
        (borders.isSetTop() ? borders.getTop() : borders.addNewTop()).setVal(STBorder.NONE);
        (borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom()).setVal(STBorder.NONE);
        (borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft()).setVal(STBorder.NONE);
        (borders.isSetRight() ? borders.getRight() : borders.addNewRight()).setVal(STBorder.NONE);
        (borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH()).setVal(STBorder.NONE);
        (borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV()).setVal(STBorder.NONE);
    }

    /** Devuelve el color hex sin '#' (POI lo espera así), o null si no es válido. */
    private static String hexWithoutHash(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            return null;
        }
        return hex.substring(1);
    }

    /**
     * Añade el cuerpo parseando markdown inline (**negrita**, *cursiva*).
     * Cada párrafo se emite con el estilo de las minutas de la firma: justificado
     * y sin espacio posterior entre párrafos (el salto de línea lo da el propio
     * texto de la IA, igual que en los modelos reales).
     */
    private void addFormattedBody(XWPFDocument doc, String content) {
        String clean = stripMarkdownFences(content);
        for (String line : clean.split("\n")) {
            XWPFParagraph p = doc.createParagraph();
            applyBodyParagraphStyle(p);
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            addInlineRuns(p, stripped);
        }
    }

    private void addInlineRuns(XWPFParagraph p, String text) {
        Matcher m = INLINE.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                applyBodyRunStyle(p.createRun()).setText(text.substring(last, m.start()));
            }
            if (m.group(2) != null) {
                XWPFRun r = applyBodyRunStyle(p.createRun());
                r.setText(m.group(2));
                r.setBold(true);
                r.setItalic(true);
            } else if (m.group(3) != null) {
                XWPFRun r = applyBodyRunStyle(p.createRun());
                r.setText(m.group(3));
                r.setBold(true);
            } else if (m.group(4) != null) {
                XWPFRun r = applyBodyRunStyle(p.createRun());
                r.setText(m.group(4));
                r.setItalic(true);
            }
            last = m.end();
        }
        if (last < text.length()) {
            applyBodyRunStyle(p.createRun()).setText(text.substring(last));
        }
    }

    /** Aplica fuente Cambria 14 pt a un run, replicando el cuerpo de las minutas. */
    private static XWPFRun applyBodyRunStyle(XWPFRun run) {
        run.setFontFamily(BODY_FONT);
        run.setFontSize(BODY_FONT_SIZE);
        return run;
    }

    /** Justifica el párrafo y elimina el espacio posterior, como en las minutas. */
    private static void applyBodyParagraphStyle(XWPFParagraph p) {
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setSpacingAfter(0);
    }

    static String stripMarkdownFences(String text) {
        String t = text.strip();
        if (t.startsWith("```")) {
            int nl = t.indexOf("\n");
            if (nl != -1) {
                t = t.substring(nl + 1);
            }
        }
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3).strip();
        }
        return t;
    }
}
