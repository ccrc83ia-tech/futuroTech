package com.futurotech.legal.domain.model;

import java.util.Map;

/**
 * Datos de un cliente. Modelo de dominio desacoplado del sistema externo de la
 * firma. Hoy se llena manualmente; a futuro un adaptador lo traerá del sistema
 * de la firma (ver ClientDataProviderPort) sin tocar el dominio.
 */
public record ClientData(
        String identification,
        String fullName,
        String address,
        String city,
        String phone,
        String email,
        Map<String, String> extraFields
) {
    public ClientData {
        extraFields = extraFields == null ? Map.of() : Map.copyOf(extraFields);
    }
}
