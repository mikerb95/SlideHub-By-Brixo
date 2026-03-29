package com.brixo.slidehub.ui.repository;

import com.brixo.slidehub.ui.model.Presentation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA de presentaciones.
 */
public interface PresentationRepository extends JpaRepository<Presentation, String> {

    List<Presentation> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Presentation> findByIdAndUserId(String id, String userId);
}
