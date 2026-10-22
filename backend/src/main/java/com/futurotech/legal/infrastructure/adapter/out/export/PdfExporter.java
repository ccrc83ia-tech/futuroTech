package com.futurotech.legal.infrastructure.adapter.out.export;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.model.Branding;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

/** Generación de PDF con membrete usando OpenPDF. */
@Component
public class PdfExporter {

    /**
     * Construye un PDF a partir de los bytes de un .docx ya generado, extrayendo
     * su texto y aplicando el membrete de la firma. Permite ofrecer descarga en
     * PDF sin volver a invocar a la IA ni almacenar un segundo artefacto.
     */
    public byte[] buildFromDocx(byte[] docxBytes, Branding branding) {
        String text;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            text = extractor.getText();
        } catch (Exception e) {
            throw new DocumentGenerationException("No se pudo convertir el documento a PDF", e);
        }
        return build(text, branding);
    }

    public byte[] build(String content, Branding branding) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();

            if (branding != null && branding.headerText() != null && !branding.headerText().isBlank()) {
                Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.decode(
                        safeColor(branding.primaryColor())));
                Paragraph header = new Paragraph(branding.headerText(), headerFont);
                header.setAlignment(Element.ALIGN_CENTER);
                doc.add(header);
                doc.add(new Paragraph(" "));
            }

            Font body = FontFactory.getFont(FontFactory.HELVETICA, 11);
            for (String line : DocxExporter.stripMarkdownFences(content).split("\n")) {
                String stripped = line.strip();
                doc.add(new Paragraph(stripped.isEmpty() ? " " : stripReplacedMarkdown(stripped), body));
            }

            if (branding != null && branding.footerText() != null && !branding.footerText().isBlank()) {
                doc.add(new Paragraph(" "));
                Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);
                Paragraph footer = new Paragraph(branding.footerText(), footerFont);
                footer.setAlignment(Element.ALIGN_CENTER);
                doc.add(footer);
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new DocumentGenerationException("Error al generar el PDF", e);
        }
    }

    private static String stripReplacedMarkdown(String text) {
        return text.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "$1")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1");
    }

    private static String safeColor(String hex) {
        return (hex != null && hex.matches("#[0-9a-fA-F]{6}")) ? hex : "#000000";
    }
}
