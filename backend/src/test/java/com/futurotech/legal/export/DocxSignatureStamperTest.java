package com.futurotech.legal.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.futurotech.legal.infrastructure.adapter.out.export.DocxSignatureStamper;

/**
 * Verifica el estampado de la firma manuscrita: sin configuración no toca el
 * documento; con una imagen configurada y una línea "Atentamente," inserta la
 * imagen de firma en el .docx.
 */
class DocxSignatureStamperTest {

    @Test
    void withoutConfiguredImageReturnsOriginalBytes() throws Exception {
        DocxSignatureStamper stamper = new DocxSignatureStamper("", 120, 60);
        byte[] docx = docxWith("Atentamente,", "PAULA ANDREA ESCOBAR SANCHEZ");

        byte[] result = stamper.stamp(docx);

        assertThat(stamper.isConfigured()).isFalse();
        assertThat(result).isEqualTo(docx);
        assertThat(countImages(result)).isZero();
    }

    @Test
    void stampsSignatureAfterAtentamente(@TempDir Path tmp) throws Exception {
        // Imagen PNG 1x1 mínima válida.
        Path img = tmp.resolve("firma.png");
        Files.write(img, onePixelPng());

        DocxSignatureStamper stamper = new DocxSignatureStamper(img.toString(), 120, 60);
        byte[] docx = docxWith("Señores", "Atentamente,", "PAULA ANDREA ESCOBAR SANCHEZ");

        byte[] result = stamper.stamp(docx);

        assertThat(stamper.isConfigured()).isTrue();
        assertThat(countImages(result)).isEqualTo(1);
    }

    @Test
    void withoutAnchorReturnsOriginalBytes(@TempDir Path tmp) throws Exception {
        Path img = tmp.resolve("firma.png");
        Files.write(img, onePixelPng());

        DocxSignatureStamper stamper = new DocxSignatureStamper(img.toString(), 120, 60);
        byte[] docx = docxWith("Señores", "COLPENSIONES"); // sin "Atentamente,"

        byte[] result = stamper.stamp(docx);

        assertThat(result).isEqualTo(docx);
        assertThat(countImages(result)).isZero();
    }

    // --- helpers ---

    private static byte[] docxWith(String... paragraphs) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String p : paragraphs) {
                doc.createParagraph().createRun().setText(p);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static int countImages(byte[] docxBytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            return doc.getAllPictures().size();
        }
    }

    /** PNG mínimo (1x1 transparente) para tener una imagen válida en el test. */
    private static byte[] onePixelPng() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
    }
}
