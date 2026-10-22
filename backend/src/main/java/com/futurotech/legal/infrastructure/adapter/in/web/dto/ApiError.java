package com.futurotech.legal.infrastructure.adapter.in.web.dto;

import java.time.Instant;

/**
 * Cuerpo de respuesta estándar para los errores de la API.
 *
 * @param timestamp momento en que se produjo el error (UTC)
 * @param status    código HTTP
 * @param error     frase corta del estado HTTP (p. ej. "Not Found")
 * @param message   mensaje para el usuario, en español
 * @param path      ruta de la petición que falló
 * @param traceId   identificador de correlación para cruzar con los logs
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId) {
}
