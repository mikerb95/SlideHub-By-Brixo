package com.brixo.slidehub.ai.repository;

import com.brixo.slidehub.ai.model.RepoAnalysis;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repositorio MongoDB para análisis de repositorios (PLAN-EXPANSION.md Fase 3,
 * tarea 30).
 */
public interface RepoAnalysisRepository extends MongoRepository<RepoAnalysis, String> {

    /**
     * Busca análisis previo de un repositorio para evitar llamadas repetidas a
     * Gemini.
     */
    Optional<RepoAnalysis> findByRepoUrl(String repoUrl);
}
