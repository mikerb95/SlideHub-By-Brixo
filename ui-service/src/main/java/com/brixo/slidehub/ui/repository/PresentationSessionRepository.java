package com.brixo.slidehub.ui.repository;

import com.brixo.slidehub.ui.model.PresentationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PresentationSessionRepository extends JpaRepository<PresentationSession, String> {

    Optional<PresentationSession> findByPresentationIdAndActiveTrue(String presentationId);

    Optional<PresentationSession> findByPresentationIdAndJoinTokenAndActiveTrue(String presentationId,
            String joinToken);
}
