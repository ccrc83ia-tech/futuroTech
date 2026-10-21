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
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
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

    private static final String DOC_XML = "word/document.xml";
    private static final Pattern INLINE = Pattern.compile(
            "(\\*\\*\\*(.+?)\\*\\*\\*|\\*\\*(.+?)\\*\\*|\\*(.+?)\\*)");

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
            if (branding != null && branding.headerText() != null && !branding.headerText().isBlank()) {
                XWPFParagraph h = doc.createParagraph();
                h.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun r = h.createRun();
                r.setText(branding.headerText());
                r.setBold(true);
                r.setFontSize(14);
            }
            addFormattedBody(doc, content);
            if (branding != null && branding.footerText() != null && !branding.footerText().isBlank()) {
                XWPFParagraph f = doc.createParagraph();
                f.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun r = f.createRun();
                r.setText(branding.footerText());
                r.setItalic(true);
                r.setFontSize(8);
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new DocumentGenerationException("Error al generar el DOCX", e);
        }
    }

    /** Añade el cuerpo parseando markdown inline (**negrita**, *cursiva*). */
    private void addFormattedBody(XWPFDocument doc, String content) {
        String clean = stripMarkdownFences(content);
        for (String line : clean.split("\n")) {
            XWPFParagraph p = doc.createParagraph();
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
                p.createRun().setText(text.substring(last, m.start()));
            }
            if (m.group(2) != null) {
                XWPFRun r = p.createRun();
                r.setText(m.group(2));
                r.setBold(true);
                r.setItalic(true);
            } else if (m.group(3) != null) {
                XWPFRun r = p.createRun();
                r.setText(m.group(3));
                r.setBold(true);
            } else if (m.group(4) != null) {
                XWPFRun r = p.createRun();
                r.setText(m.group(4));
                r.setItalic(true);
            }
            last = m.end();
        }
        if (last < text.length()) {
            p.createRun().setText(text.substring(last));
        }
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
