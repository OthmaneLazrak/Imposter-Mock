package com.imposter.mockimposter.repositories;

import com.imposter.mockimposter.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username); // ⚡ retourne Optional

    long countByRole(String admin);
}
