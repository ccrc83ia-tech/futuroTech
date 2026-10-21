package com.futurotech.legal.infrastructure.adapter.out.export;

import com.futurotech.legal.domain.exception.DocumentGenerationException;
import com.futurotech.legal.domain.model.Branding;
import com.futurotech.legal.domain.port.out.FileExporterPort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Escribe el DOCX generado a disco y devuelve el nombre del archivo. */
@Component
public class FileExporterAdapter implements FileExporterPort {

    private final DocxExporter docxExporter;
    private final Path outputDir;

    public FileExporterAdapter(DocxExporter docxExporter,
                               @Value("${docs.output-dir:generated_docs}") String outputDir) {
        this.docxExporter = docxExporter;
        this.outputDir = Path.of(outputDir);
    }

    @Override
    public String export(String content, Branding branding, String fileName, byte[] formatDocBytes) {
        try {
            Files.createDirectories(outputDir);
            byte[] docx = docxExporter.build(content, branding, formatDocBytes);
            Files.write(outputDir.resolve(fileName), docx);
            return fileName;
        } catch (IOException e) {
            throw new DocumentGenerationException("No se pudo guardar el documento", e);
        }
    }
}
