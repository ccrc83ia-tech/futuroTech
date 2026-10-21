package com.futurotech.legal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.futurotech.legal.application.service.AuthService;
import com.futurotech.legal.domain.exception.AuthenticationException;
import com.futurotech.legal.domain.model.Role;
import com.futurotech.legal.domain.model.User;
import com.futurotech.legal.domain.port.out.UserRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private UserRepositoryPort userRepo;
    private PasswordEncoder encoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepo = Mockito.mock(UserRepositoryPort.class);
        encoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepo, encoder);
    }

    @Test
    void authenticatesWithCorrectPassword() {
        String hash = encoder.encode("secret123");
        var user = new User("u-1", "admin", "Admin", "", Role.ADMIN, true, hash, null);
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));

        User result = authService.authenticate("admin", "secret123");
        assertThat(result.username()).isEqualTo("admin");
    }

    @Test
    void rejectsWrongPassword() {
        String hash = encoder.encode("secret123");
        var user = new User("u-1", "admin", "Admin", "", Role.ADMIN, true, hash, null);
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.authenticate("admin", "wrong"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void rejectsInactiveUser() {
        String hash = encoder.encode("secret123");
        var user = new User("u-1", "admin", "Admin", "", Role.ADMIN, false, hash, null);
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.authenticate("admin", "secret123"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void rejectsUnknownUser() {
        when(userRepo.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.authenticate("ghost", "x"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void ensureDefaultUsersCreatesFourWhenEmpty() {
        when(userRepo.findAll()).thenReturn(List.of());
        authService.ensureDefaultUsers();
        Mockito.verify(userRepo, Mockito.times(4)).save(Mockito.any(User.class));
    }

    @Test
    void ensureDefaultUsersSkipsWhenUsersExist() {
        var existing = new User("u-1", "admin", "Admin", "", Role.ADMIN, true, "h", null);
        when(userRepo.findAll()).thenReturn(List.of(existing));
        authService.ensureDefaultUsers();
        Mockito.verify(userRepo, Mockito.never()).save(Mockito.any(User.class));
    }
}
