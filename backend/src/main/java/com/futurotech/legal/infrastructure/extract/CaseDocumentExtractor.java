package com.futurotech.legal.infrastructure.extract;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.model.CaseAttachment;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Extrae texto plano de los documentos del caso aportados al generar
 * (PDF, DOCX, TXT). El texto se inyecta luego en el prompt como hechos del caso.
 *
 * <p>Valida tipo y tamaño. No persiste nada: devuelve un {@link CaseAttachment}
 * efímero con el nombre del archivo y su texto.
 */
@Component
public class CaseDocumentExtractor {

    private static final Logger log = LoggerFactory.getLogger(CaseDocumentExtractor.class);

    /** Límite por archivo para evitar prompts gigantes / abusos de memoria. */
    private final int maxFileMb;

    public CaseDocumentExtractor(@Value("${rag.max-file-mb:20}") int maxFileMb) {
        this.maxFileMb = maxFileMb;
    }

    /**
     * Extrae el texto de un archivo subido. Lanza {@link DocumentGenerationException}
     * si el tipo no es soportado, el archivo excede el tamaño o está vacío/corrupto.
     */
    public CaseAttachment extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentGenerationException("Archivo adjunto vacío");
        }
        String name = file.getOriginalFilename() == null ? "documento" : file.getOriginalFilename();

        long maxBytes = (long) maxFileMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new DocumentGenerationException(
                    "El archivo '" + name + "' supera el tamaño máximo de " + maxFileMb + " MB");
        }

        String lower = name.toLowerCase(Locale.ROOT);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new DocumentGenerationException("No se pudo leer el archivo '" + name + "'");
        }

        String text;
        if (lower.endsWith(".pdf")) {
            text = fromPdf(bytes, name);
        } else if (lower.endsWith(".docx")) {
            text = fromDocx(bytes, name);
        } else if (lower.endsWith(".txt")) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else {
            throw new DocumentGenerationException(
                    "Tipo de archivo no soportado: '" + name + "'. Use PDF, DOCX o TXT.");
        }

        text = normalize(text);
        if (text.isBlank()) {
            throw new DocumentGenerationException(
                    "No se pudo extraer texto de '" + name + "' (¿documento escaneado o vacío?)");
        }
        log.info("Adjunto del caso extraído: '{}' ({} chars)", name, text.length());
        return new CaseAttachment(name, text);
    }

    private String fromPdf(byte[] bytes, String name) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(pdf);
        } catch (Exception e) {
            log.warn("No se pudo leer el PDF '{}': {}", name, e.getMessage());
            throw new DocumentGenerationException("No se pudo leer el PDF '" + name + "'");
        }
    }

    private String fromDocx(byte[] bytes, String name) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (Exception e) {
            log.warn("No se pudo leer el DOCX '{}': {}", name, e.getMessage());
            throw new DocumentGenerationException("No se pudo leer el DOCX '" + name + "'");
        }
    }

    /** Colapsa líneas en blanco excesivas y recorta. */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }
}
