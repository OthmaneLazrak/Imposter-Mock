package com.imposter.mockimposter;

import com.imposter.mockimposter.entities.User;
import com.imposter.mockimposter.repositories.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
@SpringBootApplication
public class MockImposterApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockImposterApplication.class, args);
    }

    @Bean
    CommandLineRunner init(UserRepository repo, PasswordEncoder encoder) {
        return args -> {
            repo.findByUsername("user").ifPresentOrElse(
                    u -> System.out.println(">>> User 'user' already exists"),
                    () -> {
                        User u = new User();
                        u.setUsername("user");
                        u.setPassword(encoder.encode("password"));
                        u.setRole("USER");
                        u.setEnabled(true);
                        repo.save(u);
                        System.out.println(">>> User 'user' created!");

                    }

            );

            repo.findByUsername("admin").ifPresentOrElse(
                    u -> System.out.println(">>> User 'admin' already exists"),
                    () -> {
                        User u = new User();
                        u.setUsername("admin");
                        u.setPassword(encoder.encode("admin"));
                        u.setRole("ADMIN");
                        u.setEnabled(true);
                        repo.save(u);
                        System.out.println(">>> User 'admin' created!");
                    }
            );
        };
    }

}
