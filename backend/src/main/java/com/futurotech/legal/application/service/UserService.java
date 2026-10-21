package com.futurotech.legal.application.service;

import com.futurotech.legal.domain.model.Role;
import com.futurotech.legal.domain.model.User;
import com.futurotech.legal.domain.port.out.UserRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Casos de uso de gestión de usuarios (RBAC). */
@Service
public class UserService {

    private final UserRepositoryPort userRepo;
    private final AuthService authService;

    public UserService(UserRepositoryPort userRepo, AuthService authService) {
        this.userRepo = userRepo;
        this.authService = authService;
    }

    public List<User> findAll() {
        return userRepo.findAll();
    }

    public Optional<User> findById(String userId) {
        return userRepo.findById(userId);
    }

    /** Crea un usuario. La contraseña se hashea con BCrypt. */
    public User create(String username, String fullName, String email, Role role,
                       String plainPassword, List<String> customPermissions) {
        String userId = UUID.randomUUID().toString();
        User user = new User(userId, username, fullName, email, role, true,
                authService.hashPassword(plainPassword), customPermissions);
        userRepo.save(user);
        return user;
    }

    /**
     * Actualiza un usuario. Si plainPassword es null/blank, conserva el hash existente.
     */
    public User update(String userId, String fullName, String email, Role role, boolean active,
                       String plainPassword, List<String> customPermissions) {
        User existing = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));
        String hash = (plainPassword == null || plainPassword.isBlank())
                ? existing.passwordHash()
                : authService.hashPassword(plainPassword);
        User updated = new User(existing.userId(), existing.username(), fullName, email,
                role, active, hash, customPermissions);
        userRepo.save(updated);
        return updated;
    }

    public void delete(String userId) {
        userRepo.deleteById(userId);
    }
}
