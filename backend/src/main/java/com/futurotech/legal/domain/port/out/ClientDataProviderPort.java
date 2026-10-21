package com.futurotech.legal.domain.port.out;

import com.futurotech.legal.domain.model.ClientData;

import java.util.Optional;

/**
 * Proveedor de datos del cliente. Hoy: implementación manual (el abogado digita).
 * Futuro: adaptador que trae los datos del sistema de la firma, sin tocar el dominio.
 */
public interface ClientDataProviderPort {

    /** Devuelve los datos del cliente por su identificación, si están disponibles. */
    Optional<ClientData> fetchByIdentification(String identification);

    /** Indica si este proveedor puede traer datos automáticamente (habilita el botón "Traer datos"). */
    boolean supportsAutoFetch();
}
