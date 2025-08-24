package com.imposter.mockimposter.controller;

import com.imposter.mockimposter.entities.User;
import com.imposter.mockimposter.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ======================
    // 🔹 1. Récupérer tous les utilisateurs (ADMIN uniquement)
    // ======================
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<User> users = userRepository.findAll();

            // Ne pas renvoyer les mots de passe dans la réponse
            users.forEach(user -> user.setPassword(null));

            Map<String, Object> response = new HashMap<>();
            response.put("users", users);
            response.put("count", users.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("❌ Erreur lors de la récupération des utilisateurs : " + e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Erreur lors de la récupération des utilisateurs");
            errorResponse.put("error", "FETCH_ERROR");

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // ======================
    // 🔹 2. Créer un nouvel utilisateur (ADMIN)
    // ======================
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody User user) {
        try {
            // Vérifier si l'utilisateur existe déjà
            if (userRepository.findByUsername(user.getUsername()).isPresent()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("message", "Un utilisateur avec ce nom existe déjà");
                errorResponse.put("error", "USER_EXISTS");

                return ResponseEntity.status(409).body(errorResponse);
            }

            // Encoder le mot de passe
            user.setPassword(passwordEncoder.encode(user.getPassword()));

            // Définir des valeurs par défaut si nécessaire
            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole("USER");
            }
            // enabled est un boolean primitif, pas besoin de vérifier null
            // Il sera false par défaut, on peut le forcer à true
            user.setEnabled(true);

            // Sauvegarder
            User savedUser = userRepository.save(user);

            // Ne pas renvoyer le mot de passe
            savedUser.setPassword(null);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Utilisateur créé avec succès");
            response.put("user", savedUser);

            System.out.println("✅ Utilisateur créé : " + savedUser.getUsername());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Erreur lors de la création de l'utilisateur : " + e.getMessage());
            e.printStackTrace();

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Erreur lors de la création de l'utilisateur");
            errorResponse.put("error", "CREATION_ERROR");

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // ======================
    // 🔹 3. Récupérer un utilisateur par ID (ADMIN)
    // ======================
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        Optional<User> user = userRepository.findById(id);

        if (user.isPresent()) {
            User foundUser = user.get();
            foundUser.setPassword(null); // Ne pas exposer le mot de passe

            return ResponseEntity.ok(foundUser);
        }

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", "Utilisateur non trouvé");
        errorResponse.put("error", "USER_NOT_FOUND");

        return ResponseEntity.status(404).body(errorResponse);
    }

    // ======================
    // 🔹 4. Modifier un utilisateur (ADMIN)
    // ======================
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User userUpdate) {
        try {
            Optional<User> existingUserOpt = userRepository.findById(id);

            if (!existingUserOpt.isPresent()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("message", "Utilisateur non trouvé");
                errorResponse.put("error", "USER_NOT_FOUND");

                return ResponseEntity.status(404).body(errorResponse);
            }

            User existingUser = existingUserOpt.get();

            // Mettre à jour les champs (sauf le mot de passe si vide)
            if (userUpdate.getUsername() != null && !userUpdate.getUsername().isEmpty()) {
                existingUser.setUsername(userUpdate.getUsername());
            }

            if (userUpdate.getPassword() != null && !userUpdate.getPassword().isEmpty()) {
                existingUser.setPassword(passwordEncoder.encode(userUpdate.getPassword()));
            }

            if (userUpdate.getRole() != null && !userUpdate.getRole().isEmpty()) {
                existingUser.setRole(userUpdate.getRole());
            }

            // enabled est un boolean primitif, on peut directement l'assigner
            existingUser.setEnabled(userUpdate.isEnabled());

            User savedUser = userRepository.save(existingUser);
            savedUser.setPassword(null); // Ne pas renvoyer le mot de passe

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Utilisateur mis à jour avec succès");
            response.put("user", savedUser);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Erreur lors de la mise à jour de l'utilisateur : " + e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Erreur lors de la mise à jour");
            errorResponse.put("error", "UPDATE_ERROR");

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // ======================
    // 🔹 5. Activer/Désactiver un utilisateur (ADMIN)
    // ======================
    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long id) {
        try {
            Optional<User> userOpt = userRepository.findById(id);

            if (!userOpt.isPresent()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("message", "Utilisateur non trouvé");
                errorResponse.put("error", "USER_NOT_FOUND");

                return ResponseEntity.status(404).body(errorResponse);
            }

            User user = userOpt.get();
            user.setEnabled(!user.isEnabled());
            User savedUser = userRepository.save(user);
            savedUser.setPassword(null);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Statut utilisateur modifié avec succès");
            response.put("user", savedUser);
            response.put("enabled", savedUser.isEnabled());

            System.out.println("🔄 Statut modifié pour " + savedUser.getUsername() + " : " + savedUser.isEnabled());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Erreur lors du changement de statut : " + e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Erreur lors du changement de statut");
            errorResponse.put("error", "TOGGLE_ERROR");

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // ======================
    // 🔹 6. Supprimer un utilisateur (ADMIN)
    // ======================
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            if (!userRepository.existsById(id)) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("message", "Utilisateur non trouvé");
                errorResponse.put("error", "USER_NOT_FOUND");

                return ResponseEntity.status(404).body(errorResponse);
            }

            // Vérifier qu'on ne supprime pas le dernier admin
            User userToDelete = userRepository.findById(id).get();
            if ("ADMIN".equals(userToDelete.getRole())) {
                long adminCount = userRepository.countByRole("ADMIN");
                if (adminCount <= 1) {
                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("message", "Impossible de supprimer le dernier administrateur");
                    errorResponse.put("error", "LAST_ADMIN");

                    return ResponseEntity.status(400).body(errorResponse);
                }
            }

            userRepository.deleteById(id);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Utilisateur supprimé avec succès");

            System.out.println("🗑️ Utilisateur supprimé : " + userToDelete.getUsername());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Erreur lors de la suppression : " + e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Erreur lors de la suppression");
            errorResponse.put("error", "DELETE_ERROR");

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // ======================
    // 🔹 7. Obtenir le profil de l'utilisateur connecté
    // ======================
    @GetMapping("/profile")
    public ResponseEntity<?> getCurrentUserProfile() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("message", "Non authentifié");
                errorResponse.put("error", "NOT_AUTHENTICATED");

                return ResponseEntity.status(401).body(errorResponse);
            }

            String username = auth.getName();
            Optional<User> userOpt = userRepository.findByUsername(username);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setPassword(null); // Ne pas exposer le mot de passe

                return ResponseEntity.ok(user);
            }

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Utilisateur non trouvé");
            errorResponse.put("error", "USER_NOT_FOUND");

            return ResponseEntity.status(404).body(errorResponse);

        } catch (Exception e) {
            System.out.println("❌ Erreur lors de la récupération du profil : " + e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Erreur serveur");
            errorResponse.put("error", "SERVER_ERROR");

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // ======================
    // 🔹 8. Vérifier si l'utilisateur connecté est admin
    // ======================
    @GetMapping("/is-admin")
    public ResponseEntity<?> isCurrentUserAdmin() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                Map<String, Object> response = new HashMap<>();
                response.put("isAdmin", false);
                response.put("authenticated", false);

                return ResponseEntity.ok(response);
            }

            String username = auth.getName();
            Optional<User> userOpt = userRepository.findByUsername(username);

            boolean isAdmin = userOpt.isPresent() && "ADMIN".equals(userOpt.get().getRole());

            Map<String, Object> response = new HashMap<>();
            response.put("isAdmin", isAdmin);
            response.put("authenticated", true);
            response.put("username", username);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Erreur lors de la vérification admin : " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("isAdmin", false);
            response.put("error", "CHECK_ERROR");

            return ResponseEntity.status(500).body(response);
        }
    }
}