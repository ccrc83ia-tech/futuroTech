package com.futurotech.legal.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public final class EntityDtos {

    private EntityDtos() {
    }

    public record EntityRequest(
            @NotBlank String entityId,
            @NotBlank String name,
            String nit,
            String address,
            String city,
            String phone,
            String email,
            String legalRep,
            String entityType,
            String notes
    ) {
    }
}
