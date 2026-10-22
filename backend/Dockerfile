# =====================================================================
# FuturoTech AI — Backend Dockerfile
#
# Modo 1 (por defecto): build completo dentro del contenedor.
#   docker compose build backend
#   Requiere acceso a internet desde el contenedor (Maven Central).
#
# Modo 2 (prebuilt): usa el jar ya construido localmente, sin red.
#   docker compose build --build-arg PREBUILT=true backend
#   Requiere: .\gradlew.bat bootJar (ejecutado en el host antes de este comando).
# =====================================================================
ARG PREBUILT=false

# --- Etapa de build (solo activa cuando PREBUILT=false) ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
# Normaliza finales de línea CRLF->LF y da permisos antes de usar el wrapper.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew dependencies --no-daemon || true
COPY src src
RUN ./gradlew bootJar --no-daemon

# --- Etapa prebuilt (copia el jar construido en el host) ---
FROM eclipse-temurin:25-jre AS prebuilt
WORKDIR /app
COPY build/libs/*[^plain].jar app.jar

# --- Etapa runtime: selecciona la fuente según PREBUILT ---
FROM eclipse-temurin:25-jre AS runtime-false
COPY --from=build /app/build/libs/*.jar /app/app.jar

FROM eclipse-temurin:25-jre AS runtime-true
COPY --from=prebuilt /app/app.jar /app/app.jar

# La etapa final usa la variable ARG para elegir la fuente.
FROM runtime-${PREBUILT} AS final
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
