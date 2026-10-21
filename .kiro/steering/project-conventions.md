# Convenciones del Proyecto — FuturoTech AI (Java + React)

Motor Jurídico Universal. Migración desde un sistema Python (FastAPI + Streamlit) a Java + React. Este proyecto NO tiene relación con Ficohsa/NOVA — no aplicar steering, librerías ni convenciones corporativas de Ficohsa aquí.

## Stack (fijo)

- **Backend:** Java 21 + Spring Boot 4.0 (Spring Framework 7, Jakarta EE 11, Jackson 3, JUnit 6)
- **Build:** Gradle 9 con Kotlin DSL (`build.gradle.kts`)
- **Persistencia:** Spring Data JPA + Hibernate ORM 7 sobre PostgreSQL 16
- **Vector store:** pgvector (extensión de PostgreSQL) — NO ChromaDB
- **Migraciones:** Flyway — NO Alembic, NO auto-ALTER
- **IA:** Google Gemini vía `WebClient` (REST)
- **Resiliencia:** Resilience4j (circuit breaker + retry)
- **DOCX:** Apache POI · **PDF:** OpenPDF
- **Auth:** Spring Security + BCrypt + JWT (jjwt) — stateless, NO sesión en memoria
- **Frontend:** React 18 + Vite + TypeScript + Material UI + TanStack Query + Zustand
- **Tests:** JUnit 6 + Mockito + AssertJ + Testcontainers

Todas las tecnologías deben ser open source / gratuitas para uso comercial. No introducir dependencias con licencia de pago.

## Arquitectura

- Arquitectura hexagonal (puertos y adaptadores): dominio sin dependencias de framework, aplicación con casos de uso, infraestructura con adaptadores.
- Spring MVC bloqueante (NO WebFlux reactivo) — el sistema es I/O-bound esperando al LLM; no se justifica la complejidad reactiva.
- DTOs de transporte separados de los modelos de dominio.

## Reglas de migración

- El sistema Python en `c:\proyecto\derecho_peticion` es la **especificación ejecutable de referencia**. Replicar el comportamiento del dominio (composición de prompt, modo RAG estricto, preservación byte-a-byte del DOCX, reglas RBAC) con fidelidad.
- Migrar solo la lógica que aporta valor. Descartar andamiaje: SQLite, migración desde JSON, sesión HMAC.
- Mejorar donde se pueda: API REST completa y tipada, validación formal, seguridad.

## Frontend

- Pensado para abogados sin perfil técnico: flujo guiado (wizard), lenguaje del dominio (no jerga técnica como "metadata"/"template_id"), previsualización en vivo, mínima fricción.
- Accesible y responsive.

## Idioma

- UI, mensajes de error y documentación de cara al usuario: español.
- Código, nombres de variables y comentarios técnicos: inglés.

## Verificación

- Cada cambio de backend debe compilar (`gradle build`) antes de darse por terminado.
- Escribir tests para lógica de negocio nueva (JUnit + Mockito; Testcontainers para repositorios).
