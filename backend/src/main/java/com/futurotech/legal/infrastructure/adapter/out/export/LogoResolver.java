package com.futurotech.legal.infrastructure.adapter.out.export;

import com.futurotech.legal.domain.model.Branding;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Resuelve los bytes del logo de la firma para estamparlo en el membrete.
 *
 * El logo puede provenir de:
 *  - {@code branding.logoUrl()} con prefijo {@code classpath:} (recurso del jar),
 *  - {@code branding.logoUrl()} como ruta de archivo en disco,
 *  - en su defecto, el recurso por convención {@code branding/logo.png} del
 *    classpath (donde la firma deja su logo).
 *
 * Es tolerante a fallos: si no hay logo o no se puede leer, devuelve {@code null}
 * y el membrete se estampa solo con texto, sin romper la exportación.
 */
@Component
public class LogoResolver {

    private static final Logger log = LoggerFactory.getLogger(LogoResolver.class);
    private static final String DEFAULT_CLASSPATH = "branding/logo.png";
    private static final String CLASSPATH_PREFIX = "classpath:";

    /** Devuelve los bytes del logo de la firma, o {@code null} si no hay/falla. */
    public byte[] resolve(Branding branding) {
        String url = branding != null ? branding.logoUrl() : null;

        if (url != null && !url.isBlank()) {
            String trimmed = url.strip();
            if (trimmed.startsWith(CLASSPATH_PREFIX)) {
                return fromClasspath(trimmed.substring(CLASSPATH_PREFIX.length()));
            }
            // Solo rutas de archivo locales: no se descargan URLs remotas en el MVP.
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                byte[] fromFile = fromFile(trimmed);
                if (fromFile != null) {
                    return fromFile;
                }
            }
        }

        // Convención por defecto: la firma deja su logo en el classpath.
        return fromClasspath(DEFAULT_CLASSPATH);
    }

    private byte[] fromClasspath(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (Exception e) {
            log.warn("No se pudo leer el logo del classpath: {}", resourcePath);
            return null;
        }
    }

    private byte[] fromFile(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (Files.isRegularFile(path)) {
                return Files.readAllBytes(path);
            }
        } catch (Exception e) {
            log.warn("No se pudo leer el logo del archivo: {}", filePath);
        }
        return null;
    }
}
