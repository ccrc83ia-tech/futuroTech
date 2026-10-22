package com.futurotech.legal.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.futurotech.legal.application.service.TemplateService;
import com.futurotech.legal.infrastructure.adapter.in.web.TemplateController;
import com.futurotech.legal.infrastructure.config.JwtAuthFilter;
import com.futurotech.legal.infrastructure.config.JwtService;
import com.futurotech.legal.infrastructure.config.SecurityConfig;

/**
 * Verifica las reglas de autorización (@PreAuthorize) de TemplateController con
 * la cadena de seguridad real (SecurityConfig importada). No arranca BD ni IA:
 * TemplateService y JwtService son mocks, y el Authentication se inyecta con
 * spring-security-test.
 */
@WebMvcTest(TemplateController.class)
@Import({ SecurityConfig.class, JwtAuthFilter.class })
class TemplateControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TemplateService templateService;

    // Dependencia del JwtAuthFilter (bean de la cadena). No se usa en estos tests
    // porque el Authentication se inyecta directamente con los post-processors.
    @MockitoBean
    private JwtService jwtService;

    private static final String VIEW = "PERM_view_templates";
    private static final String MANAGE = "PERM_manage_templates";

    @Test
    void listWithoutAuthenticationIsDenied() throws Exception {
        // JWT stateless sin entry point de 401: Spring responde 403 al no haber autenticación.
        mvc.perform(get("/api/v1/templates"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWithViewAuthoritySucceeds() throws Exception {
        when(templateService.findAll()).thenReturn(List.of());

        mvc.perform(get("/api/v1/templates").with(user("ana").authorities(auth(VIEW))))
                .andExpect(status().isOk());
    }

    @Test
    void deleteWithOnlyViewAuthorityIsForbidden() throws Exception {
        // La gestión (borrado) exige PERM_manage_templates; con solo lectura debe dar 403.
        mvc.perform(delete("/api/v1/templates/T1")
                        .with(user("ana").authorities(auth(VIEW)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteWithManageAuthoritySucceeds() throws Exception {
        mvc.perform(delete("/api/v1/templates/T1")
                        .with(user("admin").authorities(auth(MANAGE)))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void uploadRagDocumentWithOnlyViewAuthorityIsForbidden() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "ley.pdf", "application/pdf", "contenido".getBytes());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/templates/T1/documents").file(file)
                        .with(user("ana").authorities(auth(VIEW)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRagDocumentsWithViewAuthoritySucceeds() throws Exception {
        when(templateService.listRagDocuments("T1")).thenReturn(List.of("ley.pdf"));

        mvc.perform(get("/api/v1/templates/T1/documents").with(user("ana").authorities(auth(VIEW))))
                .andExpect(status().isOk());
    }

    private static org.springframework.security.core.authority.SimpleGrantedAuthority auth(String a) {
        return new org.springframework.security.core.authority.SimpleGrantedAuthority(a);
    }
}
