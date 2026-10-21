package com.futurotech.legal.infrastructure.adapter.in.web;

import com.futurotech.legal.application.service.AuthService;
import com.futurotech.legal.domain.exception.AuthenticationException;
import com.futurotech.legal.domain.model.User;
import com.futurotech.legal.domain.port.out.UserRepositoryPort;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.AuthResponse;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.LoginRequest;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.RefreshRequest;
import com.futurotech.legal.infrastructure.config.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final UserRepositoryPort userRepo;

    public AuthController(AuthService authService, JwtService jwtService, UserRepositoryPort userRepo) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.userRepo = userRepo;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User user = authService.authenticate(req.username(), req.password());
        return toResponse(user);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        Claims claims;
        try {
            claims = jwtService.parse(req.refreshToken());
        } catch (Exception e) {
            throw new AuthenticationException();
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new AuthenticationException();
        }
        User user = userRepo.findById(claims.getSubject())
                .filter(User::active)
                .orElseThrow(AuthenticationException::new);
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        List<String> perms = user.customPermissions() != null
                ? user.customPermissions()
                : user.role().defaultPermissions().stream().map(p -> p.value()).toList();
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                user.userId(), user.username(), user.fullName(), user.role().value(), perms);
    }
}
