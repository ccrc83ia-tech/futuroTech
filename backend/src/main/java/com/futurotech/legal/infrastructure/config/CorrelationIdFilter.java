package com.futurotech.legal.infrastructure.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Asigna un identificador de correlación (traceId) a cada petición HTTP y lo
 * publica en el MDC de SLF4J para que todas las líneas de log de la misma
 * petición sean rastreables. Si el cliente envía la cabecera
 * {@code X-Correlation-ID} se respeta; si no, se genera una.
 *
 * <p>El identificador se devuelve también en la respuesta para permitir que el
 * frontend (o un operador) lo use al reportar incidencias.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Imprescindible en un pool de hilos: evita filtrar el id al siguiente request.
            MDC.remove(MDC_KEY);
        }
    }
}
