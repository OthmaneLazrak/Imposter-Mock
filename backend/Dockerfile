# Backend - Spring Boot + Python + Docker CLI
FROM openjdk:17-jdk-slim

# Installer Python, dépendances système et Docker CLI
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-venv \
    build-essential \
    curl \
    apt-transport-https \
    ca-certificates \
    gnupg \
    lsb-release \
    && rm -rf /var/lib/apt/lists/*

# Installer Docker CLI
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg && \
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null && \
    apt-get update && \
    apt-get install -y docker-ce-cli && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Créer un alias python/pip
RUN ln -sf /usr/bin/python3 /usr/bin/python && \
    ln -sf /usr/bin/pip3 /usr/bin/pip

# Répertoire de travail
WORKDIR /app

# Copier le JAR généré par Maven/Gradle
COPY target/*.jar app.jar

# CORRECTION: Copier la bonne configuration
COPY src/main/resources/application.properties /app/config/application.properties

# Copier les scripts Python
COPY script_py/ ./script_py/

# Copier requirements.txt et installer dépendances Python
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Rendre les scripts exécutables
RUN chmod +x ./script_py/*.py || true

# Exposer le port du backend
EXPOSE 8086

# Point de santé (adapté pour H2)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8086/actuator/health || exit 1

# Variable d'environnement
ENV PYTHONPATH=/app/script_py
ENV SPRING_PROFILES_ACTIVE=docker

# Lancer l'application avec le profil docker
ENTRYPOINT ["java", "-jar", "app.jar"]
