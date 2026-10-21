package com.futurotech.legal.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** DTOs de usuarios. Nunca exponen password_hash. */
public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(
            String userId,
            String username,
            String fullName,
            String email,
            String role,
            boolean active,
            List<String> permissions
    ) {
    }

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String fullName,
            String email,
            @NotBlank String role,
            @NotBlank String password,
            List<String> permissions
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank String fullName,
            String email,
            @NotBlank String role,
            boolean active,
            String password,          // opcional: si viene vacío, conserva el actual
            List<String> permissions
    ) {
    }
}
