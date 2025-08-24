package com.imposter.mockimposter.service;

import com.imposter.mockimposter.entities.User;
import com.imposter.mockimposter.repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("🔍 Tentative de chargement pour : " + username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        System.out.println("✅ Utilisateur trouvé en DB : " + user.getUsername());
        System.out.println("🔑 Hash du mot de passe : " + user.getPassword().substring(0, 10) + "...");
        System.out.println("👤 Rôle utilisateur : " + user.getRole());
        System.out.println("🟢 Utilisateur actif : " + user.isEnabled());

        // Vérifier si l'utilisateur est activé
        if (!user.isEnabled()) {
            throw new UsernameNotFoundException("User account is disabled: " + username);
        }

        // Construire les autorités/rôles
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();

        // Ajouter le rôle principal
        if (user.getRole() != null && !user.getRole().isEmpty()) {
            // Spring Security attend les rôles avec le préfixe "ROLE_"
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
            System.out.println("🔐 Autorité ajoutée : ROLE_" + user.getRole());
        } else {
            // Rôle par défaut
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            System.out.println("🔐 Autorité par défaut : ROLE_USER");
        }

        // Construire et retourner UserDetails
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword()) // Mot de passe hashé depuis la DB
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }
}