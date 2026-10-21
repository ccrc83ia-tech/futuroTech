package com.futurotech.legal.domain.model;

import java.util.List;

/**
 * Usuario del sistema con rol RBAC y permisos opcionales personalizados.
 * Si {@code customPermissions} es null, se usan los permisos por defecto del rol.
 */
public record User(
        String userId,
        String username,
        String fullName,
        String email,
        Role role,
        boolean active,
        String passwordHash,
        List<String> customPermissions
) {
    public User {
        customPermissions = customPermissions == null ? null : List.copyOf(customPermissions);
    }

    /** Verifica si el usuario tiene un permiso (custom si existe, si no el del rol). */
    public boolean hasPermission(Permission perm) {
        if (customPermissions != null) {
            return customPermissions.contains(perm.value());
        }
        return role.defaultPermissions().contains(perm);
    }
}
