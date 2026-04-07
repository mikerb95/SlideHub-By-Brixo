package com.brixo.slidehub.ui.repository;

import com.brixo.slidehub.ui.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio JPA de usuarios.
 */
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByGithubId(String githubId);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmailVerificationToken(String token);
}
