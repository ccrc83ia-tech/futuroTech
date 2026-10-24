package com.futurotech.legal.domain.model;

import java.util.List;

/**
 * Resultado de una verificación anti-alucinación sobre un borrador generado.
 * Lo producen los verificadores (citas, datos) y lo consume la capa de
 * aplicación para anotar el documento DRAFT que revisará el abogado.
 *
 * @param role      rol del verificador que produjo el resultado ("citations", "data").
 * @param passed    true si no se detectaron problemas.
 * @param findings  hallazgos concretos (afirmaciones sin respaldo, incoherencias…).
 * @param summary   resumen legible para el revisor humano.
 */
public record VerificationResult(
        String role,
        boolean passed,
        List<String> findings,
        String summary
) {
    public VerificationResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public static VerificationResult ok(String role) {
        return new VerificationResult(role, true, List.of(), "Sin hallazgos.");
    }

    public static VerificationResult skipped(String role, String reason) {
        return new VerificationResult(role, true, List.of(), "Verificación omitida: " + reason);
    }
}
