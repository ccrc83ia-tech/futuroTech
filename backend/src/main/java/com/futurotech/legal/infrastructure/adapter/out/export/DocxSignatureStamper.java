package com.futurotech.legal.infrastructure.adapter.out.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Estampa la firma manuscrita escaneada de la firma en un .docx, insertando la
 * imagen justo encima del nombre del firmante (tras la línea "Atentamente,").
 *
 * <p>Se aplica al vuelo en la descarga y SOLO cuando el documento está APROBADO
 * (nunca en borrador), de forma análoga a la marca de agua "BORRADOR": no se
 * modifica el archivo en disco. Así, si se revoca la aprobación, el documento
 * vuelve a descargarse sin firma.
 *
 * <p>Tolerante a fallos: si no hay imagen de firma configurada, la ruta no
 * existe, o el .docx no tiene línea "Atentamente,", devuelve los bytes
 * originales sin modificar para no romper la descarga.
 */
@Component
public class DocxSignatureStamper {

    private static final Logger log = LoggerFactory.getLogger(DocxSignatureStamper.class);

    /** Marcador tras el cual va la firma (insensible a mayúsculas/acentos de cierre). */
    private static final String SIGNATURE_ANCHOR = "atentamente";

    private final String imagePath;
    private final int widthPt;
    private final int heightPt;

    public DocxSignatureStamper(
            @Value("${signature.image-path:}") String imagePath,
            @Value("${signature.width-pt:120}") int widthPt,
            @Value("${signature.height-pt:60}") int heightPt) {
        this.imagePath = imagePath;
        this.widthPt = widthPt;
        this.heightPt = heightPt;
    }

    /** True si hay una imagen de firma configurada y disponible en disco. */
    public boolean isConfigured() {
        return imagePath != null && !imagePath.isBlank() && Files.isReadable(Path.of(imagePath));
    }

    /**
     * Devuelve una copia del .docx con la firma estampada sobre el nombre del
     * firmante. Si no puede aplicarla, devuelve los bytes originales.
     */
    public byte[] stamp(byte[] docxBytes) {
        if (docxBytes == null || docxBytes.length == 0 || !isConfigured()) {
            return docxBytes;
        }
        try {
            byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));
            int pictureType = pictureTypeFor(imagePath);

            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                int anchorIdx = findSignatureAnchorIndex(doc);
                if (anchorIdx < 0) {
                    return docxBytes; // sin punto de anclaje: no se toca el documento
                }

                // Inserta la firma en un párrafo nuevo ENTRE "Atentamente," y la
                // línea siguiente (el nombre del firmante). insertNewParagraph
                // inserta ANTES del cursor dado, por lo que se usa el cursor del
                // párrafo posterior al ancla. Si "Atentamente," es el último
                // párrafo, se añade la firma al final del documento.
                List<XWPFParagraph> paragraphs = doc.getParagraphs();
                XWPFParagraph signaturePara;
                if (anchorIdx + 1 < paragraphs.size()) {
                    signaturePara = doc.insertNewParagraph(
                            paragraphs.get(anchorIdx + 1).getCTP().newCursor());
                } else {
                    signaturePara = doc.createParagraph();
                }

                XWPFRun run = signaturePara.createRun();
                try (ByteArrayInputStream pic = new ByteArrayInputStream(imageBytes)) {
                    run.addPicture(pic, pictureType, "firma",
                            Units.toEMU(widthPt), Units.toEMU(heightPt));
                }

                doc.write(out);
                log.info("Firma estampada en el documento aprobado");
                return out.toByteArray();
            }
        } catch (Exception e) {
            log.warn("No se pudo estampar la firma; se entrega el documento sin firma: {}", e.getMessage());
            return docxBytes;
        }
    }

    /** Índice del primer párrafo cuyo texto contenga "Atentamente"; -1 si no hay. */
    private static int findSignatureAnchorIndex(XWPFDocument doc) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i).getText();
            if (text != null && text.toLowerCase(Locale.ROOT).contains(SIGNATURE_ANCHOR)) {
                return i;
            }
        }
        return -1;
    }

    private static int pictureTypeFor(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return Document.PICTURE_TYPE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return Document.PICTURE_TYPE_GIF;
        }
        // jpg/jpeg y por defecto
        return Document.PICTURE_TYPE_JPEG;
    }
}
