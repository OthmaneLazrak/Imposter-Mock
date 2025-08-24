package com.imposter.mockimposter.repositories;

import com.imposter.mockimposter.entities.MockProject;
import com.imposter.mockimposter.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MockProjectRepository extends JpaRepository<MockProject, Long> {
    // ⚡ On ne passe plus Optional<User>, juste User
    List<MockProject> findByUser(User user);
}
