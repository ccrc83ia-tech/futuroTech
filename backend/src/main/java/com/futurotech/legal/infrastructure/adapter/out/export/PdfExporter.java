package com.futurotech.legal.infrastructure.adapter.out.export;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.model.Branding;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

/** Generación de PDF con membrete usando OpenPDF. */
@Component
public class PdfExporter {

    private final LogoResolver logoResolver;

    public PdfExporter(LogoResolver logoResolver) {
        this.logoResolver = logoResolver;
    }

    /** Constructor sin dependencias (útil en tests); resuelve el logo por convención. */
    public PdfExporter() {
        this(new LogoResolver());
    }

    /**
     * Construye un PDF a partir de los bytes de un .docx ya generado, extrayendo
     * su texto y aplicando el membrete de la firma. Permite ofrecer descarga en
     * PDF sin volver a invocar a la IA ni almacenar un segundo artefacto.
     */
    public byte[] buildFromDocx(byte[] docxBytes, Branding branding) {
        return buildFromDocx(docxBytes, branding, null);
    }

    /**
     * Igual que {@link #buildFromDocx(byte[], Branding)} pero estampa una marca de
     * agua diagonal en cada página cuando {@code watermark} no es nulo/vacío
     * (p. ej. "BORRADOR" mientras el documento no esté aprobado).
     */
    public byte[] buildFromDocx(byte[] docxBytes, Branding branding, String watermark) {
        String text;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            text = extractor.getText();
        } catch (Exception e) {
            throw new DocumentGenerationException("No se pudo convertir el documento a PDF", e);
        }
        return build(text, branding, watermark);
    }

    public byte[] build(String content, Branding branding) {
        return build(content, branding, null);
    }

    public byte[] build(String content, Branding branding, String watermark) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            if (watermark != null && !watermark.isBlank()) {
                writer.setPageEvent(new WatermarkEvent(watermark));
            }
            doc.open();

            if (branding != null) {
                addLetterhead(doc, branding);
            }

            // Cuerpo con fuente serif justificada, coherente con el estilo Cambria 14
            // de las minutas (OpenPDF no incluye Cambria; Times New Roman es el
            // equivalente serif estándar disponible en los visores de PDF).
            Font body = FontFactory.getFont(FontFactory.TIMES_ROMAN, 12);
            for (String line : DocxExporter.stripMarkdownFences(content).split("\n")) {
                String stripped = line.strip();
                Paragraph bodyParagraph = new Paragraph(
                        stripped.isEmpty() ? " " : stripReplacedMarkdown(stripped), body);
                bodyParagraph.setAlignment(Element.ALIGN_JUSTIFIED);
                doc.add(bodyParagraph);
            }

            if (branding != null && branding.footerText() != null && !branding.footerText().isBlank()) {
                doc.add(new Paragraph(" "));
                Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
                // Cada oficina/segmento (separado por " | ") en su propio renglón.
                for (String line : branding.footerText().split("\\s*\\|\\s*")) {
                    if (line.isBlank()) {
                        continue;
                    }
                    Paragraph footer = new Paragraph(line.strip(), footerFont);
                    footer.setAlignment(Element.ALIGN_CENTER);
                    doc.add(footer);
                }
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new DocumentGenerationException("Error al generar el PDF", e);
        }
    }

    /**
     * Estampa el membrete en el PDF: logo a la izquierda y nombre de la firma a
     * la derecha (en color corporativo) dentro de una tabla sin bordes, con una
     * línea divisoria debajo. Si no hay logo, el nombre va centrado.
     */
    private void addLetterhead(Document doc, Branding branding) throws Exception {
        byte[] logo = logoResolver.resolve(branding);
        String title = branding.headerText();
        boolean hasTitle = title != null && !title.isBlank();
        if (logo == null && !hasTitle) {
            return;
        }

        Font titleFont = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD, 14, Color.decode(safeColor(branding.primaryColor())));

        if (logo != null) {
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 1f, 2f });

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Image img = Image.getInstance(logo);
            img.scaleToFit(150, 48);
            logoCell.addElement(img);
            table.addCell(logoCell);

            PdfPCell titleCell = new PdfPCell();
            titleCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            titleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            if (hasTitle) {
                Paragraph p = new Paragraph(title, titleFont);
                p.setAlignment(Element.ALIGN_RIGHT);
                titleCell.addElement(p);
            }
            table.addCell(titleCell);
            doc.add(table);
        } else {
            Paragraph header = new Paragraph(title, titleFont);
            header.setAlignment(Element.ALIGN_CENTER);
            doc.add(header);
        }

        LineSeparator rule = new LineSeparator(
                0.5f, 100, Color.decode(safeColor(branding.primaryColor())), Element.ALIGN_CENTER, -4);
        doc.add(new Chunk(rule));
        doc.add(new Paragraph(" "));
    }

    private static String stripReplacedMarkdown(String text) {
        return text.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "$1")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1");
    }

    private static String safeColor(String hex) {
        return (hex != null && hex.matches("#[0-9a-fA-F]{6}")) ? hex : "#000000";
    }

    /**
     * Estampa una marca de agua diagonal, gris y translúcida, centrada en cada
     * página. Se usa para rotular los borradores ("BORRADOR") de forma que quede
     * claro que el documento aún no fue aprobado por un abogado.
     */
    private static final class WatermarkEvent extends PdfPageEventHelper {

        private final String text;

        private WatermarkEvent(String text) {
            this.text = text;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContentUnder();
            canvas.saveState();
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(0.18f);
            canvas.setGState(gs);
            Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 72, new Color(150, 150, 150));
            float x = (document.getPageSize().getLeft() + document.getPageSize().getRight()) / 2;
            float y = (document.getPageSize().getTop() + document.getPageSize().getBottom()) / 2;
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    canvas, Element.ALIGN_CENTER, new Phrase(text, font), x, y, 45);
            canvas.restoreState();
        }
    }
}
