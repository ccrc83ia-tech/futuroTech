package com.futurotech.legal.infrastructure.config;

import com.futurotech.legal.application.service.AuthService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

/** Inicialización en el arranque: crea usuarios por defecto si la BD está vacía. */
@Component
public class StartupRunner implements ApplicationRunner {

    private final AuthService authService;

    public StartupRunner(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        authService.ensureDefaultUsers();
    }
}
