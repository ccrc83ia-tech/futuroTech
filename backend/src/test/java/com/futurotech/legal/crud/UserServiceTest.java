package com.futurotech.legal.crud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.futurotech.legal.application.service.AuthService;
import com.futurotech.legal.application.service.UserService;
import com.futurotech.legal.domain.model.Role;
import com.futurotech.legal.domain.model.User;
import com.futurotech.legal.domain.port.out.UserRepositoryPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserServiceTest {

    private UserRepositoryPort userRepo;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepo = Mockito.mock(UserRepositoryPort.class);
        AuthService authService = new AuthService(userRepo, new BCryptPasswordEncoder());
        userService = new UserService(userRepo, authService);
    }

    @Test
    void createHashesPasswordAndNeverStoresPlain() {
        var captor = ArgumentCaptor.forClass(User.class);
        userService.create("jperez", "Juan", "j@f.co", Role.ABOGADO, "secret123", null);

        Mockito.verify(userRepo).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.passwordHash()).isNotEqualTo("secret123");
        assertThat(saved.passwordHash()).startsWith("$2"); // BCrypt
        assertThat(saved.role()).isEqualTo(Role.ABOGADO);
    }

    @Test
    void updateKeepsExistingHashWhenPasswordBlank() {
        var existing = new User("u-1", "jperez", "Juan", "j@f.co", Role.ABOGADO, true, "$2a$existing", null);
        when(userRepo.findById("u-1")).thenReturn(Optional.of(existing));

        var captor = ArgumentCaptor.forClass(User.class);
        userService.update("u-1", "Juan P", "j@f.co", Role.ABOGADO, true, "", null);

        Mockito.verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().passwordHash()).isEqualTo("$2a$existing");
        assertThat(captor.getValue().fullName()).isEqualTo("Juan P");
    }

    @Test
    void updateChangesHashWhenNewPasswordProvided() {
        var existing = new User("u-1", "jperez", "Juan", "j@f.co", Role.ABOGADO, true, "$2a$old", null);
        when(userRepo.findById("u-1")).thenReturn(Optional.of(existing));

        var captor = ArgumentCaptor.forClass(User.class);
        userService.update("u-1", "Juan", "j@f.co", Role.ABOGADO, true, "nuevaClave99", null);

        Mockito.verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().passwordHash()).isNotEqualTo("$2a$old");
        assertThat(captor.getValue().passwordHash()).startsWith("$2");
    }
}
