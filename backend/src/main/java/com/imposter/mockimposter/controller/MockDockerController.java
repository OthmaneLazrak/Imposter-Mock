package com.imposter.mockimposter.controller;

import com.imposter.mockimposter.service.MockProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/docker")
public class MockDockerController {

    private static final Logger logger = Logger.getLogger(MockDockerController.class.getName());

    @Autowired
    private MockProjectService mockProjectService;

    /** Démarrer un conteneur Docker via le script python */
    @PostMapping("/start/{projectName}")
    public ResponseEntity<?> startContainer(@PathVariable String projectName) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "error", "message", "Utilisateur non authentifié"));
            }

            logger.info("[DOCKER] Tentative de démarrage du conteneur pour le projet : " + projectName);

            mockProjectService.startDockerContainer(projectName);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Conteneur démarré avec succès",
                    "projectName", projectName
            ));
        } catch (Exception e) {
            logger.severe("[DOCKER] Erreur lors du démarrage du conteneur : " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "Erreur lors du démarrage : " + e.getMessage()
                    ));
        }
    }

    /** Arrêter un conteneur Docker via le script python */
    @PostMapping("/stop/{projectName}")
    public ResponseEntity<?> stopContainer(@PathVariable String projectName) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "error", "message", "Utilisateur non authentifié"));
            }

            logger.info("[DOCKER] Tentative d'arrêt du conteneur pour le projet : " + projectName);

            mockProjectService.stopDockerContainer(projectName);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Conteneur arrêté avec succès",
                    "projectName", projectName
            ));
        } catch (Exception e) {
            logger.severe("[DOCKER] Erreur lors de l'arrêt du conteneur : " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "Erreur lors de l'arrêt : " + e.getMessage()
                    ));
        }
    }

    /** Redémarrer un conteneur Docker (stop puis start via le script python) */
    @PostMapping("/restart/{projectName}")
    public ResponseEntity<?> restartContainer(@PathVariable String projectName) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "error", "message", "Utilisateur non authentifié"));
            }

            logger.info("[DOCKER] Tentative de redémarrage du conteneur pour le projet : " + projectName);

            try {
                mockProjectService.stopDockerContainer(projectName);
            } catch (Exception e) {
                logger.warning("[DOCKER] Conteneur déjà arrêté ou inexistant : " + e.getMessage());
            }
            mockProjectService.startDockerContainer(projectName);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Conteneur redémarré avec succès",
                    "projectName", projectName
            ));
        } catch (Exception e) {
            logger.severe("[DOCKER] Erreur lors du redémarrage du conteneur : " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "Erreur lors du redémarrage : " + e.getMessage()
                    ));
        }
    }

}