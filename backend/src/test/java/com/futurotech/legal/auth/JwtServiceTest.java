package com.futurotech.legal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.futurotech.legal.domain.model.Role;
import com.futurotech.legal.domain.model.User;
import com.futurotech.legal.infrastructure.config.JwtService;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET = "una-clave-de-prueba-suficientemente-larga-0123456789";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3600, 7200);
        jwtService.getClass(); // noop
        // init() es @PostConstruct; lo invocamos por reflexión para test unitario.
        invokeInit(jwtService);
    }

    private static void invokeInit(JwtService svc) {
        try {
            var m = JwtService.class.getDeclaredMethod("init");
            m.setAccessible(true);
            m.invoke(svc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generatesAndParsesAccessToken() {
        var user = new User("u-1", "admin", "Admin", "", Role.ADMIN, true, "h", null);
        String token = jwtService.generateAccessToken(user);

        Claims claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo("u-1");
        assertThat(jwtService.role(claims)).isEqualTo("admin");
        assertThat(jwtService.isRefreshToken(claims)).isFalse();
        // admin tiene todos los permisos
        assertThat(jwtService.permissions(claims)).contains("manage_users", "generate_document");
    }

    @Test
    void refreshTokenIsMarkedAsRefresh() {
        var user = new User("u-1", "abogado", "Abg", "", Role.ABOGADO, true, "h", null);
        String refresh = jwtService.generateRefreshToken(user);
        Claims claims = jwtService.parse(refresh);
        assertThat(jwtService.isRefreshToken(claims)).isTrue();
    }

    @Test
    void customPermissionsOverrideRole() {
        var user = new User("u-2", "pasante", "Pas", "", Role.PASANTE, true, "h",
                List.of("view_templates"));
        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.parse(token);
        assertThat(jwtService.permissions(claims)).containsExactly("view_templates");
    }

    @Test
    void initFailsWithoutSecret() {
        var svc = new JwtService("", 3600, 7200);
        assertThatThrownBy(() -> invokeInit(svc))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
