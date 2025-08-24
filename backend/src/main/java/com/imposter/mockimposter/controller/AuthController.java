package com.imposter.mockimposter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials, HttpServletRequest request) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        System.out.println("🔐 Tentative de connexion pour : " + username);

        try {
            // Créer le token d'authentification
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(username, password);

            // Authentifier avec le AuthenticationManager
            Authentication auth = authenticationManager.authenticate(authToken);

            // CRUCIAL : Sauvegarder l'authentification dans SecurityContextHolder
            SecurityContextHolder.getContext().setAuthentication(auth);

            // CRUCIAL : Créer/récupérer la session HTTP et y sauvegarder le contexte de sécurité
            HttpSession session = request.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

            System.out.println("✅ Connexion réussie pour : " + username);
            System.out.println("✅ Session ID : " + session.getId());
            System.out.println("✅ Authentication principal : " + auth.getPrincipal());

            // Utiliser HashMap au lieu de Map.of()
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Connexion réussie");
            response.put("user", username);
            response.put("sessionId", session.getId());
            response.put("authenticated", true);

            return ResponseEntity.ok().body(response);

        } catch (BadCredentialsException e) {
            System.out.println("❌ Échec connexion pour : " + username + " - " + e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Nom d'utilisateur ou mot de passe incorrect");
            errorResponse.put("error", "INVALID_CREDENTIALS");

            return ResponseEntity.status(401).body(errorResponse);
        } catch (Exception e) {
            System.out.println("❌ Erreur inattendue lors de la connexion : " + e.getMessage());
            e.printStackTrace();

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Erreur serveur");
            errorResponse.put("error", "INTERNAL_ERROR");

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            // Nettoyer le contexte de sécurité
            SecurityContextHolder.clearContext();

            // Invalider la session
            HttpSession session = request.getSession(false);
            if (session != null) {
                System.out.println("🚪 Invalidation de la session : " + session.getId());
                session.invalidate();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Déconnexion réussie");
            response.put("authenticated", false);

            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            System.out.println("❌ Erreur lors de la déconnexion : " + e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Erreur lors de la déconnexion");

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @GetMapping("/check")
    public ResponseEntity<?> checkAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> response = new HashMap<>();

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            response.put("authenticated", true);
            response.put("user", auth.getName());
            response.put("authorities", auth.getAuthorities());
        } else {
            response.put("authenticated", false);
        }

        return ResponseEntity.ok(response); // ✅ Toujours 200
    }


    @GetMapping("/debug")
    public ResponseEntity<?> debugAuth(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);

        Map<String, Object> debug = new HashMap<>();
        debug.put("hasAuthentication", auth != null);
        debug.put("isAuthenticated", auth != null ? auth.isAuthenticated() : false);
        debug.put("principal", auth != null ? auth.getPrincipal().toString() : "null");
        debug.put("authorities", auth != null ? auth.getAuthorities().toString() : "null");
        debug.put("hasSession", session != null);
        debug.put("sessionId", session != null ? session.getId() : "null");

        if (session != null) {
            Object securityContext = session.getAttribute("SPRING_SECURITY_CONTEXT");
            debug.put("hasSecurityContextInSession", securityContext != null);
        }

        System.out.println("🐛 Debug auth : " + debug);
        return ResponseEntity.ok(debug);
    }
}