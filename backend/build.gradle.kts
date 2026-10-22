plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.futurotech"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["resilience4jVersion"] = "2.3.0"
extra["testcontainersVersion"] = "1.20.4"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}

dependencies {
    // --- Spring Boot ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux") // WebClient para Ollama
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Base de datos ---
    runtimeOnly("org.postgresql:postgresql")
    // Spring Boot 4 es modular: la autoconfiguración de Flyway ya no viene con
    // flyway-core; requiere el starter dedicado para ejecutar las migraciones.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    // pgvector <-> Hibernate (soporte de tipo vector)
    implementation("com.pgvector:pgvector:0.1.6")

    // --- Resiliencia (circuit breaker + retry) ---
    implementation("io.github.resilience4j:resilience4j-spring-boot3:${property("resilience4jVersion")}")
    implementation("io.github.resilience4j:resilience4j-reactor:${property("resilience4jVersion")}")

    // --- JWT ---
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // --- Exportación de documentos ---
    implementation("org.apache.poi:poi-ooxml:5.4.0")   // DOCX
    implementation("com.github.librepdf:openpdf:2.0.3") // PDF
    implementation("org.apache.pdfbox:pdfbox:3.0.4")    // extracción de texto PDF (ingesta RAG)

    // --- Tests ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
