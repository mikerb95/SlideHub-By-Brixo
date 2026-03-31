package com.brixo.slidehub.ai.repository;

import com.brixo.slidehub.ai.model.PresenterNote;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio MongoDB para notas del presentador (CLAUDE.md §10.2).
 */
public interface PresenterNoteRepository extends MongoRepository<PresenterNote, String> {

    Optional<PresenterNote> findByPresentationIdAndSlideNumber(String presentationId, int slideNumber);

    List<PresenterNote> findByPresentationIdOrderBySlideNumberAsc(String presentationId);

    void deleteByPresentationId(String presentationId);
}
