package com.imposter.mockimposter.config;

import com.imposter.mockimposter.entities.User;
import com.imposter.mockimposter.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer {

    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    public void init() {
        // Vérifie si l’admin existe déjà
        if (userRepository.findByUsername("admin") == null) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword("admin"); // {noop} si pas d’encodeur
            admin.setRole("ADMIN");
            admin.setEnabled(true);
            userRepository.save(admin);
            System.out.println("Admin créé et stocké en base !");
        } else {
            System.out.println("Admin déjà présent en base.");
        }
    }
}
