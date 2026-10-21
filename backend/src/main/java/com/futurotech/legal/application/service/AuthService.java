package com.futurotech.legal.application.service;

import com.futurotech.legal.domain.exception.AuthenticationException;
import com.futurotech.legal.domain.model.Role;
import com.futurotech.legal.domain.model.User;
import com.futurotech.legal.domain.port.out.UserRepositoryPort;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Autenticación: verificación de credenciales con BCrypt y creación segura de
 * usuarios por defecto en el primer arranque. Las contraseñas nunca se guardan
 * ni se registran en texto plano.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    private static final int PASSWORD_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final List<DefaultUser> DEFAULT_USERS = List.of(
            new DefaultUser("admin", "Administrador", Role.ADMIN),
            new DefaultUser("abogado", "Abogado", Role.ABOGADO),
            new DefaultUser("pasante", "Pasante", Role.PASANTE),
            new DefaultUser("consulta", "Solo Consulta", Role.CONSULTA));

    private final UserRepositoryPort userRepo;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepositoryPort userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /** Autentica por usuario y contraseña. Lanza AuthenticationException si falla. */
    public User authenticate(String username, String password) {
        User user = userRepo.findByUsername(username).orElseThrow(AuthenticationException::new);
        if (!user.active() || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new AuthenticationException();
        }
        return user;
    }

    public String hashPassword(String plain) {
        return passwordEncoder.encode(plain);
    }

    /** Crea usuarios por defecto con contraseñas aleatorias si no existe ninguno. */
    public void ensureDefaultUsers() {
        if (!userRepo.findAll().isEmpty()) {
            return;
        }

        String sep = "=".repeat(60);
        System.out.println(sep);
        System.out.println("  PRIMER ARRANQUE — Credenciales iniciales generadas");
        System.out.println("  Guarde estas contraseñas en un lugar seguro. No se volverán a mostrar.");
        System.out.println(sep);

        for (DefaultUser du : DEFAULT_USERS) {
            String password = generateSecurePassword();
            userRepo.save(new User(
                    UUID.randomUUID().toString(), du.username(), du.fullName(), "",
                    du.role(), true, hashPassword(password), null));

            // Contraseña completa en consola una única vez; nunca a los logs.
            System.out.printf("  %-10s -> %s%n", du.username(), password);
            log.warn("Usuario por defecto creado: {} (rol {})", du.username(), du.role().value());
        }
        System.out.println(sep);
    }

    private String generateSecurePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private record DefaultUser(String username, String fullName, Role role) {
    }
}
