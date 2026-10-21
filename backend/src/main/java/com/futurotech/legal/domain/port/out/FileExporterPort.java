package com.futurotech.legal.domain.port.out;

import com.futurotech.legal.domain.model.Branding;

/** Generación de archivos de documento (.docx, .pdf). */
public interface FileExporterPort {

    /**
     * Exporta el contenido a un archivo.
     *
     * @param content        texto generado por la IA
     * @param branding       branding de la firma (usado si no hay documento modelo)
     * @param fileName       nombre del archivo destino
     * @param formatDocBytes documento modelo .docx (si existe, se preserva su formato byte-a-byte)
     * @return la ruta/nombre del archivo generado
     */
    String export(String content, Branding branding, String fileName, byte[] formatDocBytes);
}
