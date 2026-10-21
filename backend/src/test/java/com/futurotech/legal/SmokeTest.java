package com.futurotech.legal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Smoke test de Fase 0 — verifica que el toolchain de test compila y corre.
 * El test de carga de contexto Spring (con Testcontainers + PostgreSQL) llega en Fase 1.
 */
class SmokeTest {

    @Test
    void toolchainWorks() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
