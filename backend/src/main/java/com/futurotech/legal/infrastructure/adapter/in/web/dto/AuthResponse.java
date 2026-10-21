package com.futurotech.legal.infrastructure.adapter.in.web.dto;

import java.util.List;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String userId,
        String username,
        String fullName,
        String role,
        List<String> permissions
) {
}
