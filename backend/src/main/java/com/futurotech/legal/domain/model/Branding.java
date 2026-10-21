package com.futurotech.legal.domain.model;

/**
 * Branding de la firma: membrete, colores y datos de contacto.
 * Todos los campos son opcionales (pueden ser null / vacíos).
 */
public record Branding(
        String logoUrl,
        String headerText,
        String footerText,
        String primaryColor,
        String nit,
        String address,
        String phone,
        String email,
        String website
) {
    public static Branding empty() {
        return new Branding(null, null, null, "#000000", null, null, null, null, null);
    }
}
