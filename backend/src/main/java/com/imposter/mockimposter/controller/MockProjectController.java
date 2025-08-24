package com.imposter.mockimposter.controller;

import com.imposter.mockimposter.entities.MockProject;
import com.imposter.mockimposter.service.MockProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
@RestController
@RequestMapping("/api/projects")
public class MockProjectController {

    private final MockProjectService mockProjectService;

    public MockProjectController(MockProjectService mockProjectService) {
        this.mockProjectService = mockProjectService;
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()));
    }

    // ✅ Récupérer les projets de l'utilisateur connecté
    @GetMapping
    public ResponseEntity<?> getUserProjects() {
        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Utilisateur non authentifié"
            ));
        }

        List<MockProject> projects = mockProjectService.getProjectsByCurrentUser();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", projects
        ));
    }

    // ✅ Créer un projet
    @PostMapping("/create")
    public ResponseEntity<?> createProject(
            @RequestParam("projectName") String projectName,
            @RequestParam("wsdlFile") MultipartFile wsdlFile,
            @RequestParam(value = "xsdFile", required = false) MultipartFile xsdFile) {

        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Vous devez être connecté pour créer un projet"
            ));
        }

        if (wsdlFile == null || wsdlFile.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "Fichier WSDL obligatoire"
            ));
        }

        try {
            MockProject project = mockProjectService.createMockProject(projectName, wsdlFile, xsdFile);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Projet créé avec succès",
                    "data", project
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erreur lors de la création du projet : " + e.getMessage()
            ));
        }
    }

    // ✅ Supprimer un projet
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable Long id) {
        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Utilisateur non authentifié"
            ));
        }

        Optional<MockProject> projectOpt = mockProjectService.getProjectsByCurrentUser().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();

        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Projet non trouvé ou accès interdit"
            ));
        }

        try {
            mockProjectService.deleteProject(projectOpt.get());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Projet supprimé avec succès",
                    "id", id
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erreur lors de la suppression du projet : " + e.getMessage()
            ));
        }
    }
}
