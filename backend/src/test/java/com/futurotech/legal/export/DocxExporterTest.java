package com.futurotech.legal.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import com.futurotech.legal.domain.model.Branding;
import com.futurotech.legal.infrastructure.adapter.out.export.DocxExporter;

/**
 * Verifica que el cuerpo de los DOCX generados sale con el estilo de las minutas
 * de la firma: fuente Cambria, 14 pt y párrafos justificados, tanto en el modo
 * "branded" (sin documento modelo) como en el modo "format-doc" (con modelo,
 * preservando su membrete).
 */
class DocxExporterTest {

    private final DocxExporter exporter = new DocxExporter();

    @Test
    void brandedBodyUsesCambria14Justified() throws Exception {
        byte[] out = exporter.build("Señores\nCOLPENSIONES\nAtentamente,", Branding.empty(), null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(out))) {
            List<XWPFParagraph> bodyParas = doc.getParagraphs().stream()
                    .filter(p -> p.getText() != null && !p.getText().isBlank())
                    .toList();
            assertThat(bodyParas).isNotEmpty();
            assertThat(bodyParas).allSatisfy(DocxExporterTest::assertCambria14Justified);
        }
    }

    @Test
    void formatDocBodyUsesCambria14JustifiedAndPreservesHeader() throws Exception {
        byte[] model = buildModelWithHeader("MEMBRETE DE LA FIRMA");

        byte[] out = exporter.build("Señores\nCOLPENSIONES\nAtentamente,", Branding.empty(), model);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(out))) {
            // El cuerpo nuevo va en Cambria 14 justificado.
            List<XWPFParagraph> bodyParas = doc.getParagraphs().stream()
                    .filter(p -> p.getText() != null && !p.getText().isBlank())
                    .toList();
            assertThat(bodyParas).isNotEmpty();
            assertThat(bodyParas).allSatisfy(DocxExporterTest::assertCambria14Justified);

            // El membrete del modelo se preserva.
            boolean headerPreserved = doc.getHeaderList().stream()
                    .map(XWPFHeader::getText)
                    .anyMatch(t -> t != null && t.contains("MEMBRETE DE LA FIRMA"));
            assertThat(headerPreserved).isTrue();
        }
    }

    @Test
    void brandedModeStampsFirmLetterheadAsSectionHeaderAndFooter() throws Exception {
        Branding branding = new Branding(
                null,                       // logoUrl (sin logo → encabezado solo texto)
                "TUFUTURO — Abogados en Pensiones", // headerText
                "Cra. 46 N.º 52-36 Oficina 501 Medellín | Calle 20 N.º 6-30 Pereira | www.tufuturo.co", // footerText
                "#1a1a1a",                  // primaryColor
                "900.123.456-7",            // nit
                "Cra. 46 N.º 52-36, Medellín", // address
                "514-11-87",                // phone
                "gerencia@tufuturo.co",     // email
                "www.tufuturo.co");         // website

        byte[] out = exporter.build("Señores\nCOLPENSIONES\nAtentamente,", branding, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(out))) {
            // Encabezado de sección con el nombre de la firma.
            String headerText = doc.getHeaderList().stream()
                    .map(XWPFHeader::getText)
                    .reduce("", String::concat);
            assertThat(headerText).contains("TUFUTURO — Abogados en Pensiones");

            // Pie de página de sección: cada oficina en su propio renglón.
            String footerText = doc.getFooterList().stream()
                    .map(f -> f.getText())
                    .reduce("", String::concat);
            assertThat(footerText).contains("Cra. 46 N.º 52-36 Oficina 501 Medellín");
            assertThat(footerText).contains("Calle 20 N.º 6-30 Pereira");
            assertThat(footerText).contains("www.tufuturo.co");
            // El separador " | " del seed no debe aparecer: se parte en renglones.
            assertThat(footerText).doesNotContain("|");

            // El logo de la firma (classpath:branding/logo.png) se incrusta en el
            // encabezado como imagen.
            boolean hasLogo = doc.getHeaderList().stream()
                    .anyMatch(h -> !h.getAllPictures().isEmpty());
            assertThat(hasLogo).as("el encabezado debe incrustar el logo de la firma").isTrue();

            // El cuerpo sigue en Cambria 14 justificado.
            List<XWPFParagraph> bodyParas = doc.getParagraphs().stream()
                    .filter(p -> p.getText() != null && !p.getText().isBlank())
                    .toList();
            assertThat(bodyParas).isNotEmpty();
            assertThat(bodyParas).allSatisfy(DocxExporterTest::assertCambria14Justified);
        }
    }

    private static void assertCambria14Justified(XWPFParagraph p) {
        assertThat(p.getAlignment()).isEqualTo(ParagraphAlignment.BOTH);
        for (XWPFRun run : p.getRuns()) {
            assertThat(run.getFontFamily()).isEqualTo("Cambria");
            assertThat(run.getFontSizeAsDouble()).isEqualTo(14.0);
        }
    }

    /** Construye un .docx modelo mínimo con un encabezado (membrete) para el modo format-doc. */
    private static byte[] buildModelWithHeader(String headerText) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFHeaderFooterPolicy policy = doc.createHeaderFooterPolicy();
            XWPFHeader header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT);
            header.createParagraph().createRun().setText(headerText);

            // Un párrafo de cuerpo cualquiera (será reemplazado por el exportador).
            doc.createParagraph().createRun().setText("Cuerpo original del modelo");
            doc.write(out);
            return out.toByteArray();
        }
    }
}
