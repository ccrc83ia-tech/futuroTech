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
