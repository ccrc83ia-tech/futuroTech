package com.futurotech.legal.infrastructure.adapter.out.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Estampa una marca de agua de texto (p. ej. "BORRADOR") en un .docx a nivel del
 * paquete OOXML, insertando un encabezado con un WordArt VML diagonal y
 * referenciándolo desde cada sección del documento. Word muestra así la marca de
 * agua en todas las páginas, igual que la función "Marca de agua" nativa.
 *
 * <p>Opera sobre los bytes del .docx sin alterar el cuerpo ({@code document.xml}
 * solo se toca para añadir el {@code headerReference} en sus {@code sectPr}). Es
 * tolerante a fallos: ante cualquier problema devuelve el documento original sin
 * marca, para no romper la descarga.
 */
@Component
public class DocxWatermarkStamper {

    private static final Logger log = LoggerFactory.getLogger(DocxWatermarkStamper.class);

    private static final String DOC_XML = "word/document.xml";
    private static final String DOC_RELS = "word/_rels/document.xml.rels";
    private static final String CONTENT_TYPES = "[Content_Types].xml";
    private static final String HEADER_PART = "word/wmarkheader1.xml";
    private static final String HEADER_PART_NAME = "/word/wmarkheader1.xml";
    private static final String REL_ID = "rIdWmark1";

    /**
     * Devuelve una copia del .docx con la marca de agua estampada. Si no puede
     * aplicarla, devuelve los bytes originales sin modificar.
     */
    public byte[] stamp(byte[] docxBytes, String text) {
        if (docxBytes == null || docxBytes.length == 0 || text == null || text.isBlank()) {
            return docxBytes;
        }
        try {
            Map<String, byte[]> entries = readZip(docxBytes);
            if (!entries.containsKey(DOC_XML)) {
                return docxBytes; // no es un .docx reconocible
            }

            entries.put(HEADER_PART, headerXml(text).getBytes(StandardCharsets.UTF_8));

            String docRels = entries.containsKey(DOC_RELS)
                    ? new String(entries.get(DOC_RELS), StandardCharsets.UTF_8)
                    : "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                      + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                      + "</Relationships>";
            entries.put(DOC_RELS, addHeaderRelationship(docRels).getBytes(StandardCharsets.UTF_8));

            String contentTypes = new String(entries.get(CONTENT_TYPES), StandardCharsets.UTF_8);
            entries.put(CONTENT_TYPES, addHeaderContentType(contentTypes).getBytes(StandardCharsets.UTF_8));

            String docXml = new String(entries.get(DOC_XML), StandardCharsets.UTF_8);
            entries.put(DOC_XML, addHeaderReferences(docXml).getBytes(StandardCharsets.UTF_8));

            return writeZip(entries);
        } catch (Exception e) {
            log.warn("No se pudo estampar la marca de agua en el DOCX; se entrega sin marca: {}", e.getMessage());
            return docxBytes;
        }
    }

    private static Map<String, byte[]> readZip(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    entries.put(e.getName(), zin.readAllBytes());
                }
            }
        }
        return entries;
    }

    private static byte[] writeZip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zout = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> en : entries.entrySet()) {
                zout.putNextEntry(new ZipEntry(en.getKey()));
                zout.write(en.getValue());
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** Añade la relación del encabezado de marca de agua a document.xml.rels. */
    private static String addHeaderRelationship(String rels) {
        if (rels.contains(REL_ID)) {
            return rels;
        }
        String rel = "<Relationship Id=\"" + REL_ID + "\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/header\" "
                + "Target=\"wmarkheader1.xml\"/>";
        return rels.replace("</Relationships>", rel + "</Relationships>");
    }

    /** Registra el content-type del nuevo part de encabezado. */
    private static String addHeaderContentType(String contentTypes) {
        if (contentTypes.contains(HEADER_PART_NAME)) {
            return contentTypes;
        }
        String override = "<Override PartName=\"" + HEADER_PART_NAME + "\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml\"/>";
        return contentTypes.replace("</Types>", override + "</Types>");
    }

    /**
     * Inserta un {@code <w:headerReference>} al inicio de cada {@code <w:sectPr>}
     * del cuerpo para que la marca de agua aplique a todas las secciones/páginas.
     */
    private static String addHeaderReferences(String docXml) {
        String ref = "<w:headerReference w:type=\"default\" r:id=\"" + REL_ID + "\"/>";
        // Evita duplicar si ya se estampó.
        if (docXml.contains(REL_ID)) {
            return docXml;
        }
        // Inserta la referencia justo tras la apertura de cada sectPr (con o sin atributos).
        String result = docXml.replaceAll("(<w:sectPr\\b[^>]*>)", "$1" + ref);
        // Si el sectPr viene auto-cerrado (<w:sectPr/>), conviértelo en par con la referencia.
        result = result.replaceAll("<w:sectPr\\b([^>]*)/>", "<w:sectPr$1>" + ref + "</w:sectPr>");
        return result;
    }

    /** Encabezado con el WordArt VML diagonal que Word interpreta como marca de agua. */
    private static String headerXml(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:hdr xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                + "xmlns:v=\"urn:schemas-microsoft-com:vml\" "
                + "xmlns:o=\"urn:schemas-microsoft-com:office:office\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<w:p><w:r><w:pict>"
                + "<v:shapetype id=\"_x0000_t136\" coordsize=\"21600,21600\" o:spt=\"136\" "
                + "adj=\"10800\" path=\"m@7,l@8,m@5,21600l@11,21600e\">"
                + "<v:formulas><v:f eqn=\"sum @0 0 0\"/></v:formulas>"
                + "<v:path textpathok=\"t\" o:connecttype=\"custom\"/>"
                + "<v:textpath on=\"t\" fitshape=\"t\"/></v:shapetype>"
                + "<v:shape id=\"wmark1\" type=\"#_x0000_t136\" "
                + "style=\"position:absolute;margin-left:0;margin-top:0;width:468pt;height:234pt;"
                + "rotation:315;z-index:-251654144;mso-position-horizontal:center;"
                + "mso-position-horizontal-relative:margin;mso-position-vertical:center;"
                + "mso-position-vertical-relative:margin\" "
                + "fillcolor=\"#c0c0c0\" stroked=\"f\">"
                + "<v:fill opacity=\".3\"/>"
                + "<v:textpath style=\"font-family:&quot;Calibri&quot;;font-size:1pt\" string=\"" + safe + "\"/>"
                + "</v:shape></w:pict></w:r></w:p>"
                + "</w:hdr>";
    }
}
