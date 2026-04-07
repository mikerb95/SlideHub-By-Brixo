package com.brixo.slidehub.ui.repository;

import com.brixo.slidehub.ui.model.PresentationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PresentationParticipantRepository extends JpaRepository<PresentationParticipant, String> {

    List<PresentationParticipant> findByPresentationIdOrderByDisplayNameAsc(String presentationId);

    Optional<PresentationParticipant> findByIdAndPresentationId(String id, String presentationId);

    boolean existsByPresentationIdAndDisplayNameIgnoreCase(String presentationId, String displayName);
}
