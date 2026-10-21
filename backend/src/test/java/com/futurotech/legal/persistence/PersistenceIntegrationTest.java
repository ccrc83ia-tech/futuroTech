package com.futurotech.legal.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.futurotech.legal.domain.model.Branding;
import com.futurotech.legal.domain.model.DocumentStatus;
import com.futurotech.legal.domain.model.GeneratedDocument;
import com.futurotech.legal.domain.model.Permission;
import com.futurotech.legal.domain.model.Role;
import com.futurotech.legal.domain.model.TenantConfig;
import com.futurotech.legal.domain.model.User;
import com.futurotech.legal.domain.port.out.GeneratedDocumentPort;
import com.futurotech.legal.domain.port.out.TenantRepositoryPort;
import com.futurotech.legal.domain.port.out.UserRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifica que el esquema Flyway aplica sobre PostgreSQL+pgvector, el contexto
 * Spring carga, y los adaptadores de persistencia hacen round-trip entity<->dominio.
 *
 * Requiere Docker (Testcontainers). Si Docker no está disponible en el entorno,
 * el test se omite automáticamente en lugar de fallar el build.
 */
@SpringBootTest
@Testcontainers
@EnabledIf("dockerAvailable")
class PersistenceIntegrationTest {

    @SuppressWarnings("unused")
    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("legal_engine")
            .withUsername("legal")
            .withPassword("secret");

    @Autowired
    TenantRepositoryPort tenantRepo;

    @Autowired
    UserRepositoryPort userRepo;

    @Autowired
    GeneratedDocumentPort generatedDocs;

    @Test
    void contextLoadsAndFlywayApplies() {
        // Si llegamos aquí, el contexto cargó y Flyway aplicó el esquema V1.
        assertThat(tenantRepo).isNotNull();
    }

    @Test
    void tenantRoundTripWithBrandingAndRules() {
        var branding = new Branding(null, "FIRMA DEMO", "NIT 900.000.000-0",
                "#1a237e", "900000000", "Calle 1", "3000000000", "demo@firma.co", null);
        var tenant = new TenantConfig("firma-demo", "Firma Demo S.A.S.",
                "Eres un abogado experto.", List.of("Art. 23 CP", "Ley 1755 de 2015"), branding, true);

        tenantRepo.save(tenant);

        Optional<TenantConfig> found = tenantRepo.findById("firma-demo");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Firma Demo S.A.S.");
        assertThat(found.get().legalRules()).containsExactly("Art. 23 CP", "Ley 1755 de 2015");
        assertThat(found.get().branding().headerText()).isEqualTo("FIRMA DEMO");
    }

    @Test
    void userRoundTripAndRbac() {
        var abogado = new User("u-1", "jperez", "Juan Pérez", "j@firma.co",
                Role.ABOGADO, true, "hash", null);
        userRepo.save(abogado);

        User found = userRepo.findByUsername("jperez").orElseThrow();
        assertThat(found.role()).isEqualTo(Role.ABOGADO);
        // Permisos por defecto del rol abogado
        assertThat(found.hasPermission(Permission.GENERATE_DOCUMENT)).isTrue();
        assertThat(found.hasPermission(Permission.MANAGE_USERS)).isFalse();
    }

    @Test
    void generatedDocumentAuditTrailAndStatusTransition() {
        var id = UUID.randomUUID();
        var doc = new GeneratedDocument(id, "ACCION-TUTELA-V1", 1, "u-1", "ent-1",
                Map.of("cliente_nombre", "Ana", "cedula", "123"),
                DocumentStatus.DRAFT, null, "file.docx", "abc123",
                Instant.now(), null, null);

        generatedDocs.save(doc);
        assertThat(generatedDocs.findById(id)).isPresent();
        assertThat(generatedDocs.findById(id).get().status()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(generatedDocs.findById(id).get().caseFields()).containsEntry("cliente_nombre", "Ana");

        // Transición de revisión humana
        generatedDocs.updateStatus(id, DocumentStatus.APPROVED, "u-admin");
        var reviewed = generatedDocs.findById(id).orElseThrow();
        assertThat(reviewed.status()).isEqualTo(DocumentStatus.APPROVED);
        assertThat(reviewed.reviewedBy()).isEqualTo("u-admin");
        assertThat(reviewed.reviewedAt()).isNotNull();
    }
}
