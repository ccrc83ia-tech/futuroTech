package com.futurotech.legal.infrastructure.adapter.out.export;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.model.Branding;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Component;

/** Generación de PDF con membrete usando OpenPDF. */
@Component
public class PdfExporter {

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
