package com.futurotech.legal.domain.exception;

/** Credenciales inválidas o usuario inactivo. */
public class AuthenticationException extends DomainException {
    public AuthenticationException() {
        super("Usuario o contraseña incorrectos");
    }
}
