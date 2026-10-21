package com.futurotech.legal.infrastructure.config;

import com.futurotech.legal.application.service.AuthService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/**
 * Inicialización en el arranque: crea usuarios por defecto si la BD está vacía
 * y siembra el corpus legal base del RAG (idempotente).
 */
@Component
public class StartupRunner implements ApplicationRunner {

    private final AuthService authService;
    private final RagCorpusSeeder ragCorpusSeeder;

    public StartupRunner(AuthService authService, RagCorpusSeeder ragCorpusSeeder) {
        this.authService = authService;
        this.ragCorpusSeeder = ragCorpusSeeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        authService.ensureDefaultUsers();
        ragCorpusSeeder.seed();
    }
}
