package com.futurotech.legal.domain.model;

import java.util.EnumSet;
import java.util.Set;

/** Roles del sistema y sus permisos por defecto (RBAC). */
public enum Role {
    ADMIN("admin"),
    ABOGADO("abogado"),
    PASANTE("pasante"),
    CONSULTA("consulta");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Role fromValue(String v) {
        for (Role r : values()) {
            if (r.value.equals(v)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Rol desconocido: " + v);
    }

    /** Permisos por defecto del rol (replica ROLE_PERMISSIONS del sistema Python). */
    public Set<Permission> defaultPermissions() {
        return switch (this) {
            case ADMIN -> EnumSet.allOf(Permission.class);
            case ABOGADO -> EnumSet.of(
                    Permission.GENERATE_DOCUMENT,
                    Permission.VIEW_TEMPLATES,
                    Permission.VIEW_ENTITIES,
                    Permission.MANAGE_ENTITIES,
                    Permission.VIEW_COMPANY);
            case PASANTE -> EnumSet.of(
                    Permission.GENERATE_DOCUMENT,
                    Permission.VIEW_TEMPLATES,
                    Permission.VIEW_ENTITIES);
            case CONSULTA -> EnumSet.of(
                    Permission.VIEW_TEMPLATES,
                    Permission.VIEW_ENTITIES,
                    Permission.VIEW_COMPANY);
        };
    }
}
