# Backend - Spring Boot + Python
FROM openjdk:17-jdk-slim

# Installer Python
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-venv \
    build-essential \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Créer un alias python/pip
RUN ln -sf /usr/bin/python3 /usr/bin/python && \
    ln -sf /usr/bin/pip3 /usr/bin/pip

# Répertoire de travail
WORKDIR /app

# Copier le JAR généré par Maven/Gradle
COPY target/*.jar app.jar

# Copier la config Spring Boot
COPY src/main/resources/application-docker.properties /app/config/application.properties

# Copier les scripts Python
COPY script_py/ ./script_py/

# Rendre les scripts exécutables
RUN chmod +x ./script_py/*.py || true

# Exposer le port du backend
EXPOSE 8086

# Point de santé
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8086/actuator/health || exit 1

# Variable d'environnement pour localiser les scripts
ENV PYTHONPATH=/app/script_py

# Lancer l’application
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=file:/app/config/application.properties"]
