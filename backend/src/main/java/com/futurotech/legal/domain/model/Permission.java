package com.futurotech.legal.domain.model;

/** Permisos granulares del sistema (RBAC). */
public enum Permission {
    GENERATE_DOCUMENT("generate_document"),
    VIEW_TEMPLATES("view_templates"),
    MANAGE_TEMPLATES("manage_templates"),
    VIEW_ENTITIES("view_entities"),
    MANAGE_ENTITIES("manage_entities"),
    VIEW_COMPANY("view_company"),
    MANAGE_COMPANY("manage_company"),
    MANAGE_USERS("manage_users");

    private final String value;

    Permission(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Permission fromValue(String v) {
        for (Permission p : values()) {
            if (p.value.equals(v)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Permiso desconocido: " + v);
    }
}
